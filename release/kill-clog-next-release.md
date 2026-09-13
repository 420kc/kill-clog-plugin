# Kill Clog 2.4.0 candidate

Current addition (2026-09-12): character publication now captures the game's
original appearance on `PlayerChanged` at priority 2, before Fashionscape (0)
and Weapon/Gear/Anim Replacer (1) mutate it. Equipment, body kits, colours,
item recolours/textures, gender and idle pose are copied in memory. Publication
uses that snapshot plus the current follower; no renderer/schema/API change.

Before registration or upload, the snapshot must belong to the current local
actor/account and match all nine real wearable slots in WORN. Structural body
slots are excluded from that comparison. Logout, hopping, connection loss and
shutdown clear the snapshot; normal LOADING can capture the initial appearance.
Missing/stale data asks the player to equip or unequip an item and retry. This
can occur after enabling Kill Clog mid-session. No visible-composition fallback
is allowed. Cosmetic plugins can remain enabled during normal publication.
This is compatibility with known cosmetic event handlers, not remote attestation
or protection against deliberately modified clients/higher-priority handlers.

Validation: 648 tests, zero failures/errors/skips; both Checkstyles and jar
build passed. Tests cover priority ordering, original gear/body/colour/pose in
the HTTP payload, deep-copy immunity, account/actor isolation, stale equipment,
invalid appearance and hop/reconnect clearing. Token proxy: 196,614 (+347),
still below the documented 200,000 ceiling and above the 195,000 caution mark.
Independent review: ALLOW, with 36 appearance tests and both Checkstyles rerun.
Offscreen RuneLite-font smoke: the 148px retry notice fits the 225px row.
Jar: 555,774 bytes, SHA-256
`A5FD09643D7FE2B88F671D2AFB2B4BB33F8B88ED386EAEFC72B9C103938A33DB`.
Committed as `e6584a9e`; prepared for the next shared refire as
`C:/Users/dylan/plugins/dev-client/.prepared-refire/plugins/kcpdev-e6584a9e-A5FD09643D7F.jar`.
The running client was left untouched. No push or Plugin Hub submission.

Required next in-game smoke: keep the fake scythe override enabled, publish,
and confirm the web character shows the actual weapon and original stance.
Prior scythe rejection smoke covered guard `5b1b8354`; the earlier actionable
status jar `kcpdev-431e263a-9B6262A04D27.jar` remains preserved. Prior acceptance
below does not cover these new bytes. Existing published characters update
only when their owners publish again.

Status: the e403c7d8 product/UX candidate remains Dylan-approved and smoke-passed.
Ownership recovery is independently approved. Dylan supplied Fable's ALLOW for
the total-authority/catalog-retry packet on 2026-09-12, including an independent
640-test and Checkstyle rerun. No reviewer was summoned for that follow-up.
On 2026-09-12 Dylan explicitly accepted the current jar as smoke-passed and
ready for 2.4.0 submission after the 2.3.3 Plugin Hub PR merges. His prior unlock
increased the Clog Summary total from 1197 to 1198 once, with two chat messages.
He accepted that evidence without requiring another scarce unlock. This is user
acceptance of the current candidate, not a claim that every proposed smoke step
was newly rerun. The separate acquisition-date API rollout remains tracked below.
Master and the earlier approved jars remain unchanged.

- Active branch: `hive/2.4.0`.
- Active checkout: `C:/Users/dylan/plugins/kcpdev`.
- Canonical consolidation preserves reviewed candidate `32b90541` unchanged;
  only release documentation changed during cleanup.
- Current candidate code: `e6584a9e` (original-appearance capture), following
  `431e263a` (override status) and `5b1b8354` (equipment publication guard),
  `2bff127c` (catalog retry) and total fix `31d4f597`.
- README badge/wording: `05715328cf65e3d57a2a529884aa4d32ab45141c`.
- Ownership correction: `064c09b015ecd40667e9c2a5d4c222736e7a417a`.
- Pre-fix candidate: `2bf9c639`; its smoke-approved jar remains unchanged.
  Retired branch names and build evidence are in the cleanup archive below.
