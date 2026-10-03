package dev.carbon.client.ui;

import dev.carbon.client.core.module.Module;
import dev.carbon.client.core.setting.BoolSetting;
import dev.carbon.client.core.setting.ColorSetting;
import dev.carbon.client.core.setting.KeybindSetting;
import dev.carbon.client.core.setting.ModeSetting;
import dev.carbon.client.core.setting.NumberSetting;
import dev.carbon.client.ui.render.CarbonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Reusable, native Minecraft widgets styled with the Carbon theme. */
public final class CarbonComponents {
    private CarbonComponents() {
    }

    public abstract static class Widget extends AbstractButton {
        private final String label;
        private float hoverAmount;
        private long lastAnimationNanos;

        protected Widget(int x, int y, int width, int height, String label) {
            super(x, y, width, height, Component.literal(label));
            this.label = label;
            this.lastAnimationNanos = System.nanoTime();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        protected final String label() {
            return label;
        }

        protected final float animateHover() {
            long now = System.nanoTime();
            long elapsed = now - lastAnimationNanos;
            lastAnimationNanos = now;
            hoverAmount = CarbonAnimation.approach(hoverAmount, isHovered() ? 1.0f : 0.0f,
                    elapsed, 0.11f);
            return hoverAmount;
        }

        protected final void drawSurface(GuiGraphicsExtractor graphics, int normal, int highlighted) {
            int color = CarbonTheme.mix(normal, highlighted, animateHover());
            CarbonRenderer.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), 7.0f, color);
        }

        protected final void drawLabel(GuiGraphicsExtractor graphics, String text, int color, int inset) {
            var font = Minecraft.getInstance().font;
            int textY = getY() + (getHeight() - font.lineHeight) / 2;
            CarbonText.draw(graphics, font, text, getX() + inset, textY, color, false);
        }

        protected final void drawCenteredLabel(GuiGraphicsExtractor graphics, String text, int color) {
            var font = Minecraft.getInstance().font;
            int textY = getY() + (getHeight() - font.lineHeight) / 2;
            CarbonText.centered(graphics, font, text, getX() + getWidth() / 2, textY, color, false);
        }

