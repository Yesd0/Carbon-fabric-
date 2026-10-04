# Carbon Client

Carbon Client is a PC-focused, client-only Fabric mod for Minecraft 26.2 and Java 25. It ships a single Carbon-branded module menu, a small registry of client modules, persistent settings and profiles, and a separate glass-rendering test screen. Lunar Client is a high-level interaction reference only; this project does not copy its branding or assets.

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

- **Right Shift** opens the Carbon module menu.
- **F8** opens the separate glass-rendering test screen; its animated color pattern is intentionally kept there.
- **F7** toggles the layout-debug overlay while a Carbon screen is open.

## Module menu

The menu is a single interface with a Carbon sidebar, a local username and 3D skin-head portrait, module categories, search, quick presets, profiles, and a searchable module-card area. Card clicks toggle the real registered module; the settings control opens the selected module's typed settings. Boolean, numeric, mode, color, and keybind settings write through the existing config listeners.

First launch uses the solid charcoal theme so the controls remain legible and available independently of the glass renderer. From the same menu, **Settings → Menu** switches that interface to the optional glass finish and adjusts the persistent Carbon UI scale. Glass-render failures continue to be logged; the menu switches back to its usable solid theme if its glass pipeline is unavailable. Menu-only animated pattern/particle backgrounds are not drawn; the F8 test screen is unchanged.

Profiles and module configuration stay local to `.minecraft/config/carbonclient/`. The global menu appearance and scale preference is stored alongside them in `menu.json`. The mod makes no network calls.

## Rendering and bundled assets

- `UiScale` maps Carbon UI sizing to Minecraft GUI coordinates and snaps to physical pixels. Its user multiplier is shared by Carbon's UI/HUD layout.
- Bundled Inter font resources remain in the project. Interactive labels use Minecraft's standard glyph provider because custom glyph rendering previously produced boxes on the target client.
- The Lucide SVG sources, ISC license, atlas-builder script, 96×96 glyph atlas, and JSON index remain bundled.
- Carbon's SDF renderer provides rounded glass, grain, highlight, border, shadow, and active-card treatment. Renderer failures are logged; there is no fallback that silently pretends the glass shader succeeded. The module menu's explicit solid theme is a separate, user-selectable styling mode.
- The F8 test screen uses Minecraft's native whole-backdrop GUI blur and retains its animated color field. Per-panel framebuffer sampling and custom Kawase/Gaussian blur are not implemented; the test screen shows the active blur path.

## Rebuild and validation status

The `.5` JAR was reported in-game as visually unchanged and nonfunctional. This rebuild replaces the module menu's rendering and input/hit-testing path rather than applying another font or opacity tweak. GitHub Actions builds the next immutable test release with Java 25. The sandbox has no Minecraft runtime, so neither CI success nor source presence is claimed as in-game verification; please verify the menu, skin portrait, controls, settings, saved profiles, theme switching, and UI scale in Minecraft.
