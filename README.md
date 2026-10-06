# Carbon Client

Carbon Client is a PC-focused, client-only Fabric mod for Minecraft 26.2 and Java 25. Part 3 now builds on the live Mods menu with Settings, local Waypoints, and a draggable HUD editor. Right Shift opens the non-pausing Carbon menu; the game keeps rendering behind it.

## Toolchain

- Minecraft `26.2` (unobfuscated)
- Java `25`
- Fabric Loader `0.19.1+`
- Fabric API `0.161.0+26.2`
- Fabric Loom `1.18-SNAPSHOT`
- Gradle wrapper `9.8.0`

No third-party UI library is used. With a Java 25 JDK installed:

```sh
./gradlew build --no-daemon
./gradlew runClient
```

The menu key is registered in vanilla **Options → Controls → Key Binds → Carbon Client**. F6 toggles layout measurements in the Mods screen. Minecraft's own UI font is the default to avoid the reported missing/square glyphs; the Settings page allows opting into the bundled Inter face and switching back. Inter's four TTF providers also reference `minecraft:default` for missing glyph coverage.

## Visual system

- Layout stays in 1920×1080 design pixels. `UiScale` applies `clamp(min(fbWidth/1920, fbHeight/1080), 0.6, 1.6)`, converts through Minecraft's GUI scale, and snaps to whole physical pixels.
- The palette is matte black, Carbon green for active/on states, and white text. The window is 96% opaque, with subtle edges/highlights, shadows, and a 45% world dimmer. Native blur is off; there is no glass/frosted treatment.
- Rounded panels use Carbon's SDF shader. If registration or source preflight fails, Carbon logs an ERROR and displays the red `Carbon render failed: <reason>` label; it does not silently substitute flat rectangles.
- Inter regular, medium, semibold, and bold TTF assets use 4× oversampling and the SIL Open Font License. The real-file Lucide atlas contains 24 official SVG icons and includes its license. Upstream no longer supplies `trash-2.svg`; the atlas uses official `trash.svg` for that entry.

## Mods menu

- Live starter modules, typed settings, pinning, seven filters, search, sort, grid/list view, delayed tooltips, and keyboard controls.
- Profiles can be switched or created. Module toggles and settings use Carbon's existing profile/config system.
- Filter, query, view, sort, typeface, and pins persist in `.minecraft/config/carbonclient/ui.json`.

## Part 3: Settings, Waypoints, HUD editor

- **Settings** exposes the safe/default font choice, optional Inter face, waypoint HUD overlay, the vanilla keybind route, active profile status, and a confirmed reset for presentation state. Native blur remains off.
- **Waypoints** saves a named block position and dimension locally in `.minecraft/config/carbonclient/waypoints.json`. Search and dimension filtering, coordinate copy, confirmed delete, and an optional nearest-waypoint HUD list are included. It does not teleport or send data to a server.
- **HUD editor** previews enabled and disabled HUD modules without changing their enabled state, supports drag-to-place, keyboard nudging, scale controls, reset, and enable toggles. Position and scale are persisted by the current module profile.
- Screens use the same black/green/white theme, a non-pausing backdrop, tooltips/toasts, and confirmation dialogs for destructive reset/delete actions.

## Verification

CI `clean build` checks compilation and packaging; it is not visual/runtime verification. Test in a Java 25 Minecraft 26.2 Fabric profile at GUI Scale 1–4; resize the window; compare 1080p and 1440p; confirm ordinary words render rather than square glyphs; test the Minecraft and Inter typefaces, modules/settings/profiles, waypoints, HUD drag/scale/reset, and the optional waypoint overlay. Use Spark to compare FPS with the menu open and closed. In-game font/shader appearance and Spark results remain unverified in this sandbox.
