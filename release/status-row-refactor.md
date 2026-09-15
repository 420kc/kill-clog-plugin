# Status row maintenance slice

## Boundary

Prepared on `hive/status-row-characterization` in
`C:/Users/dylan/plugins/kcpdev-status-row`, from
`1e0a55916df1748792adcb26897043ee2daeca83`.
The approved `hive/2.4.0` checkout and jar remain unchanged.
This is maintenance preparation, not another release candidate or a decision
to number the next release.

## Completed: characterize the existing panel

`PanelStatusRowCharacterizationTest` constructs the real `KillClogPanel` with
mocked service/client boundaries. It runs UI actions and assertions on the EDT,
drains queued setting updates, and delivers actual Swing timer callbacks without
sleeping. Catalog completion and client-thread work remain dormant; no HTTP,
credentials, local account cache, or running RuneLite client is needed.

Ten tests cover:

- Local-data and independent consent-setting visibility.
- Lookup feedback surviving sync and character progress/results.
- Active sync and character progress retaining their own status.
- Expired failure feedback preserving a newer lookup message.
- Silent sync failure retained for deliberate hover and cleared by success.
- Character failure expiry, escaped hover details, and lookup surviving mouse exit.
- Failure history cleared by disabling/re-enabling controls.
- Left-click dispatch only while visible, with stable reserved row height.
- Sync feedback reset preserving character progress and dropping old sync failure.
- Shutdown stopping status/glow timers and allowing the same controls to be reused.

Mockito is a test-only dependency. Reflection is restricted to locating the
current widgets/timers; it does not bypass construction, invoke private business
methods, or reproduce the implementation. When extracting the row, adjust the
fixture's field lookup while retaining the behavioral assertions.

These are automated Swing behavior checks, not visual approval or full plugin
restart/account-isolation coverage. Existing FirstPartyFeedback tests cover
automatic-feedback preference changes. Network orchestration needs a separate
fixture before its coordinator is moved.

Validation: `./gradlew.bat --offline test checkstyleMain checkstyleTest` completed
successfully: 658 tests across 73 suites, zero failures/errors/skips. Production
sources are unchanged. The earlier full rerun compiled all sources and ran the
suite; the final run corrected test formatting and passed both style gates.

## Completed: extract PanelStatusRow

`PanelStatusRow` now owns the status label, the two action labels, both
`FirstPartyFeedback` objects, the notice text/detail, hover and glow state,
the shared expiry timer and both glow timers, chalice/character icon tinting,
row layout, and teardown. It takes the config, sprite manager, tooltip
controller and idle text colour; the panel is not passed in. Method bodies
moved unchanged apart from reading the idle colour from a field and dropping
fully qualified names.

The panel keeps its package-private surface as one-line delegates
(`setKillclogSyncHandler`, `setCharacterPublishHandler`, `setSyncArrowEnabled`,
`setSyncArrowHasData`, `setCharacterPublishEnabled`, `showSyncProgress`,
`showSyncResult`, `resetSyncFeedback`, `showCharacterPublishStatus`,
`refreshSyncFeedbackSettings`), routes lookup messages through
`setSearchStatus`, reads `statusText()` for the compare-view refusal, calls
the row's `shutdown()` from its own, and re-requests the character icon after
a sprite reload. `KillClogPlugin` is unchanged. Message priority, timer policy
(including `resetSyncFeedback` stopping the shared expiry timer), EDT
scheduling and the reserved row height are as before.

Tests: the characterization fixture resolves widgets and timers on the
extracted row instead of the panel; `KillClogPanelTest` and
`ProfileAppearanceServiceTest` reference the moved static helpers. No
assertion changed.

Validation: `./gradlew.bat --offline test checkstyleMain checkstyleTest`
completed successfully on a cleared `build/test-results`: 658 tests across
73 suites, zero failures/errors/skips. A line-multiset diff of the moved
block against the new class showed only the constructor, accessors, section
comments and the widened `setSearchStatus` visibility. Not done here:
before/after visual smoke in a running client, and an independent review.

Open for a later slice: whether the plugin should hold `PanelStatusRow`
directly and drop the panel delegates. Left as-is to keep this commit a pure
extraction.

## Reversible follow-up slices

1. **Replace string-based status ownership.** Represent owner (lookup, sync,
   character), kind (hover, progress, notice, result) and message identity
   explicitly. Timer expiry must belong to the message it clears. Preserve
   current priority rules unless a separately demonstrated bug warrants changing
   one. Add same-text replacement and stale-callback cases here. Review the
   shared timer cancellation in `resetSyncFeedback`: preserving character text
   currently also stops the shared expiry timer. Do not silently change that
   policy during extraction.
2. **Move cell reset behavior into Cells.** Keep search/header reset in the
   panel. Verify icons, tooltip maps, highlights and comparison resets. Rename
   misleading rendering methods/comments in their owning slice, without a
   repository-wide cleanup.
3. **Characterize publication orchestration, then extract one coordinator.**
   Cover prerequisite sync, duplicate requests, queued manual requests, logout,
   account changes, consent revoke, disable/re-enable and late completions.
   Preserve generation/session guards and physical single-flight ownership.
   Keep event subscriptions in the plugin and existing HTTP/cache services in
   place. Do not split mutually dependent sync and publish managers.

For each production slice: relevant tests plus full test/checkstyle gates,
independent review, and focused before/after visual smoke. No full release smoke
or token recount is needed for this test-only preparation; a changed release
candidate gets its own source/token/jar receipt and approval. Revert individual
commits to roll back; none should require a data or configuration migration.
