package dev.carbon.client.ui;

import dev.carbon.client.core.config.ConfigManager;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Translucent, compact Carbon launcher-style popup for modules, profiles, and settings. */
public final class CarbonDemoScreen extends CarbonScreen {
    private static final Identifier CARBON_MARK = Identifier.fromNamespaceAndPath(
            "carbonclient", "textures/gui/carbon_mark.png");
    private static final int HEADER_HEIGHT = 56;
    private static final int CARD_GAP = 11;
    private static final int FILTER_GAP = 6;

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final ArrayList<FilterEntry> filterEntries = new ArrayList<>();
    private final ArrayList<ProfileEntry> profileEntries = new ArrayList<>();
    private final ArrayList<CarbonComponents.ModuleCard> moduleCards = new ArrayList<>();
    private final ArrayList<AbstractWidget> settingControls = new ArrayList<>();

    private CarbonComponents.Button modulesTab;
    private CarbonComponents.Button settingsTab;
    private CarbonComponents.Button closeButton;
    private CarbonComponents.Button saveProfileButton;
    private EditBox searchBox;

    private Category selectedCategory;
    private Module selectedModule;
    private View activeView = View.MODULES;
    private KeybindSetting capturingKeybind;
    private boolean suppressCapturedKey;
    private boolean suppressCapturedMouse;
    private String searchText = "";

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int sidebarWidth;
    private int sidebarX;
    private int sidebarY;
    private int sidebarHeight;
    private int contentX;
    private int contentWidth;
    private int toolbarY;
    private int cardsTop;
    private int gridColumns;
    private int gridRows;
    private int visibleGridRows;
    private int gridScrollRow;
    private int cardHeight;
    private int visibleModuleCount;
    private boolean stackedToolbar;

    public CarbonDemoScreen(ModuleManager modules, ConfigManager configManager, Screen parent) {
        super(Component.literal("Carbon Client"), parent);
        this.modules = modules;
        this.configManager = configManager;
    }