- Prior smoke-approved code: `e403c7d897e414ec37de9de742ac161beafdd9d9`.
- Submission base: 2.3.3 `96dee2429ed96187e36d1451270b114f7a9dbd07`.
- 2.3.3 Hub PR: https://github.com/runelite/plugin-hub/pull/16418
  Last checked open on 2026-09-12; recheck the accepted pin before submission.
- Historical checkpoints: tag `hive/2.3.4-submission-candidate` (`d18f41e7`)
  and log-refresh commit `1c3df015`. Their release branches are retired.
- Approved jar: `kcpdev-e403c7d8-E31A0EDAA84C.jar`, 552,802 bytes.
  SHA-256: `E31A0EDAA84CBBB9A2FEA810FB2903F70A16B22B6E152099E4F3B85B99EC3DAA`.
  Stored under `C:/Users/dylan/plugins/dev-client/.prepared-refire/plugins/`.
  The running shared client was verified to load this exact jar when Dylan
  reported the quiet-update smoke passed. Preserve these bytes; docs-only
  closeout commits do not require a replacement build or another visual smoke.
- Previous candidate `85d71da9` / code `1952dfeb` remains in Git history and
  the cleanup archive; its immutable prepared jar also remains.

## Canonical checkout and recovery

Use `C:/Users/dylan/plugins/kcpdev` on `hive/2.4.0` for all plugin work and
prepared refires. The temporary 2.3.3/2.3.4/2.4.0 release and review worktrees
were clean and removed on 2026-09-12. `master` remains at the submitted 2.3.3
commit `96dee242`; no remote refs or Plugin Hub pin changed.

Recovery archive:
`C:/Users/dylan/plugins/release-archives/killclog-20260912-161658/`.
It contains a verified `plugin-history.bundle`, `build-evidence.zip`, and
`inventory.json` with original branch heads and worktree paths. The old canonical
build is also retained there. Unique design/diagnostic histories have local
`archive/20260912/*` tags; they are historical alternatives, not active releases.
Unrelated older plugin experiments and separate API/Hive worktrees were untouched.

## Included changes from 2.3.3

1. Keep all modal titles stable during item, skill and source hovers.
2. Use one reserved item-name row below the stats divider in skill clogs,
   with 6px section gaps and the existing dense-grid column choices. Other
   modals retain their section readouts and restored spacing.
3. Refresh changed hover names and clear stale hover state when data changes.
4. Match alternate item forms to unambiguous canonical Collection Log slots
   in previews, totals and future captures, without counting forms twice.
5. Validate complete captures against canonical slots: alternate forms cannot
   stand in for a missing different item.
6. Restore normal catalog previews after a missing-player or failed lookup.
7. Add Smolcano to Mining Pets and Tiny tempor to Fishing Pets.
8. Add Quetzin and Chompy chick to Hunter Pets; share Chompy Bird Hunting with
   Hunter while retaining Ranged. Shared items count once per skill total.
9. Distinguish Local Collection Log from Kill Clog web data in Sources; credit
   Temple when it contributes dates. Preserve reduced external self requests.
10. Use Kill Clog source wording and the Kill Clog Web Sync settings heading.
11. Refresh the full local log whenever its owner opens it, including an existing
    cache; manual Search retries the same complete capture.
12. Remove the in-game page-refresh chalice and its unused page-reader code.
    Keep the panel web-sync and character controls.
13. Replace saved quantities and totals from a complete first-party capture,
    preserving dates and identity metadata. Reject interrupted, incomplete,
    house-host and changed-account captures without replacing good data.
14. Keep routine refreshes quiet; skip unchanged writes and web-sync signals,
    retain retryable failed saves, and keep the last settled catalog total.
15. Explain local setup, automatic updates, retry/repair and optional web
    publication separately in the README. Align all three version fields to 2.4.0.
16. Clarify character publication failures and waiting states in the existing
    status row and wrapped hover details, including safe support references.
17. Guard each publish continuation against cancellation, consent and account
    changes; retain single-flight requests and bounded click backoff.
18. Preserve recovery dates and newly issued credentials under the original
    account, avoiding lost authorization when a request is cancelled.
19. Retain ambiguous live unlock timestamps until a complete capture identifies
    one item; preserve pending evidence across restarts, session gaps and renames.
    Never assign an acquisition time to an undated historical import.
