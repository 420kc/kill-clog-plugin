# Chat command reference

[Back to the README](../README.md#chat)

## Quick rule

Use `!kclog [page]` for collected items and duplicate quantities. Use `!missing [page]` for missing items. Any Collection Log page in the available catalog can be used, whether or not it has a cell in Kill Clog's panel.

```text
!kclog pets
!missing pets
!kclog mixology
!kclog random events
!kclog gotr
!kclog medium clues
!missing hard
```

Full page names work. Matching ignores case, apostrophes, and extra spaces; underscores and hyphens can be typed as spaces. Shortcuts are explicit, not guessed from fragments: `ven` means Venenatis, but the letters “ven” inside “events” do not.

`pets`, `all pets`, and `allpets` all select **All Pets**. The header counts unique pets; `x2`, `x3`, etc. beside a sprite show that pet's recorded quantity. Pet quantities come from All Pets, not a sum of overlapping boss pages.

## Which plugin handles it?

| Command | Handler | Result |
| --- | --- | --- |
| `!kclog [page]` | Kill Clog | Collected items and duplicate quantities |
| `!missing [page]` | Kill Clog | Missing items |
| `!3a` / `!gilded` | Kill Clog | Third-age / gilded items and duplicate quantities |
| `!kc [item name]` | Kill Clog extension | The recorded KC of that drop, when available locally |
| `!log [page]` / `!log missing [page]` | RuneProfile when enabled; Kill Clog fallback otherwise | The owning plugin's Collection Log page output |
| `!pets` | RuneLite Chat Commands | Unique pet count and icons, without duplicate quantities |
| `!clues medium` | RuneLite Chat Commands | Completed medium clue count, not Collection Log items |
| `!kc [boss]` / `!pb [boss]` | RuneLite Chat Commands | Kill count / personal best |
| `!clog` / `!ca` | RuneLite Chat Commands | Collection Log / Combat Achievement progress |
| `!lvl [skill]`, `!total`, `!cmb`, `!price [item]`, `!qp` | RuneLite Chat Commands | Skill, total level, combat level, item price, or quest points |

Keep **Chat Commands** and the relevant command settings enabled. Turning off **HiScore** does not disable Chat Commands. Other RuneLite commands include `!bh`, `!bhrogue`, `!lms`, `!lp`, `!gc`, `!duels`, and `!sw`. [RuneLite command source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java).

Kill Clog does not take over `!pets` or `!clues`. For RuneLite's separate `!pets` list, open **All Pets** in your in-game Collection Log to update the pets RuneLite knows about. [RuneLite pet command](https://runelite.net/blog/show/2021-07-21-1.7.17-Release/).

## Page names and shorthand

Use either column after `!kclog` or `!missing`. For example, `!kclog mixology` and `!kclog mastering mixology` select the same page.

Boss-specific names can also show that encounter's KC. `!kclog rex`, `!kclog prime`, and `!kclog dks` share the Dagannoth Kings items; only the individual boss names select a specific KC. The same applies to shared wilderness, raid-mode, and Nightmare pages.

This chart lists the 125 catalog pages checked for 2.3.2. Newly added game pages also resolve by full name once the catalog loads. “Full name” means no additional shorthand is defined.

<!-- BEGIN GENERATED PAGE TABLE -->
| Page name | Also accepts |
| --- | --- |
| `abyssal sire` | `abby`, `abyssal`, `sire` |
| `aerial fishing` | `aerial` |
| `alchemical hydra` | `hydra` |
| `all pets` | `allpets`, `pets` |
| `amoxliatl` | `amox` |
| `araxxor` | `arax` |
| `barbarian assault` | `ba` |
| `barracuda trials` | `barracuda` |
| `barrows chests` | `barrows` |
| `beginner treasure trails` | `beg clues`, `beginner`, `beginner clue`, `beginner clues`, `beginners`, `begs`, `clue beg`, `clue beginner`, `clues beg`, `clues beginner` |
| `boat paints` | `paints` |
| `brimhaven agility arena` | `agility arena`, `brimhaven` |
| `brutus` | Full name |
| `bryophyta` | `bryo` |
| `callisto and artio` | `art`, `artio`, `calli`, `callisto` |
| `camdozaal` | Full name |
| `castle wars` | `cw` |
| `cerberus` | `cerb` |
| `chambers of xeric` | `chambers of xeric challenge mode`, `cm`, `cox`, `raids` |
| `champions challenge` | `champion's challenge`, `champions` |
| `chaos druids` | Full name |
| `chaos elemental` | `chaos ele`, `ele` |
| `chaos fanatic` | `chaos fan`, `fan` |
| `chompy bird hunting` | `chompies`, `chompy` |
| `colossal wyrm agility` | `wyrm agility` |
| `commander zilyana` | `sara`, `zilyana` |
| `corporeal beast` | `corp` |
| `crazy archaeologist` | `crazy arch` |
| `creature creation` | Full name |
| `cyclopes` | `defenders` |
| `dagannoth kings` | `dag kings`, `dagannoth prime`, `dagannoth rex`, `dagannoth supreme`, `dks`, `prime`, `rex`, `supreme` |
| `deranged archaeologist` | `deranged arch` |
| `doom of mokhaiotl` | `doom`, `mokhaiotl` |
| `duke sucellus` | `duke` |
| `easy treasure trails` | `clue easy`, `clues easy`, `easies`, `easy`, `easy clue`, `easy clues` |
| `elite treasure trails` | `clue elite`, `clues elite`, `elite`, `elite clue`, `elite clues`, `elites` |
| `fishing trawler` | `trawler` |
| `forestry` | Full name |
| `fortis colosseum` | `colosseum`, `sol`, `sol heredit` |
| `fossil island notes` | `fossil notes` |
| `general graardor` | `bandos`, `graardor` |
| `giant mole` | `mole` |
| `giants foundry` | `foundry`, `giant's foundry` |
| `gilded` | Full name |
| `gloughs experiments` | `demonic gorillas`, `demonics`, `glough` |
| `gnome restaurant` | `gnome delivery`, `gnome delivery service` |
| `grotesque guardians` | `gg`, `grotesque` |
| `guardians of the rift` | `gotr`, `rift` |
| `hallowed sepulchre` | `hs`, `sepulchre` |
| `hard treasure trails` | `clue hard`, `clues hard`, `hard`, `hard clue`, `hard clues`, `hards` |
| `hespori` | `hesp` |
| `hueycoatl` | `huey`, `the hueycoatl` |
| `hunter guild` | `hunter rumours`, `rumors`, `rumours` |
| `kalphite queen` | `kq` |
| `king black dragon` | `kbd` |
| `kraken` | `krak` |
| `kree arra` | `arma`, `kree`, `kreearra` |
| `kril tsutsaroth` | `kril`, `zammy` |
| `last man standing` | `lms` |
| `lost schematics` | `schematics` |
| `maggot king` | `maggot`, `mk` |
| `magic training arena` | `mage training arena`, `mta` |
| `mahogany homes` | `mh` |
| `master treasure trails` | `clue master`, `clues master`, `master`, `master clue`, `master clues`, `masters` |
| `mastering mixology` | `mix`, `mixology` |
| `medium treasure trails` | `clue med`, `clue medium`, `clues med`, `clues medium`, `med`, `medium`, `medium clue`, `medium clues`, `mediums`, `meds` |
| `mimic` | Full name |
| `miscellaneous` | `misc` |
| `monkey backpacks` | `monkeys` |
| `moons of peril` | `lunar`, `lunar chests`, `moons`, `perilous`, `perilous moons` |
| `motherlode mine` | `mlm` |
| `my notes` | Full name |
| `nex` | Full name |
| `obor` | Full name |
| `ocean encounters` | `ocean` |
| `pest control` | `pc` |
| `phantom muspah` | `muspah` |
| `random events` | `events`, `random` |
| `revenants` | `revs` |
| `rogues den` | `rogues` |
| `rooftop agility` | `rooftops` |
| `royal titans` | `the royal titans`, `titans` |
| `sailing miscellaneous` | `sailing misc` |
| `sarachnis` | `sara mage` |
| `scorpia` | Full name |
| `scroll cases` | `scrolls` |
| `scurrius` | `scur` |
| `sea treasures` | `treasures` |
| `shades of mortton` | `mortton`, `shades` |
| `shared treasure trail rewards` | `shared clues`, `shared rewards` |
| `shayzien armour` | `shayzien`, `shayzien armor` |
| `shellbane gryphon` | `shellbane` |
| `shooting stars` | `stars` |
| `skilling pets` | `skill pets` |
| `skotizo` | Full name |
| `slayer` | Full name |
| `soul wars` | `sw` |
| `temple trekking` | `trekking` |
| `tempoross` | `tempo` |
| `the fight caves` | `fight caves`, `jad`, `tztok jad` |
| `the gauntlet` | `cg`, `corrupted gauntlet`, `gauntlet`, `the corrupted gauntlet` |
| `the inferno` | `inferno`, `tzkal zuk`, `zuk` |
| `the leviathan` | `levi`, `leviathan` |
| `the mad angel` | `angel`, `mad angel` |
| `the nightmare` | `nightmare`, `nm`, `phosani`, `phosanis nightmare`, `pnm` |
| `the whisperer` | `whisp`, `whisperer` |
| `theatre of blood` | `hmt`, `theatre of blood hard mode`, `tob` |
| `thermonuclear smoke devil` | `smoke devil`, `thermy` |
| `third age` | `3a`, `3rd age` |
| `tithe farm` | `tithe` |
| `tombs of amascut` | `expert`, `toa`, `toa expert`, `tombs`, `tombs of amascut expert mode` |
| `tormented demons` | `tds` |
| `trouble brewing` | `trouble` |
| `tzhaar` | `tzhaar city` |
| `vale totems` | `totems` |
| `vardorvis` | `vard` |
| `venators` | Full name |
| `venenatis and spindel` | `spin`, `spindel`, `ven`, `venenatis`, `venom` |
| `vetion and calvarion` | `calv`, `calvarion`, `vet`, `vetion` |
| `volcanic mine` | `vm` |
| `vorkath` | `vork`, `vorky` |
| `wintertodt` | `winter`, `wt` |
| `yama` | Full name |
| `zalcano` | `zal` |
| `zulrah` | Full name |

<!-- END GENERATED PAGE TABLE -->

The chart is generated from the [page census](../src/test/resources/com/killclog/chat-pages.tsv), [boss aliases](../src/main/resources/com/killclog/chat-boss-aliases.tsv), [clue aliases](../src/main/resources/com/killclog/chat-clue-aliases.tsv), and [page aliases](../src/main/resources/com/killclog/chat-page-aliases.tsv). Run `./gradlew -q chatCommandReference` to print an updated table. Tests require this chart to match the runtime aliases.

## Compatibility and data

With RuneProfile enabled, it keeps `!log` and `!log missing`; use its [page names and aliases](https://runeprofile.com/info/alias). If RuneProfile is enabled but its log-command setting is off, Kill Clog still stands aside. With RuneProfile disabled, those commands use the same pages, shortcuts, and renderer as `!kclog` / `!missing`. No other RuneProfile commands are claimed.

Kill Clog's own outputs require Kill Clog on the client displaying the chat. A recipient without it sees the typed command, not Kill Clog's replacement sprites. The sender's Collection Log data must be available: self commands use the local cache; other-player commands use the same TempleOSRS, RuneProfile, and Killclog.com selection rules as the panel.

`!kc [item name]` is separate from page commands. For example, `!kc Elder venator fang` only expands when a local record includes the KC at which that item dropped. Older items without that record cannot have their drop KC reconstructed. Boss arguments stay with RuneLite.

The six clue tiers and Shared Treasure Trail Rewards are separate pages. There is no new “all clues” aggregate. Skill Clog aggregates are not chat pages; a native page such as Slayer is its native Collection Log page, not the larger curated Skill Clog.

## If a command cannot show a page

- **collection log page not recognized:** use the full page name or an alias in the chart; arbitrary fragments are not matched.
- **no clog data:** no Collection Log data was available for the sender.
- **page not synced:** the page is known, but the available snapshot has not captured it. This is not treated as zero progress.
- **no clog items found:** no item definition was available for the resolved page.
- **lookup failed:** the lookup could not complete.

Report an incorrect result with the exact command, RSN, and screenshot.
