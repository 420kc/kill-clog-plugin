/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the red-side comparison subsystem for Kill Clog: state, async fan-out
 * for the second player's hiscore, clog, and CA data, plus the compare-side
 * state. UI dispatch is downstream via {@link Listener}.
 * Reads the primary player's results read-only through {@link LookupSession}
 * getters; the only write path is the swap, which calls
 * {@link LookupSession#adoptState} explicitly.
 */
package com.killclog;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;

/**
 * Red-side comparison lifecycle. Subscribers receive typed events through
 * {@link Listener}; this class owns comparison state while the panel owns the
 * visible widgets.
 *
 * <p>Threading mirrors {@link LookupSession}: lifecycle methods run on the
 * EDT; service futures bridge back via {@code SwingUtilities.invokeLater}
 * before firing listener callbacks. The {@code compareLookupVersion} counter
 * is volatile so its stale-result gate is observable across those threads.
 */
@Slf4j
public class ComparisonController
{
	/**
	 * Receives lifecycle events from a {@link ComparisonController}. All
	 * methods are invoked on the EDT.
	 */
	public interface Listener
	{
		/** Comparison mode just entered. Panel should stop rendering single-player cells and route through the controller. */
		void onComparisonEnter(String redRsn);

		/** Comparison mode just exited. Panel should restore single-player cells. */
		void onComparisonExit();

		/** Red player swapped in (became the new primary). Panel should re-render the primary side and update the search bar. */
		void onSwapToRedPlayer(String newPrimaryRsn);

		/** Red-side data arrived. Panel should trigger a cell + summary-bar re-render via the controller's render hooks. */
		void onCompareDataReady();

		/** Transient comparison lookup flavor text changed. Panel should show it in the normal search status line. */
		void onCompareStatus(String msg, Color color);

		/** Red lookup failed. Panel should return the search row to its idle compare state. */
		void onCompareError(String player, Throwable err);
	}

	/**
	 * Hooks into the panel for the few widgets this controller writes to that
	 * don't live on {@link Cells}: the summary bar (playerName, clogInfoLabel)
	 * and the standalone combat + total level cells.
	 */
	public interface CellRenderTarget
	{
		JLabel combatCell();

		JLabel totalLvlCell();

		JLabel playerName();

		JLabel clogInfoLabel();

		void updateInfoIcon(AccountDisplay display);

		Color getInfoColor();

		/** Async preload of item names referenced by the clog result. */
		void preloadClogItemNames(ClogResult clog);

		/** Apply an account-type badge (clog-mode + GIM + standard hiscore). */
		void applyBadge(JLabel label, @Nullable AccountDisplay display);

		/** Preload the PvM Summary reward sprite for a CA result. */
		void preloadCaReward(@Nullable CombatAchievementResult ca);

		/** Restore the clog info cell to single-player display. */
		void restoreClogCellForCompare(@Nullable ClogResult clog);
	}

	// Constants
	static final Color COMPARE_BLUE = TitleTooltip.COMPARE_BLUE;
	static final Color COMPARE_RED = TitleTooltip.COMPARE_RED;
	private static final Color COMPARE_DIM = new Color(160, 160, 160);
	static final String COMPARE_BLUE_HEX = String.format("#%06x", COMPARE_BLUE.getRGB() & 0xFFFFFF);
	static final String COMPARE_RED_HEX = String.format("#%06x", COMPARE_RED.getRGB() & 0xFFFFFF);
	// Deps
	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final RuneProfileService runeProfileService;
	private final LookupSession lookupSession;
	private final ItemManager itemManager;
	private final KillClogConfig config;
	private final TooltipController tooltipController;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final UnsyncedClogCatalog unsyncedCatalog;
	private final Listener listener;
	@Nullable private CellRenderTarget renderTarget;
	@Nullable private Cells cells;

	// State
	private boolean comparisonMode;
	@Nullable private HiscoreResult compareHiscoreResult;
	@Nullable private ClogResult compareClogResult;
	@Nullable private CombatAchievementResult compareCaResult;
	@Nullable private String compareRsn;

	/** Monotonic counter. Each {@link #doCompareLookup} increments; async callbacks compare against this to gate stale results. */
	private volatile int compareLookupVersion = 0;

	/** True while a compare service call has been fired and has not yet resolved (or been superseded). */
	private volatile boolean compareLookupInFlight = false;

	private final Map<HiscoreSkill, TooltipData> compareTooltipDataMap = new LinkedHashMap<>();

	// Red-side data for the pending Maggot King cell (pre-enum only; see PanelData).
	@Nullable private TooltipData pendingMaggotCompareData;

	private static final String COMPARE_BLUE_TEXT_KEY = "killclog.compare.blueText";
	private static final String COMPARE_RED_TEXT_KEY = "killclog.compare.redText";
	private static final String COMPARE_BLUE_HOVER_KEY = "killclog.compare.blueHover";
	private static final String COMPARE_RED_HOVER_KEY = "killclog.compare.redHover";

	public ComparisonController(HiscoreService hiscoreService, ClogService clogService,
		RuneProfileService runeProfileService, LookupSession lookupSession,
		ItemManager itemManager, KillClogConfig config, TooltipController tooltipController,
		TooltipDataBuilder tooltipDataBuilder, Listener listener)
	{
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.runeProfileService = runeProfileService;
		this.lookupSession = lookupSession;
		this.itemManager = itemManager;
		this.config = config;
		this.tooltipController = tooltipController;
		this.tooltipDataBuilder = tooltipDataBuilder;
		this.unsyncedCatalog = new UnsyncedClogCatalog(clogService);
		this.listener = listener;
	}

	/** Wire the panel-side hook target. Late-bound because the controller is built before the panel's labels exist. */
	public void setRenderTarget(@Nullable CellRenderTarget renderTarget)
	{
		this.renderTarget = renderTarget;
	}

	/** Wire the {@link Cells} reference. Late-bound: Cells needs a controller ref at its own construction. */
	public void setCells(@Nullable Cells cells)
	{
		this.cells = cells;
	}

	public void setClogIndex(@Nullable ClogIndex clogIndex)
	{
		unsyncedCatalog.setClogIndex(clogIndex);
	}

	public void setUnsyncedCatalogResolver(Consumer<ClogResult> resolver)
	{
		unsyncedCatalog.setResolver(resolver);
	}

	// Lifecycle

	/**
	 * Enter comparison mode: build the per-cell comparison tooltip data and
	 * fire {@link Listener#onComparisonEnter} for UI dispatch. Caller must
	 * have populated {@link #compareHiscoreResult} + {@link #compareClogResult}
	 * + {@link #compareRsn} (the {@link #doCompareLookup} pipeline does this).
	 */
	public void enter()
	{
		comparisonMode = true;

		rebuildTooltipData();
		listener.onComparisonEnter(compareRsn != null ? compareRsn : "");
	}

	public void rebuildTooltipData()
	{
		compareTooltipDataMap.clear();
		pendingMaggotCompareData = null;
		if (compareHiscoreResult != null)
		{
			ClogResult catalog = compareClogResult == null ? unsyncedCatalog.result() : null;
			for (HiscoreSkill boss : PanelData.BOSSES)
			{
				String bossName = boss.getName();
				String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(bossName, bossName);
				TooltipData data = buildCompareBossData(bossName, hiscoreName, catalog);
				if (data != null)
				{
					compareTooltipDataMap.put(boss, data);
				}
			}
			if (!PanelData.hasOfficialMaggotKing())
			{
				pendingMaggotCompareData = buildCompareBossData(
					PanelData.PENDING_MAGGOT_KING_NAME, PanelData.PENDING_MAGGOT_KING_NAME, catalog);
			}
		}
	}

	/** Build one boss cell's red-side TooltipData: synced clog data, else the unsynced catalog preview. */
	@Nullable
	private TooltipData buildCompareBossData(String displayName, String hiscoreName,
		@Nullable ClogResult catalog)
	{
		String category = ClogService.bossToCategory(hiscoreName);
		int rank = compareHiscoreResult.getRank(hiscoreName);
		TooltipData data = tooltipDataBuilder.buildTooltipData(displayName, category, rank, compareClogResult);
		if (data == null)
		{
			return tooltipDataBuilder.buildUnsyncedTooltipData(
				displayName, category, rank, "Kills: ", compareHiscoreResult.getKc(hiscoreName),
				catalog != null ? catalog : unsyncedCatalog.result());
		}
		tooltipDataBuilder.preloadItemImages(data);
		return data;
	}

	/**
	 * Exit comparison mode and clear red-side state. Listener handles the UI
	 * restoration (search bar reset, cell + summary-bar re-render to single-player mode).
	 */
	public void exit()
	{
		comparisonMode = false;
		compareLookupVersion++;
		compareLookupInFlight = false;
		compareHiscoreResult = null;
		compareClogResult = null;
		compareCaResult = null;
		compareRsn = null;
		compareTooltipDataMap.clear();
		pendingMaggotCompareData = null;
		listener.onComparisonExit();
	}

	/**
	 * Swap the red player into the primary slot. Captures the current red
	 * state, calls {@link #exit()} to tear down comparison, then
	 * {@link LookupSession#adoptState} to push the captured data into the
	 * session, then fires {@code onSwapToRedPlayer} so the panel can re-render
	 * the primary side.
	 */
	public void swapToComparePlayer()
	{
		HiscoreResult swapHiscore = compareHiscoreResult;
		ClogResult swapClog = compareClogResult;
		CombatAchievementResult swapCa = compareCaResult;
		String swapName = compareRsn;
		exit();
		lookupSession.adoptState(swapHiscore, swapClog, swapCa, swapName);
		listener.onSwapToRedPlayer(swapName);
	}

	/**
	 * Begin a red-side lookup. Runs the parallel hiscore + clog + CA fan-out under
	 * a version-stamp guard and dispatches UI updates through listener events.
	 *
	 * @param player the red-side player entered in the panel search bar
	 * @param localRsn active local player, used only for self-aware search flavor
	 */
	public void doCompareLookup(String player, @Nullable String localRsn)
	{
		final String redPlayer = player.trim();
		if (redPlayer.isEmpty() || compareLookupInFlight)
		{
			return;
		}

		if (lookupSession.getHiscoreResult() == null)
		{
			return;
		}

		compareLookupInFlight = true;
		compareCaResult = null;
		final int thisLookup = ++compareLookupVersion;
		String blueName = renderTarget != null ? renderTarget.playerName().getText().trim() : "";
		boolean blueIsSelf = localRsn != null && localRsn.equalsIgnoreCase(blueName);
		boolean redIsSelf = localRsn != null && localRsn.equalsIgnoreCase(redPlayer);
		boolean samePlayer = blueName.equalsIgnoreCase(redPlayer);

		if (samePlayer)
		{
			if (blueIsSelf)
			{
				setCompareStatus(SearchMessages.COMPARE_SELF_MIRROR, SearchMessages.SELF_COLOR, blueName, redPlayer);
			}
			else
			{
				setCompareStatus(SearchMessages.COMPARE_MIRROR, COMPARE_DIM, blueName, redPlayer);
			}
			compareLookupInFlight = false;
			compareHiscoreResult = lookupSession.getHiscoreResult();
			compareClogResult = lookupSession.getClogResult();
			compareCaResult = lookupSession.getCaResult();
			if (renderTarget != null)
			{
				renderTarget.preloadCaReward(compareCaResult);
			}
			compareRsn = blueName;
			enter();
			return;
		}

		if (blueIsSelf)
		{
			setCompareStatus(SearchMessages.COMPARE_SELF_BLUE, SearchMessages.SELF_COLOR, redPlayer, redPlayer, blueName);
		}
		else if (redIsSelf)
		{
			setCompareStatus(SearchMessages.COMPARE_SELF_RED, SearchMessages.SELF_COLOR, blueName, redPlayer, blueName);
		}
		else
		{
			setCompareStatus(SearchMessages.COMPARE_SEARCH, COMPARE_DIM, blueName, blueName);
		}

		// CA lookup runs in parallel with hiscore+clog
		runeProfileService.lookup(redPlayer).thenAccept(ca ->
			javax.swing.SwingUtilities.invokeLater(() ->
			{
				if (thisLookup == compareLookupVersion)
				{
					compareCaResult = ca;
					if (renderTarget != null)
					{
						renderTarget.preloadCaReward(ca);
					}
					if (comparisonMode && listener != null)
					{
						listener.onCompareDataReady();
					}
				}
			})
		).exceptionally(ex -> null);

		hiscoreService.lookup(redPlayer, null).thenAccept(result ->
			javax.swing.SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != compareLookupVersion)
				{
					return;
				}
				compareLookupInFlight = false;

				if (result == null)
				{
					setCompareStatus(SearchMessages.COMPARE_NOT_FOUND, COMPARE_RED, redPlayer, redPlayer);
					listener.onCompareError(redPlayer, null);
					return;
				}

				compareHiscoreResult = result;

				// Fire both public clog providers in parallel with independent timeouts.
				// Red-side self comparisons keep local widget data authoritative.
				CompletableFuture<ClogResult> cTemple = clogService.lookup(redPlayer);
				CompletableFuture<ClogResult> cRp = redIsSelf
					? CompletableFuture.completedFuture(null)
					: runeProfileService.lookupClog(redPlayer);

				ClogProviderFanout.chooseFreshest(cTemple, cRp)
				.thenAccept(clogRes ->
					javax.swing.SwingUtilities.invokeLater(() ->
					{
						if (thisLookup != compareLookupVersion)
						{
							return;
						}
						compareClogResult = clogRes;
						if (clogRes != null && renderTarget != null)
						{
							renderTarget.preloadClogItemNames(clogRes);
						}
						compareRsn = clogRes != null && clogRes.getPlayerName() != null
							? clogRes.getPlayerName() : redPlayer;
						enter();
					})
				).exceptionally(ex ->
				{
					javax.swing.SwingUtilities.invokeLater(() ->
					{
						if (thisLookup != compareLookupVersion)
						{
							return;
						}
						compareRsn = redPlayer;
						enter();
					});
					return null;
				});
			})
		).exceptionally(ex ->
		{
			javax.swing.SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != compareLookupVersion)
				{
					return;
				}
				compareLookupInFlight = false;
				listener.onCompareError(redPlayer, ex);
			});
			return null;
		});
	}

	private void setCompareStatus(String[] pool, Color color, String singleArg, String secondArg)
	{
		setCompareStatus(pool, color, singleArg, secondArg, singleArg);
	}

	private void setCompareStatus(String[] pool, Color color, String singleArg, String secondArg, String firstArg)
	{
		String template = pool[ThreadLocalRandom.current().nextInt(pool.length)];
		String msg = template.indexOf("%s") == template.lastIndexOf("%s")
			? String.format(template, singleArg)
			: String.format(template, firstArg, secondArg);
		listener.onCompareStatus(msg, color);
	}

	// Read-only state

	public boolean isComparisonMode()
	{
		return comparisonMode;
	}

	@Nullable
	public HiscoreResult getCompareHiscoreResult()
	{
		return compareHiscoreResult;
	}

	@Nullable
	public ClogResult getCompareClogResult()
	{
		return compareClogResult;
	}

	@Nullable
	public CombatAchievementResult getCompareCaResult()
	{
		return compareCaResult;
	}

	@Nullable
	public String getCompareRsn()
	{
		return compareRsn;
	}

	public boolean isCompareLookupInFlight()
	{
		return compareLookupInFlight;
	}

	@Nullable
	public TooltipData getCompareTooltipData(HiscoreSkill skill)
	{
		return compareTooltipDataMap.get(skill);
	}

	@Nullable
	public TooltipData getPendingMaggotTooltipData()
	{
		return pendingMaggotCompareData;
	}

	public Map<HiscoreSkill, TooltipData> getCompareTooltipDataMap()
	{
		return Collections.unmodifiableMap(compareTooltipDataMap);
	}

	// Pure helpers

	/**
	 * Build a comparison tooltip showing both players' sprite grids stacked.
	 * Falls back to single-player tooltip when not in comparison mode.
	 */
	public JToolTip makeSpriteTooltip(JLabel owner, TooltipData blueData,
		TooltipData redData, String name)
	{
		return makeSpriteTooltip(owner, blueData, redData, name, true);
	}

	public JToolTip makeSpriteTooltip(JLabel owner, TooltipData blueData,
		TooltipData redData, String name, boolean showSpriteGrids)
	{
		JPanel parentCell = (JPanel) owner.getParent();
		CompareImgTooltip tip = new CompareImgTooltip();
		tip.setComponent(owner);
		tip.setTitle(name);
		tip.setShowSpriteGrids(showSpriteGrids);
		tip.setWikiLinksEnabled(config.wikiItemLinks());

		String blueName = renderTarget != null ? renderTarget.playerName().getText().trim() : "";
		if (blueName.isEmpty())
		{
			blueName = "Blue";
		}
		String redName = compareRsn != null ? compareRsn : "Red";

		boolean blueHas = blueData != null && blueData.allItemIds != null
			&& !blueData.allItemIds.isEmpty();
		boolean redHas = redData != null && redData.allItemIds != null
			&& !redData.allItemIds.isEmpty();
		boolean rankTracked = (blueData != null && blueData.rankTracked)
			|| (redData != null && redData.rankTracked);

		tip.setBluePlayer(blueName,
			blueData != null ? blueData.obtainedCount : -1,
			blueData != null ? blueData.totalItems : 0,
			blueData != null ? blueData.rank : 0,
			rankTracked);
		tip.setRedPlayer(redName,
			redData != null ? redData.obtainedCount : -1,
			redData != null ? redData.totalItems : 0,
			redData != null ? redData.rank : 0,
			rankTracked);
		tip.setBlueHasData(blueHas);
		tip.setRedHasData(redHas);

		List<Integer> allItemIds = blueHas ? blueData.allItemIds
			: (redHas ? redData.allItemIds : null);
		tip.setItems(allItemIds,
			blueData != null ? blueData.obtainedIds : Collections.emptySet(),
			blueData != null ? blueData.obtainedCounts : Collections.emptyMap(),
			redData != null ? redData.obtainedIds : Collections.emptySet(),
			redData != null ? redData.obtainedCounts : Collections.emptyMap(),
			mergedItemNames(blueData, redData),
			itemManager);

		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	private static Map<Integer, String> mergedItemNames(TooltipData blueData, TooltipData redData)
	{
		Map<Integer, String> names = new LinkedHashMap<>();
		if (redData != null)
		{
			names.putAll(redData.itemNames);
		}
		if (blueData != null)
		{
			names.putAll(blueData.itemNames);
		}
		return names;
	}

	@Nullable
	public TooltipData buildClueRare(String name, String clogCategory)
	{
		TooltipData data = compareClogResult != null
			? tooltipDataBuilder.buildClueRareData(name, clogCategory, compareClogResult) : null;
		return data != null ? data : unsyncedClueRare(name, clogCategory);
	}

	@Nullable
	public TooltipData buildCustomRare(String name, int[] itemIds)
	{
		TooltipData data = compareClogResult != null
			? tooltipDataBuilder.buildCustomRareData(name, itemIds, compareClogResult) : null;
		return data != null ? data
			: tooltipDataBuilder.buildUnsyncedItemData(name, itemIds, unsyncedCatalog.result());
	}

	@Nullable
	private TooltipData unsyncedClueRare(String name, String clogCategory)
	{
		ClogResult catalog = unsyncedCatalog.result();
		TooltipData data = tooltipDataBuilder.buildUnsyncedTooltipData(
			name, clogCategory, -1, null, -1, catalog);
		if (data != null)
		{
			return data;
		}
		if (PanelData.CLOG_THIRD_AGE.equals(clogCategory))
		{
			return tooltipDataBuilder.buildUnsyncedItemData(name, PanelData.THIRD_AGE_ITEMS, catalog);
		}
		if (PanelData.CLOG_GILDED.equals(clogCategory))
		{
			return tooltipDataBuilder.buildUnsyncedItemData(name, PanelData.GILDED_ITEMS, catalog);
		}
		return null;
	}

	/** Set a cell to dual blue/red values. */
	public void setCompareCell(JLabel label, int blueVal, int redVal)
	{
		setCompareCell(label, blueVal, redVal, null, null);
	}

	/** Set a cell to dual blue/red values, with optional completion-color hover state. */
	public void setCompareCell(JLabel label, int blueVal, int redVal,
		@Nullable Color blueHover, @Nullable Color redHover)
	{
		String blueText = blueVal > 0 ? ClogHelper.formatKc(blueVal) : "--";
		String redText = redVal > 0 ? ClogHelper.formatKc(redVal) : "--";
		label.putClientProperty(COMPARE_BLUE_TEXT_KEY, blueText);
		label.putClientProperty(COMPARE_RED_TEXT_KEY, redText);
		label.putClientProperty(COMPARE_BLUE_HOVER_KEY, blueHover != null ? colorHex(blueHover) : null);
		label.putClientProperty(COMPARE_RED_HOVER_KEY, redHover != null ? colorHex(redHover) : null);
		renderCompareCell(label, false);
		label.setForeground(null);
		label.setHorizontalAlignment(JLabel.CENTER);
	}

	/** Apply or clear the hover-only completion colors for a comparison cell. */
	public void setCompareCellHover(JLabel label, boolean hovering)
	{
		if (label != null && label.getClientProperty(COMPARE_BLUE_TEXT_KEY) instanceof String)
		{
			renderCompareCell(label, hovering);
		}
	}

	private void renderCompareCell(JLabel label, boolean hovering)
	{
		String blueText = String.valueOf(label.getClientProperty(COMPARE_BLUE_TEXT_KEY));
		String redText = String.valueOf(label.getClientProperty(COMPARE_RED_TEXT_KEY));
		String blueColor = hovering ? (String) label.getClientProperty(COMPARE_BLUE_HOVER_KEY) : null;
		String redColor = hovering ? (String) label.getClientProperty(COMPARE_RED_HOVER_KEY) : null;
		if (blueColor == null)
		{
			blueColor = COMPARE_BLUE_HEX;
		}
		if (redColor == null)
		{
			redColor = COMPARE_RED_HEX;
		}
		label.setText("<html><div style='text-align:center;'>"
			+ "<span style='color:" + blueColor + ";'>" + blueText + "</span><br>"
			+ "<span style='color:" + redColor + ";'>" + redText + "</span>"
			+ "</div></html>");
	}

	/** Restore a cell to single-player solo display. */
	public void restoreSoloCell(JLabel label, int val)
	{
		clearCompareCellState(label);
		label.setHorizontalAlignment(JLabel.LEADING);
		Color infoColor = renderTarget != null ? renderTarget.getInfoColor()
			: ColorScheme.LIGHT_GRAY_COLOR;
		if (val > 0)
		{
			label.setText(ClogHelper.pad(ClogHelper.formatKc(val)));
			label.setForeground(infoColor);
		}
		else
		{
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
	}

	private static void clearCompareCellState(JLabel label)
	{
		label.putClientProperty(COMPARE_BLUE_TEXT_KEY, null);
		label.putClientProperty(COMPARE_RED_TEXT_KEY, null);
		label.putClientProperty(COMPARE_BLUE_HOVER_KEY, null);
		label.putClientProperty(COMPARE_RED_HOVER_KEY, null);
	}

	/**
	 * Render comparison identity in the summary bar, or restore single-player
	 * display when comparison mode is off.
	 */
	public void updateInfoBar()
	{
		if (renderTarget == null)
		{
			return;
		}
		JLabel playerName = renderTarget.playerName();
		JLabel clogInfoLabel = renderTarget.clogInfoLabel();
		if (comparisonMode)
		{
			playerName.setForeground(COMPARE_BLUE);
			playerName.setToolTipText(" ");
			clogInfoLabel.setText(compareRsn != null ? compareRsn : "");
			clogInfoLabel.setForeground(COMPARE_RED);
			clogInfoLabel.setToolTipText(" ");
			clogInfoLabel.setIcon(null);
			clogInfoLabel.setHorizontalAlignment(JLabel.RIGHT);
			renderTarget.applyBadge(playerName,
				LookupQueries.accountDisplay(lookupSession.getHiscoreResult(),
					lookupSession.getClogResult()));
			renderTarget.applyBadge(clogInfoLabel, compareAccountDisplay());
		}
		else
		{
			playerName.setForeground(renderTarget.getInfoColor());
			clogInfoLabel.setHorizontalAlignment(JLabel.RIGHT);
			renderTarget.restoreClogCellForCompare(lookupSession.getClogResult());
		}
	}

	@Nullable
	private AccountDisplay compareAccountDisplay()
	{
		AccountType type = LookupQueries.accountType(compareHiscoreResult, compareClogResult);
		if (compareRsn == null)
		{
			return AccountDisplay.of(type,
				compareHiscoreResult != null ? compareHiscoreResult.getHiscoreTable()
					: HiscoreTable.STANDARD);
		}
		AccountType providerType = runeProfileService.getCachedAccountType(compareRsn);
		return AccountDisplay.of(
			AccountType.displayType(type,
				providerType != null && providerType.isGroupIronman() ? providerType : null),
			compareHiscoreResult != null ? compareHiscoreResult.getHiscoreTable()
				: HiscoreTable.STANDARD);
	}

	/**
	 * Refresh every cell on the panel for comparison mode (or restore each
	 * cell to single-player display when comparison is off). Iterates the
	 * boss / activity / clue-tier label maps + the standalone cells (combat,
	 * total, pvp, 3rd age, gilded, rares).
	 */
	public void updateAllCells()
	{
		if (renderTarget == null)
		{
			return;
		}
		HiscoreResult blueHiscore = lookupSession.getHiscoreResult();
		HiscoreResult redHiscore = compareHiscoreResult;

		for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getBossLabels().entrySet())
		{
			String name = PanelData.NAME_OVERRIDES.getOrDefault(entry.getKey().getName(), entry.getKey().getName());
			compareOrRestore(entry.getValue(), hiscoreKc(blueHiscore, name), hiscoreKc(redHiscore, name),
				cells.getTooltipData(entry.getKey()), compareTooltipDataMap.get(entry.getKey()));
		}

		for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getActivityLabels().entrySet())
		{
			String name = entry.getKey().getName();
			compareOrRestore(entry.getValue(), activityScore(blueHiscore, name), activityScore(redHiscore, name));
		}

		for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getClueTierLabels().entrySet())
		{
			String name = entry.getKey().getName();
			TooltipData redData = buildCompareClueTierData(entry.getKey());
			compareOrRestore(entry.getValue(), activityScore(blueHiscore, name), activityScore(redHiscore, name),
				cells.getTooltipData(entry.getKey()), redData);
		}

		compareOrRestore(renderTarget.combatCell(),
			blueHiscore != null ? blueHiscore.getCombatLevel() : -1,
			redHiscore != null ? redHiscore.getCombatLevel() : -1);

		compareOrRestore(renderTarget.totalLvlCell(),
			blueHiscore != null ? blueHiscore.getTotalLevel() : -1,
			redHiscore != null ? redHiscore.getTotalLevel() : -1);

		compareOrRestore(cells.getPvpSummaryCell(), pvpTotal(blueHiscore), pvpTotal(redHiscore));

		Map<String, TooltipData> rareTooltips = cells.getRareTooltips();
		TooltipData redThirdAge = buildClueRare("3rd Age", PanelData.CLOG_THIRD_AGE);
		TooltipData redGilded = buildClueRare("Gilded", PanelData.CLOG_GILDED);
		TooltipData redHardRare = buildCustomRare("Hard Treasure (Rare)", PanelData.HARD_RARE_ITEMS);
		TooltipData redEliteRare = buildCustomRare("Elite Treasure (Rare)", PanelData.ELITE_RARE_ITEMS);
		TooltipData redMasterRare = buildCustomRare("Master Treasure (Rare)", PanelData.MASTER_RARE_ITEMS);
		compareOrRestore(cells.getThirdAgeCell(),
			rareCount(rareTooltips.get(PanelData.CLOG_THIRD_AGE)),
			rareCount(redThirdAge),
			rareTooltips.get(PanelData.CLOG_THIRD_AGE), redThirdAge);
		compareOrRestore(cells.getGildedCell(),
			rareCount(rareTooltips.get(PanelData.CLOG_GILDED)),
			rareCount(redGilded),
			rareTooltips.get(PanelData.CLOG_GILDED), redGilded);

		compareOrRestore(cells.getHardRare(),
			rareCount(rareTooltips.get(PanelData.RARE_HARD)),
			rareCount(redHardRare),
			rareTooltips.get(PanelData.RARE_HARD), redHardRare);
		compareOrRestore(cells.getEliteRare(),
			rareCount(rareTooltips.get(PanelData.RARE_ELITE)),
			rareCount(redEliteRare),
			rareTooltips.get(PanelData.RARE_ELITE), redEliteRare);
		compareOrRestore(cells.getMasterRare(),
			rareCount(rareTooltips.get(PanelData.RARE_MASTER)),
			rareCount(redMasterRare),
			rareTooltips.get(PanelData.RARE_MASTER), redMasterRare);
	}

	@Nullable
	TooltipData buildCompareClueTierData(HiscoreSkill tier)
	{
		String category = PanelData.CLUE_CATEGORIES.get(tier);
		if (category == null)
		{
			return null;
		}
		int rank = compareHiscoreResult != null
			? compareHiscoreResult.getActivityRank(tier.getName()) : -1;
		TooltipData data = compareClogResult != null
			? tooltipDataBuilder.buildTooltipData(Cells.capitalizeTier(tier),
				category, rank, compareClogResult) : null;
		if (data != null)
		{
			return data;
		}
		int score = compareHiscoreResult != null
			? compareHiscoreResult.getActivityScore(tier.getName()) : -1;
		return tooltipDataBuilder.buildUnsyncedTooltipData(
			Cells.capitalizeTier(tier), category, rank, "Score: ", score, unsyncedCatalog.result());
	}

	private static int hiscoreKc(@Nullable HiscoreResult r, String hiscoreName)
	{
		return r != null ? r.getKc(hiscoreName) : -1;
	}

	private static int activityScore(@Nullable HiscoreResult r, String name)
	{
		return r != null ? r.getActivityScore(name) : -1;
	}

	private static int pvpTotal(@Nullable HiscoreResult r)
	{
		if (r == null)
		{
			return -1;
		}
		int total = Math.max(0, r.getActivityScore("Bounty Hunter - Hunter"))
			+ Math.max(0, r.getActivityScore("Bounty Hunter - Rogue"));
		return total > 0 ? total : -1;
	}

	private static int rareCount(@Nullable TooltipData data)
	{
		return data != null && data.obtainedCount > 0 ? data.obtainedCount : -1;
	}

	/** Toggle a cell between compare and solo display based on current mode. */
	public void compareOrRestore(JLabel label, int blueVal, int redVal)
	{
		compareOrRestore(label, blueVal, redVal, null, null);
	}

	/** Toggle a cell between compare and solo display with optional hover colors. */
	public void compareOrRestore(JLabel label, int blueVal, int redVal,
		@Nullable TooltipData blueData, @Nullable TooltipData redData)
	{
		if (label == null)
		{
			return;
		}
		if (comparisonMode)
		{
			setCompareCell(label, blueVal, redVal,
				completionColor(blueData), completionColor(redData));
		}
		else
		{
			restoreSoloCell(label, blueVal);
		}
	}

	@Nullable
	private Color completionColor(@Nullable TooltipData data)
	{
		if (data == null || data.totalItems <= 0 || data.obtainedCount < 0)
		{
			return null;
		}
		return ClogHelper.clogColor(data.obtainedCount, data.totalItems, config);
	}

	private static String colorHex(Color color)
	{
		return String.format("#%06x", color.getRGB() & 0xFFFFFF);
	}
}
