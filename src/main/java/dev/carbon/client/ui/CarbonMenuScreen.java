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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Live Carbon Mods screen: real module state, typed settings, profiles, filters, and pins. */
public final class CarbonMenuScreen extends CarbonScreen {
    private static final float PANEL_WIDTH = 1000.0f;
    private static final float PANEL_HEIGHT = 612.0f;
    private static final float HEADER_HEIGHT = 64.0f;
    private static final float SIDEBAR_WIDTH = 214.0f;
    private static final float CARD_WIDTH = 238.0f;
    private static final float CARD_HEIGHT = 228.0f;
    private static final float CARD_GAP = 14.0f;
    private static final int MAX_PROFILE_ROWS = 12;
    private static final int PROFILE_POPOVER_ROWS = 7;
    private static final int MAX_SETTINGS_ROWS = 12;
    private static final int[] COLOR_PRESETS = {
            0xFFFFFFFF, 0xFF1FC76F, 0xFF47B8FF, 0xFFFFB547, 0xFFFF5968, 0xFFB58CFF
    };
    private static final String PROFILE_PATTERN = "[a-z0-9][a-z0-9_-]{0,31}";

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final CarbonUiState uiState;
    private final List<Module> visibleModules = new ArrayList<>();
    private final List<String> profiles = new ArrayList<>();
    private final List<Box> moduleBounds = new ArrayList<>();
    private final List<Box> toggleBounds = new ArrayList<>();
    private final List<Box> pinBounds = new ArrayList<>();
    private final List<Box> settingsBounds = new ArrayList<>();
    private final List<Box> sidebarProfileBounds = new ArrayList<>();
    private final List<Box> profilePopoverBounds = new ArrayList<>();
    private final List<Box> settingBounds = new ArrayList<>();
    private final List<Box> settingSliderBounds = new ArrayList<>();
    private final List<Box> settingValueBounds = new ArrayList<>();
    private final List<Box> settingBoolBounds = new ArrayList<>();
    private final Box[] sortOptionBounds = {new Box(), new Box(), new Box()};

    private final Box panel = new Box();
    private final Box header = new Box();
    private final Box sidebar = new Box();
    private final Box content = new Box();
    private final Box profileButton = new Box();
    private final Box createProfileButton = new Box();
    private final Box searchBox = new Box();
    private final Box clearSearchButton = new Box();
    private final Box gridButton = new Box();
    private final Box listButton = new Box();
    private final Box sortButton = new Box();
    private final Box closeButton = new Box();
    private final Box sortPanel = new Box();
    private final Box sortOrderButton = new Box();
    private final Box profilePanel = new Box();
    private final Box profileCreatePanel = new Box();
    private final Box profileInputBox = new Box();
    private final Box profileConfirmButton = new Box();
    private final Box profileCancelButton = new Box();
    private final Box settingsPanel = new Box();
    private final Box settingsCloseButton = new Box();
    private final Box settingsResetButton = new Box();
    private final Box settingsViewport = new Box();
    private final Box[] categoryBounds = new Box[CarbonUiState.Filter.values().length];

    private CarbonUiState.Filter filter;
    private CarbonUiState.ViewMode viewMode;
    private CarbonUiState.SortMode sortMode;
    private boolean descending;
    private String searchQuery;
    private String profileDraft = "";
    private String hoverText = "";
    private long hoverStartedAt;
    private long toastUntilNanos;
    private String toastMessage = "";
    private int selectedModuleIndex;
    private int selectedProfileIndex;
    private int selectedSettingIndex;
    private int moduleScroll;
    private int profileScroll;
    private int settingsScroll;
    private int hoveredModuleIndex = -1;
    private int pressedColorIndex;
    private boolean searchFocused;
    private boolean profilePopoverOpen;
    private boolean profileCreateOpen;
    private boolean sortPopoverOpen;
    private boolean settingsPopoverOpen;
    private boolean layoutDebug;
    private boolean draggingNumber;
    private NumberSetting draggedNumber;
    private KeybindSetting capturingKeybind;
    private Module settingsModule;
    private float mouseDesignX;
    private float mouseDesignY;
    private GuiGraphicsExtractor currentGraphics;
    private long lastBlinkNanos;
    private boolean caretVisible = true;

    public CarbonMenuScreen(ModuleManager modules, ConfigManager configManager) {
        super(Component.literal("Carbon Client"), null);
        this.modules = Objects.requireNonNull(modules, "modules");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        Path statePath = FabricLoader.getInstance().getConfigDir().resolve("carbonclient").resolve("ui.json");
        this.uiState = CarbonUiState.load(statePath);
        this.filter = uiState.filter();
        this.viewMode = uiState.viewMode();
        this.sortMode = uiState.sortMode();
        this.descending = uiState.descending();
        this.searchQuery = uiState.query();
        for (int index = 0; index < CarbonUiState.Filter.values().length; index++) {
            categoryBounds[index] = new Box();
        }
        for (int index = 0; index < MAX_PROFILE_ROWS; index++) {
            sidebarProfileBounds.add(new Box());
            profilePopoverBounds.add(new Box());
        }
        for (int index = 0; index < MAX_SETTINGS_ROWS; index++) {
            settingBounds.add(new Box());
            settingSliderBounds.add(new Box());
            settingValueBounds.add(new Box());
            settingBoolBounds.add(new Box());
        }
        rebuildProfiles();
        rebuildVisibleModules();
    }

    @Override
    protected void init() {
        super.init();
        UiScale.update(Minecraft.getInstance());
        CarbonIcons.load();
        calculateLayout();
        rebuildVisibleModules();
    }

    private void calculateLayout() {
        float windowX = (UiScale.designWidth() - PANEL_WIDTH) * 0.5f;
        float windowY = (UiScale.designHeight() - PANEL_HEIGHT) * 0.5f;
        panel.set(windowX, windowY, PANEL_WIDTH, PANEL_HEIGHT);
        header.set(windowX, windowY, PANEL_WIDTH, HEADER_HEIGHT);
        sidebar.set(windowX, windowY + HEADER_HEIGHT, SIDEBAR_WIDTH, PANEL_HEIGHT - HEADER_HEIGHT);
        content.set(windowX + SIDEBAR_WIDTH, windowY + HEADER_HEIGHT,
                PANEL_WIDTH - SIDEBAR_WIDTH, PANEL_HEIGHT - HEADER_HEIGHT);
        profileButton.set(windowX + 12.0f, windowY + 82.0f, 190.0f, 36.0f);
        createProfileButton.set(windowX + 12.0f, windowY + 548.0f, 190.0f, 34.0f);
        searchBox.set(windowX + 500.0f, windowY + 78.0f, 218.0f, 32.0f);
        clearSearchButton.set(searchBox.x + searchBox.width - 28.0f, searchBox.y + 1.0f, 26.0f, 30.0f);
        gridButton.set(windowX + 728.0f, windowY + 79.0f, 30.0f, 30.0f);
        listButton.set(windowX + 763.0f, windowY + 79.0f, 30.0f, 30.0f);
        sortButton.set(windowX + 800.0f, windowY + 79.0f, 184.0f, 32.0f);
        closeButton.set(windowX + PANEL_WIDTH - 50.0f, windowY + 14.0f, 34.0f, 34.0f);

        float categoryY = windowY + 160.0f;
        for (int index = 0; index < categoryBounds.length; index++) {
            categoryBounds[index].set(windowX + 12.0f, categoryY, 190.0f, 30.0f);
            categoryY += 34.0f;
        }

        for (int index = 0; index < sidebarProfileBounds.size(); index++) {
            sidebarProfileBounds.get(index).set(windowX + 12.0f, windowY + 426.0f + index * 28.0f,
                    190.0f, 26.0f);
        }

        updateModuleBounds();
        updatePopoverBounds();
    }

    private void updateModuleBounds() {
        ensureModuleBoundsCapacity(visibleModules.size());
        float gridX = panel.x + SIDEBAR_WIDTH + 16.0f;
        float gridY = panel.y + 124.0f;
        float viewportTop = panel.y + 118.0f;
        float viewportBottom = panel.y + PANEL_HEIGHT - 12.0f;
        for (int index = 0; index < visibleModules.size(); index++) {
            Box card = moduleBounds.get(index);
            Box toggle = toggleBounds.get(index);
            Box pin = pinBounds.get(index);
            Box settings = settingsBounds.get(index);
            if (viewMode == CarbonUiState.ViewMode.GRID) {
                int columns = 3;
                int row = index / columns - moduleScroll;
                int column = index % columns;
                float x = gridX + column * (CARD_WIDTH + CARD_GAP);
                float y = gridY + row * (CARD_HEIGHT + CARD_GAP);
                card.set(x, y, CARD_WIDTH, CARD_HEIGHT);
                toggle.set(x + 12.0f, y + 187.0f, CARD_WIDTH - 24.0f, 30.0f);
                pin.set(x + CARD_WIDTH - 58.0f, y + 10.0f, 26.0f, 26.0f);
                settings.set(x + CARD_WIDTH - 30.0f, y + 10.0f, 26.0f, 26.0f);
                if (row < 0 || y + CARD_HEIGHT < viewportTop || y > viewportBottom) {
                    card.set(-10_000.0f, -10_000.0f, 0.0f, 0.0f);
                }
            } else {
                float rowHeight = 58.0f;
                float x = gridX;
                float y = gridY + (index - moduleScroll) * (rowHeight + 8.0f);
                card.set(x, y, 742.0f, rowHeight);
                toggle.set(x + 620.0f, y + 13.0f, 82.0f, 32.0f);
                pin.set(x + 574.0f, y + 15.0f, 26.0f, 26.0f);
                settings.set(x + 708.0f, y + 14.0f, 26.0f, 30.0f);
                if (y + rowHeight < viewportTop || y > viewportBottom) {
                    card.set(-10_000.0f, -10_000.0f, 0.0f, 0.0f);
                }
            }
        }
    }

