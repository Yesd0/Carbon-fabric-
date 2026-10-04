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
import dev.carbon.client.core.event.KeyInputEvent;
import dev.carbon.client.core.event.MouseInputEvent;
import dev.carbon.client.ui.render.CarbonGlass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carbon's single, in-game module menu. The drawing and hit regions share the same layout
 * calculations so every visible control is backed by the live module/config objects.
 */
public final class CarbonDemoScreen extends CarbonScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final int ACCENT = 0xFF54E49A;
    private static final int TEXT_PRIMARY = 0xFFF2F7F3;
    private static final int TEXT_MUTED = 0xFF9EAAA3;
    private static final int TEXT_FAINT = 0xFF728079;
    private static final int[] COLOR_CHOICES = {
            0xFF54E49A, 0xFF70B7FF, 0xFFFFBD67, 0xFFCA91FF, 0xFFFF7B8C
    };
    private static final List<Preset> PRESETS = List.of(
            new Preset("Minimal", Map.of("fps_hud", true, "cps_hud", false,
                    "keystrokes_hud", false, "zoom", false)),
            new Preset("Creator", Map.of("fps_hud", true, "cps_hud", true,
                    "keystrokes_hud", true, "zoom", false)),
            new Preset("PvP", Map.of("fps_hud", true, "cps_hud", true,
                    "keystrokes_hud", true, "zoom", true))
    );

    private final ModuleManager modules;
    private final ConfigManager configManager;
    private final CarbonMenuPreferences preferences;
    private final List<ModuleHit> moduleHits = new ArrayList<>();
    private final List<SettingHit> settingHits = new ArrayList<>();
    private final List<ModuleHit> moduleSelectorHits = new ArrayList<>();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final List<PresetHit> presetHits = new ArrayList<>();
    private final EnumMap<Category, Boolean> categoryPresence = new EnumMap<>(Category.class);

    private EditBox searchBox;
    private String searchQuery = "";
    private String username = "Player";
    private Identifier skinTexture;
    private Model.Simple headModel;
    private Category selectedCategory;
    private Module selectedModule;
    private View activeView = View.MODULES;
    private SettingsPage settingsPage = SettingsPage.MENU;
    private KeybindSetting capturingKeybind;
    private NumberSetting draggingNumberSetting;
    private boolean suppressCapturedKey;
    private boolean suppressCapturedMouse;
    private boolean draggingUiScale;
    private int moduleScrollRows;
    private int settingsScrollRows;
    private int moduleColumns = 1;
    private int moduleVisibleRows = 1;
    private int settingsVisibleRows = 1;
    private String toastMessage = "";
    private long toastUntilNanos;
    private long openedAtNanos;
    private float layoutScale = 1.0f;

    private Rect panelRect = Rect.EMPTY;
    private Rect sidebarRect = Rect.EMPTY;
    private Rect accountRect = Rect.EMPTY;
    private Rect avatarRect = Rect.EMPTY;
    private Rect modulesNavRect = Rect.EMPTY;
    private Rect settingsNavRect = Rect.EMPTY;
    private Rect allCategoryRect = Rect.EMPTY;
    private Rect profileButtonRect = Rect.EMPTY;
    private Rect createProfileRect = Rect.EMPTY;
    private Rect searchRect = Rect.EMPTY;
    private Rect searchClearRect = Rect.EMPTY;
    private Rect closeRect = Rect.EMPTY;
    private Rect contentRect = Rect.EMPTY;
    private Rect contentTitleRect = Rect.EMPTY;
    private Rect settingsMenuTabRect = Rect.EMPTY;
    private Rect settingsFeatureTabRect = Rect.EMPTY;
    private Rect glassToggleRect = Rect.EMPTY;
    private Rect scaleTrackRect = Rect.EMPTY;
    private Rect scaleHitRect = Rect.EMPTY;
    private Rect scaleMinusRect = Rect.EMPTY;
    private Rect scalePlusRect = Rect.EMPTY;
    private Rect selectedModuleRect = Rect.EMPTY;
    private Rect moduleEnableRect = Rect.EMPTY;
    private Rect resetSettingsRect = Rect.EMPTY;
    private Rect settingsListRect = Rect.EMPTY;
    private int designPanelX;
    private int designPanelY;
    private int designPanelWidth;
    private int designPanelHeight;
    private int sidebarWidth;

    public CarbonDemoScreen(ModuleManager modules, ConfigManager configManager, Screen parent) {
        super(Component.literal("Carbon Client"), parent);
        this.modules = modules;
        this.configManager = configManager;
        this.preferences = CarbonMenuPreferences.load(FabricLoader.getInstance().getConfigDir()
                .resolve("carbonclient").resolve("menu.json"));
        UiScale.setUserScaleMultiplier(preferences.uiScale());
    }

    @Override
    protected void init() {
        super.init();
        openedAtNanos = System.nanoTime();
        suppressCapturedKey = false;
        suppressCapturedMouse = false;
        CarbonIcons.load();
        UiScale.setUserScaleMultiplier(preferences.uiScale());
        UiScale.update(Minecraft.getInstance());
        refreshPlayerIdentity();
        ensureSelection();
        calculateLayout();

        searchBox = new EditBox(font, searchRect.x() + design(35), searchRect.y() + design(4),
                Math.max(1, searchRect.width() - design(69)), Math.max(1, searchRect.height() - design(8)),
                Component.literal("Search modules"));
        searchBox.setBordered(false);
        searchBox.setTextColor(TEXT_PRIMARY);
        searchBox.setHint(Component.literal("Search modules"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value == null ? "" : value;
            moduleScrollRows = 0;
        });
        searchBox.setRectangle(Math.max(1, searchRect.width() - design(69)),
                Math.max(1, searchRect.height() - design(8)), searchRect.x() + design(35), searchRect.y() + design(4));
        addRenderableWidget(searchBox);
    }

    private void refreshPlayerIdentity() {
        Minecraft client = Minecraft.getInstance();
        username = client.getUser() == null ? "Player" : client.getUser().getName();
        skinTexture = null;
        headModel = null;
        var player = client.player;
        if (player == null) {
            return;
        }
        try {
            skinTexture = player.getSkin().body().texturePath();
            headModel = new Model.Simple(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_HEAD),
                    RenderTypes::entityCutout);
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not prepare the local player's skin head for the Carbon menu", failure);
        }
    }

    private void ensureSelection() {
        if (selectedModule == null || !modules.modules().contains(selectedModule)) {
            selectedModule = modules.modules().stream().findFirst().orElse(null);
        }
        categoryPresence.clear();
        for (Module module : modules.modules()) {
            categoryPresence.put(module.category(), true);
        }
        if (selectedCategory != null && !categoryPresence.containsKey(selectedCategory)) {
            selectedCategory = null;
        }
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
        if (preferences.glassUi()) {
            super.extractBlurredBackground(graphics);
        } else {
            graphics.fill(0, 0, width, height, 0xFF080C0A);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        applyGlassSafetyFallback();
        Minecraft client = Minecraft.getInstance();
        UiScale.update(client);
        layoutScale = UiScale.rendererScale();
        calculateLayout();
        if (searchBox != null) {
            searchBox.visible = activeView == View.MODULES;
            searchBox.setRectangle(Math.max(1, searchRect.width() - design(69)),
                    Math.max(1, searchRect.height() - design(8)), searchRect.x() + design(35), searchRect.y() + design(4));
        }

        float opening = easeOutCubic(Math.min(1.0f,
                (System.nanoTime() - openedAtNanos) / 260_000_000.0f));
        extractBackdropAndPanel(graphics, opening);
        extractSidebarSurfaces(graphics, mouseX, mouseY);
        if (activeView == View.MODULES) {
            extractModuleSurfaces(graphics, mouseX, mouseY);
        } else {
            extractSettingsSurfaces(graphics, mouseX, mouseY);
        }

        graphics.nextStratum();
        extractAvatar(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.nextStratum();
        extractSidebarText(graphics, mouseX, mouseY);
        extractHeaderText(graphics, mouseX, mouseY, opening);
        if (activeView == View.MODULES) {
            extractModuleText(graphics, mouseX, mouseY);
        } else {
            extractSettingsText(graphics, mouseX, mouseY);
        }
        extractToast(graphics);
        drawOpeningAccent(graphics, opening);
        if (activeView == View.MODULES) {
            drawModuleScrollIndicator(graphics);
        } else if (settingsPage == SettingsPage.MODULE) {
            drawSettingScrollIndicator(graphics);
        }
        if (CarbonScreen.isLayoutDebugEnabled()) {
            drawDebugLayout(graphics);
        }
        CarbonGlass.drawFailureLabelGui(graphics);
    }

    private void extractBackdropAndPanel(GuiGraphicsExtractor graphics, float opening) {
        graphics.nextStratum();
        if (preferences.glassUi()) {
            drawGlassTint(graphics, 0, 0, width, height, 0x68040A07);
        } else {
            graphics.fill(0, 0, width, height, 0xD8070A08);
            // A quiet edge light keeps the selected surface readable without restoring the old pattern background.
            graphics.fill(0, 0, width, Math.max(1, design(2)), 0xFF101713);
        }
        drawSurface(graphics, panelRect, Surface.MAIN, false);
        drawSurface(graphics, sidebarRect, Surface.SIDEBAR, false);
        if (preferences.glassUi() && opening < 1.0f) {
            drawGlassTint(graphics, panelRect.x(), panelRect.y(), panelRect.width(), panelRect.height(),
                    (Math.max(0, Math.min(100, Math.round((1.0f - opening) * 42))) << 24) | 0x00101411);
        }
        graphics.outline(panelRect.x(), panelRect.y(), panelRect.width(), panelRect.height(),
                preferences.glassUi() ? 0x557FE3A5 : 0xFF28342D);
    }

    private void extractSidebarSurfaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawSurface(graphics, accountRect, Surface.CARD, accountRect.contains(mouseX, mouseY));
        drawButtonSurface(graphics, modulesNavRect, activeView == View.MODULES,
                activeView == View.MODULES || modulesNavRect.contains(mouseX, mouseY));
        drawButtonSurface(graphics, settingsNavRect, activeView == View.SETTINGS,
                activeView == View.SETTINGS || settingsNavRect.contains(mouseX, mouseY));
        drawButtonSurface(graphics, allCategoryRect, selectedCategory == null,
                selectedCategory == null || allCategoryRect.contains(mouseX, mouseY));
        for (CategoryHit hit : categoryHits) {
            drawButtonSurface(graphics, hit.rect(), selectedCategory == hit.category(),
                    selectedCategory == hit.category() || hit.rect().contains(mouseX, mouseY));
        }
        drawButtonSurface(graphics, profileButtonRect, false, profileButtonRect.contains(mouseX, mouseY));
        drawButtonSurface(graphics, createProfileRect, false, createProfileRect.contains(mouseX, mouseY));
        drawButtonSurface(graphics, closeRect, false, closeRect.contains(mouseX, mouseY));
    }

    private void extractModuleSurfaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        prepareModuleHits();
        for (ModuleHit hit : moduleHits) {
            boolean hovered = hit.card().contains(mouseX, mouseY);
            drawSurface(graphics, hit.card(), Surface.CARD, hovered);
            boolean enabled = hit.module().enabled();
            drawSwitchSurface(graphics, hit.toggle(), enabled, hit.toggle().contains(mouseX, mouseY));
            drawButtonSurface(graphics, hit.settings(), false,
                    hit.settings().contains(mouseX, mouseY));
        }
        for (PresetHit hit : presetHits) {
            drawButtonSurface(graphics, hit.rect(), false, hit.rect().contains(mouseX, mouseY));
        }
        drawSurface(graphics, searchRect, Surface.CONTROL, searchRect.contains(mouseX, mouseY));
        drawButtonSurface(graphics, searchClearRect, false, searchClearRect.contains(mouseX, mouseY));
    }

    private void extractSettingsSurfaces(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawButtonSurface(graphics, settingsMenuTabRect, settingsPage == SettingsPage.MENU,
                settingsPage == SettingsPage.MENU || settingsMenuTabRect.contains(mouseX, mouseY));
        drawButtonSurface(graphics, settingsFeatureTabRect, settingsPage == SettingsPage.MODULE,
                settingsPage == SettingsPage.MODULE || settingsFeatureTabRect.contains(mouseX, mouseY));

        if (settingsPage == SettingsPage.MENU) {
            Rect appearance = menuAppearanceRect();
            Rect sample = themePreviewRect();
            drawSurface(graphics, appearance, Surface.CARD, false);
            drawSurface(graphics, sample, Surface.CARD, sample.contains(mouseX, mouseY));
            drawSwitchSurface(graphics, glassToggleRect, preferences.glassUi(), glassToggleRect.contains(mouseX, mouseY));
            drawButtonSurface(graphics, scaleMinusRect, false, scaleMinusRect.contains(mouseX, mouseY));
            drawButtonSurface(graphics, scalePlusRect, false, scalePlusRect.contains(mouseX, mouseY));
            drawSliderTrack(graphics, scaleTrackRect, preferences.uiScale(), 0.75f, 1.50f);
        } else {
            for (ModuleHit hit : moduleSelectorHits) {
                drawButtonSurface(graphics, hit.card(), hit.module() == selectedModule,
                        hit.module() == selectedModule || hit.card().contains(mouseX, mouseY));
            }
            drawSurface(graphics, selectedModuleRect, Surface.CARD, false);
            if (selectedModule != null) {
                drawSwitchSurface(graphics, moduleEnableRect, selectedModule.enabled(),
                        moduleEnableRect.contains(mouseX, mouseY));
            }
            drawButtonSurface(graphics, resetSettingsRect, false, resetSettingsRect.contains(mouseX, mouseY));
            for (SettingHit hit : settingHits) {
                drawSurface(graphics, hit.row(), Surface.CARD, hit.row().contains(mouseX, mouseY));
                drawSettingControlSurface(graphics, hit, mouseX, mouseY);
            }
        }
    }

    private void extractAvatar(GuiGraphicsExtractor graphics) {
        if (avatarRect.width() <= 0) {
            return;
        }
        drawSurface(graphics, avatarRect, Surface.AVATAR, false);
        if (skinTexture != null) {
            try {
                // Always submit a vanilla face crop too; it remains visible if the 3D picture-in-picture renderer fails.
                int size = avatarRect.width();
                graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture,
                        avatarRect.x(), avatarRect.y(), 8.0f, 8.0f, size, size, 8, 8, 64, 64, 0xFFFFFFFF);
                graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture,
                        avatarRect.x(), avatarRect.y(), 40.0f, 8.0f, size, size, 8, 8, 64, 64, 0xFFFFFFFF);
                if (headModel != null) {
                    // A subtle idle turn makes the account portrait read as a 3D skin head.
                    float lookX = 6.0f;
                    float lookY = (float) Math.sin((System.nanoTime() - openedAtNanos) / 2_000_000_000.0) * 8.0f;
                    graphics.skin(headModel, skinTexture, Math.max(22.0f, size * 0.74f),
                            lookX, lookY, 0.0f,
                            avatarRect.x(), avatarRect.y(), avatarRect.right(), avatarRect.bottom());
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not render the local player's skin head in the Carbon menu", failure);
            }
        } else {
            int inset = design(8);
            int faceSize = Math.max(1, avatarRect.width() - inset * 2);
            graphics.fill(avatarRect.x() + inset, avatarRect.y() + inset,
                    avatarRect.x() + inset + faceSize, avatarRect.y() + inset + faceSize, 0xFF24352B);
            String initials = username.isBlank() ? "?" : username.substring(0, 1).toUpperCase(Locale.ROOT);
            drawCenteredText(graphics, initials, avatarRect.x() + avatarRect.width() / 2,
                    avatarRect.y() + avatarRect.height() / 2 - design(7), 14, 0xFFFFFFFF, true);
        }
        graphics.outline(avatarRect.x(), avatarRect.y(), avatarRect.width(), avatarRect.height(),
                preferences.glassUi() ? 0x557FE3A5 : 0xFF40564A);
    }

    private void extractSidebarText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        CarbonIcons.drawGui(graphics, "layout-grid", panelRect.x() + design(18), panelRect.y() + design(18),
                design(30), 0xFFFFFFFF);
        drawText(graphics, "CARBON", panelRect.x() + design(56), panelRect.y() + design(19),
                18, TEXT_PRIMARY, true);
        drawText(graphics, "CLIENT", panelRect.x() + design(58), panelRect.y() + design(40),
                9, ACCENT, true);
        drawText(graphics, "MOD MENU", panelRect.x() + design(17), panelRect.y() + design(60),
                8, TEXT_FAINT, true);

        drawText(graphics, username, avatarRect.right() + design(12), accountRect.y() + design(19),
                13, TEXT_PRIMARY, true);
        drawText(graphics, "Minecraft account", avatarRect.right() + design(12), accountRect.y() + design(42),
                9, TEXT_MUTED, false);
        drawNavLabel(graphics, modulesNavRect, "layout-grid", "Modules", activeView == View.MODULES);
        drawNavLabel(graphics, settingsNavRect, "settings", "Settings", activeView == View.SETTINGS);
        drawText(graphics, "CATEGORIES", panelRect.x() + design(17), allCategoryRect.y() - design(22),
                8, TEXT_FAINT, true);
        drawCategoryLabel(graphics, allCategoryRect, null, "layout-grid", "All modules", selectedCategory == null);
        for (CategoryHit hit : categoryHits) {
            drawCategoryLabel(graphics, hit.rect(), hit.category(), categoryIcon(hit.category()),
                    hit.category().label(), selectedCategory == hit.category());
        }

        String profile = activeProfileName();
        drawText(graphics, "PROFILE", profileButtonRect.x() + design(12), profileButtonRect.y() + design(5),
                7, TEXT_FAINT, true);
        drawText(graphics, profile, profileButtonRect.x() + design(12), profileButtonRect.y() + design(20),
                10, TEXT_PRIMARY, true);
        drawText(graphics, "+", createProfileRect.x() + design(8), createProfileRect.y() + design(5),
                17, ACCENT, true);
        drawText(graphics, "×", closeRect.x() + design(9), closeRect.y() + design(2),
                16, closeRect.contains(mouseX, mouseY) ? TEXT_PRIMARY : TEXT_MUTED, false);
        drawText(graphics, "›", profileButtonRect.right() - design(24), profileButtonRect.y() + design(18),
                12, TEXT_MUTED, false);
    }

    private void extractHeaderText(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float opening) {
        if (activeView == View.MODULES) {
            drawText(graphics, "Module library", contentRect.x(), panelRect.y() + design(24),
                    22, TEXT_PRIMARY, true);
            drawText(graphics, "Find a feature, tune its settings, and make it yours.",
                    contentRect.x(), panelRect.y() + design(55), 10, TEXT_MUTED, false);
            drawText(graphics, filteredModules().size() + " MODULE" + (filteredModules().size() == 1 ? "" : "S"),
                    contentRect.right() - design(110), panelRect.y() + design(58), 8, TEXT_FAINT, true);
            CarbonIcons.drawGui(graphics, "search", searchRect.x() + design(12), searchRect.y() + design(10),
                    design(15), TEXT_MUTED);
            drawText(graphics, "×", searchClearRect.x() + design(8), searchClearRect.y() + design(3),
                    14, searchClearRect.contains(mouseX, mouseY) ? TEXT_PRIMARY : TEXT_MUTED, false);
            for (PresetHit hit : presetHits) {
                drawText(graphics, hit.preset().name(), hit.rect().x() + design(12), hit.rect().y() + design(8),
                        9, hit.rect().contains(mouseX, mouseY) ? TEXT_PRIMARY : TEXT_MUTED, true);
            }
            drawText(graphics, "QUICK SETUPS", contentRect.x(), panelRect.y() + design(86),
                    8, TEXT_FAINT, true);
        } else {
            drawText(graphics, "Settings", contentRect.x(), panelRect.y() + design(24),
                    22, TEXT_PRIMARY, true);
            drawText(graphics, "Personalize the menu or configure an individual module.",
                    contentRect.x(), panelRect.y() + design(55), 10, TEXT_MUTED, false);
            drawTabLabel(graphics, settingsMenuTabRect, "Menu", settingsPage == SettingsPage.MENU);
            drawTabLabel(graphics, settingsFeatureTabRect, "Module settings", settingsPage == SettingsPage.MODULE);
        }
        if (opening < 1.0f) {
            graphics.fill(panelRect.x(), panelRect.y(), panelRect.right(), panelRect.bottom(),
                    (Math.max(0, Math.min(20, Math.round((1.0f - opening) * 20))) << 24) | 0x00161F19);
        }
    }

    private void extractModuleText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (filteredModules().isEmpty()) {
            drawText(graphics, "No modules found", contentRect.x() + design(8), moduleGridTop() + design(24),
                    15, TEXT_PRIMARY, true);
            drawText(graphics, "Try another search or choose a different category.",
                    contentRect.x() + design(8), moduleGridTop() + design(51), 10, TEXT_MUTED, false);
            return;
        }
        for (ModuleHit hit : moduleHits) {
            Module module = hit.module();
            Rect card = hit.card();
            Rect icon = new Rect(card.x() + design(15), card.y() + design(16), design(40), design(40));
            CarbonIcons.drawGui(graphics, moduleIcon(module), icon.x(), icon.y(), icon.width(), ACCENT);
            drawText(graphics, module.name(), card.x() + design(66), card.y() + design(17),
                    13, TEXT_PRIMARY, true);
            drawText(graphics, module.category().label().toUpperCase(Locale.ROOT),
                    card.x() + design(66), card.y() + design(39), 7, TEXT_FAINT, true);
            drawWrappedText(graphics, module.description(), card.x() + design(17), card.y() + design(72),
                    card.width() - design(34), 9, TEXT_MUTED, 2);
            drawChip(graphics, new Rect(card.x() + design(16), card.bottom() - design(36),
                    design(94), design(22)), module.enabled() ? "ENABLED" : "DISABLED",
                    module.enabled() ? ACCENT : TEXT_FAINT);
            drawText(graphics, module.enabled() ? "ON" : "OFF",
                    hit.toggle().x() - design(28), hit.toggle().y() + design(9),
                    8, module.enabled() ? ACCENT : TEXT_FAINT, true);
            drawText(graphics, "Settings", hit.settings().x() + design(13), hit.settings().y() + design(7),
                    8, hit.settings().contains(mouseX, mouseY) ? TEXT_PRIMARY : TEXT_MUTED, true);
            CarbonIcons.drawGui(graphics, "settings", hit.settings().x() + design(65),
                    hit.settings().y() + design(7), design(12), hit.settings().contains(mouseX, mouseY) ? ACCENT : TEXT_MUTED);
            drawSwitchKnob(graphics, hit.toggle(), module.enabled());
        }
        if (moduleHits.isEmpty()) {
            drawText(graphics, "Scroll to see more modules", contentRect.x(), moduleGridTop() + design(22),
                    9, TEXT_FAINT, false);
        }
    }

    private void extractSettingsText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (settingsPage == SettingsPage.MENU) {
            Rect appearance = menuAppearanceRect();
            drawText(graphics, "Interface appearance", appearance.x() + design(20), appearance.y() + design(20),
                    15, TEXT_PRIMARY, true);
            drawText(graphics, "One menu, two carefully tuned finishes.",
                    appearance.x() + design(20), appearance.y() + design(44), 9, TEXT_MUTED, false);
            drawText(graphics, "GLASS UI", appearance.x() + design(20), appearance.y() + design(83),
                    9, TEXT_PRIMARY, true);
            drawText(graphics, preferences.glassUi() ? "Translucent surfaces and soft blur" : "Solid charcoal surfaces, no glass shader",
                    appearance.x() + design(20), appearance.y() + design(102), 8, TEXT_MUTED, false);
            drawText(graphics, preferences.glassUi() ? "ON" : "OFF",
                    glassToggleRect.x() - design(29), glassToggleRect.y() + design(8), 8,
                    preferences.glassUi() ? ACCENT : TEXT_FAINT, true);
            drawText(graphics, "UI SCALE", appearance.x() + design(20), appearance.y() + design(151),
                    9, TEXT_PRIMARY, true);
            drawText(graphics, "Adjust Carbon interface scale", appearance.x() + design(20),
                    appearance.y() + design(169), 8, TEXT_MUTED, false);
            drawText(graphics, Math.round(preferences.uiScale() * 100) + "%",
                    scaleTrackRect.right() + design(16), scaleTrackRect.y() - design(6),
                    10, TEXT_PRIMARY, true);
            drawText(graphics, "−", scaleMinusRect.x() + design(8), scaleMinusRect.y() + design(4),
                    13, TEXT_PRIMARY, true);
            drawText(graphics, "+", scalePlusRect.x() + design(8), scalePlusRect.y() + design(4),
                    13, TEXT_PRIMARY, true);
            Rect sample = themePreviewRect();
            drawText(graphics, "LIVE PREVIEW", sample.x() + design(18), sample.y() + design(16),
                    8, TEXT_FAINT, true);
            drawText(graphics, preferences.glassUi() ? "Glass finish" : "Carbon dark finish",
                    sample.x() + design(18), sample.y() + design(42), 15, TEXT_PRIMARY, true);
            drawText(graphics, "Saved locally for your next Carbon session.",
                    sample.x() + design(18), sample.y() + design(68), 9, TEXT_MUTED, false);
            drawChip(graphics, new Rect(sample.x() + design(18), sample.y() + design(95),
                    design(86), design(22)), "CARBON", ACCENT);
            CarbonIcons.drawGui(graphics, "flask-conical", sample.right() - design(58), sample.y() + design(32),
                    design(34), ACCENT);
            drawMenuThemeDescription(graphics);
        } else {
            drawModuleSelectorText(graphics, mouseX, mouseY);
            if (selectedModule == null) {
                drawText(graphics, "No registered modules are available.", contentRect.x() + design(18),
                        selectedModuleRect.y() + design(26), 11, TEXT_MUTED, false);
                return;
            }
            drawText(graphics, selectedModule.name(), selectedModuleRect.x() + design(18),
                    selectedModuleRect.y() + design(16), 13, TEXT_PRIMARY, true);
            drawText(graphics, selectedModule.description(), selectedModuleRect.x() + design(18),
                    selectedModuleRect.y() + design(39), 8, TEXT_MUTED, false);
            drawText(graphics, selectedModule.enabled() ? "ON" : "OFF",
                    moduleEnableRect.x() - design(29), moduleEnableRect.y() + design(9), 8,
                    selectedModule.enabled() ? ACCENT : TEXT_FAINT, true);
            drawText(graphics, "Reset", resetSettingsRect.x() + design(15), resetSettingsRect.y() + design(7),
                    8, resetSettingsRect.contains(mouseX, mouseY) ? TEXT_PRIMARY : TEXT_MUTED, true);
            CarbonIcons.drawGui(graphics, "sliders-horizontal", resetSettingsRect.x() + design(54),
                    resetSettingsRect.y() + design(7), design(12), TEXT_MUTED);
            extractSettingRowsText(graphics, mouseX, mouseY);
        }
    }

    private void drawModuleSelectorText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (ModuleHit hit : moduleSelectorHits) {
            drawText(graphics, hit.module().name(), hit.card().x() + design(12), hit.card().y() + design(8),
                    8, hit.module() == selectedModule ? TEXT_PRIMARY : TEXT_MUTED, true);
        }
    }

    private void extractSettingRowsText(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (settingHits.isEmpty()) {
            drawText(graphics, "This module has no extra settings.", settingsListRect.x() + design(16),
                    settingsListRect.y() + design(18), 9, TEXT_MUTED, false);
            return;
        }
        for (SettingHit hit : settingHits) {
            Setting<?> setting = hit.setting();
            Rect row = hit.row();
            drawText(graphics, setting.label(), row.x() + design(15), row.y() + design(15),
                    10, TEXT_PRIMARY, true);
            drawText(graphics, setting.description(), row.x() + design(15), row.y() + design(37),
                    8, TEXT_MUTED, false);
            if (setting instanceof BoolSetting booleanSetting) {
                drawText(graphics, booleanSetting.enabled() ? "ON" : "OFF",
                        hit.control().x() - design(29), hit.control().y() + design(9), 8,
                        booleanSetting.enabled() ? ACCENT : TEXT_FAINT, true);
                drawSwitchKnob(graphics, hit.control(), booleanSetting.enabled());
            } else if (setting instanceof NumberSetting numberSetting) {
                drawText(graphics, numberSetting.displayValue(), hit.control().x(), hit.control().y() - design(15),
                        8, TEXT_PRIMARY, true);
                drawSliderThumb(graphics, hit.control(), numberFraction(numberSetting), ACCENT);
            } else if (setting instanceof ModeSetting modeSetting) {
                drawText(graphics, modeSetting.get(), hit.control().x() + design(10), hit.control().y() + design(9),
                        8, TEXT_PRIMARY, true);
                drawText(graphics, "›", hit.control().right() - design(17), hit.control().y() + design(7),
                        11, TEXT_MUTED, false);
            } else if (setting instanceof ColorSetting colorSetting) {
                int color = colorSetting.get();
                graphics.fill(hit.control().x(), hit.control().y(), hit.control().right(), hit.control().bottom(), color);
                drawText(graphics, String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF),
                        hit.control().x() + design(7), hit.control().y() + design(7), 7,
                        0xFFFFFFFF, true);
            } else if (setting instanceof KeybindSetting keybindSetting) {
                String label = capturingKeybind == keybindSetting ? "PRESS A KEY..." : keybindSetting.displayValue();
                drawText(graphics, label, hit.control().x() + design(8), hit.control().y() + design(9),
                        8, capturingKeybind == keybindSetting ? ACCENT : TEXT_PRIMARY, true);
            }
        }
    }

    private void drawNavLabel(GuiGraphicsExtractor graphics, Rect rect, String icon, String label, boolean active) {
        CarbonIcons.drawGui(graphics, icon, rect.x() + design(12), rect.y() + design(9), design(16),
                active ? ACCENT : TEXT_MUTED);
        drawText(graphics, label, rect.x() + design(39), rect.y() + design(11),
                10, active ? TEXT_PRIMARY : TEXT_MUTED, active);
        if (active) {
            graphics.fill(rect.x() + design(3), rect.y() + design(8), rect.x() + design(5),
                    rect.bottom() - design(8), ACCENT);
        }
    }

    private void drawCategoryLabel(GuiGraphicsExtractor graphics, Rect rect, Category category,
                                   String icon, String label, boolean active) {
        CarbonIcons.drawGui(graphics, icon, rect.x() + design(11), rect.y() + design(8), design(14),
                active ? ACCENT : TEXT_FAINT);
        drawText(graphics, label, rect.x() + design(36), rect.y() + design(9),
                9, active ? TEXT_PRIMARY : TEXT_MUTED, active);
        long count = modules.modules().stream()
                .filter(module -> category == null || module.category() == category).count();
        drawText(graphics, Integer.toString((int) count), rect.right() - design(29), rect.y() + design(9),
                8, active ? ACCENT : TEXT_FAINT, true);
    }

    private void drawTabLabel(GuiGraphicsExtractor graphics, Rect rect, String label, boolean active) {
        drawText(graphics, label, rect.x() + design(13), rect.y() + design(8),
                9, active ? TEXT_PRIMARY : TEXT_MUTED, active);
        if (active) {
            graphics.fill(rect.x() + design(12), rect.bottom() - design(3), rect.right() - design(12),
                    rect.bottom() - design(1), ACCENT);
        }
    }

    private void drawChip(GuiGraphicsExtractor graphics, Rect rect, String label, int color) {
        drawText(graphics, label, rect.x() + design(9), rect.y() + design(6), 7, color, true);
    }

    private void drawWrappedText(GuiGraphicsExtractor graphics, String text, int x, int y,
                                 int maxWidth, int size, int color, int maxLines) {
        if (text == null || text.isBlank() || maxLines <= 0) {
            return;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int lineIndex = 0;
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            String word = words[wordIndex];
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && CarbonText.widthDesign(font, candidate, CarbonText.Weight.REGULAR, size) > maxWidth) {
                if (lineIndex == maxLines - 1) {
                    String remainder = String.join(" ", java.util.Arrays.copyOfRange(words, wordIndex, words.length));
                    drawText(graphics, fitText(line + " " + remainder, size, maxWidth), x,
                            y + lineIndex * design(13), size, color, false);
                    return;
                }
                drawText(graphics, line.toString(), x, y + lineIndex * design(13), size, color, false);
                lineIndex++;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty() && lineIndex < maxLines) {
            drawText(graphics, fitText(line.toString(), size, maxWidth), x,
                    y + lineIndex * design(13), size, color, false);
        }
    }

    private String fitText(String text, int size, int maxWidth) {
        if (CarbonText.widthDesign(font, text, CarbonText.Weight.REGULAR, size) <= maxWidth) {
            return text;
        }
        String shortened = text;
        while (!shortened.isEmpty() && CarbonText.widthDesign(font, shortened + "…", CarbonText.Weight.REGULAR, size) > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y,
                          int size, int color, boolean bold) {
        CarbonText.drawUi(graphics, font, text,
                bold ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.REGULAR,
                size, x, y, color, false);
    }

    private void drawCenteredText(GuiGraphicsExtractor graphics, String text, int centerX, int y,
                                  int size, int color, boolean bold) {
        CarbonText.Weight weight = bold ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.REGULAR;
        int textWidth = Math.round(CarbonText.widthDesign(font, text, weight, size));
        CarbonText.drawUi(graphics, font, text, weight, size, centerX - textWidth / 2, y, color, false);
    }

    private void drawGlassTint(GuiGraphicsExtractor graphics, int x, int y, int drawWidth, int drawHeight, int argb) {
        CarbonGlass.drawTintedRect(graphics, x, y, drawWidth, drawHeight, design(8), argb);
    }

    private void drawSurface(GuiGraphicsExtractor graphics, Rect rect, Surface surface, boolean hovered) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        if (preferences.glassUi()) {
            CarbonGlass.drawPanelGui(graphics, rect.x(), rect.y(), rect.width(), rect.height(),
                    design(surface.radius()), surface.style());
            if (hovered) {
                drawGlassTint(graphics, rect.x(), rect.y(), rect.width(), rect.height(), 0x123FE68F);
            }
            if (surface == Surface.CARD || surface == Surface.CONTROL || surface == Surface.AVATAR) {
                graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), 0x227FE3A5);
            }
        } else {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), surface.solidColor());
            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                    hovered ? 0xFF405C4A : surface.borderColor());
            if (hovered) {
                graphics.fill(rect.x() + design(1), rect.y() + design(1), rect.right() - design(1),
                        rect.y() + design(2), 0xFF41664F);
            }
        }
    }

    private void drawButtonSurface(GuiGraphicsExtractor graphics, Rect rect, boolean active, boolean hovered) {
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        if (preferences.glassUi()) {
            int tint = active ? 0x6654E49A : hovered ? 0x343FE68F : 0x17101814;
            drawGlassTint(graphics, rect.x(), rect.y(), rect.width(), rect.height(), tint);
            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                    active ? 0x777FE3A5 : hovered ? 0x3C7FE3A5 : 0x1D7FE3A5);
        } else {
            int fill = active ? 0xFF183C2A : hovered ? 0xFF202A23 : 0xFF151B17;
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                    active ? 0xFF3A9B64 : hovered ? 0xFF405247 : 0xFF27312B);
        }
    }

    private void drawSwitchSurface(GuiGraphicsExtractor graphics, Rect rect, boolean enabled, boolean hovered) {
        if (preferences.glassUi()) {
            drawGlassTint(graphics, rect.x(), rect.y(), rect.width(), rect.height(),
                    enabled ? 0xA82AA662 : hovered ? 0x553FE68F : 0x48212A25);
            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                    enabled ? 0xAA77E1A2 : 0x607FE3A5);
        } else {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                    enabled ? 0xFF227D4A : hovered ? 0xFF344239 : 0xFF252E28);
            graphics.outline(rect.x(), rect.y(), rect.width(), rect.height(),
                    enabled ? 0xFF54C881 : 0xFF46534A);
        }
    }

    private void drawSwitchKnob(GuiGraphicsExtractor graphics, Rect rect, boolean enabled) {
        int diameter = Math.max(1, rect.height() - design(8));
        int x = enabled ? rect.right() - diameter - design(4) : rect.x() + design(4);
        int y = rect.y() + (rect.height() - diameter) / 2;
        graphics.fill(x, y, x + diameter, y + diameter, 0xFFF1F7F3);
        graphics.outline(x, y, diameter, diameter, 0x55303732);
    }

    private void drawSliderTrack(GuiGraphicsExtractor graphics, Rect rect, float value, float min, float max) {
        if (preferences.glassUi()) {
            drawGlassTint(graphics, rect.x(), rect.y(), rect.width(), rect.height(), 0x55374139);
        } else {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xFF354038);
        }
        float fraction = Math.max(0.0f, Math.min(1.0f, (value - min) / (max - min)));
        int filled = Math.round(rect.width() * fraction);
        graphics.fill(rect.x(), rect.y(), rect.x() + filled, rect.bottom(), ACCENT);
        drawSliderThumb(graphics, rect, fraction, ACCENT);
    }

    private void drawSliderThumb(GuiGraphicsExtractor graphics, Rect rect, float fraction, int color) {
        int diameter = Math.max(design(10), rect.height() + design(5));
        int centerX = rect.x() + Math.round(Math.max(0.0f, Math.min(1.0f, fraction)) * rect.width());
        int centerY = rect.y() + rect.height() / 2;
        graphics.fill(centerX - diameter / 2, centerY - diameter / 2,
                centerX + diameter / 2, centerY + diameter / 2, 0xFFF1F7F3);
        graphics.outline(centerX - diameter / 2, centerY - diameter / 2,
                diameter, diameter, color);
    }

    private void drawSettingControlSurface(GuiGraphicsExtractor graphics, SettingHit hit,
                                           int mouseX, int mouseY) {
        Setting<?> setting = hit.setting();
        Rect control = hit.control();
        if (setting instanceof BoolSetting boolSetting) {
            drawSwitchSurface(graphics, control, boolSetting.enabled(), control.contains(mouseX, mouseY));
        } else if (setting instanceof NumberSetting numberSetting) {
            drawSliderTrack(graphics, control, numberFraction(numberSetting), 0.0f, 1.0f);
        } else {
            drawButtonSurface(graphics, control, false, control.contains(mouseX, mouseY));
        }
    }

    private void extractToast(GuiGraphicsExtractor graphics) {
        if (toastMessage.isBlank() || System.nanoTime() > toastUntilNanos) {
            return;
        }
        int toastWidth = Math.max(design(180), Math.round(
                CarbonText.widthDesign(font, toastMessage, CarbonText.Weight.REGULAR, 9)) + design(34));
        Rect toast = new Rect(panelRect.x() + (panelRect.width() - toastWidth) / 2,
                panelRect.bottom() - design(52), toastWidth, design(32));
        drawSurface(graphics, toast, Surface.TOAST, false);
        drawText(graphics, "✓", toast.x() + design(11), toast.y() + design(8), 12, ACCENT, true);
        drawText(graphics, toastMessage, toast.x() + design(32), toast.y() + design(10), 9, TEXT_PRIMARY, true);
    }

    private void prepareModuleHits() {
        moduleHits.clear();
        presetHits.clear();
        if (activeView != View.MODULES) {
            return;
        }
        int presetGap = design(8);
        int presetWidth = Math.min(design(112), Math.max(design(74),
                (contentRect.width() - presetGap * 2) / Math.max(1, PRESETS.size())));
        int presetY = panelRect.y() + design(77);
        for (int i = 0; i < PRESETS.size(); i++) {
            Rect rect = new Rect(contentRect.x() + i * (presetWidth + presetGap), presetY,
                    presetWidth, design(26));
            presetHits.add(new PresetHit(PRESETS.get(i), rect));
        }

        List<Module> visible = filteredModules();
        if (visible.isEmpty()) {
            moduleScrollRows = 0;
            return;
        }
        int gap = design(14);
        int minimumCardWidth = design(350);
        moduleColumns = contentRect.width() >= minimumCardWidth * 2 + gap ? 2 : 1;
        int cardWidth = Math.max(design(220),
                (contentRect.width() - gap * (moduleColumns - 1)) / moduleColumns);
        int rowHeight = design(164);
        int rowStep = rowHeight + gap;
        int top = moduleGridTop();
        int availableHeight = Math.max(rowHeight, panelRect.bottom() - design(24) - top);
        moduleVisibleRows = Math.max(1, (availableHeight + gap) / rowStep);
        int totalRows = (visible.size() + moduleColumns - 1) / moduleColumns;
        moduleScrollRows = Math.max(0, Math.min(moduleScrollRows, Math.max(0, totalRows - moduleVisibleRows)));
        for (int index = 0; index < visible.size(); index++) {
            int row = index / moduleColumns;
            int column = index % moduleColumns;
            if (row < moduleScrollRows || row >= moduleScrollRows + moduleVisibleRows) {
                continue;
            }
            int x = contentRect.x() + column * (cardWidth + gap);
            int y = top + (row - moduleScrollRows) * rowStep;
            Rect card = new Rect(x, y, cardWidth, rowHeight);
            Rect toggle = new Rect(card.right() - design(61), card.y() + design(15), design(48), design(26));
            Rect settings = new Rect(card.right() - design(103), card.bottom() - design(39),
                    design(88), design(25));
            moduleHits.add(new ModuleHit(visible.get(index), card, toggle, settings));
        }
    }

    private void prepareSettingsHits() {
        moduleSelectorHits.clear();
        settingHits.clear();
        if (activeView != View.SETTINGS || settingsPage != SettingsPage.MODULE) {
            return;
        }
        int gap = design(8);
        int totalGap = gap * Math.max(0, modules.modules().size() - 1);
        int chipWidth = Math.max(design(72), (contentRect.width() - totalGap) / Math.max(1, modules.modules().size()));
        int x = contentRect.x();
        int y = panelRect.y() + design(119);
        for (Module module : modules.modules()) {
            moduleSelectorHits.add(new ModuleHit(module,
                    new Rect(x, y, Math.min(chipWidth, contentRect.right() - x), design(28)), Rect.EMPTY, Rect.EMPTY));
            x += chipWidth + gap;
        }
        selectedModuleRect = new Rect(contentRect.x(), panelRect.y() + design(158), contentRect.width(), design(70));
        moduleEnableRect = new Rect(selectedModuleRect.right() - design(67), selectedModuleRect.y() + design(21),
                design(48), design(26));
        resetSettingsRect = new Rect(selectedModuleRect.right() - design(156), selectedModuleRect.y() + design(21),
                design(66), design(26));
        settingsListRect = new Rect(contentRect.x(), panelRect.y() + design(244), contentRect.width(),
                Math.max(design(50), panelRect.bottom() - design(25) - (panelRect.y() + design(244))));
        if (selectedModule == null) {
            return;
        }
        int rowHeight = design(60);
        int gapRows = design(8);
        settingsVisibleRows = Math.max(1, (settingsListRect.height() + gapRows) / (rowHeight + gapRows));
        int totalRows = selectedModule.settings().size();
        settingsScrollRows = Math.max(0, Math.min(settingsScrollRows, Math.max(0, totalRows - settingsVisibleRows)));
        for (int index = settingsScrollRows;
             index < Math.min(totalRows, settingsScrollRows + settingsVisibleRows); index++) {
            Setting<?> setting = selectedModule.settings().get(index);
            Rect row = new Rect(settingsListRect.x(), settingsListRect.y() + (index - settingsScrollRows) * (rowHeight + gapRows),
                    settingsListRect.width(), rowHeight);
            Rect control = new Rect(row.right() - design(184), row.y() + design(20), design(164), design(20));
            settingHits.add(new SettingHit(setting, row, control));
        }
    }

    private void calculateLayout() {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        layoutScale = Math.max(0.35f, Math.min(1.15f, UiScale.rendererScale()));
        int marginX = design(24);
        int marginY = design(20);
        designPanelWidth = Math.min(design(1180), Math.max(design(500), safeWidth - marginX * 2));
        designPanelHeight = Math.min(design(760), Math.max(design(390), safeHeight - marginY * 2));
        designPanelWidth = Math.min(designPanelWidth, safeWidth - Math.min(marginX, safeWidth / 4));
        designPanelHeight = Math.min(designPanelHeight, safeHeight - Math.min(marginY, safeHeight / 4));
        designPanelX = Math.max(0, (safeWidth - designPanelWidth) / 2);
        designPanelY = Math.max(0, (safeHeight - designPanelHeight) / 2);
        panelRect = new Rect(designPanelX, designPanelY, Math.max(1, designPanelWidth), Math.max(1, designPanelHeight));
        sidebarWidth = Math.min(design(236), Math.max(design(174), panelRect.width() / 4));
        sidebarWidth = Math.min(sidebarWidth, Math.max(design(150), panelRect.width() - design(300)));
        sidebarRect = new Rect(panelRect.x(), panelRect.y(), sidebarWidth, panelRect.height());

        int sideInset = design(13);
        accountRect = new Rect(panelRect.x() + sideInset, panelRect.y() + design(77),
                sidebarRect.width() - sideInset * 2, design(80));
        avatarRect = new Rect(accountRect.x() + design(12), accountRect.y() + design(13), design(52), design(52));
        modulesNavRect = new Rect(panelRect.x() + sideInset, panelRect.y() + design(177),
                sidebarRect.width() - sideInset * 2, design(36));
        settingsNavRect = new Rect(panelRect.x() + sideInset, panelRect.y() + design(219),
                sidebarRect.width() - sideInset * 2, design(36));
        allCategoryRect = new Rect(panelRect.x() + sideInset, panelRect.y() + design(282),
                sidebarRect.width() - sideInset * 2, design(30));
        categoryHits.clear();
        int categoryY = allCategoryRect.bottom() + design(5);
        for (Category category : Category.values()) {
            if (!categoryPresence.containsKey(category)) {
                continue;
            }
            Rect rect = new Rect(allCategoryRect.x(), categoryY, allCategoryRect.width(), design(29));
            categoryHits.add(new CategoryHit(category, rect));
            categoryY += design(32);
        }
        profileButtonRect = new Rect(panelRect.x() + sideInset,
                panelRect.bottom() - design(67), sidebarRect.width() - sideInset * 2, design(52));
        createProfileRect = new Rect(profileButtonRect.right() - design(34), profileButtonRect.y() + design(8),
                design(25), design(25));
        closeRect = new Rect(panelRect.right() - design(40), panelRect.y() + design(13), design(27), design(27));
        int contentLeft = panelRect.x() + sidebarRect.width() + design(27);
        int contentRight = panelRect.right() - design(28);
        contentRect = new Rect(contentLeft, panelRect.y(), Math.max(1, contentRight - contentLeft), panelRect.height());
        contentTitleRect = new Rect(contentRect.x(), panelRect.y() + design(18), contentRect.width(), design(52));
        int searchWidth = Math.min(design(278), Math.max(design(164), contentRect.width() / 3));
        searchRect = new Rect(contentRect.right() - searchWidth - design(44), panelRect.y() + design(22),
                searchWidth, design(36));
        searchClearRect = new Rect(searchRect.right() - design(27), searchRect.y() + design(5), design(20), design(25));
        settingsMenuTabRect = new Rect(contentRect.x(), panelRect.y() + design(78), design(102), design(31));
        settingsFeatureTabRect = new Rect(settingsMenuTabRect.right() + design(7), settingsMenuTabRect.y(),
                design(143), design(31));
        glassToggleRect = new Rect(contentRect.right() - design(86), panelRect.y() + design(183), design(51), design(28));
        scaleTrackRect = new Rect(contentRect.x() + design(206), panelRect.y() + design(291),
                Math.max(design(90), contentRect.width() - design(374)), design(5));
        scaleHitRect = new Rect(scaleTrackRect.x() - design(6), scaleTrackRect.y() - design(10),
                scaleTrackRect.width() + design(12), design(25));
        scaleMinusRect = new Rect(scaleTrackRect.x() - design(31), scaleTrackRect.y() - design(10), design(24), design(24));
        scalePlusRect = new Rect(scaleTrackRect.right() + design(49), scaleTrackRect.y() - design(10), design(24), design(24));
        if (searchBox != null) {
            searchBox.setRectangle(Math.max(1, searchRect.width() - design(69)),
                    Math.max(1, searchRect.height() - design(8)), searchRect.x() + design(35), searchRect.y() + design(4));
        }
        prepareModuleHits();
        prepareSettingsHits();
    }

    private Rect menuAppearanceRect() {
        return new Rect(contentRect.x(), panelRect.y() + design(128), contentRect.width(), design(205));
    }

    private Rect themePreviewRect() {
        return new Rect(contentRect.x(), panelRect.y() + design(351), contentRect.width(), design(150));
    }

    private int moduleGridTop() {
        return panelRect.y() + design(123);
    }

    private int design(int value) {
        return Math.max(1, Math.round(value * layoutScale));
    }

    private List<Module> filteredModules() {
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module module : modules.modules()) {
            if (selectedCategory != null && module.category() != selectedCategory) {
                continue;
            }
            if (!query.isEmpty()) {
                String searchable = (module.name() + " " + module.description() + " "
                        + module.category().label()).toLowerCase(Locale.ROOT);
                if (!searchable.contains(query)) {
                    continue;
                }
            }
            result.add(module);
        }
        return result;
    }

    private void openModuleSettings(Module module) {
        selectedModule = module;
        settingsPage = SettingsPage.MODULE;
        settingsScrollRows = 0;
        activeView = View.SETTINGS;
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
        calculateLayout();
    }

    private void setView(View view) {
        activeView = view;
        if (view == View.SETTINGS) {
            settingsPage = SettingsPage.MENU;
        }
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
        calculateLayout();
    }

    private void applyPreset(Preset preset) {
        for (Module module : modules.modules()) {
            Boolean enabled = preset.modules().get(module.id());
            if (enabled != null && module.enabled() != enabled) {
                module.toggle();
            }
        }
        showToast(preset.name() + " setup applied");
    }

    private void toggleTheme() {
        preferences.setGlassUi(!preferences.glassUi());
        showToast(preferences.glassUi() ? "Glass finish enabled" : "Carbon dark finish enabled");
    }

    private void applyGlassSafetyFallback() {
        String failure = CarbonGlass.failureMessage();
        if (preferences.glassUi() && failure != null
                && (failure.toLowerCase(Locale.ROOT).contains("glass shader")
                || failure.toLowerCase(Locale.ROOT).contains("glass pipeline"))) {
            preferences.setGlassUi(false);
            showToast("Glass renderer unavailable; Carbon dark is active");
        }
    }

    private void updateUiScaleFromMouse(double mouseX, boolean persist) {
        float fraction = scaleTrackRect.width() <= 0 ? 0.5f
                : (float) ((mouseX - scaleTrackRect.x()) / scaleTrackRect.width());
        float value = 0.75f + Math.max(0.0f, Math.min(1.0f, fraction)) * 0.75f;
        updateUiScale(value, persist);
    }

    private void updateUiScale(float value, boolean persist) {
        float clamped = Math.max(0.75f, Math.min(1.50f, value));
        if (persist) {
            preferences.setUiScale(clamped);
        } else {
            preferences.setUiScaleLive(clamped);
        }
        UiScale.setUserScaleMultiplier(clamped);
        UiScale.update(Minecraft.getInstance());
        calculateLayout();
    }

    private void beginKeybindCapture(KeybindSetting setting) {
        capturingKeybind = setting;
        showToast("Press a key or click a mouse button");
    }

    private void changeSettingFromClick(SettingHit hit) {
        Setting<?> setting = hit.setting();
        if (setting instanceof BoolSetting boolSetting) {
            boolSetting.toggle();
        } else if (setting instanceof NumberSetting numberSetting) {
            draggingNumberSetting = numberSetting;
            updateNumberSetting(numberSetting, hit.control(), lastMouseX);
        } else if (setting instanceof ModeSetting modeSetting) {
            modeSetting.cycle();
        } else if (setting instanceof ColorSetting colorSetting) {
            colorSetting.set(nextColor(colorSetting.get()));
        } else if (setting instanceof KeybindSetting keybindSetting) {
            beginKeybindCapture(keybindSetting);
        }
    }

    private int nextColor(int current) {
        for (int i = 0; i < COLOR_CHOICES.length; i++) {
            if ((COLOR_CHOICES[i] & 0xFFFFFF) == (current & 0xFFFFFF)) {
                return COLOR_CHOICES[(i + 1) % COLOR_CHOICES.length];
            }
        }
        return COLOR_CHOICES[0];
    }

    private void updateNumberSetting(NumberSetting setting, Rect control, double mouseX) {
        if (control.width() <= 0) {
            return;
        }
        float fraction = (float) ((mouseX - control.x()) / control.width());
        setting.setFromFraction(Math.max(0.0f, Math.min(1.0f, fraction)));
    }

    private float numberFraction(NumberSetting setting) {
        double range = setting.maximum() - setting.minimum();
        if (range <= 0.0) {
            return 0.0f;
        }
        return (float) Math.max(0.0, Math.min(1.0,
                (setting.get() - setting.minimum()) / range));
    }

    private String activeProfileName() {
        try {
            String profile = configManager.activeProfile();
            if (profile == null || profile.isBlank() || profile.equalsIgnoreCase("default")) {
                return "Default";
            }
            String words = profile.replace('-', ' ').replace('_', ' ');
            return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not read the active Carbon profile name", failure);
            return "Default";
        }
    }

    private void cycleProfile() {
        List<String> profiles = configManager.listProfiles();
        if (profiles.isEmpty()) {
            showToast("No saved profiles yet");
            return;
        }
        int current = profiles.indexOf(configManager.activeProfile());
        String next = profiles.get((current + 1 + profiles.size()) % profiles.size());
        if (configManager.switchProfile(next)) {
            showToast("Profile: " + activeProfileName());
        }
    }

    private void createProfile() {
        int index = 1;
        String name;
        List<String> profiles = configManager.listProfiles();
        do {
            name = "carbon-" + index++;
        } while (profiles.contains(name));
        if (configManager.createProfile(name) && configManager.switchProfile(name)) {
            showToast("Created " + activeProfileName());
        } else {
            showToast("Could not create the profile");
        }
    }

    private void resetModuleSettings() {
        if (selectedModule == null) {
            return;
        }
        for (Setting<?> setting : selectedModule.settings()) {
            setting.reset();
        }
        showToast("Settings reset to defaults");
    }

    private void showToast(String message) {
        toastMessage = message;
        toastUntilNanos = System.nanoTime() + 2_200_000_000L;
    }

    private String moduleIcon(Module module) {
        return switch (module.id()) {
            case "fps_hud" -> "gauge";
            case "cps_hud" -> "mouse-pointer-click";
            case "keystrokes_hud" -> "keyboard";
            case "zoom" -> "zoom-in";
            default -> "layout-dashboard";
        };
    }

    private String categoryIcon(Category category) {
        return switch (category) {
            case HUD -> "gauge";
            case VISUAL -> "eye";
            case UTILITY -> "map-pin";
            case PERFORMANCE -> "flask-conical";
        };
    }

    private void drawDebugLayout(GuiGraphicsExtractor graphics) {
        if (!CarbonScreen.isLayoutDebugEnabled()) {
            return;
        }
        graphics.outline(panelRect.x(), panelRect.y(), panelRect.width(), panelRect.height(), 0xFFFF4040);
        graphics.outline(sidebarRect.x(), sidebarRect.y(), sidebarRect.width(), sidebarRect.height(), 0xFF40FFFF);
        graphics.outline(contentRect.x(), contentRect.y(), contentRect.width(), contentRect.height(), 0xFFFF40FF);
    }

    private void handleCategoryClick(Category category) {
        selectedCategory = category;
        moduleScrollRows = 0;
        calculateLayout();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (suppressCapturedMouse) {
            suppressCapturedMouse = false;
            return true;
        }
        if (capturingKeybind != null) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        if (searchBox != null && activeView == View.MODULES && searchBox.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(event, doubleClick);
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
        if (createProfileRect.contains(mouseX, mouseY)) {
            createProfile();
            return true;
        }
        if (profileButtonRect.contains(mouseX, mouseY)) {
            cycleProfile();
            return true;
        }
        if (allCategoryRect.contains(mouseX, mouseY)) {
            if (activeView != View.MODULES) {
                setView(View.MODULES);
            }
            handleCategoryClick(null);
            return true;
        }
        for (CategoryHit hit : categoryHits) {
            if (hit.rect().contains(mouseX, mouseY)) {
                if (activeView != View.MODULES) {
                    setView(View.MODULES);
                }
                handleCategoryClick(hit.category());
                return true;
            }
        }
        if (activeView == View.MODULES) {
            for (PresetHit hit : presetHits) {
                if (hit.rect().contains(mouseX, mouseY)) {
                    applyPreset(hit.preset());
                    return true;
                }
            }
            if (searchClearRect.contains(mouseX, mouseY)) {
                if (searchBox != null) {
                    searchBox.setValue("");
                    searchBox.setFocused(false);
                }
                return true;
            }
            for (ModuleHit hit : moduleHits) {
                if (hit.settings().contains(mouseX, mouseY)) {
                    openModuleSettings(hit.module());
                    return true;
                }
                if (hit.card().contains(mouseX, mouseY)) {
                    hit.module().toggle();
                    return true;
                }
            }
        } else {
            if (settingsMenuTabRect.contains(mouseX, mouseY)) {
                settingsPage = SettingsPage.MENU;
                calculateLayout();
                return true;
            }
            if (settingsFeatureTabRect.contains(mouseX, mouseY)) {
                settingsPage = SettingsPage.MODULE;
                if (selectedModule == null) {
                    selectedModule = modules.modules().stream().findFirst().orElse(null);
                }
                calculateLayout();
                return true;
            }
            if (settingsPage == SettingsPage.MENU) {
                if (glassToggleRect.contains(mouseX, mouseY)) {
                    toggleTheme();
                    return true;
                }
                if (scaleMinusRect.contains(mouseX, mouseY)) {
                    updateUiScale(preferences.uiScale() - 0.05f, true);
                    return true;
                }
                if (scalePlusRect.contains(mouseX, mouseY)) {
                    updateUiScale(preferences.uiScale() + 0.05f, true);
                    return true;
                }
                if (scaleHitRect.contains(mouseX, mouseY)) {
                    draggingUiScale = true;
                    updateUiScaleFromMouse(mouseX, false);
                    return true;
                }
            } else {
                for (ModuleHit hit : moduleSelectorHits) {
                    if (hit.card().contains(mouseX, mouseY)) {
                        selectedModule = hit.module();
                        settingsScrollRows = 0;
                        calculateLayout();
                        return true;
                    }
                }
                if (selectedModule != null && moduleEnableRect.contains(mouseX, mouseY)) {
                    selectedModule.toggle();
                    return true;
                }
                if (resetSettingsRect.contains(mouseX, mouseY)) {
                    resetModuleSettings();
                    return true;
                }
                for (SettingHit hit : settingHits) {
                    if (hit.row().contains(mouseX, mouseY)) {
                        lastMouseX = mouseX;
                        changeSettingFromClick(hit);
                        return true;
                    }
                }
            }
        }
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
        return panelRect.contains(mouseX, mouseY) || super.mouseClicked(event, doubleClick);
    }

    private double lastMouseX;

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (draggingUiScale && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateUiScaleFromMouse(event.x(), false);
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
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (draggingUiScale) {
            draggingUiScale = false;
            preferences.setUiScale(preferences.uiScale());
            return true;
        }
        draggingNumberSetting = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeView == View.MODULES && contentRect.contains(mouseX, mouseY)) {
            int totalRows = (filteredModules().size() + moduleColumns - 1) / moduleColumns;
            int maxRows = Math.max(0, totalRows - moduleVisibleRows);
            moduleScrollRows = Math.max(0, Math.min(maxRows,
                    moduleScrollRows + (verticalAmount < 0 ? 1 : -1)));
            calculateLayout();
            return true;
        }
        if (activeView == View.SETTINGS && settingsPage == SettingsPage.MODULE
                && settingsListRect.contains(mouseX, mouseY)) {
            int maxRows = selectedModule == null ? 0
                    : Math.max(0, selectedModule.settings().size() - settingsVisibleRows);
            settingsScrollRows = Math.max(0, Math.min(maxRows,
                    settingsScrollRows + (verticalAmount < 0 ? 1 : -1)));
            calculateLayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (suppressCapturedKey) {
            suppressCapturedKey = false;
            return true;
        }
        if (capturingKeybind != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            capturingKeybind.set(KeybindSetting.Binding.unboundBinding());
            capturingKeybind = null;
            showToast("Keybind cleared");
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean captureKey(KeyInputEvent event) {
        if (capturingKeybind == null || event.action() != GLFW.GLFW_PRESS) {
            return false;
        }
        if (event.keyCode() == GLFW.GLFW_KEY_ESCAPE) {
            capturingKeybind.set(KeybindSetting.Binding.unboundBinding());
            showToast("Keybind cleared");
        } else {
            capturingKeybind.set(KeybindSetting.Binding.keyboard(event.keyCode()));
            showToast("Keybind updated");
        }
        capturingKeybind = null;
        suppressCapturedKey = true;
        return true;
    }

    public boolean captureMouse(MouseInputEvent event) {
        if (capturingKeybind == null || event.action() != GLFW.GLFW_PRESS) {
            return false;
        }
        capturingKeybind.set(KeybindSetting.Binding.mouseButton(event.button()));
        capturingKeybind = null;
        suppressCapturedMouse = true;
        showToast("Mouse bind updated");
        return true;
    }

    private void drawMenuThemeDescription(GuiGraphicsExtractor graphics) {
        Rect sample = themePreviewRect();
        graphics.fill(sample.x() + design(18), sample.y() + design(84), sample.right() - design(18),
                sample.y() + design(85), preferences.glassUi() ? 0x557FE3A5 : 0xFF344139);
    }

    private void drawOpeningAccent(GuiGraphicsExtractor graphics, float opening) {
        int accentWidth = Math.round(panelRect.width() * opening);
        if (accentWidth > 0) {
            graphics.fill(panelRect.x(), panelRect.y(), panelRect.x() + accentWidth,
                    panelRect.y() + design(2), ACCENT);
        }
    }

    private void drawModuleScrollIndicator(GuiGraphicsExtractor graphics) {
        List<Module> visible = filteredModules();
        int totalRows = (visible.size() + moduleColumns - 1) / moduleColumns;
        if (totalRows <= moduleVisibleRows) {
            return;
        }
        int maxScroll = Math.max(1, totalRows - moduleVisibleRows);
        int trackX = contentRect.right() - design(4);
        int trackY = moduleGridTop();
        int trackHeight = Math.max(design(30), panelRect.bottom() - design(26) - trackY);
        graphics.fill(trackX, trackY, trackX + design(2), trackY + trackHeight, 0x553F4C43);
        int thumbY = trackY + Math.round((float) moduleScrollRows / maxScroll * (trackHeight - design(28)));
        graphics.fill(trackX, thumbY, trackX + design(2), thumbY + design(28), ACCENT);
    }

    private void drawSettingScrollIndicator(GuiGraphicsExtractor graphics) {
        if (selectedModule == null || selectedModule.settings().size() <= settingsVisibleRows) {
            return;
        }
        int trackX = settingsListRect.right() - design(3);
        int trackY = settingsListRect.y();
        int trackHeight = settingsListRect.height();
        graphics.fill(trackX, trackY, trackX + design(2), trackY + trackHeight, 0x553F4C43);
        int maxScroll = Math.max(1, selectedModule.settings().size() - settingsVisibleRows);
        int thumbY = trackY + Math.round((float) settingsScrollRows / maxScroll * Math.max(0, trackHeight - design(23)));
        graphics.fill(trackX, thumbY, trackX + design(2), thumbY + design(23), ACCENT);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0f - value;
        return 1.0f - inverse * inverse * inverse;
    }

    private enum View {
        MODULES,
        SETTINGS
    }

    private enum SettingsPage {
        MENU,
        MODULE
    }

    private enum Surface {
        MAIN(CarbonGlass.Style.MAIN, 24, 0xFF101612, 0xFF2B382F),
        SIDEBAR(CarbonGlass.Style.MAIN, 18, 0xFF0C110E, 0xFF222B25),
        CARD(CarbonGlass.Style.CARD_OFF, 15, 0xFF171E19, 0xFF29352D),
        CONTROL(CarbonGlass.Style.CARD_OFF, 10, 0xFF111713, 0xFF303B33),
        AVATAR(CarbonGlass.Style.CARD_OFF, 14, 0xFF101713, 0xFF435249),
        TOAST(CarbonGlass.Style.CARD_ON, 12, 0xFF1A241D, 0xFF43564A);

        private final CarbonGlass.Style style;
        private final int radius;
        private final int solidColor;
        private final int borderColor;

        Surface(CarbonGlass.Style style, int radius, int solidColor, int borderColor) {
            this.style = style;
            this.radius = radius;
            this.solidColor = solidColor;
            this.borderColor = borderColor;
        }

        CarbonGlass.Style style() {
            return style;
        }

        int radius() {
            return radius;
        }

        int solidColor() {
            return solidColor;
        }

        int borderColor() {
            return borderColor;
        }
    }

    private record Rect(int x, int y, int width, int height) {
        private static final Rect EMPTY = new Rect(0, 0, 0, 0);

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double pointX, double pointY) {
            return width > 0 && height > 0 && pointX >= x && pointY >= y
                    && pointX < right() && pointY < bottom();
        }
    }

    private record ModuleHit(Module module, Rect card, Rect toggle, Rect settings) {
    }

    private record SettingHit(Setting<?> setting, Rect row, Rect control) {
    }

    private record CategoryHit(Category category, Rect rect) {
    }

    private record PresetHit(Preset preset, Rect rect) {
    }

    private record Preset(String name, Map<String, Boolean> modules) {
    }
}
