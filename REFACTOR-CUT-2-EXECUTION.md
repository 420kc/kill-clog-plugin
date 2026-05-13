# Refactor cut 2 execution spec

**Read this first.** You are a fresh Code instance picking up cut 2 of the Kill Clog refactor. The architecture is locked in `REFACTOR-CUT-2.md`. Cut 1 has landed (`refactor-cut-1: doLookup body migrated to LookupSession.start()` - see `git log`); `KillClogPanel` is now talking to `LookupSession` through the `Listener` interface and reads primary-player state via `lookupSession.getX()` getters. **Your job is to extract the comparison subsystem (~600 LOC, 13 fields, 10 methods, 5 widgets) out of `KillClogPanel.java` into a new `ComparisonController` class, with the panel reduced to a pure `ComparisonController.Listener` implementation.** Zero behavior change. One atomic commit per logical step (skeleton, body migration, reference-site cleanup), mirroring how cut 1 was sequenced.

This spec is dense by design. Follow it exactly. Don't make architectural judgment calls - those are baked in already.

## Pre-flight

```bash
cd ~/plugins/kcpdev
git checkout refactor-lookup-session
git log --oneline | grep "refactor-cut-1: doLookup body migrated" | head -1
# expected: f172072 (or whatever sha cut 1's final commit landed on) - body migrated to LookupSession.start

git checkout -b refactor-comparison-controller
./gradlew build
# expected: BUILD SUCCESSFUL (compileJava + checkstyle + test)

ls REFACTOR-CUT-2.md REFACTOR-CUT-2-EXECUTION.md
# expected: both present
```

If any of these fail, stop. Tell dyl what's wrong.

## The migration in 7 steps

### Step 1 - Skeleton: create `ComparisonController.java` with state + Listener interface only

In `src/main/java/com/killclog/ComparisonController.java`, declare:

- Package + license header (mirror `LookupSession.java`).
- Class doc: brief - owns the red-side comparison subsystem; UI is downstream via `Listener`; reads primary-side state via `LookupSession` getters; never writes to the session.
- `public interface Listener` with the 5 methods from `REFACTOR-CUT-2.md`:
  - `void onComparisonEnter(String redRsn);`
  - `void onComparisonExit();`
  - `void onSwapToRedPlayer(String newPrimaryRsn);`
  - `void onCompareDataReady();`
  - `void onCompareError(String player, Throwable err);`
- State fields lifted from panel (lines 232-244 currently):
  - `private boolean comparisonMode;`
  - `@Nullable private HiscoreResult compareHiscoreResult;`
  - `@Nullable private ClogResult compareClogResult;`
  - `@Nullable private String compareRsn;`
  - `private volatile int compareLookupVersion = 0;`
  - `private volatile boolean compareLookupInFlight = false;`
  - `private final Map<HiscoreSkill, TooltipData> compareTooltipDataMap = new LinkedHashMap<>();`
- Constants (move with the controller - they live nowhere else):
  - `static final Color COMPARE_BLUE = new Color(91, 164, 207);`
  - `static final Color COMPARE_RED = new Color(224, 86, 86);`
  - `static final String COMPARE_BLUE_HEX = String.format(...);`
  - `static final String COMPARE_RED_HEX = String.format(...);`
- Deps stored as final fields:
  - `HiscoreService hiscoreService`
  - `ClogService clogService`
  - `KillClogConfig config`
  - `LookupSession lookupSession` (read-only, for getters)
  - `Listener listener`
- Read-only getters for: `isComparisonMode()`, `getCompareHiscoreResult()`, `getCompareClogResult()`, `getCompareRsn()`, `getCompareLookupVersion()`, `getCompareTooltipData(HiscoreSkill)`, `getCompareTooltipDataMap()` (read-only `Collections.unmodifiableMap` view if the panel needs to iterate).
- Stub method declarations (bodies throw `UnsupportedOperationException`; bodies arrive in step 4):
  - `public void enter(String redRsn)`
  - `public void exit()`
  - `public void swapToComparePlayer()`
  - `public void doCompareLookup(String player)`

Commit this skeleton:

```
refactor-cut-2: ComparisonController skeleton + Listener interface. Owns
red-side comparison state (13 fields, COMPARE_BLUE / COMPARE_RED constants,
compareTooltipDataMap) + emits typed events to a UI-agnostic Listener. enter
/ exit / swapToComparePlayer / doCompareLookup are stubbed pending the body
migration in the next commit. read-only state via getters; controller never
writes to LookupSession (read-only dep). compiles clean; KillClogPanel
unchanged in this commit (additive only). next commit migrates the bodies +
the ~50 reference sites.
```

