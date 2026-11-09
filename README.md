# BloodMoon

Plugin that adds a deadly BloodMoon to your Paper server once every few days.

> **This is an altered, unofficial continuation** of [BloodMoon by SpectralMemories](https://gitlab.com/SpectralMemories/bloodmoon),
> which was abandoned at 0.8.1 (Minecraft 1.18.2). It is not the original software. The original license (zlib) is kept in `LICENSE`.

## Compatibility

- Built against the Paper API **1.20.4**, Java 17.
- **Paper only** (and Paper forks such as Purpur) since 0.9: texts use Adventure / MiniMessage, which plain Spigot does not ship.
- Meant to run on Paper **1.20.4 – 1.21.x**: particles, potion effects and enchantments are looked up by name,
  so the 1.20.5 renames (`EXPLOSION_HUGE`, `INCREASE_DAMAGE`, `DAMAGE_ALL`...) do not break it.
- Old config files keep working; old effect / enchantment names (`SLOW`, `CONFUSION`, `DAMAGE_ALL`...) are still accepted.

## What's new in 0.9

- **Enhanced mobs**: armor, weapons, enchantments, speed and health for monsters spawned during a BloodMoon.
- **Crimson Moon**: a rarer, harder BloodMoon with multiplied difficulty and rewards (`/bm start crimson` to force one).
- **Kill ranking and rewards**: top killers and every player over a minimum get configurable console commands.
- **Ambience**: start title, start sound and red particles around players.
- **PlaceholderAPI**, `/bm` alias, clickable `/bm help` and tab completion.
- **MiniMessage** in every text: locales, boss name, boss bar, titles, item names and lore.

## Text format (MiniMessage)

Every text in `locales.yml` and the `$name` / `$desc` of `DropItemList` items use
[MiniMessage](https://docs.advntr.dev/minimessage/format.html): `<red>`, `<#ff5555>`, `<gradient:#7a0000:#ff2a2a>`,
`<rainbow>`, `<bold>`, `<hover:show_text:'...'>`, `<click:run_command:'/bm top'>`, `<newline>`...

Old files keep working: `&a`, `&l`, `§c` and `&#RRGGBB` are converted automatically, and both formats can be mixed.
Placeholders like `$p` or `$k` are inserted as plain text, so a player name can never inject tags.

```yaml
StartTitle: "<gradient:#7a0000:#ff2a2a><bold>BloodMoon</bold></gradient>"
HordeArrived: "<red>A horde has descended upon <white>$p</white>!"
```

```yaml
DropItemList:
  - "DIAMOND_SWORD:1:1:$name <gradient:#ff0000:#ffaa00>Excalibur</gradient>:$desc <gray>The legendary sword$n<dark_gray>Bloodforged:$enchant SHARPNESS,5"
```

## Commands

| Command | Permission | Default |
|---|---|---|
| `/bm show` | `bloodmoon.show` | everyone |
| `/bm top` | `bloodmoon.top` | everyone |
| `/bm start [crimson]` | `bloodmoon.start` | op |
| `/bm stop` | `bloodmoon.stop` | op |
| `/bm reload` | `bloodmoon.reload` | op |
| `/bm spawnzombieboss` | `bloodmoon.spawnzombieboss` | op |
| `/bm spawnhorde [player]` | `bloodmoon.spawnhorde` | op |
| `/bm killbosses [true\|false]` | `bloodmoon.killbosses` | op |

From the console, put the world name right after the subcommand: `bm start world crimson`.

## Placeholders (PlaceholderAPI)

| Placeholder | Value |
|---|---|
| `%bloodmoon_active%` | `true` / `false` in the player's world |
| `%bloodmoon_crimson%` | `true` if the current BloodMoon is a Crimson Moon |
| `%bloodmoon_days_left%` | days until the next BloodMoon (`0` while active) |
| `%bloodmoon_time_left%` | real time left in the current BloodMoon, `mm:ss` |
| `%bloodmoon_kills%` | the player's kills in the current BloodMoon |
| `%bloodmoon_rank%` | the player's position in the ranking (`0` without kills) |
| `%bloodmoon_top_<n>_name%` / `%bloodmoon_top_<n>_kills%` | ranking position `n` |

`active`, `crimson`, `days_left` and `time_left` also accept a world name at the end, for example `%bloodmoon_days_left_survival%`.

## LevelledMobs

BloodMoon detects [LevelledMobs](https://arcaneplugins.gitbook.io/levelledmobs-the-ultimate-mob-levelling-solution) on its own; nothing has to be installed or enabled.

- **No double scaling**: with `LevelledMobsHandlesStats: true` (default) enhanced mobs keep their armor and weapons,
  but health and speed are left to LevelledMobs.
- **Bosses keep their identity**: with `LevelledMobsLevelBosses: false` (default) the zombie boss and its minions get
  the `levelledmobs:nolevel` key, so LevelledMobs gives them no level and no nametag.
- **Rules for BloodMoon mobs**: every BloodMoon mob carries the PDC key `bloodmoon:mob` with the value
  `bloodmoon`, `crimson`, `horde`, `boss` or `minion`. Add it to LevelledMobs' `externalplugins.yml`:

```yaml
bloodmoon:
  friendly-name: 'bloodmoon'
  plugin-name: 'BloodMoon'
  key-name: 'mob'
  key-type: 'PDC'
  requirement: 'exists'
  placeholder-name: '%bloodmoon-type%'

crimson-moon:
  friendly-name: 'crimson-moon'
  plugin-name: 'BloodMoon'
  key-name: 'mob'
  key-type: 'PDC'
  requirement: 'contains'
  requirement-value: 'crimson'
```

and use it in a `rules.yml` custom rule, for example to give mobs higher levels during a Crimson Moon:

```yaml
  - custom-rule: 'Crimson Moon mobs'
    is-enabled: true
    conditions:
      external-plugins:
        included-list: ['crimson-moon']
    settings:
      minLevel: 20
      maxLevel: 50
```

BloodMoon's own XP, drop and damage multipliers still apply on top of the levels; lower them in `config.yml` if the result is too strong.

## Building

Requires any JDK 17 or newer:

```
./gradlew build
```

The plugin jar ends up in `build/libs/`.

## Changes since 0.8.1

See `notes.txt`.

## Credits

- **[SpectralMemories](https://gitlab.com/SpectralMemories)**: original author of BloodMoon (2020 – 0.8.1).
  All the original commit history is kept in this repository.
- **Leonardo Reynoso ([LeonardoRRC](https://github.com/LeonardoRRC))**: 0.9 continuation (Paper 1.20.4+, MiniMessage, new features).

Released under the original zlib license (see `LICENSE`). This version is altered from the original and is not
endorsed by the original author.
