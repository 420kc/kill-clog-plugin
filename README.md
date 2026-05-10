# Kill Clog

PvM-Focused HiScores Overhaul with Collection Log Integration

When you click Install, RuneLite prompts a warning about your IP being sent to a 3rd-party server.

![install-warning](screenshots/install-warning.png)

The 3rd-party is [TempleOSRS](https://templeosrs.com). Kill Clog reads your public collection log data from there.

Kill Clog never collects passwords, tokens, bank data, account credentials, or private RuneLite data. Source is public at [github.com/420kc/kill-clog-plugin](https://github.com/420kc/kill-clog-plugin) and open to audit.

## Installation

1. Install Kill Clog from the RuneLite Plugin Hub.
   > Look for the red chalice icon in your sidebar tabs.
   > Optional: disable the vanilla HiScores plugin to fully replace it.

2. Install the TempleOSRS plugin.
   > Lets players view each others' collection logs.

## Sync

1. Open your Collection Log in the game and open the Kill Clog side panel.

2. Click the red chalice at the top to capture your clog.
   > Flashes green, goes red, steady green when complete (1-2 sec).
   > Don't miss the moment. Watch your KCs come alive.
   > Chat confirms: `Kill Clog: N items synced to Kill Clog`.

![sync-path](screenshots/sync-path.gif)

3. To make your clog visible to other Kill Clog users looking you up, sync to TempleOSRS via the TempleOSRS plugin's own sync button.

![panel](screenshots/panel.png)

## Tooltips

Hover for an outline preview, click to lock a tint on a boss tile.

| | |
|:---:|:---:|
| ![tooltip-hover](screenshots/tooltip-hover.gif) | ![tooltip-click](screenshots/tooltip-click.gif) |
| Hover \| Outline | Click \| Tint |

![tooltip-boss](screenshots/tooltip-boss.png)

## Quick Lookup

Right-click "Kill Clog" on any player name to look them up instantly. Works on friends list, ignore list, friends chat, clan + guest clan, GIM panel, chatbox, and private messages. The side panel auto-opens.

Double-click the magnifying-glass icon in the search bar to look up yourself.

![player-menu](screenshots/player-menu.png)

## Chat Commands

![chat-kclog](screenshots/chat-kclog.png)

![chat-missing](screenshots/chat-missing.png)

`!kclog [boss]` replaces your chat line with your collection log progress for that boss, plus the inline sprites of every unique you already have.

`Vorkath: 12/14 [item] [item] [item] ...`

`!missing [boss]` flips it. The count and sprites of everything still unobtained.

`Vorkath: 2/14 missing [item] [item]`

Common shorthand works (`vork`, `cox`, `tob`, `jad`, `nm`, `pnm`, `cg`, `thermy`, `cerb`, `huey`, and more). Partial typing falls through to substring match. Both commands share the panel's cache, so they stay fast.

## Player Comparison

Compare any two players side by side.

![compare](screenshots/compareplayers.gif)

| | |
|:---:|:---:|
| ![comp-cox](screenshots/cbcvs420kccox.png) | ![comp-rax](screenshots/alsoevsjabbaurax.png) |

## Summaries

Six grouped readouts (player, skills, clog, PvM, clue, PvP).

| | | |
|:---:|:---:|:---:|
| ![summary-player-hcim](screenshots/summary-player-hcim.png) | ![summary-skills](screenshots/summary-skills.png) | ![summary-clog](screenshots/summary-clog.png) |
| ![summary-pvm](screenshots/summary-pvm.png) | ![summary-clue](screenshots/summary-clue.png) | ![summary-pvp](screenshots/summary-pvp.png) |

## Activities Tray

A second-row strip for non-boss content (Wintertodt, Tempoross, etc.). Toggle between data and focus modes.

| | | |
|:---:|:---:|:---:|
| ![tray-open](screenshots/tray-open.png) | ![tray-toggle](screenshots/tray-toggle.gif) | ![tray-closed](screenshots/tray-closed.png) |

Data \| Focus

## Progress Highlighter

Outline every boss tile with the same completion state across the panel. Empty, In Progress, 1 Away, Completed.

| | |
|:---:|:---:|
| ![highlighter1](screenshots/highlighter1.png) | ![highlighter2](screenshots/highlighter2.png) |

<sub>16,777,216^5 = 1,329,227,995,784,915,872,903,807,060,280,344,576 combinations</sub>

## Configuration

![config](screenshots/config.png)

- Auto-Lookup on Login
- Tooltip mode: Hover or Click
- Highlight mode: Tint, Outline, or None

## Bonus

Days of clicking the wrong HiScores tab and firing into the void are behind us. Ironman types are automatically identified and ranked among the correct set of HiScores (regular, iron, uim, hcim, gim).

![sys-messages](screenshots/sys-messages.png)

Rotating system messages.

## Local Cache

Player data caches locally for fast repeat searches. Recent lookups (under 5 minutes) serve from memory. Older ones refresh on next search. Your own data also auto-refreshes when you chat in-game, so a long session stays current without a manual sync. Clear the cache by deleting files from `~/.runelite/kill-clog/`.

## TempleOSRS

Your own collection log syncs locally via the red chalice. That data lives on your machine and never expires. Looking up other players uses [TempleOSRS](https://templeosrs.com). Anyone who hasn't synced their clog to Temple (via Temple's own RuneLite plugin) won't have clog data to show.

## Updates

If new collection log items are released in game updates, re-sync with the red chalice in your collection log.

---

Questions or feedback? Open an issue on [GitHub](https://github.com/420kc/kill-clog-plugin/issues).
