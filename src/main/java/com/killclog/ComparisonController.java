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
import javax.annotation.Nullable;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ColorScheme;

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
	 * Cell-render target the controller writes to when updating compare cells
	 * + the info bar. The panel implements this so the controller can render
	 * without holding a panel reference (preserves the boundary).
	 */
	public interface CellRenderTarget
	{
		Map<HiscoreSkill, javax.swing.JLabel> bossLabels();

		Map<HiscoreSkill, javax.swing.JLabel> activityLabels();

		javax.swing.JLabel combatCell();

		javax.swing.JLabel totalLvlCell();

		javax.swing.JLabel pvpSummaryCell();

		javax.swing.JLabel playerName();

		javax.swing.JLabel clogInfoLabel();

		void updateInfoIcon(AccountType type);

		Color getInfoColor();
	}

	// ── Constants ─────────────────────────────────────────────────────────
	static final Color COMPARE_BLUE = new Color(91, 164, 207);
	static final Color COMPARE_RED = new Color(224, 86, 86);
	static final String COMPARE_BLUE_HEX = String.format("#%06x", COMPARE_BLUE.getRGB() & 0xFFFFFF);
	static final String COMPARE_RED_HEX = String.format("#%06x", COMPARE_RED.getRGB() & 0xFFFFFF);

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

	/**
	 * Mirror panel-side compare state writes into the controller during the
	 * cut-2 transition. Once the panel-side fields are removed and all writers
	 * route through the controller's lifecycle, this bridge becomes dead and
	 * gets dropped. Until then, the controller's read-only API
	 * ({@link #isComparisonMode}, {@link #getCompareHiscoreResult}, etc.)
	 * needs the state populated so any caller switched over to
	 * {@code comparison.X} sees consistent data.
	 */
	public void syncCompareState(boolean comparisonMode,
		@Nullable HiscoreResult compareHiscoreResult,
		@Nullable ClogResult compareClogResult,
		@Nullable String compareRsn)
	{
		this.comparisonMode = comparisonMode;
		this.compareHiscoreResult = compareHiscoreResult;
		this.compareClogResult = compareClogResult;
		this.compareRsn = compareRsn;
	}

	/** Per-field setter used by the panel-side doCompareLookup pipeline during the cut-2 transition; goes away once doCompareLookup itself migrates. */
	public void setCompareHiscoreResult(@Nullable HiscoreResult result)
	{
		this.compareHiscoreResult = result;
	}

	/** Per-field setter used by the panel-side doCompareLookup pipeline during the cut-2 transition; goes away once doCompareLookup itself migrates. */
	public void setCompareClogResult(@Nullable ClogResult result)
	{
		this.compareClogResult = result;
	}

	/** Per-field setter used by the panel-side doCompareLookup pipeline during the cut-2 transition; goes away once doCompareLookup itself migrates. */
	public void setCompareRsn(@Nullable String rsn)
	{
		this.compareRsn = rsn;
	}

	/** Transitional setter for the in-flight flag; goes away when doCompareLookup migrates. */
	public void setCompareLookupInFlight(boolean inFlight)
	{
		this.compareLookupInFlight = inFlight;
	}

	/** Increment the compare lookup version stamp and return the new value. */
	public int bumpCompareLookupVersion()
	{
		return ++compareLookupVersion;
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
	 * Begin a red-side lookup for {@code player}. Body migrates from
	 * {@code KillClogPanel.doCompareLookup} in cut 2 step 3.
	 */
	public void doCompareLookup(String player)
	{
		throw new UnsupportedOperationException("ComparisonController.doCompareLookup not yet wired; tracked in REFACTOR-CUT-2-EXECUTION.md");
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

	// ── Pure helpers (dormant in this commit) ─────────────────────────────
	// These mirror panel methods of the same name. Until the panel callsites
	// switch to comparison.X(), the panel's copy is what runs at runtime;
	// these are the migration target for cut 2 step 4.

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
