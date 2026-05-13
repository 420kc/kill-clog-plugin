# Refactor cut 1 execution spec

**Read this first.** You are a fresh Code instance picking up cut 1 of the Kill Clog refactor. The architecture is locked in `REFACTOR-CUT-1.md`. The `LookupSession.java` skeleton (commit `41cf365` on `refactor-lookup-session` branch) exists with state fields, Listener interface, getters, and a stubbed `start()` method. **Your job is to migrate the body of `KillClogPanel.doLookup()` into `LookupSession.start()` and rewire every reference site in `KillClogPanel.java`.** Zero behavior change. One atomic commit.

This spec is dense by design. Follow it exactly. Don't make architectural judgment calls - those are baked in already.

## Pre-flight

```bash
cd ~/plugins/kcpdev
git checkout refactor-lookup-session
git log --oneline -3
# expected last commit: 41cf365 refactor-cut-1: LookupSession skeleton
./gradlew compileJava
# expected: BUILD SUCCESSFUL
```

If any of these fail, stop. Tell dyl what's wrong.

## The migration in 7 steps

### Step 1 - Implement `LookupSession.start()` body

In `src/main/java/com/killclog/LookupSession.java`, replace the `throw new UnsupportedOperationException(...)` with the body of the current `KillClogPanel.doLookup()` (lines 1998-2160-ish), translated mechanically:

- Every call to `setSearchStatus(msg, color)` → `listener.onLookupStart(player, isSelf, isFirstSelfGreeting)` (only at the start) OR fold into the relevant `onCachedResult` / `onHiscoreResult` / `onNotFound` / `onError` call
- `searchBar.setIcon(...)` → removed from session; handled by listener in its onX method
- `resetAllLabels()` → call moves to listener's `onLookupStart`
- `renderHiscoreResult(...)` → removed; listener handles via `onHiscoreResult` / `onCachedResult`
- `renderClogResult(...)` → listener handles via `onClogResult` / `onCachedResult`
- `nameAutocompleter.addToSearchHistory(player)` → KEEP in session (it's not UI, it's a service call); but consider - actually it IS UI-coupled (the autocomplete is a widget). Move to listener via a new event OR call directly from session if `NameAutocompleter` is passed in. **Decision: pass `NameAutocompleter nullable ref` to session constructor, call directly.**
- All `lookupVersion` mutations and `lookupInFlight` mutations stay inside session
- All `hiscoreResult = result;` and `clogResult = result;` writes stay inside session
- `SwingUtilities.invokeLater(...)` wrappers around listener calls stay (preserve EDT semantics)

Skip these from doLookup (panel keeps them):
- Reading `searchBar.getText().trim()` - panel passes the player string to `session.start()`
- `localRsn` / `localAccountType` - panel passes these to `session.start()` parameters
- `compareTooltipDataMap.clear()` (if present) - comparison mode is OUT OF SCOPE for cut 1; leave that call in panel after `session.start()` returns

### Step 2 - Update `LookupSession` constructor signature

Current:
```java
public LookupSession(HiscoreService hiscoreService, ClogService clogService,
    KillClogConfig config, Listener listener)
```

Add `NameAutocompleter` as nullable:
```java
public LookupSession(HiscoreService hiscoreService, ClogService clogService,
    KillClogConfig config, @Nullable NameAutocompleter nameAutocompleter, Listener listener)
```

Store + use in start() at the cache-hit and api-success points.

### Step 3 - In `KillClogPanel.java`, instantiate the session

Add a field near other final-ish refs (after `tooltipController` declaration, around line 230):

```java
private final LookupSession lookupSession;
```

In the constructor (around line 270 after `this.tooltipController = ...`), instantiate it:

```java
this.lookupSession = new LookupSession(hiscoreService, clogService, config, nameAutocompleter, this);
```

Note: `nameAutocompleter` is set later in setNameAutocompleter(). Either:
- Move that setter to update both the panel field AND `lookupSession`'s internal ref via a `setNameAutocompleter()` on session
- Or defer session construction until after `nameAutocompleter` is set

**Decision: add `setNameAutocompleter(NameAutocompleter)` to `LookupSession`. Panel's existing setter calls both.**

### Step 4 - Panel implements `LookupSession.Listener`

Add `implements LookupSession.Listener` to the class declaration. Implement the 6 methods at the bottom of the class. Each method body is COPIED VERBATIM from the corresponding chunk of the current doLookup body:

| Listener method | Maps to current doLookup chunk |
|---|---|
| `onLookupStart` | lines 2010-2026 (the pre-lookup UI reset block) |
| `onCachedResult` | lines 2039-2058 (the Timer reveal callback body) |
| `onHiscoreResult` | lines 2070-2110 (the api result callback body, success path) |
| `onClogResult` | the clog-service callback (lines ~2150-2180, find it) |
| `onNotFound` | lines 2075-2087 (the result==null branch) |
| `onError` | lines 2113-2130 (the .exceptionally() body) |

Each method ends with the side-effects already in doLookup (renderHiscoreResult, renderClogResult, etc).

### Step 5 - Replace `doLookup()` body

