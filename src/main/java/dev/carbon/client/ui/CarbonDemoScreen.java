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
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Carbon's animated, high-contrast PC module menu with real module, profile and preset controls. */
public final class CarbonDemoScreen extends CarbonScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Identifier CARBON_MARK = Identifier.fromNamespaceAndPath(
            "carbonclient", "textures/gui/carbon_mark.png");
    private static final List<Preset> PRESETS = List.of(
            new Preset("Minimal", "A quiet screen with FPS only.", Map.of(
                    "fps", true, "cps", false, "keystrokes", false, "zoom", false)),
            new Preset("Creator", "FPS, CPS and movement keys.", Map.of(
                    "fps", true, "cps", true, "keystrokes", true, "zoom", false)),
            new Preset("PvP", "Full HUD plus hold-to-zoom.", Map.of(
                    "fps", true, "cps", true, "keystrokes", true, "zoom", true))
    );

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final ArrayList<FilterEntry> filterEntries = new ArrayList<>();
    private final ArrayList<CarbonComponents.ModuleCard> moduleCards = new ArrayList<>();
    private final ArrayList<AbstractWidget> settingControls = new ArrayList<>();

    private CarbonComponents.NavButton modulesTab;
    private CarbonComponents.NavButton settingsTab;
    private CarbonComponents.IconButton closeButton;
    private CarbonComponents.Button presetChoiceButton;
    private CarbonComponents.Button applyPresetButton;
    private CarbonComponents.Button profileSelectButton;
    private CarbonComponents.Button createProfileButton;
    private CarbonComponents.Button resetSettingsButton;
    private CarbonComponents.Toggle moduleEnabledControl;
    private EditBox searchBox;

    private Category selectedCategory;
    private Module selectedModule;
    private View activeView = View.MODULES;
    private KeybindSetting capturingKeybind;
    private boolean suppressCapturedKey;
    private boolean suppressCapturedMouse;
    private boolean avatarFailureLogged;
    private String searchText = "";
    private String screenTitle = "All Modules";
    private String currentUsername = "Player";
    private String toastMessage;
    private int presetIndex = 1;
    private long screenOpenedAtNanos;
    private long toastStartedAtNanos;

    private Model.Simple avatarHeadModel;
    private Identifier avatarSkinTexture;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int sidebarWidth;
    private int contentX;
    private int contentWidth;
    private int cardsTop;
    private int avatarX;
    private int avatarY;
    private int avatarSize;
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
        screenOpenedAtNanos = System.nanoTime();
        UiScale.update(Minecraft.getInstance());
        layoutScale = UiScale.rendererScale();
        CarbonIcons.load();
        filterEntries.clear();
        moduleCards.clear();
        settingControls.clear();
        calculateLayout();
        initializeAvatar();
        if (selectedModule == null) {
            selectedModule = firstModule();
        }
        updateScreenTitle();

        addHeaderControls();
        addFilterControls();
        addSearchBox();
        addPresetControls();
        rebuildProfileControls();
        rebuildModuleCards(false);
        rebuildSettingControls();
        updateVisibility();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiScale.update(Minecraft.getInstance());
        layoutScale = UiScale.rendererScale();
        float entry = CarbonAnimation.easeOutCubic(
                (System.nanoTime() - screenOpenedAtNanos) / 360_000_000.0f);

        // Darken, but do not replace, the blurred world backdrop. The moving test pattern stays F8-only.
        graphics.nextStratum();
        CarbonGlass.drawTintedRect(graphics, 0, 0, width, height, 0.0f, 0x62050A08);

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        float centerX = panelX + panelWidth * 0.5f;
        float centerY = panelY + panelHeight * 0.5f;
        float panelScale = 0.985f + 0.015f * entry;
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(panelScale, panelScale);
        graphics.pose().translate(-centerX, -centerY + (1.0f - entry) * design(14));

        CarbonGlass.drawPanelGui(graphics, panelX, panelY, panelWidth, panelHeight,
                22.0f, CarbonGlass.Style.MAIN);
        CarbonGlass.drawTintedRect(graphics, panelX, panelY, sidebarWidth, panelHeight,
                22.0f, 0x68101813);
        CarbonGlass.drawTintedRect(graphics, panelX + sidebarWidth - design(1), panelY + design(22),
                design(1), panelHeight - design(44), 0.5f, 0x4B7D9C86);

        graphics.nextStratum();
        drawSidebarSurfaces(graphics);
        if (activeView == View.MODULES) {
            drawModulesSurface(graphics);
        } else {
            drawSettingsSurface(graphics);
        }

        graphics.nextStratum();
        drawProfileAvatar(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.nextStratum();
        drawBrandAndSidebarText(graphics);
        drawMainHeader(graphics);
        drawPresetDescription(graphics);
        drawSettingsHeader(graphics);
        drawToast(graphics);

        graphics.pose().popMatrix();
        CarbonGlass.drawFailureLabelGui(graphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeView != View.MODULES || gridRows <= visibleGridRows || verticalAmount == 0.0
                || mouseX < contentX || mouseX > contentX + contentWidth
                || mouseY < cardsTop || mouseY > panelY + panelHeight - design(42)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int maximum = Math.max(0, gridRows - visibleGridRows);
        gridScrollRow = Math.max(0, Math.min(maximum,
                gridScrollRow + (verticalAmount < 0.0 ? 1 : -1)));
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
        UiScale.update(Minecraft.getInstance());
        layoutScale = UiScale.rendererScale();
        int horizontalMargin = design(40);
        int verticalMargin = design(40);
        panelWidth = Math.max(1, Math.min(design(1160), width - horizontalMargin));
        panelHeight = Math.max(1, Math.min(design(720), height - verticalMargin));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        sidebarWidth = Math.min(design(244), Math.max(design(176), panelWidth / 3));
        sidebarWidth = Math.min(sidebarWidth, Math.max(1, panelWidth - design(260)));
        contentX = panelX + sidebarWidth + design(26);
        contentWidth = Math.max(1, panelX + panelWidth - design(28) - contentX);
        avatarX = panelX + design(18);
        avatarY = panelY + design(85);
        avatarSize = design(52);
        cardsTop = panelY + design(152);
    }

    private void initializeAvatar() {
        Minecraft client = Minecraft.getInstance();
        if (client.getUser() != null && client.getUser().getName() != null
                && !client.getUser().getName().isBlank()) {
            currentUsername = client.getUser().getName();
        }
        if (client.player == null) {
            avatarHeadModel = null;
            avatarSkinTexture = null;
            return;
        }
        try {
            avatarSkinTexture = client.player.getSkin().body().texturePath();
            avatarHeadModel = new Model.Simple(
                    client.getEntityModels().bakeLayer(ModelLayers.PLAYER_HEAD), RenderTypes::entityCutout);
            avatarFailureLogged = false;
        } catch (Throwable failure) {
            avatarHeadModel = null;
            avatarSkinTexture = null;
            if (!avatarFailureLogged) {
                LOGGER.warn("Could not prepare the Carbon menu's 3D player head; using the avatar icon", failure);
                avatarFailureLogged = true;
            }
        }
    }

    private void addHeaderControls() {
        int navX = panelX + design(12);
        int navWidth = Math.max(1, sidebarWidth - design(24));
        int navHeight = design(38);
        modulesTab = new CarbonComponents.NavButton(navX, panelY + design(166), navWidth, navHeight,
                "layout-grid", "Modules", () -> setActiveView(View.MODULES), activeView == View.MODULES);
        settingsTab = new CarbonComponents.NavButton(navX, panelY + design(210), navWidth, navHeight,
                "settings", "Settings", () -> setActiveView(View.SETTINGS), activeView == View.SETTINGS);
        int closeSize = design(30);
        closeButton = new CarbonComponents.IconButton(
                panelX + panelWidth - design(46), panelY + design(20), closeSize, closeSize,
                "x", "Close Carbon menu", this::onClose);
        resetSettingsButton = new CarbonComponents.Button(
                contentX + contentWidth - design(104), panelY + design(92), design(88), design(28),
                "RESET", this::resetSelectedSettings, false, true);
        resetSettingsButton.visible = false;
        addRenderableWidget(modulesTab);
        addRenderableWidget(settingsTab);
        addRenderableWidget(closeButton);
        addRenderableWidget(resetSettingsButton);
    }

    private void addFilterControls() {
        ArrayList<FilterEntry> definitions = new ArrayList<>();
        definitions.add(new FilterEntry(null, "All", null));
        for (Category category : Category.values()) {
            if (hasModules(category)) {
                definitions.add(new FilterEntry(category, category.label(), null));
            }
        }

        var font = Minecraft.getInstance().font;
        int filterX = contentX;
        int filterY = panelY + design(80);
        int filterHeight = design(32);
        for (FilterEntry definition : definitions) {
            int buttonWidth = Math.max(design(48), CarbonText.width(font, definition.label()) + design(26));
            CarbonComponents.Button button = new CarbonComponents.Button(
                    filterX, filterY, buttonWidth, filterHeight, definition.label(),
                    () -> selectCategory(definition.category()), definition.category() == selectedCategory,
                    true);
            filterEntries.add(new FilterEntry(definition.category(), definition.label(), button));
            addRenderableWidget(button);
            filterX += buttonWidth + design(8);
        }
    }

    private void addSearchBox() {
        int searchX = panelX + panelWidth - design(304);
        int searchY = panelY + design(20);
        int searchWidth = design(244);
        int searchHeight = design(36);
        searchBox = new EditBox(Minecraft.getInstance().font,
                searchX + design(34), searchY + design(4),
                Math.max(1, searchWidth - design(46)), Math.max(1, searchHeight - design(8)),
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
    }

    private void addPresetControls() {
        int buttonX = panelX + design(12);
        int buttonWidth = Math.max(1, sidebarWidth - design(24));
        presetChoiceButton = new CarbonComponents.Button(
                buttonX, panelY + design(316), buttonWidth, design(31), presetLabel(),
                this::cyclePreset, false, true);
        applyPresetButton = new CarbonComponents.Button(
                buttonX, panelY + design(354), buttonWidth, design(31), "APPLY PRESET",
                this::applyPreset, true, true);
        addRenderableWidget(presetChoiceButton);
        addRenderableWidget(applyPresetButton);
    }

    private void rebuildProfileControls() {
        if (profileSelectButton != null) {
            removeWidget(profileSelectButton);
        }
        if (createProfileButton != null) {
            removeWidget(createProfileButton);
        }
        if (configManager == null) {
            profileSelectButton = null;
            createProfileButton = null;
            return;
        }

        int buttonX = panelX + design(12);
        int buttonWidth = Math.max(1, sidebarWidth - design(24));
        profileSelectButton = new CarbonComponents.Button(
                buttonX, panelY + design(462), buttonWidth, design(30), activeProfileLabel(),
                this::cycleProfile, true, true);
        createProfileButton = new CarbonComponents.Button(
                buttonX, panelY + design(499), buttonWidth, design(29), "NEW PROFILE",
                this::createProfile, false, true);
        addRenderableWidget(profileSelectButton);
        addRenderableWidget(createProfileButton);
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
                    0, 0, design(240), design(128), module, () -> openModuleSettings(selected));
            card.setEntranceDelayMillis((moduleCards.size() % 9) * 42L);
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

        int gap = design(18);
        int minimumCardWidth = design(208);
        gridColumns = Math.max(1, Math.min(3,
                (contentWidth + gap) / Math.max(1, minimumCardWidth + gap)));
        while (gridColumns > 1
                && (contentWidth - gap * (gridColumns - 1)) / gridColumns < minimumCardWidth) {
            gridColumns--;
        }
        gridRows = (moduleCards.size() + gridColumns - 1) / gridColumns;
        int availableHeight = Math.max(1, panelY + panelHeight - design(48) - cardsTop);
        cardHeight = Math.max(1, Math.min(design(128), availableHeight));
        visibleGridRows = Math.max(1, Math.min(gridRows,
                (availableHeight + gap) / Math.max(1, cardHeight + gap)));
        gridScrollRow = Math.max(0, Math.min(gridScrollRow, Math.max(0, gridRows - visibleGridRows)));

        int cardWidth = Math.max(1, (contentWidth - gap * (gridColumns - 1)) / gridColumns);
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
        moduleEnabledControl = null;
        if (selectedModule == null) {
            selectedModule = firstModule();
        }
        if (selectedModule == null) {
            return;
        }

        int controlX = contentX + design(20);
        int controlWidth = Math.max(design(90), Math.min(design(560), contentWidth - design(40)));
        int controlY = panelY + design(150);
        moduleEnabledControl = new CarbonComponents.Toggle(
                controlX, controlY, controlWidth, design(36), selectedModule);
        settingControls.add(moduleEnabledControl);
        addRenderableWidget(moduleEnabledControl);
        controlY += design(45);

        for (Setting<?> setting : selectedModule.settings()) {
            AbstractWidget control;
            int controlHeight;
            if (setting instanceof NumberSetting numberSetting) {
                controlHeight = design(42);
                control = new CarbonComponents.Slider(controlX, controlY, controlWidth, controlHeight,
                        numberSetting);
            } else if (setting instanceof BoolSetting boolSetting) {
                controlHeight = design(34);
                control = new CarbonComponents.Toggle(controlX, controlY, controlWidth, controlHeight,
                        boolSetting);
            } else if (setting instanceof ModeSetting modeSetting) {
                controlHeight = design(34);
                control = new CarbonComponents.ModeButton(controlX, controlY, controlWidth, controlHeight,
                        modeSetting);
            } else if (setting instanceof ColorSetting colorSetting) {
                controlHeight = design(34);
                control = new CarbonComponents.ColorButton(controlX, controlY, controlWidth, controlHeight,
                        colorSetting);
            } else if (setting instanceof KeybindSetting keybindSetting) {
                controlHeight = design(34);
                control = new CarbonComponents.KeybindButton(controlX, controlY, controlWidth, controlHeight,
                        keybindSetting, () -> beginKeybindCapture(keybindSetting));
            } else {
                continue;
            }
            settingControls.add(control);
            addRenderableWidget(control);
            controlY += controlHeight + design(8);
        }
    }

    private void drawSidebarSurfaces(GuiGraphicsExtractor graphics) {
        CarbonGlass.drawPanelGui(graphics, panelX + design(12), panelY + design(70),
                sidebarWidth - design(24), design(84), 13.0f, CarbonGlass.Style.CARD_OFF);
        if (activeView == View.MODULES) {
            CarbonGlass.drawPanelGui(graphics, panelX + design(12), panelY + design(304),
                    sidebarWidth - design(24), design(108), 13.0f, CarbonGlass.Style.CARD_OFF);
            CarbonGlass.drawPanelGui(graphics, panelX + design(12), panelY + design(448),
                    sidebarWidth - design(24), design(88), 13.0f, CarbonGlass.Style.CARD_OFF);
        }
        int avatarInset = design(10);
        CarbonGlass.drawTintedRect(graphics, avatarX - avatarInset, avatarY - avatarInset,
                avatarSize + avatarInset * 2, avatarSize + avatarInset * 2, design(14), 0x65313D34);
        CarbonGlass.outlineTintedRect(graphics, avatarX - avatarInset, avatarY - avatarInset,
                avatarSize + avatarInset * 2, avatarSize + avatarInset * 2,
                design(14), 0x6E55E695, 0x26313D34);
    }

    private void drawModulesSurface(GuiGraphicsExtractor graphics) {
        int searchX = panelX + panelWidth - design(304);
        int searchY = panelY + design(20);
        CarbonGlass.drawTintedRect(graphics, searchX, searchY, design(244), design(36),
                design(18), 0x75313D34);
        CarbonGlass.outlineTintedRect(graphics, searchX, searchY, design(244), design(36),
                design(18), 0x557D9C86, 0x4D18221D);

        int countWidth = design(132);
        int countHeight = design(23);
        int countX = contentX + contentWidth - countWidth;
        int countY = panelY + design(119);
        CarbonGlass.drawTintedRect(graphics, countX, countY, countWidth, countHeight,
                countHeight * 0.5f, 0x553FE18E);

        if (visibleModuleCount == 0) {
            graphics.nextStratum();
            CarbonText.centered(graphics, Minecraft.getInstance().font, "No modules match this search",
                    contentX + contentWidth / 2, cardsTop + design(26), CarbonTheme.TEXT_MUTED, false);
        }

        if (gridRows > visibleGridRows && visibleGridRows > 0) {
            int trackX = panelX + panelWidth - design(9);
            int trackY = cardsTop;
            int trackHeight = Math.max(1, panelY + panelHeight - design(48) - cardsTop);
            int thumbHeight = Math.max(design(28), trackHeight * visibleGridRows / gridRows);
            int maxScroll = Math.max(1, gridRows - visibleGridRows);
            int thumbY = trackY + (trackHeight - thumbHeight) * gridScrollRow / maxScroll;
            CarbonGlass.drawTintedRect(graphics, trackX, trackY, design(4), trackHeight,
                    design(2), 0x45FFFFFF);
            CarbonGlass.drawTintedRect(graphics, trackX, thumbY, design(4), thumbHeight,
                    design(2), 0xD033D889);
        }
    }

    private void drawSettingsSurface(GuiGraphicsExtractor graphics) {
        int surfaceY = panelY + design(76);
        int surfaceHeight = Math.max(1, panelY + panelHeight - design(24) - surfaceY);
        CarbonGlass.drawPanelGui(graphics, contentX, surfaceY, contentWidth, surfaceHeight,
                18.0f, CarbonGlass.Style.CARD_OFF);
        if (selectedModule == null) {
            graphics.nextStratum();
            CarbonText.centered(graphics, Minecraft.getInstance().font, "No modules registered",
                    contentX + contentWidth / 2, surfaceY + design(44), CarbonTheme.TEXT_MUTED, false);
        }
    }

    private void drawProfileAvatar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (avatarHeadModel == null || avatarSkinTexture == null) {
            CarbonIcons.drawGui(graphics, "user", avatarX + design(8), avatarY + design(8),
                    avatarSize - design(16), CarbonTheme.ACCENT);
            return;
        }
        try {
            float centerX = avatarX + avatarSize * 0.5f;
            float centerY = avatarY + avatarSize * 0.5f;
            float pointerYaw = Math.max(-9.0f, Math.min(9.0f, (mouseX - centerX) / Math.max(1.0f, avatarSize) * 18.0f));
            float pointerPitch = Math.max(-5.0f, Math.min(5.0f, (mouseY - centerY) / Math.max(1.0f, avatarSize) * 10.0f));
            float idleYaw = (float) Math.sin((System.nanoTime() - screenOpenedAtNanos) / 850_000_000.0) * 4.0f;
            graphics.skin(avatarHeadModel, avatarSkinTexture, 34.0f,
                    pointerPitch - 2.0f, pointerYaw + idleYaw, 0.0f,
                    avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize);
        } catch (Throwable failure) {
            if (!avatarFailureLogged) {
                LOGGER.warn("Could not render the Carbon menu's 3D player head; using the avatar icon", failure);
                avatarFailureLogged = true;
            }
            CarbonIcons.drawGui(graphics, "user", avatarX + design(8), avatarY + design(8),
                    avatarSize - design(16), CarbonTheme.ACCENT);
        }
    }

    private void drawBrandAndSidebarText(GuiGraphicsExtractor graphics) {
        var font = Minecraft.getInstance().font;
        int markSize = design(28);
        int markX = panelX + design(20);
        int markY = panelY + design(22);
        graphics.blit(CARBON_MARK, markX, markY, markX + markSize, markY + markSize,
                0.0f, 1.0f, 0.0f, 1.0f);
        CarbonText.drawUi(graphics, font, "CARBON", CarbonText.Weight.BOLD,
                15.0f, markX + markSize + design(9), panelY + design(28), CarbonTheme.TEXT, false);
        float breathe = 0.65f + CarbonAnimation.pulse(System.nanoTime() - screenOpenedAtNanos, 0.8f) * 0.35f;
        int dotColor = CarbonTheme.mix(0xFF319B66, CarbonTheme.ACCENT, breathe);
        CarbonGlass.drawTintedRect(graphics, panelX + sidebarWidth - design(22), panelY + design(31),
                design(6), design(6), design(3), dotColor);

        String username = fitText(font, currentUsername, CarbonText.Weight.SEMIBOLD, 13.0f,
                sidebarWidth - avatarSize - design(58));
        int userTextX = avatarX + avatarSize + design(16);
        CarbonText.drawUi(graphics, font, username, CarbonText.Weight.SEMIBOLD,
                13.0f, userTextX, panelY + design(98), CarbonTheme.TEXT, false);
        CarbonText.drawUi(graphics, font, "CLIENT ACCOUNT", CarbonText.Weight.MEDIUM,
                9.0f, userTextX, panelY + design(119), CarbonTheme.TEXT_MUTED, false);
        CarbonGlass.drawTintedRect(graphics, userTextX, panelY + design(136), design(5), design(5),
                design(3), CarbonTheme.ACCENT);
        CarbonText.drawUi(graphics, font, "IN GAME", CarbonText.Weight.MEDIUM,
                9.0f, userTextX + design(10), panelY + design(135), CarbonTheme.ACCENT, false);

        CarbonText.drawUi(graphics, font, "QUICK PRESETS", CarbonText.Weight.SEMIBOLD,
                10.0f, panelX + design(18), panelY + design(285), CarbonTheme.TEXT_MUTED, false);
        CarbonText.drawUi(graphics, font, "SAVED PROFILE", CarbonText.Weight.SEMIBOLD,
                10.0f, panelX + design(18), panelY + design(431), CarbonTheme.TEXT_MUTED, false);
        CarbonText.drawUi(graphics, font, "v1.0.0", CarbonText.Weight.REGULAR,
                10.0f, panelX + design(18), panelY + panelHeight - design(26), CarbonTheme.TEXT_DIM, false);
    }

    private void drawMainHeader(GuiGraphicsExtractor graphics) {
        var font = Minecraft.getInstance().font;
        CarbonText.drawUi(graphics, font, screenTitle, CarbonText.Weight.SEMIBOLD,
                22.0f, contentX + design(4), panelY + design(26), CarbonTheme.TEXT, false);
        CarbonText.drawUi(graphics, font,
                activeView == View.MODULES
                        ? "Switch features on, search, or open a card to fine-tune it."
                        : "Tune the selected Carbon feature. Changes save automatically.",
                CarbonText.Weight.REGULAR, 11.0f, contentX + design(4), panelY + design(55),
                CarbonTheme.TEXT_MUTED, false);

        if (activeView == View.MODULES) {
            int enabled = 0;
            for (Module module : modules.modules()) {
                if (module.enabled()) {
                    enabled++;
                }
            }
            String count = enabled + " / " + modules.modules().size() + " ACTIVE";
            CarbonText.centered(graphics, font, count,
                    contentX + contentWidth - design(66), panelY + design(125), CarbonTheme.TEXT, false);
        }

        if (searchBox != null && activeView == View.MODULES) {
            CarbonIcons.drawGui(graphics, "search", panelX + panelWidth - design(292),
                    panelY + design(30), design(16), CarbonTheme.TEXT_MUTED);
        }
    }

    private void drawPresetDescription(GuiGraphicsExtractor graphics) {
        if (activeView != View.MODULES) {
            return;
        }
        Preset preset = PRESETS.get(presetIndex);
        CarbonText.drawUi(graphics, Minecraft.getInstance().font, preset.description(),
                CarbonText.Weight.REGULAR, 9.0f,
                panelX + design(22), panelY + design(392), CarbonTheme.TEXT_MUTED, false);
    }

    private void drawSettingsHeader(GuiGraphicsExtractor graphics) {
        if (activeView != View.SETTINGS || selectedModule == null) {
            return;
        }
        var font = Minecraft.getInstance().font;
        CarbonText.drawUi(graphics, font, selectedModule.name(), CarbonText.Weight.SEMIBOLD,
                18.0f, contentX + design(22), panelY + design(96), CarbonTheme.TEXT, false);
        CarbonText.drawUi(graphics, font, selectedModule.description(), CarbonText.Weight.REGULAR,
                11.0f, contentX + design(22), panelY + design(119), CarbonTheme.TEXT_MUTED, false);
    }

    private void drawToast(GuiGraphicsExtractor graphics) {
        if (toastMessage == null) {
            return;
        }
        long elapsed = System.nanoTime() - toastStartedAtNanos;
        if (elapsed > 2_000_000_000L) {
            toastMessage = null;
            return;
        }
        float fade = elapsed < 1_600_000_000L
                ? 1.0f : 1.0f - (elapsed - 1_600_000_000L) / 400_000_000.0f;
        float slide = 1.0f - CarbonAnimation.easeOutCubic(
                Math.min(1.0f, elapsed / 180_000_000.0f));
        var font = Minecraft.getInstance().font;
        int toastWidth = Math.min(contentWidth, Math.max(design(150),
                CarbonText.width(font, toastMessage) + design(40)));
        int toastHeight = design(34);
        int x = contentX + (contentWidth - toastWidth) / 2;
        int y = panelY + panelHeight - design(52) + Math.round(slide * design(10));
        int alpha = Math.round(0xCE * Math.max(0.0f, fade));
        CarbonGlass.drawTintedRect(graphics, x, y, toastWidth, toastHeight,
                toastHeight * 0.5f, (alpha << 24) | 0x00111A14);
        CarbonGlass.drawTintedRect(graphics, x + design(12), y + design(11), design(6), design(6),
                design(3), (Math.round(255 * fade) << 24) | 0x0055E695);
        CarbonText.centered(graphics, font, toastMessage,
                x + toastWidth / 2 + design(5), y + (toastHeight - font.lineHeight) / 2,
                CarbonTheme.TEXT, false);
    }

    private void updateScreenTitle() {
        if (activeView == View.SETTINGS) {
            screenTitle = "Module settings";
        } else if (selectedCategory == null) {
            screenTitle = "Carbon modules";
        } else {
            screenTitle = selectedCategory.label() + " modules";
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

    private void cyclePreset() {
        presetIndex = (presetIndex + 1) % PRESETS.size();
        presetChoiceButton.updateLabel(presetLabel());
        showToast("Selected " + PRESETS.get(presetIndex).name() + " preset");
    }

    private String presetLabel() {
        return "‹  " + PRESETS.get(presetIndex).name().toUpperCase(Locale.ROOT) + "  ›";
    }

    private void applyPreset() {
        Preset preset = PRESETS.get(presetIndex);
        int changed = 0;
        for (Module module : modules.modules()) {
            Boolean requested = preset.moduleStates().get(module.id());
            if (requested != null && requested != module.enabled()) {
                module.setEnabled(requested);
                changed++;
            }
        }
        showToast(preset.name() + " applied · " + changed + " changed");
    }

    private void cycleProfile() {
        if (configManager == null) {
            return;
        }
        List<String> profiles = configManager.listProfiles();
        if (profiles.isEmpty()) {
            return;
        }
        int index = profiles.indexOf(configManager.activeProfile());
        String next = profiles.get((index + 1 + profiles.size()) % profiles.size());
        selectProfile(next);
    }

    private void selectProfile(String profile) {
        if (configManager == null || !configManager.switchProfile(profile)) {
            return;
        }
        profileSelectButton.updateLabel(activeProfileLabel());
        showToast("Loaded profile " + profile);
    }

    private String activeProfileLabel() {
        if (configManager == null) {
            return "DEFAULT";
        }
        String name = configManager.activeProfile();
        String display = name.equals("default") ? "Default" : name.replace('-', ' ');
        return fitText(Minecraft.getInstance().font, display, CarbonText.Weight.MEDIUM, 11.0f,
                sidebarWidth - design(46));
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
            profileSelectButton.updateLabel(activeProfileLabel());
            showToast("Created profile " + profileName);
        }
    }

    private void resetSelectedSettings() {
        if (selectedModule == null) {
            return;
        }
        for (Setting<?> setting : selectedModule.settings()) {
            setting.reset();
        }
        showToast(selectedModule.name() + " settings reset");
    }

    private void setActiveView(View view) {
        if (activeView == view) {
            return;
        }
        activeView = view;
        updateScreenTitle();
        if (view == View.SETTINGS) {
            rebuildSettingControls();
        }
        updateVisibility();
    }

    private void openModuleSettings(Module module) {
        selectedModule = module;
        activeView = View.SETTINGS;
        updateScreenTitle();
        rebuildSettingControls();
        updateVisibility();
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
        for (CarbonComponents.ModuleCard card : moduleCards) {
            card.visible = showingModules;
        }
        if (searchBox != null) {
            searchBox.visible = showingModules;
        }
        if (presetChoiceButton != null) {
            presetChoiceButton.visible = showingModules;
        }
        if (applyPresetButton != null) {
            applyPresetButton.visible = showingModules;
        }
        if (profileSelectButton != null) {
            profileSelectButton.visible = showingModules;
        }
        if (createProfileButton != null) {
            createProfileButton.visible = showingModules;
        }
        if (resetSettingsButton != null) {
            resetSettingsButton.visible = !showingModules;
        }
        for (AbstractWidget control : settingControls) {
            control.visible = !showingModules;
        }
        if (showingModules) {
            layoutModuleCards();
        }
    }

    private void updateKeybindButtons() {
        for (AbstractWidget control : settingControls) {
            if (control instanceof CarbonComponents.KeybindButton button) {
                button.setListening(button.setting() == capturingKeybind);
            }
        }
    }

    private void showToast(String message) {
        toastMessage = message;
        toastStartedAtNanos = System.nanoTime();
    }

    private String fitText(net.minecraft.client.gui.Font font, String text, CarbonText.Weight weight,
                           float size, int maximumWidth) {
        if (CarbonText.widthDesign(font, text, weight, size) <= maximumWidth) {
            return text;
        }
        String suffix = "…";
        int end = text.length();
        while (end > 0 && CarbonText.widthDesign(font, text.substring(0, end) + suffix,
                weight, size) > maximumWidth) {
            end--;
        }
        return end == 0 ? suffix : text.substring(0, end).stripTrailing() + suffix;
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

    private int design(int pixels) {
        return Math.max(1, Math.round(pixels * layoutScale));
    }

    private record FilterEntry(Category category, String label, CarbonComponents.Button button) {
    }

    private record Preset(String name, String description, Map<String, Boolean> moduleStates) {
    }

    private enum View {
        MODULES,
        SETTINGS
    }
}