20. Carry saved acquisition dates through optional web sync, proof lookup and
    Recent, separately from API receipt times. Companion API deployed at
    `69a70503` on 2026-09-13 02:34 UTC (September 12 locally).
21. Refresh the displayed local log immediately after captures and matched drops,
    preserving stats, CA, rank selection and colors without restarting provider
    lookups. Leave other-player lookups alone; refresh comparison colors/totals.

## Current preparation proof (2026-09-12)

Code `e403c7d8` passed compile, both Checkstyle gates, jar build and 624 tests
(zero failures/errors/skips). Independent date-history and quiet-refresh reviews
returned ALLOW; follow-up review checked the comparison redraw order and totals.
Dylan approved the final quiet-update smoke on 2026-09-12. This supersedes the
earlier 612-test/code `1952dfeb` candidate. Existing Swing fixtures and isolated
failure-path tests remain relevant; the smoke is not proof of every rare account
or service failure scenario.

The code diff from 2.3.3 through `e403c7d8` is 60 files, +2,992/-942 lines,
including tests/docs. The quiet-update slice alone is 43 production lines added
and 6 removed across three files (122/8 including its tests).

Earlier measurements, retained as history:
The earlier `a852a227` checkpoint diff from 2.3.3 was 49 files, +2,467/-931 lines; the character
slice from `5469f66d` is 10 files, +963/-181, including tests and documentation.
The earlier raw counts included comments and are not the Hub-style estimate.
The recovered local counter strips comments from main Java, trims trailing line
whitespace, collapses blank lines, and uses `o200k_base`. It reproduces 193,171
at the older 24703ced checkpoint, 187,786 at 2.3.3, and 195,101 at 2bf9c639.
Ownership correction: 195,988 tokens. After the two correctness fixes: 195,937
tokens (local proxy, 51 fewer).
Current equipment guard and status candidate `431e263a`: 196,267 tokens using
the same local counter. This is below the documented 200,000 cap but above
the conservative 195,000 working ceiling; do not report a large safety margin.
The Hub's documented limit is 200,000; our conservative working ceiling is
195,000. This is a local proxy, not the private bot's exact implementation.
Run the existing local counter once per changed release candidate and record its
result alongside the build receipt; it is not currently a Gradle/CI gate.
Counter: `C:/Users/dylan/.claude/skills/runelite-plugin-gate/count_plugin_tokens.py`.
Maintainer scope: https://github.com/runelite/plugin-hub/pull/16026#issuecomment-5554814434

Earlier in this lane, live API health and the private deployment marker were checked. The bind
verifier now matches this release, the render queue is idle, and the retained
API log contains no appearance request/failure notice events since the notice
deployment. That gives no new real-user failure-path coverage or delivery proof.

## Character publication cleanup

Implemented locally after `5469f66d`. Independent review allowed `7a47f007`
and reproduced 611 tests. The final follow-up `a852a227` also received ALLOW,
with 31 appearance tests, 23 panel tests and both Checkstyle gates rerun.
The later candidate smoke is recorded above; no new character feature is pending.

- Bring forward the bounded API error-code/support-ref messages from `5a06060f`.
- Keep the existing character icon, status row and success flash. Show updating,
  accepted rendering, access recovery waiting, unavailable service and unknown
  connection outcomes truthfully. Retain useful details on hover, without
  replacing these states with Publish failed or adding a dialog/settings panel.
- Check current consent and account identity on the client thread before every
  HTTP dispatch. Keep a service flight occupied until its existing chain settles,
  so a cancelled UI cannot start overlapping HTTP work after re-enable.
- Retain a newly issued account-scoped secret even when cancelled, preventing an
  avoidable recovery lockout; stop subsequent publication. Already-sent requests
  cannot be retracted by cancelling the UI.
- Honor Retry-After on failed POSTs, with bounded click backoff and no new
  automatic retry loop. Accepted rendering has a short repeat-click backoff;
  network loss advises checking the profile before retrying.
- Preserve recovery activation dates (they were mistakenly treated as invalid
  hex secrets) and display the server's date in the user's timezone.
