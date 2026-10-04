# Carbon Client — Parts A–B

Carbon Client is a PC-focused, client-only Fabric mod for Minecraft 26.2 and Java 25. Part A provides Carbon's scale helper, bundled Inter weights, Lucide atlas, SDF glass renderer, and visual test screen. Part B adds the real-module menu with Carbon sidebar navigation, category tabs, search, module cards, settings, and local profiles.

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

## Part A rendering

- `UiScale` maps the 1920×1080 reference design to Minecraft GUI coordinates and snaps to physical pixels.
- Four static Inter TTF weights are bundled with the SIL Open Font License. Font providers use fully qualified `carbonclient:font/...` resource locations so Minecraft can load the custom glyphs.
- Lucide SVG sources, ISC license, atlas-builder script, 96×96 glyph atlas, and JSON index are included.
- Carbon's SDF shader renders rounded, translucent glass, grain, highlight, border, shadow, and ON-state cards. Rendering failures are logged and shown in red; there is no flat-fill fallback.
- **Blur path:** Minecraft's native whole-backdrop GUI blur follows the Menu Background Blurriness option. Per-panel framebuffer sampling, quarter-scale Kawase/Gaussian capture, and resize caching are not implemented; the active path is named in the visual test screen.

## Part B module menu

- The centered 1000×620 reference layout has a 188px Carbon sidebar, Carbon wordmark and green mark, Modules/Settings navigation, profile controls, header search, All/HUD/Visual/Utility/Performance tabs, and a responsive three-column card grid wired to real Carbon modules.
- Cards use the bundled atlas icons, module descriptions, glass ON/OFF states, a toggle, and a settings action. Search, category filtering, scrolling, profiles, and setting controls are connected to the existing module/config managers. No placeholder modules are added.
- The module menu has no animated test-pattern layer; only the Part A F8 test screen draws that pattern.

## Validation status

The local sandbox has no Java runtime, so Gradle cannot run locally. GitHub Actions builds the JAR with Java 25; no PC Minecraft launch, font rendering check, visual review, or Spark comparison has been completed here. After installing, verify GUI scales 1–4, resize, 1080p/1440p, font glyphs, blur/shader rendering, the static module-menu backdrop, and Spark FPS against the vanilla pause screen.
