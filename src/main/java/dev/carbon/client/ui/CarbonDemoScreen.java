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
import dev.carbon.client.ui.render.CarbonGlass;
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

/** Carbon's translucent PC module menu with sidebar navigation and live module cards. */
public final class CarbonDemoScreen extends CarbonScreen {
    private static final Identifier CARBON_MARK = Identifier.fromNamespaceAndPath(
            "carbonclient", "textures/gui/carbon_mark.png");

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final ArrayList<FilterEntry> filterEntries = new ArrayList<>();
    private final ArrayList<ProfileEntry> profileEntries = new ArrayList<>();
    private final ArrayList<CarbonComponents.ModuleCard> moduleCards = new ArrayList<>();
    private final ArrayList<AbstractWidget> settingControls = new ArrayList<>();

    private CarbonComponents.NavButton modulesTab;
    private CarbonComponents.NavButton settingsTab;
    private CarbonComponents.IconButton closeButton;
    private CarbonComponents.Button saveProfileButton;
    private EditBox searchBox;

    private Category selectedCategory;
    private Module selectedModule;
    private View activeView = View.MODULES;
    private KeybindSetting capturingKeybind;
    private boolean suppressCapturedKey;
    private boolean suppressCapturedMouse;
    private String searchText = "";
    private String screenTitle = "All Modules";

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
    private float layoutScale = 1.0f;

    public CarbonDemoScreen(ModuleManager modules, ConfigManager configManager, Screen parent) {
        super(Component.literal("Carbon Client"), parent);
        this.modules = modules;
        this.configManager = configManager;
    }

