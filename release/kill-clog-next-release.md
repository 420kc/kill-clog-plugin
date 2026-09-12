# Kill Clog 2.4.0 candidate

Status: Dylan-approved, smoke-passed local candidate on 2026-09-12. Product scope
is frozen. Submission remains gated by the ownership-recovery review finding and
the date API rollout below. Master and the preserved 2.3.4 checkpoints are unchanged.

- Active branch: `hive/2.4.0-candidate`.
- Active checkout: `C:/Users/dylan/.codex/worktrees/killclog-240-candidate`.
- Approved candidate code: `e403c7d897e414ec37de9de742ac161beafdd9d9`.
- Submission base: 2.3.3 `96dee2429ed96187e36d1451270b114f7a9dbd07`.
- 2.3.3 Hub PR: https://github.com/runelite/plugin-hub/pull/16418
  Last checked open on 2026-09-12; recheck the accepted pin before submission.
- Historical checkpoints: `hive/2.3.4-submission-candidate` (`d18f41e7`)
  and `hive/2.3.4-log-refresh` (`1c3df015`). Keep their names and jars as history.
- Approved jar: `kcpdev-e403c7d8-E31A0EDAA84C.jar`, 552,802 bytes.
  SHA-256: `E31A0EDAA84CBBB9A2FEA810FB2903F70A16B22B6E152099E4F3B85B99EC3DAA`.
  Stored under `C:/Users/dylan/plugins/dev-client/.prepared-refire/plugins/`.
  The running shared client was verified to load this exact jar when Dylan
  reported the quiet-update smoke passed. Preserve these bytes; docs-only
  closeout commits do not require a replacement build or another visual smoke.
- Previous candidate `85d71da9` / code `1952dfeb` is retained as
  `hive/2.4.0-before-unlock-history`; its immutable prepared jar also remains.

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
    Recent, separately from API receipt times. Requires the companion API rollout.
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
At `a852a227`, tracked main Java was 109 files / 29,339 lines / 229,293 `o200k_base` tokens
(sum per file). This is a source-size measurement, not account usage or proof
against an old token ceiling; the 2.3.3 baseline measures 223,885 the same way.

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

## Final release gates

Completed: candidate code committed; 624 tests, compile, Checkstyle and jar gates;
independent implementation reviews; Dylan's candidate/quiet-update smoke; exact
running-jar verification. Keep the approved UI and behavior frozen.

- [ ] Validate architectural review F1 against current code: a missing/malformed
      identity ledger may let an explicitly owned cache be adopted under another
      account. Reproduce with isolated fixtures and fix if confirmed before Hub
      submission. The date and quiet-refresh reviews did not close this finding.
- [ ] Approve/deploy the acquisition-date API companion, then verify plugin sync
      and web Recent. Worktree `C:/Users/dylan/.codex/worktrees/collection-unlock-dates`,
      branch `hive/collection-unlock-dates`, tip `f76ff14d`, code `16e0f3fb`.
      Local API integration tests and the real web normalizer passed; no deployment
      occurred in this slice. Existing receipt-only profiles can have no Recent
      until a new sync supplies known dates. The already-missed hat is not repaired.
- [ ] Confirm final README/screenshots, recheck 2.3.3's accepted Hub pin, then
      prepare master and obtain push/submission authorization for one Hub pin.

Automated failure coverage includes incomplete captures, house-host exclusion,
account switches, consent cancellation, duplicate requests, recovery responses,
rate limits and transport loss. Extra account/body-type/recolor/empty-account
smokes are useful when available; these are coverage opportunities, not a new
feature backlog. Reopen implementation only for a concrete failure.

Companion operations: one controlled API failure should verify actual Hive Ops
chat/sidebar and Telegram delivery. This is separate from plugin layout approval.
The seven-day recovery policy, expanded recovery alerts and asynchronous support
reference correlation remain separate decisions, outside this release slice.

The supplied architectural review also lists F2 (event-order total overcount),
F3 (catalog request cleanup race), and F4 (comparison PB data). These remain
unvalidated follow-ups, not silently completed work. Triage them before final
release approval; none requires reopening the approved layout or a package rewrite.

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
