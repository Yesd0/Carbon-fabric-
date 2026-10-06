package dev.carbon.client.ui;

import dev.carbon.client.core.config.ConfigManager;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.hud.HudManager;
import dev.carbon.client.ui.render.CarbonShapes;
import dev.carbon.client.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** Part 3: persistent Carbon interface, waypoint-overlay, and safety settings. */
public final class CarbonSettingsScreen extends CarbonScreen {
    private static final float PANEL_WIDTH = 1000.0f;
    private static final float PANEL_HEIGHT = 612.0f;

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final HudManager hudManager;
    private final WaypointManager waypointManager;
    private final CarbonUiState uiState;
    private final Box panel = new Box();
    private final Box modsTab = new Box();
    private final Box waypointsTab = new Box();
    private final Box hudTab = new Box();
    private final Box settingsTab = new Box();
    private final Box closeButton = new Box();
    private final Box fontRow = new Box();
    private final Box waypointOverlayRow = new Box();
    private final Box keyRow = new Box();
    private final Box profileRow = new Box();
    private final Box blurRow = new Box();
    private final Box resetButton = new Box();
    private final Box resetConfirm = new Box();
    private final Box resetCancel = new Box();
    private boolean confirmReset;
    private float mouseX;
    private float mouseY;

    public CarbonSettingsScreen(ModuleManager modules, ConfigManager configManager, HudManager hudManager,
                                WaypointManager waypointManager, CarbonUiState uiState) {
        super(Component.literal("Carbon Client Settings"), null);
        this.modules = Objects.requireNonNull(modules, "modules");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.hudManager = Objects.requireNonNull(hudManager, "hudManager");
        this.waypointManager = Objects.requireNonNull(waypointManager, "waypointManager");
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        CarbonText.setTypeface(uiState.typeface());
    }

    @Override
    protected void init() {
        super.init();
        UiScale.update(Minecraft.getInstance());
        CarbonIcons.load();
        calculateLayout();
    }