```java
public void doLookup()
{
    String player = searchBar.getText().trim();
    if (player.isEmpty() || lookupSession.isLookupInFlight())
    {
        if (!lookupSession.isLookupInFlight())
        {
            setSearchStatus("Enter RSN", TEXT_DIM);
        }
        return;
    }
    lookupSession.start(player, localRsn, localAccountType);
}
```

That's it for doLookup. Body shrinks from ~150 lines to ~10.

### Step 6 - Migrate all read sites of lookup state

In `KillClogPanel.java`, find every read of `hiscoreResult`, `clogResult`, `currentLookupRsn`, `clogLastChanged`, `lookupVersion`, `lookupInFlight`:

```bash
grep -nE "\b(hiscoreResult|clogResult|currentLookupRsn|clogLastChanged|lookupVersion|lookupInFlight)\b" src/main/java/com/killclog/KillClogPanel.java
```

For each match:
- `hiscoreResult` → `lookupSession.getHiscoreResult()`
- `clogResult` → `lookupSession.getClogResult()`
- `currentLookupRsn` → `lookupSession.getCurrentLookupRsn()`
- `clogLastChanged` → `lookupSession.getClogLastChanged()`
- `lookupVersion` (read) → `lookupSession.getLookupVersion()`
- `lookupInFlight` (read) → `lookupSession.isLookupInFlight()`

Special cases:
- `hiscoreResult = result;` writes: REMOVE. The session does this internally now.
- `lookupVersion = X;` writes (the `lookupVersion++` increments in error paths): REMOVE. Session handles version stamping internally now via its `start()` flow.

After this step, the panel should have ZERO local references to these 6 names except via the session getters.

### Step 7 - Remove the 6 state fields from KillClogPanel

Delete lines 215-228 (the `hiscoreResult`, `clogResult`, `rsn`, `currentLookupRsn`, `clogLastChanged`, `lookupVersion`, `lookupInFlight` declarations). Keep `localRsn` and `localAccountType` - those stay in panel as they're not lookup state.

**Note:** `rsn` (line 217) might be referenced separately from `currentLookupRsn`. Grep for it. If `rsn` is used as a "current player display name" distinct from the lookup mechanism, KEEP it in panel. If it's redundant with `currentLookupRsn`, replace with `lookupSession.getCurrentLookupRsn()`.

## Verification gates

After step 7, before commit:

1. **Build:**
   ```bash
   ./gradlew compileJava
   ```
   Must show BUILD SUCCESSFUL. If errors, you missed a reference site in step 6 or have a type mismatch.

2. **Manual smoke test:**
   ```bash
   ./gradlew run
   ```
   Wait for RuneLite to launch with the plugin sideloaded. Test these paths:
   - Lookup self (cached path) - should reveal data after the 600ms timer
   - Lookup self (after a stale cache - close + reopen plugin to force) - should hit API
   - Lookup random player like "asdfqwerty12345" → expect not-found message
   - Lookup with network disconnected → expect "Lookup failed" message
   - Lookup, then immediately lookup another player → first lookup should NOT race-overwrite the second

3. **Lint pass:**
   ```bash
   grep -nE "\b(hiscoreResult|clogResult|lookupVersion|lookupInFlight)\b" src/main/java/com/killclog/KillClogPanel.java | grep -v "lookupSession\.\|//"
   ```
   Should print NOTHING. Any remaining reference is a missed migration.

4. **Council pre-review (recommended):**
   ```bash
   cd ~/auto-wake-workspace/infra/review-swarm
   git -C ~/plugins/kcpdev stash push --keep-index
   python3 review.py refactor-lookup-session~1..HEAD --agents shibui,code-simplicity,monolith-hunter
   git -C ~/plugins/kcpdev stash pop
   ```
   monolith-hunter should produce a markedly LOWER score for KillClogPanel.java than its baseline.

## Commit message template

```
refactor-cut-1: extract LookupSession from KillClogPanel.

migrates the async lookup pipeline (cache check, parallel hiscore + clog
api fan-out, version-stamped result gating, in-flight guard) out of the
2897-line panel into a UI-agnostic LookupSession class.

panel becomes a pure presenter:
- implements LookupSession.Listener
- 6 lifecycle events: onLookupStart, onCachedResult, onHiscoreResult,
  onClogResult, onNotFound, onError
- reads state via session getters; never mutates session fields directly
- doLookup() shrinks 150 -> 10 lines

session owns:
- hiscoreResult, clogResult, currentLookupRsn, clogLastChanged state
- lookupVersion (monotonic, gates stale async callbacks)
- lookupInFlight (the early-return guard)
- the cache-check + reveal-timer flow
- the parallel hiscore + clog api fan-out
- error path version-stamp increments

zero behavior change. all 4 lookup paths smoke-tested:
cached / api-success / not-found / error. version-stamp invariant
preserved (rapid back-to-back lookups don't race-overwrite).

next: cut 2 extracts ComparisonController (~600 lines), gates on this.
```

## Fail-safe

If at any step the build breaks or smoke test fails:
1. `git stash` your in-flight changes
2. tell dyl what broke
3. don't push, don't merge

## When you're done

1. Verify all 4 verification gates passed
2. Commit with the template message
3. Update REFACTOR-CUT-1.md with `## Status: complete (commit <sha>)`
4. Telegram dyl via heartbeat bot: `REFACTOR-CUT-1 LANDED: <sha>, build green, smoke 4/4`
