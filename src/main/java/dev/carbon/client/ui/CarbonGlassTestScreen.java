package dev.carbon.client.ui;

import dev.carbon.client.ui.render.CarbonBlur;
import dev.carbon.client.ui.render.CarbonGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Part A visual harness for checking Carbon's native-backdrop glass treatment. */
public final class CarbonGlassTestScreen extends CarbonScreen {
    private static final float PANEL_WIDTH = 880.0f;
    private static final float PANEL_HEIGHT = 510.0f;
    private static final float CARD_WIDTH = 240.0f;
    private static final float CARD_HEIGHT = 96.0f;
    private static final float CARD_GAP = 40.0f;
    private static final int[] PATTERN_COLORS = {
            0x48FF527E, 0x45396BFF, 0x43F2B544, 0x4642DDB3,
            0x49CF57EA, 0x4539C6F4, 0x43FF8E54
    };

    private float panelX;
    private float panelY;
    private float onCardX;
    private float offCardX;
    private float cardsY;
    private int cachedFramebufferWidth = -1;
    private int cachedFramebufferHeight = -1;
    private int cachedGuiScale = -1;
    private float cachedUiScale = -1.0f;
    private String scaleDebugLine = "";
    private String panelDebugLine = "";
    private String onCardDebugLine = "";
    private String offCardDebugLine = "";

    public CarbonGlassTestScreen(Screen parent) {
        super(Component.literal("Carbon Glass Test"), parent);
    }