        protected final void beginTextLayer(GuiGraphicsExtractor graphics) {
            graphics.nextStratum();
        }
    }

    public static final class Button extends Widget {
        private final Runnable action;
        private boolean accent;
        private final boolean centered;

        public Button(int x, int y, int width, int height, String label, Runnable action,
                      boolean accent, boolean centered) {
            super(x, y, width, height, label);
            this.action = action;
            this.accent = accent;
            this.centered = centered;
        }

        public void setAccent(boolean accent) {
            this.accent = accent;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int normal = accent ? CarbonTheme.ACCENT_DEEP : CarbonTheme.PANEL_RAISED;
            int hover = accent ? CarbonTheme.ACCENT : CarbonTheme.PANEL_HOVER;
            drawSurface(graphics, normal, hover);
            beginTextLayer(graphics);
            int textColor = accent ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED;
            if (centered) {
                drawCenteredLabel(graphics, label(), textColor);
            } else {
                drawLabel(graphics, label(), textColor, 10);
            }
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }
    }

    /** Card-style module control with distinct options and enabled-state click zones. */
    public static final class ModuleCard extends Widget {
        private final Module module;
        private final Runnable openOptions;
        private final String categoryLabel;
        private final String iconLabel;

        public ModuleCard(int x, int y, int width, int height, Module module, Runnable openOptions) {
            super(x, y, width, height, module.name());
            this.module = module;
            this.openOptions = openOptions;
            this.categoryLabel = module.category().label().toUpperCase(java.util.Locale.ROOT);
            this.iconLabel = createIconLabel(module);
        }

        public Module module() {
            return module;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int surface = CarbonTheme.mix(CarbonTheme.CARD, CarbonTheme.CARD_HOVER, animateHover());
            CarbonRenderer.outline(graphics, getX(), getY(), getWidth(), getHeight(),
                    12.0f, CarbonTheme.BORDER_SOFT, surface);

            int stateHeight = statusHeight();
            int optionsHeight = stateHeight;
            int stateY = getY() + getHeight() - stateHeight;
            int optionsY = stateY - optionsHeight - 2;
            int iconSize = Math.min(50, Math.max(34, Math.min(getWidth() / 4, optionsY - getY() - 28)));
            int iconX = getX() + (getWidth() - iconSize) / 2;
            int iconY = getY() + 10;
            int iconSurface = module.enabled() ? CarbonTheme.ACCENT_MUTED : CarbonTheme.PANEL_RAISED;
            CarbonRenderer.outline(graphics, iconX, iconY, iconSize, iconSize, 12.0f,
                    CarbonTheme.BORDER_SOFT, iconSurface);

            var font = Minecraft.getInstance().font;
            graphics.nextStratum();
            CarbonText.centered(graphics, font, iconLabel, getX() + getWidth() / 2,
                    iconY + (iconSize - font.lineHeight) / 2, CarbonTheme.TEXT, false);
            int titleY = iconY + iconSize + 7;
            CarbonText.centered(graphics, font, module.name(), getX() + getWidth() / 2,
                    titleY, CarbonTheme.TEXT, false);
            CarbonText.centered(graphics, font, categoryLabel, getX() + getWidth() / 2,
                    titleY + font.lineHeight + 2, CarbonTheme.TEXT_DIM, false);

            int rowX = getX() + 1;
            int rowWidth = getWidth() - 2;
            CarbonRenderer.roundedRect(graphics, rowX, optionsY, rowWidth, optionsHeight,
                    0.0f, CarbonTheme.PANEL_RAISED);
            CarbonRenderer.roundedRect(graphics, rowX, optionsY, rowWidth, 1, 0.5f,
                    CarbonTheme.BORDER_SOFT);
            graphics.nextStratum();
            CarbonText.centered(graphics, font, "OPTIONS  >", getX() + getWidth() / 2,
                    optionsY + (optionsHeight - font.lineHeight) / 2, CarbonTheme.TEXT, false);

            int stateColor = module.enabled() ? CarbonTheme.SUCCESS_SURFACE : CarbonTheme.ERROR_SURFACE;
            CarbonRenderer.roundedRect(graphics, rowX, stateY, rowWidth, stateHeight,
                    0.0f, stateColor);
            graphics.nextStratum();
            CarbonText.centered(graphics, font, module.enabled() ? "ENABLED" : "DISABLED",
                    getX() + getWidth() / 2, stateY + (stateHeight - font.lineHeight) / 2,
                    CarbonTheme.TEXT, false);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            module.toggle();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                super.onClick(event, doubleClick);
                return;
            }
            setFocused(true);
            playDownSound(Minecraft.getInstance().getSoundManager());
            if (event.y() - getY() >= getHeight() - statusHeight()) {
                module.toggle();
            } else {
                openOptions.run();
            }
        }

        private int statusHeight() {
            return Math.max(22, Math.min(31, getHeight() / 7));
        }

        private static String createIconLabel(Module module) {
            return switch (module.id()) {
                case "fps" -> "FPS";
                case "cps" -> "CPS";
                case "keystrokes" -> "KEYS";
                case "zoom" -> "ZOOM";
                default -> module.name().toUpperCase(java.util.Locale.ROOT);
            };
        }
    }

    /** A compact switch bound either to a module's enabled state or a BoolSetting. */
    public static final class Toggle extends Widget {
        private final Module module;
        private final BoolSetting setting;

        public Toggle(int x, int y, int width, int height, Module module) {
            super(x, y, width, height, module.name());
            this.module = module;
            this.setting = null;
        }

        public Toggle(int x, int y, int width, int height, BoolSetting setting) {
            super(x, y, width, height, setting.label());
            this.module = null;
            this.setting = setting;
        }

        private boolean value() {
            return module != null ? module.enabled() : setting.enabled();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean enabled = value();
            drawSurface(graphics, CarbonTheme.PANEL_RAISED, CarbonTheme.PANEL_HOVER);

            int trackX = getX() + getWidth() - 38;
            int trackY = getY() + (getHeight() - 16) / 2;
            CarbonRenderer.roundedRect(graphics, trackX, trackY, 30, 16, 8.0f,
                    enabled ? CarbonTheme.ACCENT_DEEP : CarbonTheme.TRACK_OFF);
            int knobX = enabled ? trackX + 16 : trackX + 2;
            CarbonRenderer.roundedRect(graphics, knobX, trackY + 2, 12, 12, 6.0f,
                    enabled ? CarbonTheme.ACCENT : CarbonTheme.TEXT_MUTED);

            beginTextLayer(graphics);
            if (getWidth() > 72) {
                drawLabel(graphics, label(), CarbonTheme.TEXT, 10);
            }
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (module != null) {
                module.toggle();
            } else {
                setting.toggle();
            }
        }
    }

    /** Number-setting slider. Mouse clicks and drags map directly onto the setting range. */
    public static final class Slider extends Widget {
        private final NumberSetting setting;
        private double cachedValue = Double.NaN;
        private String cachedDisplayValue = "";
        private int cachedDisplayWidth;

        public Slider(int x, int y, int width, int height, NumberSetting setting) {
            super(x, y, width, height, setting.label());
            this.setting = setting;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            drawSurface(graphics, CarbonTheme.PANEL_RAISED, CarbonTheme.PANEL_HOVER);

            int trackX = getX() + 10;
            int trackWidth = Math.max(1, getWidth() - 20);
            int trackY = getY() + getHeight() - 9;
            CarbonRenderer.roundedRect(graphics, trackX, trackY, trackWidth, 3, 1.5f,
                    CarbonTheme.TRACK_OFF);
            double currentValue = setting.get();
            if (Double.compare(currentValue, cachedValue) != 0) {
                cachedValue = currentValue;
                cachedDisplayValue = setting.displayValue();
                cachedDisplayWidth = CarbonText.width(Minecraft.getInstance().font, cachedDisplayValue);
            }
            double range = setting.maximum() - setting.minimum();
            double fraction = range <= 0.0 ? 0.0 : (currentValue - setting.minimum()) / range;
            int fillWidth = (int) Math.round(trackWidth * Math.max(0.0, Math.min(1.0, fraction)));
            if (fillWidth > 0) {
                CarbonRenderer.roundedRect(graphics, trackX, trackY, fillWidth, 3, 1.5f,
                        CarbonTheme.ACCENT);
            }

            beginTextLayer(graphics);
            var font = Minecraft.getInstance().font;
            int textY = getY() + 5;
            CarbonText.draw(graphics, font, label(), getX() + 10, textY, CarbonTheme.TEXT, false);
            CarbonText.draw(graphics, font, cachedDisplayValue,
                    getX() + getWidth() - cachedDisplayWidth - 10, textY, CarbonTheme.ACCENT, false);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            setting.set(setting.get() + setting.step());
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                setFromMouse(event.x());
            } else {
                super.onClick(event, doubleClick);
            }
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                setFromMouse(event.x());
            }
        }

        private void setFromMouse(double mouseX) {
            int trackX = getX() + 10;
            int trackWidth = Math.max(1, getWidth() - 20);
            setting.setFromFraction((mouseX - trackX) / trackWidth);
        }
    }

    /** Click-to-cycle control for a ModeSetting. */
    public static final class ModeButton extends Widget {
        private final ModeSetting setting;

        public ModeButton(int x, int y, int width, int height, ModeSetting setting) {
            super(x, y, width, height, setting.label());
            this.setting = setting;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            drawSurface(graphics, CarbonTheme.PANEL_RAISED, CarbonTheme.PANEL_HOVER);
            beginTextLayer(graphics);
            var font = Minecraft.getInstance().font;
            String mode = setting.get();
            int textY = getY() + (getHeight() - font.lineHeight) / 2;
            CarbonText.draw(graphics, font, label(), getX() + 10, textY, CarbonTheme.TEXT, false);
            CarbonText.draw(graphics, font, mode,
                    getX() + getWidth() - CarbonText.width(font, mode) - 10, textY, CarbonTheme.ACCENT, false);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            setting.cycle();
        }
    }

    /** Palette swatch that cycles through a small set of useful ARGB colors. */
    public static final class ColorButton extends Widget {
        private static final int[] PALETTE = {
                0xFFFFFFFF,
                CarbonTheme.ACCENT,
                0xFFFFD166,
                0xFFFF7777,
                0xFF77A8FF
        };

        private final ColorSetting setting;

        public ColorButton(int x, int y, int width, int height, ColorSetting setting) {
            super(x, y, width, height, setting.label());
            this.setting = setting;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            drawSurface(graphics, CarbonTheme.PANEL_RAISED, CarbonTheme.PANEL_HOVER);
            int swatchWidth = Math.min(34, Math.max(20, getWidth() / 5));
            int swatchX = getX() + getWidth() - swatchWidth - 9;
            int swatchY = getY() + (getHeight() - 14) / 2;
            CarbonRenderer.roundedRect(graphics, swatchX, swatchY, swatchWidth, 14, 5.0f, setting.get());
            beginTextLayer(graphics);
            drawLabel(graphics, label(), CarbonTheme.TEXT, 10);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            int current = setting.get();
            for (int index = 0; index < PALETTE.length; index++) {
                if (PALETTE[index] == current) {
                    setting.set(PALETTE[(index + 1) % PALETTE.length]);
                    return;
                }
            }
            setting.set(PALETTE[0]);
        }
    }

    /** Press-to-rebind control; CarbonDemoScreen completes the capture via client input events. */
    public static final class KeybindButton extends Widget {
        private final KeybindSetting setting;
        private final Runnable beginCapture;
        private KeybindSetting.Binding cachedBinding;
        private String cachedDisplayValue;
        private boolean listening;

        public KeybindButton(int x, int y, int width, int height, KeybindSetting setting, Runnable beginCapture) {
            super(x, y, width, height, setting.label());
            this.setting = setting;
            this.beginCapture = beginCapture;
        }

        public KeybindSetting setting() {
            return setting;
        }

        public void setListening(boolean listening) {
            this.listening = listening;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int base = listening ? CarbonTheme.ACCENT_MUTED : CarbonTheme.PANEL_RAISED;
            int hover = listening ? CarbonTheme.ACCENT_DEEP : CarbonTheme.PANEL_HOVER;
            drawSurface(graphics, base, hover);
            beginTextLayer(graphics);

            var font = Minecraft.getInstance().font;
            if (cachedBinding == null || !cachedBinding.equals(setting.get())) {
                cachedBinding = setting.get();
                cachedDisplayValue = setting.displayValue();
            }
            String value = listening ? "Press a key or mouse button" : cachedDisplayValue;
            int textY = getY() + (getHeight() - font.lineHeight) / 2;
            CarbonText.draw(graphics, font, label(), getX() + 10, textY, CarbonTheme.TEXT, false);
            CarbonText.draw(graphics, font, value,
                    getX() + getWidth() - CarbonText.width(font, value) - 10,
                    textY, listening ? CarbonTheme.WARNING : CarbonTheme.ACCENT, false);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            listening = true;
            beginCapture.run();
        }
    }
}
