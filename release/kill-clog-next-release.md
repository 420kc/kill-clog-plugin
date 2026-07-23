# Kill Clog Next Release

Target version: `1.6.4`

Current branch: `1.6.4` (squash merges to `master` for the hub pin)

## Release Notes

Kill Clog 1.6.4

* boss tooltips get kc in addition to rank and obtained, PB included where available
* new "!kc [item name]" command to reveal kill count it was obtained on, starting with items collected on v1.6.4
* TempleOSRS dates fill in older undated items on self lookup, and the last updated notice tracks the newest item
* comparison summary format fixes
* pvp summary and empty cells follow configured palette
* long tooltip title spacing fix
* kc, pb, and rank tooltip lines each get a settings switch, all on by default
* !kclog resolves brand new bosses before a sync has seen them, starting with Maggot King
* combat achievement tiers follow the game's live thresholds, so a new CA batch counts from day one without waiting on syncs
* comparison lookups get the same timeouts as the primary search, riding one shared pipeline under the hood
* removed rounding from slayer xp in PvM summary
* skill summary reshuffled: per-skill xp and rank sit right under the title, total exp anchors the bottom
