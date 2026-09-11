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
