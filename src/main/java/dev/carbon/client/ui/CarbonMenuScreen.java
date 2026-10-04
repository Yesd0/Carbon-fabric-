package dev.carbon.client.ui;

import dev.carbon.client.core.config.ConfigManager;
import dev.carbon.client.core.module.Category;
import dev.carbon.client.core.module.Module;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.core.setting.BoolSetting;
import dev.carbon.client.core.setting.ColorSetting;
import dev.carbon.client.core.setting.KeybindSetting;
import dev.carbon.client.core.setting.ModeSetting;
import dev.carbon.client.core.setting.NumberSetting;
import dev.carbon.client.core.setting.Setting;
import dev.carbon.client.ui.render.CarbonShapes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Large, rounded Carbon module menu. All cards and controls edit live module/config objects. */
public final class CarbonMenuScreen extends CarbonScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final int DESIGN_PANEL_WIDTH = 1400;
    private static final int DESIGN_PANEL_HEIGHT = 840;
    private static final int[] COLOR_CHOICES = {
            0xFFFFFFFF, 0xFFCACBD0, 0xFF8A8D93, 0xFF44474D, 0xFF15171A
    };
    private static final List<Preset> PRESETS = List.of(
            new Preset("Essentials", Map.of("fps", true, "cps", false,
                    "keystrokes", false, "zoom", false)),
            new Preset("PvP", Map.of("fps", true, "cps", true,
                    "keystrokes", true, "zoom", true)),
            new Preset("Creator", Map.of("fps", true, "cps", true,
                    "keystrokes", true, "zoom", false))
    );

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final CarbonMenuPreferences preferences;
    private final List<ModuleHit> moduleHits = new ArrayList<>();
    private final List<SettingHit> settingHits = new ArrayList<>();
    private final List<PresetHit> presetHits = new ArrayList<>();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final List<ProfileHit> profileHits = new ArrayList<>();
    private final EnumMap<Category, Integer> categoryCounts = new EnumMap<>(Category.class);
    private final Map<String, CardMotion> cardMotions = new HashMap<>();

    private EditBox searchBox;
    private EditBox profileNameBox;
    private String searchQuery = "";
    private String username = "Player";
    private ItemStack avatarHeadStack;
    private UUID avatarPlayerId;
    private long nextAvatarRefreshNanos;
    private Category selectedCategory;
    private Module selectedModule;
    private View activeView = View.MODULES;
    private SettingsPage settingsPage = SettingsPage.APPEARANCE;
    private KeybindSetting capturingKeybind;
    private NumberSetting draggingNumberSetting;
    private boolean draggingUiScale;
    private int moduleScrollRows;
    private int settingsScrollRows;
    private int profileScrollRows;
    private String toastMessage = "";
    private long toastUntilNanos;
    private long openedAtNanos;
    private long previousFrameNanos;
    private long frameDeltaNanos;
    private float openingProgress;
    private float layoutScale = 1.0f;
    private int moduleColumns = 2;
    private int moduleVisibleRows = 1;

    private Rect panelRect = Rect.EMPTY;
    private Rect sidebarRect = Rect.EMPTY;
    private Rect accountRect = Rect.EMPTY;
    private Rect avatarRect = Rect.EMPTY;
    private Rect modulesNavRect = Rect.EMPTY;
    private Rect hudEditorNavRect = Rect.EMPTY;
    private Rect settingsNavRect = Rect.EMPTY;
    private Rect profilesNavRect = Rect.EMPTY;
    private Rect allCategoryRect = Rect.EMPTY;
    private Rect searchRect = Rect.EMPTY;
    private Rect searchClearRect = Rect.EMPTY;
    private Rect closeRect = Rect.EMPTY;
    private Rect moduleGridRect = Rect.EMPTY;
    private Rect appearanceScaleCard = Rect.EMPTY;
    private Rect scaleTrackRect = Rect.EMPTY;
    private Rect fontCard = Rect.EMPTY;
    private Rect menuKeyCard = Rect.EMPTY;
    private Rect moduleInfoRect = Rect.EMPTY;
    private Rect resetSettingsRect = Rect.EMPTY;
    private Rect settingsListRect = Rect.EMPTY;
    private Rect profilesListRect = Rect.EMPTY;
    private Rect profileInputRect = Rect.EMPTY;
    private Rect profileCreateRect = Rect.EMPTY;
    private int sidebarWidth;

    public CarbonMenuScreen(ModuleManager modules, ConfigManager configManager, Screen parent) {
        super(Component.literal("Carbon Client"), parent);
        this.modules = modules;
        this.configManager = configManager;
        this.preferences = CarbonMenuPreferences.load(FabricLoader.getInstance().getConfigDir()
                .resolve("carbonclient").resolve("menu.json"));
        UiScale.setUserScaleMultiplier(preferences.uiScale());
        CarbonText.setCustomFontEnabled(preferences.customFont());
        ensureSelection();
    }

    @Override
    protected void init() {
        super.init();
        openedAtNanos = System.nanoTime();
        previousFrameNanos = openedAtNanos;
        CarbonIcons.load();
        UiScale.setUserScaleMultiplier(preferences.uiScale());
        UiScale.update(Minecraft.getInstance());
        refreshPlayerIdentity();
        ensureSelection();
        calculateLayout();

        searchBox = new EditBox(font, searchRect.x(), searchRect.y(),
                Math.max(1, searchRect.width()), Math.max(1, searchRect.height()),
                Component.literal("Search modules"));
        searchBox.setBordered(false);
        searchBox.setTextColor(CarbonTheme.TEXT);
        searchBox.setHint(Component.literal("Search modules"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value == null ? "" : value;
            moduleScrollRows = 0;
        });
        addRenderableWidget(searchBox);

        profileNameBox = new EditBox(font, profileInputRect.x(), profileInputRect.y(),
                Math.max(1, profileInputRect.width()), Math.max(1, profileInputRect.height()),
                Component.literal("New profile name"));
        profileNameBox.setBordered(false);
        profileNameBox.setTextColor(CarbonTheme.TEXT);
        profileNameBox.setHint(Component.literal("profile-name"));
        addRenderableWidget(profileNameBox);
        updateWidgetLayout();
    }

    private void refreshPlayerIdentity() {
        Minecraft client = Minecraft.getInstance();
        username = client.getUser() == null ? "Player" : client.getUser().getName();
        nextAvatarRefreshNanos = System.nanoTime() + 2_000_000_000L;
        var player = client.player;
        if (player == null) {
            avatarPlayerId = null;
            avatarHeadStack = null;
            return;
        }

        UUID playerId = player.getUUID();
        if (Objects.equals(avatarPlayerId, playerId) && avatarHeadStack != null && !avatarHeadStack.isEmpty()) {
            return;
        }
        avatarPlayerId = playerId;
        avatarHeadStack = null;
        try {
            // Let Minecraft's normal item renderer resolve the local skin profile on a PLAYER_HEAD.
            // Unlike a GUI skin quad, the vanilla head item is an actual shaded 3D cube.
            avatarHeadStack = new ItemStack(Items.PLAYER_HEAD);
            avatarHeadStack.set(DataComponents.PROFILE,
                    ResolvableProfile.createResolved(player.getGameProfile()));
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not prepare the local player's vanilla 3D head item for the Carbon menu", failure);
        }
    }

    private void refreshAvatarIfNeeded() {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null) {
            if (avatarPlayerId != null || avatarHeadStack != null) {
                avatarPlayerId = null;
                avatarHeadStack = null;
            }
            return;
        }
        boolean playerChanged = !Objects.equals(avatarPlayerId, player.getUUID());
        boolean avatarUnavailable = avatarHeadStack == null || avatarHeadStack.isEmpty();
        if (playerChanged || avatarUnavailable && System.nanoTime() >= nextAvatarRefreshNanos) {
            refreshPlayerIdentity();
        }
    }

    private void ensureSelection() {
        if (selectedModule == null || !modules.modules().contains(selectedModule)) {
            selectedModule = modules.modules().stream().findFirst().orElse(null);
        }
        categoryCounts.clear();
        for (Module module : modules.modules()) {
            categoryCounts.merge(module.category(), 1, Integer::sum);
        }
        if (selectedCategory != null && !categoryCounts.containsKey(selectedCategory)) {
            selectedCategory = null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        UiScale.update(Minecraft.getInstance());
        layoutScale = Math.max(0.001f, UiScale.rendererScale());
        calculateLayout();
        updateWidgetLayout();
        updateFrameClock();
        refreshAvatarIfNeeded();

        extractPanelSurfaces(graphics, mouseX, mouseY);
        switch (activeView) {
            case MODULES -> extractModuleSurfaces(graphics, mouseX, mouseY);
            case SETTINGS -> extractSettingsSurfaces(graphics, mouseX, mouseY);
            case PROFILES -> extractProfileSurfaces(graphics, mouseX, mouseY);
        }

        graphics.nextStratum();
        extractAvatar(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.nextStratum();
        extractSidebarText(graphics, mouseX, mouseY);
        extractHeaderText(graphics);
        switch (activeView) {
            case MODULES -> extractModuleText(graphics);
            case SETTINGS -> extractSettingsText(graphics);
            case PROFILES -> extractProfileText(graphics);
        }
        extractToast(graphics);
    }

    private void updateFrameClock() {
        long now = System.nanoTime();
        frameDeltaNanos = Math.max(0L, Math.min(50_000_000L, now - previousFrameNanos));
        previousFrameNanos = now;
        float elapsed = (now - openedAtNanos) / 250_000_000.0f;
        openingProgress = CarbonAnimation.easeOutCubic(Math.min(1.0f, elapsed));
    }

    private void calculateLayout() {
        int horizontalMargin = design(28);
        int verticalMargin = design(24);
        int panelWidth = Math.min(design(DESIGN_PANEL_WIDTH), Math.max(1, width - horizontalMargin * 2));
        int panelHeight = Math.min(design(DESIGN_PANEL_HEIGHT), Math.max(1, height - verticalMargin * 2));
        panelRect = new Rect((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
        sidebarWidth = Math.min(design(216), Math.max(design(116), Math.round(panelWidth * 0.255f)));
        sidebarWidth = Math.min(sidebarWidth, Math.max(1, panelWidth - design(250)));
        sidebarRect = new Rect(panelRect.x(), panelRect.y(), sidebarWidth, panelRect.height());

        int sidePad = design(15);
        accountRect = new Rect(sidebarRect.x() + sidePad, panelRect.y() + design(78),
                Math.max(1, sidebarWidth - sidePad * 2), design(72));
        avatarRect = new Rect(accountRect.x() + design(10), accountRect.y() + design(12), design(48), design(48));
        int navX = sidebarRect.x() + sidePad;
        int navWidth = Math.max(1, sidebarWidth - sidePad * 2);
        int navY = panelRect.y() + design(170);
        int navHeight = design(38);
        modulesNavRect = new Rect(navX, navY, navWidth, navHeight);
        settingsNavRect = new Rect(navX, navY + design(45), navWidth, navHeight);
        profilesNavRect = new Rect(navX, navY + design(90), navWidth, navHeight);
        hudEditorNavRect = new Rect(navX, navY + design(135), navWidth, navHeight);
        allCategoryRect = new Rect(navX, panelRect.y() + design(370), navWidth, design(31));

        categoryHits.clear();
        int categoryY = panelRect.y() + design(407);
        int categoryHeight = design(29);
        for (Category category : Category.values()) {
            if (categoryCounts.containsKey(category)) {
                categoryHits.add(new CategoryHit(category,
                        new Rect(navX, categoryY, navWidth, categoryHeight)));
                categoryY += design(34);
            }
        }

        Rect content = new Rect(panelRect.x() + sidebarWidth, panelRect.y(),
                Math.max(1, panelRect.width() - sidebarWidth), panelRect.height());
        int contentPad = design(28);
        closeRect = new Rect(content.right() - design(42), content.y() + design(18), design(28), design(28));
        searchRect = new Rect(content.right() - design(264), content.y() + design(24), design(206), design(34));
        searchClearRect = new Rect(searchRect.right() - design(31), searchRect.y(), design(28), searchRect.height());

        int contentDesignWidth = Math.round(content.width() / layoutScale);
        moduleColumns = contentDesignWidth >= 790 ? 2 : 1;
        int gridX = content.x() + contentPad;
        int gridY = panelRect.y() + design(143);
        int gridWidth = Math.max(1, content.width() - contentPad * 2);
        int gridBottom = panelRect.bottom() - design(24);
        moduleGridRect = new Rect(gridX, gridY, gridWidth, Math.max(1, gridBottom - gridY));
        int cardHeight = design(146);
        int cardGap = design(15);
        moduleVisibleRows = Math.max(1, (moduleGridRect.height() + cardGap) / Math.max(1, cardHeight + cardGap));

        appearanceScaleCard = new Rect(gridX, panelRect.y() + design(104), gridWidth, design(112));
        scaleTrackRect = new Rect(appearanceScaleCard.x() + design(26), appearanceScaleCard.bottom() - design(28),
                Math.max(1, appearanceScaleCard.width() - design(52)), design(6));
        fontCard = new Rect(gridX, appearanceScaleCard.bottom() + design(16), gridWidth, design(72));
        menuKeyCard = new Rect(gridX, fontCard.bottom() + design(13), gridWidth, design(84));
        moduleInfoRect = new Rect(gridX, panelRect.y() + design(96), gridWidth, design(70));
        resetSettingsRect = new Rect(moduleInfoRect.right() - design(122), moduleInfoRect.y() + design(17),
                design(104), design(34));
        settingsListRect = new Rect(gridX, moduleInfoRect.bottom() + design(15), gridWidth,
                Math.max(1, panelRect.bottom() - design(24) - (moduleInfoRect.bottom() + design(15))));

        profilesListRect = new Rect(gridX, panelRect.y() + design(105), gridWidth,
                Math.max(1, panelRect.height() - design(245)));
        profileInputRect = new Rect(gridX, panelRect.bottom() - design(101),
                Math.max(1, gridWidth - design(116)), design(38));
        profileCreateRect = new Rect(profileInputRect.right() + design(10), profileInputRect.y(),
                Math.max(1, gridWidth - profileInputRect.width() - design(10)), profileInputRect.height());
    }

    private void updateWidgetLayout() {
        if (searchBox != null) {
            searchBox.visible = activeView == View.MODULES;
            searchBox.setRectangle(Math.max(1, searchRect.width() - design(62)), Math.max(1, searchRect.height()),
                    searchRect.x() + design(30), searchRect.y());
        }
        if (profileNameBox != null) {
            profileNameBox.visible = activeView == View.PROFILES;
            profileNameBox.setRectangle(Math.max(1, profileInputRect.width() - design(12)),
                    Math.max(1, profileInputRect.height()), profileInputRect.x() + design(6), profileInputRect.y());
        }
    }

    private void extractPanelSurfaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawPanel(graphics, panelRect, CarbonTheme.FRAME, CarbonTheme.BORDER_SOFT, design(24));
        int accentWidth = Math.round((panelRect.width() - design(48)) * openingProgress);
        if (accentWidth > 0) {
            graphics.fill(panelRect.x() + design(24), panelRect.y() + 1,
                    panelRect.x() + design(24) + accentWidth, panelRect.y() + 2, CarbonTheme.ACCENT_MUTED);
        }
        drawRounded(graphics, sidebarRect, design(21), CarbonTheme.SIDEBAR);
        graphics.fill(sidebarRect.right() - 1, panelRect.y() + design(20), sidebarRect.right(),
                panelRect.bottom() - design(20), CarbonTheme.BORDER_SOFT);
        drawPanel(graphics, accountRect, CarbonTheme.PANEL, CarbonTheme.BORDER_SOFT, design(14));
        drawPanel(graphics, modulesNavRect, activeView == View.MODULES ? CarbonTheme.PANEL_HOVER : CarbonTheme.SIDEBAR,
                activeView == View.MODULES ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(11));
        drawPanel(graphics, settingsNavRect, activeView == View.SETTINGS ? CarbonTheme.PANEL_HOVER : CarbonTheme.SIDEBAR,
                activeView == View.SETTINGS ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(11));
        drawPanel(graphics, profilesNavRect, activeView == View.PROFILES ? CarbonTheme.PANEL_HOVER : CarbonTheme.SIDEBAR,
                activeView == View.PROFILES ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(11));
        boolean hudEditorHovered = hudEditorNavRect.contains(mouseX, mouseY);
        drawPanel(graphics, hudEditorNavRect,
                hudEditorHovered ? CarbonTheme.PANEL_HOVER : CarbonTheme.SIDEBAR,
                hudEditorHovered ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(11));
        drawPanel(graphics, allCategoryRect, selectedCategory == null ? CarbonTheme.PANEL_RAISED : CarbonTheme.SIDEBAR,
                selectedCategory == null ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(8));
        for (CategoryHit hit : categoryHits) {
            boolean selected = selectedCategory == hit.category();
            drawPanel(graphics, hit.bounds(), selected ? CarbonTheme.PANEL_RAISED : CarbonTheme.SIDEBAR,
                    selected ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(8));
        }
        drawPanel(graphics, closeRect, CarbonTheme.PANEL, CarbonTheme.BORDER_SOFT, design(8));
    }

    private void extractModuleSurfaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        moduleHits.clear();
        presetHits.clear();
        drawPanel(graphics, searchRect, CarbonTheme.PANEL, CarbonTheme.BORDER_SOFT, design(9));
        CarbonIcons.drawGui(graphics, "search", searchRect.x() + design(11),
                searchRect.y() + design(9), design(16), CarbonTheme.TEXT_DIM);
        if (searchQuery.isEmpty()) {
            // The edit box owns focus, caret, and text input; this remains a genuine searchable field.
        } else {
            drawPanel(graphics, searchClearRect, CarbonTheme.PANEL_RAISED, CarbonTheme.BORDER_SOFT, design(7));
            CarbonIcons.drawGui(graphics, "x", searchClearRect.x() + design(8),
                    searchClearRect.y() + design(8), design(14), CarbonTheme.TEXT_MUTED);
        }

        List<Module> filtered = filteredModules();
        int presetX = moduleGridRect.x();
        int presetY = panelRect.y() + design(93);
        for (Preset preset : PRESETS) {
            int chipWidth = design(preset.name().equals("Essentials") ? 105 : 80);
            Rect chip = new Rect(presetX, presetY, chipWidth, design(29));
            presetHits.add(new PresetHit(preset, chip));
            boolean selected = presetMatches(preset);
            drawPanel(graphics, chip,
                    selected ? CarbonTheme.PANEL_RAISED : CarbonTheme.PANEL,
                    selected ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(8));
            presetX += chipWidth + design(8);
        }

        int columns = Math.max(1, moduleColumns);
        int cardGap = design(15);
        int cardHeight = design(146);
        int cardWidth = Math.max(1, (moduleGridRect.width() - cardGap * (columns - 1)) / columns);
        int totalRows = (filtered.size() + columns - 1) / columns;
        int maxScroll = Math.max(0, totalRows - moduleVisibleRows);
        moduleScrollRows = Math.max(0, Math.min(maxScroll, moduleScrollRows));
        int firstIndex = moduleScrollRows * columns;
        int lastIndex = Math.min(filtered.size(), firstIndex + moduleVisibleRows * columns);
        for (int index = firstIndex; index < lastIndex; index++) {
            int row = (index - firstIndex) / columns;
            int column = index % columns;
            int x = moduleGridRect.x() + column * (cardWidth + cardGap);
            int y = moduleGridRect.y() + row * (cardHeight + cardGap);
            Module module = filtered.get(index);
            CardMotion motion = motionFor(module);
            motion.update(frameDeltaNanos, module.enabled(), cardRectAt(x, y, cardWidth, cardHeight).contains(mouseX, mouseY));
            int enterOffset = design((1.0f - motion.enter) * 7.0f);
            Rect card = new Rect(x, y + enterOffset, cardWidth, cardHeight);
            Rect settings = new Rect(card.right() - design(42), card.y() + design(13), design(28), design(28));
            moduleHits.add(new ModuleHit(module, card, settings, motion));
            extractModuleCardSurface(graphics, module, card, settings, motion, mouseX, mouseY);
        }
    }

    private Rect cardRectAt(int x, int y, int cardWidth, int cardHeight) {
        return new Rect(x, y, cardWidth, cardHeight);
    }

    private void extractModuleCardSurface(GuiGraphicsExtractor graphics, Module module, Rect card,
                                          Rect settings, CardMotion motion, int mouseX, int mouseY) {
        float hover = CarbonAnimation.clamp01(motion.hover);
        float enabled = CarbonAnimation.clamp01(motion.enabled);
        int fill = CarbonTheme.mix(CarbonTheme.CARD, CarbonTheme.CARD_HOVER,
                Math.max(hover * 0.8f, enabled * 0.14f));
        int border = CarbonTheme.mix(CarbonTheme.BORDER_SOFT, CarbonTheme.BORDER,
                Math.max(hover * 0.65f, enabled * 0.85f));
        if (motion.clickPulse > 0.01f) {
            fill = CarbonTheme.mix(fill, CarbonTheme.PANEL_HOVER, motion.clickPulse * 0.42f);
        }
        drawPanel(graphics, card, fill, border, design(14));

        int railWidth = design(3 + 2 * enabled);
        drawRounded(graphics, new Rect(card.x() + design(10), card.y() + design(16), railWidth,
                card.height() - design(32)), design(3), CarbonTheme.mix(CarbonTheme.BORDER_SOFT, CarbonTheme.ACCENT, enabled));
        Rect iconTile = new Rect(card.x() + design(21), card.y() + design(22), design(42), design(42));
        drawPanel(graphics, iconTile, CarbonTheme.PANEL_RAISED, CarbonTheme.BORDER_SOFT, design(10));
        CarbonIcons.drawGui(graphics, iconFor(module), iconTile.x() + design(10),
                iconTile.y() + design(10), design(22), CarbonTheme.TEXT);
        drawPanel(graphics, settings, settings.contains(mouseX, mouseY) ? CarbonTheme.PANEL_HOVER : CarbonTheme.PANEL,
                CarbonTheme.BORDER_SOFT, design(8));
        CarbonIcons.drawGui(graphics, "settings", settings.x() + design(7),
                settings.y() + design(7), design(14), CarbonTheme.TEXT_MUTED);

        int statusWidth = design(module.enabled() ? 66 : 77);
        Rect status = new Rect(card.x() + design(21), card.bottom() - design(37), statusWidth, design(22));
        drawPanel(graphics, status, module.enabled() ? CarbonTheme.PANEL_HOVER : CarbonTheme.PANEL,
                module.enabled() ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(7));
    }

    private void extractSettingsSurfaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        settingHits.clear();
        if (settingsPage == SettingsPage.APPEARANCE) {
            drawPanel(graphics, appearanceScaleCard, CarbonTheme.CARD, CarbonTheme.BORDER_SOFT, design(12));
            drawRounded(graphics, scaleTrackRect, Math.max(1, scaleTrackRect.height() / 2), CarbonTheme.TRACK_OFF);
            float fraction = (preferences.uiScale() - 0.75f) / (1.50f - 0.75f);
            int fillWidth = Math.round(scaleTrackRect.width() * fraction);
            if (fillWidth > 0) {
                drawRounded(graphics, new Rect(scaleTrackRect.x(), scaleTrackRect.y(), fillWidth,
                        scaleTrackRect.height()), Math.max(1, scaleTrackRect.height() / 2), CarbonTheme.ACCENT_MUTED);
            }
            int thumbX = scaleTrackRect.x() + fillWidth;
            int thumbSize = design(13);
            drawRounded(graphics, new Rect(thumbX - thumbSize / 2, scaleTrackRect.y() + scaleTrackRect.height() / 2 - thumbSize / 2,
                    thumbSize, thumbSize), thumbSize / 2, CarbonTheme.ACCENT);
            drawPanel(graphics, fontCard, CarbonTheme.CARD, CarbonTheme.BORDER_SOFT, design(11));
            drawPanel(graphics, menuKeyCard, CarbonTheme.CARD, CarbonTheme.BORDER_SOFT, design(11));
        } else {
            drawPanel(graphics, moduleInfoRect, CarbonTheme.CARD, CarbonTheme.BORDER_SOFT, design(11));
            drawPanel(graphics, resetSettingsRect, CarbonTheme.PANEL, CarbonTheme.BORDER_SOFT, design(8));
            if (selectedModule == null) {
                return;
            }
            List<Setting<?>> settings = selectedModule.settings();
            int rowHeight = design(73);
            int gap = design(8);
            int visible = Math.max(1, (settingsListRect.height() + gap) / Math.max(1, rowHeight + gap));
            int maxScroll = Math.max(0, settings.size() - visible);
            settingsScrollRows = Math.max(0, Math.min(settingsScrollRows, maxScroll));
            int end = Math.min(settings.size(), settingsScrollRows + visible);
            for (int index = settingsScrollRows; index < end; index++) {
                Setting<?> setting = settings.get(index);
                int rowIndex = index - settingsScrollRows;
                int y = settingsListRect.y() + rowIndex * (rowHeight + gap);
                Rect row = new Rect(settingsListRect.x(), y, settingsListRect.width(), rowHeight);
                Rect control = settingControlRect(setting, row);
                settingHits.add(new SettingHit(setting, row, control));
                drawPanel(graphics, row,
                        row.contains(mouseX, mouseY) ? CarbonTheme.CARD_HOVER : CarbonTheme.CARD,
                        row.contains(mouseX, mouseY) ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(10));
                extractSettingControlSurface(graphics, setting, control);
            }
        }
    }

    private void extractSettingControlSurface(GuiGraphicsExtractor graphics, Setting<?> setting, Rect control) {
        if (setting instanceof BoolSetting boolSetting) {
            drawPanel(graphics, control, boolSetting.enabled() ? CarbonTheme.PANEL_HOVER : CarbonTheme.PANEL,
                    boolSetting.enabled() ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(8));
        } else if (setting instanceof NumberSetting numberSetting) {
            int trackHeight = design(4);
            int trackY = control.y() + control.height() / 2 - trackHeight / 2;
            Rect track = new Rect(control.x(), trackY, control.width(), trackHeight);
            drawRounded(graphics, track, Math.max(1, trackHeight / 2), CarbonTheme.TRACK_OFF);
            double range = numberSetting.maximum() - numberSetting.minimum();
            float amount = range <= 0.0 ? 0.0f
                    : (float) ((numberSetting.get() - numberSetting.minimum()) / range);
            int fillWidth = Math.round(control.width() * amount);
            if (fillWidth > 0) {
                drawRounded(graphics, new Rect(track.x(), track.y(), fillWidth, track.height()),
                        Math.max(1, trackHeight / 2), CarbonTheme.ACCENT_MUTED);
            }
            int thumbSize = design(11);
            int centerX = control.x() + fillWidth;
            drawRounded(graphics, new Rect(centerX - thumbSize / 2, control.y() + control.height() / 2 - thumbSize / 2,
                    thumbSize, thumbSize), thumbSize / 2, CarbonTheme.ACCENT);
        } else {
            drawPanel(graphics, control, CarbonTheme.PANEL, CarbonTheme.BORDER_SOFT, design(8));
        }
    }

    private void extractProfileSurfaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        profileHits.clear();
        drawPanel(graphics, profilesListRect, CarbonTheme.PANEL, CarbonTheme.BORDER_SOFT, design(12));
        List<String> profiles = configManager == null ? List.of("default") : configManager.listProfiles();
        int rowHeight = design(48);
        int gap = design(8);
        int visible = Math.max(1, (profilesListRect.height() - design(20) + gap) / Math.max(1, rowHeight + gap));
        profileScrollRows = Math.max(0, Math.min(profileScrollRows, Math.max(0, profiles.size() - visible)));
        int end = Math.min(profiles.size(), profileScrollRows + visible);
        for (int index = profileScrollRows; index < end; index++) {
            int y = profilesListRect.y() + design(10) + (index - profileScrollRows) * (rowHeight + gap);
            Rect row = new Rect(profilesListRect.x() + design(10), y,
                    Math.max(1, profilesListRect.width() - design(20)), rowHeight);
            String profile = profiles.get(index);
            profileHits.add(new ProfileHit(profile, row));
            boolean selected = configManager != null && profile.equals(configManager.activeProfile());
            drawPanel(graphics, row, selected ? CarbonTheme.PANEL_HOVER :
                            row.contains(mouseX, mouseY) ? CarbonTheme.CARD_HOVER : CarbonTheme.CARD,
                    selected ? CarbonTheme.BORDER : CarbonTheme.BORDER_SOFT, design(9));
        }
        drawPanel(graphics, profileInputRect, CarbonTheme.CARD, CarbonTheme.BORDER_SOFT, design(8));
        drawPanel(graphics, profileCreateRect, CarbonTheme.PANEL_RAISED, CarbonTheme.BORDER, design(8));
    }

    private void extractAvatar(GuiGraphicsExtractor graphics) {
        if (avatarRect.width() <= 0 || avatarRect.height() <= 0) {
            return;
        }
        if (avatarHeadStack != null && !avatarHeadStack.isEmpty()) {
            try {
                int size = Math.min(avatarRect.width(), avatarRect.height());
                float itemScale = size / 16.0f;
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(avatarRect.x() + (avatarRect.width() - size) / 2.0f,
                            avatarRect.y() + (avatarRect.height() - size) / 2.0f);
                    graphics.pose().scale(itemScale, itemScale);
                    graphics.fakeItem(avatarHeadStack, 0, 0);
                } finally {
                    graphics.pose().popMatrix();
                }
                return;
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not extract the local player's vanilla 3D head item in the Carbon menu", failure);
            }
        }

        CarbonIcons.drawGui(graphics, "user", avatarRect.x() + design(11),
                avatarRect.y() + design(11), design(26), CarbonTheme.TEXT_MUTED);
    }

    private void extractSidebarText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        text(graphics, "CARBON", sidebarRect.x() + design(21), panelRect.y() + design(25),
                18, CarbonTheme.TEXT, CarbonText.Weight.BOLD);
        text(graphics, "CLIENT  /  26.2", sidebarRect.x() + design(22), panelRect.y() + design(49),
                8, CarbonTheme.TEXT_DIM, CarbonText.Weight.MEDIUM);
        String name = fit(username, CarbonText.Weight.SEMIBOLD, 11,
                accountRect.width() - avatarRect.width() - design(37));
        text(graphics, name, avatarRect.right() + design(10), accountRect.y() + design(23),
                11, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
        text(graphics, Minecraft.getInstance().player == null ? "NOT IN WORLD" : "PLAYER PROFILE",
                avatarRect.right() + design(10), accountRect.y() + design(43),
                7, CarbonTheme.TEXT_DIM, CarbonText.Weight.MEDIUM);

        drawNavLabel(graphics, "layout-grid", "Modules", modulesNavRect, activeView == View.MODULES);
        drawNavLabel(graphics, "sliders-horizontal", "Settings", settingsNavRect, activeView == View.SETTINGS);
        drawNavLabel(graphics, "user", "Profiles", profilesNavRect, activeView == View.PROFILES);
        drawNavLabel(graphics, "layout-dashboard", "HUD Editor", hudEditorNavRect,
                hudEditorNavRect.contains(mouseX, mouseY));
        text(graphics, "CATEGORIES", allCategoryRect.x() + design(4), allCategoryRect.y() - design(20),
                8, CarbonTheme.TEXT_DIM, CarbonText.Weight.SEMIBOLD);
        drawCategoryLabel(graphics, "All modules", allCategoryRect, selectedCategory == null,
                modules.modules().size());
        for (CategoryHit hit : categoryHits) {
            drawCategoryLabel(graphics, hit.category().label(), hit.bounds(),
                    hit.category() == selectedCategory, categoryCounts.getOrDefault(hit.category(), 0));
        }
        text(graphics, CarbonUI.menuKeyLabel().toUpperCase(Locale.ROOT) + "  ·  OPTIONS → CONTROLS",
                sidebarRect.x() + design(19), panelRect.bottom() - design(26),
                7, CarbonTheme.TEXT_DIM, CarbonText.Weight.MEDIUM);
    }

    private void drawNavLabel(GuiGraphicsExtractor graphics, String icon, String label, Rect bounds, boolean selected) {
        int color = selected ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED;
        CarbonIcons.drawGui(graphics, icon, bounds.x() + design(12),
                bounds.y() + design(10), design(18), color);
        text(graphics, label, bounds.x() + design(42), bounds.y() + design(12), 10,
                color, selected ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.MEDIUM);
    }

    private void drawCategoryLabel(GuiGraphicsExtractor graphics, String label, Rect bounds,
                                   boolean selected, int count) {
        int color = selected ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED;
        text(graphics, label, bounds.x() + design(12), bounds.y() + design(9), 9,
                color, selected ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.REGULAR);
        String countText = Integer.toString(count);
        float width = CarbonText.widthUi(font, countText, CarbonText.Weight.REGULAR, 8);
        text(graphics, countText, bounds.right() - design(11) - Math.round(width), bounds.y() + design(9),
                8, CarbonTheme.TEXT_DIM, CarbonText.Weight.REGULAR);
    }

    private void extractHeaderText(GuiGraphicsExtractor graphics) {
        String title;
        String subtitle;
        switch (activeView) {
            case MODULES -> {
                title = selectedCategory == null ? "Modules" : selectedCategory.label();
                subtitle = "CLICK A CARD TO TOGGLE  ·  GEAR OPENS SETTINGS";
            }
            case SETTINGS -> {
                if (settingsPage == SettingsPage.APPEARANCE) {
                    title = "Settings";
                    subtitle = "A CLEANER CARBON, YOUR WAY";
                } else {
                    title = selectedModule == null ? "Module settings" : selectedModule.name();
                    subtitle = selectedModule == null ? "LIVE MODULE CONFIGURATION" :
                            selectedModule.category().label().toUpperCase(Locale.ROOT) + "  /  LIVE SETTINGS";
                }
            }
            case PROFILES -> {
                title = "Profiles";
                subtitle = "SAVE AND SWITCH MODULE CONFIGURATIONS";
            }
            default -> throw new IllegalStateException("Unknown Carbon menu view " + activeView);
        }
        text(graphics, title, contentLeft() + design(28), panelRect.y() + design(25),
                18, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
        text(graphics, subtitle, contentLeft() + design(29), panelRect.y() + design(51),
                8, CarbonTheme.TEXT_DIM, CarbonText.Weight.MEDIUM);
        drawPanel(graphics, closeRect, CarbonTheme.PANEL, CarbonTheme.BORDER_SOFT, design(8));
        CarbonIcons.drawGui(graphics, "x", closeRect.x() + design(7),
                closeRect.y() + design(7), design(14), CarbonTheme.TEXT_MUTED);

        if (activeView == View.MODULES) {
            String result = filteredModules().size() + (filteredModules().size() == 1 ? " MODULE" : " MODULES");
            float width = CarbonText.widthUi(font, result, CarbonText.Weight.MEDIUM, 8);
            text(graphics, result, moduleGridRect.right() - Math.round(width),
                    panelRect.y() + design(99), 8, CarbonTheme.TEXT_DIM, CarbonText.Weight.MEDIUM);
        }
    }

    private void extractModuleText(GuiGraphicsExtractor graphics) {
        for (PresetHit hit : presetHits) {
            CarbonText.centeredUi(graphics, font, hit.preset().name(), CarbonText.Weight.MEDIUM,
                    9, hit.bounds().x() + hit.bounds().width() / 2.0f,
                    hit.bounds().y() + design(9), CarbonTheme.TEXT_MUTED, false);
        }
        if (moduleHits.isEmpty()) {
            String message = searchQuery.isBlank() ? "No modules in this category" : "No modules match your search";
            CarbonText.centeredUi(graphics, font, message, CarbonText.Weight.MEDIUM, 11,
                    moduleGridRect.x() + moduleGridRect.width() / 2.0f,
                    moduleGridRect.y() + design(30), CarbonTheme.TEXT_DIM, false);
            return;
        }
        for (ModuleHit hit : moduleHits) {
            Module module = hit.module();
            Rect card = hit.card();
            int textX = card.x() + design(75);
            int maxTitle = card.right() - textX - design(54);
            String name = fit(module.name(), CarbonText.Weight.SEMIBOLD, 13, maxTitle);
            text(graphics, name, textX, card.y() + design(25), 13,
                    CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
            String description = fit(module.description(), CarbonText.Weight.REGULAR, 8,
                    card.width() - design(102));
            text(graphics, description, textX, card.y() + design(51), 8,
                    CarbonTheme.TEXT_MUTED, CarbonText.Weight.REGULAR);
            Rect status = new Rect(card.x() + design(21), card.bottom() - design(37),
                    design(module.enabled() ? 66 : 77), design(22));
            String label = module.enabled() ? "ENABLED" : "DISABLED";
            CarbonText.centeredUi(graphics, font, label, CarbonText.Weight.SEMIBOLD, 7,
                    status.x() + status.width() / 2.0f, status.y() + design(7),
                    module.enabled() ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM, false);
            String settingCount = module.settings().size() + " SETTINGS";
            text(graphics, settingCount, card.right() - design(94), card.bottom() - design(30),
                    7, CarbonTheme.TEXT_DIM, CarbonText.Weight.MEDIUM);
        }
    }

    private void extractSettingsText(GuiGraphicsExtractor graphics) {
        if (settingsPage == SettingsPage.APPEARANCE) {
            text(graphics, "Interface scale", appearanceScaleCard.x() + design(20),
                    appearanceScaleCard.y() + design(18), 11, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
            text(graphics, "Adjust the size of Carbon's panels and controls", appearanceScaleCard.x() + design(20),
                    appearanceScaleCard.y() + design(40), 8, CarbonTheme.TEXT_MUTED, CarbonText.Weight.REGULAR);
            String scaleLabel = Math.round(preferences.uiScale() * 100.0f) + "%";
            float scaleWidth = CarbonText.widthUi(font, scaleLabel, CarbonText.Weight.SEMIBOLD, 10);
            text(graphics, scaleLabel, appearanceScaleCard.right() - design(20) - Math.round(scaleWidth),
                    appearanceScaleCard.y() + design(19), 10, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);

            text(graphics, "Typeface", fontCard.x() + design(18), fontCard.y() + design(16),
                    10, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
            text(graphics, preferences.customFont() ? "Bundled Inter with Minecraft glyph fallback" :
                            "Minecraft default font (safe fallback)",
                    fontCard.x() + design(18), fontCard.y() + design(38),
                    8, CarbonTheme.TEXT_MUTED, CarbonText.Weight.REGULAR);
            drawValuePill(graphics, fontCard, preferences.customFont() ? "INTER" : "VANILLA");

            text(graphics, "Menu key", menuKeyCard.x() + design(18), menuKeyCard.y() + design(17),
                    10, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
            text(graphics, "Change it in Options  →  Controls  →  Carbon Client", menuKeyCard.x() + design(18),
                    menuKeyCard.y() + design(40), 8, CarbonTheme.TEXT_MUTED, CarbonText.Weight.REGULAR);
            drawValuePill(graphics, menuKeyCard, CarbonUI.menuKeyLabel().toUpperCase(Locale.ROOT));
            text(graphics, "Your key choice is saved by Minecraft's normal keybind settings.",
                    menuKeyCard.x() + design(18), menuKeyCard.bottom() - design(12), 7,
                    CarbonTheme.TEXT_DIM, CarbonText.Weight.REGULAR);
            return;
        }

        if (selectedModule == null) {
            text(graphics, "No module is registered.", moduleInfoRect.x() + design(18),
                    moduleInfoRect.y() + design(28), 10, CarbonTheme.TEXT_MUTED, CarbonText.Weight.REGULAR);
            return;
        }
        text(graphics, selectedModule.description(), moduleInfoRect.x() + design(18),
                moduleInfoRect.y() + design(19), 9, CarbonTheme.TEXT, CarbonText.Weight.MEDIUM);
        text(graphics, "STATUS  ·  " + (selectedModule.enabled() ? "ENABLED" : "DISABLED"),
                moduleInfoRect.x() + design(18), moduleInfoRect.y() + design(43), 7,
                selectedModule.enabled() ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM, CarbonText.Weight.SEMIBOLD);
        CarbonText.centeredUi(graphics, font, "RESET", CarbonText.Weight.SEMIBOLD, 7,
                resetSettingsRect.x() + resetSettingsRect.width() / 2.0f,
                resetSettingsRect.y() + design(13), CarbonTheme.TEXT_MUTED, false);

        for (SettingHit hit : settingHits) {
            Setting<?> setting = hit.setting();
            Rect row = hit.row();
            text(graphics, setting.label(), row.x() + design(15), row.y() + design(15),
                    9, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
            String description = fit(setting.description(), CarbonText.Weight.REGULAR, 7,
                    row.width() - design(215));
            text(graphics, description, row.x() + design(15), row.y() + design(39),
                    7, CarbonTheme.TEXT_DIM, CarbonText.Weight.REGULAR);
            String value;
            int valueColor = CarbonTheme.TEXT;
            if (setting instanceof BoolSetting boolSetting) {
                value = boolSetting.enabled() ? "ON" : "OFF";
                valueColor = boolSetting.enabled() ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM;
            } else if (setting instanceof NumberSetting numberSetting) {
                value = numberSetting.displayValue();
            } else if (setting instanceof ModeSetting modeSetting) {
                value = fit(modeSetting.displayValue(), CarbonText.Weight.MEDIUM, 8, hit.control().width() - design(12));
            } else if (setting instanceof KeybindSetting keybindSetting) {
                value = capturingKeybind == keybindSetting ? "PRESS A KEY..." : bindingLabel(keybindSetting.get());
                value = fit(value, CarbonText.Weight.MEDIUM, 8, hit.control().width() - design(12));
                if (capturingKeybind == keybindSetting) {
                    valueColor = CarbonTheme.TEXT;
                }
            } else if (setting instanceof ColorSetting colorSetting) {
                value = colorSetting.displayValue();
            } else {
                value = setting.displayValue();
            }
            if (setting instanceof NumberSetting numberSetting) {
                float valueWidth = CarbonText.widthUi(font, value, CarbonText.Weight.SEMIBOLD, 8);
                text(graphics, value, hit.control().x() + hit.control().width() - Math.round(valueWidth),
                        row.y() + design(12), 8, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
            } else if (setting instanceof ColorSetting colorSetting) {
                Rect swatch = new Rect(hit.control().x() + design(8), hit.control().y() + design(8), design(18), design(18));
                drawRounded(graphics, swatch, design(5), colorSetting.get());
                text(graphics, value, swatch.right() + design(8), hit.control().y() + design(11),
                        7, CarbonTheme.TEXT_MUTED, CarbonText.Weight.MEDIUM);
            } else {
                CarbonText.centeredUi(graphics, font, value, CarbonText.Weight.SEMIBOLD, 7,
                        hit.control().x() + hit.control().width() / 2.0f,
                        hit.control().y() + design(12), valueColor, false);
            }
        }
    }

    private void extractProfileText(GuiGraphicsExtractor graphics) {
        String active = configManager == null ? "default" : configManager.activeProfile();
        text(graphics, "SAVED PROFILES", profilesListRect.x() + design(14), profilesListRect.y() - design(20),
                8, CarbonTheme.TEXT_DIM, CarbonText.Weight.SEMIBOLD);
        for (ProfileHit hit : profileHits) {
            String name = fit(hit.name(), CarbonText.Weight.SEMIBOLD, 10, hit.bounds().width() - design(120));
            text(graphics, name, hit.bounds().x() + design(15), hit.bounds().y() + design(17),
                    10, CarbonTheme.TEXT, CarbonText.Weight.SEMIBOLD);
            String status = hit.name().equals(active) ? "ACTIVE" : "USE PROFILE";
            float width = CarbonText.widthUi(font, status, CarbonText.Weight.MEDIUM, 7);
            text(graphics, status, hit.bounds().right() - design(12) - Math.round(width),
                    hit.bounds().y() + design(18), 7,
                    hit.name().equals(active) ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM, CarbonText.Weight.MEDIUM);
        }
        text(graphics, "CREATE A PROFILE", profileInputRect.x(), profileInputRect.y() - design(20),
                8, CarbonTheme.TEXT_DIM, CarbonText.Weight.SEMIBOLD);
        CarbonText.centeredUi(graphics, font, "CREATE", CarbonText.Weight.SEMIBOLD, 8,
                profileCreateRect.x() + profileCreateRect.width() / 2.0f,
                profileCreateRect.y() + design(13), CarbonTheme.TEXT, false);
        if (profileHits.isEmpty()) {
            text(graphics, "Your default configuration will appear here.",
                    profilesListRect.x() + design(20), profilesListRect.y() + design(58),
                    8, CarbonTheme.TEXT_DIM, CarbonText.Weight.REGULAR);
        }
    }

    private void extractToast(GuiGraphicsExtractor graphics) {
        long now = System.nanoTime();
        if (toastMessage.isBlank() || now > toastUntilNanos) {
            return;
        }
        int toastWidth = Math.max(design(170), Math.round(
                CarbonText.widthUi(font, toastMessage, CarbonText.Weight.MEDIUM, 8)) + design(30));
        Rect toast = new Rect(panelRect.x() + (panelRect.width() - toastWidth) / 2,
                panelRect.bottom() - design(47), toastWidth, design(29));
        drawPanel(graphics, toast, CarbonTheme.PANEL_HOVER, CarbonTheme.BORDER, design(8));
        CarbonText.centeredUi(graphics, font, toastMessage, CarbonText.Weight.MEDIUM, 8,
                toast.x() + toast.width() / 2.0f, toast.y() + design(9), CarbonTheme.TEXT, false);
    }

    private void drawValuePill(GuiGraphicsExtractor graphics, Rect parent, String value) {
        int pillWidth = Math.max(design(54), Math.round(
                CarbonText.widthUi(font, value, CarbonText.Weight.SEMIBOLD, 7)) + design(18));
        Rect pill = new Rect(parent.right() - design(16) - pillWidth,
                parent.y() + (parent.height() - design(25)) / 2, pillWidth, design(25));
        drawPanel(graphics, pill, CarbonTheme.PANEL_HOVER, CarbonTheme.BORDER, design(7));
        CarbonText.centeredUi(graphics, font, value, CarbonText.Weight.SEMIBOLD, 7,
                pill.x() + pill.width() / 2.0f, pill.y() + design(8), CarbonTheme.TEXT, false);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, Rect rect, int fill, int border, int radius) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        drawRounded(graphics, rect, radius, border);
        if (rect.width() > 2 && rect.height() > 2) {
            Rect inner = new Rect(rect.x() + 1, rect.y() + 1, rect.width() - 2, rect.height() - 2);
            drawRounded(graphics, inner, Math.max(0, radius - 1), fill);
        }
    }

    /** Smooth solid rounded fill; the SDF edge is antialiased without a glass or blur effect. */
    private void drawRounded(GuiGraphicsExtractor graphics, Rect rect, int radius, int color) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        CarbonShapes.drawRounded(graphics, rect.x(), rect.y(), rect.width(), rect.height(), radius, color);
    }

    private List<Module> filteredModules() {
        String query = searchQuery.toLowerCase(Locale.ROOT).trim();
        ArrayList<Module> result = new ArrayList<>();
        for (Module module : modules.modules()) {
            if (selectedCategory != null && module.category() != selectedCategory) {
                continue;
            }
            if (!query.isEmpty() && !module.name().toLowerCase(Locale.ROOT).contains(query)
                    && !module.description().toLowerCase(Locale.ROOT).contains(query)
                    && !module.id().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            result.add(module);
        }
        return result;
    }

    private boolean presetMatches(Preset preset) {
        for (Map.Entry<String, Boolean> state : preset.states().entrySet()) {
            Module module = modules.get(state.getKey());
            if (module != null && module.enabled() != state.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void applyPreset(Preset preset) {
        for (Map.Entry<String, Boolean> state : preset.states().entrySet()) {
            Module module = modules.get(state.getKey());
            if (module != null) {
                module.setEnabled(state.getValue());
            }
        }
        showToast(preset.name() + " preset applied");
    }

    private CardMotion motionFor(Module module) {
        return cardMotions.computeIfAbsent(module.id(), ignored -> new CardMotion());
    }

    private Rect settingControlRect(Setting<?> setting, Rect row) {
        if (setting instanceof NumberSetting) {
            int controlWidth = Math.min(design(178), Math.max(design(84), row.width() / 3));
            return new Rect(row.right() - design(20) - controlWidth,
                    row.y() + design(44), controlWidth, design(20));
        }
        int controlWidth = Math.min(design(174), Math.max(design(94), row.width() / 3));
        return new Rect(row.right() - design(17) - controlWidth,
                row.y() + (row.height() - design(31)) / 2, controlWidth, design(31));
    }

    private void changeSetting(SettingHit hit) {
        Setting<?> setting = hit.setting();
        if (setting instanceof BoolSetting boolSetting) {
            boolSetting.toggle();
        } else if (setting instanceof NumberSetting numberSetting) {
            if (hit.control().contains(lastMouseX, lastMouseY)) {
                updateNumberSetting(numberSetting, hit.control(), lastMouseX);
                draggingNumberSetting = numberSetting;
            }
        } else if (setting instanceof ModeSetting modeSetting) {
            modeSetting.cycle();
        } else if (setting instanceof KeybindSetting keybindSetting) {
            capturingKeybind = keybindSetting;
            showToast("Press a key or mouse button  ·  Backspace clears  ·  Escape cancels");
        } else if (setting instanceof ColorSetting colorSetting) {
            colorSetting.set(nextColor(colorSetting.get()));
        }
    }

    private void updateNumberSetting(NumberSetting setting, Rect control, double mouseX) {
        double fraction = (mouseX - control.x()) / Math.max(1.0, control.width());
        setting.setFromFraction(fraction);
    }

    private int nextColor(int current) {
        for (int index = 0; index < COLOR_CHOICES.length; index++) {
            if (COLOR_CHOICES[index] == current) {
                return COLOR_CHOICES[(index + 1) % COLOR_CHOICES.length];
            }
        }
        return COLOR_CHOICES[0];
    }

    private void updateUiScale(double mouseX, boolean persist) {
        double fraction = (mouseX - scaleTrackRect.x()) / Math.max(1.0, scaleTrackRect.width());
        float scale = 0.75f + (float) Math.max(0.0, Math.min(1.0, fraction)) * 0.75f;
        preferences.setUiScaleLive(scale);
        UiScale.setUserScaleMultiplier(preferences.uiScale());
        UiScale.update(Minecraft.getInstance());
        layoutScale = Math.max(0.001f, UiScale.rendererScale());
        calculateLayout();
        updateWidgetLayout();
        if (persist) {
            preferences.setUiScale(preferences.uiScale());
        }
    }

    private void showToast(String message) {
        toastMessage = message;
        toastUntilNanos = System.nanoTime() + 2_200_000_000L;
    }

    private void setView(View view) {
        activeView = view;
        if (view != View.MODULES && searchBox != null) {
            searchBox.setFocused(false);
        }
        if (view != View.PROFILES && profileNameBox != null) {
            profileNameBox.setFocused(false);
        }
        if (view == View.SETTINGS) {
            settingsPage = SettingsPage.APPEARANCE;
        }
        calculateLayout();
        updateWidgetLayout();
    }

    private void openModuleSettings(Module module) {
        selectedModule = module;
        settingsPage = SettingsPage.MODULE;
        settingsScrollRows = 0;
        setView(View.SETTINGS);
        settingsPage = SettingsPage.MODULE;
        calculateLayout();
    }

    private void resetSelectedModuleSettings() {
        if (selectedModule == null) {
            return;
        }
        for (Setting<?> setting : selectedModule.settings()) {
            setting.reset();
        }
        settingsScrollRows = 0;
        showToast(selectedModule.name() + " settings reset");
    }

    private void createProfile() {
        if (configManager == null || profileNameBox == null) {
            return;
        }
        String name = profileNameBox.getValue().trim();
        if (name.isBlank()) {
            showToast("Enter a profile name first");
            return;
        }
        if (configManager.createProfile(name)) {
            profileNameBox.setValue("");
            profileScrollRows = 0;
            showToast("Profile created: " + name);
        } else {
            showToast("Name unavailable  ·  use letters, numbers, dash or underscore");
        }
    }

    private void fitScaleAndSave() {
        draggingUiScale = false;
        preferences.setUiScale(preferences.uiScale());
    }

    private int contentLeft() {
        return panelRect.x() + sidebarWidth;
    }

    private int design(float value) {
        return Math.max(1, Math.round(value * layoutScale));
    }

    private void text(GuiGraphicsExtractor graphics, String value, float x, float y, float size,
                      int color, CarbonText.Weight weight) {
        CarbonText.drawUi(graphics, font, value, weight, size, x, y, color, false);
    }

    private String fit(String value, CarbonText.Weight weight, float size, int width) {
        if (width <= 0 || value == null || value.isEmpty()) {
            return "";
        }
        if (CarbonText.widthUi(font, value, weight, size) <= width) {
            return value;
        }
        String ellipsis = "…";
        int low = 0;
        int high = value.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            String candidate = value.substring(0, middle) + ellipsis;
            if (CarbonText.widthUi(font, candidate, weight, size) <= width) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return value.substring(0, low) + ellipsis;
    }

    private static String iconFor(Module module) {
        return switch (module.id()) {
            case "fps" -> "gauge";
            case "cps" -> "mouse-pointer-click";
            case "keystrokes" -> "keyboard";
            case "zoom" -> "zoom-in";
            default -> switch (module.category()) {
                case HUD -> "layout-dashboard";
                case VISUAL -> "eye";
                case UTILITY -> "sliders-horizontal";
                case PERFORMANCE -> "gauge";
            };
        };
    }

    private String bindingLabel(KeybindSetting.Binding binding) {
        if (binding.unbound()) {
            return "UNBOUND";
        }
        if (binding.mouse()) {
            return switch (binding.code()) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "MOUSE 1";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "MOUSE 2";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MOUSE 3";
                default -> "MOUSE " + (binding.code() + 1);
            };
        }
        int code = binding.code();
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
            return Character.toString((char) ('A' + code - GLFW.GLFW_KEY_A));
        }
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
            return Character.toString((char) ('0' + code - GLFW.GLFW_KEY_0));
        }
        return switch (code) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RIGHT SHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LEFT SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LEFT CTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RIGHT CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LEFT ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RIGHT ALT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "KEY " + code;
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) Math.round(event.x());
        int mouseY = (int) Math.round(event.y());
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (capturingKeybind != null) {
            capturingKeybind.set(KeybindSetting.Binding.mouseButton(event.button()));
            capturingKeybind = null;
            showToast("Mouse keybind saved");
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        if (closeRect.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (modulesNavRect.contains(mouseX, mouseY)) {
            setView(View.MODULES);
            return true;
        }
        if (settingsNavRect.contains(mouseX, mouseY)) {
            setView(View.SETTINGS);
            return true;
        }
        if (profilesNavRect.contains(mouseX, mouseY)) {
            setView(View.PROFILES);
            return true;
        }
        if (hudEditorNavRect.contains(mouseX, mouseY)) {
            Minecraft.getInstance().gui.setScreen(new CarbonHudEditorScreen(modules.modules(), this));
            return true;
        }
        if (activeView == View.MODULES) {
            if (allCategoryRect.contains(mouseX, mouseY)) {
                selectedCategory = null;
                moduleScrollRows = 0;
                return true;
            }
            for (CategoryHit hit : categoryHits) {
                if (hit.bounds().contains(mouseX, mouseY)) {
                    selectedCategory = hit.category();
                    moduleScrollRows = 0;
                    return true;
                }
            }
            if (!searchQuery.isEmpty() && searchClearRect.contains(mouseX, mouseY)) {
                searchBox.setValue("");
                searchBox.setFocused(true);
                return true;
            }
            for (PresetHit hit : presetHits) {
                if (hit.bounds().contains(mouseX, mouseY)) {
                    applyPreset(hit.preset());
                    return true;
                }
            }
            for (ModuleHit hit : moduleHits) {
                if (hit.settings().contains(mouseX, mouseY)) {
                    openModuleSettings(hit.module());
                    return true;
                }
                if (hit.card().contains(mouseX, mouseY)) {
                    hit.module().toggle();
                    hit.motion().clickUntil = System.nanoTime() + 180_000_000L;
                    return true;
                }
            }
        } else if (activeView == View.SETTINGS) {
            if (settingsPage == SettingsPage.APPEARANCE) {
                if (scaleTrackRect.contains(mouseX, mouseY) || appearanceScaleCard.contains(mouseX, mouseY)
                        && mouseY >= scaleTrackRect.y() - design(7)) {
                    draggingUiScale = true;
                    updateUiScale(mouseX, false);
                    return true;
                }
                if (fontCard.contains(mouseX, mouseY)) {
                    boolean enabled = !preferences.customFont();
                    preferences.setCustomFont(enabled);
                    CarbonText.setCustomFontEnabled(enabled);
                    showToast(enabled ? "Inter font enabled" : "Minecraft fallback font enabled");
                    return true;
                }
            } else {
                if (resetSettingsRect.contains(mouseX, mouseY)) {
                    resetSelectedModuleSettings();
                    return true;
                }
                for (SettingHit hit : settingHits) {
                    if (hit.row().contains(mouseX, mouseY)) {
                        changeSetting(hit);
                        return true;
                    }
                }
            }
        } else if (activeView == View.PROFILES) {
            if (profileCreateRect.contains(mouseX, mouseY)) {
                createProfile();
                return true;
            }
            for (ProfileHit hit : profileHits) {
                if (hit.bounds().contains(mouseX, mouseY) && configManager != null) {
                    if (configManager.switchProfile(hit.name())) {
                        showToast("Loaded profile: " + hit.name());
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private double lastMouseX;
    private double lastMouseY;

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingUiScale && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateUiScale(event.x(), false);
            return true;
        }
        if (draggingNumberSetting != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            SettingHit current = settingHits.stream().filter(hit -> hit.setting() == draggingNumberSetting)
                    .findFirst().orElse(null);
            if (current != null) {
                updateNumberSetting(draggingNumberSetting, current.control(), event.x());
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingUiScale) {
            fitScaleAndSave();
            return true;
        }
        draggingNumberSetting = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int rows = verticalAmount < 0.0 ? 1 : -1;
        if (activeView == View.MODULES && moduleGridRect.contains((int) mouseX, (int) mouseY)) {
            int totalRows = (filteredModules().size() + moduleColumns - 1) / moduleColumns;
            moduleScrollRows = Math.max(0, Math.min(Math.max(0, totalRows - moduleVisibleRows), moduleScrollRows + rows));
            return true;
        }
        if (activeView == View.SETTINGS && settingsPage == SettingsPage.MODULE
                && settingsListRect.contains((int) mouseX, (int) mouseY)) {
            int total = selectedModule == null ? 0 : selectedModule.settings().size();
            int visible = Math.max(1, (settingsListRect.height() + design(8)) / Math.max(1, design(81)));
            settingsScrollRows = Math.max(0, Math.min(Math.max(0, total - visible), settingsScrollRows + rows));
            return true;
        }
        if (activeView == View.PROFILES && profilesListRect.contains((int) mouseX, (int) mouseY)) {
            int total = configManager == null ? 1 : configManager.listProfiles().size();
            int visible = Math.max(1, (profilesListRect.height() + design(8)) / Math.max(1, design(56)));
            profileScrollRows = Math.max(0, Math.min(Math.max(0, total - visible), profileScrollRows + rows));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (capturingKeybind != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                capturingKeybind = null;
                showToast("Keybind capture cancelled");
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                capturingKeybind.set(KeybindSetting.Binding.unboundBinding());
                capturingKeybind = null;
                showToast("Keybind cleared");
            } else if (key >= 0) {
                capturingKeybind.set(KeybindSetting.Binding.keyboard(key));
                capturingKeybind = null;
                showToast("Keybind saved: " + bindingLabel(KeybindSetting.Binding.keyboard(key)));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && activeView == View.PROFILES) {
            createProfile();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        capturingKeybind = null;
        super.onClose();
    }

    private enum View {
        MODULES,
        SETTINGS,
        PROFILES
    }

    private enum SettingsPage {
        APPEARANCE,
        MODULE
    }

    private record Preset(String name, Map<String, Boolean> states) {
    }

    private record ModuleHit(Module module, Rect card, Rect settings, CardMotion motion) {
    }

    private record SettingHit(Setting<?> setting, Rect row, Rect control) {
    }

    private record PresetHit(Preset preset, Rect bounds) {
    }

    private record CategoryHit(Category category, Rect bounds) {
    }

    private record ProfileHit(String name, Rect bounds) {
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
    }

    private static final class CardMotion {
        private float hover;
        private float enabled;
        private float enter;
        private float clickPulse;
        private long clickUntil;

        private void update(long elapsed, boolean isEnabled, boolean isHovered) {
            hover = CarbonAnimation.approach(hover, isHovered ? 1.0f : 0.0f, elapsed, 0.11f);
            enabled = CarbonAnimation.approach(enabled, isEnabled ? 1.0f : 0.0f, elapsed, 0.16f);
            enter = CarbonAnimation.approach(enter, 1.0f, elapsed, 0.19f);
            clickPulse = CarbonAnimation.approach(clickPulse,
                    System.nanoTime() < clickUntil ? 1.0f : 0.0f, elapsed, 0.12f);
        }
    }
}
