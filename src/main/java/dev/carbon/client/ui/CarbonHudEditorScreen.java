package dev.carbon.client.ui;

import dev.carbon.client.core.config.ConfigManager;
import dev.carbon.client.core.event.HudRenderEvent;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.hud.HudManager;
import dev.carbon.client.hud.HudModule;
import dev.carbon.client.ui.render.CarbonShapes;
import dev.carbon.client.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Part 3 HUD editor: live previews, drag-to-place, scale controls, reset, and enable state. */
public final class CarbonHudEditorScreen extends CarbonScreen {
    private static final float BAR_WIDTH = 960.0f;
    private static final float BAR_HEIGHT = 124.0f;
    private static final int WHITE_OUTLINE = 0xCCF4F6F3;

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final HudManager hudManager;
    private final WaypointManager waypointManager;
    private final CarbonUiState uiState;
    private final List<HudModule> hudModules = new ArrayList<>();
    private final HudRenderEvent editorEvent = new HudRenderEvent();
    private final Box bar = new Box();
    private final Box exitButton = new Box();
    private final Box[] moduleRows;
    private final Box toggleButton = new Box();
    private final Box scaleDownButton = new Box();
    private final Box scaleUpButton = new Box();
    private final Box resetButton = new Box();

    private int selectedIndex;
    private int draggingIndex = -1;
    private int dragOffsetX;
    private int dragOffsetY;
    private int screenWidth;
    private int screenHeight;
    private float mouseDesignX;
    private float mouseDesignY;

    public CarbonHudEditorScreen(ModuleManager modules, ConfigManager configManager, HudManager hudManager,
                                 WaypointManager waypointManager, CarbonUiState uiState) {
        super(Component.literal("Carbon HUD Editor"), null);
        this.modules = Objects.requireNonNull(modules, "modules");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.hudManager = Objects.requireNonNull(hudManager, "hudManager");
        this.waypointManager = Objects.requireNonNull(waypointManager, "waypointManager");
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        CarbonText.setTypeface(uiState.typeface());
        for (dev.carbon.client.core.module.Module module : modules.modules()) {
            if (module instanceof HudModule hudModule) {
                hudModules.add(hudModule);
            }
        }
        moduleRows = new Box[Math.max(1, hudModules.size())];
        for (int index = 0; index < moduleRows.length; index++) {
            moduleRows[index] = new Box();
        }
    }

    @Override
    protected void init() {
        super.init();
        UiScale.update(Minecraft.getInstance());
        CarbonIcons.load();
        calculateLayout();
        hudManager.setEditorPreviewActive(true);
    }

