# Kill Clog 2.4.0 candidate

Status: local preparation, not ready for submission. This supersedes the active
2.3.4 release name; it does not alter the preserved 2.3.4 checkpoints or master.

- Active branch: `hive/2.4.0-candidate`.
- Active checkout: `C:/Users/dylan/.codex/worktrees/killclog-240-candidate`.
- Candidate behavior baseline: `1c3df015a9fb8a0f8b37879812be1f0c8aee569f`.
- Submission base: 2.3.3 `96dee2429ed96187e36d1451270b114f7a9dbd07`.
- 2.3.3 Hub PR: https://github.com/runelite/plugin-hub/pull/16418
  Still open on 2026-09-12; build passed, maintainer review required.
- Historical checkpoints: `hive/2.3.4-submission-candidate` (`d18f41e7`)
  and `hive/2.3.4-log-refresh` (`1c3df015`). Keep their names and jars as history.
- No 2.4 jar has been selected for the shared client yet. A previously prepared
  2.3.4 jar does not become 2.4 merely because this checkout's version changed.

## Included changes from 2.3.3

1. Keep all modal titles stable during item, skill and source hovers.
2. Put item names beneath their own sprite sections, with 2px text centering;
   retain the restored original spacing and bounded sizing for dense cards.
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

## Current preparation proof (2026-09-12)

Version-only preparation passed compile, both Checkstyle gates, jar build and
593 tests (zero failures/errors/skips). Production behavior remains `1c3df015`;
only CLIENT_VERSION changes to 2.4.0. No new real-client smoke is claimed.
The 2.4 jar was built locally but was not selected for shared refire.

Live API health and the private deployment marker were checked again. The bind
verifier now matches this release, the render queue is idle, and the retained
API log contains no appearance request/failure notice events since the notice
deployment. That gives no new real-user failure-path coverage or delivery proof.

## Character publication cleanup

Implemented locally after `5469f66d`; independent review and a new real-client
smoke are required before adoption.

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

Verified production on 2026-09-12: `d6b7d2b508ff9d5934697de5bf7b8a81179aef53`,
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
- Hive Ops `1739c48c` is built/reviewed and prepared for the next shared refire,
  not proven loaded. Actual Telegram delivery and real-client chat/sidebar
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

`C:/Users/dylan/plugins/kcpdev`, branch `hive/2.4.0` at `9c9280e3`, remains a
separate design checkpoint. Do not merge it wholesale over the new candidate.

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

## Remaining submission checklist

- [ ] Independently review the completed character-publish cleanup above.
- [ ] Run compile, both Checkstyle gates, full tests, relevant render fixtures and
      source-size checks on the final exact commit. Recalculate base-to-head size.
- [ ] Stage API contract/recovery/queued-render cases with authorized test accounts;
      verify ready player and follower models, signed recolors, both body types,
      palette extremes, changed equipment and repeat publication.
- [ ] Check privacy: opt-in off by default, prerequisite profile sync, settings
      disable mid-flight, logout/account switch, existing deletion/revocation
      behavior, and no secrets/raw API bodies in user text or notices.
- [ ] Build and select an immutable 2.4.0 jar through Plugin Studio after code is
      ready; prove the loaded version/hash before Dylan's real-client smoke.
- [ ] Smoke fresh/empty and existing-cache log opens, manual Search, interrupted
      capture, house-host exclusion, quantity repair, dates and restart persistence.
- [ ] Smoke labels below sprites across PvM, Clog, Player, Skills and Sailing;
      long names, comparison, pointer exit and wiki targets. No title replacement.
- [ ] Smoke Tithe Farm variants, Mining/Fishing/Hunter pet totals and missing-player
      lookup while comparison is active. Keep per-skill shared-item deduplication.
- [ ] Smoke character first publish/repeat, unsupported follower/transform,
      queued render and actionable failure; verify one controlled private notice
      in Hive Ops and Telegram. Do not use production failure spam as a test.
- [ ] Approve final terse README/release notes and any outdated screenshots.
- [ ] Recheck 2.3.3 Hub merge and its exact accepted pin before preparing the next
      submission. Preserve its submitted branch while review is open.
- [ ] After final review and Dylan smoke, prepare master, obtain push/submission
      authorization, and create the Hub update with one pin commit. Recheck CI.

The latest behavior baseline had 593 passing tests and prior refresh reviews.
Those receipts do not constitute approval of unfinished character work or a fresh
2.4 real-client smoke. Historical release receipts follow unchanged.

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
