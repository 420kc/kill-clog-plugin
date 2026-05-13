# Refactor cut 3: extract cell-factory tooltip routing from KillClogPanel

## Status: complete

Landed across 2 commits on `refactor-cell-factories` (skeleton + supporting fields 2e74522 → tooltip routing extraction d95eded). The 4 supporting fields (tooltipDataMap, rareTooltips, clueIcons, pvpActivityIcons) moved to `CellFactory`. The 4 routing-bearing cell factory `createToolTip` overrides (clue tier, clue rare, custom rare, boss) collapsed from inline if/else into one-line `cellFactory.buildXTooltip` calls. The single-player `makeSpriteTooltip` rendering stays on the panel via a `SinglePlayerTooltipBuilder` interface injected at construction (panel still owns spriteManager + tooltipController for parent-cell hover wiring). Verification grep returns only the panel's `rareTooltips()` interface method declaration (which delegates correctly to `cellFactory.getRareTooltips()`). Build green incl. checkstyle. Manual smoke test deferred (no RuneLite in the autonomous window).

Branch: `refactor-cell-factories` (off `refactor-comparison-controller` after cut 2 lands)

**Gates on cut 1 + cut 2.** The cell-factory tooltip routing reads primary-side state via `LookupSession` (cut 1) and dispatches to `comparison.makeSpriteTooltip` / `buildClueRare` / `buildCustomRare` (cut 2). Cut 3 cannot land cleanly until both prior cuts ship.

## What's getting cut

After cuts 1 + 2 land, `KillClogPanel.java` is still ~2,500 lines, and roughly 250 of those are anonymous `JLabel` subclass cell factories that override `createToolTip()` to route between single-player and comparison-mode tooltip paths. The factories live around panel lines 1060-1340 and produce 6 distinct cell types:

- `makeActivityCell(HiscoreSkill)` — activity grid cell with sprite icon + tooltip
- `makePvpSummaryCell()` — PvP summary cell with PvpSummaryTooltip
- `makeClueTierCell(HiscoreSkill, int, boolean)` — clue tier cell (easy/medium/hard/elite/master/grandmaster), normal or compact layout
- `makeClueRareCell(String, int, String, boolean)` — 3rd Age + Gilded rare cells
- `makeCustomRareCell(String, int, String, int[])` — Hard/Elite/Master rare cells
- `makeBossCell(HiscoreSkill)` — boss kc cell with comparison-aware tooltip

Each factory creates a `JLabel` subclass with an inline `createToolTip()` override that:
1. Checks `comparison.isComparisonMode()`.
2. If yes, builds dual-player tooltip data (calling `comparison.buildClueRare` / `buildCustomRare` as needed) and returns `comparison.makeSpriteTooltip(...)`.
3. If no, builds single-player tooltip data and returns one of the existing single-player tooltip classes (`ImgTooltip`, `ClueSummaryTooltip`, `PvpSummaryTooltip`, etc.).

This routing is the third concentration of bundled state + behavior in the panel. The factories pull from `tooltipDataMap`, `tooltipDataBuilder`, `clogService`, `lookupSession`, `comparison`, `clueIcons`, `pvpActivityIcons`, `spriteManager`, `itemManager` — every dep used by tooltip rendering.

## Audit numbers (against `KillClogPanel.java` @ refactor-comparison-controller HEAD)

- 6 factory methods, ~250 LOC total
- 4 inline `createToolTip()` overrides that already delegate to `comparison.makeSpriteTooltip` (locked in by cut 2)
- ~30 reads of `tooltipDataMap` + `rareTooltips` + `clueIcons` + `pvpActivityIcons` from inside the overrides
- 6 callsites in the panel constructor (each `makeXCell(...)` returns a `JPanel` that gets added to the layout)

## Target shape

```
CellFactory (new)                          KillClogPanel (slimmer)
├─ deps                                    ├─ UI widgets (primary side, unchanged)
│   ├─ HiscoreService                      ├─ session: LookupSession
│   ├─ ClogService                         ├─ comparison: ComparisonController
│   ├─ ItemManager                         ├─ cellFactory: CellFactory
│   ├─ SpriteManager                       └─ implements CellFactory.Listener (for hover-driven re-renders, if any)
│   ├─ LookupSession (read-only)
│   ├─ ComparisonController (read-only)
│   ├─ TooltipDataBuilder
│   ├─ TooltipController
│   └─ tooltipDataMap (the primary-side per-skill TooltipData store)
├─ factory methods
│   ├─ buildActivityCell(HiscoreSkill)
│   ├─ buildPvpSummaryCell()
│   ├─ buildClueTierCell(HiscoreSkill, int, boolean)
│   ├─ buildClueRareCell(String, int, String, boolean)
│   ├─ buildCustomRareCell(String, int, String, int[])
│   └─ buildBossCell(HiscoreSkill)
└─ tooltip-routing private helpers
    ├─ buildSinglePlayerTooltip(...)
    ├─ buildComparisonTooltip(...)
    └─ wireSpriteAsync(label, spriteId)
```