    @Override
    protected void init() {
        filterEntries.clear();
        profileEntries.clear();
        moduleCards.clear();
        settingControls.clear();
        calculateLayout();
        if (selectedModule == null) {
            selectedModule = firstModule();
        }

        addHeaderControls();
        addFilterControls();
        addSearchBox();
        rebuildProfileButtons();
        rebuildModuleCards(false);
        rebuildSettingControls();
        updateVisibility();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // A low-opacity scrim keeps the world visible through the native menu blur.
        CarbonRenderer.roundedRect(graphics, 0, 0, width, height, 0.0f, CarbonTheme.SCRIM);
        CarbonRenderer.roundedRect(graphics, panelX + 1, panelY + 5, panelWidth, panelHeight,
                18.0f, 0x4A000000);
        CarbonRenderer.outline(graphics, panelX, panelY, panelWidth, panelHeight,
                18.0f, CarbonTheme.BORDER, CarbonTheme.FRAME);
        CarbonRenderer.roundedRect(graphics, panelX + 1, panelY + 1, panelWidth - 2,
                HEADER_HEIGHT, 17.0f, 0xAA111915);
        CarbonRenderer.roundedRect(graphics, panelX + 12, sidebarY, sidebarWidth - 22,
                sidebarHeight, 13.0f, CarbonTheme.PANEL);
        CarbonRenderer.roundedRect(graphics, panelX + sidebarWidth, panelY + HEADER_HEIGHT,
                1, panelHeight - HEADER_HEIGHT - 1, 0.5f, CarbonTheme.BORDER_SOFT);

        if (activeView == View.MODULES) {
            drawModulesSurface(graphics);
        } else {
            drawSettingsSurface(graphics);
        }

        graphics.nextStratum();
        var font = Minecraft.getInstance().font;
        graphics.blit(CARBON_MARK, panelX + 17, panelY + 11, panelX + 50, panelY + 44,
                0.0f, 1.0f, 0.0f, 1.0f);
        CarbonText.draw(graphics, font, "CARBON CLIENT", panelX + 59, panelY + 15,
                CarbonTheme.TEXT, false);
        CarbonText.draw(graphics, font, "CLIENT CONTROL CENTER", panelX + 60, panelY + 32,
                CarbonTheme.TEXT_DIM, false);

        if (activeView == View.MODULES) {
            CarbonText.draw(graphics, font, "PROFILES", sidebarX + 12, sidebarY + 12,
                    CarbonTheme.TEXT_DIM, false);
        } else {
            CarbonText.draw(graphics, font, "MODULE SETTINGS", contentX + 20,
                    panelY + HEADER_HEIGHT + 20, CarbonTheme.TEXT_DIM, false);
            if (selectedModule != null) {
                CarbonText.draw(graphics, font, selectedModule.name(), contentX + 20,
                        panelY + HEADER_HEIGHT + 43, CarbonTheme.TEXT, false);
                CarbonText.draw(graphics, font, selectedModule.description(), contentX + 20,
                        panelY + HEADER_HEIGHT + 62, CarbonTheme.TEXT_MUTED, false);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeView != View.MODULES || gridRows <= visibleGridRows || verticalAmount == 0.0
                || mouseX < contentX || mouseX > contentX + contentWidth
                || mouseY < cardsTop || mouseY > panelY + panelHeight - 10) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int maximum = Math.max(0, gridRows - visibleGridRows);
        gridScrollRow = Math.max(0, Math.min(maximum, gridScrollRow + (verticalAmount < 0.0 ? 1 : -1)));
        layoutModuleCards();
        return true;
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

    private void calculateLayout() {
        int margin = Math.max(8, Math.min(32, Math.min(width, height) / 20));
        panelWidth = Math.max(1, Math.min(1120, width - margin * 2));
        panelHeight = Math.max(1, Math.min(760, height - margin * 2));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        sidebarWidth = Math.min(194, Math.max(90, panelWidth / 5));
        sidebarX = panelX + 11;
        sidebarY = panelY + HEADER_HEIGHT + 9;
        sidebarHeight = Math.max(1, panelHeight - HEADER_HEIGHT - 20);
        contentX = panelX + sidebarWidth + 18;
        contentWidth = Math.max(1, panelX + panelWidth - 17 - contentX);
        toolbarY = panelY + HEADER_HEIGHT + 9;
    }

    private void addHeaderControls() {
        int tabX = panelX + sidebarWidth + 44;
        int tabWidth = Math.min(108, Math.max(76, panelWidth / 9));
        int tabGap = 10;
        modulesTab = new CarbonComponents.Button(tabX, panelY + 15, tabWidth, 28,
                "MODULES", () -> setActiveView(View.MODULES), activeView == View.MODULES, true);
        settingsTab = new CarbonComponents.Button(tabX + tabWidth + tabGap, panelY + 15,
                tabWidth, 28, "SETTINGS", () -> setActiveView(View.SETTINGS),
                activeView == View.SETTINGS, true);
        closeButton = new CarbonComponents.Button(panelX + panelWidth - 46, panelY + 14,
                30, 30, "X", this::onClose, false, true);
        addRenderableWidget(modulesTab);
        addRenderableWidget(settingsTab);
        addRenderableWidget(closeButton);
    }

    private void addFilterControls() {
        ArrayList<FilterEntry> definitions = new ArrayList<>();
        definitions.add(new FilterEntry(null, "ALL", null));
        for (Category category : Category.values()) {
            if (hasModules(category)) {
                definitions.add(new FilterEntry(category, category.label().toUpperCase(Locale.ROOT), null));
            }
        }

        var font = Minecraft.getInstance().font;
        int requiredWidth = 0;
        for (int index = 0; index < definitions.size(); index++) {
            FilterEntry definition = definitions.get(index);
            int buttonWidth = Math.max(48, CarbonText.width(font, definition.label()) + 22);
            requiredWidth += buttonWidth + (index == 0 ? 0 : FILTER_GAP);
        }
        int searchWidth = Math.min(210, Math.max(96, contentWidth / 3));
        stackedToolbar = requiredWidth + searchWidth + 12 > contentWidth;
        int filterX = contentX;
        for (FilterEntry definition : definitions) {
            int buttonWidth = Math.max(48, CarbonText.width(font, definition.label()) + 22);
            CarbonComponents.Button button = new CarbonComponents.Button(
                    filterX, toolbarY, buttonWidth, 26, definition.label(),
                    () -> selectCategory(definition.category()), definition.category() == selectedCategory,
                    true);
            FilterEntry entry = new FilterEntry(definition.category(), definition.label(), button);
            filterEntries.add(entry);
            addRenderableWidget(button);
            filterX += buttonWidth + FILTER_GAP;
        }
    }

    private void addSearchBox() {
        int filterEnd = contentX;
        for (FilterEntry entry : filterEntries) {
            filterEnd = Math.max(filterEnd, entry.button().getX() + entry.button().getWidth());
        }
        int searchY = stackedToolbar ? toolbarY + 32 : toolbarY;
        int searchX = stackedToolbar ? contentX : filterEnd + 8;
        int searchWidth = Math.max(72, contentX + contentWidth - searchX);
        searchBox = new EditBox(Minecraft.getInstance().font, searchX + 7, searchY + 3,
                Math.max(30, searchWidth - 14), 19, CarbonText.component("SEARCH MODULES"));
        searchBox.setBordered(false);
        searchBox.setTextColor(CarbonTheme.TEXT);
        searchBox.setTextColorUneditable(CarbonTheme.TEXT_MUTED);
        searchBox.setHint(CarbonText.component("SEARCH MODULES"));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            searchText = value;
            rebuildModuleCards(true);
        });
        addRenderableWidget(searchBox);

        int toolbarRows = stackedToolbar ? 64 : 30;
        cardsTop = toolbarY + toolbarRows + 8;
    }

