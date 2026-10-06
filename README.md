# Carbon Client

Carbon Client is a PC-focused, client-only Fabric mod for Minecraft 26.2 and Java 25. This branch is being transformed in gated parts. **Part 2 now provides the live Carbon Mods menu**, building on the Part 1 visual foundation. The full Settings, Waypoints, and HUD-editor workflows remain outside this part.

## Toolchain

- Minecraft `26.2` (unobfuscated)
- Java `25`
- Fabric Loader `0.19.1+`
- Fabric API `0.161.0+26.2`
- Fabric Loom `1.18-SNAPSHOT`
- Gradle wrapper `9.8.0`

No third-party UI library is used. With a Java 25 JDK installed, build and launch the client with:

```sh
./gradlew build --no-daemon
./gradlew runClient
```

The Carbon menu is registered in vanilla **Options → Controls → Key Binds → Carbon Client** and defaults to **Right Shift**. It does not pause the game. Press **F6** to toggle measured layout bounds and design-pixel sizes.

## Part 1 visual foundation

- Layout values stay in 1920×1080 design pixels. `UiScale` applies `clamp(min(fbWidth/1920, fbHeight/1080), 0.6, 1.6)`, converts through Minecraft's GUI scale, and snaps positions and sizes to whole framebuffer pixels.
- Carbon uses matte dark gradients, 96% window opacity, subtle white borders and top highlights, layered shadows, a green accent, and a 45% matte world dimmer with radial vignette. Native background blur is off; there is no glass/frosted blur.
- Rounded surfaces use the custom SDF shader for coverage, gradients, and radial vignette. If registration or source preflight fails, Carbon logs an ERROR with the cause and displays a red `Carbon render failed: <reason>` message rather than substituting flat rectangles.
- Text uses bundled Inter TTF providers in regular, medium, semibold, and bold weights, with 4× oversampling and the SIL Open Font License. Text size is optically boosted and snapped to a minimum of 7 physical pixels to improve small-label legibility; tracked uppercase layouts and bounded text caches are retained.
- The icon atlas is built from real Lucide SVG files in `tools/icons-src/`, packed by `tools/build_icons.py`, mipmapped, and linearly sampled. It contains 24 icons and includes the Lucide license notice. Upstream no longer supplies `trash-2.svg`; the atlas uses the official `trash.svg` file for that entry.

## Part 2 Mods menu

- Real starter modules are displayed and toggled live. The card settings buttons open typed module settings: booleans, numbers/sliders, modes, colors, and keyboard/mouse keybinds update the actual module objects and are saved through Carbon's existing config manager.
- Filters cover all modules, HUD, visual, utility, performance, pinned, and enabled states. Search matches IDs, names, descriptions, categories, and setting labels/descriptions. Sorting supports name, category, and enabled status with either order; grid and list layouts are available.
- Profiles can be switched and created in the profile popover. Module enabled state and settings continue to use Carbon's profile/config system. Filter, query, view, sort, and pinned-module state persist in `.minecraft/config/carbonclient/ui.json`.
- Cards include real Lucide icons, pin and settings actions, active states, delayed tooltips, and empty-search feedback. Keyboard access includes search (`/` or Ctrl+F), arrow-key module navigation, Enter to toggle, Shift+Enter for settings, P to pin, Tab to change view, and Escape to close/dismiss.

The Part 1 style-test screen remains in the source for visual regression work; Right Shift now opens the live Mods menu. Part 3 remains gated: the full Settings screen, Waypoints, HUD editor, and broader dialogs/toasts/polish are not included.

## Verification

`./gradlew build --no-daemon` checks compilation and packaging; it is not visual/runtime verification. Please test in a Java 25 Minecraft 26.2 Fabric profile at GUI Scale 1–4, resize the window, compare 1080p and 1440p, check that Inter text is legible and stable, exercise module toggles/settings/profile changes and search/filter/sort/grid/list, and use Spark to compare FPS with the screen open and closed. In-game shader/text appearance and FPS remain unverified in this sandbox.
