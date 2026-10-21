/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 * Owns the red-side comparison subsystem for Kill Clog: state, async fan-out
 * for the second player's hiscore, clog, and CA data, plus the compare-side
 * state. UI dispatch is downstream via {@link Listener}.
 * Reads the primary player's results read-only through {@link LookupSession}
 * getters.
 */
package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;

/**
 * Red-side comparison lifecycle. Subscribers receive typed events through
 * {@link Listener}; this class owns comparison state while the panel owns the
 * visible widgets.
 *
 * <p>Threading mirrors {@link LookupSession}: lifecycle methods run on the
 * EDT, and the owned {@link LookupFanout} bridges service futures back to the
 * EDT behind its version-stamped stale-result gate.
 */
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

		Color getInfoColor();

		/** Async preload of item names referenced by the clog result. */
		void preloadClogItemNames(ClogResult clog);

		/** Apply an account-type badge (clog-mode + GIM + standard hiscore). */
		void applyBadge(JLabel label, @Nullable AccountDisplay display);

		/** The primary player's identity as the panel keeps it, or null before it has one. */
		@Nullable
		AccountDisplay primaryIdentity();

		/** Preload the Combat Summary reward sprite for a CA result. */
		void preloadCaReward(@Nullable CombatAchievementResult ca);

		/** Restore the clog info cell to single-player display. */
		void restoreClogCellForCompare(@Nullable ClogResult clog);
	}

	// Constants
	static final Color COMPARE_BLUE = TitleTooltip.COMPARE_BLUE;
	static final Color COMPARE_RED = TitleTooltip.COMPARE_RED;
	static final String COMPARE_BLUE_HEX = colorHex(COMPARE_BLUE);
	static final String COMPARE_RED_HEX = colorHex(COMPARE_RED);
	// Deps
	// The badge rule both name rows share.
	private final PanelAccountTypes accountTypes;
	private final LookupSession lookupSession;

	private final KillClogConfig config;
	private final TooltipController tooltipController;
	private final TooltipDataBuilder tooltipDataBuilder;
	private final UnsyncedClogCatalog unsyncedCatalog;
	private final Listener listener;
	@Nullable private CellRenderTarget renderTarget;
	// The panel's identity-row name rule; label text is only the fallback.
	@Setter
	private Supplier<String> blueNameSupplier = () -> null;
	@Nullable private Cells cells;

	// State
	@Getter
	private boolean comparisonMode;
	private ComparedPlayer compared = ComparedPlayer.EMPTY;

	/** One committed identity; optional CA enrichment replaces this whole value. */
	private static final class ComparedPlayer
	{
		static final ComparedPlayer EMPTY = new ComparedPlayer(-1, null, null, null, null, null);
		final int generation;
		@Nullable final HiscoreResult hiscore;
		@Nullable final ClogResult clog;
		@Nullable final CombatAchievementResult ca;
		@Nullable final String rsn;
		/** RuneLite's own type when this player is you, like a group ironman the hiscores read as regular. */
		@Nullable final AccountType selfType;

		ComparedPlayer(int generation, HiscoreResult hiscore, ClogResult clog,
			CombatAchievementResult ca, String rsn, AccountType selfType)
		{
			this.generation = generation;
			this.hiscore = hiscore;
			this.clog = clog;
			this.ca = ca;
			this.rsn = rsn;
			this.selfType = selfType;
		}
	}

	/** Mutable optional evidence belongs only to this lookup's captured callbacks. */
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class PendingLookup
	{
		final int generation;
		final String requestedName;
		@Nullable final AccountType selfType;
		CombatAchievementResult ca;

	}

	/**
	 * Red-side transport: version stamps, EDT bridging, and timeout policy
	 * shared with the primary side, in its own version space.
	 */
	private final LookupFanout fanout;

	private final Map<HiscoreSkill, TooltipData> compareTooltipDataMap = new LinkedHashMap<>();

	private static final String COMPARE_BLUE_TEXT_KEY = "killclog.compare.blueText";
	private static final String COMPARE_RED_TEXT_KEY = "killclog.compare.redText";
	private static final String COMPARE_BLUE_HOVER_KEY = "killclog.compare.blueHover";
	private static final String COMPARE_RED_HOVER_KEY = "killclog.compare.redHover";

	public ComparisonController(HiscoreService hiscoreService, ClogService clogService,
		RuneProfileService runeProfileService, KillclogService killclogService,
		LookupSession lookupSession,
		KillClogConfig config, TooltipController tooltipController,
		TooltipDataBuilder tooltipDataBuilder, UnsyncedClogCatalog unsyncedCatalog, Listener listener)
	{
		this.accountTypes = new PanelAccountTypes(runeProfileService);
		this.lookupSession = lookupSession;
		this.config = config;
		this.tooltipController = tooltipController;
		this.tooltipDataBuilder = tooltipDataBuilder;
		this.unsyncedCatalog = unsyncedCatalog;
		this.listener = listener;
		this.fanout = new LookupFanout(hiscoreService, clogService, runeProfileService, killclogService);
	}

	/** The comparison reads the same game as the main lookup. */
	void readLeague(@Nullable String league, @Nullable LocalClogCache leagueLog)
	{
		fanout.readLeague(league, leagueLog);
	}

	/** Wire the panel-side hook target. Late-bound because the controller is built before the panel's labels exist. */
	public void setRenderTarget(@Nullable CellRenderTarget renderTarget)
	{
		this.renderTarget = renderTarget;
	}

	@Setter
	// Follows the vanilla virtual-total setting; display only, never ranking.
	private Supplier<Boolean> virtualTotalLevel = () -> false;

	/** Wire the {@link Cells} reference. Late-bound: Cells needs a controller ref at its own construction. */
	public void setCells(@Nullable Cells cells)
	{
		this.cells = cells;
	}

	public void setClogIndex(@Nullable ClogIndex clogIndex)
	{
		fanout.setClogIndex(clogIndex);
	}

	// Lifecycle

	/**
	 * Enter comparison mode: build the per-cell comparison tooltip data and
	 * fire {@link Listener#onComparisonEnter} for UI dispatch. Caller must
	 * have committed one complete player snapshot first.
	 */
	public void enter()
	{
		fanout.settle();
		comparisonMode = true;

		rebuildTooltipData();
		listener.onComparisonEnter(compared.rsn != null ? compared.rsn : "");
	}

	public void rebuildTooltipData()
	{
		compareTooltipDataMap.clear();
		if (compared.hiscore != null)
		{
			for (HiscoreSkill boss : PanelData.BOSSES)
			{
				HiscoreSkill card = PanelData.card(boss);
				String bossName = card.getName();
				String hiscoreName = PanelData.hiscoreName(bossName);
				// No red PB source exists, for a hard mode either.
				TooltipData data = TooltipDataBuilder.withHardMode(buildCompareBossData(bossName, hiscoreName),
					card, getCompareHiscoreResult(), name -> null);
				if (data != null)
				{
					compareTooltipDataMap.put(boss, data);
				}
			}
		}
	}

	/** Build one boss cell's red-side TooltipData: synced clog data, else the unsynced catalog preview. */
	@Nullable
	private TooltipData buildCompareBossData(String displayName, String hiscoreName)
	{
		HiscoreResult view = getCompareHiscoreResult();
		int kc = view.getKc(hiscoreName);
		// The red card is an ordinary solo card, so it carries the compared player's KC, or Glory on Sol Heredit,
		// the same way the blue one does. No red PB source exists.
		boolean showGlory = ColosseumGlory.leadsCard(displayName, view);
		return tooltipDataBuilder.buildBossCard(displayName, ClogService.bossToCategory(hiscoreName),
			view.getRank(hiscoreName), kc, null, getCompareClogResult(),
			showGlory ? null : "Kills: ", showGlory ? -1 : kc, unsyncedCatalog.result());
	}

	/**
	 * Exit comparison mode and clear red-side state. Listener handles the UI
	 * restoration (search bar reset, cell + summary-bar re-render to single-player mode).
	 */
	public void exit()
	{
		reset();
		listener.onComparisonExit();
	}

	/** Quiet shutdown: invalidate pending and settled callbacks without UI restoration. */
	void reset()
	{
		comparisonMode = false;
		fanout.invalidate();
		compared = ComparedPlayer.EMPTY;
		compareTooltipDataMap.clear();
	}

	/**
	 * Begin a red-side lookup. Runs the parallel hiscore + clog + CA fan-out under
	 * a version-stamp guard and dispatches UI updates through listener events.
	 *
	 * @param player the red-side player entered in the panel search bar
	 * @param localRsn active local player, used for self-aware search flavor
	 * @param localAccountType RuneLite's own type for the local player, worn when the red player is you
	 */
	public void doCompareLookup(String player, @Nullable String localRsn, @Nullable AccountType localAccountType)
	{
		final String redPlayer = player.trim();
		// A new comparison takes the place of one still in flight: its stamp goes stale.
		if (!RsnInputPolicy.isValid(redPlayer))
		{
			return;
		}

		if (lookupSession.getHiscoreResult() == null)
		{
			return;
		}

		final int thisLookup = fanout.begin();
		String blueName = renderTarget != null ? renderTarget.playerName().getText().trim() : "";
		boolean blueIsSelf = RsnInputPolicy.sameName(localRsn, blueName);
		boolean redIsSelf = RsnInputPolicy.sameName(localRsn, redPlayer);
		boolean samePlayer = RsnInputPolicy.sameName(blueName, redPlayer);
		AccountType redSelfType = redIsSelf ? localAccountType : null;
		final PendingLookup pending = new PendingLookup(thisLookup, redPlayer, redSelfType);

		if (samePlayer)
		{
			// Entering the comparison clears the status line, so the mirror's line is set after it, or it never shows.
			publish(new ComparedPlayer(thisLookup, lookupSession.getNativeHiscoreResult(),
				lookupSession.getNativeClogResult(), lookupSession.getCaResult(), blueName, redSelfType));
			if (blueIsSelf)
			{
				setCompareStatus(SearchMessages.COMPARE_SELF_MIRROR, SearchMessages.SELF_COLOR, blueName, redPlayer);
			}
			else
			{
				setCompareStatus(SearchMessages.COMPARE_MIRROR, KillClogPanel.TEXT_DIM, blueName, redPlayer);
			}
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
			setCompareStatus(SearchMessages.COMPARE_SEARCH, KillClogPanel.TEXT_DIM, blueName, blueName);
		}

		// CA lookup runs in parallel with hiscore+clog
		fanout.fetchCa(redPlayer, thisLookup, ca ->
		{
			pending.ca = ca;
			if (compared.generation == thisLookup)
			{
				compared = new ComparedPlayer(thisLookup, compared.hiscore, compared.clog, ca, compared.rsn,
					compared.selfType);
				if (renderTarget != null) renderTarget.preloadCaReward(ca);
				listener.onCompareDataReady();
			}
		});

		fanout.fetchHiscore(redPlayer, null, thisLookup, result ->
		{
			if (result == null)
			{
				fanout.invalidate();
				setCompareStatus(SearchMessages.COMPARE_NOT_FOUND, COMPARE_RED, redPlayer, redPlayer);
				listener.onCompareError(redPlayer, null);
				return;
			}

			// Clog fires only after the red player proved real: comparison
			// entry needs the hiscore side regardless, so the sequencing
			// costs nothing and spares provider calls on typos.
			fanout.fetchClog(redPlayer, redIsSelf, thisLookup,
				clogRes -> commit(pending, result, clogRes),
				() -> commit(pending, result, null));
		}, ex ->
		{
			fanout.invalidate();
			listener.onCompareError(redPlayer, ex);
		});
	}

	private void commit(PendingLookup pending, HiscoreResult hiscore, @Nullable ClogResult clog)
	{
		String name = clog != null && clog.getPlayerName() != null ? clog.getPlayerName() : pending.requestedName;
		publish(new ComparedPlayer(pending.generation, hiscore, clog, pending.ca, name, pending.selfType));
	}

	private void publish(ComparedPlayer player)
	{
		compared = player;
		if (renderTarget != null)
		{
			if (player.clog != null) renderTarget.preloadClogItemNames(player.clog);
			renderTarget.preloadCaReward(player.ca);
		}
		enter();
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

	/** The rival's own board: the leaderboard selector steps aside in a comparison. */
	@Nullable
	/** Comparing with a rival whose hiscores have answered: the cards show side by side. */
	boolean showsPair()
	{
		return isComparisonMode() && getCompareHiscoreResult() != null;
	}

	public HiscoreResult getCompareHiscoreResult()
	{
		return compared.hiscore;
	}

	@Nullable
	public ClogResult getCompareClogResult()
	{
		return compared.clog;
	}

	@Nullable
	public CombatAchievementResult getCompareCaResult()
	{
		return compared.ca;
	}

	@Nullable
	public String getCompareRsn()
	{
		return compared.rsn;
	}

	public boolean isCompareLookupInFlight()
	{
		return fanout.isInFlight();
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

	// Pure helpers

	/**
	 * Wrap the two players' single tooltips into one side-by-side comparison
	 * card, named and colored per player.
	 */
	public JToolTip wrapSideBySide(JComponent owner, JToolTip blueTip, JToolTip redTip)
	{
		TitleTooltip.scrollTogether(blueTip, redTip);
		String blueName = blueNameSupplier.get();
		if (blueName == null || blueName.trim().isEmpty())
		{
			blueName = renderTarget != null ? renderTarget.playerName().getText().trim() : "";
		}
		if (blueName.isEmpty())
		{
			blueName = "Blue";
		}
		String redName = compared.rsn != null ? compared.rsn : "Red";
		SideBySideTooltip tip = new SideBySideTooltip(blueName, blueTip, redName, redTip);
		if (renderTarget != null)
		{
			// Reuse displayed account identity, never the selected rank leaderboard.
			tip.setAccountBadges(renderTarget.playerName().getIcon(), renderTarget.clogInfoLabel().getIcon());
		}
		tip.setComponent(owner);
		return tip;
	}

	/** Set a cell to dual blue/red values, with optional completion-color hover state. */
	public void setCompareCell(JLabel label, int blueVal, int redVal,
		@Nullable Color blueHover, @Nullable Color redHover)
	{
		String blueText = ClogHelper.countText(blueVal);
		String redText = ClogHelper.countText(redVal);
		label.putClientProperty(COMPARE_BLUE_TEXT_KEY, blueText);
		label.putClientProperty(COMPARE_RED_TEXT_KEY, redText);
		label.putClientProperty(COMPARE_BLUE_HOVER_KEY, blueHover != null ? colorHex(blueHover) : null);
		label.putClientProperty(COMPARE_RED_HOVER_KEY, redHover != null ? colorHex(redHover) : null);
		renderCompareCell(label, false);
		label.setForeground(null);
		label.setHorizontalAlignment(JLabel.CENTER);
	}

	/** Drop a cell's comparison state without repainting; caller renders solo. */
	public void clearCompareCell(JLabel label)
	{
		label.putClientProperty(COMPARE_BLUE_TEXT_KEY, null);
		label.putClientProperty(COMPARE_RED_TEXT_KEY, null);
		label.putClientProperty(COMPARE_BLUE_HOVER_KEY, null);
		label.putClientProperty(COMPARE_RED_HOVER_KEY, null);
	}

	/**
	 * Wire the hover listeners that swap a comparison cell's blue/red values to
	 * each player's completion colors while hovered. Shared by every dual-value
	 * cell: bosses, activities, and skills.
	 */
	public void installCompareCellHover(JPanel cell, JLabel label)
	{
		MouseAdapter compareHover = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setCompareCellHover(label, true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (!isInsideCell(cell, e))
				{
					setCompareCellHover(label, false);
				}
			}
		};

		cell.addMouseListener(compareHover);
		label.addMouseListener(compareHover);
	}

	private static boolean isInsideCell(JPanel cell, MouseEvent e)
	{
		Object source = e.getSource();
		if (!(source instanceof Component))
		{
			return false;
		}
		Point p = SwingUtilities.convertPoint((Component) source, e.getPoint(), cell);
		return p.x >= 0 && p.y >= 0 && p.x < cell.getWidth() && p.y < cell.getHeight();
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
		clearCompareCell(label);
		label.setHorizontalAlignment(JLabel.LEADING);
		Color infoColor = renderTarget != null ? renderTarget.getInfoColor()
			: ColorScheme.LIGHT_GRAY_COLOR;
		Cells.showCount(label, val, infoColor);
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
			tooltipController.setTooltipText(playerName, " ");
			clogInfoLabel.setText(compared.rsn != null ? compared.rsn : "");
			clogInfoLabel.setForeground(COMPARE_RED);
			tooltipController.setTooltipText(clogInfoLabel, " ");
			clogInfoLabel.setIcon(null);
			clogInfoLabel.setHorizontalAlignment(JLabel.RIGHT);
			// The primary name keeps the identity the panel resolved, RuneLite's own type included.
			AccountDisplay blue = renderTarget.primaryIdentity();
			renderTarget.applyBadge(playerName, blue != null ? blue
				: LookupQueries.accountDisplay(lookupSession.getHiscoreResult(), lookupSession.getNativeClogResult()));
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
		return compared.selfType != null ? accountTypes.own(compared.selfType, compared.hiscore)
			: accountTypes.displayIdentity(compared.hiscore, compared.clog, compared.rsn);
	}

	/**
	 * Refresh every cell on the panel for comparison mode (or restore each
	 * cell to single-player display when comparison is off). Iterates the
	 * boss / activity / clue-tier label maps + the standalone cells (combat,
	 * total).
	 */
	public void updateAllCells()
	{
		if (renderTarget == null)
		{
			return;
		}
		HiscoreResult blueHiscore = lookupSession.getHiscoreResult();
		HiscoreResult redHiscore = getCompareHiscoreResult();

		for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getBossLabels().entrySet())
		{
			String name = PanelData.hiscoreName(entry.getKey().getName());
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

		boolean virtualTotal = virtualTotalLevel.get();
		compareOrRestore(renderTarget.totalLvlCell(),
			blueHiscore != null ? ClogHelper.displayTotalLevel(blueHiscore, virtualTotal) : -1,
			redHiscore != null ? ClogHelper.displayTotalLevel(redHiscore, virtualTotal) : -1);
	}

	@Nullable
	TooltipData buildCompareClueTierData(HiscoreSkill tier)
	{
		return tooltipDataBuilder.buildClueTierCard(tier, getCompareHiscoreResult(), getCompareClogResult(),
			unsyncedCatalog.result());
	}

	private static int hiscoreKc(@Nullable HiscoreResult r, String hiscoreName)
	{
		return r != null ? r.getKc(hiscoreName) : -1;
	}

	private static int activityScore(@Nullable HiscoreResult r, String name)
	{
		return r != null ? r.getActivityScore(name) : -1;
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