- When this installation lacks the token for an existing recovery request,
  direct the user to the installation that started it or support. Waiting alone
  cannot give this installation the missing token.
- Explain unsupported local player transforms before making an HTTP request.
  Keep current API validation and explicit unsupported-follower retry guidance.
- Test actual delayed registration/claim/retry sequences, cancellation, account
  switch, logout, duplicate clicks, 202, 429, 503, malformed replies and connection
  loss with isolated configuration and intercepted HTTP. Never use live accounts
  or real credential storage for these tests.

The earlier checklist was broader than the agreed public UX slice. Continuous
polling, an additional recovery UI, new automatic retries, and duplicated client
palette tables are excluded. The existing API contract remains authoritative.

### Recovery policy decision

The API's existing lost-secret recovery and registration-lock delays are seven
days. This is not a new 2.4 behavior and is not changed by clearer status text.
Normal first-time publishing does not wait. A retained old publishing secret can
cancel a pending takeover; losing all copies can require delayed recovery.
RuneProfile's inspected upload path instead uses an account-derived identifier
without this additional device-recovery workflow.

Consider simplifying this separately against the same owner-sync trust boundary,
or retaining continuity protection with an approved alternative recovery path.
Shortening the timer alone changes the protection without eliminating the extra
workflow. No timer change, credential migration, API deployment or security
policy adoption is part of this plugin cleanup.

## API and private operations

Last verified production snapshot on 2026-09-12: `d6b7d2b508ff9d5934697de5bf7b8a81179aef53`,
containing the existing palette/wearable compatibility fix `105fb6f4` plus
private failure notices. This is separate from the plugin diff.

- API schema v3 already accepts the plugin's equipment/colors/overrides/follower
  and idle pose recipe. Renderer and API share `appearance-contract.json`.
- API distinguishes ready (200), accepted/pending (202), render failure (503),
  superseded work (409), rate limits and recovery states. The plugin needs to
  preserve those distinctions; weakening API validation is not the remedy.
- Registration/publish failures and asynchronous render failures now reach a
  private event feed and configured Telegram notifier with fixed reason codes
  and random support refs. Notices contain no player names, recipes or credentials.
- The running shared-client classpath includes Hive Ops `1739c48c`.
  Actual Telegram delivery and real-client chat/sidebar
  delivery still require one controlled failure smoke.
- Recovery/claim/cancel failures are logged but are outside the current alert
  filter. Local capture and transport failures may never reach the API. Decide
  whether actionable recovery failures need private notices; routine waiting and
  cancelled requests should not page the operator.
- Render notices use a separate ref from the originating HTTP request. Correlating
  the asynchronous job ref with the user-visible ref would improve support.
- Dedup is five minutes per stage/status/reason, capped at 12 notices/minute.
  Alert delivery is best effort. Pre-auth rejection noise and shared feed retention
  remain non-blocking operational follow-ups, not proof of a successful publish.

## Preserved broader redesign, excluded from this candidate

The former canonical design is preserved at tag
`archive/20260912/2.4.0-earlier-design` (`8e7cccca`, including the `9c9280e3`
checkpoint). Do not merge it wholesale over the current canonical candidate.

- Slayer: Superiors, Light/Dark/Dusk Mystic Sets, then a deduplicated remainder.
- Defence: curated melee/ranged/magic armour plus defenders through Avernic hilt.
- Remove Slayer stats/Superior item content from PvM Summary.
- Larger Clog Summary redesign: Total/completion, current tier sprite and complete
  tier legend, five native-tab totals/placeholders, Special renamed Highlights.
- Supporting fixed-row/adaptive layouts and old parallel renderer cleanup.

Some matching/hover/lookup work from that branch is already in the candidate.
Only the remaining design differences should be considered for a later slice.
PB privacy separation, Armoury, cache-delete buttons and provider-routing changes
are not part of this submission boundary.

## Final release gates

