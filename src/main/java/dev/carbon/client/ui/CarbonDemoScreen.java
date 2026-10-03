package dev.carbon.client.ui;

import dev.carbon.client.core.event.KeyInputEvent;
import dev.carbon.client.core.event.MouseInputEvent;
import dev.carbon.client.core.module.Category;
import dev.carbon.client.core.module.Module;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.core.setting.BoolSetting;
import dev.carbon.client.core.setting.ColorSetting;
import dev.carbon.client.core.setting.KeybindSetting;
import dev.carbon.client.core.setting.ModeSetting;
import dev.carbon.client.core.setting.NumberSetting;
import dev.carbon.client.core.setting.Setting;
import dev.carbon.client.ui.render.CarbonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

/** A live demo/configuration screen bound to Carbon's real module and setting objects. */
public final class CarbonDemoScreen extends CarbonScreen {
    private final ModuleManager modules;
    private final ArrayList<CategoryEntry> categoryEntries = new ArrayList<>();
    private final ArrayList<ModuleEntry> moduleEntries = new ArrayList<>();
    private final ArrayList<AbstractWidget> settingControls = new ArrayList<>();

    private Category selectedCategory;
    private Module selectedModule;
    private KeybindSetting capturingKeybind;
    private boolean suppressCapturedKey;
    private boolean suppressCapturedMouse;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int sidebarWidth;
    private int listX;
    private int listWidth;
    private int settingsX;
    private int settingsWidth;
    private int contentY;

    public CarbonDemoScreen(ModuleManager modules, Screen parent) {
        super(Component.literal("Carbon Client"), parent);
        this.modules = modules;
    }