The `tooltipDataMap` field moves with `CellFactory` (it's the per-skill primary-side tooltip cache). `rareTooltips` also moves (or stays on panel if other code outside the factories reads it — verify during execution).

`clueIcons` + `pvpActivityIcons` (the icon caches loaded from sprites) move too — they're only used inside the factory's tooltip overrides.

## Listener interface (panel implements, optional)

If the cell factories don't trigger any panel-level callbacks beyond what `LookupSession.Listener` + `ComparisonController.Listener` already cover, the `CellFactory.Listener` interface is empty / unnecessary. Verify during execution; preference is no listener if nothing needs it.

## Dependencies cut 3 takes on cut 1 + cut 2

`CellFactory` reads (never writes):
- `lookupSession.getHiscoreResult()` / `getClogResult()` for single-player tooltip data
- `comparison.isComparisonMode()` to choose the routing branch
- `comparison.buildClueRare(name, category)` / `buildCustomRare(name, ids)` for the red-side data
- `comparison.makeSpriteTooltip(owner, blueData, redData, name)` to build the dual tooltip
- `comparison.getCompareTooltipData(skill)` for boss cells

If cut 1 or 2 hasn't landed and you're tempted to pass `KillClogPanel` itself into `CellFactory` as a getter source: don't. That re-couples the three classes and undoes the boundaries the prior cuts established.

## Scope discipline

This refactor is **cell-factory + tooltip routing extraction only**. The following are explicitly NOT in this cut:

- New tooltip features (e.g. "tooltip pinning", "tooltip history")
- Renaming public methods on `KillClogPanel` (zero behavior change rule)
- Touching anything outside the cell-factory + tooltip surface inventory above
- Changing the underlying tooltip classes (`CompareImgTooltip`, `ImgTooltip`, `ClueSummaryTooltip`, etc.)

## Verification gate

Before merging back to dev:

1. `./gradlew build` passes (compileJava + checkstyle + test)
2. `./gradlew run` launches RuneLite with the plugin sideloaded
3. Manual smoke test (all 6 cell types):
   - Hover an activity cell (single-player + comparison mode) → tooltip renders correctly in both modes
   - Hover the PvP summary cell → tooltip shows correct data
   - Hover a clue tier cell (each: easy/medium/hard/elite/master/grandmaster) → tooltip renders
   - Hover the 3rd Age + Gilded clue rare cells → tooltip renders in both modes
   - Hover the Hard/Elite/Master custom rare cells → tooltip renders in both modes
   - Hover a boss cell → tooltip renders in both modes (this is the highest-traffic path; verify dual-player display works)
4. Lint pass:
   ```bash
   grep -nE "\b(tooltipDataMap|rareTooltips|clueIcons|pvpActivityIcons)\b" src/main/java/com/killclog/KillClogPanel.java | grep -v "cellFactory\.\|//"
   ```
   Should print nothing (or print only what intentionally stayed on the panel — verify).
5. Council pre-review (recommended): run review-swarm with shibui + code-simplicity + monolith-hunter on the diff. monolith-hunter should flag the panel as substantially simpler — combined with cut 1 + cut 2, the panel should drop from ~2,900 lines (pre-refactor) to ~1,500 lines (target end state per the cut-2 spec).

## Why this is the smallest cut

~250 LOC moving (vs ~600 in cut 2, ~200 in cut 1). The factories are mostly self-contained (each is one method that returns a `JPanel`); the dependencies on cuts 1 + 2 are read-only via established getters; the inline overrides already delegate to `comparison.X` for the dual-player paths.

**Dedicated session: 2-3 hours.** Treat it the way cuts 1 + 2 were treated. Don't rush it.

## After cut 3

`KillClogPanel.java` should sit around 1,400-1,500 lines, all of it actual panel UI work + the small amount of layout glue between the three controllers (`LookupSession`, `ComparisonController`, `CellFactory`). The orchestration (lookup, comparison, tooltip routing) lives in dedicated controllers, each with a clear boundary, each independently testable.

The Plugin Hub install base (~7,000 active users as of 2026-05-13) sees zero behavior change across all three cuts. Every commit on the refactor branches preserves the smoke-test contract; the only thing that's changed is the internal structure.

## Pre-flight (do not start cut 3 if any of these fail)

```bash
cd ~/plugins/kcpdev

# 1. Cut 1 must have landed
git log --oneline | grep "refactor-cut-1: doLookup body migrated" | head -1

# 2. Cut 2 must have landed
git log --oneline | grep "refactor-cut-2: mark complete" | head -1

# 3. Build clean
./gradlew build

# 4. Manual smoke test of cut 1 (4 paths) + cut 2 (6 paths) all pass.
```

If any of these fail, stop. Resolve the prior cuts first.

## Related

- `REFACTOR-CUT-1.md` — design doc for the LookupSession extraction (commit f172072)
- `REFACTOR-CUT-1-EXECUTION.md` — step-by-step recipe for cut 1
- `REFACTOR-CUT-2.md` — design doc for the ComparisonController extraction (commit ad42218)
- `REFACTOR-CUT-2-EXECUTION.md` — step-by-step recipe for cut 2
- `src/main/java/com/killclog/KillClogPanel.java` — the source file being decomposed
- `src/main/java/com/killclog/LookupSession.java` — cut 1's destination class
- `src/main/java/com/killclog/ComparisonController.java` — cut 2's destination class
