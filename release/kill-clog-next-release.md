# Kill Clog 2.3.4 preserved submission candidate

Status: Dylan-approved for preservation and submission preparation on 2026-09-11.
The final combined independent review is ALLOW with no required code corrections.
Nothing was pushed or submitted during this task. Plugin Hub availability is not
claimed by this receipt.

- Candidate: `d18f41e7b6652128cf37e7a2fcd4f65f8b8d27ca`.
- Local preservation tag: `hive/2.3.4-submission-candidate`.
- Branch: `hive/2.3.4-modal-polish`.
- Worktree: `C:/Users/dylan/.codex/worktrees/killclog-234-modal-polish`.
- Base: accepted 2.3.3 `96dee2429ed96187e36d1451270b114f7a9dbd07`.
- Exact diff: 35 files, +1174/-234; production Java: 20 files, +678/-186.
- Prepared jar: `kcpdev-d18f41e7-453FAEFA88AD.jar`, 541679 bytes.
- SHA-256: `453FAEFA88AD29D8C237C200D6342871BFBB795B3C86340C9E347823526CE45F`.

Scope: fixed modal titles, reserved hover labels above sprite rows, 2px label
offset and 4px separation before the next section; bounded dense-card layouts;
item-variant matching with strict canonical-slot setup checks; failed-lookup
catalog recovery; four small skill-catalog corrections with deduplicated totals;
and source/setup wording. Self captures say Local Collection Log, web results
say Kill Clog, and Temple remains credited when supplying item dates. Self
lookups still avoid redundant RuneProfile and killclog.com clog requests.

Independent proof: full build, 590 tests with no failures/errors/skips, clean
Checkstyle, 83 focused tests, and approximately 110 geometry assertions. The
review covered the entire base-to-candidate diff in an isolated worktree.
Full report: `C:/Users/dylan/.codex/visualizations/2026/09/11/01a08de4-ec0e-7063-9a9a-4d59f436d0fc/killclog-review/234-final-review.md`.

Remaining release checks: confirm variant/category totals and failed primary
lookup behavior while comparison is active. A fresh 1197-item setup capture
was observed during this task; final-label real-client verification was not
separately recorded. Preserve the candidate unchanged until release follow-through;
any further code changes need their own verification. Push and Hub submission
still require authorization.

This plugin task is parked. The next requested lane is the Armoury build for
staging on killclog.com, in a fresh conversation starting at
`C:/Users/dylan/420live/killclog.com-repo`. Re-prove that repository's state and
the staging target, then load its applicable instructions before implementation.
No Armoury implementation, staging deployment, or PB privacy change was started
here. PB privacy and broader provider-provenance presentation remain parked.

## Kill Clog 2.4.0 work branch

The preserved 2.3.4 changes have not yet been integrated into this branch.
The following describes the existing 2.4.0 code baseline; this shutdown update
changes release documentation only.

Status: local development, not submitted to Plugin Hub. Version fields are 2.4.0.
Includes the accepted 2.3.3 setup and hardening release at `96dee242`, together
with the existing 2.4.0 Skill Clog layouts, Collection Log summary redesign, and
item-variant normalization. Preserve 2.3.3's automatic setup and strict capture
checks when extending these features.

The First-time setup README copy is prepared here for the 2.4.0 release. Keep
these changes local until that release is ready for the Hub; this branch does
not change the submitted 2.3.3 pin or its published README.

The integrated capture path counts canonical log slots before accepting a full
Search walk. Multiple forms of one item cannot fill a missing different slot.
The merge regression tests cover canonical persistence and partial-slot rejection.
Further 2.4.0 development still needs its own review and real-client smoke.

## Included release: Kill Clog 2.3.3

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
