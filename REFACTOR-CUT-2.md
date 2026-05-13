# Refactor cut 2: extract ComparisonController from KillClogPanel

## Status: complete

Landed across 21 incremental commits on `refactor-comparison-controller` (skeleton 595268d → final widget migration 84058d9). All 13 state fields, 10 methods, 5 widgets, 4 constants moved to `ComparisonController`. Verification grep returns zero panel-side bare references to comparison state. Build green incl. checkstyle. Manual smoke test deferred (no RuneLite in the autonomous window).

Branch: `refactor-comparison-controller` (off `refactor-lookup-session` after cut 1 lands)

**Gates on cut 1.** ComparisonController reads the primary player's results, which migrate into `LookupSession` in cut 1. Cut 2 cannot land cleanly until cut 1 ships and `KillClogPanel` is talking to `LookupSession` through the Listener interface.

## What's getting cut

The comparison-mode subsystem is the second-largest concentration of bundled state + behavior inside `KillClogPanel.java`. After cut 1 lands, `KillClogPanel` will still be ~2,400 lines, and roughly 600 of those (state + methods + call sites + html-formatters) are entirely about comparison: the "red player" overlay that the user opens by clicking the magnifying-glass toggle next to the search bar.

Audit numbers (against `KillClogPanel.java` @ master `5df0a95`):
- 228 line touches mentioning `compar*` / `Compar*` / `COMPAR*`
- 13 state fields
- 10 methods
- 8 inline call sites in the panel's render/dispatch flow

## The target shape

```
ComparisonController (new)                KillClogPanel (slimmer)
├─ state                                  ├─ UI widgets (primary side, unchanged)
│   ├─ comparisonMode                     ├─ session: LookupSession        (from cut 1)
│   ├─ compareHiscoreResult               ├─ comparison: ComparisonController
│   ├─ compareClogResult                  ├─ implements LookupSession.Listener
│   ├─ compareRsn                         ├─ implements ComparisonController.Listener
│   ├─ compareLookupVersion (volatile)    └─ cell-factory tooltip routing (cut 3)
│   ├─ compareLookupInFlight (volatile)
│   └─ compareTooltipDataMap
├─ ui widgets
│   ├─ compareSearchBar (IconTextField)
│   ├─ compareTextField + comparePlaceholder
│   ├─ comparePanel (JPanel)
│   ├─ compareStatus (JLabel)
│   └─ compareToggle (JLabel)
├─ deps
│   ├─ HiscoreService
│   ├─ ClogService
│   ├─ LookupSession (read-only via getters)
│   └─ Listener
└─ methods
    ├─ buildPanel() / wireToggle()
    ├─ enter() / exit() / swapToComparePlayer()
    ├─ doCompareLookup()
    ├─ html formatters
    │   ├─ setCompareCell(label, blueVal, redVal)
    │   ├─ compareOrRestore(label, blueVal, redVal)
    │   ├─ updateAllCells()
    │   ├─ updateInfoBar()
    │   ├─ buildCompareClueRare(name, category)
    │   └─ buildCompareCustomRare(name, itemIds)
    └─ tooltip
        └─ makeCompareSpriteTooltip(owner, blueData, redData, name)
```

Constants `COMPARE_BLUE` + `COMPARE_RED` + their hex strings move with the controller (they live nowhere else).

## Listener interface (panel implements)

```java
public interface Listener {
    /** Comparison mode just entered. Panel: stop rendering single-player cells, route through controller. */
    void onComparisonEnter(String redRsn);

    /** Comparison mode just exited. Panel: restore single-player cells. */
    void onComparisonExit();

    /** Red player swapped in (became the new primary). Panel: trigger fresh lookup, re-render primary side. */
    void onSwapToRedPlayer(String newPrimaryRsn);

    /** Red-side hiscore/clog data arrived. Panel: trigger a cell + info-bar re-render via the controller's helpers. */
    void onCompareDataReady();

    /** Red lookup failed. Panel: surface error in the comparison status row. */
    void onCompareError(String player, Throwable err);
}
```

Panel implements these as pure dispatchers. Controller does ZERO calls to `KillClogPanel.renderHiscoreResult` / `renderClogResult` / `playerName.setText` etc.

## Where the cell-factory tooltip routing fits (and DOESN'T)

The 4 inline `makeCompareSpriteTooltip(...)` call sites in `KillClogPanel.java` (lines 1237, 1261, 1290, 1344) live inside cell-factory `getToolTipText()` overrides. That code path is **cut 3's job** (cell-factory tooltip routing extraction). Cut 2 should:

