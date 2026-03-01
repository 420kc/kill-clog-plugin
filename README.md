# Kill Clog

Reimagine your boss log.

![Kill Clog panel screenshot](https://raw.githubusercontent.com/420kc/kill-clog-plugin/master/screenshots/panel.png)

## Features

### Grand Unified Search Theory
No more searching the wrong hiscores. One search bar, one search. Account type is auto-detected and the correct helmet is applied to the RSN (Regular, Ironman, HCIM, UIM, De-Ironed).

### Collection Log Tooltips
Kills and collections in the same place. Hover any boss to see your collection log progress. Obtained items marked with green checkmarks, missing items with empty checkboxes. Item counts are shown for duplicates.

![Tooltip screenshot](https://raw.githubusercontent.com/420kc/kill-clog-plugin/master/screenshots/tooltip.png)

### Completionist's Highlighter
Boss kill counts colored by your collection log completion:
- **Green** — all items obtained
- **Amber** — some items obtained
- **Red** — kills but no drops yet

All three colors are fully configurable and toggled with a keybind.

![Highlighter screenshot](https://raw.githubusercontent.com/420kc/kill-clog-plugin/master/screenshots/highlighter.png)

### Status Bar
At a glance: account badge + player name, collections obtained, and total level. Rotating search and not-found messages for mild amusement.

## How It Works
- Type a player name and press Enter to look up any account
- Right-click any player in-game and select "Kill Clog Lookup"
- Your RSN auto-fills when you log in
- Collection log data is pulled from [TempleOSRS](https://templeosrs.com) — install the TempleOSRS plugin, open your collection log in-game, and click the sync button in the top right

## Configuration

| Setting | Description |
|---------|-------------|
| Default Player | RSN to look up on panel open |
| Show Collection Log | Enable/disable clog tooltips |
| Player Menu Lookup | Add "Kill Clog Lookup" to right-click menu |
| Completionist's Highlighter | Color boss kill counts by clog completion |
| Highlighter Keybind | Key to toggle the highlighter |
| Status Bar Color | Color for player name and stats |
| Not Found Color | Color for "player not found" messages |
| Completed / In Progress / Empty | Highlighter colors (fully configurable) |

## Reporting an Issue

Found a bug or have a suggestion? [Open an issue](https://github.com/420kc/kill-clog-plugin/issues) on GitHub.
