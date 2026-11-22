/*
 * Copyright (c) 2026, 420 kc <dyl@420kc.dev>
 */
package com.killclog;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.swing.*;
import lombok.*;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;

/**
 * A comparison with a second player, the rival, in red beside the looked-up player's blue: its lookup of the rival's
 * hiscore, log and Combat Achievements, what a comparison holds while it is on, and the values and cards its cells
 * show. It tells the panel each step through {@link Listener}; the panel keeps the widgets, and the looked-up player's
 * results are only read, from {@link LookupSession}.
 *
 * As there, its methods run on the Swing thread, and its own {@link LookupFanout} brings each answer back to it,
 * dropping one a newer lookup has overtaken.
 */
public class ComparisonController
{
	/** What a comparison tells the panel, always on the Swing thread. */
	public interface Listener
	{
		/** A comparison began: the cells are this controller's to draw until it ends. */
		void onComparisonEnter(String redRsn);

		/** The comparison ended: the panel draws one player's cells again. */
		void onComparisonExit();

		/** More of the rival arrived: the cells and the header are drawn again. */
		void onCompareDataReady();

		/** The comparison on screen took a later log for its rival: redraw it as on entry, leaving the search bar be. */
		void onCompareClogReady();

		/** A line about the rival's lookup, for the search status line. */
		void onCompareStatus(String msg, Color color);

		/** The rival's lookup failed: the search row goes back to waiting for a name to compare. */
		void onCompareError(String player, Throwable err);
	}

	/**
	 * The few widgets a comparison writes that {@link Cells} doesn't hold: the header's name and log count, and the
	 * combat and total level cells.
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

		/** The looked-up player's identity as the panel keeps it, or null before it has one. */
		@Nullable
		AccountDisplay primaryIdentity();