### Step 2 - Update `ComparisonController` constructor signature + scaffold the panel side

Constructor:
```java
public ComparisonController(HiscoreService hiscoreService, ClogService clogService,
    KillClogConfig config, LookupSession lookupSession, Listener listener)
```

In `KillClogPanel.java`:
- Add `implements ComparisonController.Listener` to the class declaration (alongside the existing `LookupSession.Listener`).
- Add a field after `lookupSession`: `private final ComparisonController comparison;`
- Instantiate in the constructor right after `this.lookupSession = ...`:
  ```java
  this.comparison = new ComparisonController(hiscoreService, clogService, config, lookupSession, this);
  ```
- Add the 5 `ComparisonController.Listener` stub method bodies at the bottom of the class (after the `LookupSession.Listener` block). Empty bodies for now; populated in step 5.

Build clean. Commit:

```
refactor-cut-2: scaffold ComparisonController into KillClogPanel. panel now
implements ComparisonController.Listener with stub bodies, holds the
controller as a final field. controller stays dormant: enter / exit /
doCompareLookup all still throw, the existing comparison code in the panel
owns the live behavior. zero behavior change. unblocks the body-migration
commit.
```

### Step 3 - Implement `ComparisonController` body

Translate the bodies of these panel methods into the controller, line-for-line:

