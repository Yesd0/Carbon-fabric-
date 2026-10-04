# Carbon Client

Carbon Client is a PC-focused, client-only Fabric mod for Minecraft 26.2 and Java 25. It provides a compact Carbon-branded module menu and a small registry of client-side HUD and visual modules. Lunar Client is a high-level interaction reference only; Carbon does not use Lunar branding or assets.

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

The menu uses an opaque, solid black-and-white palette with rounded panels, compact module cards, subtle hover/enable/entry animations, and no glass, blur, or translucent surfaces. It includes:

- The current Minecraft username and a 3D head rendered from the local player's skin.
- Live module categories and search across module names, descriptions, and IDs.
- Module cards that toggle the real module when clicked; enabled state is informational, not a separate ON/OFF control.
- A settings gear on every module card that opens that module's actual typed settings.
- Working Essentials, PvP, and Creator presets that update the registered starter modules.
- Persistent UI scale and a bundled Carbon Display font, with a one-click Minecraft-font fallback if the custom face is not legible on a particular client.
- Saved configuration profiles backed by Carbon's existing config manager.

The retained starter modules are FPS, CPS, Keystrokes, and Zoom. Module values, keybinds, enabled state, and profiles are stored locally under `.minecraft/config/carbonclient/`. Menu scale and font preference are stored in `menu.json`; Minecraft stores the menu-opening key with its normal options. The mod makes no network calls.

## Bundled assets and validation

Bundled Inter/Carbon font resources, their licenses, and the Lucide icon atlas remain in the project. Carbon's glass shader, blur helpers, F8 test screen, and F7 layout-debug path have been removed. The UI draws its rounded surfaces with ordinary opaque GUI fills; there is no shader or glass fallback path.

The GitHub Actions workflow builds the next immutable test release as `v1.0.0-test.8` with Java 25. CI verifies compilation and packaging, not in-game behavior. The sandbox has no Minecraft runtime, so the menu, keybind screen, skin portrait, settings, profiles, and scale still need hands-on verification in Minecraft.
