# Kill Clog Next Release

Target version: `2.1.0`

## Release Notes

Kill Clog 2.1.0

first-party collection log sync, opt-in and off by default:

* a new "Sync Collection Log to Killclog.com" setting publishes your locally tracked collection log and personal bests to your own killclog.com profile. nothing is sent until you turn it on, and opting out at killclog.com deletes everything stored
* player lookups now also read killclog.com alongside TempleOSRS and RuneProfile, so synced players' logs and personal bests show up in lookups and boss tooltips. looking up a never-synced name costs no extra requests
* the README and privacy copy name every endpoint the plugin talks to: TempleOSRS, RuneProfile, killclog.com, Jagex's own hiscores, and the OSRS Wiki item mapping

also in this release:

* a camera on your own profile creates a native in-game profile card, copies it to your clipboard, saves a png, and opens a large preview with collection log and personal best provenance
* hiscore lookups now use the name-keyed json endpoint, so game updates that reshuffle the hiscores can no longer blank or misalign boss kcs
* hovering a pet in the player summary shows its name and links its wiki page
* skill summaries follow the virtual levels plugin when you have it enabled, up to 126 and virtual totals
* new titan prestige for 200m xp in every skill
* mad angel collection log data maps correctly
