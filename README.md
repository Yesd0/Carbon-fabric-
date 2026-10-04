# Carbon Client

Carbon Client is a PC-focused, client-only Fabric mod for Minecraft 26.2 and Java 25. It provides a larger, rounded Carbon-branded module menu, an in-game HUD layout editor, and a small registry of client-side HUD and visual modules. Feather and Lunar are high-level interaction references only; Carbon does not use their branding or assets.

## Toolchain

- Minecraft `26.2`
- Java `25`
- Fabric Loader `0.19.5+`
- Fabric API `0.161.0+26.2`
- Fabric Loom `1.18-SNAPSHOT`
- Gradle wrapper `9.8.0`

Minecraft 26.x is unobfuscated, so this project intentionally has no mappings dependency. It uses Loom's `net.fabricmc.fabric-loom` plugin ID and `implementation` dependencies.

## Build and controls

With a Java 25 JDK installed, run:

```sh
./gradlew build --no-daemon
```

The distributable is `build/libs/carbonclient-1.0.0.jar`. Install it in a Minecraft 26.2 PC profile with Fabric Loader and Fabric API.

The Carbon menu key is registered in Minecraft's normal **Options → Controls → Key Binds → Carbon Client** category. It defaults to **Right Shift** and can be changed there. The menu does not use a hard-coded keyboard hook for opening.

## Menu

The larger menu uses opaque grayscale panels over the live, unpaused world. Solid rounded surfaces have shader-antialiased edges; there is no full-screen cover, glass treatment, or blur. The local username is paired with a vanilla-rendered 3D `PLAYER_HEAD` carrying the current player profile, without a square portrait border. It also includes:

- Live module categories and search across module names, descriptions, and IDs.
- Module cards that toggle the real module when clicked; enabled state is informational, not a separate ON/OFF control.
- A settings gear on every module card that opens that module's actual typed settings.
- Working Essentials, PvP, and Creator presets that update the registered starter modules.
- An in-game HUD editor: drag widgets to move them, drag the corner or use scale controls to resize them, preview disabled widgets, and reset placement/size. The world remains visible while editing.
- Persistent UI scale and bundled Inter regular/medium/semibold/bold faces, with a one-click Minecraft-font fallback and vanilla glyph references for unsupported characters.
- Saved configuration profiles backed by Carbon's existing config manager.

The retained starter modules are FPS, CPS, Keystrokes, and Zoom. Module values, keybinds, enabled state, HUD positions, HUD scale, and profiles are stored locally under `.minecraft/config/carbonclient/`; HUD placement/scale are normal typed module settings and are included in profiles. Menu scale and font preference are stored in `menu.json`; Minecraft stores the menu-opening key with its normal options. The mod makes no network calls.

## Bundled assets and validation

Bundled Inter font resources and licenses, the Lucide icon atlas, and a small solid rounded-rectangle shader remain in the project. The shader only computes antialiased coverage for opaque grayscale fills; it does not sample or blur the world. Carbon's glass shader, blur helpers, F8 test screen, and F7 layout-debug path have been removed.

The GitHub Actions workflow builds the next immutable test release as `v1.0.0-test.10` with Java 25. CI verifies compilation and packaging, not in-game behavior. The sandbox has no Minecraft runtime, so the 3D portrait, HUD editor interactions, live gameplay backdrop, keybind screen, settings, profiles, and low-resolution layout still need hands-on visual/runtime verification in Minecraft.
