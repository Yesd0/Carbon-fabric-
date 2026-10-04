# Carbon Client — Parts A–B

Carbon Client is a PC-focused, client-only Fabric mod for Minecraft 26.2 and Java 25. Part A provides Carbon's scale helper, bundled Inter weights, Lucide atlas, SDF glass renderer, and visual test screen. Part B now includes a clearer, animated module dashboard with functional module switches, real module settings, quick presets, local profiles, and a 3D preview of the signed-in player's skin.

## Toolchain pins

- Minecraft `26.2`
- Java `25`
- Fabric Loader `0.19.5`
- Fabric API `0.161.0+26.2`
- Fabric Loom `1.18-SNAPSHOT`
- Gradle wrapper `9.8.0`

Minecraft 26.x is unobfuscated, so this project intentionally has no mappings dependency. It uses Loom's `net.fabricmc.fabric-loom` plugin ID and `implementation` dependencies.

## Build and test

Install a Java 25 JDK and run:

```sh
./gradlew build --no-daemon
```

The distributable is `build/libs/carbonclient-1.0.0.jar`. Install it in a Minecraft 26.2 PC profile with Fabric Loader and Fabric API. **Right Shift** opens the module menu; **F8** opens the glass-rendering test screen; **F7** toggles the test-screen layout-debug overlay. The test screen keeps its moving color pattern; the module menu does not draw that animated pattern.

## Part 1 foundation

- `core/module`: categorized modules, module registry, lifecycle, and safe failure handling.
- `core/setting`: Bool, Number, Color, Mode, and Keybind settings.
- `core/event`: exact-type, no-reflection event bus and client/HUD/input events.
- `core/config`: schema-versioned Gson config, local profiles, corrupt-file recovery, atomic asynchronous writes.
- `core/util`, `hud`, `modules`: anchoring/color helpers, HUD manager/base, Keystrokes, CPS, FPS, and Zoom.
- Configuration and profiles remain local to `.minecraft/config/carbonclient/`; the foundation makes no network calls.

## Part A rendering and readable fonts

- `UiScale` maps the 1920×1080 reference design to Minecraft GUI coordinates and snaps to physical pixels.
- Four static Inter weights are bundled under the SIL Open Font License. The menu now uses generated, antialiased bitmap atlases with a vanilla-font fallback rather than TTF glyph atlases, avoiding common shader/backend TTF glyph artifacts while retaining the bundled font appearance.
- Lucide SVG sources, ISC license, atlas-builder script, 96×96 glyph atlas, and JSON index are included.
- Carbon's SDF shader renders rounded, translucent glass, grain, highlight, border, shadow, and ON-state cards. Rendering failures are logged and shown in red; there is no flat-fill fallback.
- **Blur path:** Minecraft's native whole-backdrop GUI blur follows the Menu Background Blurriness option. Per-panel framebuffer sampling, quarter-scale Kawase/Gaussian capture, and resize caching are not implemented; the active path is named in the visual test screen.

## Part B module menu

- The centered dashboard has a high-contrast glass panel, Carbon sidebar, local username and 3D player-skin head, Modules/Settings navigation, a quick-preset box, and an active saved-profile selector with a new-profile button.
- Minimal, Creator, and PvP presets apply to the registered features. The live module grid shows real Carbon modules only; each switch toggles its module lifecycle, the settings action opens that module's controls, and category/search/scroll/profile actions are wired to the existing managers.
- The menu uses an eased open transition, staggered module-card entrances, animated hover and toggle states, an idle/mouse-tracked avatar, and a short action toast. Only populated categories are shown. No placeholder modules are added.
- The module menu has no animated test-pattern layer; only the Part A F8 test screen draws that pattern.

## Validation status

The local sandbox has no Java runtime, so Gradle cannot run locally. GitHub Actions builds the JAR with Java 25; no PC Minecraft launch, font screenshot, visual review, or Spark comparison has been completed here. After installing, verify GUI scales 1–4, resize, 1080p/1440p, bitmap-font clarity (including with your shader setup), local-skin head rendering, module toggles/presets/settings, blur rendering, and Spark FPS against the vanilla pause screen.
