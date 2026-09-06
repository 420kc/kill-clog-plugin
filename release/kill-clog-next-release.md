# Kill Clog Next Release

Target version: `2.3.1`

## Release Notes

Kill Clog 2.3.1

* treats RuneProfile's catalog-shaped all-zero response as unsynced while retaining account identity
* prevents fast RuneProfile responses from leaving a stuck lookup
* reserves status-row spacing and flashes the chalice green for manual sync success
* keeps automatic sync silent by default; manual sync and character uploads show progress and failure text with an icon flash on success
* groups popup activation, appearance, links, and stat lines under Modal Appearance
* adds a centered Sources footer that shows only verified synced collection-log sources
