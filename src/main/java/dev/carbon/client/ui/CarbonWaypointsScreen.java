package dev.carbon.client.ui;

import dev.carbon.client.core.config.ConfigManager;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.hud.HudManager;
import dev.carbon.client.ui.render.CarbonShapes;
import dev.carbon.client.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Part 3: local waypoints, current-dimension filtering, and copy/delete actions. */
public final class CarbonWaypointsScreen extends CarbonScreen {
    private static final float PANEL_WIDTH = 1000.0f;
    private static final float PANEL_HEIGHT = 612.0f;
    private static final int MAX_ROWS = 7;
    private static final int MAX_NAME_LENGTH = 32;

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final HudManager hudManager;
    private final WaypointManager waypointManager;
    private final CarbonUiState uiState;
    private final List<WaypointManager.Waypoint> visible = new ArrayList<>();
    private final Box panel = new Box();
    private final Box modsTab = new Box();
    private final Box waypointsTab = new Box();
    private final Box hudTab = new Box();
    private final Box settingsTab = new Box();
    private final Box closeButton = new Box();
    private final Box searchBox = new Box();
    private final Box dimensionButton = new Box();
    private final Box overlayButton = new Box();
    private final Box addButton = new Box();
    private final Box[] rowBounds = new Box[MAX_ROWS];
    private final Box[] copyBounds = new Box[MAX_ROWS];
    private final Box[] deleteBounds = new Box[MAX_ROWS];
    private final Box modal = new Box();
    private final Box inputBox = new Box();
    private final Box confirmButton = new Box();
    private final Box cancelButton = new Box();
    private final Box deleteConfirmButton = new Box();
    private final Box deleteCancelButton = new Box();

    private String query = "";
    private String nameDraft = "";
    private String pendingDeleteId;
    private String toast = "";
    private long toastUntil;
    private long hoverStarted;
    private String hoverText = "";
    private float mouseX;
    private float mouseY;
    private int scroll;
    private boolean currentDimensionOnly = true;
    private boolean searchFocused;
    private boolean createDialog;
    private boolean deleteDialog;
    private boolean caretVisible = true;
    private long lastCaretNanos;

    public CarbonWaypointsScreen(ModuleManager modules, ConfigManager configManager, HudManager hudManager,
                                 WaypointManager waypointManager, CarbonUiState uiState) {
        super(Component.literal("Carbon Waypoints"), null);
        this.modules = Objects.requireNonNull(modules, "modules");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.hudManager = Objects.requireNonNull(hudManager, "hudManager");
        this.waypointManager = Objects.requireNonNull(waypointManager, "waypointManager");
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        CarbonText.setTypeface(uiState.typeface());
        for (int index = 0; index < MAX_ROWS; index++) {
            rowBounds[index] = new Box();
            copyBounds[index] = new Box();
            deleteBounds[index] = new Box();
        }
        rebuildVisible();
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
        searchBox.set(x + 232.0f, y + 88.0f, 306.0f, 34.0f);
        dimensionButton.set(x + 548.0f, y + 88.0f, 136.0f, 34.0f);
        overlayButton.set(x + 692.0f, y + 88.0f, 142.0f, 34.0f);
        addButton.set(x + 842.0f, y + 88.0f, 142.0f, 34.0f);
        for (int index = 0; index < MAX_ROWS; index++) {
            float rowY = y + 136.0f + index * 60.0f;
            rowBounds[index].set(x + 232.0f, rowY, 752.0f, 52.0f);
            copyBounds[index].set(x + 888.0f, rowY + 10.0f, 30.0f, 30.0f);
            deleteBounds[index].set(x + 936.0f, rowY + 10.0f, 30.0f, 30.0f);
        }
        modal.set(x + 320.0f, y + 218.0f, 360.0f, 190.0f);
        inputBox.set(modal.x + 24.0f, modal.y + 76.0f, 312.0f, 36.0f);
        cancelButton.set(modal.x + 24.0f, modal.y + 138.0f, 148.0f, 34.0f);
        confirmButton.set(modal.x + 184.0f, modal.y + 138.0f, 152.0f, 34.0f);
        deleteConfirmButton.set(modal.x + 184.0f, modal.y + 128.0f, 152.0f, 34.0f);
        deleteCancelButton.set(modal.x + 24.0f, modal.y + 128.0f, 148.0f, 34.0f);
    }

