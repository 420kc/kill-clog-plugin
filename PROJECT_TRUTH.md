# Kill Clog Project Truth

Current as of 2026-06-18. Prove moving facts with `git status --short --branch`
and `git log -1 --oneline --decorate` before editing.

## Repo And Branch Canon

- Canonical repo: `C:\Users\dylan\plugins\kcpdev`
- Public remote: `origin` = `https://github.com/420kc/kill-clog-plugin.git`
- `master` is the Plugin Hub PR submission line.
- Start release work from current `master`, not stale local release branches.
- Current next release target: `1.4.3`.

## Current Release Shape

`master` contains the accepted 1.4.2 release work plus post-1.4.2 tooltip
parity work:

- item sprites in single and comparison clog tooltips expose their item name on
  hover and open the OSRS Wiki item page on click
- wiki item links can be disabled from config without removing hover item names
- comparison total labels have the same hover/underline affordance as RSNs
- Kill Clog chatbox notices are grouped into config toggles for new drops, sync
  help, sync results, and warnings
- chat commands include boss KC in `!kclog` / `!missing` headers when known
- tooltip sizing measures real content instead of fixed placeholder widths
- quiet no-data copy for unsynced comparison states

## Explicitly Excluded

Do not include the client-to-Kill-Clog-server sync prototype in the next release.
That work lives on `experiment/kc-sync-prototype` and includes `SyncService`,
endpoint/token config, and client upload hooks. It is not ready.

Existing local collection-log sync/cache behavior is still core plugin behavior:
the chalice sync builds the local cache, and collection-log chat messages update
that local cache during play. That is separate from the excluded server sync.

## Local Branch Notes

- `next-release` is stale relative to current `master` and should not be used as
  the active release branch.
- `fix/kc-tooltip-name-wrap` is also stale relative to current `master`.
- `backup/*` and `preserve/*` branches are historical safety refs.

## Gates

Before calling plugin code ready:

```powershell
.\gradlew.bat compileJava checkstyleMain checkstyleTest test
```

Real-client visual/interaction smoke is Dylan's gate before Plugin Hub PR
submission.
