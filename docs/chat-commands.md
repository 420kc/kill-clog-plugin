# Chat command reference

[Back to the README](../README.md#chat)

## Which plugin handles it?

| Command | Handler | Result |
| --- | --- | --- |
| `!kclog [boss or clue tier]` | Kill Clog | Collected items, with duplicate quantities |
| `!missing [boss or clue tier]` | Kill Clog | Missing items |
| `!3a` / `!gilded` | Kill Clog | Collected items in that rare-item group |
| `!kc [item name]` | Kill Clog extension | The recorded KC of that drop, when available locally |
| `!log [page]` / `!log missing [page]` | RuneProfile when enabled; Kill Clog fallback otherwise | RuneProfile's supported pages when it handles the command; Kill Clog's bosses and clue tiers otherwise |
| `!pets` | RuneLite Chat Commands | Unique pet count and icons |
| `!clues medium` | RuneLite Chat Commands | Completed medium clue count, not Collection Log items |
| `!kc [boss]` / `!pb [boss]` | RuneLite Chat Commands | Kill count / personal best |
| `!clog` / `!ca` | RuneLite Chat Commands | Collection Log / Combat Achievement progress |
| `!lvl [skill]`, `!total`, `!cmb`, `!price [item]`, `!qp` | RuneLite Chat Commands | Skill, total level, combat level, item price, or quest points |

Other RuneLite Chat Commands include `!bh`, `!bhrogue`, `!lms`, `!lp`, `!gc`, `!duels`, and `!sw`. Keep **Chat Commands** and the relevant command settings enabled; disabling **HiScore** does not disable it. [RuneLite command source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java).

Kill Clog's four registered commands need Kill Clog enabled on the client displaying the chat. People without it see the typed command, not its replacement sprites. A recognized page also needs Collection Log data for the sender.

## Pets

Use `!pets` for RuneLite's pet list. Open **All Pets** in your in-game Collection Log to update the list RuneLite knows about. It tracks unique pets, not duplicate quantities. This is separate from Kill Clog's local cache. [RuneLite pet command](https://runelite.net/blog/show/2021-07-21-1.7.17-Release/).

Kill Clog does not register `!pets`, and `!kclog pets` / `!kclog all pets` are not supported. A boss page such as `!kclog hydra` does show that page's pet quantity when present in its data.

## Kill Clog examples

```text
!kclog hydra
!kclog cox
!kclog medium clues
!missing clues medium
!3a
!gilded
!kc Elder venator fang
```

`!kc [item name]` only expands when a local record includes the KC at which that item dropped. Older items without that record cannot have their drop KC reconstructed. Boss arguments stay with RuneLite.

`!3a` and `!gilded` show unique obtained items; their dedicated outputs do not currently include duplicate quantities.

### Bosses and raids

Use a full name or an alias below after `!kclog` or `!missing`. Matching is case-insensitive; apostrophes are optional. Related encounters can share a Collection Log page even when their KC differs.

| Full name | Other supported names |
| --- | --- |
| Abyssal Sire | `sire`, `abby` |
| Alchemical Hydra | `hydra` |
| Amoxliatl | `amox` |
| Araxxor | `arax` |
| Artio | `art` |
| Barrows Chests | `barrows` |
| Brutus | Full name |
| Bryophyta | `bryo` |
| Callisto | `calli` |
| Cal'varion | `calvarion`, `calv` |
| Cerberus | `cerb` |
| Chambers of Xeric | `cox`, `raids` |
| Chambers of Xeric: Challenge Mode | `cm` |
| Chaos Elemental | `chaos ele`, `ele` |
| Chaos Fanatic | `chaos fan`, `fan` |
| Commander Zilyana | `sara`, `zilyana` |
| Corporeal Beast | `corp` |
| Crazy Archaeologist | `crazy arch` |
| Dagannoth Prime | `prime` |
| Dagannoth Rex | `rex` |
| Dagannoth Supreme | `supreme` |
| Deranged Archaeologist | `deranged arch` |
| Doom of Mokhaiotl | `doom`, `mokhaiotl` |
| Duke Sucellus | `duke` |
| General Graardor | `bandos`, `graardor` |
| Giant Mole | `mole` |
| Grotesque Guardians | `grotesque`, `gg` |
| Hespori | `hesp` |
| Kalphite Queen | `kq` |
| King Black Dragon | `kbd` |
| Kraken | `krak` |
| Kree'Arra | `arma`, `kree`, `kreearra` |
| K'ril Tsutsaroth | `zammy`, `kril` |
| Lunar Chests | `lunar`, `moons`, `perilous`, `perilous moons` |
| Mad Angel | `angel` |
| Maggot King | `maggot`, `mk` |
| Mimic | Full name |
| Nex | Full name |
| Nightmare | `nm` |
| Phosani's Nightmare | `phosani`, `pnm` |
| Obor | Full name |
| Phantom Muspah | `muspah` |
| Sarachnis | `sara mage` |
| Scorpia | Full name |
| Scurrius | `scur` |
| Shellbane Gryphon | `shellbane` |
| Skotizo | Full name |
| Sol Heredit | `sol`, `colosseum` |
| Spindel | `spin` |
| Tempoross | `tempo` |
| The Gauntlet | `gauntlet` |
| The Corrupted Gauntlet | `cg`, `corrupted gauntlet` |
| The Hueycoatl | `huey`, `hueycoatl` |
| The Leviathan | `levi`, `leviathan` |
| The Royal Titans | `titans`, `royal titans` |
| The Whisperer | `whisp`, `whisperer` |
| Theatre of Blood | `tob` |
| Theatre of Blood: Hard Mode | `hmt` |
| Thermonuclear Smoke Devil | `thermy`, `smoke devil` |
| Tombs of Amascut | `toa`, `tombs` |
| Tombs of Amascut: Expert Mode | `expert`, `toa expert` |
| TzKal-Zuk | `zuk`, `inferno` |
| TzTok-Jad | `jad` |
| Vardorvis | `vard` |
| Venenatis | `ven`, `venom` |
| Vet'ion | `vetion`, `vet` |
| Vorkath | `vork`, `vorky` |
| Wintertodt | `wt`, `winter` |
| Yama | Full name |
| Zalcano | `zal` |
| Zulrah | Full name |

The [boss-name catalog](../src/main/resources/com/killclog/hiscore-layout.tsv) and [alias catalog](../src/main/resources/com/killclog/chat-boss-aliases.tsv) are the source for this table. A recognized boss can still report no clog items when its page is absent from the available catalog.

### Clue tiers

Use any name below after `!kclog` or `!missing`. For example, `!kclog medium clues` shows obtained items; `!clues medium` is RuneLite's separate completion-count command.

| Tier | Supported names |
| --- | --- |
| Beginner | `beginner treasure trails`, `begs`, `beg clues`, `beginners`, `beginner clues`, `beginner clue`, `clues beg`, `clues beginner`, `clue beg`, `clue beginner` |
| Easy | `easy treasure trails`, `easy clues`, `easy clue`, `easies`, `clues easy`, `clue easy` |
| Medium | `medium treasure trails`, `meds`, `med`, `mediums`, `medium clues`, `medium clue`, `clues med`, `clues medium`, `clue med`, `clue medium` |
| Hard | `hard treasure trails`, `hards`, `hard clues`, `hard clue`, `clue hard`, `clues hard` |
| Elite | `elite treasure trails`, `elites`, `elite clues`, `elite clue`, `clue elite`, `clues elite` |
| Master | `master treasure trails`, `masters`, `master clues`, `master clue`, `clue master`, `clues master` |

These are the six individual tiers, not an all-clues aggregate. Use `!3a` and `!gilded` for their dedicated rare-item groups. [Clue alias catalog](../src/main/resources/com/killclog/chat-clue-aliases.tsv).

## RuneProfile compatibility

With RuneProfile enabled, it keeps `!log` and `!log missing`; use its [page names and aliases](https://runeprofile.com/info/alias). Kill Clog does not replace that handler. If RuneProfile is enabled but its log-command setting is off, Kill Clog still stands aside.

With RuneProfile disabled, these use Kill Clog:

```text
!log hydra
!log medium clues
!log missing medium clues
```

That fallback has the same supported names as `!kclog` / `!missing`. It does not implement every RuneProfile page. [RuneProfile command source](https://github.com/ReinhardtR/runeprofile-plugin/blob/2da51cd7a8dcf6a5ed0e827a2df2bb985d0e0550/src/main/java/com/runeprofile/ui/CollectionLogCommand.java).

## Unsupported arguments and unexpected results

Skill Clog names, `pets`, `all pets`, `random events`, and other general Collection Log pages are not part of Kill Clog's command target list.

There is a known loose-match bug: `!kclog random events` can match the `ven` alias inside “events” and return Venenatis. That is an incorrect match, not Random Events support. Use a listed name or alias.

- **collection log page not recognized:** the argument did not resolve to a supported target.
- **no clog data:** the sender's Collection Log was unavailable.
- **no clog items found:** the target resolved, but its item catalog was unavailable.
- **lookup failed:** the data request failed.

Report an incorrect result with the exact command, RSN, and screenshot.
