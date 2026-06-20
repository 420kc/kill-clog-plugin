# Kill Clog Next Release

Target version: `1.4.4`

Current branch: `master`

This release is the post-1.4.3 line. It should include the latest tooltip,
comparison, chat-command, and no-data polish already on `master`, while keeping
the client-to-Kill-Clog-server sync prototype out.

## Included

- Clickable collection-log item sprites in single and comparison image tooltips.
  Hovering an item shows its name; clicking opens the OSRS Wiki item page.
- Tooltip item names follow the same completion convention as the item grids:
  obtained names are green, unobtained names are red.
- A config toggle for Wiki item links, default on.
- A config toggle for autosync chat messages.
- Comparison total labels use the same hover/underline affordance as the player
  names.
- `!kclog` and `!missing` headers include boss KC when Kill Clog knows it.
- `!3a` follows the panel bucket: 3rd age ring stays under Mimic, so the
  command reports out of 23 3rd age items.
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
- disabling Autosync chat messages suppresses `Added ... to Kill Clog` messages
- no client upload/server-sync config appears in the plugin settings
