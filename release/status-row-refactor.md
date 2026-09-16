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

Independent review: approved as a local extraction with no blocking findings;
running-client smoke still outstanding.

## Completed: explicit status ownership

`StatusMessage` carries owner (lookup, sync, character), kind (hover, notice,
progress, result), text, colour and its own expiry timer. `PanelStatusRow`
holds the current message and decides from it: the row is free while blank or
while the message yields (hover, notice); feedback lands only while free or
already its owner's; a sync success flashes only over a blank row or sync-owned
text; leaving a control clears only that owner's yielding message. Lookup text
still arrives as strings and holds the row until the panel replaces or blanks
it. The plugin's character strings are classified once in
`showCharacterPublishStatus`: `updating character...` is progress, the notice
constants are notices, and anything else, including `Publish failed` and
`Finishing previous request...`, is a result. `FirstPartyFeedback` passes the
kind through its status callback and reports a failure as a result rather than
as progress text. Colours follow the kind: progress and hover in k1, notices
and results dim, exactly as before.

An expiry now belongs to the message it was started for and clears nothing
else; replacing or blanking a message cancels its expiry. Two behaviours
changed as a consequence, both covered by new tests:

- A stale expiry can no longer clear a newer message with the same text or
  drop the reference to the newer timer
  (`sameTextReplacementKeepsItsOwnExpiry`).
- Hovering the character control while a notice shows keeps the hover line
  until the pointer leaves; the notice's expiry no longer clears it mid-hover
  (`hoverOverNoticeOutlivesTheNoticeExpiry`).

`resetSyncFeedback` still stops whichever expiry is running, character
included; that policy is handled in the next commit. The string predicates
`isSyncOwnedStatus` and `canFlashSyncSuccess` are gone and their coverage
moved to `syncSuccessFlashesOnlyOverBlankOrSyncOwnedText`; `isCharacterNotice`
remains as the door classifier. The characterization fixture reads the expiry
through the current message. `KillClogPlugin` is unchanged.

Validation: `.\gradlew.bat --offline compileJava test checkstyleMain
checkstyleTest --rerun-tasks` in PowerShell, exit 0, on a cleared
`build/test-results`: 661 tests across 73 suites, zero
failures/errors/skips. Not done here: running-client visual smoke, and an
independent review.

## Completed: sync reset keeps character expiry

`resetSyncFeedback` runs on logout and account change. It used to stop the
shared expiry timer regardless of owner, so a character result shown moments
earlier, such as `Publish failed`, lost its expiry and stayed on the row until
something else wrote over it. Reproduced first with
`syncResetLeavesCharacterExpiryRunning` against the previous commit:

    PanelStatusRowCharacterizationTest > syncResetLeavesCharacterExpiryRunning FAILED
    Caused by: java.lang.AssertionError: character result keeps its expiry through a sync reset
    14 tests completed, 1 failed

The reset now clears only a sync-owned message, and with it its expiry;
lookup and character messages and their expiries are untouched. This is an
intentional behaviour fix, not an extraction artefact.

Validation: same PowerShell gate, exit 0, on a cleared `build/test-results`:
662 tests across 73 suites, zero failures/errors/skips. Not done here:
running-client visual smoke, and an independent review.

## Completed: cell reset moved into Cells

`Cells.reset()` returns every cell to its pre-lookup state: dashes in the
resting colour, the resting tooltip, boss cells back on their original icons,
and both tooltip caches emptied. The panel's `resetForLookup` (formerly
`resetAllLabels`) keeps the header side: pinned tooltip, compare entry, the
setup notice, player name, clog info, combat and total cells, the compare
icon, and the skill display refresh. The two trailing `rareTooltips.remove`
calls were dropped; they followed a `clear()` of the same map and had since
both were written in the same commit. Cell resets now run before the header
resets rather than interleaved with them; no cell or header widget depends
on the other, so the resting state is identical.

