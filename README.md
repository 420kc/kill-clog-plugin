# Kill Clog

Reimagine your boss log.

![Kill Clog panel screenshot](https://raw.githubusercontent.com/420kc/kill-clog-plugin/master/screenshots/panel.png)

## Features

### Boss KC Grid
Three-column boss grid with kill count for every boss on the hiscores. Account type is auto-detected and displayed with the correct badge (Regular, Ironman, HCIM, UIM, De-Ironed).

### Collection Log Tooltips
Hover any boss to see your collection log progress. Obtained items show as green checkmarks, missing items as gray squares. Item counts are shown for duplicates.

![Tooltip screenshot](https://raw.githubusercontent.com/420kc/kill-clog-plugin/master/screenshots/tooltip.png)

### Completionist's Highlighter
Boss cells light up based on your collection log completion:
- **Green** — all items obtained
- **Amber** — some items obtained
- **Red** — kills but no drops yet

All three colors are fully configurable. Toggle with a configurable keybind.

![Highlighter screenshot](https://raw.githubusercontent.com/420kc/kill-clog-plugin/master/screenshots/highlighter.png)

### Status Bar
At a glance: account badge + player name, total collection log count, and total level.

## How It Works
- Type a player name and press Enter to look up any account
- Right-click any player in-game and select "Kill Clog Lookup"
- Your RSN auto-fills when you log in
- Collection log data is pulled from [TempleOSRS](https://templeosrs.com) — make sure your account is synced

## Configuration

| Setting | Description |
|---------|-------------|
| Default Player | RSN to look up on panel open |
| Show Collection Log | Enable/disable clog tooltips |
| Player Menu Lookup | Add "Kill Clog Lookup" to right-click menu |
| Completionist's Highlighter | Color boss cells by clog completion |
| Highlighter Keybind | Key to toggle the highlighter |
| Status Bar Color | Color for player name and stats |
| Not Found Color | Color for "player not found" messages |
| Completed / In Progress / Empty | Highlighter colors (fully configurable) |

## Reporting an Issue

Found a bug or have a suggestion? [Open an issue](https://github.com/420kc/kill-clog-plugin/issues) on GitHub.
