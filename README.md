# Resource Flow

Client-side Mindustry mod: replaces the vanilla core-items bar with a compact HUD overlay that shows the current count **and** net flow (+/- per second) for every item your team owns.

Works on vanilla servers — it only reads client-side state and draws UI.

![Resource Flow HUD](screenshot.png)

## Features

- Per item: `[icon] <count>` idle, or `[icon] <count> +X/s` producing / `-X/s` consuming
- Green = net gain, red = net loss
- Items wrap to a new row after 4 per row, with fixed column widths so things don't jump around as rates change
- Items stay in their slot once seen, even if the count temporarily drops to zero

## Settings

Found under `Settings > Game > Resource Flow`:

| Setting | Default | Description |
|---|---|---|
| Show +/- rate | on | Toggle the rate column entirely |
| Wrap rate in brackets | off | `+3.2k/s` vs `(+3.2k/s)` |
| Hide items with no change | off | Drop items whose rate is zero |
| Background opacity | 40% | Fades only the panel, not the text |
| Sample interval | 1.0s | Window the rate is averaged over |

Plus a keybind under `Controls > Resource Flow` (**F9** by default) that toggles the rate column on/off live.

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