    private void updatePopoverBounds() {
        float winRight = panel.x + panel.width;
        profilePanel.set(panel.x + 183.0f, panel.y + 76.0f, 250.0f, 268.0f);
        for (int index = 0; index < profilePopoverBounds.size(); index++) {
            profilePopoverBounds.get(index).set(profilePanel.x + 12.0f,
                    profilePanel.y + 50.0f + index * 26.0f, profilePanel.width - 24.0f, 24.0f);
        }
        profileCreatePanel.set(panel.x + 320.0f, panel.y + 190.0f, 360.0f, 214.0f);
        profileInputBox.set(profileCreatePanel.x + 24.0f, profileCreatePanel.y + 82.0f, 312.0f, 36.0f);
        profileConfirmButton.set(profileCreatePanel.x + 184.0f, profileCreatePanel.y + 154.0f, 152.0f, 34.0f);
        profileCancelButton.set(profileCreatePanel.x + 24.0f, profileCreatePanel.y + 154.0f, 148.0f, 34.0f);
        sortPanel.set(Math.min(winRight - 175.0f, sortButton.x + sortButton.width - 162.0f),
                sortButton.y + sortButton.height + 8.0f, 162.0f, 166.0f);
        sortOrderButton.set(sortPanel.x + 12.0f, sortPanel.y + 128.0f, sortPanel.width - 24.0f, 26.0f);
        for (int index = 0; index < sortOptionBounds.length; index++) {
            sortOptionBounds[index].set(sortPanel.x + 10.0f, sortPanel.y + 34.0f + index * 30.0f,
                    sortPanel.width - 20.0f, 27.0f);
        }
        settingsPanel.set(panel.x + (PANEL_WIDTH - 456.0f) * 0.5f,
                panel.y + 86.0f, 456.0f, 470.0f);
        settingsCloseButton.set(settingsPanel.x + settingsPanel.width - 42.0f,
                settingsPanel.y + 14.0f, 28.0f, 28.0f);
        settingsResetButton.set(settingsPanel.x + 16.0f,
                settingsPanel.y + settingsPanel.height - 46.0f, 116.0f, 30.0f);
        settingsViewport.set(settingsPanel.x + 14.0f, settingsPanel.y + 72.0f,
                settingsPanel.width - 28.0f, settingsPanel.height - 128.0f);
        for (int index = 0; index < settingBounds.size(); index++) {
            Box row = settingBounds.get(index);
            row.set(settingsPanel.x + 16.0f, settingsViewport.y + index * 56.0f,
                    settingsPanel.width - 32.0f, 52.0f);
            settingSliderBounds.get(index).set(row.x + 8.0f, row.y + 36.0f, row.width - 16.0f, 12.0f);
            settingValueBounds.get(index).set(row.x + row.width - 144.0f, row.y + 10.0f, 128.0f, 28.0f);
            settingBoolBounds.get(index).set(row.x + row.width - 64.0f, row.y + 17.0f, 46.0f, 20.0f);
        }
    }

    private void ensureModuleBoundsCapacity(int size) {
        while (moduleBounds.size() < size) {
            moduleBounds.add(new Box());
            toggleBounds.add(new Box());
            pinBounds.add(new Box());
            settingsBounds.add(new Box());
        }
    }

    private void rebuildProfiles() {
        profiles.clear();
        profiles.addAll(configManager.listProfiles());
        if (profiles.size() > PROFILE_POPOVER_ROWS) {
            profileScroll = Math.min(profileScroll, profiles.size() - PROFILE_POPOVER_ROWS);
        } else {
            profileScroll = 0;
        }
    }

