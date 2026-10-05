# Carbon Client

Carbon Client is a PC-focused, client-only Fabric mod for Minecraft 26.2 and Java 25. This branch is being transformed in gated parts; **Part 1 currently installs Carbon's visual foundation and style-test screen only**. The full Mods, Settings, Waypoints, Profiles, and HUD-editor workflows are not part of this stage.

## Toolchain

- Minecraft `26.2` (unobfuscated)
- Java `25`
- Fabric Loader `0.19.1+`
- Fabric API `0.161.0+26.2`
- Fabric Loom `1.18-SNAPSHOT`
- Gradle wrapper `9.8.0`

No third-party UI library is used. With a Java 25 JDK installed, build with:

```sh
./gradlew build --no-daemon
```

To test in Minecraft:

```sh
./gradlew runClient
```

The Carbon style test is registered in vanilla **Options → Controls → Key Binds → Carbon Client** and defaults to **Right Shift**. Press **F6** while it is open to toggle measured layout bounds and design-pixel sizes. The screen is non-pausing; the game world continues behind a 45% matte dimmer and radial vignette.

## Part 1 rendering and assets

- Layout values stay in 1920×1080 design pixels. `UiScale` applies `clamp(min(fbWidth/1920, fbHeight/1080), 0.6, 1.6)`, converts through Minecraft's GUI scale, and snaps positions and sizes to whole framebuffer pixels.
- Carbon uses matte dark gradients, 96% window opacity, subtle white borders and top highlights, layered shadows, a green accent, and restrained monochrome shader noise. Native background blur is off; there is no glass/frosted blur.
- Rounded surfaces use the custom SDF shader for coverage, gradients and radial vignette; borders, bevels, shadows, and optional green glow are composed from SDF shapes. If registration or source validation fails, Carbon logs an ERROR with the cause and shows a red `Carbon render failed: <reason>` message instead of substituting flat rectangles.
- Text uses bundled Inter TTF providers in regular, medium, semibold, and bold weights, with 4× oversampling and the SIL Open Font License. Tracked uppercase text layouts are cached.
- The icon atlas is built from real Lucide SVG files in `tools/icons-src/`, packed by `tools/build_icons.py`, mipmapped and linearly sampled. It contains 24 icons and includes the Lucide license notice. Current Lucide renamed the requested `trash-2` asset to `trash`; Carbon uses the official `trash.svg` file for that entry.

The style-test screen includes a 1000×612 design-pixel window, profile/sidebar and header samples, filters/search/view/sort controls, four module-card examples, matte buttons, a live demo toggle, delayed tooltip, and the F6 layout-debug overlay. Its cards and sample controls demonstrate appearance; they do not edit real modules or persist profile state. The retained ModuleManager, settings, config, event, and HUD systems remain available for the later gated parts. The old menu and HUD-editor visuals have been removed rather than left active beside the new style test.

## Verification

`./gradlew build --no-daemon` checks Java compilation and packaging; it is not visual/runtime verification. Please verify in a Java 25 Minecraft 26.2 Fabric profile at GUI Scale 1–4, resize the window, compare 1080p and 1440p, and use Spark to compare FPS with the screen open/closed. In-game shader appearance and that performance comparison have not been verified in this sandbox.
