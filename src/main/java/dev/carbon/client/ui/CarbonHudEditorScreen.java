package dev.carbon.client.ui;

import dev.carbon.client.core.event.HudRenderEvent;
import dev.carbon.client.core.module.Module;
import dev.carbon.client.hud.HudModule;
import dev.carbon.client.ui.render.CarbonShapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** In-game HUD layout editor: widgets stay live over the world and save through module settings. */
public final class CarbonHudEditorScreen extends CarbonScreen {
    private static final int TOOLBAR_MARGIN = 12;
    private static final int SNAP_DISTANCE = 7;
    private static final int RESIZE_HANDLE_SIZE = 12;

    private final List<HudModule> hudModules = new ArrayList<>();
    private final List<ModuleBounds> moduleBounds = new ArrayList<>();
    private final HudRenderEvent previewEvent = new HudRenderEvent();

    private HudModule selectedModule;
    private HudModule draggingModule;
    private DragMode dragMode = DragMode.NONE;
    private int dragMouseX;
    private int dragMouseY;
    private int dragOriginX;
    private int dragOriginY;
    private int dragWidth;
    private int dragHeight;
    private float dragScale;
    private int snapGuideX = -1;
    private int snapGuideY = -1;

    private Rect toolbar = Rect.EMPTY;
    private Rect backButton = Rect.EMPTY;
    private Rect decreaseButton = Rect.EMPTY;
    private Rect increaseButton = Rect.EMPTY;
    private Rect toggleButton = Rect.EMPTY;
    private Rect resetButton = Rect.EMPTY;
    private boolean compactToolbar;

