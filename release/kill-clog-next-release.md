# Kill Clog Next Release

Target version: `1.5.1`

Current branch: `master`

## Release Notes

Kill Clog 1.5.1:

- Renames the regular 1-defence account label from Defense Pure to Pure.
- Adds `:killclog:` and `:clog:` chat icons.
- Keeps boss KC parsing aligned after Jagex's Maggot King hiscore row.
- Carries forward 1.5.0 support for Pures, Skillers, boss Wiki page links, and
  collection-log tooltip polish.

This release is the post-1.5.0 line. It should keep the client-to-Kill-Clog
server sync prototype out.

## Included

- Boss names in boss collection-log tooltips link to their OSRS Wiki page when
  Wiki Links are enabled.
- Clickable collection-log item sprites in single and comparison image tooltips.
  Hovering an item shows its name; clicking opens the OSRS Wiki item page.
- Tooltip item names follow the same completion convention as the item grids:
  obtained names are green, unobtained names are red.
- Regular 1-defence pures and level-3 skillers auto-refine to their specialty
  hiscore table after the normal lookup identifies the account shape. Iron
  accounts stay on their ironman hiscore table.
- Pure and skiller accounts now show their native hiscore badge in the
  infobar and their account label in player summary tooltips.
- Starting a new lookup clears the PvP summary cell immediately, so a failed or
  pending search cannot keep stale PvP data from the previous player.
- A config toggle for Wiki item links, default on.
- A config toggle for autosync chat messages.
- Comparison total labels use the same hover/underline affordance as the player
  names.
- `!kclog` and `!missing` headers include boss KC when Kill Clog knows it.
- `!3a` follows the panel bucket: 3rd age ring stays under Mimic, so the
  command reports out of 23 3rd age items.
- `:killclog:` renders the red chalice and `:clog:` renders the collection log
  book in player chat.
- Maggot King hiscore parsing is guarded so later bosses keep the right KC
  values while RuneLite catches up with the official boss enum and sprite.
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
- Manual hiscore endpoint selector UI. Pure/skiller lookup is automatic for now.

## Required Gates

```powershell
.\gradlew.bat compileJava checkstyleMain checkstyleTest test
```

Then Dylan real-client smoke:

- item tooltip hover name appears above the separator
- item click opens the OSRS Wiki instead of closing the tooltip
- boss tooltip title hover turns white and click opens the boss Wiki page
- regular 1-defence/skiller lookup waits for the refined hiscore table before
  painting ranks
- regular 1-defence/skiller infobar badges and player summary account labels
  match the refined hiscore table
- comparison totals keep their hover/underline affordance
- `!kclog` and `!missing` show KC where known
- `:killclog:` and `:clog:` render as chat icons without the Emoji plugin
- Maggot King does not shift Mimic/Nex/Zulrah KC values
- disabling Wiki Item Links keeps hover names but prevents wiki opens
- disabling Autosync chat messages suppresses `Added ... to Kill Clog` messages
- no client upload/server-sync config appears in the plugin settings