    @Override
    protected void init() {
        UiScale.update(Minecraft.getInstance());
        CarbonIcons.load();
        updateLayout();
        updateDebugLabels();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiScale.update(Minecraft.getInstance());
        updateLayout();
        updateDebugLabels();
        UiScale.pushRendererScale(graphics);

        drawMovingPattern(graphics);
        graphics.nextStratum();
        CarbonGlass.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT,
                26.0f, CarbonGlass.Style.MAIN);
        graphics.nextStratum();
        CarbonGlass.drawPanel(graphics, onCardX, cardsY, CARD_WIDTH, CARD_HEIGHT,
                16.0f, CarbonGlass.Style.CARD_ON);
        CarbonGlass.drawPanel(graphics, offCardX, cardsY, CARD_WIDTH, CARD_HEIGHT,
                16.0f, CarbonGlass.Style.CARD_OFF);
        graphics.nextStratum();
        drawSampleCardSurfaces(graphics, onCardX, true);
        drawSampleCardSurfaces(graphics, offCardX, false);
        graphics.nextStratum();
        drawPanelContent(graphics);
        graphics.nextStratum();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.nextStratum();
        if (CarbonScreen.isLayoutDebugEnabled()) {
            drawLayoutDebug(graphics);
        }
        CarbonGlass.drawFailureLabel(graphics);
        UiScale.popRendererScale(graphics);
    }

    private void drawMovingPattern(GuiGraphicsExtractor graphics) {
        float width = UiScale.designWidth();
        float height = UiScale.designHeight();
        long now = System.nanoTime();
        float seconds = (now % 60_000_000_000L) / 1_000_000_000.0f;
        graphics.fill(0, 0, Math.round(width), Math.round(height), 0x33040A12);

        for (int lane = 0; lane < PATTERN_COLORS.length; lane++) {
            float phase = lane * 0.86f;
            float wave = (float) Math.sin(seconds * 0.44f + phase);
            float drift = (float) Math.cos(seconds * 0.31f + phase * 0.73f);
            float centerX = width * 0.5f + wave * width * 0.22f;
            float centerY = height * (0.13f + lane * 0.125f) + drift * 48.0f;
            float angle = (float) Math.sin(seconds * 0.22f + phase) * 0.085f;

            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY);
            graphics.pose().rotate(angle);
            int halfWidth = Math.round(width * 0.34f);
            graphics.fill(-halfWidth, -42, halfWidth, 42, PATTERN_COLORS[lane]);
            graphics.pose().popMatrix();
        }
    }

    private void drawPanelContent(GuiGraphicsExtractor graphics) {
        var font = Minecraft.getInstance().font;
        CarbonText.drawDesign(graphics, font, "CARBON GLASS TEST",
                CarbonText.Weight.SEMIBOLD, 22.0f, panelX + 32.0f, panelY + 27.0f,
                CarbonTheme.TEXT, false);
        CarbonText.drawDesign(graphics, font, "Native blurred backdrop with two Carbon glass states",
                CarbonText.Weight.REGULAR, 12.0f, panelX + 33.0f, panelY + 62.0f,
                0x8CF1F7F2, false);
        CarbonText.drawDesign(graphics, font, "F7 toggles layout debug",
                CarbonText.Weight.MEDIUM, 11.0f, panelX + 33.0f, panelY + 83.0f,
                0xA5EAF3EC, false);

        drawSampleCardContent(graphics, onCardX, true);
        drawSampleCardContent(graphics, offCardX, false);

        CarbonText.drawDesign(graphics, font, "The moving color field remains visible through the card tint.",
                CarbonText.Weight.REGULAR, 13.0f, panelX + 32.0f, panelY + 326.0f,
                0xB8F1F7F2, false);
        CarbonText.drawDesign(graphics, font, "No solid-panel fallback is used if Carbon's glass pipeline fails.",
                CarbonText.Weight.REGULAR, 12.0f, panelX + 32.0f, panelY + 350.0f,
                0x8CF1F7F2, false);
        CarbonText.drawDesign(graphics, font, CarbonBlur.DEBUG_PATH,
                CarbonText.Weight.MEDIUM, 11.0f, panelX + 32.0f, panelY + PANEL_HEIGHT - 32.0f,
                0xA5EAF3EC, false);
    }

    private void drawSampleCardSurfaces(GuiGraphicsExtractor graphics, float x, boolean enabled) {
        float tileX = x + 16.0f;
        float tileY = cardsY + 16.0f;
        CarbonGlass.drawTintedRectDesign(graphics, tileX, tileY, 44.0f, 44.0f,
                13.0f, enabled ? 0x7753E894 : 0x6877847B);

        float switchX = x + CARD_WIDTH - 56.0f;
        float switchY = cardsY + 16.0f;
        CarbonGlass.drawTintedRectDesign(graphics, switchX, switchY, 40.0f, 22.0f,
                11.0f, enabled ? 0xBA26CC72 : 0xA66D7971);
        CarbonGlass.drawTintedRectDesign(graphics,
                switchX + (enabled ? 20.0f : 2.0f), switchY + 2.0f,
                18.0f, 18.0f, 9.0f, enabled ? 0xFFF5FFF8 : 0xD6E6ECE7);

        float chipX = x + 72.0f;
        float chipY = cardsY + 66.0f;
        CarbonGlass.drawTintedRectDesign(graphics, chipX, chipY, 42.0f, 17.0f,
                8.5f, enabled ? 0x773DCE7B : 0x6977837B);
        float settingsX = x + CARD_WIDTH - 36.0f;
        float settingsY = cardsY + 64.0f;
        CarbonGlass.drawTintedRectDesign(graphics, settingsX, settingsY, 20.0f, 20.0f,
                7.0f, 0x556F8076);
    }

    private void drawSampleCardContent(GuiGraphicsExtractor graphics, float x, boolean enabled) {
        var font = Minecraft.getInstance().font;
        float tileX = x + 16.0f;
        float tileY = cardsY + 16.0f;
        CarbonIcons.drawDesign(graphics, enabled ? "gauge" : "keyboard",
                tileX + 11.0f, tileY + 11.0f, 22.0f,
                enabled ? 0xFFF5FFF8 : 0xBDF1F7F2);

        CarbonText.drawDesign(graphics, font, enabled ? "Glass card ON" : "Glass card OFF",
                CarbonText.Weight.SEMIBOLD, 15.0f, x + 72.0f, cardsY + 17.0f,
                CarbonTheme.TEXT, false);
        CarbonText.drawDesign(graphics, font, enabled ? "Green tint, visible blur" : "Neutral tint, visible blur",
                CarbonText.Weight.REGULAR, 12.0f, x + 72.0f, cardsY + 41.0f,
                0x8CF1F7F2, false);

        float chipX = x + 72.0f;
        float chipY = cardsY + 66.0f;
        CarbonText.centeredDesign(graphics, font, enabled ? "ON" : "OFF",
                CarbonText.Weight.MEDIUM, 11.0f,
                chipX + 21.0f, chipY + 3.0f, enabled ? CarbonTheme.TEXT : 0xC6E2E9E4, false);

        float settingsX = x + CARD_WIDTH - 36.0f;
        float settingsY = cardsY + 64.0f;
        CarbonIcons.drawDesign(graphics, "settings", settingsX + 3.0f, settingsY + 3.0f,
                14.0f, enabled ? CarbonTheme.TEXT : 0xC6E2E9E4);
    }

    private void drawLayoutDebug(GuiGraphicsExtractor graphics) {
        graphics.outline(Math.round(panelX), Math.round(panelY),
                Math.round(PANEL_WIDTH), Math.round(PANEL_HEIGHT), 0xE65BFFA2);
        graphics.outline(Math.round(onCardX), Math.round(cardsY),
                Math.round(CARD_WIDTH), Math.round(CARD_HEIGHT), 0xE6EAF7EE);
        graphics.outline(Math.round(offCardX), Math.round(cardsY),
                Math.round(CARD_WIDTH), Math.round(CARD_HEIGHT), 0xE6EAF7EE);
        var font = Minecraft.getInstance().font;
        float x = 24.0f;
        float y = 72.0f;
        CarbonText.drawDesign(graphics, font, "LAYOUT DEBUG  |  F7 toggles",
                CarbonText.Weight.SEMIBOLD, 13.0f, x, y, 0xFFFFFFFF, false);
        CarbonText.drawDesign(graphics, font, scaleDebugLine,
                CarbonText.Weight.REGULAR, 11.0f, x, y + 20.0f, 0xD9F1F7F2, false);
        CarbonText.drawDesign(graphics, font, panelDebugLine,
                CarbonText.Weight.REGULAR, 11.0f, x, y + 37.0f, 0xD9F1F7F2, false);
        CarbonText.drawDesign(graphics, font, onCardDebugLine,
                CarbonText.Weight.REGULAR, 11.0f, x, y + 54.0f, 0xD9F1F7F2, false);
        CarbonText.drawDesign(graphics, font, offCardDebugLine,
                CarbonText.Weight.REGULAR, 11.0f, x, y + 71.0f, 0xD9F1F7F2, false);
    }

    private void updateLayout() {
        float designWidth = UiScale.designWidth();
        float designHeight = UiScale.designHeight();
        panelX = UiScale.snap((designWidth - PANEL_WIDTH) * 0.5f);
        panelY = UiScale.snap((designHeight - PANEL_HEIGHT) * 0.5f);
        float rowWidth = CARD_WIDTH * 2.0f + CARD_GAP;
        onCardX = UiScale.snap(panelX + (PANEL_WIDTH - rowWidth) * 0.5f);
        offCardX = UiScale.snap(onCardX + CARD_WIDTH + CARD_GAP);
        cardsY = UiScale.snap(panelY + 164.0f);
    }

    private void updateDebugLabels() {
        int framebufferWidth = UiScale.framebufferWidth();
        int framebufferHeight = UiScale.framebufferHeight();
        int guiScale = UiScale.guiScale();
        float uiScale = UiScale.uiScale();
        if (framebufferWidth == cachedFramebufferWidth
                && framebufferHeight == cachedFramebufferHeight
                && guiScale == cachedGuiScale
                && Float.compare(uiScale, cachedUiScale) == 0) {
            return;
        }

        cachedFramebufferWidth = framebufferWidth;
        cachedFramebufferHeight = framebufferHeight;
        cachedGuiScale = guiScale;
        cachedUiScale = uiScale;
        scaleDebugLine = "Framebuffer " + framebufferWidth + "x" + framebufferHeight
                + " | GUI " + guiScale + " | uiScale " + uiScale
                + " | renderer " + UiScale.rendererScale();
        panelDebugLine = bounds("main", panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 26.0f);
        onCardDebugLine = bounds("ON", onCardX, cardsY, CARD_WIDTH, CARD_HEIGHT, 16.0f);
        offCardDebugLine = bounds("OFF", offCardX, cardsY, CARD_WIDTH, CARD_HEIGHT, 16.0f);
    }

    private static String bounds(String name, float x, float y, float width, float height, float radius) {
        return name + " x=" + Math.round(x) + " y=" + Math.round(y)
                + " w=" + Math.round(width) + " h=" + Math.round(height)
                + " r=" + Math.round(radius) + " design px";
    }
}