    public CarbonHudEditorScreen(List<Module> modules, Screen parent) {
        super(Component.literal("Carbon HUD Editor"), parent);
        for (Module module : modules) {
            if (module instanceof HudModule hudModule) {
                hudModules.add(hudModule);
            }
        }
        selectedModule = hudModules.stream().findFirst().orElse(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        calculateToolbar();
        previewEvent.prepareForEditor(graphics);
        moduleBounds.clear();

        for (HudModule module : hudModules) {
            module.refreshEditorPreview();
            int x = module.screenX(width);
            int y = module.screenY(height);
            int moduleWidth = module.renderedWidth();
            int moduleHeight = module.renderedHeight();
            moduleBounds.add(new ModuleBounds(module, new Rect(x, y, moduleWidth, moduleHeight)));
            module.extractEditorPreview(previewEvent);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();
        extractSnapGuides(graphics);
        extractWidgetOutlines(graphics, mouseX, mouseY);
        extractToolbarSurfaces(graphics);

        graphics.nextStratum();
        extractWidgetLabels(graphics);
        extractToolbarText(graphics);
    }

    private void calculateToolbar() {
        int toolbarWidth = Math.max(1, Math.min(760, width - TOOLBAR_MARGIN * 2));
        compactToolbar = toolbarWidth < 560;
        int toolbarHeight = compactToolbar ? 112 : 84;
        toolbar = new Rect((width - toolbarWidth) / 2, TOOLBAR_MARGIN, toolbarWidth, toolbarHeight);
        backButton = new Rect(toolbar.right() - (compactToolbar ? 64 : 80),
                toolbar.y() + 9, compactToolbar ? 52 : 66, 24);

        if (compactToolbar) {
            int buttonY = toolbar.bottom() - 28;
            int buttonX = toolbar.x() + 12;
            int available = Math.max(1, toolbar.width() - 24);
            float factor = Math.min(1.0f, available / 183.0f);
            int narrowWidth = Math.max(18, Math.round(26 * factor));
            int actionWidth = Math.max(40, Math.round(58 * factor));
            int gap = Math.max(2, Math.round(5 * factor));
            decreaseButton = new Rect(buttonX, buttonY, narrowWidth, 21);
            increaseButton = new Rect(decreaseButton.right() + gap, buttonY, narrowWidth, 21);
            toggleButton = new Rect(increaseButton.right() + gap, buttonY, actionWidth, 21);
            resetButton = new Rect(toggleButton.right() + gap, buttonY, actionWidth, 21);
        } else {
            int buttonY = toolbar.y() + 53;
            resetButton = new Rect(toolbar.right() - 88, buttonY, 74, 22);
            toggleButton = new Rect(resetButton.x() - 82, buttonY, 74, 22);
            increaseButton = new Rect(toggleButton.x() - 36, buttonY, 30, 22);
            decreaseButton = new Rect(increaseButton.x() - 35, buttonY, 29, 22);
        }
    }

    private void extractSnapGuides(GuiGraphicsExtractor graphics) {
        if (dragMode == DragMode.MOVE && snapGuideX >= 0) {
            graphics.fill(snapGuideX, 0, snapGuideX + 1, height, CarbonTheme.ACCENT_MUTED);
        }
        if (dragMode == DragMode.MOVE && snapGuideY >= 0) {
            graphics.fill(0, snapGuideY, width, snapGuideY + 1, CarbonTheme.ACCENT_MUTED);
        }
    }

    private void extractWidgetOutlines(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (ModuleBounds hit : moduleBounds) {
            Rect bounds = hit.bounds();
            boolean selected = hit.module() == selectedModule;
            boolean hovered = bounds.contains(mouseX, mouseY);
            int color = selected ? CarbonTheme.ACCENT
                    : hovered ? CarbonTheme.ACCENT_DEEP : CarbonTheme.ACCENT_MUTED;
            int thickness = selected ? 2 : 1;
            drawRoundedOutline(graphics, bounds, thickness, color);

            if (selected) {
                Rect handle = resizeHandle(bounds);
                drawRounded(graphics, handle, 4, CarbonTheme.ACCENT);
                drawRounded(graphics, new Rect(handle.x() + 3, handle.y() + 3,
                        Math.max(1, handle.width() - 6), Math.max(1, handle.height() - 6)),
                        2, CarbonTheme.PANEL);
            }
        }
    }

    private void extractWidgetLabels(GuiGraphicsExtractor graphics) {
        for (ModuleBounds hit : moduleBounds) {
            HudModule module = hit.module();
            Rect bounds = hit.bounds();
            String label = module.name() + (module.enabled() ? "" : "  ·  OFF");
            int labelWidth = Math.max(46, (int) Math.ceil(
                    CarbonText.widthUi(font, label, CarbonText.Weight.MEDIUM, 8)) + 16);
            int labelHeight = 19;
            int labelX = Math.max(4, Math.min(width - labelWidth - 4, bounds.x()));
            int aboveY = bounds.y() - labelHeight - 3;
            int labelY = aboveY >= 4 ? aboveY : Math.min(height - labelHeight - 4, bounds.bottom() + 3);
            Rect labelBounds = new Rect(labelX, labelY, labelWidth, labelHeight);
            drawPanel(graphics, labelBounds, CarbonTheme.PANEL_RAISED,
                    module == selectedModule ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, 7);
            CarbonText.drawUi(graphics, font, label, CarbonText.Weight.MEDIUM, 8,
                    labelBounds.x() + 8, labelBounds.y() + 5,
                    module == selectedModule ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED, false);
        }
    }

    private void extractToolbarSurfaces(GuiGraphicsExtractor graphics) {
        drawPanel(graphics, toolbar, CarbonTheme.FRAME, CarbonTheme.BORDER, 13);
        drawButton(graphics, backButton, CarbonTheme.PANEL_RAISED);
        drawButton(graphics, decreaseButton, CarbonTheme.PANEL_HOVER);
        drawButton(graphics, increaseButton, CarbonTheme.PANEL_HOVER);
        if (selectedModule != null) {
            drawButton(graphics, toggleButton,
                    selectedModule.enabled() ? CarbonTheme.PANEL_HOVER : CarbonTheme.CARD_HOVER);
        } else {
            drawButton(graphics, toggleButton, CarbonTheme.PANEL);
        }
        drawButton(graphics, resetButton, CarbonTheme.PANEL_HOVER);
    }

    private void extractToolbarText(GuiGraphicsExtractor graphics) {
        CarbonText.drawUi(graphics, font, "CARBON  /  HUD EDITOR", CarbonText.Weight.BOLD, 11,
                toolbar.x() + 14, toolbar.y() + 11, CarbonTheme.TEXT, false);
        CarbonText.drawUi(graphics, font, "Drag to move  ·  corner to resize  ·  world stays live",
                CarbonText.Weight.REGULAR, 8, toolbar.x() + 14, toolbar.y() + 33,
                CarbonTheme.TEXT_MUTED, false);
        CarbonText.centeredUi(graphics, font, "MENU", CarbonText.Weight.SEMIBOLD, 8,
                backButton.x() + backButton.width() / 2.0f, backButton.y() + 7,
                CarbonTheme.TEXT, false);

        String selected = selectedModule == null ? "No HUD widgets registered" :
                selectedModule.name() + "  ·  " + Math.round(selectedModule.hudScale() * 100.0f) + "%";
        int textLimit = compactToolbar
                ? toolbar.width() - 26
                : Math.max(1, decreaseButton.x() - toolbar.x() - 28);
        selected = fit(selected, textLimit);
        CarbonText.drawUi(graphics, font, selected, CarbonText.Weight.MEDIUM, 8,
                toolbar.x() + 14, toolbar.y() + (compactToolbar ? 57 : 60),
                CarbonTheme.TEXT, false);

        CarbonText.centeredUi(graphics, font, "−", CarbonText.Weight.SEMIBOLD, 10,
                decreaseButton.x() + decreaseButton.width() / 2.0f, decreaseButton.y() + 5,
                CarbonTheme.TEXT, false);
        CarbonText.centeredUi(graphics, font, "+", CarbonText.Weight.SEMIBOLD, 10,
                increaseButton.x() + increaseButton.width() / 2.0f, increaseButton.y() + 5,
                CarbonTheme.TEXT, false);
        CarbonText.centeredUi(graphics, font, selectedModule == null ? "—"
                        : selectedModule.enabled() ? "LIVE" : "ENABLE",
                CarbonText.Weight.SEMIBOLD, 7,
                toggleButton.x() + toggleButton.width() / 2.0f, toggleButton.y() + 7,
                selectedModule == null ? CarbonTheme.TEXT_DIM : CarbonTheme.TEXT, false);
        CarbonText.centeredUi(graphics, font, "RESET", CarbonText.Weight.SEMIBOLD, 7,
                resetButton.x() + resetButton.width() / 2.0f, resetButton.y() + 7,
                CarbonTheme.TEXT_MUTED, false);
    }

    private void drawRoundedOutline(GuiGraphicsExtractor graphics, Rect bounds, int thickness, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int inset = thickness == 1 ? 1 : 2;
        int bar = Math.max(1, thickness);
        CarbonShapes.drawRounded(graphics, bounds.x() - inset, bounds.y() - inset,
                bounds.width() + inset * 2, bar, 1, color);
        CarbonShapes.drawRounded(graphics, bounds.x() - inset, bounds.bottom() + inset - bar,
                bounds.width() + inset * 2, bar, 1, color);
        CarbonShapes.drawRounded(graphics, bounds.x() - inset, bounds.y(),
                bar, bounds.height(), 1, color);
        CarbonShapes.drawRounded(graphics, bounds.right() + inset - bar, bounds.y(),
                bar, bounds.height(), 1, color);
    }

    private void drawButton(GuiGraphicsExtractor graphics, Rect rect, int fill) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        drawPanel(graphics, rect, fill, CarbonTheme.BORDER, Math.min(7, rect.height() / 2));
    }

    private void drawPanel(GuiGraphicsExtractor graphics, Rect rect, int fill, int border, int radius) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        drawRounded(graphics, rect, radius, border);
        if (rect.width() > 2 && rect.height() > 2) {
            drawRounded(graphics, new Rect(rect.x() + 1, rect.y() + 1,
                    rect.width() - 2, rect.height() - 2), Math.max(0, radius - 1), fill);
        }
    }

