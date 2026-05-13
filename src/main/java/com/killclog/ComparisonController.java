/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the red-side comparison subsystem for Kill Clog: state, async fan-out
 * for the second player's hiscore + clog, html cell formatters, and the
 * compare-mode tooltip. UI is downstream via {@link Listener}. Reads the
 * primary player's results read-only via {@link LookupSession} getters and
 * never writes back into the session (except the explicit
 * {@link LookupSession#adoptState} call from the swap path, which is the one
 * sanctioned bridge between the two).
 *
 * Extracted from KillClogPanel as refactor cut 2.
 */
package com.killclog;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;

/**
 * Encapsulates the red-side comparison lifecycle. A panel subscribes via
 * {@link Listener} and reacts to typed events; this class never touches the
 * primary-side widgets directly. Comparison-side widgets (search bar, status
 * label, toggle) are owned here and exposed to the panel via accessors so the
 * panel can lay them out.
 *
 * <p>Threading model mirrors {@link LookupSession}: lifecycle methods are
 * called on the EDT; service futures bridge back via
 * {@code SwingUtilities.invokeLater} before invoking listener callbacks.
 *
 * <p>State is read via getters; no setter exposes controller fields. All
 * mutations flow through the lifecycle methods and the async callbacks they
 * spawn. The compare lookup-version counter ({@link #compareLookupVersion})
 * is volatile to make stale-result gating observable across the background
 * threads the underlying services hop through.
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

		/** Red-side hiscore + clog data arrived. Panel should trigger a cell + info-bar re-render via the controller's render hooks. */
		void onCompareDataReady();

		/** Red lookup failed. Panel should surface the error in the comparison status row. */
		void onCompareError(String player, Throwable err);
	}

	/**
	 * Panel-UI hook target. The panel implements this so the controller can
	 * write to the info-bar widgets it doesn't own (playerName + clogInfoLabel
	 * are panel-side; the cells live on {@link Cells}). The combat + total
	 * level cells are the only standalone cells the panel still owns.
	 */
	public interface CellRenderTarget
	{
		javax.swing.JLabel combatCell();

		javax.swing.JLabel totalLvlCell();

		javax.swing.JLabel playerName();

		javax.swing.JLabel clogInfoLabel();

		void updateInfoIcon(AccountType type);

		Color getInfoColor();

		/** Trigger an async preload of item names referenced by the clog result. */
		void preloadClogItemNames(ClogResult clog);

		/** Apply an account-type badge to {@code label}. */
		void applyBadge(javax.swing.JLabel label, @Nullable AccountType type);

		/** Restore the clog info cell to single-player display. */
		void restoreClogCellForCompare(@Nullable ClogResult clog);
	}

	// ── Constants ─────────────────────────────────────────────────────────
	static final Color COMPARE_BLUE = new Color(91, 164, 207);
	static final Color COMPARE_RED = new Color(224, 86, 86);
	static final String COMPARE_BLUE_HEX = String.format("#%06x", COMPARE_BLUE.getRGB() & 0xFFFFFF);
	static final String COMPARE_RED_HEX = String.format("#%06x", COMPARE_RED.getRGB() & 0xFFFFFF);
	private static final Color COMPARE_DIM = new Color(160, 160, 160);

	// ── Deps ──────────────────────────────────────────────────────────────
	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final KillClogConfig config;
	private final LookupSession lookupSession;
	private final ItemManager itemManager;
	private final TooltipController tooltipController;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final Listener listener;
	@Nullable private CellRenderTarget renderTarget;
	@Nullable private Cells cells;

	// ── State ─────────────────────────────────────────────────────────────
	private boolean comparisonMode;
	@Nullable private HiscoreResult compareHiscoreResult;
	@Nullable private ClogResult compareClogResult;
	@Nullable private String compareRsn;

	/** Monotonic counter. Each {@link #doCompareLookup} increments; async callbacks compare against this to gate stale results. */
	private volatile int compareLookupVersion = 0;

	/** True while a compare service call has been fired and has not yet resolved (or been superseded). */
	private volatile boolean compareLookupInFlight = false;

	private final Map<HiscoreSkill, TooltipData> compareTooltipDataMap = new LinkedHashMap<>();

	// ── Widgets ───────────────────────────────────────────────────────────
	private final JLabel compareStatus = new JLabel(" ");
	private final IconTextField compareSearchBar = new IconTextField();
	@Nullable private JTextField compareTextField;
	private String comparePlaceholder = "Comparison";
	@Nullable private JPanel comparePanel;
	@Nullable private JLabel compareToggle;

	public ComparisonController(HiscoreService hiscoreService, ClogService clogService,
		KillClogConfig config, LookupSession lookupSession, ItemManager itemManager,
		TooltipController tooltipController, TooltipDataBuilder tooltipDataBuilder,
		Listener listener)
	{
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.config = config;
		this.lookupSession = lookupSession;
		this.itemManager = itemManager;
		this.tooltipController = tooltipController;
		this.tooltipDataBuilder = tooltipDataBuilder;
		this.listener = listener;
	}

	/**
	 * Late-bound cell-render target. The panel constructs the controller
	 * before its cell label maps are populated; this lets the panel forward
	 * the target once layout is built.
	 */
	public void setRenderTarget(@Nullable CellRenderTarget renderTarget)
	{
		this.renderTarget = renderTarget;
	}

	/** Late-bound cells reference (cells need a controller ref at construction time, so this is set after both exist). */
	public void setCells(@Nullable Cells cells)
	{
		this.cells = cells;
	}

	/** The compare-side status label (panel reads this for layout + initial styling). */
	public JLabel getCompareStatusLabel()
	{
		return compareStatus;
	}

	/** The compare-side search bar (panel reads this for layout + initial styling + action listener wiring). */
	public IconTextField getCompareSearchBar()
	{
		return compareSearchBar;
	}

	/** Inner JTextField of {@link #compareSearchBar}, captured during the panel's buildCompareSearch wiring. */
	@Nullable
	public JTextField getCompareTextField()
	{
		return compareTextField;
	}

	public void setCompareTextField(@Nullable JTextField tf)
	{
		this.compareTextField = tf;
	}

	public String getComparePlaceholder()
	{
		return comparePlaceholder;
	}

	@Nullable
	public JPanel getComparePanel()
	{
		return comparePanel;
	}

	public void setComparePanel(@Nullable JPanel panel)
	{
		this.comparePanel = panel;
	}

	@Nullable
	public JLabel getCompareToggle()
	{
		return compareToggle;
	}

	public void setCompareToggle(@Nullable JLabel toggle)
	{
		this.compareToggle = toggle;
	}

	/** Refresh the comparison search bar's placeholder text after the primary player changes. */
	public void updateComparePlaceholder(String name)
	{
		String old = comparePlaceholder;
		comparePlaceholder = name + " vs...";
		if (compareTextField == null || compareTextField.hasFocus())
		{
			return;
		}
		String current = compareTextField.getText();
		if (current.isEmpty() || current.equals(old) || current.equals("Comparison"))
		{
			compareTextField.setText(comparePlaceholder);
			compareTextField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}
	}

	/** Set the compare status label text + color. */
	public void setCompareStatus(String msg, Color color)
	{
		compareStatus.setText(msg);
		compareStatus.setForeground(color);
	}

	/** Pick a random message from {@code pool}, format it with {@code player} (twice for two-slot templates), and set the compare status. */
	public void setCompareStatus(String[] pool, String player, Color color)
	{
		String msg = String.format(pool[ThreadLocalRandom.current().nextInt(pool.length)], player, player);
		compareStatus.setText(msg);
		compareStatus.setForeground(color);
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────

	/**
	 * Enter comparison mode. State has already been written through
	 * {@link #syncCompareState} (or, post-migration, set directly by the
	 * doCompareLookup pipeline). Builds the comparison-side tooltip data and
	 * fires {@link Listener#onComparisonEnter} for the UI dispatch.
	 */
	public void enter()
	{
		comparisonMode = true;

		compareTooltipDataMap.clear();
		if (compareHiscoreResult != null)
		{
			for (HiscoreSkill boss : PanelData.BOSSES)
			{
				String bossName = boss.getName();
				String hiscoreName = PanelData.NAME_OVERRIDES.getOrDefault(bossName, bossName);
				String category = ClogService.bossToCategory(hiscoreName);
				int rank = compareHiscoreResult.getRank(hiscoreName);
				TooltipData data = tooltipDataBuilder.buildTooltipData(bossName, category, rank, compareClogResult);
				if (data != null)
				{
					compareTooltipDataMap.put(boss, data);
					tooltipDataBuilder.preloadItemImages(data);
				}
				else if (rank > 0)
				{
					int total = clogService.getCategoryItemCount(category);
					compareTooltipDataMap.put(boss, new TooltipData(
						bossName, rank, -1, Math.max(total, 0),
						Collections.emptyList(),
						Collections.emptySet(),
						Collections.emptyMap()));
				}
			}
		}

		listener.onComparisonEnter(compareRsn != null ? compareRsn : "");
	}

	/**
	 * Exit comparison mode and clear red-side state. Listener handles the UI
	 * restoration (search bar reset, status row blanked, cell + info-bar
	 * re-render to single-player mode).
	 */
	public void exit()
	{
		comparisonMode = false;
		compareLookupVersion++;
		compareLookupInFlight = false;
		compareHiscoreResult = null;
		compareClogResult = null;
		compareRsn = null;
		compareTooltipDataMap.clear();
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
		String swapName = compareRsn;
		exit();
		lookupSession.adoptState(swapHiscore, swapClog, swapName);
		listener.onSwapToRedPlayer(swapName);
	}

	/**
	 * Begin a red-side lookup. Reads the search bar text, gates on placeholder
	 * + in-flight, runs the parallel hiscore + clog API fan-out under a
	 * version-stamp guard, and dispatches UI updates via setCompareStatus +
	 * the listener events.
	 *
	 * @param localRsn the local player's RSN (for self-detection messaging)
	 */
	public void doCompareLookup(@Nullable String localRsn)
	{
		String player = compareSearchBar.getText().trim();
		if (player.isEmpty() || player.equals(comparePlaceholder) || compareLookupInFlight)
		{
			return;
		}

		if (lookupSession.getHiscoreResult() == null)
		{
			return;
		}

		compareLookupInFlight = true;
		final int thisLookup = ++compareLookupVersion;
		compareSearchBar.setIcon(IconTextField.Icon.LOADING_DARKER);
		String blueName = renderTarget != null ? renderTarget.playerName().getText().trim() : "";
		boolean blueIsSelf = localRsn != null && localRsn.equalsIgnoreCase(blueName);
		boolean redIsSelf = localRsn != null && localRsn.equalsIgnoreCase(player);
		boolean samePlayer = blueName.equalsIgnoreCase(player);

		if (samePlayer)
		{
			compareLookupInFlight = false;
			compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
			compareSearchBar.setText("");
			compareHiscoreResult = lookupSession.getHiscoreResult();
			compareClogResult = lookupSession.getClogResult();
			compareRsn = blueName;
			if (blueIsSelf)
			{
				setCompareStatus(SearchMessages.COMPARE_SELF_MIRROR, blueName, SearchMessages.SELF_COLOR);
			}
			else
			{
				setCompareStatus(SearchMessages.COMPARE_MIRROR, blueName, COMPARE_DIM);
			}
			enter();
			return;
		}

		if (blueIsSelf || redIsSelf)
		{
			String[] pool = blueIsSelf ? SearchMessages.COMPARE_SELF_BLUE : SearchMessages.COMPARE_SELF_RED;
			String msg = pool[ThreadLocalRandom.current().nextInt(pool.length)];
			if (msg.contains("%s") && msg.indexOf("%s") != msg.lastIndexOf("%s"))
			{
				msg = String.format(msg, blueName, player);
			}
			else
			{
				msg = String.format(msg, blueIsSelf ? player : blueName);
			}
			setCompareStatus(msg, SearchMessages.SELF_COLOR);
		}
		else
		{
			setCompareStatus(SearchMessages.COMPARE_SEARCH, blueName, COMPARE_DIM);
		}

		hiscoreService.lookup(player, null).thenAccept(result ->
			javax.swing.SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != compareLookupVersion)
				{
					return;
				}
				compareLookupInFlight = false;

				if (result == null)
				{
					compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
					compareSearchBar.setText("");
					setCompareStatus(SearchMessages.COMPARE_NOT_FOUND,
						renderTarget != null ? renderTarget.playerName().getText().trim() : "",
						COMPARE_RED);
					return;
				}

				compareHiscoreResult = result;

				clogService.lookup(player).thenAccept(clogRes ->
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
							? clogRes.getPlayerName() : player;
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
						compareRsn = player;
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
				compareSearchBar.setIcon(IconTextField.Icon.SEARCH);
				compareSearchBar.setText("");
				setCompareStatus("Lookup failed", COMPARE_RED);
			});
			return null;
		});
	}

	// ── Read-only state ───────────────────────────────────────────────────

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
	public String getCompareRsn()
	{
		return compareRsn;
	}

	public int getCompareLookupVersion()
	{
		return compareLookupVersion;
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

	public Map<HiscoreSkill, TooltipData> getCompareTooltipDataMap()
	{
		return Collections.unmodifiableMap(compareTooltipDataMap);
	}

	// ── Pure helpers ──────────────────────────────────────────────────────

	/**
	 * Build a comparison tooltip showing both players' sprite grids stacked.
	 * Falls back to single-player tooltip when not in comparison mode.
	 */
	public JToolTip makeSpriteTooltip(JLabel owner, TooltipData blueData,
		TooltipData redData, String name)
	{
		JPanel parentCell = (JPanel) owner.getParent();
		CompareImgTooltip tip = new CompareImgTooltip();
		tip.setComponent(owner);
		tip.setTitle(name);

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

		tip.setBluePlayer(blueName,
			blueData != null ? blueData.obtainedCount : -1,
			blueData != null ? blueData.totalItems : 0,
			blueData != null ? blueData.rank : 0);
		tip.setRedPlayer(redName,
			redData != null ? redData.obtainedCount : -1,
			redData != null ? redData.totalItems : 0,
			redData != null ? redData.rank : 0);
		tip.setBlueHasData(blueHas);
		tip.setRedHasData(redHas);

		List<Integer> allItemIds = blueHas ? blueData.allItemIds
			: (redHas ? redData.allItemIds : null);
		tip.setItems(allItemIds,
			blueData != null ? blueData.obtainedIds : Collections.emptySet(),
			blueData != null ? blueData.obtainedCounts : Collections.emptyMap(),
			redData != null ? redData.obtainedIds : Collections.emptySet(),
			redData != null ? redData.obtainedCounts : Collections.emptyMap(),
			itemManager);

		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	@Nullable
	public TooltipData buildClueRare(String name, String clogCategory)
	{
		return compareClogResult != null
			? tooltipDataBuilder.buildClueRareData(name, clogCategory, compareClogResult)
			: null;
	}

	@Nullable
	public TooltipData buildCustomRare(String name, int[] itemIds)
	{
		return compareClogResult != null
			? tooltipDataBuilder.buildCustomRareData(name, itemIds, compareClogResult)
			: null;
	}

	/** Set a cell to dual blue/red values. */
	public void setCompareCell(JLabel label, int blueVal, int redVal)
	{
		String blueText = blueVal > 0 ? ClogHelper.formatKc(blueVal) : "--";
		String redText = redVal > 0 ? ClogHelper.formatKc(redVal) : "--";
		label.setText("<html><div style='text-align:center;'>"
			+ "<span style='color:" + COMPARE_BLUE_HEX + ";'>" + blueText + "</span><br>"
			+ "<span style='color:" + COMPARE_RED_HEX + ";'>" + redText + "</span>"
			+ "</div></html>");
		label.setForeground(null);
		label.setHorizontalAlignment(JLabel.CENTER);
	}

	/** Restore a cell to single-player solo display. */
	public void restoreSoloCell(JLabel label, int val)
	{
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

	/**
	 * Render the comparison-mode info bar (blue name on the left, red name on
	 * the right with an account-type badge), or restore single-player display
	 * when comparison mode is off. Mirrors the legacy
	 * {@code KillClogPanel.updateInfoBarForComparison}.
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
			clogInfoLabel.setText(compareRsn != null ? compareRsn : "");
			clogInfoLabel.setForeground(COMPARE_RED);
			clogInfoLabel.setToolTipText(null);
			clogInfoLabel.setHorizontalAlignment(JLabel.RIGHT);
			AccountType redType = compareClogResult != null ? compareClogResult.getTempleAccountType() : null;
			if (redType == null && compareHiscoreResult != null)
			{
				redType = compareHiscoreResult.getAccountType();
			}
			renderTarget.applyBadge(clogInfoLabel, redType);
		}
		else
		{
			playerName.setForeground(renderTarget.getInfoColor());
			clogInfoLabel.setHorizontalAlignment(JLabel.RIGHT);
			renderTarget.restoreClogCellForCompare(lookupSession.getClogResult());
		}
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
			compareOrRestore(entry.getValue(), hiscoreKc(blueHiscore, name), hiscoreKc(redHiscore, name));
		}

		for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getActivityLabels().entrySet())
		{
			String name = entry.getKey().getName();
			compareOrRestore(entry.getValue(), activityScore(blueHiscore, name), activityScore(redHiscore, name));
		}

		for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getClueTierLabels().entrySet())
		{
			String name = entry.getKey().getName();
			compareOrRestore(entry.getValue(), activityScore(blueHiscore, name), activityScore(redHiscore, name));
		}

		compareOrRestore(renderTarget.combatCell(),
			blueHiscore != null ? blueHiscore.getCombatLevel() : -1,
			redHiscore != null ? redHiscore.getCombatLevel() : -1);

		compareOrRestore(renderTarget.totalLvlCell(),
			blueHiscore != null ? blueHiscore.getTotalLevel() : -1,
			redHiscore != null ? redHiscore.getTotalLevel() : -1);

		compareOrRestore(cells.getPvpSummaryCell(), pvpTotal(blueHiscore), pvpTotal(redHiscore));

		Map<String, TooltipData> rareTooltips = cells.getRareTooltips();
		compareOrRestore(cells.getThirdAgeCell(),
			rareCount(rareTooltips.get(PanelData.CLOG_THIRD_AGE)),
			rareCount(buildClueRare("3rd Age", PanelData.CLOG_THIRD_AGE)));
		compareOrRestore(cells.getGildedCell(),
			rareCount(rareTooltips.get(PanelData.CLOG_GILDED)),
			rareCount(buildClueRare("Gilded", PanelData.CLOG_GILDED)));

		compareOrRestore(cells.getHardRare(),
			rareCount(rareTooltips.get(PanelData.RARE_HARD)),
			rareCount(buildCustomRare("Hard Treasure (Rare)", PanelData.HARD_RARE_ITEMS)));
		compareOrRestore(cells.getEliteRare(),
			rareCount(rareTooltips.get(PanelData.RARE_ELITE)),
			rareCount(buildCustomRare("Elite Treasure (Rare)", PanelData.ELITE_RARE_ITEMS)));
		compareOrRestore(cells.getMasterRare(),
			rareCount(rareTooltips.get(PanelData.RARE_MASTER)),
			rareCount(buildCustomRare("Master Treasure (Rare)", PanelData.MASTER_RARE_ITEMS)));
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
		if (label == null)
		{
			return;
		}
		if (comparisonMode)
		{
			setCompareCell(label, blueVal, redVal);
		}
		else
		{
			restoreSoloCell(label, blueVal);
		}
	}
}