| Panel method (current line) | Controller destination | Notes |
|---|---|---|
| `exitComparisonMode` (line 1467) | `exit()` | UI side effects fan out to `listener.onComparisonExit()` |
| `swapToComparePlayer` (line 1485) | `swapToComparePlayer()` | The `lookupSession.adoptState(swapHiscore, swapClog, swapName)` call STAYS - controller calls it directly on the session ref it holds. Then fires `listener.onSwapToRedPlayer(swapName)` for the panel-side UI updates. |
| `doCompareLookup` (line 1534) | `doCompareLookup(String player)` | Receives `player` from listener; same async pipeline as the original; UI dispatch via `listener.onCompareDataReady()` on success, `listener.onCompareError(player, ex)` on fail. The version-stamp + in-flight guard pattern mirrors `LookupSession.start()` - preserve `compareLookupVersion++` and `compareLookupInFlight = true/false` semantics exactly. |
| `setCompareCell` (line 1770) | `setCompareCell(...)` | Pure html formatter; moves wholesale. |
| `compareOrRestore` (line 1797) | `compareOrRestore(...)` | Pure html formatter; reads primary side via `lookupSession.getHiscoreResult()` etc. - no panel reach-through. |
| `updateAllCellsForComparison` (line 1833) | `updateAllCells(...)` | Renamed (drop `ForComparison` suffix - the controller's name implies it). Takes the panel's cell maps as parameters or the panel calls it via the listener path. **Decision: pass the cell maps + label refs the controller needs as constructor args (or as a `CellRenderTarget` interface the panel implements), so the controller can call back into the panel's labels without holding a panel reference.** |
| `updateInfoBarForComparison` (line 1694) | `updateInfoBar(...)` | Same renaming + same parameter-passing decision. |
| `buildCompareClueRare` (line 1889) | `buildClueRare(...)` | Pure data builder; reads `compareClogResult`. |
| `buildCompareCustomRare` (line 1896) | `buildCustomRare(...)` | Same. |
| `makeCompareSpriteTooltip` (line 1045) | `makeSpriteTooltip(...)` | Pure constructor of a `JToolTip`; moves wholesale. |
| `buildCompareSearch` (line 1394) | `buildSearchUi(...)` | Returns the `JPanel` containing the comparison search bar. Panel still adds it to layout via the result; widgets `compareSearchBar`, `compareTextField`, `comparePanel`, `compareStatus`, `compareToggle` move with the controller. |

For the cell-rendering hooks (`updateAllCells`, `updateInfoBar`, `compareOrRestore`, `setCompareCell`), define a small `CellRenderTarget` interface the panel implements:

```java
public interface CellRenderTarget {
    Map<HiscoreSkill, JLabel> bossLabels();
    Map<String, JLabel> activityLabels();
    JLabel combatCell();
    JLabel totalLvlCell();
    JLabel pvpSummaryCell();
    JLabel playerName();
    JLabel clogInfoLabel();
    void updateInfoIcon(@Nullable AccountType type);
    Color getInfoColor();
}
```

Pass the target into the controller constructor or as a setter (mirrors how `LookupSession` accepted late-bound `NameAutocompleter`). Controller then calls `target.bossLabels().get(skill).setText(...)` instead of reaching into `KillClogPanel` directly.

Skip these from the comparison methods (panel keeps them):
- The 4 inline `makeCompareSpriteTooltip` call sites at panel lines 1233, 1257, 1286, 1344 - those are inside cell-factory `getToolTipText()` overrides; **cut 3's job**. They change from `makeCompareSpriteTooltip(...)` to `comparison.makeSpriteTooltip(...)` (delegation), nothing else.
- `compareToggle` mouse listener wiring (panel lines 375-401) - the toggle is a panel widget; the click handler calls `comparison.enter(rsn)` or `comparison.exit()` instead of the inline body.

Commit when build clean:

```
refactor-cut-2: implement ComparisonController body. enter / exit /
swapToComparePlayer / doCompareLookup migrated from KillClogPanel with
mechanical translation: ui side effects fan out to listener events, html
formatters + cell rendering route through a CellRenderTarget interface the
panel implements (so controller never reaches into the panel directly).
swap path calls lookupSession.adoptState directly. controller is still
unreachable from the panel (the existing comparison methods + call sites
are untouched), so this commit is zero-behavior-change scaffold. next
commit: replace the panel's comparison entry points to delegate, populate
listener stub bodies, migrate the 50ish reference sites.
```

### Step 4 - Replace the panel's comparison entry points

In `KillClogPanel.java`:
- The `compareToggle` click handler at line 375: replace inline enter/exit logic with `comparison.enter(...)` or `comparison.exit()`.
- The `playerName` click handler at line 628 (the `swapToComparePlayer` trigger): replace with `comparison.swapToComparePlayer()`.
- The `compareSearchBar` action listener that calls `doCompareLookup`: replace with `comparison.doCompareLookup(searchBar.getText().trim())` (or whatever the input source is - confirm against the existing wiring).
- Delete the now-orphaned `enterComparisonMode` (if it exists), `exitComparisonMode`, `swapToComparePlayer`, `doCompareLookup`, `setCompareCell`, `compareOrRestore`, `updateAllCellsForComparison`, `updateInfoBarForComparison`, `buildCompareClueRare`, `buildCompareCustomRare`, `makeCompareSpriteTooltip`, `buildCompareSearch` methods from the panel - they live on the controller now.

### Step 5 - Populate panel's `ComparisonController.Listener` stub bodies

| Listener method | Body |
|---|---|
| `onComparisonEnter(redRsn)` | Show comparison panel widget, update `compareToggle` icon to "on" state, kick off cell + info-bar render via `comparison.updateAllCells(this)` + `comparison.updateInfoBar(this)`. |
| `onComparisonExit()` | Restore single-player cells (call existing `renderHiscoreResult` + `renderClogResult` with primary-side data from `lookupSession`), update `compareToggle` icon to "off". |
| `onSwapToRedPlayer(newPrimaryRsn)` | The `lookupSession.adoptState` already fired in the controller; here the panel re-renders primary side: `renderHiscoreResult(lookupSession.getHiscoreResult(), newPrimaryRsn, false, null)` etc. Then `searchBar.setText(newPrimaryRsn)`. |
| `onCompareDataReady()` | Re-render: `comparison.updateAllCells(this); comparison.updateInfoBar(this); revalidate(); repaint();` |
| `onCompareError(player, err)` | Surface error in `compareStatus` widget (now owned by controller; panel asks: `comparison.getStatusLabel().setText("...")` - or controller exposes a `setStatusError(String)` helper). |

### Step 6 - Migrate the ~50 reference sites in `KillClogPanel.java`

```bash
grep -nE "\b(comparisonMode|compareHiscoreResult|compareClogResult|compareRsn|compareLookupVersion|compareLookupInFlight|compareTooltipDataMap)\b" src/main/java/com/killclog/KillClogPanel.java
```

For each match (excluding the now-deleted method bodies):
- `comparisonMode` → `comparison.isComparisonMode()`
- `compareHiscoreResult` → `comparison.getCompareHiscoreResult()`
- `compareClogResult` → `comparison.getCompareClogResult()`
- `compareRsn` → `comparison.getCompareRsn()`
- `compareLookupVersion` → `comparison.getCompareLookupVersion()` (read-only; should not be needed externally - controller manages it internally)
- `compareLookupInFlight` → unused outside controller after migration; remove any panel-side reads
- `compareTooltipDataMap.get(skill)` → `comparison.getCompareTooltipData(skill)`
- `compareTooltipDataMap.clear()` → controller exposes `clearCompareTooltips()` and the panel calls that

Use the same word-boundary sed pattern that worked for cut 1:
```bash
sed -i \
  -e 's/\bcomparisonMode\b/comparison.isComparisonMode()/g' \
  -e 's/\bcompareHiscoreResult\b/comparison.getCompareHiscoreResult()/g' \
  -e 's/\bcompareClogResult\b/comparison.getCompareClogResult()/g' \
  -e 's/\bcompareRsn\b/comparison.getCompareRsn()/g' \
  src/main/java/com/killclog/KillClogPanel.java
```

(Watch out: `compareTooltipDataMap` and `compareLookupVersion` need site-specific edits, not a blind sed - they often appear as method calls or assignments that don't translate cleanly to a getter.)

### Step 7 - Remove the comparison state fields + widgets + constants from `KillClogPanel`

Delete from the panel (lines 228-244 currently):
- `COMPARE_BLUE`, `COMPARE_RED`, `COMPARE_BLUE_HEX`, `COMPARE_RED_HEX` (move with controller)
- `comparisonMode`, `compareHiscoreResult`, `compareClogResult`, `compareRsn`, `compareLookupVersion`, `compareLookupInFlight`, `compareTooltipDataMap`
- `compareSearchBar`, `compareTextField`, `comparePlaceholder`, `comparePanel`, `compareStatus`, `compareToggle` (move with controller)

Keep the toggle's mouse listener wiring on the panel - but it now calls `comparison.toggleEnter()` (or whatever the controller exposes for the toggle interaction).

After step 7, the panel should have ZERO references to bare comparison state names except via `comparison.X()` method calls.

## Verification gates

After step 7, before commit:

1. **Build:**
   ```bash
   ./gradlew build
   ```
   Must show BUILD SUCCESSFUL. If errors, you missed a reference site in step 6 or have a type mismatch.

2. **Manual smoke test (6 paths from REFACTOR-CUT-2.md):**
   ```bash
   ./gradlew run
   ```
   - Look up self
   - Compare-toggle → enter "Lynx Titan" → press enter → red data loads → cells show blue/red split
   - Click compare search bar's clear → comparison stays
   - Compare-toggle again → exits, cells restore to single-player
   - With comparison open: re-search a different primary → exits comparison
   - With comparison open: click primary player name → swap-to-red triggers, blue becomes new primary, comparison exits

3. **Lint pass:**
   ```bash
   grep -nE "\b(comparisonMode|compareHiscoreResult|compareClogResult|compareRsn|compareLookupVersion|compareLookupInFlight|compareTooltipDataMap)\b" src/main/java/com/killclog/KillClogPanel.java | grep -v "comparison\.\|//"
   ```
   Should print NOTHING. Any remaining reference is a missed migration.

4. **Council pre-review (recommended):**
   ```bash
   cd ~/auto-wake-workspace/infra/review-swarm
   git -C ~/plugins/kcpdev stash push --keep-index
   python3 review.py refactor-comparison-controller~1..HEAD --agents shibui,code-simplicity,monolith-hunter
   git -C ~/plugins/kcpdev stash pop
   ```
   monolith-hunter should drop the panel's complexity score by ~25% and stop flagging "comparison subsystem bundled in".

5. **Cell-factory tooltip sites still work:** manually verify the 4 sites at lines 1233, 1257, 1286, 1344 (now calling `comparison.makeSpriteTooltip(...)`) still produce the dual-player tooltip in comparison mode. They are NOT moving in this cut.

## Commit message template (final body-migration commit)

```
refactor-cut-2: comparison subsystem extracted to ComparisonController.
every reference site in KillClogPanel.java now reads through
comparison.getX() getters; panel implements ComparisonController.Listener
and dispatches comparison ui updates via onComparisonEnter /
onComparisonExit / onSwapToRedPlayer / onCompareDataReady /
onCompareError. controller owns the 13 state fields, 10 methods, 5
widgets, 4 constants, and 600 lines that previously lived in the panel.
zero behavior change. version-stamp invariants preserved on the
compareLookupVersion counter (volatile + edt-wrapped listener calls).
panel implements CellRenderTarget so the controller can write to the
panel's cells without holding a panel reference. cell-factory tooltip
call sites at lines 1233, 1257, 1286, 1344 just delegate via
comparison.makeSpriteTooltip(); the cell-factory layer stays in the
panel as cut 3 territory. build clean, ready for manual smoke test.
```

## Fail-safe

If at any step the build breaks or smoke test fails:
1. `git stash` your in-flight changes
2. tell dyl what broke
3. don't push, don't merge

## When you're done

1. Verify all 5 verification gates passed
2. Commit with the template message
3. Update `REFACTOR-CUT-2.md` with `## Status: complete (commit <sha>)`
4. Telegram dyl via heartbeat bot: `REFACTOR-CUT-2 LANDED: <sha>, build green, smoke 6/6`
5. Cut 3 (cell-factory tooltip routing) is the next refactor - design doc `REFACTOR-CUT-3.md` is the prerequisite.