    private void drawRounded(GuiGraphicsExtractor graphics, Rect rect, int radius, int color) {
        CarbonShapes.drawRounded(graphics, rect.x(), rect.y(), rect.width(), rect.height(), radius, color);
    }

    private String fit(String value, int maxWidth) {
        if (maxWidth <= 0 || value.isEmpty()
                || CarbonText.widthUi(font, value, CarbonText.Weight.MEDIUM, 8) <= maxWidth) {
            return maxWidth <= 0 ? "" : value;
        }
        String ellipsis = "…";
        int low = 0;
        int high = value.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String candidate = value.substring(0, middle) + ellipsis;
            if (CarbonText.widthUi(font, candidate, CarbonText.Weight.MEDIUM, 8) <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return value.substring(0, low) + ellipsis;
    }

    private Rect resizeHandle(Rect bounds) {
        int size = Math.min(RESIZE_HANDLE_SIZE, Math.min(bounds.width(), bounds.height()));
        return new Rect(bounds.right() - size / 2, bounds.bottom() - size / 2, size, size);
    }

    private void setSelectedScale(double newScale) {
        if (selectedModule == null) {
            return;
        }
        int oldX = selectedModule.screenX(width);
        int oldY = selectedModule.screenY(height);
        selectedModule.setEditorScale(newScale);
        selectedModule.setEditorPosition(oldX, oldY, width, height);
    }

    private void resetLayouts() {
        for (HudModule module : hudModules) {
            module.resetLayout();
        }
        snapGuideX = -1;
        snapGuideY = -1;
    }

    private void beginDrag(HudModule module, double mouseX, double mouseY, DragMode mode) {
        selectedModule = module;
        draggingModule = module;
        dragMode = mode;
        dragMouseX = (int) Math.round(mouseX);
        dragMouseY = (int) Math.round(mouseY);
        dragOriginX = module.screenX(width);
        dragOriginY = module.screenY(height);
        dragWidth = module.renderedWidth();
        dragHeight = module.renderedHeight();
        dragScale = module.hudScale();
        snapGuideX = -1;
        snapGuideY = -1;
    }

    private void updateDraggedWidget(double mouseX, double mouseY) {
        if (draggingModule == null || dragMode == DragMode.NONE) {
            return;
        }
        int deltaX = (int) Math.round(mouseX) - dragMouseX;
        int deltaY = (int) Math.round(mouseY) - dragMouseY;
        if (dragMode == DragMode.RESIZE) {
            double widthRatio = (double) deltaX / Math.max(1, dragWidth);
            double heightRatio = (double) deltaY / Math.max(1, dragHeight);
            double adjustment = Math.abs(widthRatio) >= Math.abs(heightRatio) ? widthRatio : heightRatio;
            float nextScale = Math.max(0.5f, Math.min(2.0f, dragScale * (1.0f + (float) adjustment)));
            draggingModule.setEditorScale(nextScale);
            draggingModule.setEditorPosition(dragOriginX, dragOriginY, width, height);
            snapGuideX = -1;
            snapGuideY = -1;
            return;
        }

        int nextX = dragOriginX + deltaX;
        int nextY = dragOriginY + deltaY;
        int moduleWidth = draggingModule.renderedWidth();
        int moduleHeight = draggingModule.renderedHeight();
        nextX = Math.max(0, Math.min(Math.max(0, width - moduleWidth), nextX));
        nextY = Math.max(0, Math.min(Math.max(0, height - moduleHeight), nextY));
        snapGuideX = -1;
        snapGuideY = -1;

        int centerX = width / 2;
        if (Math.abs(nextX + moduleWidth / 2 - centerX) <= SNAP_DISTANCE) {
            nextX = centerX - moduleWidth / 2;
            snapGuideX = centerX;
        } else if (nextX <= SNAP_DISTANCE) {
            nextX = 0;
            snapGuideX = 0;
        } else if (width - (nextX + moduleWidth) <= SNAP_DISTANCE) {
            nextX = width - moduleWidth;
            snapGuideX = width;
        }

        int centerY = height / 2;
        if (Math.abs(nextY + moduleHeight / 2 - centerY) <= SNAP_DISTANCE) {
            nextY = centerY - moduleHeight / 2;
            snapGuideY = centerY;
        } else if (nextY <= SNAP_DISTANCE) {
            nextY = 0;
            snapGuideY = 0;
        } else if (height - (nextY + moduleHeight) <= SNAP_DISTANCE) {
            nextY = height - moduleHeight;
            snapGuideY = height;
        }
        draggingModule.setEditorPosition(nextX, nextY, width, height);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        if (backButton.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (resetButton.contains(mouseX, mouseY)) {
            resetLayouts();
            return true;
        }
        if (selectedModule != null && toggleButton.contains(mouseX, mouseY)) {
            selectedModule.toggle();
            return true;
        }
        if (selectedModule != null && decreaseButton.contains(mouseX, mouseY)) {
            setSelectedScale(selectedModule.hudScale() - 0.05);
            return true;
        }
        if (selectedModule != null && increaseButton.contains(mouseX, mouseY)) {
            setSelectedScale(selectedModule.hudScale() + 0.05);
            return true;
        }
        if (toolbar.contains(mouseX, mouseY)) {
            return true;
        }

        for (int index = moduleBounds.size() - 1; index >= 0; index--) {
            ModuleBounds hit = moduleBounds.get(index);
            Rect hitBounds = hit.module() == selectedModule ? hit.bounds().expand(RESIZE_HANDLE_SIZE / 2) : hit.bounds();
            if (hitBounds.contains(mouseX, mouseY)) {
                DragMode mode = hit.module() == selectedModule
                        && resizeHandle(hit.bounds()).contains(mouseX, mouseY)
                        ? DragMode.RESIZE : DragMode.MOVE;
                beginDrag(hit.module(), mouseX, mouseY, mode);
                return true;
            }
        }
        selectedModule = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragMode != DragMode.NONE) {
            updateDraggedWidget(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragMode != DragMode.NONE) {
            dragMode = DragMode.NONE;
            draggingModule = null;
            snapGuideX = -1;
            snapGuideY = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedModule != null && verticalAmount != 0.0
                && (toolbar.contains(mouseX, mouseY) || moduleBounds.stream()
                .anyMatch(hit -> hit.module() == selectedModule && hit.bounds().contains(mouseX, mouseY)))) {
            setSelectedScale(selectedModule.hudScale() + (verticalAmount > 0.0 ? 0.05 : -0.05));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE
    }

    private record ModuleBounds(HudModule module, Rect bounds) {
    }

    private record Rect(int x, int y, int width, int height) {
        private static final Rect EMPTY = new Rect(0, 0, 0, 0);

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        Rect expand(int amount) {
            return new Rect(x - amount, y - amount, width + amount * 2, height + amount * 2);
        }
    }
}