Approved baseline: e403c7d8, 624 tests, compile/Checkstyle/jar gates, independent
reviews, Dylan's quiet-update smoke, and exact running-jar verification.
Ownership correction: 637 tests, zero failures/skips; compile/Checkstyle/jar gates
passed with JDK 11.0.30 and resolved RuneLite 1.12.38. New jar: 553,880 bytes,
SHA-256 51ac58985105f1d0f3a3aa772dc6eaf44c128997ddbd31fb8b48cd112098bc5a.
That ownership-only jar is superseded for the next smoke by the total/catalog
follow-up: 640 tests, zero failures/skips; compile, both Checkstyle gates and jar
passed. New jar: 553,848 bytes, SHA-256
e4b987671231ca2e1e973dd0452a253ac93db242b25e1c581fcc91afcde9ee20.
Dylan supplied Fable's ALLOW for `c1f6b12e..05715328` on 2026-09-12. Fable
independently reran `./gradlew.bat --offline test checkstyleMain checkstyleTest
--rerun-tasks`: build successful, 7 tasks executed, 640 tests, zero failures,
errors or skips. The six-file +228/-68 slice and three-file production +20/-41
counts matched. Fable checked the existing jar size/hash but did not rebuild it.
Canonical consolidation changed release documentation only; the reviewed code
and jar remain unchanged. Focused smoke remains below. Keep the approved UI frozen.

Review cautions: live totals now depend on game counters rather than chat-side
increments. A total may trail the unlock message by a tick; verify it advances
without opening the Collection Log. Fable's catalog-completion ordering note was
non-blocking: current production callers do not immediately re-fetch from a
completion continuation. No further code change was requested.

- [x] Reproduce architectural review F1: four temporary-file regressions fail
      against the approved baseline (foreign adoption, malformed ledger, foreign
      rename destination, and queued overwrite after ledger loss).
- [x] Finish independent review of the ownership correction (final ALLOW).
      The first review caught a writer-lock race and recovery regressions; the
      revision queues all arbitration on the disk writer, preserves damaged
      owned/unclaimed files before setup retries, and keeps provider-only caches
      writable. Readable foreign captures and unreadable ledgers remain blocked.
- [x] Current total/catalog candidate smoke accepted by Dylan on 2026-09-12.
      Earlier smoke plus independent gates are accepted for submission; no claim
      is made that login, restart, account switching and cold lookup were all
      newly repeated against this jar.
- [x] Real-unlock smoke requirement accepted by Dylan using the prior observation:
      Clog Summary increased once from 1197 to 1198, while two chat messages
      appeared. He explicitly waived another unlock-based rerun because unlocks
      are scarce. Duplicate chat output is recorded, not claimed fixed.
- [x] Integrate and validate the acquisition-date API companion against actual
      production f34f575f, retaining its newer Recent-source selection.
      Worktree `C:/Users/dylan/.codex/worktrees/collection-unlock-dates`, branch
      `hive/collection-unlock-dates`, reviewed tip `9e450c83` (date code 16e0f3fb).
      Independent review ALLOW; API suite 258 pass / one skip, renderer 17 pass,
      and real website normalizer fixture pass. Built renderer SHA-256 equals
      production: 2866c99ebbbb1077a14d7fe8a112032cb49cbe56866e79bb622d945d5fdb8c15.
- [x] Approve/deploy the reviewed date companion. Dylan approved; integrated
      with live Ops and deployed at `69a70503` on 2026-09-13 02:34 UTC.
      Independent integration ALLOW; 264 API passes / one existing skip;
      renderer unchanged, health/security checks passed, publishing re-enabled.
- [ ] Confirm live web Recent after a normal manual sync carrying saved dates.
      Undated first-party profiles gain known acquisition history on their next
      sync; existing dated provider history remains available. The already-missed hat is not repaired.
- [x] Recheck README setup/update/web-sync wording; all ten referenced local
      images exist. Badge matches Dylan's solid #551919 swatch and was visually
      checked against the real Shields endpoint. Setup notes now say totals follow
      the game's count. No additional catalog-retry instructions are needed.
- [x] Recheck Hub: PR #16418 is OPEN, not merged. Accepted pin is still 2.3.2
      c1eb9773cb730f568fd73115568afd0677635150 as of 2026-09-12.
- [ ] After 2.3.3 is accepted, prepare master and obtain push/submission approval
      for the final 2.4 Hub pin.

