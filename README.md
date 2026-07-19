# Kill Clog

[![version](screenshots/version-badge.svg)](https://github.com/420kc/kill-clog-plugin/tags)

Kill Clog is a HiScores overhaul that offers fast and easy visuals of collection log progress and account snapshots.

![Kill Clog](screenshots/hero-vanilla-vs-killclog.png)

![comparison](screenshots/comparison-1.4.0.png)

![summaries](screenshots/summaries.png)

## privacy and data

![install-warning](screenshots/install-warning.png)

Public player lookups read from [TempleOSRS](https://templeosrs.com) and [RuneProfile](https://runeprofile.com). The install warning is about those external lookups. Kill Clog does not use `killclog.com` as a player-profile source or upload your clog data.

The EHB stat in the PvM summary uses EHB rates by TempleOSRS, bundled with the plugin and refreshed each release. No extra requests are made to compute it.

Source code is public at [github.com/420kc/kill-clog-plugin](https://github.com/420kc/kill-clog-plugin) and open to audit.

## installation

1. Install Kill Clog from the RuneLite Plugin Hub.
2. Optional, but useful for public visibility: keep your TempleOSRS and/or RuneProfile data synced so other players can see your public clog.
3. Sync local collection log: Open your collection log in-game and click the red chalice at the top, then chat messages will guide you to right click search your collection log and click back.

## auto-sync

Once the first sync has built your local cache, collection-log chat messages keep
new drops current during long sessions.

## quick lookup

![player-menu](screenshots/player-menu.png)

## chat commands

![chat-kclog](screenshots/chat-kclog.png)

![chat-missing](screenshots/chat-missing.png)

`!kclog [boss]` shows your collected items.

`Vorkath: 12/14 [item] [item] [item] ...`

`!missing [boss]` shows unobtained items.

`Vorkath: 2/14 missing [item] [item]`

`!3a`

`!gilded`

## configuration

![config](screenshots/configuration-1.4.0.png)

- Auto-Lookup on Login
- Player Menu Lookup
- Menu Locations
- Tooltip Activation (Hover, Click)
- Cell Hover (Outline, Tint, None)
- Progress Highlighter (full color customization)
- Kill Clog chat message toggles

## local cache

Local cache keeps repeat searches fast (5 min TTL). Clear by deleting `~/.runelite/kill-clog/`.

---

Questions or feedback? Open an issue on [GitHub](https://github.com/420kc/kill-clog-plugin/issues).