    private void rebuildProfileButtons() {
        for (ProfileEntry entry : profileEntries) {
            removeWidget(entry.button());
        }
        if (saveProfileButton != null) {
            removeWidget(saveProfileButton);
            saveProfileButton = null;
        }
        profileEntries.clear();
        if (configManager == null) {
            return;
        }

        List<String> profiles = configManager.listProfiles();
        int buttonY = sidebarY + 37;
        int buttonHeight = 27;
        int gap = 6;
        int maximumButtons = Math.max(1, (sidebarHeight - 86) / (buttonHeight + gap));
        int shown = Math.min(profiles.size(), maximumButtons);
        for (int index = 0; index < shown; index++) {
            String profile = profiles.get(index);
            String label = profile.equals("default") ? "DEFAULT" : profile.toUpperCase(Locale.ROOT);
            CarbonComponents.Button button = new CarbonComponents.Button(
                    sidebarX + 8, buttonY, sidebarWidth - 38, buttonHeight, label,
                    () -> selectProfile(profile), profile.equals(configManager.activeProfile()), false);
            profileEntries.add(new ProfileEntry(profile, button));
            addRenderableWidget(button);
            buttonY += buttonHeight + gap;
        }

        int saveY = sidebarY + sidebarHeight - 38;
        saveProfileButton = new CarbonComponents.Button(sidebarX + 8, saveY,
                sidebarWidth - 38, 28, "SAVE AS NEW", this::createProfile, false, true);
        saveProfileButton.visible = activeView == View.MODULES;
        addRenderableWidget(saveProfileButton);
    }

