# Kill Clog Next Release

Target version: `1.6.0`

Current branch: `master`

## Release Notes

Kill Clog 1.6.0:

- Reworks unsynced collection-log popups so public lookups without synced clog data still show native hiscore context, rank rows, and explorable dimmed item grids.
- Builds unsynced tooltip data from the in-game collection-log index first, with provider catalog fallback, so item names and sprites match synced data.
- Extends unsynced tooltip support across boss cells, clue tiers, rare buckets, comparison mode, and activity-tray summary surfaces.
- Adds a Slayer row to PvM Summary, showing collection-log progress when synced and XP/rank context when unsynced.
- Adds a Superiors section to PvM Summary with Imbued heart and Eternal gem obtained/unobtained sprites.
- Brings the Slayer and Superiors PvM Summary rows into comparison mode.
- Moves TempleOSRS/RuneProfile provenance into Clog Summary only, behind a compact green hover badge that reveals source rows below Recent.
- Fixes comparison item-grid sprites so each player uses their own stack counts for stackable item variants.
- Adds skill-hover stats in solo and comparison Skill Summary tooltips, swapping the GOTR footer for rank and XP while hovering a skill.
- Adds chat emoji aliases for `:green:`, `:rune:`, `:dragon:`, and `:gilded:`.
- Shrinks the `:killclog:` chat icon to match the smaller inline clog sprites and hardens plugin icon fallback loading.
- Centralizes Kill Clog icon loading across chat emoji, panel, nav, sync, and collection-log overlay surfaces.
- Polishes clue-summary rank styling so only the `#` is orange and the rank value remains white.
- Adds regression coverage for source-badge sizing, tier emoji rewrites, `:killclog:` icon loading/sizing, and per-player comparison sprite counts.

This release is the post-1.5.1 line. It keeps the client-to-Kill-Clog server
sync prototype out.

## Included

- Full dimmed collection-log item grids for unsynced public lookups, preserving
  wiki hover and click exploration even before the player has synced collection
  log data.
- Native-style unsynced stat rows:
  - boss popups use `Kills:`
  - clue popups use `Score:`
  - rank remains a separate `Rank:` row where the hiscore tracks it
- Shared unsynced catalog plumbing through primary and comparison cell systems.
- Tooltip item-name resolution that refreshes both primary and comparison
  unsynced popups once names resolve.
- PvM Summary Slayer row in solo and comparison views.
- PvM Summary Superiors section in solo and comparison views.
- Clog Summary provider-source hover badge for TempleOSRS, RuneProfile, or both.
- Per-player stack-count sprite loading in comparison image tooltips.
- Skill Summary hover stats for solo and comparison tooltips.
- Chat aliases for the green clog icon and rune/dragon/gilded tier sprites.
- Smaller, safer `:killclog:` emoji rendering.
- Shared Kill Clog icon helper with collection-log-book fallback.
- Tests covering the new source badge, chat icon, and comparison sprite-count
  behavior.

## Excluded

- `experiment/kc-sync-prototype`: server-side Kill Clog sync/upload from the
  client. This branch is intentionally out of the release until the product,
  privacy, and backend paths are ready.
- Stale boss-tooltip crest experiments from `next-release` /
  `fix/kc-tooltip-name-wrap`.
- Killclog.com web parity work. This release is focused on the RuneLite plugin.

## Required Gates

```powershell
.\gradlew.bat compileJava checkstyleMain checkstyleTest test
.\gradlew.bat jar
```

Then Dylan real-client smoke:

- unsynced boss tooltip shows `Kills:`, `Rank:`, and dimmed item sprites with
  wiki links
- unsynced clue and rare bucket popups show the same dimmed catalog behavior
- comparison mode shows unsynced boss, clue, and rare popups for the red player
- PvM Summary shows Slayer in solo synced, solo unsynced, and comparison cases
- PvM Summary shows Superiors with Imbued heart and Eternal gem sprites
- synced Slayer level value is white and the `(x/y)` progress uses stoplight
  coloring
- unsynced Slayer row shows XP and rank, for example `XP: 13.3M #429`
- Clog Summary source badge is dim at rest, bright on hover, and reveals
  TempleOSRS/RuneProfile source rows below Recent
- boss clog tooltips do not show provenance badges
- hovering a skill in solo or comparison Skill Summary shows rank and XP in
  the footer, then returns to GOTR when the hover leaves
- `:green:`, `:rune:`, `:dragon:`, and `:gilded:` render as chat icons
- `:killclog:` renders as the smaller chalice icon without the Emoji plugin
- comparison item-grid sprites use the correct per-player stack-count variants