    private void rebuildVisibleModules() {
        visibleModules.clear();
        String query = searchQuery.toLowerCase(Locale.ROOT).strip();
        for (Module module : modules.modules()) {
            if (!matchesFilter(module) || !matchesQuery(module, query)) {
                continue;
            }
            visibleModules.add(module);
        }
        Comparator<Module> comparator = switch (sortMode) {
            case NAME -> Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER);
            case CATEGORY -> Comparator.comparing((Module module) -> module.category().label(),
                    String.CASE_INSENSITIVE_ORDER).thenComparing(Module::name, String.CASE_INSENSITIVE_ORDER);
            case ENABLED -> Comparator.comparing(Module::enabled).reversed()
                    .thenComparing(Module::name, String.CASE_INSENSITIVE_ORDER);
        };
        if (descending) {
            comparator = comparator.reversed();
        }
        visibleModules.sort(comparator);
        selectedModuleIndex = Math.max(0, Math.min(selectedModuleIndex, visibleModules.size() - 1));
        moduleScroll = Math.max(0, Math.min(moduleScroll, maximumModuleScroll()));
        updateModuleBounds();
    }

    private boolean matchesFilter(Module module) {
        return switch (filter) {
            case ALL -> true;
            case HUD -> module.category() == Category.HUD;
            case VISUAL -> module.category() == Category.VISUAL;
            case UTILITY -> module.category() == Category.UTILITY;
            case PERFORMANCE -> module.category() == Category.PERFORMANCE;
            case PINNED -> uiState.isPinned(module.id());
            case ENABLED -> module.enabled();
        };
    }

    private static boolean matchesQuery(Module module, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (contains(module.id(), query) || contains(module.name(), query)
                || contains(module.description(), query) || contains(module.category().label(), query)) {
            return true;
        }
        for (Setting<?> setting : module.settings()) {
            if (contains(setting.id(), query) || contains(setting.label(), query)
                    || contains(setting.description(), query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (UiScale.update(Minecraft.getInstance())) {
            calculateLayout();
        }
        mouseDesignX = UiScale.toDesign(mouseX);
        mouseDesignY = UiScale.toDesign(mouseY);
        updateHover();
        updateCaret();

        if (!CarbonShapes.isReady()) {
            UiScale.pushRendererScale(graphics);
            try {
                drawRenderFailure(graphics);
            } finally {
                UiScale.popRendererScale(graphics);
            }
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        currentGraphics = graphics;
        UiScale.pushRendererScale(graphics);
        try {
            drawBaseSurfaces(graphics);
            drawModuleSurfaces(graphics);
            drawPopoverSurfaces(graphics);
            graphics.nextStratum();
            drawBaseText(graphics);
            drawModuleText(graphics);
            drawPopoverText(graphics);
            graphics.nextStratum();
            drawTooltipAndToast(graphics);
            if (layoutDebug) {
                drawLayoutDebug(graphics);
            }
        } finally {
            UiScale.popRendererScale(graphics);
            currentGraphics = null;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawBaseSurfaces(GuiGraphicsExtractor graphics) {
        CarbonShapes.drawRounded(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                0.0f, CarbonTheme.BACKDROP);
        CarbonShapes.drawVignette(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                CarbonTheme.VIGNETTE);
        CarbonShapes.drawShadow(graphics, panel.x, panel.y, panel.width, panel.height,
                12.0f, 64.0f, 24.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, panel.x, panel.y, panel.width, panel.height,
                12.0f, 1.0f, CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.WINDOW_TOP, CarbonTheme.WINDOW_BOTTOM);
        CarbonShapes.drawTopHighlight(graphics, panel.x + 1.0f, panel.y + 1.0f,
                panel.width - 2.0f, 12.0f, CarbonTheme.WINDOW_HIGHLIGHT);
        CarbonShapes.drawGradientRect(graphics, header.x + 1.0f, header.y + 1.0f,
                header.width - 2.0f, header.height, 11.0f, CarbonTheme.HEADER_TOP, CarbonTheme.HEADER_BOTTOM);
        CarbonShapes.drawGradientRect(graphics, sidebar.x + 1.0f, sidebar.y + 7.0f,
                sidebar.width, sidebar.height - 8.0f, 11.0f, CarbonTheme.SIDEBAR_TOP, CarbonTheme.SIDEBAR_BOTTOM);
        CarbonShapes.drawGradientRect(graphics, content.x + 1.0f, content.y + 7.0f,
                content.width - 2.0f, content.height - 8.0f, 10.0f,
                CarbonTheme.CONTENT_TOP, CarbonTheme.CONTENT_BOTTOM);
        CarbonShapes.drawGradientRect(graphics, panel.x + 1.0f, panel.y + 63.0f,
                panel.width - 2.0f, 1.0f, 0.0f, CarbonTheme.DIVIDER, CarbonTheme.DIVIDER);
        CarbonShapes.drawGradientRect(graphics, sidebar.x + SIDEBAR_WIDTH - 1.0f,
                sidebar.y + 18.0f, 1.0f, sidebar.height - 36.0f, 0.0f,
                CarbonTheme.DIVIDER, CarbonTheme.DIVIDER);

        CarbonShapes.drawBorderedSurface(graphics, profileButton.x, profileButton.y,
                profileButton.width, profileButton.height, 8.0f, 1.0f,
                selectedProfile() ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER,
                selectedProfile() ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER,
                CarbonTheme.BUTTON_TOP, CarbonTheme.BUTTON_BOTTOM);
        CarbonShapes.drawTopHighlight(graphics, profileButton.x + 1.0f, profileButton.y + 1.0f,
                profileButton.width - 2.0f, 8.0f, CarbonTheme.WINDOW_HIGHLIGHT);
        drawButton(graphics, createProfileButton, false, true, 8.0f);
        drawInput(graphics, searchBox, searchFocused);
        drawButton(graphics, gridButton, viewMode == CarbonUiState.ViewMode.GRID, false, 7.0f);
        drawButton(graphics, listButton, viewMode == CarbonUiState.ViewMode.LIST, false, 7.0f);
        drawButton(graphics, sortButton, sortPopoverOpen, false, 7.0f);
        drawButton(graphics, closeButton, closeButton.contains(mouseDesignX, mouseDesignY), false, 7.0f);
        if (!searchQuery.isEmpty()) {
            CarbonShapes.drawRounded(graphics, clearSearchButton.x + 8.0f, clearSearchButton.y + 8.0f,
                    12.0f, 12.0f, 6.0f, CarbonTheme.RED_SOFT);
        }

        for (int index = 0; index < categoryBounds.length; index++) {
            CarbonUiState.Filter item = CarbonUiState.Filter.values()[index];
            Box bounds = categoryBounds[index];
            boolean active = item == filter;
            if (active || bounds.contains(mouseDesignX, mouseDesignY)) {
                int top = active ? CarbonTheme.CARD_ACTIVE_WASH_TOP : CarbonTheme.BUTTON_HOVER_TOP;
                int bottom = active ? CarbonTheme.CARD_ACTIVE_WASH_BOTTOM : CarbonTheme.BUTTON_HOVER_BOTTOM;
                int border = active ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER;
                CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y, bounds.width, bounds.height,
                        7.0f, 1.0f, border, border, top, bottom);
            }
        }

        drawProfileRows(graphics);
    }

    private void drawPopoverSurfaces(GuiGraphicsExtractor graphics) {
        drawProfilePopoverSurface(graphics);
        drawSortPopoverSurface(graphics);
        drawCreateProfileSurface(graphics);
        drawSettingsPopoverSurface(graphics);
    }

    private void drawProfileRows(GuiGraphicsExtractor graphics) {
        int rowCount = Math.min(3, Math.max(0, profiles.size()));
        for (int index = 0; index < rowCount; index++) {
            int profileIndex = index + profileScroll;
            if (profileIndex >= profiles.size()) {
                break;
            }
            Box row = sidebarProfileBounds.get(index);
            if (row.contains(mouseDesignX, mouseDesignY)) {
                CarbonShapes.drawBorderedSurface(graphics, row.x, row.y, row.width, row.height,
                        6.0f, 1.0f, CarbonTheme.BUTTON_BORDER, CarbonTheme.BUTTON_BORDER,
                        CarbonTheme.BUTTON_HOVER_TOP, CarbonTheme.BUTTON_HOVER_BOTTOM);
            }
        }
    }

    private void drawProfilePopoverSurface(GuiGraphicsExtractor graphics) {
        if (!profilePopoverOpen) {
            return;
        }
        CarbonShapes.drawShadow(graphics, profilePanel.x, profilePanel.y,
                profilePanel.width, profilePanel.height, 10.0f, 16.0f, 6.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, profilePanel.x, profilePanel.y,
                profilePanel.width, profilePanel.height, 10.0f, 1.0f,
                CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
        int count = Math.min(PROFILE_POPOVER_ROWS, Math.max(0, profiles.size() - profileScroll));
        for (int index = 0; index < count; index++) {
            Box row = profilePopoverBounds.get(index);
            if (row.contains(mouseDesignX, mouseDesignY)
                    || index + profileScroll == selectedProfileIndex) {
                CarbonShapes.drawRounded(graphics, row.x, row.y, row.width, row.height,
                        6.0f, CarbonTheme.BUTTON_HOVER_TOP);
            }
            if (profiles.get(index + profileScroll).equals(configManager.activeProfile())) {
                CarbonShapes.drawRounded(graphics, row.x + 4.0f, row.y + 6.0f,
                        3.0f, 12.0f, 1.0f, CarbonTheme.ACCENT);
            }
        }
    }

    private void drawSortPopoverSurface(GuiGraphicsExtractor graphics) {
        if (!sortPopoverOpen) {
            return;
        }
        CarbonShapes.drawShadow(graphics, sortPanel.x, sortPanel.y,
                sortPanel.width, sortPanel.height, 8.0f, 14.0f, 4.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, sortPanel.x, sortPanel.y,
                sortPanel.width, sortPanel.height, 8.0f, 1.0f,
                CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
        for (int index = 0; index < CarbonUiState.SortMode.values().length; index++) {
            Box row = sortOptionBounds[index];
            if (row.contains(mouseDesignX, mouseDesignY)) {
                CarbonShapes.drawRounded(graphics, row.x, row.y, row.width, row.height,
                        5.0f, CarbonTheme.BUTTON_HOVER_TOP);
            }
        }
        drawButton(graphics, sortOrderButton, false, false, 6.0f);
    }

    private void drawCreateProfileSurface(GuiGraphicsExtractor graphics) {
        if (!profileCreateOpen) {
            return;
        }
        CarbonShapes.drawRounded(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                0.0f, 0x70000000);
        CarbonShapes.drawShadow(graphics, profileCreatePanel.x, profileCreatePanel.y,
                profileCreatePanel.width, profileCreatePanel.height, 12.0f, 24.0f, 10.0f,
                CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, profileCreatePanel.x, profileCreatePanel.y,
                profileCreatePanel.width, profileCreatePanel.height, 12.0f, 1.0f,
                CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
        CarbonShapes.drawTopHighlight(graphics, profileCreatePanel.x + 1.0f, profileCreatePanel.y + 1.0f,
                profileCreatePanel.width - 2.0f, 12.0f, CarbonTheme.WINDOW_HIGHLIGHT);
        drawInput(graphics, profileInputBox, true);
        drawButton(graphics, profileConfirmButton, true, false, 7.0f);
        drawButton(graphics, profileCancelButton, false, false, 7.0f);
    }

    private void drawSettingsPopoverSurface(GuiGraphicsExtractor graphics) {
        if (!settingsPopoverOpen || settingsModule == null) {
            return;
        }
        CarbonShapes.drawRounded(graphics, 0.0f, 0.0f, UiScale.designWidth(), UiScale.designHeight(),
                0.0f, 0x66000000);
        CarbonShapes.drawShadow(graphics, settingsPanel.x, settingsPanel.y,
                settingsPanel.width, settingsPanel.height, 12.0f, 24.0f, 10.0f,
                CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, settingsPanel.x, settingsPanel.y,
                settingsPanel.width, settingsPanel.height, 12.0f, 1.0f,
                CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
        CarbonShapes.drawTopHighlight(graphics, settingsPanel.x + 1.0f, settingsPanel.y + 1.0f,
                settingsPanel.width - 2.0f, 12.0f, CarbonTheme.WINDOW_HIGHLIGHT);
        drawButton(graphics, settingsCloseButton, settingsCloseButton.contains(mouseDesignX, mouseDesignY), false, 6.0f);
        drawButton(graphics, settingsResetButton, settingsResetButton.contains(mouseDesignX, mouseDesignY), false, 6.0f);

        int settingsCount = settingsModule.settings().size();
        int rowCount = Math.min(MAX_SETTINGS_ROWS, Math.max(0, settingsCount - settingsScroll));
        for (int index = 0; index < rowCount; index++) {
            Box row = settingBounds.get(index);
            if (row.y + row.height < settingsViewport.y || row.y > settingsViewport.y + settingsViewport.height) {
                continue;
            }
            boolean selected = index + settingsScroll == selectedSettingIndex;
            CarbonShapes.drawRounded(graphics, row.x, row.y, row.width, row.height,
                    7.0f, row.contains(mouseDesignX, mouseDesignY) || selected
                            ? CarbonTheme.BUTTON_HOVER_BOTTOM : 0x321D2024);
            Setting<?> setting = settingsModule.settings().get(index + settingsScroll);
            Box slider = settingSliderBounds.get(index);
            Box value = settingValueBounds.get(index);
            Box boolControl = settingBoolBounds.get(index);
            if (setting instanceof NumberSetting number) {
                float fraction = numberFraction(number);
                CarbonShapes.drawRounded(graphics, slider.x, slider.y + 4.0f,
                        slider.width, 4.0f, 2.0f, CarbonTheme.TRACK_OFF);
                CarbonShapes.drawRounded(graphics, slider.x, slider.y + 4.0f,
                        slider.width * fraction, 4.0f, 2.0f, CarbonTheme.ACCENT);
                CarbonShapes.drawRounded(graphics, slider.x + slider.width * fraction - 5.0f,
                        slider.y + 1.0f, 10.0f, 10.0f, 5.0f, CarbonTheme.TEXT);
            } else if (setting instanceof BoolSetting bool) {
                CarbonShapes.drawRounded(graphics, boolControl.x, boolControl.y,
                        boolControl.width, boolControl.height, 10.0f,
                        bool.enabled() ? CarbonTheme.ACCENT : CarbonTheme.TRACK_OFF);
                CarbonShapes.drawRounded(graphics,
                        boolControl.x + (bool.enabled() ? 27.0f : 3.0f), boolControl.y + 3.0f,
                        14.0f, 14.0f, 7.0f, CarbonTheme.TEXT);
            } else if (setting instanceof ModeSetting) {
                drawButton(graphics, value, value.contains(mouseDesignX, mouseDesignY), false, 6.0f);
            } else if (setting instanceof KeybindSetting) {
                drawButton(graphics, value, capturingKeybind == setting, false, 6.0f);
            } else if (setting instanceof ColorSetting color) {
                CarbonShapes.drawBorderedSurface(graphics, value.x, value.y,
                        value.width, value.height, 6.0f, 1.0f,
                        CarbonTheme.BUTTON_BORDER, CarbonTheme.BUTTON_BORDER,
                        color.get(), color.get());
            }
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, Box bounds, boolean hovered,
                            boolean active, float radius) {
        int top = active ? CarbonTheme.ACCENT : hovered ? CarbonTheme.BUTTON_HOVER_TOP : CarbonTheme.BUTTON_TOP;
        int bottom = active ? CarbonTheme.ACCENT_DEEP : hovered ? CarbonTheme.BUTTON_HOVER_BOTTOM : CarbonTheme.BUTTON_BOTTOM;
        int border = active ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER;
        CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y, bounds.width, bounds.height,
                radius, 1.0f, border, border, top, bottom);
        if (!active) {
            CarbonShapes.drawTopHighlight(graphics, bounds.x + 1.0f, bounds.y + 1.0f,
                    bounds.width - 2.0f, radius, CarbonTheme.WINDOW_HIGHLIGHT);
        }
    }

    private void drawInput(GuiGraphicsExtractor graphics, Box bounds, boolean focused) {
        int border = focused ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.INPUT_BORDER;
        CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y, bounds.width, bounds.height,
                7.0f, 1.0f, border, border, CarbonTheme.INPUT_TOP, CarbonTheme.INPUT_BOTTOM);
    }

    private void drawModuleSurfaces(GuiGraphicsExtractor graphics) {
        for (int index = 0; index < visibleModules.size(); index++) {
            Module module = visibleModules.get(index);
            Box card = moduleBounds.get(index);
            if (card.width <= 0.0f || card.height <= 0.0f) {
                continue;
            }
            boolean hovered = card.contains(mouseDesignX, mouseDesignY);
            boolean selected = index == selectedModuleIndex;
            boolean pinned = uiState.isPinned(module.id());
            int border = selected ? CarbonTheme.CARD_ACTIVE_BORDER
                    : module.enabled() ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.CARD_BORDER;
            int top = hovered ? CarbonTheme.CARD_HOVER_TOP : CarbonTheme.CARD_TOP;
            int bottom = hovered ? CarbonTheme.CARD_HOVER_BOTTOM : CarbonTheme.CARD_BOTTOM;
            CarbonShapes.drawShadow(graphics, card.x, card.y, card.width, card.height,
                    12.0f, 18.0f, 5.0f, CarbonTheme.WINDOW_SHADOW);
            CarbonShapes.drawBorderedSurface(graphics, card.x, card.y, card.width, card.height,
                    12.0f, 1.0f, border, border, top, bottom);
            CarbonShapes.drawTopHighlight(graphics, card.x + 1.0f, card.y + 1.0f,
                    card.width - 2.0f, 12.0f, CarbonTheme.WINDOW_HIGHLIGHT);
            if (module.enabled()) {
                CarbonShapes.drawGradientRect(graphics, card.x + 1.0f, card.y + 1.0f,
                        card.width - 2.0f, card.height - 2.0f, 11.0f,
                        CarbonTheme.CARD_ACTIVE_WASH_TOP, CarbonTheme.CARD_ACTIVE_WASH_BOTTOM);
            }
            drawButton(graphics, toggleBounds.get(index), toggleBounds.get(index).contains(mouseDesignX, mouseDesignY),
                    module.enabled(), 7.0f);
            if (pinBounds.get(index).contains(mouseDesignX, mouseDesignY) || pinned) {
                CarbonShapes.drawRounded(graphics, pinBounds.get(index).x + 3.0f,
                        pinBounds.get(index).y + 3.0f, 20.0f, 20.0f, 6.0f,
                        pinned ? CarbonTheme.ACCENT_SOFT : CarbonTheme.BUTTON_HOVER_BOTTOM);
            }
            drawButton(graphics, settingsBounds.get(index),
                    settingsBounds.get(index).contains(mouseDesignX, mouseDesignY), false, 6.0f);
        }
    }

    private void drawBaseText(GuiGraphicsExtractor graphics) {
        drawTracked(graphics, "CARBON", CarbonText.Weight.BOLD, 18.0f,
                panel.x + 24.0f, panel.y + 17.0f, CarbonTheme.TEXT, CarbonText.Tracking.WORDMARK);
        drawTracked(graphics, "CLIENT", CarbonText.Weight.MEDIUM, 9.0f,
                panel.x + 24.0f + CarbonText.trackedWidth(font, "CARBON", CarbonText.Weight.BOLD,
                        18.0f, CarbonText.Tracking.WORDMARK) + 10.0f,
                panel.y + 24.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        drawTracked(graphics, "MODS", CarbonText.Weight.SEMIBOLD, 12.0f,
                panel.x + SIDEBAR_WIDTH + 18.0f, panel.y + 24.0f,
                CarbonTheme.ACCENT_HIGHLIGHT, CarbonText.Tracking.LABEL);
        CarbonIcons.drawDesign(graphics, "x", panel.x + PANEL_WIDTH - 42.0f,
                panel.y + 22.0f, 16.0f, CarbonTheme.TEXT_MUTED);

        drawTracked(graphics, "ACTIVE PROFILE", CarbonText.Weight.SEMIBOLD, 9.0f,
                sidebar.x + 14.0f, sidebar.y + 2.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        CarbonIcons.drawDesign(graphics, "user", profileButton.x + 10.0f,
                profileButton.y + 10.0f, 16.0f,
                selectedProfile() ? CarbonTheme.ACCENT : CarbonTheme.TEXT_MUTED);
        String activeProfile = displayProfile(configManager.activeProfile());
        drawText(fit(activeProfile, CarbonText.Weight.SEMIBOLD, 12.0f, 116),
                CarbonText.Weight.SEMIBOLD, 12.0f, profileButton.x + 34.0f,
                profileButton.y + 10.0f, CarbonTheme.TEXT, false);
        CarbonIcons.drawDesign(graphics, "arrow-up-down", profileButton.x + 164.0f,
                profileButton.y + 10.0f, 15.0f, CarbonTheme.TEXT_MUTED);

        drawTracked(graphics, "LIBRARY", CarbonText.Weight.SEMIBOLD, 9.0f,
                sidebar.x + 14.0f, sidebar.y + 68.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        for (int index = 0; index < categoryBounds.length; index++) {
            CarbonUiState.Filter item = CarbonUiState.Filter.values()[index];
            Box row = categoryBounds[index];
            boolean selected = item == filter;
            String icon = filterIcon(item);
            CarbonIcons.drawDesign(graphics, icon, row.x + 10.0f, row.y + 7.0f,
                    16.0f, selected ? CarbonTheme.ACCENT : CarbonTheme.TEXT_DIM);
            String label = item.label();
            if (label.equals("All modules")) {
                label = "All modules";
            }
            drawText(label, selected ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.MEDIUM,
                    11.0f, row.x + 36.0f, row.y + 8.0f,
                    selected ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED, false);
            int count = countFor(item);
            drawText(Integer.toString(count), CarbonText.Weight.MEDIUM, 9.0f,
                    row.x + row.width - 28.0f, row.y + 9.0f, CarbonTheme.TEXT_DIM, false);
        }

        drawTracked(graphics, "PROFILES", CarbonText.Weight.SEMIBOLD, 9.0f,
                sidebar.x + 14.0f, sidebar.y + 335.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        int profileCount = Math.min(3, Math.max(0, profiles.size()));
        for (int index = 0; index < profileCount; index++) {
            int profileIndex = Math.min(profiles.size() - 1, index + profileScroll);
            Box row = sidebarProfileBounds.get(index);
            String name = displayProfile(profiles.get(profileIndex));
            CarbonIcons.drawDesign(graphics, "star", row.x + 9.0f, row.y + 5.0f,
                    14.0f, profiles.get(profileIndex).equals(configManager.activeProfile())
                            ? CarbonTheme.ACCENT : CarbonTheme.TEXT_DIM);
            drawText(fit(name, CarbonText.Weight.MEDIUM, 10.0f, 124), CarbonText.Weight.MEDIUM,
                    10.0f, row.x + 31.0f, row.y + 7.0f,
                    profiles.get(profileIndex).equals(configManager.activeProfile())
                            ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED, false);
        }
        CarbonIcons.drawDesign(graphics, "plus", createProfileButton.x + 14.0f,
                createProfileButton.y + 9.0f, 16.0f, CarbonTheme.TEXT);
        drawTracked(graphics, "NEW PROFILE", CarbonText.Weight.SEMIBOLD, 9.0f,
                createProfileButton.x + 40.0f, createProfileButton.y + 11.0f,
                CarbonTheme.TEXT, CarbonText.Tracking.LABEL);

        CarbonIcons.drawDesign(graphics, "search", searchBox.x + 10.0f,
                searchBox.y + 8.0f, 16.0f, searchFocused ? CarbonTheme.ACCENT : CarbonTheme.TEXT_DIM);
        String queryText = searchQuery.isEmpty() && !searchFocused ? "Search modules…" : searchQuery;
        if (searchFocused && caretVisible) {
            queryText += "|";
        }
        drawText(fit(queryText, CarbonText.Weight.REGULAR, 11.0f, searchBox.width - 54.0f),
                CarbonText.Weight.REGULAR, 11.0f, searchBox.x + 34.0f, searchBox.y + 9.0f,
                searchQuery.isEmpty() && !searchFocused ? CarbonTheme.TEXT_DIM : CarbonTheme.TEXT, false);
        if (!searchQuery.isEmpty()) {
            CarbonIcons.drawDesign(graphics, "x", clearSearchButton.x + 7.0f,
                    clearSearchButton.y + 7.0f, 14.0f, CarbonTheme.TEXT_MUTED);
        }
        CarbonIcons.drawDesign(graphics, "layout-grid", gridButton.x + 7.0f,
                gridButton.y + 7.0f, 16.0f,
                viewMode == CarbonUiState.ViewMode.GRID ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM);
        CarbonIcons.drawDesign(graphics, "list", listButton.x + 7.0f,
                listButton.y + 7.0f, 16.0f,
                viewMode == CarbonUiState.ViewMode.LIST ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM);
        CarbonIcons.drawDesign(graphics, "arrow-up-down", sortButton.x + 10.0f,
                sortButton.y + 8.0f, 16.0f,
                sortPopoverOpen ? CarbonTheme.ACCENT : CarbonTheme.TEXT_MUTED);
        drawText("Sort: " + sortMode.label() + (descending ? " ↓" : " ↑"),
                CarbonText.Weight.MEDIUM, 10.0f, sortButton.x + 34.0f,
                sortButton.y + 10.0f, CarbonTheme.TEXT_MUTED, false);

        drawText(visibleModules.size() + (visibleModules.size() == 1 ? " module" : " modules"),
                CarbonText.Weight.MEDIUM, 10.0f, content.x + 18.0f,
                panel.y + 112.0f, CarbonTheme.TEXT_DIM, false);
        drawText("↑↓ select   Enter toggle   Shift+Enter settings   P pin   F pinned   Tab view",
                CarbonText.Weight.REGULAR, 9.0f, content.x + 180.0f,
                panel.y + PANEL_HEIGHT - 15.0f, CarbonTheme.TEXT_DIM, false);
    }

    private void drawModuleText(GuiGraphicsExtractor graphics) {
        if (visibleModules.isEmpty()) {
            CarbonIcons.drawDesign(graphics, "search", content.x + 358.0f,
                    panel.y + 260.0f, 36.0f, CarbonTheme.TEXT_DIM);
            CarbonText.centered(graphics, font, "No modules match this filter.",
                    CarbonText.Weight.MEDIUM, 14.0f, content.x + 393.0f,
                    panel.y + 310.0f, CarbonTheme.TEXT_MUTED, false);
            CarbonText.centered(graphics, font, "Try another category or search term.",
                    CarbonText.Weight.REGULAR, 11.0f, content.x + 393.0f,
                    panel.y + 336.0f, CarbonTheme.TEXT_DIM, false);
            return;
        }

        for (int index = 0; index < visibleModules.size(); index++) {
            Module module = visibleModules.get(index);
            Box card = moduleBounds.get(index);
            if (card.width <= 0.0f || card.height <= 0.0f) {
                continue;
            }
            Box toggle = toggleBounds.get(index);
            Box pin = pinBounds.get(index);
            Box settings = settingsBounds.get(index);
            if (viewMode == CarbonUiState.ViewMode.GRID) {
                drawGridModuleText(module, card, toggle, pin, settings, index == selectedModuleIndex);
            } else {
                drawListModuleText(module, card, toggle, pin, settings, index == selectedModuleIndex);
            }
        }
    }

    private void drawGridModuleText(Module module, Box card, Box toggle, Box pin,
                                    Box settings, boolean selected) {
        CarbonIcons.drawDesign(graphicsPlaceholder(), moduleIcon(module), card.x + 95.0f,
                card.y + 28.0f, 48.0f, module.enabled() ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT);
        CarbonIcons.drawDesign(graphicsPlaceholder(), "star", pin.x + 5.0f, pin.y + 5.0f,
                16.0f, uiState.isPinned(module.id()) ? CarbonTheme.ACCENT : CarbonTheme.TEXT_DIM);
        CarbonIcons.drawDesign(graphicsPlaceholder(), "settings", settings.x + 6.0f,
                settings.y + 7.0f, 16.0f, CarbonTheme.TEXT_MUTED);
        CarbonText.centered(graphicsPlaceholder(), font, module.name(), CarbonText.Weight.SEMIBOLD,
                16.0f, card.x + card.width * 0.5f, card.y + 88.0f,
                CarbonTheme.TEXT, false);
        CarbonText.centered(graphicsPlaceholder(), font, fit(module.description(), CarbonText.Weight.REGULAR,
                        11.0f, card.width - 26.0f), CarbonText.Weight.REGULAR, 11.0f,
                card.x + card.width * 0.5f, card.y + 114.0f, CarbonTheme.TEXT_MUTED, false);
        drawTracked(graphicsPlaceholder(), module.category().label().toUpperCase(Locale.ROOT),
                CarbonText.Weight.MEDIUM, 8.0f, card.x + 16.0f, card.y + 151.0f,
                CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        drawTracked(graphicsPlaceholder(), module.enabled() ? "ACTIVE" : "READY",
                CarbonText.Weight.SEMIBOLD, 8.0f, card.x + card.width - 64.0f,
                card.y + 151.0f, module.enabled() ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT_DIM,
                CarbonText.Tracking.LABEL);
        drawButtonLabel(graphicsPlaceholder(), module.enabled() ? "ON" : "OFF", toggle,
                module.enabled() ? CarbonTheme.WINDOW_BOTTOM : CarbonTheme.TEXT_MUTED, true);
        if (selected) {
            CarbonShapes.drawOutline(graphicsPlaceholder(), card.x + 3.0f, card.y + 3.0f,
                    card.width - 6.0f, card.height - 6.0f, 1.0f, CarbonTheme.ACCENT_HIGHLIGHT);
        }
    }

    private void drawListModuleText(Module module, Box row, Box toggle, Box pin,
                                    Box settings, boolean selected) {
        CarbonIcons.drawDesign(graphicsPlaceholder(), moduleIcon(module), row.x + 14.0f,
                row.y + 6.0f, 42.0f, module.enabled() ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT);
        drawText(module.name(), CarbonText.Weight.SEMIBOLD, 14.0f,
                row.x + 70.0f, row.y + 8.0f, CarbonTheme.TEXT, false);
        drawText(fit(module.description(), CarbonText.Weight.REGULAR, 10.0f, 300),
                CarbonText.Weight.REGULAR, 10.0f, row.x + 70.0f, row.y + 32.0f,
                CarbonTheme.TEXT_MUTED, false);
        drawText(module.category().label(), CarbonText.Weight.MEDIUM, 9.0f,
                row.x + 390.0f, row.y + 33.0f, CarbonTheme.TEXT_DIM, false);
        CarbonIcons.drawDesign(graphicsPlaceholder(), "star", pin.x + 5.0f, pin.y + 5.0f,
                16.0f, uiState.isPinned(module.id()) ? CarbonTheme.ACCENT : CarbonTheme.TEXT_DIM);
        CarbonIcons.drawDesign(graphicsPlaceholder(), "settings", settings.x + 6.0f,
                settings.y + 7.0f, 16.0f, CarbonTheme.TEXT_MUTED);
        drawButtonLabel(graphicsPlaceholder(), module.enabled() ? "ON" : "OFF", toggle,
                module.enabled() ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT_MUTED, true);
        if (selected) {
            CarbonShapes.drawOutline(graphicsPlaceholder(), row.x + 2.0f, row.y + 2.0f,
                    row.width - 4.0f, row.height - 4.0f, 1.0f, CarbonTheme.ACCENT_HIGHLIGHT);
        }
    }

    private void drawPopoverText(GuiGraphicsExtractor graphics) {
        if (profilePopoverOpen) {
            drawTracked(graphics, "SWITCH PROFILE", CarbonText.Weight.SEMIBOLD, 9.0f,
                    profilePanel.x + 14.0f, profilePanel.y + 14.0f,
                    CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
            int count = Math.min(PROFILE_POPOVER_ROWS, Math.max(0, profiles.size() - profileScroll));
            for (int index = 0; index < count; index++) {
                String name = profiles.get(index + profileScroll);
                Box row = profilePopoverBounds.get(index);
                drawText(fit(displayProfile(name), CarbonText.Weight.MEDIUM, 11.0f, 160),
                        CarbonText.Weight.MEDIUM, 11.0f, row.x + 14.0f,
                        row.y + 5.0f, name.equals(configManager.activeProfile())
                                ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT, false);
                if (name.equals(configManager.activeProfile())) {
                    drawTracked(graphics, "ACTIVE", CarbonText.Weight.SEMIBOLD, 7.0f,
                            row.x + row.width - 58.0f, row.y + 7.0f,
                            CarbonTheme.ACCENT_HIGHLIGHT, CarbonText.Tracking.LABEL);
                }
            }
        }
        if (sortPopoverOpen) {
            drawTracked(graphics, "SORT MODULES", CarbonText.Weight.SEMIBOLD, 9.0f,
                    sortPanel.x + 12.0f, sortPanel.y + 12.0f,
                    CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
            for (int index = 0; index < CarbonUiState.SortMode.values().length; index++) {
                CarbonUiState.SortMode mode = CarbonUiState.SortMode.values()[index];
                drawText(mode.label(), mode == sortMode ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.MEDIUM,
                        10.0f, sortPanel.x + 20.0f, sortPanel.y + 41.0f + index * 30.0f,
                        mode == sortMode ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT_MUTED, false);
            }
            drawText(descending ? "Order: Z → A" : "Order: A → Z", CarbonText.Weight.MEDIUM,
                    9.0f, sortOrderButton.x + 10.0f, sortOrderButton.y + 8.0f,
                    CarbonTheme.TEXT, false);
        }
        if (profileCreateOpen) {
            drawTracked(graphics, "CREATE PROFILE", CarbonText.Weight.SEMIBOLD, 12.0f,
                    profileCreatePanel.x + 24.0f, profileCreatePanel.y + 22.0f,
                    CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
            drawText("Name this local module setup.", CarbonText.Weight.REGULAR, 11.0f,
                    profileCreatePanel.x + 24.0f, profileCreatePanel.y + 48.0f,
                    CarbonTheme.TEXT_MUTED, false);
            String draft = profileDraft.isEmpty() ? "profile-name" : profileDraft;
            if (!profileDraft.isEmpty() && caretVisible) {
                draft += "|";
            }
            drawText(draft, CarbonText.Weight.REGULAR, 12.0f,
                    profileInputBox.x + 10.0f, profileInputBox.y + 10.0f,
                    profileDraft.isEmpty() ? CarbonTheme.TEXT_DIM : CarbonTheme.TEXT, false);
            drawTracked(graphics, "CANCEL", CarbonText.Weight.SEMIBOLD, 9.0f,
                    profileCancelButton.x + 46.0f, profileCancelButton.y + 11.0f,
                    CarbonTheme.TEXT_MUTED, CarbonText.Tracking.LABEL);
            drawTracked(graphics, "CREATE", CarbonText.Weight.SEMIBOLD, 9.0f,
                    profileConfirmButton.x + 48.0f, profileConfirmButton.y + 11.0f,
                    CarbonTheme.WINDOW_BOTTOM, CarbonText.Tracking.LABEL);
        }
        if (settingsPopoverOpen && settingsModule != null) {
            CarbonIcons.drawDesign(graphics, moduleIcon(settingsModule), settingsPanel.x + 18.0f,
                    settingsPanel.y + 18.0f, 34.0f, CarbonTheme.ACCENT);
            drawText(settingsModule.name(), CarbonText.Weight.SEMIBOLD, 15.0f,
                    settingsPanel.x + 62.0f, settingsPanel.y + 18.0f,
                    CarbonTheme.TEXT, false);
            drawText("Live settings · changes save to " + displayProfile(configManager.activeProfile()),
                    CarbonText.Weight.REGULAR, 10.0f,
                    settingsPanel.x + 62.0f, settingsPanel.y + 42.0f,
                    CarbonTheme.TEXT_MUTED, false);
            CarbonIcons.drawDesign(graphics, "x", settingsCloseButton.x + 7.0f,
                    settingsCloseButton.y + 7.0f, 14.0f, CarbonTheme.TEXT_MUTED);
            int count = Math.min(MAX_SETTINGS_ROWS,
                    Math.max(0, settingsModule.settings().size() - settingsScroll));
            for (int index = 0; index < count; index++) {
                Setting<?> setting = settingsModule.settings().get(index + settingsScroll);
                Box row = settingBounds.get(index);
                Box valueBox = settingValueBounds.get(index);
                if (row.y + row.height < settingsViewport.y || row.y > settingsViewport.y + settingsViewport.height) {
                    continue;
                }
                drawText(fit(setting.label(), CarbonText.Weight.MEDIUM, 11.0f, 230),
                        CarbonText.Weight.MEDIUM, 11.0f,
                        row.x + 10.0f, row.y + 10.0f, CarbonTheme.TEXT, false);
                drawText(fit(setting.description(), CarbonText.Weight.REGULAR, 9.0f, 272),
                        CarbonText.Weight.REGULAR, 9.0f,
                        row.x + 10.0f, row.y + 27.0f, CarbonTheme.TEXT_DIM, false);
                if (setting instanceof NumberSetting number) {
                    drawText(number.displayValue(), CarbonText.Weight.SEMIBOLD, 10.0f,
                            row.x + row.width - 54.0f, row.y + 10.0f,
                            CarbonTheme.ACCENT_HIGHLIGHT, false);
                } else if (setting instanceof BoolSetting bool) {
                    drawText(bool.displayValue(), CarbonText.Weight.SEMIBOLD, 9.0f,
                            row.x + row.width - 66.0f, row.y + 5.0f,
                            bool.enabled() ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT_MUTED, false);
                } else if (setting instanceof ModeSetting mode) {
                    drawText(fit(mode.get(), CarbonText.Weight.MEDIUM, 9.0f, 90),
                            CarbonText.Weight.MEDIUM, 9.0f,
                            valueBox.x + 10.0f, valueBox.y + 8.0f,
                            CarbonTheme.TEXT, false);
                } else if (setting instanceof KeybindSetting keybind) {
                    String bindingText = capturingKeybind == keybind ? "Press a key…" : bindingLabel(keybind.get());
                    drawText(fit(bindingText, CarbonText.Weight.MEDIUM, 9.0f, 110),
                            CarbonText.Weight.MEDIUM, 9.0f,
                            valueBox.x + 8.0f, valueBox.y + 8.0f,
                            capturingKeybind == keybind ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT,
                            false);
                } else if (setting instanceof ColorSetting color) {
                    drawText(color.displayValue(), CarbonText.Weight.MEDIUM, 8.0f,
                            valueBox.x + 7.0f, valueBox.y + 8.0f,
                            CarbonTheme.TEXT, false);
                }
            }
            drawTracked(graphics, "RESET MODULE SETTINGS", CarbonText.Weight.SEMIBOLD, 8.0f,
                    settingsResetButton.x + 10.0f, settingsResetButton.y + 10.0f,
                    CarbonTheme.TEXT_MUTED, CarbonText.Tracking.LABEL);
        }
    }

    private void drawTooltipAndToast(GuiGraphicsExtractor graphics) {
        if (!hoverText.isEmpty() && System.nanoTime() - hoverStartedAt >= 450_000_000L) {
            float width = Math.min(380.0f,
                    CarbonText.width(font, hoverText, CarbonText.Weight.REGULAR, 10.0f) + 20.0f);
            float x = clamp(mouseDesignX + 14.0f, 8.0f, UiScale.designWidth() - width - 8.0f);
            float y = clamp(mouseDesignY + 16.0f, 8.0f, UiScale.designHeight() - 40.0f);
            CarbonShapes.drawShadow(graphics, x, y, width, 30.0f, 7.0f, 10.0f, 4.0f,
                    CarbonTheme.WINDOW_SHADOW);
            CarbonShapes.drawBorderedSurface(graphics, x, y, width, 30.0f, 7.0f, 1.0f,
                    CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                    CarbonTheme.BUTTON_TOP, CarbonTheme.BUTTON_BOTTOM);
            CarbonText.centered(graphics, font, fit(hoverText, CarbonText.Weight.REGULAR, 10.0f,
                            width - 18.0f), CarbonText.Weight.REGULAR, 10.0f,
                    x + width * 0.5f, y + 10.0f, CarbonTheme.TEXT, false);
        }
        if (System.nanoTime() < toastUntilNanos && !toastMessage.isEmpty()) {
            float width = Math.max(180.0f, CarbonText.width(font, toastMessage,
                    CarbonText.Weight.MEDIUM, 11.0f) + 28.0f);
            float x = panel.x + (panel.width - width) * 0.5f;
            float y = panel.y + panel.height - 48.0f;
            CarbonShapes.drawShadow(graphics, x, y, width, 32.0f, 8.0f,
                    12.0f, 4.0f, CarbonTheme.WINDOW_SHADOW);
            CarbonShapes.drawBorderedSurface(graphics, x, y, width, 32.0f, 8.0f, 1.0f,
                    CarbonTheme.CARD_ACTIVE_BORDER, CarbonTheme.CARD_ACTIVE_BORDER,
                    CarbonTheme.BUTTON_TOP, CarbonTheme.BUTTON_BOTTOM);
            CarbonText.centered(graphics, font, toastMessage, CarbonText.Weight.MEDIUM, 11.0f,
                    x + width * 0.5f, y + 10.0f, CarbonTheme.TEXT, false);
        }
    }

    private void drawLayoutDebug(GuiGraphicsExtractor graphics) {
        CarbonShapes.drawOutline(graphics, panel.x, panel.y, panel.width, panel.height,
                1.5f, CarbonTheme.ACCENT);
        CarbonShapes.drawOutline(graphics, sidebar.x, sidebar.y, sidebar.width, sidebar.height,
                1.0f, CarbonTheme.RED);
        CarbonShapes.drawOutline(graphics, content.x, content.y, content.width, content.height,
                1.0f, CarbonTheme.TEXT_MUTED);
        CarbonText.drawTracked(graphics, font, "F6 LAYOUT DEBUG", CarbonText.Weight.BOLD, 10.0f,
                panel.x + 14.0f, panel.y + panel.height - 20.0f,
                CarbonTheme.ACCENT_HIGHLIGHT, CarbonText.Tracking.LABEL);
    }

    private void drawRenderFailure(GuiGraphicsExtractor graphics) {
        CarbonText.centered(graphics, font, "Carbon render failed: " + CarbonShapes.failureReason(),
                CarbonText.Weight.SEMIBOLD, 14.0f, UiScale.designWidth() * 0.5f,
                UiScale.designHeight() * 0.5f, CarbonTheme.RED, false);
    }

    private void updateHover() {
        String next = "";
        int nextModule = -1;
        for (int index = 0; index < visibleModules.size(); index++) {
            Box card = moduleBounds.get(index);
            if (card.contains(mouseDesignX, mouseDesignY)) {
                Module module = visibleModules.get(index);
                nextModule = index;
                if (pinBounds.get(index).contains(mouseDesignX, mouseDesignY)) {
                    next = "Pin " + module.name() + " for quick access.";
                } else if (settingsBounds.get(index).contains(mouseDesignX, mouseDesignY)) {
                    next = "Open live settings for " + module.name() + ".";
                } else if (toggleBounds.get(index).contains(mouseDesignX, mouseDesignY)) {
                    next = module.enabled() ? "Disable " + module.name() + "." : "Enable " + module.name() + ".";
                } else {
                    next = module.description();
                }
                break;
            }
        }
        if (next.isEmpty() && searchBox.contains(mouseDesignX, mouseDesignY)) {
            next = "Search module names, IDs, descriptions, and settings.";
        } else if (next.isEmpty() && sortButton.contains(mouseDesignX, mouseDesignY)) {
            next = "Sort by name, category, or enabled state.";
        } else if (next.isEmpty() && createProfileButton.contains(mouseDesignX, mouseDesignY)) {
            next = "Create a local profile from the current module state.";
        }
        if (!next.equals(hoverText) || nextModule != hoveredModuleIndex) {
            hoverText = next;
            hoveredModuleIndex = nextModule;
            hoverStartedAt = System.nanoTime();
        }
    }

    private void updateCaret() {
        long now = System.nanoTime();
        if (now - lastBlinkNanos >= 500_000_000L) {
            caretVisible = !caretVisible;
            lastBlinkNanos = now;
        }
    }

    private GuiGraphicsExtractor graphicsPlaceholder() {
        if (currentGraphics == null) {
            throw new IllegalStateException("Carbon text draw requested outside the screen extraction pass");
        }
        return currentGraphics;
    }

    private void drawText(String text, CarbonText.Weight weight, float size,
                          float x, float y, int color, boolean shadow) {
        CarbonText.draw(graphicsPlaceholder(), font, text, weight, size, x, y, color, shadow);
    }

    private void drawTracked(GuiGraphicsExtractor graphics, String text, CarbonText.Weight weight,
                             float size, float x, float y, int color, CarbonText.Tracking tracking) {
        CarbonText.drawTracked(graphics, font, text, weight, size, x, y, color, tracking);
    }

    private void drawButtonLabel(GuiGraphicsExtractor graphics, String text, Box bounds, int color, boolean centered) {
        if (centered) {
            CarbonText.centeredTracked(graphics, font, text, CarbonText.Weight.SEMIBOLD,
                    9.0f, bounds.x + bounds.width * 0.5f, bounds.y + 10.0f,
                    color, CarbonText.Tracking.LABEL);
        } else {
            drawTracked(graphics, text, CarbonText.Weight.SEMIBOLD, 9.0f,
                    bounds.x + 8.0f, bounds.y + 10.0f, color, CarbonText.Tracking.LABEL);
        }
    }

    private float numberFraction(NumberSetting number) {
        double span = number.maximum() - number.minimum();
        if (span <= 0.0) {
            return 0.0f;
        }
        return (float) Math.max(0.0, Math.min(1.0,
                (number.get() - number.minimum()) / span));
    }

    private String bindingLabel(KeybindSetting.Binding binding) {
        if (binding == null || binding.unbound()) {
            return "Unbound";
        }
        if (binding.mouse()) {
            return switch (binding.code()) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "Mouse 1";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "Mouse 2";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "Mouse 3";
                default -> "Mouse " + (binding.code() + 1);
            };
        }
        return switch (binding.code()) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "Left Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "Left Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "Left Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "Right Alt";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            default -> binding.code() >= GLFW.GLFW_KEY_A && binding.code() <= GLFW.GLFW_KEY_Z
                    ? Character.toString((char) ('A' + binding.code() - GLFW.GLFW_KEY_A))
                    : "Key " + binding.code();
        };
    }

    private boolean changeSetting(Setting<?> setting, int visibleIndex, float mouseX, float mouseY) {
        Box value = settingValueBounds.get(visibleIndex);
        if (setting instanceof BoolSetting bool) {
            if (settingBoolBounds.get(visibleIndex).contains(mouseX, mouseY)) {
                bool.toggle();
            }
            return true;
        }
        if (setting instanceof NumberSetting number) {
            Box slider = settingSliderBounds.get(visibleIndex);
            if (slider.contains(mouseX, mouseY)) {
                updateNumber(number, slider, mouseX);
                draggedNumber = number;
                draggingNumber = true;
            }
            return true;
        }
        if (setting instanceof ModeSetting mode) {
            if (value.contains(mouseX, mouseY)) {
                mode.cycle();
            }
            return true;
        }
        if (setting instanceof ColorSetting color) {
            if (value.contains(mouseX, mouseY)) {
                int current = color.get();
                int next = 0;
                for (int step = 1; step <= COLOR_PRESETS.length; step++) {
                    int candidateIndex = (pressedColorIndex + step) % COLOR_PRESETS.length;
                    if (COLOR_PRESETS[candidateIndex] != current) {
                        next = candidateIndex;
                        break;
                    }
                }
                pressedColorIndex = next;
                color.set(COLOR_PRESETS[next]);
            }
            return true;
        }
        if (setting instanceof KeybindSetting keybind) {
            if (value.contains(mouseX, mouseY)) {
                capturingKeybind = keybind;
            }
            return true;
        }
        return false;
    }

    private void updateNumber(NumberSetting setting, Box slider, float mouseX) {
        if (slider.width <= 0.0f) {
            return;
        }
        setting.setFromFraction((mouseX - slider.x) / slider.width);
    }

    private void activateSettingFromKeyboard(int direction, boolean confirm) {
        if (settingsModule == null || settingsModule.settings().isEmpty()) {
            return;
        }
        Setting<?> setting = settingsModule.settings().get(selectedSettingIndex);
        if (setting instanceof BoolSetting bool) {
            bool.toggle();
        } else if (setting instanceof NumberSetting number) {
            number.set(number.get() + number.step() * direction);
        } else if (setting instanceof ModeSetting mode) {
            List<String> modes = mode.modes();
            int next = Math.floorMod(modes.indexOf(mode.get()) + direction, modes.size());
            mode.set(modes.get(next));
        } else if (setting instanceof ColorSetting color) {
            int current = color.get();
            int index = -1;
            for (int candidate = 0; candidate < COLOR_PRESETS.length; candidate++) {
                if (COLOR_PRESETS[candidate] == current) {
                    index = candidate;
                    break;
                }
            }
            int start = index < 0 ? (direction >= 0 ? -1 : 0) : index;
            int next = Math.floorMod(start + direction, COLOR_PRESETS.length);
            color.set(COLOR_PRESETS[next]);
        } else if (setting instanceof KeybindSetting keybind && confirm) {
            capturingKeybind = keybind;
        }
    }

    private int countFor(CarbonUiState.Filter item) {
        int count = 0;
        for (Module module : modules.modules()) {
            boolean match = switch (item) {
                case ALL -> true;
                case HUD -> module.category() == Category.HUD;
                case VISUAL -> module.category() == Category.VISUAL;
                case UTILITY -> module.category() == Category.UTILITY;
                case PERFORMANCE -> module.category() == Category.PERFORMANCE;
                case PINNED -> uiState.isPinned(module.id());
                case ENABLED -> module.enabled();
            };
            if (match) {
                count++;
            }
        }
        return count;
    }

    private boolean selectedProfile() {
        return !configManager.activeProfile().isBlank();
    }

    private String filterIcon(CarbonUiState.Filter filter) {
        return switch (filter) {
            case ALL -> "layout-grid";
            case HUD -> "layout-dashboard";
            case VISUAL -> "eye";
            case UTILITY -> "sliders-horizontal";
            case PERFORMANCE -> "gauge";
            case PINNED -> "star";
            case ENABLED -> "eye";
        };
    }

    private String moduleIcon(Module module) {
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

    private int maximumModuleScroll() {
        if (viewMode == CarbonUiState.ViewMode.GRID) {
            int rows = (int) Math.ceil(visibleModules.size() / 3.0);
            return Math.max(0, rows - 2);
        }
        int visibleRows = Math.max(1, (int) ((PANEL_HEIGHT - 150.0f) / 66.0f));
        return Math.max(0, visibleModules.size() - visibleRows);
    }

    private String fit(String value, CarbonText.Weight weight, float size, float availableWidth) {
        if (value == null || value.isEmpty() || CarbonText.width(font, value, weight, size) <= availableWidth) {
            return value == null ? "" : value;
        }
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && CarbonText.width(font, value.substring(0, end) + ellipsis,
                weight, size) > availableWidth) {
            end--;
        }
        return end <= 0 ? ellipsis : value.substring(0, end).stripTrailing() + ellipsis;
    }

    private static String displayProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return "Default";
        }
        String[] parts = profile.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                label.append(part.substring(1));
            }
        }
        return label.toString();
    }

    private void applyFilter(CarbonUiState.Filter next) {
        filter = next;
        uiState.setFilter(next);
        moduleScroll = 0;
        selectedModuleIndex = 0;
        rebuildVisibleModules();
    }

    private void switchView(CarbonUiState.ViewMode next) {
        viewMode = next;
        uiState.setViewMode(next);
        moduleScroll = 0;
        calculateLayout();
    }

    private void rebuildSort() {
        uiState.setSortMode(sortMode);
        uiState.setDescending(descending);
        rebuildVisibleModules();
    }

    private void toggleModule(Module module) {
        module.toggle();
        rebuildVisibleModules();
        showToast(module.name() + (module.enabled() ? " enabled" : " disabled"));
    }

    private void togglePin(Module module) {
        uiState.togglePinned(module.id());
        rebuildVisibleModules();
        showToast(uiState.isPinned(module.id()) ? "Pinned " + module.name() : "Unpinned " + module.name());
    }

    private void openSettings(Module module) {
        settingsModule = module;
        selectedSettingIndex = 0;
        settingsPopoverOpen = true;
        profilePopoverOpen = false;
        profileCreateOpen = false;
        sortPopoverOpen = false;
        settingsScroll = 0;
        updatePopoverBounds();
    }

    private void showToast(String message) {
        toastMessage = message;
        toastUntilNanos = System.nanoTime() + 1_800_000_000L;
    }

    private void createProfile() {
        String slug = profileDraft.toLowerCase(Locale.ROOT);
        if (!slug.matches(PROFILE_PATTERN) || "default".equals(slug)) {
            showToast("Use 1–32 letters, digits, _ or -.");
            return;
        }
        if (!configManager.createProfile(slug)) {
            showToast("A profile with that name already exists.");
            return;
        }
        profileDraft = "";
        profileCreateOpen = false;
        profilePopoverOpen = false;
        rebuildProfiles();
        rebuildVisibleModules();
        showToast("Created profile " + displayProfile(slug));
    }

    private void updateNumberFromMouse(float x) {
        if (!draggingNumber || draggedNumber == null || settingsModule == null) {
            return;
        }
        for (int index = 0; index < settingsModule.settings().size(); index++) {
            Setting<?> setting = settingsModule.settings().get(index);
            if (setting == draggedNumber) {
                updateNumber(draggedNumber, settingSliderBounds.get(index - settingsScroll), x);
                return;
            }
        }
    }

    private void closePopovers() {
        sortPopoverOpen = false;
        profilePopoverOpen = false;
        profileCreateOpen = false;
        settingsPopoverOpen = false;
        capturingKeybind = null;
        draggingNumber = false;
        draggedNumber = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        UiScale.update(Minecraft.getInstance());
        float mouseX = UiScale.toDesign(event.x());
        float mouseY = UiScale.toDesign(event.y());
        int button = event.button();
        if (capturingKeybind != null) {
            capturingKeybind.set(KeybindSetting.Binding.mouseButton(button));
            capturingKeybind = null;
            showToast("Mouse binding saved.");
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && sortButton.contains(mouseX, mouseY)) {
                descending = !descending;
                rebuildSort();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (profileCreateOpen) {
            if (profileConfirmButton.contains(mouseX, mouseY)) {
                createProfile();
                return true;
            }
            if (profileCancelButton.contains(mouseX, mouseY)) {
                profileCreateOpen = false;
                profileDraft = "";
                return true;
            }
            if (!profileCreatePanel.contains(mouseX, mouseY)) {
                profileCreateOpen = false;
                profileDraft = "";
                return true;
            }
            return true;
        }
        if (settingsPopoverOpen && settingsModule != null) {
            if (settingsCloseButton.contains(mouseX, mouseY)) {
                settingsPopoverOpen = false;
                capturingKeybind = null;
                return true;
            }
            if (settingsResetButton.contains(mouseX, mouseY)) {
                for (Setting<?> setting : settingsModule.settings()) {
                    setting.reset();
                }
                showToast("Module settings reset.");
                return true;
            }
            for (int index = 0; index < settingsModule.settings().size()
                    && index - settingsScroll < settingBounds.size(); index++) {
                int rowIndex = index - settingsScroll;
                if (rowIndex < 0) {
                    continue;
                }
                Box row = settingBounds.get(rowIndex);
                if (row.y + row.height < settingsViewport.y || row.y > settingsViewport.y + settingsViewport.height
                        || !row.contains(mouseX, mouseY)) {
                    continue;
                }
                changeSetting(settingsModule.settings().get(index), rowIndex, mouseX, mouseY);
                return true;
            }
            if (!settingsPanel.contains(mouseX, mouseY)) {
                settingsPopoverOpen = false;
                capturingKeybind = null;
                return true;
            }
            return true;
        }
        if (profilePopoverOpen) {
            int profileCount = Math.min(PROFILE_POPOVER_ROWS, profiles.size() - profileScroll);
            for (int index = 0; index < profileCount; index++) {
                Box row = profilePopoverBounds.get(index);
                if (row.contains(mouseX, mouseY)) {
                    selectedProfileIndex = index + profileScroll;
                    String profile = profiles.get(index + profileScroll);
                    if (configManager.switchProfile(profile)) {
                        rebuildVisibleModules();
                        showToast("Switched to " + displayProfile(profile));
                    }
                    profilePopoverOpen = false;
                    return true;
                }
            }
            if (!profilePanel.contains(mouseX, mouseY) && !profileButton.contains(mouseX, mouseY)) {
                profilePopoverOpen = false;
            }
            return true;
        }
        if (sortPopoverOpen) {
            for (int index = 0; index < CarbonUiState.SortMode.values().length; index++) {
                Box row = sortOptionBounds[index];
                if (row.contains(mouseX, mouseY)) {
                    sortMode = CarbonUiState.SortMode.values()[index];
                    rebuildSort();
                    sortPopoverOpen = false;
                    return true;
                }
            }
            if (sortOrderButton.contains(mouseX, mouseY)) {
                descending = !descending;
                rebuildSort();
                return true;
            }
            if (!sortPanel.contains(mouseX, mouseY) && !sortButton.contains(mouseX, mouseY)) {
                sortPopoverOpen = false;
            }
            return true;
        }

        if (profileButton.contains(mouseX, mouseY)) {
            profilePopoverOpen = true;
            profileCreateOpen = false;
            rebuildProfiles();
            selectedProfileIndex = Math.max(0, profiles.indexOf(configManager.activeProfile()));
            profileScroll = clamp(selectedProfileIndex, 0, Math.max(0, profiles.size() - PROFILE_POPOVER_ROWS));
            updatePopoverBounds();
            return true;
        }
        if (createProfileButton.contains(mouseX, mouseY)) {
            profileCreateOpen = true;
            profilePopoverOpen = false;
            profileDraft = "";
            return true;
        }
        if (searchBox.contains(mouseX, mouseY)) {
            searchFocused = true;
            if (clearSearchButton.contains(mouseX, mouseY)) {
                searchQuery = "";
                uiState.setQuery(searchQuery);
                rebuildVisibleModules();
            }
            return true;
        }
        if (gridButton.contains(mouseX, mouseY)) {
            searchFocused = false;
            switchView(CarbonUiState.ViewMode.GRID);
            return true;
        }
        if (listButton.contains(mouseX, mouseY)) {
            searchFocused = false;
            switchView(CarbonUiState.ViewMode.LIST);
            return true;
        }
        if (sortButton.contains(mouseX, mouseY)) {
            searchFocused = false;
            sortPopoverOpen = true;
            updatePopoverBounds();
            return true;
        }
        if (closeButton.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        for (int index = 0; index < categoryBounds.length; index++) {
            if (categoryBounds[index].contains(mouseX, mouseY)) {
                searchFocused = false;
                applyFilter(CarbonUiState.Filter.values()[index]);
                return true;
            }
        }
        int sideProfileCount = Math.min(3, Math.max(0, profiles.size() - profileScroll));
        for (int index = 0; index < sideProfileCount; index++) {
            if (sidebarProfileBounds.get(index).contains(mouseX, mouseY)) {
                selectedProfileIndex = index + profileScroll;
                String profile = profiles.get(index + profileScroll);
                if (configManager.switchProfile(profile)) {
                    rebuildVisibleModules();
                    showToast("Switched to " + displayProfile(profile));
                }
                return true;
            }
        }
        for (int index = 0; index < visibleModules.size(); index++) {
            Box card = moduleBounds.get(index);
            if (!card.contains(mouseX, mouseY)) {
                continue;
            }
            searchFocused = false;
            selectedModuleIndex = index;
            Module module = visibleModules.get(index);
            if (pinBounds.get(index).contains(mouseX, mouseY)) {
                togglePin(module);
            } else if (settingsBounds.get(index).contains(mouseX, mouseY)) {
                openSettings(module);
            } else if (toggleBounds.get(index).contains(mouseX, mouseY)) {
                toggleModule(module);
            } else {
                toggleModule(module);
            }
            return true;
        }
        searchFocused = false;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingNumber && draggedNumber != null) {
            UiScale.update(Minecraft.getInstance());
            updateNumberFromMouse(UiScale.toDesign(event.x()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingNumber = false;
        draggedNumber = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float designX = UiScale.toDesign(mouseX);
        float designY = UiScale.toDesign(mouseY);
        if (settingsPopoverOpen && settingsViewport.contains(designX, designY) && settingsModule != null) {
            int rows = Math.max(0, settingsModule.settings().size() - 5);
            settingsScroll = clamp(settingsScroll + (verticalAmount < 0.0 ? 1 : -1), 0, rows);
            updatePopoverBounds();
            return true;
        }
        if (profilePopoverOpen && profilePanel.contains(designX, designY)) {
            profileScroll = clamp(profileScroll + (verticalAmount < 0.0 ? 1 : -1),
                    0, Math.max(0, profiles.size() - PROFILE_POPOVER_ROWS));
            updatePopoverBounds();
            return true;
        }
        if (content.contains(designX, designY)) {
            moduleScroll = clamp(moduleScroll + (verticalAmount < 0.0 ? 1 : -1),
                    0, maximumModuleScroll());
            updateModuleBounds();
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
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                capturingKeybind.set(KeybindSetting.Binding.unboundBinding());
                capturingKeybind = null;
                showToast("Keybind cleared.");
                return true;
            }
            if (key != GLFW.GLFW_KEY_UNKNOWN) {
                capturingKeybind.set(KeybindSetting.Binding.keyboard(key));
                capturingKeybind = null;
                showToast("Key binding saved.");
                return true;
            }
            return true;
        }
        if (profileCreateOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                profileCreateOpen = false;
                profileDraft = "";
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                createProfile();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !profileDraft.isEmpty()) {
                profileDraft = profileDraft.substring(0, profileDraft.length() - 1);
                return true;
            }
            return true;
        }
        if (settingsPopoverOpen && settingsModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                settingsPopoverOpen = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int direction = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                selectedSettingIndex = clamp(selectedSettingIndex + direction,
                        0, Math.max(0, settingsModule.settings().size() - 1));
                if (selectedSettingIndex < settingsScroll) {
                    settingsScroll = selectedSettingIndex;
                } else if (selectedSettingIndex >= settingsScroll + 5) {
                    settingsScroll = selectedSettingIndex - 4;
                }
                updatePopoverBounds();
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT
                    || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) {
                int direction = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
                activateSettingFromKeyboard(direction, key == GLFW.GLFW_KEY_ENTER
                        || key == GLFW.GLFW_KEY_SPACE);
                return true;
            }
        }
        if (profilePopoverOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                profilePopoverOpen = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int direction = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                selectedProfileIndex = clamp(selectedProfileIndex + direction,
                        0, Math.max(0, profiles.size() - 1));
                if (selectedProfileIndex < profileScroll) {
                    profileScroll = selectedProfileIndex;
                } else if (selectedProfileIndex >= profileScroll + PROFILE_POPOVER_ROWS) {
                    profileScroll = selectedProfileIndex - PROFILE_POPOVER_ROWS + 1;
                }
                updatePopoverBounds();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER && !profiles.isEmpty()) {
                String profile = profiles.get(selectedProfileIndex);
                if (configManager.switchProfile(profile)) {
                    rebuildVisibleModules();
                    showToast("Switched to " + displayProfile(profile));
                }
                profilePopoverOpen = false;
                return true;
            }
        }
        if (sortPopoverOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                sortPopoverOpen = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int direction = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                int next = (sortMode.ordinal() + direction + CarbonUiState.SortMode.values().length)
                        % CarbonUiState.SortMode.values().length;
                sortMode = CarbonUiState.SortMode.values()[next];
                rebuildSort();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                sortPopoverOpen = false;
                return true;
            }
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                int end = searchQuery.offsetByCodePoints(searchQuery.length(), -1);
                searchQuery = searchQuery.substring(0, end);
                uiState.setQuery(searchQuery);
                rebuildVisibleModules();
                return true;
            }
            if (event.isSelectAll()) {
                searchQuery = "";
                uiState.setQuery(searchQuery);
                rebuildVisibleModules();
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (sortPopoverOpen || profilePopoverOpen || settingsPopoverOpen) {
                closePopovers();
                return true;
            }
            onClose();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_F && event.hasControlDown()) || key == GLFW.GLFW_KEY_SLASH) {
            searchFocused = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_F6) {
            layoutDebug = !layoutDebug;
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            switchView(viewMode == CarbonUiState.ViewMode.GRID
                    ? CarbonUiState.ViewMode.LIST : CarbonUiState.ViewMode.GRID);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            moveSelection(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            moveSelection(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            moveSelection(viewMode == CarbonUiState.ViewMode.GRID ? -3 : -1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            moveSelection(viewMode == CarbonUiState.ViewMode.GRID ? 3 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && !visibleModules.isEmpty()) {
            Module module = visibleModules.get(selectedModuleIndex);
            if (event.hasShiftDown()) {
                openSettings(module);
            } else {
                toggleModule(module);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_P && !visibleModules.isEmpty()) {
            togglePin(visibleModules.get(selectedModuleIndex));
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && !visibleModules.isEmpty()) {
            toggleFilter(CarbonUiState.Filter.PINNED);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String value = event.codepointAsString();
        if (profileCreateOpen) {
            for (int index = 0; index < value.length(); index++) {
                char character = Character.toLowerCase(value.charAt(index));
                if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
                        || character == '_' || character == '-') {
                    if (profileDraft.length() < 32) {
                        profileDraft += character;
                    }
                }
            }
            return true;
        }
        if (searchFocused && !value.isEmpty() && !Character.isISOControl(event.codepoint())) {
            if (searchQuery.length() + value.length() <= 64) {
                searchQuery += value;
                uiState.setQuery(searchQuery);
                rebuildVisibleModules();
            }
            return true;
        }
        return super.charTyped(event);
    }

    private void moveSelection(int delta) {
        if (visibleModules.isEmpty()) {
            selectedModuleIndex = 0;
            return;
        }
        selectedModuleIndex = clamp(selectedModuleIndex + delta,
                0, visibleModules.size() - 1);
        if (viewMode == CarbonUiState.ViewMode.GRID) {
            int row = selectedModuleIndex / 3;
            if (row < moduleScroll) {
                moduleScroll = row;
            } else if (row >= moduleScroll + 2) {
                moduleScroll = row - 1;
            }
        } else if (selectedModuleIndex < moduleScroll) {
            moduleScroll = selectedModuleIndex;
        } else if (selectedModuleIndex >= moduleScroll + 7) {
            moduleScroll = selectedModuleIndex - 6;
        }
        updateModuleBounds();
    }

    private void toggleFilter(CarbonUiState.Filter target) {
        applyFilter(filter == target ? CarbonUiState.Filter.ALL : target);
    }

    @Override
    public void onClose() {
        uiState.setQuery(searchQuery);
        uiState.save();
        super.onClose();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class Box {
        private float x;
        private float y;
        private float width;
        private float height;

        private Box() {
        }

        private Box(float x, float y, float width, float height) {
            set(x, y, width, height);
        }

        private void set(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(float px, float py) {
            return width > 0.0f && height > 0.0f && px >= x && py >= y
                    && px < x + width && py < y + height;
        }
    }
}
