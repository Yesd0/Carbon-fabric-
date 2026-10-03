package dev.carbon.client.modules.hud;

import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.HudRenderEvent;
import dev.carbon.client.core.util.ColorUtil;
import dev.carbon.client.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class KeystrokesHudModule extends HudModule {
    private static final int BOX = 18;
    private static final int GAP = 2;
    private static final int KEY_BACKGROUND = 0xC0161C20;
    private static final int KEY_PRESSED = 0xFF0FA85A;
    private static final int BORDER = 0x66FFFFFF;
    private static final int WIDTH = 72;
    private static final int HEIGHT = BOX * 3 + GAP * 2 + 8 + GAP;

    private boolean up;
    private boolean left;
    private boolean down;
    private boolean right;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    public KeystrokesHudModule(EventBus eventBus) {
        super(eventBus, "keystrokes", "Keystrokes", "Shows movement and action keys", true, 0.02, 0.42);
    }

    @Override
    protected void onHudTick() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || client.player == null) {
            up = left = down = right = jump = sneak = sprint = false;
            return;
        }
        up = client.options.keyUp.isDown();
        left = client.options.keyLeft.isDown();
        down = client.options.keyDown.isDown();
        right = client.options.keyRight.isDown();
        jump = client.options.keyJump.isDown();
        sneak = client.options.keyShift.isDown();
        sprint = client.options.keySprint.isDown();
    }

    @Override
    protected int width() {
        return WIDTH;
    }

    @Override
    protected int height() {
        return HEIGHT;
    }

    @Override
    protected void render(HudRenderEvent event, int x, int y) {
        GuiGraphicsExtractor graphics = event.graphics();
        Font font = Minecraft.getInstance().font;
        int centerX = x + BOX + GAP;
        drawKey(graphics, font, "W", centerX, y, up);
        drawKey(graphics, font, "A", x, y + BOX + GAP, left);
        drawKey(graphics, font, "S", centerX, y + BOX + GAP, down);
        drawKey(graphics, font, "D", x + (BOX + GAP) * 2, y + BOX + GAP, right);
        drawKey(graphics, font, "JUMP", x, y + (BOX + GAP) * 2, jump, WIDTH);

        int indicatorY = y + HEIGHT - 8;
        drawIndicator(graphics, font, "SNEAK", x, indicatorY, sneak);
        drawIndicator(graphics, font, "SPRINT", x + 38, indicatorY, sprint);
    }

    private void drawKey(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, boolean pressed) {
        drawKey(graphics, font, label, x, y, pressed, BOX);
    }

    private void drawKey(GuiGraphicsExtractor graphics, Font font, String label, int x, int y,
                         boolean pressed, int width) {
        int fill = pressed ? KEY_PRESSED : KEY_BACKGROUND;
        graphics.fill(x, y, x + width, y + BOX, fill);
        graphics.outline(x, y, width, BOX, BORDER);
        graphics.text(font, label, x + 4, y + 5, textColor().get(), true);
    }

    private void drawIndicator(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, boolean active) {
        int color = active ? 0xFF7BFFB5 : textColor().get();
        graphics.fill(x, y, x + 34, y + 8, ColorUtil.withAlpha(active ? KEY_PRESSED : KEY_BACKGROUND, 210));
        graphics.text(font, label, x + 2, y, color, false);
    }
}