    @Override
    protected void init() {
        UiScale.update(Minecraft.getInstance());
        layoutScale = UiScale.rendererScale();
        CarbonIcons.load();
        filterEntries.clear();
        profileEntries.clear();
        moduleCards.clear();
        settingControls.clear();
        calculateLayout();
        if (selectedModule == null) {
            selectedModule = firstModule();
        }
        updateScreenTitle();

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
        UiScale.update(Minecraft.getInstance());
        layoutScale = UiScale.rendererScale();

        // The module menu intentionally has no animated test-pattern layer.
        graphics.nextStratum();
        CarbonGlass.drawTintedRect(graphics, 0, 0, width, height, 0.0f, 0x26050A08);
        UiScale.pushRendererScale(graphics);
        CarbonGlass.drawPanel(graphics,
                panelX / layoutScale, panelY / layoutScale,
                panelWidth / layoutScale, panelHeight / layoutScale,
                20.0f, CarbonGlass.Style.MAIN);
        CarbonGlass.drawTintedRectDesign(graphics,
                panelX / layoutScale, panelY / layoutScale,
                sidebarWidth / layoutScale, panelHeight / layoutScale,
                20.0f, 0x54141D18);
        CarbonGlass.drawTintedRectDesign(graphics,
                (panelX + sidebarWidth) / layoutScale, panelY / layoutScale,
                1.0f, panelHeight / layoutScale, 0.5f, 0x287D9C86);
        UiScale.popRendererScale(graphics);

        graphics.nextStratum();
        if (activeView == View.MODULES) {
            drawModulesSurface(graphics);
        } else {
            drawSettingsSurface(graphics);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.nextStratum();
        var font = Minecraft.getInstance().font;
        int markSize = design(28);
        int markX = panelX + design(24);
        int markY = panelY + design(22);
        graphics.blit(CARBON_MARK, markX, markY, markX + markSize, markY + markSize,
                0.0f, 1.0f, 0.0f, 1.0f);
        CarbonText.drawUi(graphics, font, "CARBON", CarbonText.Weight.BOLD,
                15.0f, markX + markSize + design(10), panelY + design(28), CarbonTheme.TEXT, false);
        CarbonGlass.drawTintedRect(graphics, panelX + design(132), panelY + design(29),
                design(6), design(6), design(3), CarbonTheme.ACCENT);

        CarbonText.drawUi(graphics, font, screenTitle, CarbonText.Weight.SEMIBOLD,
                22.0f, contentX + design(24), panelY + design(27), CarbonTheme.TEXT, false);
        if (activeView == View.MODULES) {
            CarbonText.drawUi(graphics, font, "PROFILES", CarbonText.Weight.MEDIUM,
                    11.0f, panelX + design(24), panelY + design(202), CarbonTheme.TEXT_DIM, false);
        }
        CarbonText.drawUi(graphics, font, "v1.0.0", CarbonText.Weight.REGULAR,
                11.0f, panelX + design(24), panelY + panelHeight - design(28),
                CarbonTheme.TEXT_DIM, false);

        if (searchBox != null && activeView == View.MODULES) {
            int iconSize = design(16);
            int iconX = panelX + panelWidth - design(260);
            int iconY = panelY + design(30);
            CarbonIcons.drawGui(graphics, "search", iconX, iconY, iconSize, CarbonTheme.TEXT_MUTED);
        }
        CarbonGlass.drawFailureLabelGui(graphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeView != View.MODULES || gridRows <= visibleGridRows || verticalAmount == 0.0
                || mouseX < contentX || mouseX > contentX + contentWidth
                || mouseY < cardsTop || mouseY > panelY + panelHeight - design(20)) {
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

    private void updateKeybindButtons() {
        for (AbstractWidget control : settingControls) {
            if (control instanceof CarbonComponents.KeybindButton button) {
                button.setListening(button.setting() == capturingKeybind);
            }
        }
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
        UiScale.update(Minecraft.getInstance());
        layoutScale = UiScale.rendererScale();
        int horizontalMargin = design(32);
        int verticalMargin = design(32);
        panelWidth = Math.max(1, Math.min(design(1000), width - horizontalMargin));
        panelHeight = Math.max(1, Math.min(design(620), height - verticalMargin));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        sidebarWidth = Math.min(design(188), Math.max(design(90), panelWidth / 3));
        sidebarX = panelX;
        sidebarY = panelY;
        sidebarHeight = panelHeight;
        contentX = panelX + design(212);
        contentWidth = Math.max(1, panelX + panelWidth - design(20) - contentX);
        toolbarY = panelY + design(76);
    }

    private int design(int pixels) {
        return Math.max(1, Math.round(pixels * layoutScale));
    }

    private void addHeaderControls() {
        int navX = panelX + design(12);
        int navWidth = Math.min(design(164), sidebarWidth - design(24));
        int navHeight = design(40);
        modulesTab = new CarbonComponents.NavButton(navX, panelY + design(84), navWidth, navHeight,
                "layout-grid", "Modules", () -> setActiveView(View.MODULES), activeView == View.MODULES);
        settingsTab = new CarbonComponents.NavButton(navX, panelY + design(128), navWidth, navHeight,
                "settings", "Settings", () -> setActiveView(View.SETTINGS), activeView == View.SETTINGS);
        int closeSize = design(28);
        closeButton = new CarbonComponents.IconButton(
                panelX + panelWidth - design(42), panelY + design(22), closeSize, closeSize,
                "x", "Close Carbon menu", this::onClose);
        addRenderableWidget(modulesTab);
        addRenderableWidget(settingsTab);
        addRenderableWidget(closeButton);
    }

    private void addFilterControls() {
        ArrayList<FilterEntry> definitions = new ArrayList<>();
        definitions.add(new FilterEntry(null, "All", null));
        for (Category category : Category.values()) {
            definitions.add(new FilterEntry(category, category.label(), null));
        }

        var font = Minecraft.getInstance().font;
        int filterX = contentX;
        int filterY = panelY + design(76);
        int filterHeight = design(32);
        for (FilterEntry definition : definitions) {
            int buttonWidth = Math.max(design(48), CarbonText.width(font, definition.label()) + design(28));
            CarbonComponents.Button button = new CarbonComponents.Button(
                    filterX, filterY, buttonWidth, filterHeight, definition.label(),
                    () -> selectCategory(definition.category()), definition.category() == selectedCategory,
                    true);
            FilterEntry entry = new FilterEntry(definition.category(), definition.label(), button);
            filterEntries.add(entry);
            addRenderableWidget(button);
            filterX += buttonWidth + design(8);
        }
    }

    private void addSearchBox() {
        int searchX = panelX + panelWidth - design(300);
        int searchY = panelY + design(20);
        int searchWidth = design(240);
        int searchHeight = design(36);
        searchBox = new EditBox(Minecraft.getInstance().font,
                searchX + design(36), searchY + design(4),
                Math.max(1, searchWidth - design(48)), Math.max(1, searchHeight - design(8)),
                CarbonText.component("Search modules"));
        searchBox.setBordered(false);
        searchBox.setTextColor(CarbonTheme.TEXT);
        searchBox.setTextColorUneditable(CarbonTheme.TEXT_MUTED);
        searchBox.setHint(CarbonText.component("Search modules"));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            searchText = value;
            rebuildModuleCards(true);
        });
        addRenderableWidget(searchBox);
        cardsTop = panelY + design(124);
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
        int buttonY = panelY + design(222);
        int buttonHeight = design(30);
        int gap = design(6);
        int profileBottom = panelY + panelHeight - design(104);
        int maximumButtons = Math.max(1, (profileBottom - buttonY) / Math.max(1, buttonHeight + gap));
        int shown = Math.min(profiles.size(), maximumButtons);
        for (int index = 0; index < shown; index++) {
            String profile = profiles.get(index);
            String label = profile.equals("default") ? "DEFAULT" : profile.toUpperCase(Locale.ROOT);
            CarbonComponents.Button button = new CarbonComponents.Button(
                    panelX + design(12), buttonY, sidebarWidth - design(24), buttonHeight, label,
                    () -> selectProfile(profile), profile.equals(configManager.activeProfile()), false);
            profileEntries.add(new ProfileEntry(profile, button));
            addRenderableWidget(button);
            buttonY += buttonHeight + gap;
        }

        int saveY = panelY + panelHeight - design(60);
        saveProfileButton = new CarbonComponents.Button(panelX + design(12), saveY,
                sidebarWidth - design(24), design(32), "NEW PROFILE", this::createProfile, false, true);
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
                    0, 0, design(240), design(96), module, () -> openModuleSettings(selected));
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

        int gap = design(22);
        int preferredCardWidth = design(240);
        int minimumCardWidth = design(196);
        gridColumns = Math.max(1, Math.min(3,
                (contentWidth + gap) / Math.max(1, minimumCardWidth + gap)));
        while (gridColumns > 1
                && (contentWidth - gap * (gridColumns - 1)) / gridColumns < minimumCardWidth) {
            gridColumns--;
        }
        gridRows = (moduleCards.size() + gridColumns - 1) / gridColumns;
        int availableHeight = Math.max(1, panelY + panelHeight - design(20) - cardsTop);
        cardHeight = Math.max(1, Math.min(design(96), availableHeight));
        visibleGridRows = Math.max(1, Math.min(gridRows,
                (availableHeight + gap) / Math.max(1, cardHeight + gap)));
        gridScrollRow = Math.max(0, Math.min(gridScrollRow, Math.max(0, gridRows - visibleGridRows)));

        int cardWidth = Math.max(1,
                (contentWidth - gap * (gridColumns - 1)) / gridColumns);
        if (gridColumns == 1) {
            cardWidth = Math.min(contentWidth, Math.max(minimumCardWidth, preferredCardWidth));
        }
        for (int index = 0; index < moduleCards.size(); index++) {
            int row = index / gridColumns;
            int column = index % gridColumns;
            int x = contentX + column * (cardWidth + gap);
            int y = cardsTop + (row - gridScrollRow) * (cardHeight + gap);
            CarbonComponents.ModuleCard card = moduleCards.get(index);
            card.setRectangle(cardWidth, cardHeight, x, y);
            card.updateDescriptionWidth(Math.max(1, cardWidth - design(112)));
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

        int controlX = contentX + design(20);
        int controlWidth = Math.max(design(90), Math.min(design(490), contentWidth - design(40)));
        int controlY = panelY + design(124);
        for (Setting<?> setting : selectedModule.settings()) {
            AbstractWidget control = null;
            int controlHeight;
            if (setting instanceof NumberSetting numberSetting) {
                controlHeight = design(40);
                control = new CarbonComponents.Slider(controlX, controlY, controlWidth, controlHeight,
                        numberSetting);
            } else if (setting instanceof BoolSetting boolSetting) {
                controlHeight = design(32);
                control = new CarbonComponents.Toggle(controlX, controlY, controlWidth, controlHeight,
                        boolSetting);
            } else if (setting instanceof ModeSetting modeSetting) {
                controlHeight = design(32);
                control = new CarbonComponents.ModeButton(controlX, controlY, controlWidth, controlHeight,
                        modeSetting);
            } else if (setting instanceof ColorSetting colorSetting) {
                controlHeight = design(32);
                control = new CarbonComponents.ColorButton(controlX, controlY, controlWidth, controlHeight,
                        colorSetting);
            } else if (setting instanceof KeybindSetting keybindSetting) {
                controlHeight = design(32);
                control = new CarbonComponents.KeybindButton(controlX, controlY, controlWidth, controlHeight,
                        keybindSetting, () -> beginKeybindCapture(keybindSetting));
            } else {
                continue;
            }
            settingControls.add(control);
            addRenderableWidget(control);
            controlY += controlHeight + design(7);
        }
    }

    private void drawModulesSurface(GuiGraphicsExtractor graphics) {
        int searchX = panelX + panelWidth - design(300);
        int searchY = panelY + design(20);
        CarbonGlass.drawTintedRect(graphics, searchX, searchY, design(240), design(36),
                design(18), 0x5B1B2821);
        CarbonGlass.outlineTintedRect(graphics, searchX, searchY, design(240), design(36),
                design(18), 0x447D9C86, 0x4B1B2821);
        if (visibleModuleCount == 0) {
            graphics.nextStratum();
            CarbonText.centered(graphics, Minecraft.getInstance().font, "No modules found",
                    contentX + contentWidth / 2, cardsTop + design(24), CarbonTheme.TEXT_MUTED, false);
        }

        if (gridRows > visibleGridRows && visibleGridRows > 0) {
            int trackX = panelX + panelWidth - design(9);
            int trackY = cardsTop;
            int trackHeight = Math.max(1, panelY + panelHeight - design(20) - cardsTop);
            int thumbHeight = Math.max(design(24), trackHeight * visibleGridRows / gridRows);
            int maxScroll = Math.max(1, gridRows - visibleGridRows);
            int thumbY = trackY + (trackHeight - thumbHeight) * gridScrollRow / maxScroll;
            CarbonGlass.drawTintedRect(graphics, trackX, trackY, design(4), trackHeight,
                    design(2), 0x3EFFFFFF);
            CarbonGlass.drawTintedRect(graphics, trackX, thumbY, design(4), thumbHeight,
                    design(2), 0xB833D889);
        }
    }

    private void drawSettingsSurface(GuiGraphicsExtractor graphics) {
        int surfaceY = panelY + design(76);
        int surfaceHeight = Math.max(1, panelY + panelHeight - design(16) - surfaceY);
        CarbonGlass.drawPanelGui(graphics, contentX, surfaceY, contentWidth, surfaceHeight,
                16.0f, CarbonGlass.Style.CARD_OFF);
        if (selectedModule == null) {
            graphics.nextStratum();
            CarbonText.centered(graphics, Minecraft.getInstance().font, "No module selected",
                    contentX + contentWidth / 2, surfaceY + design(40), CarbonTheme.TEXT_MUTED, false);
        }
    }

    private void updateScreenTitle() {
        if (activeView == View.SETTINGS) {
            screenTitle = "Module Settings";
        } else if (selectedCategory == null) {
            screenTitle = "All Modules";
        } else {
            screenTitle = selectedCategory.label() + " Modules";
        }
    }

    private void selectCategory(Category category) {
        selectedCategory = category;
        updateScreenTitle();
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
        updateScreenTitle();
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
        if (modulesTab != null) {
            modulesTab.setAccent(showingModules);
        }
        if (settingsTab != null) {
            settingsTab.setAccent(!showingModules);
        }
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
        for (ProfileEntry entry : profileEntries) {
            entry.button().visible = showingModules;
        }
        if (saveProfileButton != null) {
            saveProfileButton.visible = showingModules;
        }
        for (AbstractWidget control : settingControls) {
            control.visible = !showingModules;
        }
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