Automated failure coverage includes incomplete captures, house-host exclusion,
account switches, consent cancellation, duplicate requests, recovery responses,
rate limits and transport loss. Extra account/body-type/recolor/empty-account
smokes are useful when available; these are coverage opportunities, not a new
feature backlog. Reopen implementation only for a concrete failure.

Companion operations: one controlled API failure should verify actual Hive Ops
chat/sidebar and Telegram delivery. This is separate from plugin layout approval.
The seven-day recovery policy, expanded recovery alerts and asynchronous support
reference correlation remain separate decisions, outside this release slice.

Audit follow-up and Fable review scope:

- F2 fixed in 31d4f597: drop events record membership/date/KC without incrementing
  the scalar a second time. Game varp/broadcast counts own the saved total; a full
  capture remains the downward correction. Tests cover counter-first, chat-first,
  zero-to-one, duplicate/shared-page notifications, consecutive unlocks, persisted
  totals and date/KC retention. The now-unused obtainedAnywhere helper was removed.
- F3 fixed in 2bff127c: both catalog methods register a stable local future before
  attaching synchronized, identity-checked cleanup and return that local reference.
  Tests cover immediate transport/parse failure, delayed shared failure, retry
  success, immediate success, and retained successful catalog caching.
- F4 remains deferred: red comparison PB parity is ordinary maintenance.
- Latest slice: `c1f6b12e..05715328`, six files +228/-68 including tests and README;
  production Java alone is three files +20/-41. Both regressions were reproduced
  before the fixes. The live badge still reports the actual shipped Hub version.
- For a combined review of ownership plus these fixes, use `2bf9c639..HEAD`.
  For full release scope, use 2.3.3 `96dee242..HEAD`. Do not treat the prior
  ownership ALLOW as approval of the subsequent total/catalog changes.
- Dylan arranged Fable independently and supplied ALLOW; no agent was invoked
  for these fixes. Independent gate details and remaining smoke are recorded above.
- Broader dead-code deletion, package moves, coordinator extraction and a new
  release branch remain parked. Acquisition-date API deployment is complete;
  normal-account sync/web Recent confirmation remains above.

UX closeout: opening the log handles local setup/update/repair; matched drops
update local Recent immediately while self is displayed; web sync publishes saved
data; character publication stays separately opt-in. No extra repair button,
settings, retry dialog or manual self-lookup requirement is needed.

Next page direction (not implemented): a lightweight 2.4.0 page for killclog.com
with a short introduction, three player-facing highlights, one approved screenshot
and the Plugin Hub link. Call it a preview until the release is available on Hub;
do not turn the public page into this internal checklist.

Historical release receipts follow unchanged.

## Prior release receipt

# Kill Clog 2.3.3

Status: accepted for Plugin Hub submission after independent review and manual smoke.
Availability in the Plugin Hub still depends on the update PR being merged.

Base: `fb136da978449349e330bf87748460a0ff51ee6e` on master.
Its plugin source matches shipped 2.3.2. The separate 2.4.0 feature branch is
not part of this release.

## Release notes

* fixes first-time Collection Log setup when public provider data is already cached
* starts first-time setup automatically when opening your own Collection Log, including empty accounts; manual Search remains available for retries
* recognizes native Search callbacks that do not emit a menu click and excludes the house host's Adventure Log
* records a completed empty Search separately from having items available to sync
* requires complete mapped Search evidence before marking setup complete; incomplete captures can be retried
* stops guessing hidden item ownership from category totals and retains previously observed hidden items
* selects local personal-best profiles by RuneLite account identity, preserving progress across name changes
* protects existing cache files when a save fails and drains pending saves in order across plugin disable/re-enable
* isolates lookup timeouts so shared provider requests can still finish and populate their caches
* cancels pending comparison lookups on Escape, comparison disable, and panel shutdown
* keeps the previous comparison player coherent until a replacement's identity, hiscores, and clog are ready
* avoids CSV requests for definitive hiscore 404s, falls back for malformed JSON, and retries failed tables under a bounded budget
* uses known self account types to reduce hiscore requests while keeping current stats and fetching other leaderboard ranks on demand
* removes unused comparison and tooltip paths while preserving the live two-card comparison and 2.3.2 catalogs

