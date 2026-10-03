# Carbon Client — Part A

Carbon Client is a client-only Fabric mod for Minecraft 26.2 and Java 25. Part A supplies the UI scale, Inter font assets and license, Lucide icon build pipeline and atlas, Carbon's SDF glass pipeline, the native-menu-blur backdrop path, and an in-game glass test screen. Part B's module-menu layout and Right Shift binding are not part of this stage.

## Toolchain pins

- Minecraft `26.2`
- Java `25`
- Fabric Loader `0.19.5`
- Fabric API `0.161.0+26.2`
- Fabric Loom `1.18-SNAPSHOT`
- Gradle wrapper `9.8.0`

Minecraft 26.x is unobfuscated, so this project intentionally has no mappings dependency. It uses Loom's `net.fabricmc.fabric-loom` plugin ID and `implementation` dependencies.

## Build and run

Install a Java 25 JDK, then run:

```sh
./gradlew build --no-daemon
```

The distributable is `build/libs/carbonclient-1.0.0.jar`. Install it with Fabric Loader and Fabric API for Minecraft 26.2. In-game, press **F8** to open the Part A `CarbonGlassTestScreen`; press **F7** to toggle its layout-debug overlay. The test screen does not pause the world.

## Part 1 foundation

- `core/module`: categorized modules, module registry, lifecycle and safe failure handling.
- `core/setting`: Bool, Number, Color, Mode, and Keybind settings.
- `core/event`: exact-type, no-reflection event bus and client/HUD/input events.
- `core/config`: schema-versioned Gson config, local profiles, corrupt-file recovery, atomic asynchronous writes.
- `core/util`, `hud`, `modules`: anchoring/color helpers, HUD manager/base, Keystrokes, CPS, FPS, and Zoom.
- Configuration and profiles remain local to `.minecraft/config/carbonclient/`; the foundation makes no network calls.

## Part A rendering assets and test

- `UiScale` maps the 1920×1080 design reference to Minecraft GUI coordinates and snaps geometry to framebuffer pixels.
- Minecraft's TTF provider does not select variable-font axes. The official Inter variable TTF is therefore instanced as separate Regular, Medium, SemiBold, and Bold TTFs. The source and SIL Open Font License are included under `tools/font-src/` and `assets/carbonclient/font/`.
- The requested Lucide icons are checked-in SVG source files under `tools/icons-src/`; `tools/build_icons.py` builds the white transparent atlas and JSON index at `assets/carbonclient/textures/gui/`. The ISC license is included with the icon sources. To rebuild the atlas, install `tools/requirements-icons.txt` and run `python tools/build_icons.py`.
- Glass panels and cards use a Carbon SDF shader with rounded masks, translucent tint, grain, highlight, borders, shadows, and the green enabled state. There is no flat-fill fallback: a rendering failure is logged and shown in red as `Carbon render failed: <reason>`.
- **Blur path:** Minecraft's native GUI blur is requested for the whole backdrop, controlled by Minecraft's Menu Background Blurriness option. It is used because 26.2's retained GUI extraction does not expose a per-panel framebuffer sample at the point Carbon submits a panel. The requested quarter-scale Kawase/Gaussian per-panel capture and resize cache are not implemented. The active native path is named on the test screen.
- The test screen draws a moving color pattern behind one main glass panel and two 240×96 sample cards (green ON and neutral OFF). F7 outlines the measured bounds and shows framebuffer dimensions, GUI scale, `uiScale`, renderer scale, and design-pixel rectangles.

## Validation status

The Gradle build could not start in this sandbox because no Java runtime or `JAVA_HOME` is installed; network access also prevented provisioning Java 25. No game launch or runtime rendering check has been completed here. This is a desktop/PC client target. On a Java 25 / Minecraft 26.2 PC client, check GUI scales 1–4, resize, 1080p and 1440p, and compare Spark FPS against the vanilla pause screen.