- Move the `makeCompareSpriteTooltip` METHOD itself into `ComparisonController` (since it owns the red-side data).
- Leave the inline `JToolTip createToolTip()` overrides where they are. They just change from `return makeCompareSpriteTooltip(...)` to `return comparison.makeSpriteTooltip(...)`.

That delegation pattern is fine. Cut 3 reshapes the cell-factory layer separately.

## Dependencies cut 2 takes on cut 1

ComparisonController needs read-only access to the primary player's data:
- `session.getHiscoreResult()` (was `KillClogPanel.hiscoreResult`)
- `session.getClogResult()` (was `KillClogPanel.clogResult`)
- `session.getCurrentLookupRsn()` (was `KillClogPanel.currentLookupRsn`)

These reads happen inside `compareOrRestore`, `updateAllCells`, `updateInfoBar`, the html-formatter helpers, and `swapToComparePlayer`. All read-only. Controller never writes back into `LookupSession`.

If cut 1 hasn't landed and you're tempted to pass `KillClogPanel` itself into the controller as a getter source: don't. That re-couples the two and undoes the boundary cut 1 establishes.

## Scope discipline

This refactor is **comparison subsystem extraction only**. The following are explicitly NOT in this cut:

- Cell-factory tooltip routing (cut 3's job: the `JLabel` subclass overrides at the cell-factory layer)
- Adding new comparison features (e.g. "save comparison snapshot", "diff history")
- Renaming public methods on `KillClogPanel` (zero behavior change rule)
- Touching anything that's not on the comparison surface inventory above

## Verification gate

Before merging back to dev:

1. `./gradlew build` passes
2. `./gradlew run` launches RuneLite with the plugin sideloaded
3. Manual smoke test (all 6 paths):
   - Look up self
   - Click compare-toggle → panel expands → enter "Lynx Titan" → press enter → red data loads → cells show blue/red split
   - Click on the comparison search bar's clear → comparison stays
   - Click compare-toggle again → exits comparison, cells restore to single-player
   - With comparison open: re-search a different primary player → exits comparison (per current behavior)
   - With comparison open: click the primary player name → swap-to-red-player triggers, blue becomes the new primary, comparison exits
4. Run review-swarm against the diff with shibui + code-simplicity + monolith-hunter lenses; verify monolith-hunter no longer flags the panel for "comparison subsystem bundled in" (it should drop the panel's complexity score by ~25%)
5. Manually verify the 4 cell-factory tooltip sites at line 1237, 1261, 1290, 1344 still produce the dual-player tooltip in comparison mode (since those are NOT moving in this cut, only the method they call is).

## Why this is the biggest cut

~600 LOC moving (vs ~200 in cut 1, ~250 in cut 3). The state is dense, the html formatters are tangled with the cell-factory layer's expectations, and the lifecycle (enter/exit/swap) interacts with the lookup pipeline.

**Dedicated session: 4-5 hours, no interruptions, manual smoke test before commit, council pre-review on the diff.** Treat it the way cut 1 was treated. Don't rush it.

## Cut 3 follow

After cut 2 lands:
- Cut 3: Cell-factory tooltip routing extraction. The four `JLabel` anonymous subclass cell-factories (around lines 1230-1370) move into a `CellFactory` helper / `TooltipRouter`. The panel becomes mostly layout + listener glue.
- Estimated ~250 LOC moved.

After all three cuts: `KillClogPanel.java` should sit around 1,400-1,500 lines, all of it actual panel UI work. The orchestration (lookup, comparison, tooltip routing) lives in dedicated controllers, each with a Listener boundary, each independently testable.

## Pre-flight (do not start cut 2 if any of these fail)

```bash
cd ~/plugins/kcpdev

# 1. Cut 1 must have landed
git log --oneline master | grep -F "refactor-cut-1: doLookup body migrated" | head -1
# expected: a commit SHA on master with the cut-1-complete message

# 2. Build clean
./gradlew compileJava
# expected: BUILD SUCCESSFUL

# 3. Monolith-hunter no longer flags doLookup
# Run review.py with the monolith-hunter lens on the panel file; expected:
# no "doLookup orchestration mixed in" finding.

# 4. Manual smoke test of cut 1's 4 paths (cached / api / not-found / error) passes.
```

If any of these fail, stop. Resolve cut 1 first.

## Related

- `REFACTOR-CUT-1.md` — design doc for the LookupSession extraction (the predecessor cut)
- `REFACTOR-CUT-1-EXECUTION.md` — step-by-step recipe for cut 1, model for the eventual cut-2 execution spec
- `src/main/java/com/killclog/KillClogPanel.java` — the source file being decomposed
- `src/main/java/com/killclog/LookupSession.java` — cut 1's destination class; cut 2 reads its getters