    private void rebuildModuleCards(boolean resetScroll) {
        for (CarbonComponents.ModuleCard card : moduleCards) {
            removeWidget(card);
        }
        moduleCards.clear();

        String query = searchText.trim().toLowerCase(Locale.ROOT);
        visibleModuleCount = 0;
        for (Module module : modules.modules()) {
            if (selectedCategory != null && module.category() != selectedCategory) {
                continue;
            }
            if (!query.isEmpty() && !module.name().toLowerCase(Locale.ROOT).contains(query)
                    && !module.description().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            visibleModuleCount++;
            Module selected = module;
            CarbonComponents.ModuleCard card = new CarbonComponents.ModuleCard(
                    0, 0, 180, 180, module, () -> openModuleSettings(selected));
            moduleCards.add(card);
            addRenderableWidget(card);
        }
        if (resetScroll) {
            gridScrollRow = 0;
        }
        layoutModuleCards();
        updateVisibility();
    }

    private void layoutModuleCards() {
        if (moduleCards.isEmpty()) {
            gridColumns = 1;
            gridRows = 0;
            visibleGridRows = 0;
            cardHeight = 0;
            return;
        }
        gridColumns = contentWidth >= 650 ? 3 : contentWidth >= 410 ? 2 : 1;
        gridRows = (moduleCards.size() + gridColumns - 1) / gridColumns;
        int availableHeight = Math.max(1, panelY + panelHeight - 14 - cardsTop);
        int targetVisibleRows = Math.min(2, gridRows);
        cardHeight = Math.max(146, Math.min(208,
                (availableHeight - (targetVisibleRows - 1) * CARD_GAP) / targetVisibleRows));
        visibleGridRows = Math.max(1, Math.min(gridRows,
                (availableHeight + CARD_GAP) / (cardHeight + CARD_GAP)));
        gridScrollRow = Math.max(0, Math.min(gridScrollRow, Math.max(0, gridRows - visibleGridRows)));
        for (int index = 0; index < moduleCards.size(); index++) {
            int row = index / gridColumns;
            int column = index % gridColumns;
            int cardWidth = Math.max(1, (contentWidth - CARD_GAP * (gridColumns - 1)) / gridColumns);
            int x = contentX + column * (cardWidth + CARD_GAP);
            int y = cardsTop + (row - gridScrollRow) * (cardHeight + CARD_GAP);
            CarbonComponents.ModuleCard card = moduleCards.get(index);
            card.setRectangle(cardWidth, cardHeight, x, y);
            card.visible = row >= gridScrollRow && row < gridScrollRow + visibleGridRows;
        }
    }

    private void rebuildSettingControls() {
        for (AbstractWidget control : settingControls) {
            removeWidget(control);
        }
        settingControls.clear();
        if (selectedModule == null) {
            selectedModule = firstModule();
        }
        if (selectedModule == null) {
            return;
        }

        int controlX = contentX + 20;
        int controlWidth = Math.max(90, Math.min(490, contentWidth - 40));
        int controlY = panelY + HEADER_HEIGHT + 94;
        for (Setting<?> setting : selectedModule.settings()) {
            AbstractWidget control = null;
            int controlHeight;
            if (setting instanceof NumberSetting numberSetting) {
                controlHeight = 40;
                control = new CarbonComponents.Slider(controlX, controlY, controlWidth, controlHeight,
                        numberSetting);
            } else if (setting instanceof BoolSetting boolSetting) {
                controlHeight = 32;
                control = new CarbonComponents.Toggle(controlX, controlY, controlWidth, controlHeight,
                        boolSetting);
            } else if (setting instanceof ModeSetting modeSetting) {
                controlHeight = 32;
                control = new CarbonComponents.ModeButton(controlX, controlY, controlWidth, controlHeight,
                        modeSetting);
            } else if (setting instanceof ColorSetting colorSetting) {
                controlHeight = 32;
                control = new CarbonComponents.ColorButton(controlX, controlY, controlWidth, controlHeight,
                        colorSetting);
            } else if (setting instanceof KeybindSetting keybindSetting) {
                controlHeight = 32;
                control = new CarbonComponents.KeybindButton(controlX, controlY, controlWidth, controlHeight,
                        keybindSetting, () -> beginKeybindCapture(keybindSetting));
            } else {
                continue;
            }
            settingControls.add(control);
            addRenderableWidget(control);
            controlY += controlHeight + 7;
        }
    }

    private void drawModulesSurface(GuiGraphicsExtractor graphics) {
        int searchWidth = Math.max(72, contentX + contentWidth - (stackedToolbar ? contentX : lastFilterEnd() + 8));
        int searchY = stackedToolbar ? toolbarY + 32 : toolbarY;
        int searchX = stackedToolbar ? contentX : lastFilterEnd() + 8;
        CarbonRenderer.roundedRect(graphics, searchX, searchY, searchWidth, 26,
                7.0f, CarbonTheme.PANEL_RAISED);
        CarbonRenderer.outline(graphics, searchX, searchY, searchWidth, 26,
                7.0f, CarbonTheme.BORDER_SOFT, CarbonTheme.PANEL_RAISED);
        if (visibleModuleCount == 0) {
            graphics.nextStratum();
            CarbonText.centered(graphics, Minecraft.getInstance().font, "NO MODULES FOUND",
                    contentX + contentWidth / 2, cardsTop + 24, CarbonTheme.TEXT_MUTED, false);
        }
    }

    private void drawSettingsSurface(GuiGraphicsExtractor graphics) {
        CarbonRenderer.roundedRect(graphics, contentX, toolbarY, contentWidth,
                panelY + panelHeight - toolbarY - 14, 14.0f, CarbonTheme.PANEL);
        CarbonRenderer.outline(graphics, contentX, toolbarY, contentWidth,
                panelY + panelHeight - toolbarY - 14, 14.0f, CarbonTheme.BORDER_SOFT, CarbonTheme.PANEL);
        if (selectedModule == null) {
            graphics.nextStratum();
            CarbonText.centered(graphics, Minecraft.getInstance().font, "NO MODULE SELECTED",
                    contentX + contentWidth / 2, toolbarY + 40, CarbonTheme.TEXT_MUTED, false);
        }
    }

    private int lastFilterEnd() {
        if (filterEntries.isEmpty()) {
            return contentX;
        }
        FilterEntry last = filterEntries.get(filterEntries.size() - 1);
        return last.button().getX() + last.button().getWidth();
    }

    private void selectCategory(Category category) {
        selectedCategory = category;
        for (FilterEntry entry : filterEntries) {
            entry.button().setAccent(entry.category() == category);
        }
        rebuildModuleCards(true);
    }

    private void selectProfile(String profile) {
        if (configManager == null || !configManager.switchProfile(profile)) {
            return;
        }
        for (ProfileEntry entry : profileEntries) {
            entry.button().setAccent(entry.name().equals(profile));
        }
    }

    private void createProfile() {
        if (configManager == null) {
            return;
        }
        List<String> profiles = configManager.listProfiles();
        String profileName = null;
        for (int index = 1; index <= 999; index++) {
            String candidate = "carbon-" + index;
            if (!profiles.contains(candidate)) {
                profileName = candidate;
                break;
            }
        }
        if (profileName != null && configManager.createProfile(profileName)) {
            rebuildProfileButtons();
            updateVisibility();
        }
    }

    private void setActiveView(View view) {
        if (activeView == view) {
            return;
        }
        activeView = view;
        modulesTab.setAccent(view == View.MODULES);
        settingsTab.setAccent(view == View.SETTINGS);
        updateVisibility();
    }

    private void openModuleSettings(Module module) {
        selectedModule = module;
        setActiveView(View.SETTINGS);
        rebuildSettingControls();
    }

    private void updateVisibility() {
        boolean showingModules = activeView == View.MODULES;
        for (FilterEntry entry : filterEntries) {
            entry.button().visible = showingModules;
        }
        if (showingModules) {
            layoutModuleCards();
        } else {
            for (CarbonComponents.ModuleCard card : moduleCards) {
                card.visible = false;
            }
        }
        if (searchBox != null) {
            searchBox.visible = showingModules;
        }
        if (saveProfileButton != null) {
            saveProfileButton.visible = showingModules;
        }
        for (AbstractWidget control : settingControls) {
            control.visible = !showingModules;
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

    private Module firstModule() {
        for (Module module : modules.modules()) {
            return module;
        }
        return null;
    }

    private record FilterEntry(Category category, String label, CarbonComponents.Button button) {
    }

    private record ProfileEntry(String name, CarbonComponents.Button button) {
    }

    private enum View {
        MODULES,
        SETTINGS
    }
}