Renamed in the panel, mechanically: `toggleHighlighter(boolean)` is now
`renderResults()`, since it repaints the header and every cell from the
session results and applies the highlighter only when the setting is on; all
eight callers passed that same setting. `renderHiscoreResult`,
`renderClogResult` and their comments were left alone.

`PanelCellResetCharacterizationTest` (previous commit) renders a small
result set, colours the empty cells the way the highlighter does, shows the
compare icon, seeds the header, then drives `onLookupStart` and asserts the
resting text, colour, tooltip and icon of every boss, activity, clue tier,
rare and PvP cell, both tooltip caches, and the header. A second case pins
that `onError` goes through the same reset. Observed while writing it and
left as-is: after a miss or failure the panel immediately rebuilds catalog
previews, so rare cells show a custom tooltip again rather than their name.

Validation: same PowerShell gate, exit 0, on a cleared `build/test-results`:
664 tests across 74 suites, zero failures/errors/skips. Not done here:
running-client visual smoke, and an independent review.

## Completed: publication orchestration characterized and extracted

`PluginPublicationCharacterizationTest` (previous commit) constructs the
real plugin with its injected collaborators replaced: the executor and the
client thread are queues the test pumps by hand, the sync and appearance
services are mocks whose futures the test completes, the cache reports a
session epoch the test can change, and the panel is a mock whose feedback
calls are the assertions. It runs `startUp`, captures the panel handlers and
the capture listener, and covers: a manual push narrating and reporting;
repeat clicks during a flight queuing one follow-up; the capture debounce
coalescing and staying quiet; logout before the client hop, and a late
completion after logout, staying silent with the next session pushing again;
a session change before the timer fires dropping the push; opt-out mid-flight
then opt-in queuing behind the old request; one server-advised contention
retry; a publish rendering, reporting and ignoring a double click; the
profile-required prerequisite sync followed by exactly one retry; a failed
prerequisite sync failing the publish and freeing the slot; logout during the
prerequisite sync withdrawing the publish; disabling character publishing
silencing the flight and re-enabling allowing a new one; and shutdown
dropping a scheduled push. Dependencies are injected by reflection over the
plugin's `@Inject` fields, as the container would; no private method is
invoked. `PublishResult`'s two-argument constructor became package-private so
a mocked publish can complete with one.

`PublicationCoordinator` now owns the flow: the pending debounce, the sync
gate, the character in-flight, parked-behind-sync and prerequisite-attempted
flags, the character generation, and with them scheduling, cancellation, the
push, the dispatch, both feedback fences and the personal-best cargo gather.
Method bodies moved unchanged apart from panel calls going through the
coordinator's `Feedback` interface and the account type arriving as a
supplier. The plugin keeps every event subscription and forwards the moments
that matter: `scheduleAutomaticSync` on capture, login and settled identity;
`scheduleSync(0, true)` on opt-in; `manualSync` and `publishCharacter` as the
panel handlers; `cancelSync` and `cancelCharacterPublish` on logout, opt-out
and shutdown. It keeps the status vocabulary, `characterPublishTerminalStatus`
and `enforceCharacterSettingDependency`, and adapts the panel to `Feedback`.
Sync and character publish stay one unit because a publish can be parked
behind a sync. The plugin went from 1451 to 987 lines.

Validation: same PowerShell gate on a cleared `build/test-results`: 678
tests across 75 suites, zero failures/errors/skips. Gradle's exit code
did not propagate through the harness used here, so the build banner and the
XML reports are the proof. Not done here: running-client visual smoke, and an
independent review.

## Reversible follow-up slices

All four planned slices are complete; nothing remains queued.

For each production slice: relevant tests plus full test/checkstyle gates,
independent review, and focused before/after visual smoke. No full release smoke
or token recount is needed for this test-only preparation; a changed release
candidate gets its own source/token/jar receipt and approval. Revert individual
commits to roll back; none should require a data or configuration migration.
