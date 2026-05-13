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
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.hiscore.HiscoreSkill;

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

		Map<String, javax.swing.JLabel> activityLabels();

		javax.swing.JLabel combatCell();

		javax.swing.JLabel totalLvlCell();

		javax.swing.JLabel pvpSummaryCell();

		javax.swing.JLabel playerName();

		javax.swing.JLabel clogInfoLabel();

		void updateInfoIcon(@Nullable AccountType type);

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
		KillClogConfig config, LookupSession lookupSession, Listener listener)
	{
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.config = config;
		this.lookupSession = lookupSession;
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

	// ── Lifecycle ─────────────────────────────────────────────────────────

	/**
	 * Enter comparison mode for {@code redRsn}. Body migrates from
	 * {@code KillClogPanel.enterComparisonMode} in cut 2 step 3.
	 */
	public void enter(String redRsn)
	{
		throw new UnsupportedOperationException("ComparisonController.enter not yet wired; tracked in REFACTOR-CUT-2-EXECUTION.md");
	}

	/**
	 * Exit comparison mode and clear red-side state. Body migrates from
	 * {@code KillClogPanel.exitComparisonMode} in cut 2 step 3.
	 */
	public void exit()
	{
		throw new UnsupportedOperationException("ComparisonController.exit not yet wired; tracked in REFACTOR-CUT-2-EXECUTION.md");
	}

	/**
	 * Swap the red player into the primary slot. Calls
	 * {@link LookupSession#adoptState} to push the red results into the
	 * session, then fires {@code onSwapToRedPlayer} so the panel can
	 * re-render. Body migrates from {@code KillClogPanel.swapToComparePlayer}
	 * in cut 2 step 3.
	 */
	public void swapToComparePlayer()
	{
		throw new UnsupportedOperationException("ComparisonController.swapToComparePlayer not yet wired; tracked in REFACTOR-CUT-2-EXECUTION.md");
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
}
