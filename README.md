# Carbon Client — Parts 1–2

Carbon Client is a single-jar, client-only Fabric mod for Minecraft 26.2 and Java 25. Part 1 supplies the module, setting, event, persistence, HUD, and initial feature foundations. Part 2 adds CarbonUI, its translucent green glass theme, native GUI rendering, reusable controls, and a compact in-game popup for real module and profile configuration.

## Toolchain pins

- Minecraft `26.2`
- Java `25`
- Fabric Loader `0.19.5`
- Fabric API `0.161.0+26.2`
- Fabric Loom `1.18-SNAPSHOT` (the current recommendation shown by the Fabric Develop page)
- Gradle wrapper `9.8.0`

Minecraft 26.x is unobfuscated, so this project intentionally has no mappings dependency. It uses Loom's `net.fabricmc.fabric-loom` plugin ID and `implementation` dependencies.

## Build

Install a Java 25 JDK and run:

```sh
./gradlew build --no-daemon
```

The distributable is `build/libs/carbonclient-1.0.0.jar`. Install it with Fabric Loader and Fabric API for Minecraft 26.2.

## Part 1 contents

- `core/module`: categorized modules, module registry, lifecycle and safe failure handling.
- `core/setting`: Bool, Number, Color, Mode, and Keybind settings.
- `core/event`: exact-type, no-reflection event bus and client/HUD/input events.
- `core/config`: schema-versioned Gson config, local profiles, corrupt-file recovery, atomic asynchronous writes.
- `core/util`, `hud`, `modules`: anchoring/color helpers, HUD manager/base, Keystrokes, CPS, FPS, Zoom.
- The foundation makes no network calls; config and profiles remain local to the game directory.

## Configuration

Carbon Client stores data in `.minecraft/config/carbonclient/`. The root config is `config.json`; named local profiles are stored in `profiles/`. Invalid config files are preserved as timestamped `.broken-*` files before defaults are loaded.

## Part 2 — CarbonUI

- F8 opens a compact, non-pausing popup over the game world, with a translucent Carbon glass palette, Carbon mark, custom display font, category filters, search, profile sidebar, module cards, and a separate settings view.
- Only the real HUD and Zoom modules are shown. Cards expose module state and settings; number, mode, color, toggle, and keyboard/mouse keybind controls remain connected to the persisted settings.
- Profiles can be switched or created from the sidebar and are stored by the existing local config manager.
- Carbon buttons, toggles, sliders, mode controls, color swatches, keybind controls, and module cards use Minecraft's native `Screen`/`AbstractButton` lifecycle and are registered with `addRenderableWidget`.
- Carbon's rounded panels use an SDF Blaze3D render pipeline and retained GUI render states. If SDF pipeline registration itself fails, the UI falls back to Minecraft's native rectangular fill renderer.
- Background blur is requested through Minecraft's native GUI blur stratum and follows the configured menu-background blur option. The supplied GLSL also contains a reusable separable Gaussian kernel.
- No raw OpenGL calls or third-party UI rendering libraries are used.
