package dev.carbon.client.ui;

import dev.carbon.client.core.module.Module;
import dev.carbon.client.core.setting.BoolSetting;
import dev.carbon.client.core.setting.ColorSetting;
import dev.carbon.client.core.setting.KeybindSetting;
import dev.carbon.client.core.setting.ModeSetting;
import dev.carbon.client.core.setting.NumberSetting;
import dev.carbon.client.ui.render.CarbonGlass;
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
        private String label;
        private float hoverAmount;
        private long lastAnimationNanos;
        private final long createdAtNanos;

        protected Widget(int x, int y, int width, int height, String label) {
            super(x, y, width, height, Component.literal(label));
            this.label = label;
            this.lastAnimationNanos = System.nanoTime();
            this.createdAtNanos = lastAnimationNanos;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        protected final String label() {
            return label;
        }

        protected final void setLabel(String label) {
            this.label = label;
            setMessage(Component.literal(label));
        }

        protected final float animateEntrance(long delayNanos) {
            long elapsed = System.nanoTime() - createdAtNanos - Math.max(0L, delayNanos);
            return CarbonAnimation.easeOutBack(elapsed / 240_000_000.0f);
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
            CarbonGlass.drawTintedRect(graphics, getX(), getY(), getWidth(), getHeight(),
                    7.0f * UiScale.rendererScale(), color);
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

        public void updateLabel(String label) {
            setLabel(label);
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

    /** Compact icon-only action using a real Carbon atlas image. */
    public static final class IconButton extends Widget {
        private final String iconName;
        private final Runnable action;

        public IconButton(int x, int y, int width, int height, String iconName,
                          String narrationLabel, Runnable action) {
            super(x, y, width, height, narrationLabel);
            this.iconName = iconName;
            this.action = action;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            drawSurface(graphics, 0x1AFFFFFF, 0x36FFFFFF);
            beginTextLayer(graphics);
            int iconSize = Math.max(1, Math.min(getWidth(), getHeight()) * 2 / 3);
            CarbonIcons.drawGui(graphics, iconName,
                    getX() + (getWidth() - iconSize) / 2,
                    getY() + (getHeight() - iconSize) / 2,
                    iconSize, CarbonTheme.TEXT);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }
    }

    /** Sidebar navigation button with a bundled Carbon atlas icon. */
    public static final class NavButton extends Widget {
        private final String iconName;
        private final Runnable action;
        private boolean accent;

        public NavButton(int x, int y, int width, int height, String iconName, String label,
                         Runnable action, boolean accent) {
            super(x, y, width, height, label);
            this.iconName = iconName;
            this.action = action;
            this.accent = accent;
        }

        public void setAccent(boolean accent) {
            this.accent = accent;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int normal = accent ? 0x2A33D889 : 0x13FFFFFF;
            int hover = accent ? 0x4A3FE895 : 0x18FFFFFF;
            drawSurface(graphics, normal, hover);
            beginTextLayer(graphics);
            int iconSize = design(20);
            int iconX = getX() + design(16);
            int iconY = getY() + (getHeight() - iconSize) / 2;
            CarbonIcons.drawGui(graphics, iconName, iconX, iconY, iconSize,
                    accent ? CarbonTheme.ACCENT : CarbonTheme.TEXT_MUTED);
            int textY = getY() + (getHeight() - design(14)) / 2;
            CarbonText.drawUi(graphics, Minecraft.getInstance().font, label(),
                    CarbonText.Weight.MEDIUM, 14.0f, getX() + design(48), textY,
                    accent ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED, false);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        private int design(int pixels) {
            return Math.max(1, Math.round(pixels * UiScale.rendererScale()));
        }
    }

    /** Card-style module control with distinct options and enabled-state click zones. */
    public static final class ModuleCard extends Widget {
        private final Module module;
        private final Runnable openOptions;
        private final Runnable afterToggle;
        private final String categoryLabel;
        private final String iconName;
        private String shortDescription;
        private long entranceDelayNanos;
        private long lastModuleStateNanos;
        private float enabledAmount;

        public ModuleCard(int x, int y, int width, int height, Module module,
                          Runnable openOptions, Runnable afterToggle) {
            super(x, y, width, height, module.name());
            this.module = module;
            this.openOptions = openOptions;
            this.afterToggle = afterToggle;
            this.categoryLabel = module.category().label().toUpperCase(java.util.Locale.ROOT);
            this.iconName = iconFor(module);
            this.shortDescription = module.description();
            this.enabledAmount = module.enabled() ? 1.0f : 0.0f;
            this.lastModuleStateNanos = System.nanoTime();
        }

        public void setEntranceDelayMillis(long delayMillis) {
            entranceDelayNanos = Math.max(0L, delayMillis) * 1_000_000L;
        }

        public Module module() {
            return module;
        }

        public void updateDescriptionWidth(int maximumWidth) {
            var font = Minecraft.getInstance().font;
            String source = module.description();
            if (CarbonText.width(font, source) <= maximumWidth) {
                shortDescription = source;
                return;
            }
            String suffix = "…";
            int end = source.length();
            while (end > 0 && CarbonText.width(font, source.substring(0, end) + suffix) > maximumWidth) {
                end--;
            }
            shortDescription = end == 0 ? suffix : source.substring(0, end).stripTrailing() + suffix;
        }

        private int design(int pixels) {
            return Math.max(1, Math.round(pixels * UiScale.rendererScale()));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            float entrance = animateEntrance(entranceDelayNanos);
            long now = System.nanoTime();
            enabledAmount = CarbonAnimation.approach(enabledAmount, module.enabled() ? 1.0f : 0.0f,
                    now - lastModuleStateNanos, 0.16f);
            lastModuleStateNanos = now;
            float hover = animateHover();

            graphics.pose().pushMatrix();
            float scale = 0.97f + 0.03f * CarbonAnimation.easeOutCubic(entrance);
            graphics.pose().translate(getX() + getWidth() * 0.5f, getY() + getHeight() * 0.5f);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-getX() - getWidth() * 0.5f,
                    -getY() - getHeight() * 0.5f + (1.0f - entrance) * design(9));

            CarbonGlass.drawPanelGui(graphics, getX(), getY(), getWidth(), getHeight(), 16.0f,
                    enabledAmount > 0.5f ? CarbonGlass.Style.CARD_ON : CarbonGlass.Style.CARD_OFF);
            if (enabledAmount > 0.001f) {
                int alpha = Math.round(18.0f * enabledAmount);
                CarbonGlass.drawTintedRect(graphics, getX(), getY(), getWidth(), getHeight(),
                        16.0f, (alpha << 24) | 0x003FE18E);
            }
            if (hover > 0.001f) {
                int alpha = Math.round(18.0f * hover);
                CarbonGlass.drawTintedRect(graphics, getX(), getY(), getWidth(), getHeight(),
                        16.0f, (alpha << 24) | 0x00FFFFFF);
            }

            int margin = design(16);
            int tileSize = design(44);
            int iconSize = design(22);
            int tileX = getX() + margin;
            int tileY = getY() + margin;
            CarbonGlass.drawTintedRect(graphics, tileX, tileY, tileSize, tileSize, design(12),
                    CarbonTheme.mix(0x55FFFFFF, 0x7545D78A, enabledAmount));

            int toggleWidth = design(40);
            int toggleHeight = design(22);
            int toggleX = getX() + getWidth() - design(16) - toggleWidth;
            int toggleY = getY() + design(16);
            CarbonGlass.drawTintedRect(graphics, toggleX, toggleY, toggleWidth, toggleHeight,
                    toggleHeight * 0.5f, CarbonTheme.mix(0x88465158, 0xE522B96A, enabledAmount));
            int knobSize = design(14);
            int knobTravel = design(18);
            int knobX = toggleX + design(4) + Math.round(knobTravel * enabledAmount);
            int knobY = toggleY + (toggleHeight - knobSize) / 2;
            CarbonGlass.drawTintedRect(graphics, knobX, knobY, knobSize, knobSize,
                    knobSize * 0.5f, 0xFFF4FFF8);

            int settingsWidth = design(92);
            int settingsHeight = design(26);
            int settingsX = getX() + getWidth() - margin - settingsWidth;
            int settingsY = getY() + getHeight() - design(16) - settingsHeight;
            CarbonGlass.drawTintedRect(graphics, settingsX, settingsY, settingsWidth, settingsHeight,
                    design(8), 0x8A27382D);

            var font = Minecraft.getInstance().font;
            int textX = getX() + design(72);
            int titleY = getY() + design(18);
            int descriptionY = getY() + design(44);
            int chipY = getY() + design(78);
            int categoryWidth = Math.max(design(58), CarbonText.width(font, categoryLabel) + design(16));
            int stateWidth = design(58);
            graphics.nextStratum();
            CarbonIcons.drawGui(graphics, iconName,
                    tileX + (tileSize - iconSize) / 2, tileY + (tileSize - iconSize) / 2,
                    iconSize, CarbonTheme.TEXT);
            CarbonText.drawUi(graphics, font, module.name(), CarbonText.Weight.SEMIBOLD,
                    15.0f, textX, titleY, CarbonTheme.TEXT, false);
            CarbonText.draw(graphics, font, shortDescription, textX, descriptionY,
                    0xB8F1F7F2, false);
            CarbonGlass.drawTintedRect(graphics, textX, chipY, categoryWidth, design(19),
                    design(9), 0x3BFFFFFF);
            CarbonGlass.drawTintedRect(graphics, textX + categoryWidth + design(7), chipY,
                    stateWidth, design(19), design(9),
                    enabledAmount > 0.5f ? 0x663FE18E : 0x3BFFFFFF);
            graphics.nextStratum();
            CarbonText.drawUi(graphics, font, categoryLabel, CarbonText.Weight.MEDIUM,
                    10.0f, textX + design(8), chipY + design(3), CarbonTheme.TEXT_MUTED, false);
            CarbonText.drawUi(graphics, font, enabledAmount > 0.5f ? "ON" : "OFF",
                    CarbonText.Weight.SEMIBOLD, 10.0f,
                    textX + categoryWidth + design(7) + design(8), chipY + design(3),
                    enabledAmount > 0.5f ? CarbonTheme.ACCENT : CarbonTheme.TEXT_MUTED, false);
            int settingsIconSize = design(14);
            CarbonIcons.drawGui(graphics, "settings", settingsX + design(8),
                    settingsY + (settingsHeight - settingsIconSize) / 2,
                    settingsIconSize, CarbonTheme.TEXT);
            CarbonText.drawUi(graphics, font, "SETTINGS", CarbonText.Weight.MEDIUM,
                    9.0f, settingsX + design(27), settingsY + design(7), CarbonTheme.TEXT, false);
            graphics.pose().popMatrix();
        }

        @Override
        public void onPress(InputWithModifiers input) {
            toggleModule();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return;
            }
            setFocused(true);
            playDownSound(Minecraft.getInstance().getSoundManager());
            double relativeX = event.x() - getX();
            double relativeY = event.y() - getY();
            int settingsLeft = getWidth() - design(16) - design(92);
            int settingsTop = getHeight() - design(16) - design(26);
            if (relativeY >= settingsTop && relativeY <= settingsTop + design(26)
                    && relativeX >= settingsLeft && relativeX <= settingsLeft + design(92)) {
                openOptions.run();
            } else {
                // The entire card is a forgiving on/off target; only the gear opens settings.
                toggleModule();
            }
        }

        private void toggleModule() {
            module.toggle();
            afterToggle.run();
        }

        private static String iconFor(Module module) {
            return switch (module.id()) {
                case "fps" -> "gauge";
                case "cps" -> "mouse-pointer-click";
                case "keystrokes" -> "keyboard";
                case "zoom" -> "zoom-in";
                default -> "layout-grid";
            };
        }
    }

    /** A compact switch bound either to a module's enabled state or a BoolSetting. */
    public static final class Toggle extends Widget {
        private final Module module;
        private final BoolSetting setting;
        private float animatedValue;
        private long lastValueNanos;

        public Toggle(int x, int y, int width, int height, Module module) {
            super(x, y, width, height, module.name());
            this.module = module;
            this.setting = null;
            this.animatedValue = module.enabled() ? 1.0f : 0.0f;
            this.lastValueNanos = System.nanoTime();
        }

        public Toggle(int x, int y, int width, int height, BoolSetting setting) {
            super(x, y, width, height, setting.label());
            this.module = null;
            this.setting = setting;
            this.animatedValue = setting.enabled() ? 1.0f : 0.0f;
            this.lastValueNanos = System.nanoTime();
        }

        private boolean value() {
            return module != null ? module.enabled() : setting.enabled();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            long now = System.nanoTime();
            animatedValue = CarbonAnimation.approach(animatedValue, value() ? 1.0f : 0.0f,
                    now - lastValueNanos, 0.14f);
            lastValueNanos = now;
            drawSurface(graphics, CarbonTheme.PANEL_RAISED, CarbonTheme.PANEL_HOVER);

            int trackWidth = design(34);
            int trackHeight = design(18);
            int trackX = getX() + getWidth() - design(48);
            int trackY = getY() + (getHeight() - trackHeight) / 2;
            CarbonGlass.drawTintedRect(graphics, trackX, trackY, trackWidth, trackHeight,
                    trackHeight * 0.5f, CarbonTheme.mix(CarbonTheme.TRACK_OFF, CarbonTheme.ACCENT_DEEP,
                            animatedValue));
            int knobSize = design(14);
            int knobTravel = Math.max(0, trackWidth - knobSize - design(4));
            int knobX = trackX + design(2) + Math.round(knobTravel * animatedValue);
            CarbonGlass.drawTintedRect(graphics, knobX, trackY + (trackHeight - knobSize) / 2,
                    knobSize, knobSize, knobSize * 0.5f,
                    CarbonTheme.mix(CarbonTheme.TEXT_MUTED, CarbonTheme.TEXT, animatedValue));

            beginTextLayer(graphics);
            if (getWidth() > design(72)) {
                drawLabel(graphics, label(), CarbonTheme.TEXT, design(10));
            }
        }

        private int design(int pixels) {
            return Math.max(1, Math.round(pixels * UiScale.rendererScale()));
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
            CarbonGlass.drawTintedRect(graphics, trackX, trackY, trackWidth, 3, 1.5f,
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
                CarbonGlass.drawTintedRect(graphics, trackX, trackY, fillWidth, 3, 1.5f,
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
            CarbonGlass.drawTintedRect(graphics, swatchX, swatchY, swatchWidth, 14, 5.0f, setting.get());
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