    private void calculateLayout() {
        float x = (UiScale.designWidth() - PANEL_WIDTH) * 0.5f;
        float y = (UiScale.designHeight() - PANEL_HEIGHT) * 0.5f;
        panel.set(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        modsTab.set(x + 224.0f, y + 16.0f, 68.0f, 32.0f);
        waypointsTab.set(x + 300.0f, y + 16.0f, 96.0f, 32.0f);
        hudTab.set(x + 404.0f, y + 16.0f, 88.0f, 32.0f);
        settingsTab.set(x + 500.0f, y + 16.0f, 92.0f, 32.0f);
        closeButton.set(x + PANEL_WIDTH - 50.0f, y + 14.0f, 34.0f, 34.0f);
        float rowX = x + 242.0f;
        float rowWidth = 720.0f;
        fontRow.set(rowX, y + 164.0f, rowWidth, 58.0f);
        waypointOverlayRow.set(rowX, y + 236.0f, rowWidth, 58.0f);
        keyRow.set(rowX, y + 308.0f, rowWidth, 58.0f);
        profileRow.set(rowX, y + 380.0f, rowWidth, 58.0f);
        blurRow.set(rowX, y + 452.0f, rowWidth, 58.0f);
        resetButton.set(x + 242.0f, y + 530.0f, 184.0f, 36.0f);
        resetConfirm.set(x + 505.0f, y + 333.0f, 138.0f, 36.0f);
        resetCancel.set(x + 657.0f, y + 333.0f, 138.0f, 36.0f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (UiScale.update(Minecraft.getInstance())) {
            calculateLayout();
        }
        this.mouseX = UiScale.toDesign(mouseX);
        this.mouseY = UiScale.toDesign(mouseY);
        if (!CarbonShapes.isReady()) {
            UiScale.pushRendererScale(graphics);
            try {
                drawCarbonRenderFailure(graphics);
            } finally {
                UiScale.popRendererScale(graphics);
            }
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        UiScale.pushRendererScale(graphics);
        try {
            drawSurfaces(graphics);
            graphics.nextStratum();
            drawText(graphics);
        } finally {
            UiScale.popRendererScale(graphics);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSurfaces(GuiGraphicsExtractor graphics) {
        CarbonShapes.drawRounded(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                0.0f, CarbonTheme.BACKDROP);
        CarbonShapes.drawVignette(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                CarbonTheme.VIGNETTE);
        CarbonShapes.drawShadow(graphics, panel.x, panel.y, panel.width, panel.height,
                12.0f, 64.0f, 24.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, panel.x, panel.y, panel.width, panel.height,
                12.0f, 1.0f, CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.WINDOW_TOP, CarbonTheme.WINDOW_BOTTOM);
        CarbonShapes.drawGradientRect(graphics, panel.x + 1.0f, panel.y + 63.0f,
                panel.width - 2.0f, 1.0f, 0.0f, CarbonTheme.DIVIDER, CarbonTheme.DIVIDER);
        drawButton(graphics, modsTab, modsTab.contains(mouseX, mouseY), false);
        drawButton(graphics, waypointsTab, waypointsTab.contains(mouseX, mouseY), false);
        drawButton(graphics, hudTab, hudTab.contains(mouseX, mouseY), false);
        drawButton(graphics, settingsTab, true, false);
        drawButton(graphics, closeButton, closeButton.contains(mouseX, mouseY), false);
        drawRow(graphics, fontRow);
        drawRow(graphics, waypointOverlayRow);
        drawRow(graphics, keyRow);
        drawRow(graphics, profileRow);
        drawRow(graphics, blurRow);
        drawButton(graphics, resetButton, resetButton.contains(mouseX, mouseY), false);
        if (confirmReset) {
            CarbonShapes.drawRounded(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                    0.0f, 0xAA000000);
            Box modal = new Box(panel.x + 320.0f, panel.y + 226.0f, 360.0f, 180.0f);
            CarbonShapes.drawShadow(graphics, modal.x, modal.y, modal.width, modal.height,
                    12.0f, 24.0f, 10.0f, CarbonTheme.WINDOW_SHADOW);
            CarbonShapes.drawBorderedSurface(graphics, modal.x, modal.y, modal.width, modal.height,
                    12.0f, 1.0f, CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                    CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
            drawButton(graphics, resetConfirm, resetConfirm.contains(mouseX, mouseY), true);
            drawButton(graphics, resetCancel, resetCancel.contains(mouseX, mouseY), false);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, Box row) {
        CarbonShapes.drawBorderedSurface(graphics, row.x, row.y, row.width, row.height,
                9.0f, 1.0f, CarbonTheme.BUTTON_BORDER, CarbonTheme.BUTTON_BORDER,
                CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
        CarbonShapes.drawTopHighlight(graphics, row.x + 1.0f, row.y + 1.0f,
                row.width - 2.0f, 9.0f, CarbonTheme.WINDOW_HIGHLIGHT);
    }

    private void drawButton(GuiGraphicsExtractor graphics, Box bounds, boolean hovered, boolean active) {
        int top = active ? CarbonTheme.ACCENT : hovered ? CarbonTheme.BUTTON_HOVER_TOP : CarbonTheme.BUTTON_TOP;
        int bottom = active ? CarbonTheme.ACCENT_DEEP
                : hovered ? CarbonTheme.BUTTON_HOVER_BOTTOM : CarbonTheme.BUTTON_BOTTOM;
        int border = active ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER;
        CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y, bounds.width, bounds.height,
                7.0f, 1.0f, border, border, top, bottom);
    }

    private void drawText(GuiGraphicsExtractor graphics) {
        CarbonText.drawTracked(graphics, font, "CARBON", CarbonText.Weight.BOLD, 18.0f,
                panel.x + 24.0f, panel.y + 17.0f, CarbonTheme.TEXT, CarbonText.Tracking.WORDMARK);
        CarbonText.drawTracked(graphics, font, "CLIENT", CarbonText.Weight.MEDIUM, 9.0f,
                panel.x + 122.0f, panel.y + 24.0f, CarbonTheme.TEXT_MUTED, CarbonText.Tracking.LABEL);
        drawTab(graphics, modsTab, "MODS", CarbonTheme.TEXT_MUTED);
        drawTab(graphics, waypointsTab, "WAYPOINTS", CarbonTheme.TEXT_MUTED);
        drawTab(graphics, hudTab, "HUD EDITOR", CarbonTheme.TEXT_MUTED);
        drawTab(graphics, settingsTab, "SETTINGS", CarbonTheme.TEXT);
        CarbonIcons.drawDesign(graphics, "x", closeButton.x + 9.0f, closeButton.y + 9.0f,
                16.0f, CarbonTheme.TEXT_MUTED);
        CarbonIcons.drawDesign(graphics, "settings", panel.x + 246.0f, panel.y + 92.0f,
                24.0f, CarbonTheme.ACCENT);
        CarbonText.draw(graphics, font, "CARBON SETTINGS", CarbonText.Weight.BOLD, 18.0f,
                panel.x + 280.0f, panel.y + 94.0f, CarbonTheme.TEXT, false);
        CarbonText.draw(graphics, font, "Appearance, HUD overlay, and local profile controls.",
                CarbonText.Weight.REGULAR, 11.0f, panel.x + 280.0f, panel.y + 122.0f,
                CarbonTheme.TEXT_MUTED, false);

        drawSettingLabel(graphics, fontRow, "Menu typeface", "Minecraft UI is the safe default; Inter is optional and can be switched back here.");
        CarbonText.draw(graphics, font,
                uiState.typeface() == CarbonUiState.Typeface.INTER ? "INTER · TEST" : "MINECRAFT UI",
                CarbonText.Weight.SEMIBOLD, 10.0f, fontRow.x + fontRow.width - 170.0f,
                fontRow.y + 22.0f, CarbonTheme.ACCENT_HIGHLIGHT, false);
        drawSettingLabel(graphics, waypointOverlayRow, "Waypoint HUD", "Show the nearest saved waypoints while playing.");
        CarbonText.draw(graphics, font, waypointManager.showOnHud() ? "ON" : "OFF",
                CarbonText.Weight.BOLD, 11.0f, waypointOverlayRow.x + waypointOverlayRow.width - 76.0f,
                waypointOverlayRow.y + 21.0f,
                waypointManager.showOnHud() ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT, false);
        drawSettingLabel(graphics, keyRow, "Menu shortcut", "Change it in Minecraft Options → Controls → Key Binds.");
        CarbonText.draw(graphics, font, CarbonUI.menuKeyLabel(), CarbonText.Weight.SEMIBOLD, 11.0f,
                keyRow.x + keyRow.width - 150.0f, keyRow.y + 21.0f, CarbonTheme.TEXT, false);
        drawSettingLabel(graphics, profileRow, "Active profile", "Module toggles and settings save to the selected profile.");
        CarbonText.draw(graphics, font, displayProfile(configManager.activeProfile()), CarbonText.Weight.SEMIBOLD,
                11.0f, profileRow.x + profileRow.width - 174.0f, profileRow.y + 21.0f,
                CarbonTheme.ACCENT_HIGHLIGHT, false);
        drawSettingLabel(graphics, blurRow, "World backdrop", "Matte black overlay · native blur stays off.");
        CarbonText.draw(graphics, font, "45% DIM", CarbonText.Weight.SEMIBOLD, 10.0f,
                blurRow.x + blurRow.width - 92.0f, blurRow.y + 22.0f, CarbonTheme.TEXT, false);
        CarbonIcons.drawDesign(graphics, "trash", resetButton.x + 12.0f, resetButton.y + 10.0f,
                16.0f, CarbonTheme.TEXT);
        CarbonText.drawTracked(graphics, font, "RESET UI STATE", CarbonText.Weight.SEMIBOLD, 9.0f,
                resetButton.x + 38.0f, resetButton.y + 12.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
        if (confirmReset) {
            CarbonText.drawTracked(graphics, font, "RESET CARBON UI?", CarbonText.Weight.BOLD, 12.0f,
                    panel.x + 346.0f, panel.y + 254.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
            CarbonText.draw(graphics, font, "This clears filters, pins, search, and view preferences.",
                    CarbonText.Weight.REGULAR, 10.0f, panel.x + 346.0f, panel.y + 284.0f,
                    CarbonTheme.TEXT_MUTED, false);
            CarbonText.drawTracked(graphics, font, "RESET", CarbonText.Weight.BOLD, 9.0f,
                    resetConfirm.x + 44.0f, resetConfirm.y + 12.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
            CarbonText.drawTracked(graphics, font, "CANCEL", CarbonText.Weight.SEMIBOLD, 9.0f,
                    resetCancel.x + 39.0f, resetCancel.y + 12.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
        }
    }

    private void drawSettingLabel(GuiGraphicsExtractor graphics, Box row, String label, String description) {
        CarbonText.draw(graphics, font, label, CarbonText.Weight.SEMIBOLD, 12.0f,
                row.x + 18.0f, row.y + 10.0f, CarbonTheme.TEXT, false);
        CarbonText.draw(graphics, font, description, CarbonText.Weight.REGULAR, 9.0f,
                row.x + 18.0f, row.y + 34.0f, CarbonTheme.TEXT_MUTED, false);
    }

    private void drawTab(GuiGraphicsExtractor graphics, Box box, String label, int color) {
        CarbonText.centeredTracked(graphics, font, label, CarbonText.Weight.SEMIBOLD, 8.0f,
                box.x + box.width * 0.5f, box.y + 12.0f, color, CarbonText.Tracking.LABEL);
    }

    private static String displayProfile(String value) {
        if (value == null || value.isBlank() || value.equals("default")) {
            return "Default";
        }
        String[] words = value.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!name.isEmpty()) {
                    name.append(' ');
                }
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return name.toString();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        UiScale.update(Minecraft.getInstance());
        float x = UiScale.toDesign(event.x());
        float y = UiScale.toDesign(event.y());
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        if (confirmReset) {
            if (resetConfirm.contains(x, y)) {
                uiState.resetPresentation();
                CarbonText.setTypeface(uiState.typeface());
                confirmReset = false;
                return true;
            }
            if (resetCancel.contains(x, y) || !panel.contains(x, y)) {
                confirmReset = false;
                return true;
            }
            return true;
        }
        if (modsTab.contains(x, y)) {
            CarbonUI.openMods();
            return true;
        }
        if (waypointsTab.contains(x, y)) {
            CarbonUI.openWaypoints();
            return true;
        }
        if (hudTab.contains(x, y)) {
            CarbonUI.openHudEditor();
            return true;
        }
        if (closeButton.contains(x, y)) {
            onClose();
            return true;
        }
        if (fontRow.contains(x, y)) {
            CarbonUiState.Typeface next = uiState.typeface() == CarbonUiState.Typeface.MINECRAFT
                    ? CarbonUiState.Typeface.INTER : CarbonUiState.Typeface.MINECRAFT;
            uiState.setTypeface(next);
            CarbonText.setTypeface(next);
            return true;
        }
        if (waypointOverlayRow.contains(x, y)) {
            waypointManager.setShowOnHud(!waypointManager.showOnHud());
            return true;
        }
        if (resetButton.contains(x, y)) {
            confirmReset = true;
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (confirmReset && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            confirmReset = false;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            CarbonUI.openWaypoints();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        CarbonUI.openMods();
    }

    private static final class Box {
        private float x;
        private float y;
        private float width;
        private float height;

        private Box() {
        }

        private Box(float x, float y, float width, float height) {
            set(x, y, width, height);
        }

        private void set(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }
}