    @Override
    protected void init() {
        categoryEntries.clear();
        moduleEntries.clear();
        settingControls.clear();

        panelWidth = Math.min(700, Math.max(320, width - 32));
        panelWidth = Math.min(panelWidth, width);
        panelHeight = Math.min(420, Math.max(240, height - 32));
        panelHeight = Math.min(panelHeight, height);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        sidebarWidth = Math.min(142, Math.max(108, panelWidth / 5));
        int bodyX = panelX + sidebarWidth + 18;
        int bodyWidth = Math.max(120, panelWidth - sidebarWidth - 36);
        int columnGap = 12;
        listWidth = Math.max(96, (bodyWidth - columnGap) * 46 / 100);
        int settingsPanelWidth = Math.max(100, bodyWidth - listWidth - columnGap);
        listX = bodyX;
        settingsWidth = settingsPanelWidth - 16;
        settingsX = bodyX + listWidth + columnGap + 8;
        contentY = panelY + 80;

        if (selectedCategory == null || !hasModules(selectedCategory)) {
            selectedCategory = firstCategory();
        }
        if (selectedModule == null || selectedModule.category() != selectedCategory) {
            selectedModule = firstModule(selectedCategory);
        }

        int categoryY = panelY + 88;
        for (Category category : Category.values()) {
            if (!hasModules(category)) {
                continue;
            }
            Category capturedCategory = category;
            CarbonComponents.Button button = new CarbonComponents.Button(
                    panelX + 12, categoryY, sidebarWidth - 24, 28, category.label(),
                    () -> selectCategory(capturedCategory), category == selectedCategory, false
            );
            categoryEntries.add(new CategoryEntry(category, button));
            addRenderableWidget(button);
            categoryY += 35;
        }

        int[] categoryRows = new int[Category.values().length];
        int rowTop = contentY + 20;
        for (Module module : modules.modules()) {
            int rowIndex = categoryRows[module.category().ordinal()]++;
            int rowY = rowTop + rowIndex * 46;
            int rowWidth = Math.max(64, listWidth - 46);
            Module capturedModule = module;
            CarbonComponents.Button row = new CarbonComponents.Button(
                    listX, rowY, rowWidth, 36, module.name(),
                    () -> selectModule(capturedModule), module == selectedModule, false
            );
            CarbonComponents.Toggle toggle = new CarbonComponents.Toggle(
                    listX + rowWidth + 6, rowY, 40, 36, module
            );
            boolean visible = module.category() == selectedCategory;
            row.visible = visible;
            toggle.visible = visible;
            moduleEntries.add(new ModuleEntry(module, row, toggle));
            addRenderableWidget(row);
            addRenderableWidget(toggle);
        }

        CarbonComponents.Button closeButton = new CarbonComponents.Button(
                panelX + panelWidth - 82, panelY + 14, 68, 24, "Close", this::onClose, false, true
        );
        addRenderableWidget(closeButton);
        rebuildSettingControls();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        CarbonRenderer.roundedRect(graphics, 0, 0, width, height, 0.0f, CarbonTheme.SCRIM);
        CarbonRenderer.roundedRect(graphics, panelX, panelY + 5, panelWidth, panelHeight,
                16.0f, 0x66000000);
        CarbonRenderer.roundedRect(graphics, panelX, panelY, panelWidth, panelHeight,
                16.0f, CarbonTheme.FRAME);
        CarbonRenderer.roundedRect(graphics, panelX + 1, panelY + 1, panelWidth - 2, 48,
                15.0f, CarbonTheme.PANEL);
        CarbonRenderer.roundedRect(graphics, panelX + 10, panelY + 58, sidebarWidth,
                panelHeight - 70, 12.0f, CarbonTheme.PANEL);
        CarbonRenderer.roundedRect(graphics, settingsX, panelY + 66, settingsWidth + 16,
                panelHeight - 78, 12.0f, CarbonTheme.PANEL);

        graphics.nextStratum();
        var font = Minecraft.getInstance().font;
        graphics.text(font, "CARBON", panelX + 20, panelY + 12, CarbonTheme.ACCENT, false);
        graphics.text(font, "Client interface", panelX + 20, panelY + 27, CarbonTheme.TEXT_MUTED, false);
        graphics.text(font, "MODULES", panelX + 22, panelY + 68, CarbonTheme.TEXT_DIM, false);
        graphics.text(font, selectedCategory == null ? "Modules" : selectedCategory.label(),
                listX, panelY + 62, CarbonTheme.TEXT, false);
        graphics.text(font, "SETTINGS", settingsX + 10, panelY + 76, CarbonTheme.TEXT_DIM, false);

        if (selectedModule != null) {
            graphics.text(font, selectedModule.name(), settingsX + 10, panelY + 94,
                    CarbonTheme.TEXT, false);
        } else {
            graphics.text(font, "No modules registered", listX, panelY + 88,
                    CarbonTheme.TEXT_MUTED, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    public void beginKeybindCapture(KeybindSetting setting) {
        capturingKeybind = setting;
        for (AbstractWidget control : settingControls) {
            if (control instanceof CarbonComponents.KeybindButton button) {
                button.setListening(button.setting() == setting);
            }
        }
    }

    public boolean captureKey(KeyInputEvent event) {
        if (capturingKeybind == null || event.action() != GLFW.GLFW_PRESS) {
            return false;
        }
        KeybindSetting target = capturingKeybind;
        target.set(event.keyCode() == GLFW.GLFW_KEY_ESCAPE
                ? KeybindSetting.Binding.unboundBinding()
                : KeybindSetting.Binding.keyboard(event.keyCode()));
        capturingKeybind = null;
        suppressCapturedKey = true;
        updateKeybindButtons();
        return true;
    }

    public boolean captureMouse(MouseInputEvent event) {
        if (capturingKeybind == null || event.action() != GLFW.GLFW_PRESS) {
            return false;
        }
        capturingKeybind.set(KeybindSetting.Binding.mouseButton(event.button()));
        capturingKeybind = null;
        suppressCapturedMouse = true;
        updateKeybindButtons();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (suppressCapturedKey) {
            suppressCapturedKey = false;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (suppressCapturedMouse) {
            suppressCapturedMouse = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void updateKeybindButtons() {
        for (AbstractWidget control : settingControls) {
            if (control instanceof CarbonComponents.KeybindButton button) {
                button.setListening(button.setting() == capturingKeybind);
            }
        }
    }

    private void selectCategory(Category category) {
        selectedCategory = category;
        if (selectedModule == null || selectedModule.category() != category) {
            selectedModule = firstModule(category);
        }
        for (CategoryEntry entry : categoryEntries) {
            entry.button().setAccent(entry.category() == category);
        }
        for (ModuleEntry entry : moduleEntries) {
            boolean visible = entry.module().category() == category;
            entry.row().visible = visible;
            entry.toggle().visible = visible;
            entry.row().setAccent(entry.module() == selectedModule);
        }
        rebuildSettingControls();
    }

    private void selectModule(Module module) {
        selectedModule = module;
        for (ModuleEntry entry : moduleEntries) {
            entry.row().setAccent(entry.module() == module);
        }
        rebuildSettingControls();
    }

    private void rebuildSettingControls() {
        capturingKeybind = null;
        suppressCapturedKey = false;
        suppressCapturedMouse = false;
        for (AbstractWidget control : settingControls) {
            removeWidget(control);
        }
        settingControls.clear();
        if (selectedModule == null) {
            return;
        }

        int controlX = settingsX + 8;
        int controlWidth = Math.max(72, settingsWidth - 16);
        int controlY = panelY + 116;
        for (Setting<?> setting : selectedModule.settings()) {
            AbstractWidget control = null;
            int controlHeight = 34;
            if (setting instanceof NumberSetting numberSetting) {
                control = new CarbonComponents.Slider(controlX, controlY, controlWidth, 40, numberSetting);
                controlHeight = 40;
            } else if (setting instanceof BoolSetting boolSetting) {
                control = new CarbonComponents.Toggle(controlX, controlY, controlWidth, 32, boolSetting);
                controlHeight = 32;
            } else if (setting instanceof ModeSetting modeSetting) {
                control = new CarbonComponents.ModeButton(controlX, controlY, controlWidth, 32, modeSetting);
                controlHeight = 32;
            } else if (setting instanceof ColorSetting colorSetting) {
                control = new CarbonComponents.ColorButton(controlX, controlY, controlWidth, 32, colorSetting);
                controlHeight = 32;
            } else if (setting instanceof KeybindSetting keybindSetting) {
                control = new CarbonComponents.KeybindButton(controlX, controlY, controlWidth, 32,
                        keybindSetting, () -> beginKeybindCapture(keybindSetting));
                controlHeight = 32;
            }

            if (control != null) {
                settingControls.add(control);
                addRenderableWidget(control);
                controlY += controlHeight + 7;
            }
        }
    }

    private boolean hasModules(Category category) {
        for (Module module : modules.modules()) {
            if (module.category() == category) {
                return true;
            }
        }
        return false;
    }

    private Category firstCategory() {
        for (Category category : Category.values()) {
            if (hasModules(category)) {
                return category;
            }
        }
        return null;
    }

    private Module firstModule(Category category) {
        if (category == null) {
            return null;
        }
        for (Module module : modules.modules()) {
            if (module.category() == category) {
                return module;
            }
        }
        return null;
    }

    private record CategoryEntry(Category category, CarbonComponents.Button button) {
    }

    private record ModuleEntry(Module module, CarbonComponents.Button row, CarbonComponents.Toggle toggle) {
    }
}