		/** Restore the clog info cell to single-player display. */
		void restoreClogCellForCompare(@Nullable ClogResult clog);
	}

	// Constants
	static final String COMPARE_BLUE_HEX = colorHex(KillClogColors.COMPARE_BLUE);
	static final String COMPARE_RED_HEX = colorHex(KillClogColors.COMPARE_RED);
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

	/** One rival as committed; when their Combat Achievements arrive, a new one replaces it whole. */
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

	/** The rival's requests, stamped and timed as the looked-up player's are and counted apart from them. */
	private final LookupFanout fanout;

	private final Map<HiscoreSkill, TooltipData> compareTooltipDataMap = new LinkedHashMap<>();

	private static final String COMPARE_CELL_KEY = "killclog.compare.cell";

	/**
	 * A cell in a comparison: both players' values, and the color each takes while the cell is hovered, held as one
	 * so a cell is never half in a comparison.
	 */
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static final class CompareCell
	{
		private final String blue;
		private final String red;
		private final String blueHover;
		private final String redHover;

		/** The two values stacked, each in its player's color, or its completion color while hovered. */
		String html(boolean hovering)
		{
			return "<html><div style='text-align:center;'>"
				+ "<span style='color:" + (hovering ? blueHover : COMPARE_BLUE_HEX) + ";'>" + blue + "</span><br>"
				+ "<span style='color:" + (hovering ? redHover : COMPARE_RED_HEX) + ";'>" + red + "</span>"
				+ "</div></html>";
		}
	}

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
	// Follows Virtual Levels' virtual total setting; display only, never ranking.
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
	 * Begin the comparison: build each cell's card for the rival and tell the panel ({@link
	 * Listener#onComparisonEnter}). The rival's hiscore and log are committed, whole, before this.
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
				// The red card is an ordinary solo card, built as the blue one is. No red PB source exists, for a hard
				// mode either.
				TooltipData data = tooltipDataBuilder.bossCard(boss, getCompareHiscoreResult(), getCompareClogResult(),
					name -> null, unsyncedCatalog.result());
				if (data != null)
				{
					compareTooltipDataMap.put(boss, data);
				}
			}
		}
	}

	/**
	 * End the comparison and forget the rival. The listener puts the panel back: the search bar, and the cells and
	 * header for one player.
	 */
	public void exit()
	{
		reset();
		listener.onComparisonExit();
	}

	/** End it quietly: nothing asked or answered so far may call back, and the panel is told nothing. */
	void reset()
	{
		comparisonMode = false;
		fanout.invalidate();
		compared = ComparedPlayer.EMPTY;
		compareTooltipDataMap.clear();
	}

	/**
	 * Look up a rival: their hiscore, log and Combat Achievements, each stamped so a newer lookup's answers win, with
	 * every step told to the listener. {@code player} is the name typed in the search bar; {@code localRsn} is the
	 * player logged in, for the search line's wording about yourself; {@code localAccountType} is RuneLite's own type
	 * for them, worn when the rival is you.
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
				setCompareStatus(SearchMessages.COMPARE_SELF_MIRROR, KillClogColors.SELF_COLOR, blueName, redPlayer);
			}
			else
			{
				setCompareStatus(SearchMessages.COMPARE_MIRROR, KillClogColors.TEXT_DIM, blueName, redPlayer);
			}
			return;
		}

		if (blueIsSelf)
		{
			setCompareStatus(SearchMessages.COMPARE_SELF_BLUE, KillClogColors.SELF_COLOR, redPlayer, redPlayer, blueName);
		}
		else if (redIsSelf)
		{
			setCompareStatus(SearchMessages.COMPARE_SELF_RED, KillClogColors.SELF_COLOR, blueName, redPlayer, blueName);
		}
		else
		{
			setCompareStatus(SearchMessages.COMPARE_SEARCH, KillClogColors.TEXT_DIM, blueName, blueName);
		}

		// The Combat Achievements are asked alongside the hiscore and the log.
		fanout.fetchCa(redPlayer, thisLookup, ca ->
		{
			pending.ca = ca;
			if (compared.generation == thisLookup)
			{
				compared = new ComparedPlayer(thisLookup, compared.hiscore, compared.clog, ca, compared.rsn,
					compared.selfType);
				listener.onCompareDataReady();
			}
		});

		fanout.fetchHiscore(redPlayer, null, thisLookup, result ->
		{
			if (result == null)
			{
				fanout.invalidate();
				setCompareStatus(SearchMessages.COMPARE_NOT_FOUND, KillClogColors.COMPARE_RED, redPlayer, redPlayer);
				listener.onCompareError(redPlayer, null);
				return;
			}

			// The log is asked only once the hiscores know the rival: a comparison needs the hiscore anyway, so the
			// wait costs nothing and a mistyped name asks no log site.
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

	/** Which comparison the rival on screen came from: each new comparison has its own. */
	int comparedGeneration()
	{
		return compared.generation;
	}

	private void publish(ComparedPlayer player)
	{
		// TempleOSRS's late answer for the comparison on screen redraws it without entering again, which would clear
		// the search bar the next name may already be typed into.
		boolean late = comparisonMode && compared.generation == player.generation;
		compared = player;
		if (renderTarget != null && player.clog != null)
		{
			renderTarget.preloadClogItemNames(player.clog);
		}
		if (late)
		{
			rebuildTooltipData();
			listener.onCompareClogReady();
		}
		else
		{
			enter();
		}
	}

	private void setCompareStatus(String[] pool, Color color, String singleArg, String secondArg)
	{
		setCompareStatus(pool, color, singleArg, secondArg, singleArg);
	}

	private void setCompareStatus(String[] pool, Color color, String singleArg, String secondArg, String firstArg)
	{
		String template = SearchMessages.pick(pool);
		String msg = template.indexOf("%s") == template.lastIndexOf("%s")
			? String.format(template, singleArg)
			: String.format(template, firstArg, secondArg);
		listener.onCompareStatus(msg, color);
	}

	// Read-only state

	/** Comparing with a rival whose hiscores have answered: the cards show side by side. */
	boolean showsPair()
	{
		return isComparisonMode() && getCompareHiscoreResult() != null;
	}

	/** The rival's own board: the leaderboard selector steps aside in a comparison. */
	@Nullable
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
	 * A card alone, or beside the rival's while comparing, held open over the cell it opened from so what is on it
	 * can be hovered. The comparison is on only once the rival's hiscores are in, so the pair always has both.
	 */
	JToolTip held(JLabel owner, JToolTip solo, Supplier<JToolTip> rival)
	{
		JToolTip tip = showsPair() ? wrapSideBySide(owner, solo, rival.get()) : solo;
		if (owner.getParent() instanceof JPanel)
		{
			tooltipController.keepTooltipOnHover(tip, (JPanel) owner.getParent());
		}
		return tip;
	}

	/** Both players' own cards side by side as one, each under its player's name and color. */
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
		CompareCell cell = new CompareCell(ClogHelper.countText(blueVal), ClogHelper.countText(redVal),
			blueHover != null ? colorHex(blueHover) : COMPARE_BLUE_HEX, redHover != null ? colorHex(redHover) : COMPARE_RED_HEX);
		label.putClientProperty(COMPARE_CELL_KEY, cell);
		label.setText(cell.html(false));
		label.setForeground(null);
		label.setHorizontalAlignment(JLabel.CENTER);
	}

	/** Drop a cell's comparison state without repainting; caller renders solo. */
	public void clearCompareCell(JLabel label)
	{
		label.putClientProperty(COMPARE_CELL_KEY, null);
	}

	/**
	 * While a comparison cell is hovered, its two values wear each player's completion colors in place of blue and
	 * red. Every cell with two values takes it: bosses, activities and skills.
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
		Object cell = label != null ? label.getClientProperty(COMPARE_CELL_KEY) : null;
		if (cell instanceof CompareCell)
		{
			label.setText(((CompareCell) cell).html(hovering));
		}
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
	 * The header in a comparison: both names in their colors, each with its badge. Out of one, the one player's again.
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
			playerName.setForeground(KillClogColors.COMPARE_BLUE);
			tooltipController.setTooltipText(playerName, " ");
			clogInfoLabel.setText(compared.rsn != null ? compared.rsn : "");
			clogInfoLabel.setForeground(KillClogColors.COMPARE_RED);
			tooltipController.setTooltipText(clogInfoLabel, " ");
			clogInfoLabel.setIcon(null);
			clogInfoLabel.setHorizontalAlignment(JLabel.RIGHT);
			// The looked-up player's name keeps the identity the panel settled on, RuneLite's own type included.
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
	 * Every cell drawn for the comparison, or back to the one player's once it is off: bosses, activities, clue tiers,
	 * and the combat and total level cells.
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
			blueHiscore != null ? SkillCellGrid.displayTotalLevel(blueHiscore, virtualTotal) : -1,
			redHiscore != null ? SkillCellGrid.displayTotalLevel(redHiscore, virtualTotal) : -1);
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
