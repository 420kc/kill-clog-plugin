# Kill Clog

Kill Clog makes the hiscores flexier and tells the story instead of just the stats.

![Kill Clog](screenshots/hero-vanilla-vs-killclog.png)

## privacy and data

Kill Clog never sends data to any server. Your clog stays on disk. Public lookups read [TempleOSRS](https://templeosrs.com).

![install-warning](screenshots/install-warning.png)

The install warning is about TempleOSRS, the only external request the plugin makes. Source is public at [github.com/420kc/kill-clog-plugin](https://github.com/420kc/kill-clog-plugin) and open to audit.

## installation

1. Install Kill Clog from the RuneLite Plugin Hub.
2. Install the [TempleOSRS](https://templeosrs.com) plugin. In its settings, enable **"Automatically sync to temple"**.
3. In RuneLite settings, enable **"Collection Log - Chat messages"**.

## sync

Open your collection log in-game, open the Kill Clog side panel, click the red chalice at the top.

![sync-path](screenshots/sync-path.gif)

To make your clog visible to other Kill Clog users, sync to TempleOSRS via its own sync button.

## auto-sync

Your local data auto-refreshes from collection log chat messages. Long sessions stay current without re-syncing.

## tooltips

| | |
|:---:|:---:|
| ![tooltip-outline](screenshots/tooltip-hover.gif) | ![tooltip-tint](screenshots/tooltip-click.gif) |
| Outline | Tint |

![tooltip-boss](screenshots/tooltip-boss.png)

## quick lookup

Right-click "Kill Clog" on any player name. Side panel auto-opens.

Double-click the magnifying glass to look up yourself.

![player-menu](screenshots/player-menu.png)

## chat commands

![chat-kclog](screenshots/chat-kclog.png)

![chat-missing](screenshots/chat-missing.png)

`!kclog [boss]` shows your collection log progress for that boss, plus inline sprites of every unique you already have.

`Vorkath: 12/14 [item] [item] [item] ...`

`!missing [boss]` flips it.

`Vorkath: 2/14 missing [item] [item]`

Common shorthand works (`vork`, `cox`, `tob`, `jad`, `nm`, `pnm`, `cg`, `thermy`, `cerb`, `huey`, and more).

## player comparison

Compare any two players side by side.

![compare](screenshots/compareplayers.gif)

| | |
|:---:|:---:|
| ![comp-cox](screenshots/cbcvs420kccox.png) | ![comp-rax](screenshots/alsoevsjabbaurax.png) |

## summaries

Six grouped readouts: player, skills, clog, PvM, clue, PvP.

![summaries](screenshots/summaries.png)

## activities tray

toggle with menu button in summary bar

| | | |
|:---:|:---:|:---:|
| ![tray-open](screenshots/tray-open.png) | ![tray-toggle](screenshots/tray-toggle.gif) | ![tray-closed](screenshots/tray-closed.png) |

## progress highlighter

<sub>16,777,216^5 = 1,329,227,995,784,915,872,903,807,060,280,344,576 combinations</sub>

## configuration

![config](screenshots/config.png)

- Auto-Lookup on Login
- Player Menu Lookup
- Tooltip Activation (Hover or Click)
- Cell Hover (Outline, Tint, None)
- Progress Highlighter (full color customization)
- Toggle Keybind

## local cache

Local cache for fast repeat searches (5-minute TTL). Clear by deleting `~/.runelite/kill-clog/`.

---

Questions or feedback? Open an issue on [GitHub](https://github.com/420kc/kill-clog-plugin/issues).