    private void rebuildVisible() {
        visible.clear();
        String dimension = waypointManager.currentDimension();
        String queryLower = query.toLowerCase(Locale.ROOT).strip();
        for (WaypointManager.Waypoint waypoint : waypointManager.waypoints()) {
            if (currentDimensionOnly && !dimension.isEmpty() && !waypoint.dimension().equals(dimension)) {
                continue;
            }
            if (!queryLower.isEmpty() && !waypoint.name().toLowerCase(Locale.ROOT).contains(queryLower)
                    && !waypoint.dimension().toLowerCase(Locale.ROOT).contains(queryLower)
                    && !coordinates(waypoint).contains(queryLower)) {
                continue;
            }
            visible.add(waypoint);
        }
        visible.sort(Comparator.comparing((WaypointManager.Waypoint waypoint) ->
                        !waypoint.dimension().equals(dimension))
                .thenComparing(WaypointManager.Waypoint::name, String.CASE_INSENSITIVE_ORDER));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, visible.size() - MAX_ROWS)));
    }

    private static String coordinates(WaypointManager.Waypoint waypoint) {
        return waypoint.x() + " " + waypoint.y() + " " + waypoint.z();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (UiScale.update(Minecraft.getInstance())) {
            calculateLayout();
        }
        this.mouseX = UiScale.toDesign(mouseX);
        this.mouseY = UiScale.toDesign(mouseY);
        updateCaret();
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
            graphics.nextStratum();
            drawToast(graphics);
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
        drawButton(graphics, waypointsTab, false, true);
        drawButton(graphics, hudTab, hudTab.contains(mouseX, mouseY), false);
        drawButton(graphics, settingsTab, settingsTab.contains(mouseX, mouseY), false);
        drawButton(graphics, closeButton, closeButton.contains(mouseX, mouseY), false);
        drawInput(graphics, searchBox, searchFocused);
        drawButton(graphics, dimensionButton, dimensionButton.contains(mouseX, mouseY), currentDimensionOnly);
        drawButton(graphics, overlayButton, overlayButton.contains(mouseX, mouseY), waypointManager.showOnHud());
        drawButton(graphics, addButton, addButton.contains(mouseX, mouseY), true);
        int count = Math.min(MAX_ROWS, Math.max(0, visible.size() - scroll));
        for (int index = 0; index < count; index++) {
            Box row = rowBounds[index];
            drawRow(graphics, row, row.contains(mouseX, mouseY));
            drawButton(graphics, copyBounds[index], copyBounds[index].contains(mouseX, mouseY), false);
            drawButton(graphics, deleteBounds[index], deleteBounds[index].contains(mouseX, mouseY), false);
        }
        if (createDialog || deleteDialog) {
            CarbonShapes.drawRounded(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                    0.0f, 0xAA000000);
            CarbonShapes.drawShadow(graphics, modal.x, modal.y, modal.width, modal.height,
                    12.0f, 24.0f, 10.0f, CarbonTheme.WINDOW_SHADOW);
            CarbonShapes.drawBorderedSurface(graphics, modal.x, modal.y, modal.width, modal.height,
                    12.0f, 1.0f, CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                    CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
            if (createDialog) {
                drawInput(graphics, inputBox, true);
                drawButton(graphics, cancelButton, cancelButton.contains(mouseX, mouseY), false);
                drawButton(graphics, confirmButton, confirmButton.contains(mouseX, mouseY), true);
            } else {
                drawButton(graphics, deleteCancelButton, deleteCancelButton.contains(mouseX, mouseY), false);
                drawButton(graphics, deleteConfirmButton, deleteConfirmButton.contains(mouseX, mouseY), true);
            }
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, Box row, boolean hovered) {
        CarbonShapes.drawBorderedSurface(graphics, row.x, row.y, row.width, row.height,
                8.0f, 1.0f, hovered ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER,
                CarbonTheme.BUTTON_BORDER, hovered ? CarbonTheme.CARD_HOVER_TOP : CarbonTheme.CARD_TOP,
                hovered ? CarbonTheme.CARD_HOVER_BOTTOM : CarbonTheme.CARD_BOTTOM);
    }

    private void drawButton(GuiGraphicsExtractor graphics, Box bounds, boolean hovered, boolean active) {
        int top = active ? CarbonTheme.ACCENT : hovered ? CarbonTheme.BUTTON_HOVER_TOP : CarbonTheme.BUTTON_TOP;
        int bottom = active ? CarbonTheme.ACCENT_DEEP
                : hovered ? CarbonTheme.BUTTON_HOVER_BOTTOM : CarbonTheme.BUTTON_BOTTOM;
        int border = active ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER;
        CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y, bounds.width, bounds.height,
                7.0f, 1.0f, border, border, top, bottom);
    }

    private void drawInput(GuiGraphicsExtractor graphics, Box bounds, boolean focused) {
        CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y, bounds.width, bounds.height,
                7.0f, 1.0f, focused ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.INPUT_BORDER,
                CarbonTheme.INPUT_BORDER, CarbonTheme.INPUT_TOP, CarbonTheme.INPUT_BOTTOM);
    }

    private void drawText(GuiGraphicsExtractor graphics) {
        CarbonText.drawTracked(graphics, font, "CARBON", CarbonText.Weight.BOLD, 18.0f,
                panel.x + 24.0f, panel.y + 17.0f, CarbonTheme.TEXT, CarbonText.Tracking.WORDMARK);
        CarbonText.drawTracked(graphics, font, "CLIENT", CarbonText.Weight.MEDIUM, 9.0f,
                panel.x + 122.0f, panel.y + 24.0f, CarbonTheme.TEXT_MUTED, CarbonText.Tracking.LABEL);
        drawTab(graphics, modsTab, "MODS", CarbonTheme.TEXT_MUTED);
        drawTab(graphics, waypointsTab, "WAYPOINTS", CarbonTheme.TEXT);
        drawTab(graphics, hudTab, "HUD EDITOR", CarbonTheme.TEXT_MUTED);
        drawTab(graphics, settingsTab, "SETTINGS", CarbonTheme.TEXT_MUTED);
        CarbonIcons.drawDesign(graphics, "x", closeButton.x + 9.0f, closeButton.y + 9.0f,
                16.0f, CarbonTheme.TEXT_MUTED);
        CarbonText.drawTracked(graphics, "WAYPOINTS", CarbonText.Weight.BOLD, 14.0f,
                panel.x + 232.0f, panel.y + 75.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
        CarbonIcons.drawDesign(graphics, "search", searchBox.x + 10.0f, searchBox.y + 9.0f,
                16.0f, searchFocused ? CarbonTheme.ACCENT : CarbonTheme.TEXT_MUTED);
        String shownQuery = query.isEmpty() && !searchFocused ? "Search names, dimensions, coordinates" : query;
        if (searchFocused && caretVisible) {
            shownQuery += "|";
        }
        CarbonText.draw(graphics, font, shownQuery, CarbonText.Weight.REGULAR, 10.0f,
                searchBox.x + 34.0f, searchBox.y + 11.0f,
                query.isEmpty() && !searchFocused ? CarbonTheme.TEXT_MUTED : CarbonTheme.TEXT, false);
        CarbonText.centeredTracked(graphics, font, currentDimensionOnly ? "THIS DIMENSION" : "ALL DIMENSIONS",
                CarbonText.Weight.SEMIBOLD, 7.0f, dimensionButton.x + dimensionButton.width * 0.5f,
                dimensionButton.y + 12.0f, currentDimensionOnly ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED,
                CarbonText.Tracking.LABEL);
        CarbonText.centeredTracked(graphics, font, waypointManager.showOnHud() ? "HUD ON" : "HUD OFF",
                CarbonText.Weight.SEMIBOLD, 8.0f, overlayButton.x + overlayButton.width * 0.5f,
                overlayButton.y + 12.0f, waypointManager.showOnHud() ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED,
                CarbonText.Tracking.LABEL);
        CarbonIcons.drawDesign(graphics, "plus", addButton.x + 10.0f, addButton.y + 9.0f,
                16.0f, CarbonTheme.TEXT);
        CarbonText.drawTracked(graphics, "ADD HERE", CarbonText.Weight.SEMIBOLD, 8.0f,
                addButton.x + 34.0f, addButton.y + 13.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);

        int count = Math.min(MAX_ROWS, Math.max(0, visible.size() - scroll));
        for (int index = 0; index < count; index++) {
            WaypointManager.Waypoint waypoint = visible.get(index + scroll);
            Box row = rowBounds[index];
            CarbonIcons.drawDesign(graphics, "map-pin", row.x + 12.0f, row.y + 17.0f,
                    18.0f, CarbonTheme.ACCENT);
            CarbonText.draw(graphics, font, fit(waypoint.name(), CarbonText.Weight.SEMIBOLD, 12.0f, 260),
                    CarbonText.Weight.SEMIBOLD, 12.0f, row.x + 40.0f, row.y + 8.0f,
                    CarbonTheme.TEXT, false);
            String dimension = waypoint.dimension().equals(waypointManager.currentDimension())
                    ? "CURRENT DIMENSION" : waypoint.dimension();
            CarbonText.draw(graphics, font, fit(coordinates(waypoint) + "   ·   " + dimension,
                            CarbonText.Weight.REGULAR, 9.0f, 600),
                    CarbonText.Weight.REGULAR, 9.0f, row.x + 40.0f, row.y + 31.0f,
                    CarbonTheme.TEXT_MUTED, false);
            CarbonIcons.drawDesign(graphics, "copy", copyBounds[index].x + 7.0f,
                    copyBounds[index].y + 7.0f, 16.0f, CarbonTheme.TEXT_MUTED);
            CarbonIcons.drawDesign(graphics, "trash", deleteBounds[index].x + 7.0f,
                    deleteBounds[index].y + 7.0f, 16.0f, CarbonTheme.TEXT_MUTED);
        }
        if (visible.isEmpty()) {
            CarbonText.centered(graphics, font,
                    waypointManager.waypoints().isEmpty() ? "No waypoints yet." : "No waypoints match this filter.",
                    CarbonText.Weight.SEMIBOLD, 14.0f, panel.x + 610.0f, panel.y + 300.0f,
                    CarbonTheme.TEXT, false);
            CarbonText.centered(graphics, font, "Add one here to save your current position.",
                    CarbonText.Weight.REGULAR, 10.0f, panel.x + 610.0f, panel.y + 326.0f,
                    CarbonTheme.TEXT_MUTED, false);
        }
        CarbonText.draw(graphics, font, visible.size() + " saved waypoint" + (visible.size() == 1 ? "" : "s"),
                CarbonText.Weight.MEDIUM, 9.0f, panel.x + 232.0f, panel.y + PANEL_HEIGHT - 24.0f,
                CarbonTheme.TEXT_MUTED, false);
        if (createDialog) {
            CarbonText.drawTracked(graphics, "ADD WAYPOINT", CarbonText.Weight.BOLD, 12.0f,
                    modal.x + 24.0f, modal.y + 22.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
            CarbonText.draw(graphics, font, "Saved at your current block position and dimension.",
                    CarbonText.Weight.REGULAR, 9.0f, modal.x + 24.0f, modal.y + 48.0f,
                    CarbonTheme.TEXT_MUTED, false);
            String name = nameDraft.isEmpty() ? "Waypoint name" : nameDraft;
            if (!nameDraft.isEmpty() && caretVisible) {
                name += "|";
            }
            CarbonText.draw(graphics, font, name, CarbonText.Weight.REGULAR, 11.0f,
                    inputBox.x + 10.0f, inputBox.y + 11.0f,
                    nameDraft.isEmpty() ? CarbonTheme.TEXT_MUTED : CarbonTheme.TEXT, false);
            drawModalButtonLabel(graphics, cancelButton, "CANCEL", CarbonTheme.TEXT);
            drawModalButtonLabel(graphics, confirmButton, "SAVE", CarbonTheme.TEXT);
        }
        if (deleteDialog) {
            CarbonText.drawTracked(graphics, "DELETE WAYPOINT?", CarbonText.Weight.BOLD, 12.0f,
                    modal.x + 24.0f, modal.y + 34.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
            CarbonText.draw(graphics, font, "This only removes the local marker.",
                    CarbonText.Weight.REGULAR, 10.0f, modal.x + 24.0f, modal.y + 70.0f,
                    CarbonTheme.TEXT_MUTED, false);
            drawModalButtonLabel(graphics, deleteCancelButton, "CANCEL", CarbonTheme.TEXT);
            drawModalButtonLabel(graphics, deleteConfirmButton, "DELETE", CarbonTheme.TEXT);
        }
    }

    private void drawModalButtonLabel(GuiGraphicsExtractor graphics, Box box, String label, int color) {
        CarbonText.centeredTracked(graphics, font, label, CarbonText.Weight.SEMIBOLD, 9.0f,
                box.x + box.width * 0.5f, box.y + 12.0f, color, CarbonText.Tracking.LABEL);
    }

    private void drawTab(GuiGraphicsExtractor graphics, Box box, String label, int color) {
        CarbonText.centeredTracked(graphics, font, label, CarbonText.Weight.SEMIBOLD, 8.0f,
                box.x + box.width * 0.5f, box.y + 12.0f, color, CarbonText.Tracking.LABEL);
    }

    private void drawToast(GuiGraphicsExtractor graphics) {
        if (toast.isEmpty() || System.nanoTime() > toastUntil) {
            return;
        }
        float width = Math.max(180.0f, CarbonText.width(font, toast, CarbonText.Weight.MEDIUM, 10.0f) + 30.0f);
        float x = panel.x + (panel.width - width) * 0.5f;
        float y = panel.y + panel.height - 52.0f;
        CarbonShapes.drawShadow(graphics, x, y, width, 32.0f, 8.0f, 12.0f, 4.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, x, y, width, 32.0f, 8.0f, 1.0f,
                CarbonTheme.CARD_ACTIVE_BORDER, CarbonTheme.CARD_ACTIVE_BORDER,
                CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
        CarbonText.centered(graphics, font, toast, CarbonText.Weight.MEDIUM, 10.0f,
                x + width * 0.5f, y + 10.0f, CarbonTheme.TEXT, false);
    }

    private void updateCaret() {
        long now = System.nanoTime();
        if (now - lastCaretNanos >= 500_000_000L) {
            caretVisible = !caretVisible;
            lastCaretNanos = now;
        }
    }

    private void setToast(String text) {
        toast = text;
        toastUntil = System.nanoTime() + 1_800_000_000L;
    }

    private String fit(String text, CarbonText.Weight weight, float size, float width) {
        if (CarbonText.width(font, text, weight, size) <= width) {
            return text;
        }
        int end = text.length();
        while (end > 0 && CarbonText.width(font, text.substring(0, end) + "…", weight, size) > width) {
            end--;
        }
        return end <= 0 ? "…" : text.substring(0, end).stripTrailing() + "…";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        UiScale.update(Minecraft.getInstance());
        float x = UiScale.toDesign(event.x());
        float y = UiScale.toDesign(event.y());
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        if (createDialog) {
            if (confirmButton.contains(x, y)) {
                createWaypoint();
                return true;
            }
            if (cancelButton.contains(x, y) || !modal.contains(x, y)) {
                createDialog = false;
                nameDraft = "";
                return true;
            }
            return true;
        }
        if (deleteDialog) {
            if (deleteConfirmButton.contains(x, y)) {
                waypointManager.delete(pendingDeleteId);
                pendingDeleteId = null;
                deleteDialog = false;
                rebuildVisible();
                setToast("Waypoint removed.");
                return true;
            }
            if (deleteCancelButton.contains(x, y) || !modal.contains(x, y)) {
                pendingDeleteId = null;
                deleteDialog = false;
                return true;
            }
            return true;
        }
        if (modsTab.contains(x, y)) {
            CarbonUI.openMods();
            return true;
        }
        if (hudTab.contains(x, y)) {
            CarbonUI.openHudEditor();
            return true;
        }
        if (settingsTab.contains(x, y)) {
            CarbonUI.openSettings();
            return true;
        }
        if (closeButton.contains(x, y)) {
            onClose();
            return true;
        }
        if (searchBox.contains(x, y)) {
            searchFocused = true;
            return true;
        }
        if (dimensionButton.contains(x, y)) {
            currentDimensionOnly = !currentDimensionOnly;
            scroll = 0;
            rebuildVisible();
            return true;
        }
        if (overlayButton.contains(x, y)) {
            waypointManager.setShowOnHud(!waypointManager.showOnHud());
            return true;
        }
        if (addButton.contains(x, y)) {
            if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) {
                setToast("Join a world before adding a waypoint.");
            } else {
                createDialog = true;
                nameDraft = "";
            }
            return true;
        }
        int count = Math.min(MAX_ROWS, Math.max(0, visible.size() - scroll));
        for (int index = 0; index < count; index++) {
            WaypointManager.Waypoint waypoint = visible.get(index + scroll);
            if (copyBounds[index].contains(x, y)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(
                        waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z());
                setToast("Waypoint coordinates copied.");
                return true;
            }
            if (deleteBounds[index].contains(x, y)) {
                pendingDeleteId = waypoint.id();
                deleteDialog = true;
                return true;
            }
        }
        searchFocused = false;
        return true;
    }

    private void createWaypoint() {
        WaypointManager.Waypoint waypoint = waypointManager.createAtPlayer(nameDraft);
        if (waypoint == null) {
            setToast(nameDraft.isBlank() ? "Enter a waypoint name." : "A world is not available.");
            return;
        }
        createDialog = false;
        nameDraft = "";
        scroll = 0;
        rebuildVisible();
        setToast("Saved " + waypoint.name() + " at " + coordinates(waypoint) + ".");
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (panel.contains(UiScale.toDesign(mouseX), UiScale.toDesign(mouseY))) {
            scroll = Math.max(0, Math.min(Math.max(0, visible.size() - MAX_ROWS),
                    scroll + (verticalAmount < 0.0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (createDialog || deleteDialog) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                createDialog = false;
                deleteDialog = false;
                nameDraft = "";
                pendingDeleteId = null;
                return true;
            }
            if (createDialog && key == GLFW.GLFW_KEY_ENTER) {
                createWaypoint();
                return true;
            }
            if (createDialog && key == GLFW.GLFW_KEY_BACKSPACE && !nameDraft.isEmpty()) {
                nameDraft = nameDraft.substring(0, nameDraft.length() - 1);
                return true;
            }
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                int end = query.offsetByCodePoints(query.length(), -1);
                query = query.substring(0, end);
                rebuildVisible();
                return true;
            }
            if (event.isSelectAll()) {
                query = "";
                rebuildVisible();
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_SLASH || (key == GLFW.GLFW_KEY_F && event.hasControlDown())) {
            searchFocused = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_N) {
            if (Minecraft.getInstance().player != null) {
                createDialog = true;
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            scroll = Math.min(Math.max(0, visible.size() - MAX_ROWS), scroll + 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String input = event.codepointAsString();
        if (createDialog && !input.isEmpty() && !Character.isISOControl(event.codepoint())) {
            if (nameDraft.length() + input.length() <= MAX_NAME_LENGTH) {
                nameDraft += input;
            }
            return true;
        }
        if (searchFocused && !input.isEmpty() && !Character.isISOControl(event.codepoint())) {
            if (query.length() + input.length() <= 64) {
                query += input;
                rebuildVisible();
            }
            return true;
        }
        return super.charTyped(event);
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
