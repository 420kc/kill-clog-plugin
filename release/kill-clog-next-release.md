# Kill Clog Next Release

Target version: `1.4.3`

Current branch: `master`

This release is the post-1.4.2 line. It should include the latest tooltip,
comparison, chat-command, and no-data polish already on `master`, while keeping
the client-to-Kill-Clog-server sync prototype out.

## Included

- Clickable collection-log item sprites in single and comparison image tooltips.
  Hovering an item shows its name; clicking opens the OSRS Wiki item page.
- A config toggle for Wiki item links, default on.
- Config toggles for Kill Clog chatbox notices: new drops, sync help, sync
  results, and warnings.
- Comparison total labels use the same hover/underline affordance as the player
  names.
- `!kclog` and `!missing` headers include boss KC when Kill Clog knows it.
- Rare bucket tooltips use real zero-state data instead of implying missing
  collection-log data for synced players with no 3rd age or gilded items.
- Summary tooltip widths measure real values and long ranks instead of leaving
  awkward fixed gaps or clipping.
- Comparison no-data copy is quiet: true missing data appears as a dimmed dash
  in the player's comparison color.

## Excluded

- `experiment/kc-sync-prototype`: server-side Kill Clog sync/upload from the
  client. This branch is intentionally out of the release until the product,
  privacy, and backend paths are ready.
- Stale boss-tooltip crest experiments from `next-release` /
  `fix/kc-tooltip-name-wrap`.

## Required Gates

```powershell
.\gradlew.bat compileJava checkstyleMain checkstyleTest test
```

Then Dylan real-client smoke:

- item tooltip hover name appears above the separator
- item click opens the OSRS Wiki instead of closing the tooltip
- comparison totals keep their hover/underline affordance
- `!kclog` and `!missing` show KC where known
- disabling Wiki Item Links keeps hover names but prevents wiki opens
- disabling New Drops suppresses `Added ... to Kill Clog` messages
- no client upload/server-sync config appears in the plugin settings