Existing opt-in publication, account-hash sync, CA authority, provider precedence,
PB variants/source tags, configuration keys, and cache paths stay intact. The
nullable setup marker is additive. Existing legacy ownership records are not
destructively rewritten; historical guessed records cannot be identified reliably.
Atomic file replacement is not a guarantee against power loss.

Negative caching and broad architectural extractions are deferred. Manual retries
continue to reach Jagex immediately.

## Validation and smoke coverage

Automatic Search uses the specific Collection Log sync permission confirmed by
[Riktenx on June 22](https://github.com/runelite/plugin-hub/pull/12380#issuecomment-4773784134).
[RuneProfile's merged restoration](https://github.com/runelite/plugin-hub/pull/12826)
cites the same permission. This is not a general menu automation exception.

Compile, both checkstyle checks, and all 578 tests passed, including an independent
rerun. On September 11, automatic setup passed an in-game smoke on a zero-item
account: opening the log produced reading and completion notices without a manual
Search click, and the saved cache recorded completed setup with no obtained items
or first-party item marks. Populated/provider-preseed capture, partial streams,
duplicate requests, and house-host exclusions are covered by regression fixtures.
The zero-counter shortcut has been removed; empty and populated logs use Search.

The release smoke checklist below remains available for future regression checks.
Always confirm the loaded jar/version when using a development client.

- On an account with provider data but no first-party setup, open Collection Log
  and let automatic capture finish. Restart and confirm setup remains complete.
- On an established account, refresh an ordinary category and a category with
  hidden items. Confirm visible ownership/quantities, retained history, and
  unchanged comparison tooltip geometry.
- If an empty account is available, confirm automatic Search completes setup
  without creating items or enabling an empty upload.
- Confirm the current game's obtained-item Search stream matches the completion
  counter, including untradeables/hidden items and a retry after interruption.
  Fixture tests cannot establish the live script 4100/varp 2943 contract.
- Check self PB display and prepared sync data on the intended account, including
  a renamed account where available. Publishing remains explicitly opt-in.
- Start a comparison, cancel before it appears, and retry. Replace an active
  comparison and confirm its name/data remain coherent while loading. Toggle
  the plugin off/on during a lookup and verify old results do not reappear.
- Search a known player and a missing name. Select Normal, Ironman, Hardcore,
  Ultimate, Skiller, and Pure ranks; confirm stats and account identity stay
  coherent when ranks are unavailable or historical.
- Capture clog/CA progress, disable/re-enable, and restart to confirm persistence.

The plugin becomes available to users after the Plugin Hub update is merged.

## Previous release: 2.3.2

Released version: `2.3.2`

[Plugin Hub update #16177](https://github.com/runelite/plugin-hub/pull/16177)
merged on 2026-09-08 and pins `c1eb9773cb730f568fd73115568afd0677635150`.
The notes below record the previous shipped release.

## Release Notes

Kill Clog 2.3.2

* adds an optional leaderboard selector under Lookup, with Normal, Ironman, Hardcore, Ultimate, Skiller, and Pure icons below the grid
* changes ranks without changing detected account identity, levels, XP, KC, Collection Logs, or personal bests
* uses the selected leaderboard for both players in comparison; new primary lookups return to automatic selection
* clears unavailable ranks and labels loading, unavailable, or historical results on the selected icon's hover text
* reuses recent hiscores responses and fetches other leaderboard ranks on demand
* shows Unranked for missing entries on a loaded leaderboard, without treating loading or failed requests as unranked
* includes Abyssal protector in Runecraft's Pets section and adds Phoenix under Firemaking Pets; shared pets still count once toward each skill total
* lets long chat clogs wrap between items while keeping duplicate counts with their sprites
* expands !kclog [page] and !missing [page] to the full Collection Log catalog, including All Pets, Mastering Mixology, and Random Events
* shows pet duplicate quantities with !kclog pets; !3a and !gilded now show quantities too
* replaces loose substring matching with full page names and explicit shortcuts, fixing random events matching ven for Venenatis
* uses the panel's collection-log source selection for chat while keeping local self data authoritative
* documents command ownership and the complete page/shortcut chart; RuneLite commands and RuneProfile's !log handler remain intact
