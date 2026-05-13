# Refactor cut 1: extract LookupSession from KillClogPanel

Branch: `refactor-lookup-session` (off `dev` @ `8a172a0`)

## What's getting cut

`KillClogPanel.java` is 2897 lines. The async lookup pipeline (player search → cache check → hiscore + clog parallel fetches → version-stamped result handling) lives as a 150-line method body inside the panel, with state spread across 6 instance fields and 30+ read sites.

## The target shape

```
LookupSession (new)                KillClogPanel (slimmer)
├─ state                            ├─ UI widgets (unchanged)
│   ├─ hiscoreResult                ├─ rendering methods (unchanged)
│   ├─ clogResult                   ├─ session.start(player)
│   ├─ currentLookupRsn             ├─ reads session.getHiscoreResult() etc
│   ├─ clogLastChanged              └─ implements LookupSession.Listener
│   ├─ lookupVersion (volatile)
│   └─ lookupInFlight (volatile)
├─ deps
│   ├─ HiscoreService
│   ├─ ClogService
│   └─ Listener
└─ methods
    ├─ start(player, localRsn, localAccountType)
    │   ├─ cache check + Timer reveal
    │   ├─ async hiscore lookup
    │   └─ version-stamp gating throughout
    └─ getters (read-only, no mutation from panel)
```

## Listener interface (panel implements)

```java
public interface Listener {
    void onLookupStart(String player, boolean isSelf, boolean isFirstSelfGreeting);
    void onCachedResult(String player, HiscoreResult hs, ClogResult clog,
                       boolean isSelf, AccountType knownType, boolean isFirstSelfGreeting);
    void onHiscoreResult(String player, HiscoreResult hs,
                        boolean isSelf, AccountType knownType, boolean isFirstSelfGreeting);
    void onClogResult(String player, ClogResult clog, boolean isSelf, int lookupVersion);
    void onNotFound(String player);
    void onError(String player, Throwable err);
}
```

Panel implements these as pure UI updates (`setSearchStatus`, `searchBar.setIcon`, `renderHiscoreResult`, `playerName.setText`). Session does ZERO direct UI calls.

## Scope discipline

This refactor is **state + orchestration extraction only**. The following are explicitly NOT in this cut:

- Comparison mode (cut 2's job: `ComparisonController`)
- Cell-factory tooltip routing (cut 3's job)
- Renaming public methods on the panel (zero behavior change)
- New features

## Verification gate

Before merging back to dev:

1. `./gradlew build` passes
2. `./gradlew run` launches RuneLite with the plugin sideloaded
3. Manual smoke test:
   - Lookup self (cached path)
   - Lookup self (stale cache, full API path)
   - Lookup random player (not found path)
   - Lookup with network down (error path)
4. Run review-swarm against the diff with shibui + code-simplicity + monolith-hunter lenses; verify monolith-hunter no longer flags the panel for "doLookup orchestration mixed in"

## Sequencing notes

Refactor of a shipped plugin (~7,000 active installs, Plugin Hub). The doLookup body has subtle invariants (version-stamp gating on every async callback, in-flight guards, parallel clog/hiscore interleave) that require focused attention to preserve.

**Dedicated session: 2-3 hours, no interruptions, manual smoke test before commit, council pre-review on the diff.**

## Cut 2 + cut 3 follow

After cut 1 lands cleanly:
- Cut 2: ComparisonController extraction (~600 lines moved, biggest one). Dedicated session, gates on cut 1.
- Cut 3: Cell-factory tooltip routing collapse (~60 lines, self-contained, easy session).

End-state: KillClogPanel drops from 2897 → ~1500 lines, single-responsibility (pure presenter), with `LookupSession` + `ComparisonController` as collaborators.

The RuneLite reviewer's first reaction reading the diff: *the seams are obvious, the panel is a presenter, the async lifecycle is testable in isolation.*
