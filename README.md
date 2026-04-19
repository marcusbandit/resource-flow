# Resource Flow

Client-side Mindustry mod: replaces the vanilla core-items bar with a compact HUD overlay that shows the current count **and** net flow (+/- per second) for every item your team owns.

Works on vanilla servers — it only reads client-side state and draws UI.

## Features

- Format per item: `[icon] <count>` (idle) or `[icon] <count> (+X/s)` (producing) / `(-X/s)` (consuming)
- Green = net gain, red = net loss
- Sampled once per second, so the rate is an average over that window
- Items wrap to a new row after 4 per row
- Column widths are fixed so items don't jump around when rates appear or disappear
- Items stay in their slot once seen, even if the count temporarily drops to zero

## Install

Grab `resource-flow.jar` from the [latest release](../../releases/latest) and drop it into your Mindustry `mods/` folder.

| Platform | Path |
|---|---|
| Steam (Linux) | `~/.local/share/Steam/steamapps/common/Mindustry/saves/mods/` |
| Steam (Windows) | `%APPDATA%\Mindustry\mods\` |
| Steam (macOS) | `~/Library/Application Support/Mindustry/mods/` |
| Standalone (Linux) | `~/.local/share/Mindustry/mods/` |
| Standalone (Windows) | `%APPDATA%\Mindustry\mods\` |

Or open Mindustry → Mods, click the folder icon — it opens the right directory.

Restart Mindustry after dropping the jar in.

## Requirements

- Mindustry v7, build ≥ 157
- Java-enabled desktop build (the slim mobile builds without a Java runtime will not load this)

## Build from source

```sh
./gradlew jar
```

Output: `build/libs/resource-flow.jar`

## License

MIT