    private void calculateLayout() {
        float x = (UiScale.designWidth() - BAR_WIDTH) * 0.5f;
        float y = UiScale.designHeight() - BAR_HEIGHT - 24.0f;
        bar.set(x, y, BAR_WIDTH, BAR_HEIGHT);
        exitButton.set(x + BAR_WIDTH - 112.0f, y + 12.0f, 92.0f, 30.0f);
        float itemX = x + 16.0f;
        for (int index = 0; index < moduleRows.length; index++) {
            moduleRows[index].set(itemX, y + 52.0f, 120.0f, 30.0f);
            itemX += 128.0f;
        }
        float controlsX = x + BAR_WIDTH - 404.0f;
        toggleButton.set(controlsX, y + 52.0f, 94.0f, 30.0f);
        scaleDownButton.set(controlsX + 104.0f, y + 52.0f, 34.0f, 30.0f);
        scaleUpButton.set(controlsX + 144.0f, y + 52.0f, 34.0f, 30.0f);
        resetButton.set(controlsX + 190.0f, y + 52.0f, 112.0f, 30.0f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (UiScale.update(Minecraft.getInstance())) {
            calculateLayout();
        }
        mouseDesignX = UiScale.toDesign(mouseX);
        mouseDesignY = UiScale.toDesign(mouseY);
        screenWidth = graphics.guiWidth();
        screenHeight = graphics.guiHeight();
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
            CarbonShapes.drawRounded(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                    0.0f, 0x75000000);
            CarbonShapes.drawVignette(graphics, 0.0f, 0.0f,
                    UiScale.designWidth(), UiScale.designHeight(), CarbonTheme.VIGNETTE);
        } finally {
            UiScale.popRendererScale(graphics);
        }
        graphics.nextStratum();

        editorEvent.prepareForEditor(graphics);
        for (HudModule module : hudModules) {
            module.refreshEditorPreview();
            module.extractEditorPreview(editorEvent);
        }
        graphics.nextStratum();

        UiScale.pushRendererScale(graphics);
        try {
            drawSelectionOutlines(graphics);
            drawBar(graphics);
            graphics.nextStratum();
            drawText(graphics);
        } finally {
            UiScale.popRendererScale(graphics);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSelectionOutlines(GuiGraphicsExtractor graphics) {
        for (int index = 0; index < hudModules.size(); index++) {
            HudModule module = hudModules.get(index);
            int x = module.screenX(screenWidth);
            int y = module.screenY(screenHeight);
            float designX = UiScale.toDesign(x);
            float designY = UiScale.toDesign(y);
            float designWidth = module.renderedWidth() / UiScale.rendererScale();
            float designHeight = module.renderedHeight() / UiScale.rendererScale();
            CarbonShapes.drawOutline(graphics, designX - 4.0f, designY - 4.0f,
                    designWidth + 8.0f, designHeight + 8.0f, index == selectedIndex ? 2.0f : 1.0f,
                    index == selectedIndex ? CarbonTheme.ACCENT_HIGHLIGHT : WHITE_OUTLINE);
        }
    }

    private void drawBar(GuiGraphicsExtractor graphics) {
        CarbonShapes.drawShadow(graphics, bar.x, bar.y, bar.width, bar.height,
                12.0f, 24.0f, 8.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, bar.x, bar.y, bar.width, bar.height,
                12.0f, 1.0f, CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.WINDOW_TOP, CarbonTheme.WINDOW_BOTTOM);
        CarbonShapes.drawTopHighlight(graphics, bar.x + 1.0f, bar.y + 1.0f,
                bar.width - 2.0f, 12.0f, CarbonTheme.WINDOW_HIGHLIGHT);
        drawButton(graphics, exitButton, exitButton.contains(mouseDesignX, mouseDesignY), false);
        for (int index = 0; index < hudModules.size(); index++) {
            Box row = moduleRows[index];
            CarbonShapes.drawBorderedSurface(graphics, row.x, row.y, row.width, row.height,
                    7.0f, 1.0f,
                    index == selectedIndex ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER,
                    CarbonTheme.BUTTON_BORDER,
                    index == selectedIndex ? CarbonTheme.CARD_ACTIVE_WASH_TOP : CarbonTheme.BUTTON_TOP,
                    index == selectedIndex ? CarbonTheme.CARD_ACTIVE_WASH_BOTTOM : CarbonTheme.BUTTON_BOTTOM);
        }
        if (!hudModules.isEmpty()) {
            drawButton(graphics, toggleButton, toggleButton.contains(mouseDesignX, mouseDesignY),
                    hudModules.get(selectedIndex).enabled());
            drawButton(graphics, scaleDownButton, scaleDownButton.contains(mouseDesignX, mouseDesignY), false);
            drawButton(graphics, scaleUpButton, scaleUpButton.contains(mouseDesignX, mouseDesignY), false);
            drawButton(graphics, resetButton, resetButton.contains(mouseDesignX, mouseDesignY), false);
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, Box box, boolean hovered, boolean active) {
        int top = active ? CarbonTheme.ACCENT : hovered ? CarbonTheme.BUTTON_HOVER_TOP : CarbonTheme.BUTTON_TOP;
        int bottom = active ? CarbonTheme.ACCENT_DEEP
                : hovered ? CarbonTheme.BUTTON_HOVER_BOTTOM : CarbonTheme.BUTTON_BOTTOM;
        int border = active ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER;
        CarbonShapes.drawBorderedSurface(graphics, box.x, box.y, box.width, box.height,
                7.0f, 1.0f, border, border, top, bottom);
    }

    private void drawText(GuiGraphicsExtractor graphics) {
        CarbonText.drawTracked(graphics, font, "CARBON HUD EDITOR", CarbonText.Weight.BOLD, 12.0f,
                bar.x + 18.0f, bar.y + 16.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
        CarbonText.draw(graphics, font, "Drag a preview to move it · position and scale save with the active profile",
                CarbonText.Weight.REGULAR, 9.0f, bar.x + 198.0f, bar.y + 18.0f,
                CarbonTheme.TEXT_MUTED, false);
        CarbonText.centeredTracked(graphics, font, "BACK TO MODS", CarbonText.Weight.SEMIBOLD, 8.0f,
                exitButton.x + exitButton.width * 0.5f, exitButton.y + 11.0f,
                CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
        for (int index = 0; index < hudModules.size(); index++) {
            HudModule module = hudModules.get(index);
            Box row = moduleRows[index];
            CarbonText.draw(graphics, font, fit(module.name(), CarbonText.Weight.SEMIBOLD, 8.0f, 66),
                    CarbonText.Weight.SEMIBOLD, 8.0f, row.x + 8.0f, row.y + 11.0f,
                    index == selectedIndex ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED, false);
            CarbonText.draw(graphics, font, module.enabled() ? "ON" : "OFF",
                    CarbonText.Weight.BOLD, 8.0f, row.x + row.width - 28.0f, row.y + 11.0f,
                    module.enabled() ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT_MUTED, false);
        }
        if (!hudModules.isEmpty()) {
            HudModule selected = hudModules.get(selectedIndex);
            CarbonText.centeredTracked(graphics, font, selected.enabled() ? "ON" : "OFF",
                    CarbonText.Weight.BOLD, 8.0f, toggleButton.x + toggleButton.width * 0.5f,
                    toggleButton.y + 11.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
            CarbonText.centeredTracked(graphics, font, "−", CarbonText.Weight.BOLD, 13.0f,
                    scaleDownButton.x + scaleDownButton.width * 0.5f, scaleDownButton.y + 8.0f,
                    CarbonTheme.TEXT, CarbonText.Tracking.NONE);
            CarbonText.centeredTracked(graphics, font, "+", CarbonText.Weight.BOLD, 13.0f,
                    scaleUpButton.x + scaleUpButton.width * 0.5f, scaleUpButton.y + 8.0f,
                    CarbonTheme.TEXT, CarbonText.Tracking.NONE);
            CarbonText.centeredTracked(graphics, font, "RESET", CarbonText.Weight.SEMIBOLD, 8.0f,
                    resetButton.x + resetButton.width * 0.5f, resetButton.y + 11.0f,
                    CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
            double scale = selected.hudScale();
            int px = selected.screenX(screenWidth);
            int py = selected.screenY(screenHeight);
            CarbonText.draw(graphics, font,
                    "X " + px + "   Y " + py + "   SCALE " + String.format(java.util.Locale.ROOT, "%.2f", scale),
                    CarbonText.Weight.MEDIUM, 8.0f, bar.x + 18.0f, bar.y + 92.0f,
                    CarbonTheme.TEXT_MUTED, false);
        } else {
            CarbonText.draw(graphics, font, "No HUD modules are registered.", CarbonText.Weight.MEDIUM, 11.0f,
                    bar.x + 18.0f, bar.y + 72.0f, CarbonTheme.TEXT, false);
        }
    }

    private String fit(String value, CarbonText.Weight weight, float size, float maxWidth) {
        if (CarbonText.width(font, value, weight, size) <= maxWidth) {
            return value;
        }
        int end = value.length();
        while (end > 0 && CarbonText.width(font, value.substring(0, end) + "…", weight, size) > maxWidth) {
            end--;
        }
        return end <= 0 ? "…" : value.substring(0, end).stripTrailing() + "…";
    }

    private void select(int index) {
        if (hudModules.isEmpty()) {
            return;
        }
        selectedIndex = Math.max(0, Math.min(hudModules.size() - 1, index));
    }

    private void changeScale(double delta) {
        if (!hudModules.isEmpty()) {
            HudModule module = hudModules.get(selectedIndex);
            module.setEditorScale(module.hudScale() + delta);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        UiScale.update(Minecraft.getInstance());
        float x = UiScale.toDesign(event.x());
        float y = UiScale.toDesign(event.y());
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        if (exitButton.contains(x, y)) {
            onClose();
            return true;
        }
        for (int index = 0; index < hudModules.size(); index++) {
            if (moduleRows[index].contains(x, y)) {
                select(index);
                return true;
            }
        }
        if (!hudModules.isEmpty() && bar.contains(x, y)) {
            if (toggleButton.contains(x, y)) {
                hudModules.get(selectedIndex).toggle();
            } else if (scaleDownButton.contains(x, y)) {
                changeScale(-0.05);
            } else if (scaleUpButton.contains(x, y)) {
                changeScale(0.05);
            } else if (resetButton.contains(x, y)) {
                hudModules.get(selectedIndex).resetLayout();
            }
            return true;
        }
        int guiX = (int) Math.round(event.x());
        int guiY = (int) Math.round(event.y());
        for (int index = hudModules.size() - 1; index >= 0; index--) {
            HudModule module = hudModules.get(index);
            int moduleX = module.screenX(screenWidth);
            int moduleY = module.screenY(screenHeight);
            if (guiX >= moduleX && guiY >= moduleY
                    && guiX <= moduleX + module.renderedWidth()
                    && guiY <= moduleY + module.renderedHeight()) {
                select(index);
                draggingIndex = index;
                dragOffsetX = guiX - moduleX;
                dragOffsetY = guiY - moduleY;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingIndex >= 0 && draggingIndex < hudModules.size()) {
            int guiX = (int) Math.round(event.x());
            int guiY = (int) Math.round(event.y());
            hudModules.get(draggingIndex).setEditorPosition(guiX - dragOffsetX, guiY - dragOffsetY,
                    screenWidth, screenHeight);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingIndex = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && !hudModules.isEmpty()) {
            select((selectedIndex + 1) % hudModules.size());
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && !hudModules.isEmpty()) {
            hudModules.get(selectedIndex).toggle();
            return true;
        }
        if (key == GLFW.GLFW_KEY_R && !hudModules.isEmpty()) {
            hudModules.get(selectedIndex).resetLayout();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) && !hudModules.isEmpty()) {
            changeScale(0.05);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) && !hudModules.isEmpty()) {
            changeScale(-0.05);
            return true;
        }
        if (!hudModules.isEmpty() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            HudModule module = hudModules.get(selectedIndex);
            int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
            int step = event.hasShiftDown() ? 10 : 1;
            module.setEditorPosition(module.screenX(screenWidth) + dx * step,
                    module.screenY(screenHeight) + dy * step, screenWidth, screenHeight);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        hudManager.setEditorPreviewActive(false);
        CarbonUI.openMods();
    }

    @Override
    public void removed() {
        hudManager.setEditorPreviewActive(false);
        super.removed();
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
