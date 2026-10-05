package dev.carbon.client.ui;

import dev.carbon.client.ui.render.CarbonShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Visual test harness for Carbon's Part 1 surfaces, typography, Lucide atlas, and layout bounds. */
public final class CarbonStyleTestScreen extends CarbonScreen {
    private static final float WINDOW_WIDTH = 1000.0f;
    private static final float WINDOW_HEIGHT = 612.0f;
    private static final float HEADER_HEIGHT = 64.0f;
    private static final float SIDEBAR_WIDTH = 214.0f;
    private static final float CARD_WIDTH = 238.0f;
    private static final float CARD_HEIGHT = 228.0f;
    private static final int CARD_COUNT = 4;
    private static final String[] TABS = {"MODS", "WAYPOINTS", "SETTINGS"};
    private static final float[] TAB_WIDTHS = {70.0f, 108.0f, 86.0f};
    private static final String[] FILTERS = {"ALL", "NEW", "HUD", "VISUAL", "UTILITY", "PERFORMANCE"};
    private static final float[] FILTER_WIDTHS = {50.0f, 48.0f, 46.0f, 64.0f, 68.0f, 92.0f};
    private static final String[] CARD_NAMES = {"FPS", "KEYSTROKES", "CPS", "ZOOM"};
    private static final String[] CARD_DESCRIPTIONS = {
            "Live frame-rate monitor", "Show pressed movement keys", "Clicks per second", "Smooth zoom control"
    };
    private static final String[] CARD_ICONS = {"gauge", "keyboard", "mouse-pointer-click", "zoom-in"};
    private static final String[] CARD_CATEGORIES = {"HUD", "HUD", "HUD", "VISUAL"};
    private static final String CARD_TOOLTIP = "Click the status bar to toggle this style sample.";
    private static final String BUTTON_TOOLTIP = "Button samples show Carbon's pressed and hover states.";
    private static final String SWITCH_TOOLTIP = "This switch is interactive in the style test.";

    private final Bounds window = new Bounds();
    private final Bounds header = new Bounds();
    private final Bounds sidebar = new Bounds();
    private final Bounds content = new Bounds();
    private final Bounds closeButton = new Bounds();
    private final Bounds profileRow = new Bounds();
    private final Bounds profileAddButton = new Bounds();
    private final Bounds profileDeleteButton = new Bounds();
    private final Bounds createProfileButton = new Bounds();
    private final Bounds hudEditorButton = new Bounds();
    private final Bounds viewGridButton = new Bounds();
    private final Bounds viewListButton = new Bounds();
    private final Bounds sortButton = new Bounds();
    private final Bounds searchField = new Bounds();
    private final Bounds samplePanel = new Bounds();
    private final Bounds primarySampleButton = new Bounds();
    private final Bounds secondarySampleButton = new Bounds();
    private final Bounds sampleSwitch = new Bounds();
    private final Bounds sampleSwitchTrack = new Bounds();
    private final Bounds redStateBar = new Bounds();
    private final Bounds[] navRows = {new Bounds(), new Bounds(), new Bounds()};
    private final Bounds[] tabs = {new Bounds(), new Bounds(), new Bounds()};
    private final Bounds[] chips = {
            new Bounds(), new Bounds(), new Bounds(), new Bounds(), new Bounds(), new Bounds()
    };
    private final Bounds[] cards = {new Bounds(), new Bounds(), new Bounds(), new Bounds()};
    private final Bounds[] cardOptions = {new Bounds(), new Bounds(), new Bounds(), new Bounds()};
    private final Bounds[] cardSettings = {new Bounds(), new Bounds(), new Bounds(), new Bounds()};
    private final Bounds[] cardStatus = {new Bounds(), new Bounds(), new Bounds(), new Bounds()};

    private final boolean[] demoEnabled = {true, false, true, false};
    private int selectedTab;
    private int selectedFilter;
    private int selectedView;
    private boolean selectedProfile = true;
    private boolean demoSwitch = true;
    private boolean primaryPressed = true;
    private boolean secondaryPressed;
    private boolean sortDescending;
    private boolean layoutDebug;
    private int hoverKind;
    private int hoverCard = -1;
    private long hoverStartedAt;
    private String viewportMeasurement = "VIEW 1920 x 1080";
    private String debugCardMeasurement = "CARD 238 x 228  |  VIEW 1920 x 1080";

    public CarbonStyleTestScreen() {
        super(Component.literal("Carbon Style Test"), null);
    }

    @Override
    protected void init() {
        super.init();
        UiScale.update(Minecraft.getInstance());
        calculateLayout();
        CarbonIcons.load();
    }

    private void calculateLayout() {
        float windowX = (UiScale.designWidth() - WINDOW_WIDTH) * 0.5f;
        float windowY = (UiScale.designHeight() - WINDOW_HEIGHT) * 0.5f;
        window.set(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT);
        header.set(windowX, windowY, WINDOW_WIDTH, HEADER_HEIGHT);
        sidebar.set(windowX, windowY + HEADER_HEIGHT, SIDEBAR_WIDTH, WINDOW_HEIGHT - HEADER_HEIGHT);
        content.set(windowX + SIDEBAR_WIDTH, windowY + HEADER_HEIGHT,
                WINDOW_WIDTH - SIDEBAR_WIDTH, WINDOW_HEIGHT - HEADER_HEIGHT);
        closeButton.set(windowX + 952.0f, windowY + 16.0f, 30.0f, 30.0f);

        float tabGroupWidth = TAB_WIDTHS[0] + TAB_WIDTHS[1] + TAB_WIDTHS[2] + 28.0f;
        float tabX = windowX + (WINDOW_WIDTH - tabGroupWidth) * 0.5f;
        for (int index = 0; index < tabs.length; index++) {
            tabs[index].set(tabX, windowY + 17.0f, TAB_WIDTHS[index], 30.0f);
            tabX += TAB_WIDTHS[index] + 14.0f;
        }

        profileRow.set(windowX + 12.0f, windowY + 83.0f, 190.0f, 34.0f);
        profileAddButton.set(windowX + 12.0f, windowY + 476.0f, 36.0f, 36.0f);
        profileDeleteButton.set(windowX + 54.0f, windowY + 476.0f, 36.0f, 36.0f);
        for (int index = 0; index < navRows.length; index++) {
            navRows[index].set(windowX + 12.0f, windowY + 221.0f + index * 38.0f,
                    190.0f, 32.0f);
        }
        createProfileButton.set(windowX + 12.0f, windowY + 528.0f, 190.0f, 28.0f);
        hudEditorButton.set(windowX + 12.0f, windowY + 566.0f, 190.0f, 34.0f);

        float chipX = windowX + 230.0f;
        for (int index = 0; index < chips.length; index++) {
            chips[index].set(chipX, windowY + 80.0f, FILTER_WIDTHS[index], 28.0f);
            chipX += FILTER_WIDTHS[index] + 8.0f;
        }
        viewGridButton.set(windowX + 649.0f, windowY + 79.0f, 30.0f, 30.0f);
        viewListButton.set(windowX + 684.0f, windowY + 79.0f, 30.0f, 30.0f);
        sortButton.set(windowX + 719.0f, windowY + 79.0f, 30.0f, 30.0f);
        searchField.set(windowX + 754.0f, windowY + 79.0f, 230.0f, 30.0f);

        cards[0].set(windowX + 230.0f, windowY + 124.0f, CARD_WIDTH, CARD_HEIGHT);
        cards[1].set(windowX + 482.0f, windowY + 124.0f, CARD_WIDTH, CARD_HEIGHT);
        cards[2].set(windowX + 734.0f, windowY + 124.0f, CARD_WIDTH, CARD_HEIGHT);
        cards[3].set(windowX + 230.0f, windowY + 366.0f, CARD_WIDTH, CARD_HEIGHT);
        for (int index = 0; index < CARD_COUNT; index++) {
            Bounds card = cards[index];
            cardOptions[index].set(card.x, card.y + 151.0f, 198.0f, 36.0f);
            cardSettings[index].set(card.x + 202.0f, card.y + 151.0f, 36.0f, 36.0f);
            cardStatus[index].set(card.x + 1.0f, card.y + 192.0f, CARD_WIDTH - 2.0f, 36.0f);
        }

        samplePanel.set(windowX + 482.0f, windowY + 366.0f, 490.0f, CARD_HEIGHT);
        primarySampleButton.set(samplePanel.x + 18.0f, samplePanel.y + 74.0f, 170.0f, 34.0f);
        secondarySampleButton.set(samplePanel.x + 198.0f, samplePanel.y + 74.0f, 170.0f, 34.0f);
        sampleSwitch.set(samplePanel.x + 18.0f, samplePanel.y + 136.0f, 46.0f, 26.0f);
        redStateBar.set(samplePanel.x + 296.0f, samplePanel.y + 136.0f, 176.0f, 26.0f);
        sampleSwitchTrack.set(sampleSwitch.x, sampleSwitch.y + 1.0f,
                sampleSwitch.width, sampleSwitch.height - 2.0f);

        viewportMeasurement = "VIEW " + Math.round(UiScale.designWidth()) + " x "
                + Math.round(UiScale.designHeight());
        debugCardMeasurement = "CARD 238 x 228  |  " + viewportMeasurement;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (UiScale.update(Minecraft.getInstance())) {
            calculateLayout();
        }
        float designMouseX = UiScale.toDesign(mouseX);
        float designMouseY = UiScale.toDesign(mouseY);
        updateHover(designMouseX, designMouseY);

        UiScale.pushRendererScale(graphics);
        if (!CarbonShapes.isReady()) {
            drawRenderFailure(graphics);
            UiScale.popRendererScale(graphics);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        float viewportWidth = UiScale.designWidth();
        float viewportHeight = UiScale.designHeight();
        CarbonShapes.drawRounded(graphics, 0.0f, 0.0f, viewportWidth, viewportHeight,
                0.0f, CarbonTheme.BACKDROP);
        CarbonShapes.drawVignette(graphics, 0.0f, 0.0f, viewportWidth, viewportHeight, CarbonTheme.VIGNETTE);
        drawWindowSurfaces(graphics, designMouseX, designMouseY);

        graphics.nextStratum();
        drawWindowTextAndIcons(graphics, designMouseX, designMouseY);

        graphics.nextStratum();
        drawTooltip(graphics, designMouseX, designMouseY);
        if (layoutDebug) {
            drawLayoutDebug(graphics);
        }
        UiScale.popRendererScale(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawWindowSurfaces(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        CarbonShapes.drawShadow(graphics, window.x, window.y, window.width, window.height,
                12.0f, 64.0f, 24.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, window.x, window.y, window.width, window.height,
                12.0f, 1.0f, CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.WINDOW_TOP, CarbonTheme.WINDOW_BOTTOM);
        CarbonShapes.drawTopHighlight(graphics, window.x + 1.0f, window.y + 1.0f,
                window.width - 2.0f, 12.0f, CarbonTheme.WINDOW_HIGHLIGHT);
        CarbonShapes.drawGradientRect(graphics, header.x + 1.0f, header.y + 1.0f,
                header.width - 2.0f, header.height, 11.0f, CarbonTheme.HEADER_TOP, CarbonTheme.HEADER_BOTTOM);
        CarbonShapes.drawGradientRect(graphics, sidebar.x + 1.0f, sidebar.y + 7.0f,
                sidebar.width, sidebar.height - 8.0f, 11.0f, CarbonTheme.SIDEBAR_TOP, CarbonTheme.SIDEBAR_BOTTOM);
        CarbonShapes.drawGradientRect(graphics, content.x + 1.0f, content.y + 7.0f,
                content.width - 2.0f, content.height - 8.0f, 10.0f,
                CarbonTheme.CONTENT_TOP, CarbonTheme.CONTENT_BOTTOM);
        CarbonShapes.drawGradientRect(graphics, window.x + 1.0f, window.y + 63.0f,
                window.width - 2.0f, 1.0f, 0.0f, CarbonTheme.DIVIDER, CarbonTheme.DIVIDER);
        CarbonShapes.drawGradientRect(graphics, sidebar.x + SIDEBAR_WIDTH - 1.0f,
                sidebar.y + 18.0f, 1.0f, sidebar.height - 36.0f, 0.0f,
                CarbonTheme.DIVIDER, CarbonTheme.DIVIDER);

        drawSidebarSurfaces(graphics, mouseX, mouseY);
        drawHeaderSurfaces(graphics, mouseX, mouseY);
        drawFilterSurfaces(graphics, mouseX, mouseY);
        drawCardSurfaces(graphics, mouseX, mouseY);
        drawSamplePanelSurfaces(graphics, mouseX, mouseY);
    }

    private void drawSidebarSurfaces(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        drawNavSurface(graphics, profileRow, profileRow.contains(mouseX, mouseY), selectedProfile, 9.0f);
        drawButtonSurface(graphics, profileAddButton,
                profileAddButton.contains(mouseX, mouseY), false, 8.0f);
        drawButtonSurface(graphics, profileDeleteButton,
                profileDeleteButton.contains(mouseX, mouseY), false, 8.0f);
        for (int index = 0; index < navRows.length; index++) {
            drawNavSurface(graphics, navRows[index], navRows[index].contains(mouseX, mouseY),
                    index == selectedTab, 8.0f);
        }
        drawButtonSurface(graphics, createProfileButton, createProfileButton.contains(mouseX, mouseY), false, 8.0f);
        drawButtonSurface(graphics, hudEditorButton, hudEditorButton.contains(mouseX, mouseY), true, 9.0f);
        CarbonShapes.drawGlow(graphics, hudEditorButton.x, hudEditorButton.y,
                hudEditorButton.width, hudEditorButton.height, 9.0f, 7.0f, CarbonTheme.ACCENT_GLOW);
    }

    private void drawNavSurface(GuiGraphicsExtractor graphics, Bounds bounds,
                                boolean hovered, boolean active, float radius) {
        if (!hovered && !active) {
            return;
        }
        int top = active ? 0x241FC76F : CarbonTheme.BUTTON_HOVER_TOP;
        int bottom = active ? 0x101FC76F : CarbonTheme.BUTTON_HOVER_BOTTOM;
        int border = active ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER;
        CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y, bounds.width, bounds.height,
                radius, 1.0f, border, border, top, bottom);
    }

    private void drawHeaderSurfaces(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        for (int index = 0; index < tabs.length; index++) {
            if (index == selectedTab || tabs[index].contains(mouseX, mouseY)) {
                drawNavSurface(graphics, tabs[index], tabs[index].contains(mouseX, mouseY),
                        index == selectedTab, 8.0f);
            }
        }
        drawButtonSurface(graphics, closeButton, closeButton.contains(mouseX, mouseY), false, 8.0f);
    }

    private void drawFilterSurfaces(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        for (int index = 0; index < chips.length; index++) {
            boolean active = index == selectedFilter;
            boolean hover = chips[index].contains(mouseX, mouseY);
            drawNavSurface(graphics, chips[index], hover, active, 7.0f);
        }
        drawNavSurface(graphics, viewGridButton, viewGridButton.contains(mouseX, mouseY), selectedView == 0, 7.0f);
        drawNavSurface(graphics, viewListButton, viewListButton.contains(mouseX, mouseY), selectedView == 1, 7.0f);
        drawButtonSurface(graphics, sortButton, sortButton.contains(mouseX, mouseY), false, 7.0f);
        drawInputSurface(graphics, searchField, searchField.contains(mouseX, mouseY));
    }

    private void drawCardSurfaces(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        for (int index = 0; index < CARD_COUNT; index++) {
            Bounds card = cards[index];
            boolean hover = card.contains(mouseX, mouseY);
            boolean enabled = demoEnabled[index];
            float lift = hover ? 2.0f : 0.0f;
            float top = card.y - lift;
            int border = enabled ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.CARD_BORDER;
            int topFill = hover ? CarbonTheme.CARD_HOVER_TOP : CarbonTheme.CARD_TOP;
            int bottomFill = hover ? CarbonTheme.CARD_HOVER_BOTTOM : CarbonTheme.CARD_BOTTOM;
            CarbonShapes.drawShadow(graphics, card.x, top, card.width, card.height,
                    12.0f, 18.0f, 5.0f, CarbonTheme.WINDOW_SHADOW);
            CarbonShapes.drawBorderedSurface(graphics, card.x, top, card.width, card.height,
                    12.0f, 1.0f, border, border, topFill, bottomFill);
            CarbonShapes.drawTopHighlight(graphics, card.x + 1.0f, top + 1.0f,
                    card.width - 2.0f, 12.0f, CarbonTheme.WINDOW_HIGHLIGHT);
            if (enabled) {
                CarbonShapes.drawGradientRect(graphics, card.x + 1.0f, top + 1.0f,
                        card.width - 2.0f, card.height - 2.0f, 11.0f,
                        CarbonTheme.CARD_ACTIVE_WASH_TOP, CarbonTheme.CARD_ACTIVE_WASH_BOTTOM);
                CarbonShapes.drawGlow(graphics, card.x, top, card.width, card.height,
                        12.0f, 5.0f, CarbonTheme.ACCENT_GLOW);
            }

            drawButtonSurface(graphics, cardOptions[index], cardOptions[index].contains(mouseX, mouseY),
                    false, 7.0f, -lift);
            drawButtonSurface(graphics, cardSettings[index], cardSettings[index].contains(mouseX, mouseY),
                    false, 7.0f, -lift);
            Bounds status = cardStatus[index];
            if (enabled) {
                CarbonShapes.drawGradientRect(graphics, status.x, status.y - lift, status.width, status.height,
                        11.0f, CarbonTheme.TRACK_ON, CarbonTheme.TRACK_ON_BOTTOM);
            } else {
                CarbonShapes.drawBorderedSurface(graphics, status.x, status.y - lift, status.width, status.height,
                        11.0f, 1.0f, CarbonTheme.BUTTON_BORDER, CarbonTheme.BUTTON_BORDER,
                        CarbonTheme.disabledBarTop(CarbonTheme.DisabledBarStyle.NEUTRAL),
                        CarbonTheme.disabledBarBottom(CarbonTheme.DisabledBarStyle.NEUTRAL));
            }
        }
    }

    private void drawSamplePanelSurfaces(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        CarbonShapes.drawShadow(graphics, samplePanel.x, samplePanel.y, samplePanel.width, samplePanel.height,
                12.0f, 14.0f, 4.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, samplePanel.x, samplePanel.y,
                samplePanel.width, samplePanel.height, 12.0f, 1.0f,
                CarbonTheme.CARD_BORDER, CarbonTheme.CARD_BORDER,
                CarbonTheme.CARD_TOP, CarbonTheme.CARD_BOTTOM);
        CarbonShapes.drawTopHighlight(graphics, samplePanel.x + 1.0f, samplePanel.y + 1.0f,
                samplePanel.width - 2.0f, 12.0f, CarbonTheme.WINDOW_HIGHLIGHT);
        drawButtonSurface(graphics, primarySampleButton,
                primarySampleButton.contains(mouseX, mouseY), primaryPressed, 8.0f);
        drawButtonSurface(graphics, secondarySampleButton,
                secondarySampleButton.contains(mouseX, mouseY), secondaryPressed, 8.0f);
        Bounds track = sampleSwitchTrack;
        if (demoSwitch) {
            CarbonShapes.drawGradientRect(graphics, track.x, track.y, track.width, track.height,
                    track.height * 0.5f, CarbonTheme.TRACK_ON, CarbonTheme.TRACK_ON_BOTTOM);
        } else {
            CarbonShapes.drawGradientRect(graphics, track.x, track.y, track.width, track.height,
                    track.height * 0.5f, CarbonTheme.TRACK_OFF, CarbonTheme.TRACK_OFF);
        }
        float knobX = demoSwitch ? track.x + track.width - track.height : track.x;
        CarbonShapes.drawShadow(graphics, knobX + 2.0f, track.y + 2.0f,
                track.height - 4.0f, track.height - 4.0f, 99.0f, 4.0f, 1.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawRounded(graphics, knobX + 2.0f, track.y + 2.0f,
                track.height - 4.0f, track.height - 4.0f, 99.0f, CarbonTheme.TEXT);
        CarbonShapes.drawBorderedSurface(graphics, redStateBar.x, redStateBar.y,
                redStateBar.width, redStateBar.height, 8.0f, 1.0f,
                CarbonTheme.RED_SOFT, CarbonTheme.RED_SOFT,
                CarbonTheme.disabledBarTop(CarbonTheme.DisabledBarStyle.RED),
                CarbonTheme.disabledBarBottom(CarbonTheme.DisabledBarStyle.RED));
    }

    private void drawWindowTextAndIcons(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        drawBrand(graphics);
        drawHeaderTabs(graphics);
        CarbonIcons.drawDesign(graphics, "x", closeButton.x + 8.0f, closeButton.y + 8.0f,
                14.0f, closeButton.contains(mouseX, mouseY) ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED);

        drawSidebarText(graphics, mouseX, mouseY);
        drawFilterText(graphics, mouseX, mouseY);
        drawCardsTextAndIcons(graphics, mouseX, mouseY);
        drawSamplePanelTextAndIcons(graphics);
    }

    private void drawBrand(GuiGraphicsExtractor graphics) {
        CarbonText.drawTracked(graphics, font, "CARBON", CarbonText.Weight.BOLD, 18.0f,
                window.x + 24.0f, window.y + 20.0f, CarbonTheme.TEXT, CarbonText.Tracking.WORDMARK);
        float brandWidth = CarbonText.trackedWidth(font, "CARBON", CarbonText.Weight.BOLD,
                18.0f, CarbonText.Tracking.WORDMARK);
        CarbonText.drawTracked(graphics, font, "CLIENT", CarbonText.Weight.MEDIUM, 9.0f,
                window.x + 24.0f + brandWidth + 10.0f, window.y + 27.0f,
                CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        CarbonText.drawTracked(graphics, font, "STYLE TEST", CarbonText.Weight.MEDIUM, 8.0f,
                window.x + 24.0f, window.y + 43.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
    }

    private void drawHeaderTabs(GuiGraphicsExtractor graphics) {
        for (int index = 0; index < tabs.length; index++) {
            Bounds tab = tabs[index];
            int color = index == selectedTab ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT_MUTED;
            CarbonText.centeredTracked(graphics, font, TABS[index],
                    index == selectedTab ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.MEDIUM,
                    11.0f, tab.x + tab.width * 0.5f, tab.y + 10.0f, color, CarbonText.Tracking.LABEL);
            if (index == selectedTab) {
                CarbonShapes.drawGradientRect(graphics, tab.x + 15.0f, tab.y + tab.height - 2.0f,
                        tab.width - 30.0f, 2.0f, 1.0f, CarbonTheme.ACCENT, CarbonTheme.ACCENT_DEEP);
            }
        }
    }

    private void drawSidebarText(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        CarbonText.drawTracked(graphics, font, "PROFILE", CarbonText.Weight.SEMIBOLD, 9.0f,
                sidebar.x + 14.0f, sidebar.y + 4.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        CarbonIcons.drawDesign(graphics, "user", profileRow.x + 10.0f, profileRow.y + 9.0f,
                16.0f, selectedProfile ? CarbonTheme.ACCENT : CarbonTheme.TEXT_MUTED);
        CarbonText.draw(graphics, font, "My Setup", CarbonText.Weight.SEMIBOLD, 12.0f,
                profileRow.x + 34.0f, profileRow.y + 9.0f, CarbonTheme.TEXT, false);
        CarbonIcons.drawDesign(graphics, "trash", profileRow.x + profileRow.width - 48.0f,
                profileRow.y + 9.0f, 16.0f, profileRow.contains(mouseX, mouseY)
                        ? CarbonTheme.TEXT_DIM : CarbonTheme.TEXT_DISABLED);
        CarbonIcons.drawDesign(graphics, "pencil", profileRow.x + profileRow.width - 26.0f,
                profileRow.y + 9.0f, 16.0f, profileRow.contains(mouseX, mouseY)
                        ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM);

        CarbonText.drawTracked(graphics, font, "LIBRARY", CarbonText.Weight.SEMIBOLD, 9.0f,
                sidebar.x + 14.0f, sidebar.y + 139.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        drawSidebarNav(graphics, "layout-grid", "All modules", 0);
        drawSidebarNav(graphics, "map-pin", "Waypoints", 1);
        drawSidebarNav(graphics, "settings", "Settings", 2);

        CarbonText.drawTracked(graphics, font, "SAVED PROFILES", CarbonText.Weight.SEMIBOLD, 9.0f,
                sidebar.x + 14.0f, sidebar.y + 314.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        CarbonIcons.drawDesign(graphics, "star", sidebar.x + 17.0f, sidebar.y + 342.0f,
                14.0f, CarbonTheme.ACCENT);
        CarbonText.draw(graphics, font, "Essentials", CarbonText.Weight.MEDIUM, 11.0f,
                sidebar.x + 39.0f, sidebar.y + 343.0f,
                selectedProfile ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED, false);
        CarbonText.drawTracked(graphics, font, "PROFILE ACTIONS", CarbonText.Weight.SEMIBOLD, 8.0f,
                sidebar.x + 14.0f, sidebar.y + 390.0f, CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
        CarbonIcons.drawDesign(graphics, "plus", profileAddButton.x + 10.0f,
                profileAddButton.y + 10.0f, 16.0f,
                profileAddButton.contains(mouseX, mouseY) ? CarbonTheme.ACCENT : CarbonTheme.TEXT_MUTED);
        CarbonIcons.drawDesign(graphics, "trash", profileDeleteButton.x + 10.0f,
                profileDeleteButton.y + 10.0f, 16.0f,
                profileDeleteButton.contains(mouseX, mouseY) ? CarbonTheme.RED : CarbonTheme.TEXT_MUTED);
        CarbonIcons.drawDesign(graphics, "plus", createProfileButton.x + 10.0f,
                createProfileButton.y + 7.0f, 14.0f,
                createProfileButton.contains(mouseX, mouseY) ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED);
        CarbonText.centeredTracked(graphics, font, "SAVE AS NEW PROFILE", CarbonText.Weight.SEMIBOLD,
                9.0f, createProfileButton.x + createProfileButton.width * 0.5f + 7.0f,
                createProfileButton.y + 9.0f, CarbonTheme.TEXT_MUTED, CarbonText.Tracking.LABEL);
        CarbonText.centeredTracked(graphics, font, "OPEN HUD EDITOR", CarbonText.Weight.SEMIBOLD,
                10.0f, hudEditorButton.x + hudEditorButton.width * 0.5f,
                hudEditorButton.y + 11.0f, CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
    }

    private void drawSidebarNav(GuiGraphicsExtractor graphics, String icon, String label, int index) {
        Bounds row = navRows[index];
        boolean selected = index == selectedTab;
        CarbonIcons.drawDesign(graphics, icon, row.x + 12.0f, row.y + 8.0f, 16.0f,
                selected ? CarbonTheme.ACCENT : CarbonTheme.TEXT_DIM);
        CarbonText.draw(graphics, font, label, selected ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.MEDIUM,
                11.0f, row.x + 38.0f, row.y + 9.0f,
                selected ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED, false);
    }

    private void drawFilterText(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        for (int index = 0; index < chips.length; index++) {
            Bounds chip = chips[index];
            CarbonText.centeredTracked(graphics, font, FILTERS[index],
                    index == selectedFilter ? CarbonText.Weight.SEMIBOLD : CarbonText.Weight.MEDIUM,
                    9.0f, chip.x + chip.width * 0.5f, chip.y + 9.0f,
                    index == selectedFilter ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT_MUTED,
                    CarbonText.Tracking.LABEL);
        }
        CarbonIcons.drawDesign(graphics, "layout-grid", viewGridButton.x + 7.0f,
                viewGridButton.y + 7.0f, 16.0f,
                selectedView == 0 ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM);
        CarbonIcons.drawDesign(graphics, "list", viewListButton.x + 7.0f,
                viewListButton.y + 7.0f, 16.0f,
                selectedView == 1 ? CarbonTheme.TEXT : CarbonTheme.TEXT_DIM);
        CarbonIcons.drawDesign(graphics, "arrow-up-down", sortButton.x + 7.0f,
                sortButton.y + 7.0f, 16.0f,
                sortButton.contains(mouseX, mouseY) || sortDescending ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED);
        CarbonIcons.drawDesign(graphics, "search", searchField.x + 10.0f,
                searchField.y + 7.0f, 16.0f, CarbonTheme.TEXT_DIM);
        CarbonText.draw(graphics, font, "Search modules", CarbonText.Weight.REGULAR, 11.0f,
                searchField.x + 34.0f, searchField.y + 9.0f, CarbonTheme.TEXT_DIM, false);
    }

    private void drawCardsTextAndIcons(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        for (int index = 0; index < CARD_COUNT; index++) {
            Bounds card = cards[index];
            float lift = card.contains(mouseX, mouseY) ? 2.0f : 0.0f;
            CarbonIcons.drawDesign(graphics, CARD_ICONS[index], card.x + 95.0f,
                    card.y + 38.0f - lift, 48.0f, CarbonTheme.TEXT);
            if (index == 0) {
                CarbonShapes.drawBorderedSurface(graphics, card.x + 10.0f, card.y + 10.0f - lift,
                        38.0f, 16.0f, 6.0f, 1.0f, CarbonTheme.CARD_BORDER, CarbonTheme.CARD_BORDER,
                        CarbonTheme.BUTTON_TOP, CarbonTheme.BUTTON_BOTTOM);
                CarbonText.centeredTracked(graphics, font, "NEW", CarbonText.Weight.SEMIBOLD, 7.0f,
                        card.x + 29.0f, card.y + 14.0f - lift, CarbonTheme.ACCENT_HIGHLIGHT,
                        CarbonText.Tracking.LABEL);
            }
            if (card.contains(mouseX, mouseY)) {
                CarbonIcons.drawDesign(graphics, "star", card.x + card.width - 27.0f,
                        card.y + 10.0f - lift, 16.0f,
                        index == 0 ? CarbonTheme.ACCENT : CarbonTheme.TEXT_DIM);
            }
            CarbonText.centered(graphics, font, CARD_NAMES[index], CarbonText.Weight.REGULAR, 19.0f,
                    card.x + card.width * 0.5f, card.y + 101.0f - lift, CarbonTheme.TEXT, false);
            CarbonText.centered(graphics, font, CARD_DESCRIPTIONS[index], CarbonText.Weight.REGULAR, 11.0f,
                    card.x + card.width * 0.5f, card.y + 127.0f - lift, CarbonTheme.TEXT_MUTED, false);
            CarbonText.drawTracked(graphics, font, "OPTIONS", CarbonText.Weight.SEMIBOLD, 9.0f,
                    cardOptions[index].x + 16.0f, cardOptions[index].y + 12.0f - lift,
                    cardOptions[index].contains(mouseX, mouseY) ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED,
                    CarbonText.Tracking.LABEL);
            CarbonIcons.drawDesign(graphics, "sliders-horizontal", cardSettings[index].x + 10.0f,
                    cardSettings[index].y + 10.0f - lift, 16.0f,
                    cardSettings[index].contains(mouseX, mouseY) ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED);
            CarbonText.centeredTracked(graphics, font, CARD_CATEGORIES[index], CarbonText.Weight.MEDIUM,
                    7.0f, card.x + card.width * 0.5f, card.y + 142.0f - lift,
                    CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
            CarbonText.centeredTracked(graphics, font, demoEnabled[index] ? "ON" : "OFF",
                    CarbonText.Weight.BOLD, 10.0f, cardStatus[index].x + cardStatus[index].width * 0.5f,
                    cardStatus[index].y + 13.0f - lift,
                    demoEnabled[index] ? CarbonTheme.WINDOW_BOTTOM : CarbonTheme.TEXT_MUTED,
                    CarbonText.Tracking.LABEL);
        }
    }

    private void drawSamplePanelTextAndIcons(GuiGraphicsExtractor graphics) {
        CarbonText.drawTracked(graphics, font, "INTERACTION SAMPLES", CarbonText.Weight.SEMIBOLD,
                10.0f, samplePanel.x + 18.0f, samplePanel.y + 19.0f,
                CarbonTheme.TEXT, CarbonText.Tracking.LABEL);
        CarbonText.draw(graphics, font, "Matte buttons, one toggle, and a delayed tooltip.",
                CarbonText.Weight.REGULAR, 11.0f, samplePanel.x + 18.0f, samplePanel.y + 43.0f,
                CarbonTheme.TEXT_MUTED, false);
        CarbonIcons.drawDesign(graphics, "plus", primarySampleButton.x + 12.0f,
                primarySampleButton.y + 9.0f, 16.0f, CarbonTheme.WINDOW_BOTTOM);
        CarbonText.centeredTracked(graphics, font, "PRIMARY", CarbonText.Weight.SEMIBOLD, 9.0f,
                primarySampleButton.x + primarySampleButton.width * 0.5f + 8.0f,
                primarySampleButton.y + 11.0f,
                primaryPressed ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT,
                CarbonText.Tracking.LABEL);
        CarbonIcons.drawDesign(graphics, "copy", secondarySampleButton.x + 12.0f,
                secondarySampleButton.y + 9.0f, 16.0f,
                secondaryPressed ? CarbonTheme.TEXT : CarbonTheme.TEXT_MUTED);
        CarbonText.centeredTracked(graphics, font, "SECONDARY", CarbonText.Weight.SEMIBOLD, 9.0f,
                secondarySampleButton.x + secondarySampleButton.width * 0.5f + 8.0f,
                secondarySampleButton.y + 11.0f,
                secondaryPressed ? CarbonTheme.ACCENT_HIGHLIGHT : CarbonTheme.TEXT_MUTED,
                CarbonText.Tracking.LABEL);
        CarbonText.draw(graphics, font, "DEMO TOGGLE", CarbonText.Weight.MEDIUM, 10.0f,
                sampleSwitch.x + 62.0f, sampleSwitch.y + 7.0f, CarbonTheme.TEXT_MUTED, false);
        CarbonIcons.drawDesign(graphics, "trash", redStateBar.x + 9.0f,
                redStateBar.y + 5.0f, 16.0f, CarbonTheme.RED);
        CarbonText.centeredTracked(graphics, font, "RED STATE", CarbonText.Weight.SEMIBOLD, 8.0f,
                redStateBar.x + redStateBar.width * 0.5f + 7.0f,
                redStateBar.y + 8.0f, CarbonTheme.RED, CarbonText.Tracking.LABEL);
        CarbonText.drawTracked(graphics, font, "HOVER 400 MS FOR TOOLTIP", CarbonText.Weight.MEDIUM,
                8.0f, samplePanel.x + 18.0f, samplePanel.y + 185.0f,
                CarbonTheme.TEXT_DIM, CarbonText.Tracking.LABEL);
    }

    private void drawButtonSurface(GuiGraphicsExtractor graphics, Bounds bounds,
                                   boolean hovered, boolean active, float radius) {
        drawButtonSurface(graphics, bounds, hovered, active, radius, 0.0f);
    }

    private void drawButtonSurface(GuiGraphicsExtractor graphics, Bounds bounds,
                                   boolean hovered, boolean active, float radius, float offsetY) {
        int top = active ? CarbonTheme.ACCENT : hovered ? CarbonTheme.BUTTON_HOVER_TOP : CarbonTheme.BUTTON_TOP;
        int bottom = active ? CarbonTheme.ACCENT_DEEP : hovered ? CarbonTheme.BUTTON_HOVER_BOTTOM : CarbonTheme.BUTTON_BOTTOM;
        int border = active ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.BUTTON_BORDER;
        CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y + offsetY, bounds.width, bounds.height,
                radius, 1.0f, border, border, top, bottom);
        if (!active) {
            CarbonShapes.drawTopHighlight(graphics, bounds.x + 1.0f, bounds.y + offsetY + 1.0f,
                    bounds.width - 2.0f, radius, CarbonTheme.WINDOW_HIGHLIGHT);
        }
    }

    private void drawInputSurface(GuiGraphicsExtractor graphics, Bounds bounds, boolean hovered) {
        int border = hovered ? CarbonTheme.CARD_ACTIVE_BORDER : CarbonTheme.INPUT_BORDER;
        CarbonShapes.drawBorderedSurface(graphics, bounds.x, bounds.y, bounds.width, bounds.height,
                8.0f, 1.0f, border, border, CarbonTheme.INPUT_TOP, CarbonTheme.INPUT_BOTTOM);
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, float mouseX, float mouseY) {
        if (hoverKind == 0 || System.nanoTime() - hoverStartedAt < 400_000_000L) {
            return;
        }
        String message = switch (hoverKind) {
            case 1 -> CARD_TOOLTIP;
            case 2 -> BUTTON_TOOLTIP;
            case 3 -> SWITCH_TOOLTIP;
            default -> null;
        };
        if (message == null) {
            return;
        }
        float tooltipWidth = Math.max(164.0f,
                CarbonText.width(font, message, CarbonText.Weight.REGULAR, 10.0f) + 22.0f);
        float tooltipX = clamp(mouseX + 14.0f, 8.0f, UiScale.designWidth() - tooltipWidth - 8.0f);
        float tooltipY = clamp(mouseY + 16.0f, 8.0f, UiScale.designHeight() - 36.0f);
        CarbonShapes.drawShadow(graphics, tooltipX, tooltipY, tooltipWidth, 28.0f,
                7.0f, 10.0f, 4.0f, CarbonTheme.WINDOW_SHADOW);
        CarbonShapes.drawBorderedSurface(graphics, tooltipX, tooltipY, tooltipWidth, 28.0f,
                7.0f, 1.0f, CarbonTheme.WINDOW_BORDER, CarbonTheme.WINDOW_BORDER,
                CarbonTheme.BUTTON_TOP, CarbonTheme.BUTTON_BOTTOM);
        CarbonText.centered(graphics, font, message, CarbonText.Weight.REGULAR, 10.0f,
                tooltipX + tooltipWidth * 0.5f, tooltipY + 9.0f, CarbonTheme.TEXT, false);
    }

    private void drawLayoutDebug(GuiGraphicsExtractor graphics) {
        CarbonShapes.drawOutline(graphics, window.x, window.y, window.width, window.height,
                1.5f, CarbonTheme.ACCENT);
        CarbonShapes.drawOutline(graphics, header.x, header.y, header.width, header.height,
                1.0f, CarbonTheme.RED);
        CarbonShapes.drawOutline(graphics, sidebar.x, sidebar.y, sidebar.width, sidebar.height,
                1.0f, CarbonTheme.ACCENT_HIGHLIGHT);
        CarbonShapes.drawOutline(graphics, content.x, content.y, content.width, content.height,
                1.0f, CarbonTheme.TEXT_MUTED);
        CarbonShapes.drawOutline(graphics, searchField.x, searchField.y,
                searchField.width, searchField.height, 1.0f, CarbonTheme.RED);
        for (Bounds card : cards) {
            CarbonShapes.drawOutline(graphics, card.x, card.y, card.width, card.height,
                    1.0f, CarbonTheme.ACCENT);
        }
        CarbonText.drawTracked(graphics, font, "F6  LAYOUT DEBUG", CarbonText.Weight.BOLD,
                10.0f, window.x + 16.0f, window.y + window.height - 21.0f,
                CarbonTheme.ACCENT_HIGHLIGHT, CarbonText.Tracking.LABEL);
        CarbonText.draw(graphics, font, "WIN 1000 x 612  |  HEADER 1000 x 64  |  SIDEBAR 214 x 548",
                CarbonText.Weight.MEDIUM, 9.0f, window.x + 175.0f, window.y + window.height - 20.0f,
                CarbonTheme.TEXT, false);
        CarbonText.draw(graphics, font, debugCardMeasurement,
                CarbonText.Weight.MEDIUM, 9.0f, window.x + 600.0f, window.y + window.height - 20.0f,
                CarbonTheme.TEXT, false);
    }

    private void drawRenderFailure(GuiGraphicsExtractor graphics) {
        String reason = CarbonShapes.failureReason();
        String message = "Carbon render failed: " + reason;
        CarbonText.centered(graphics, font, message, CarbonText.Weight.SEMIBOLD, 14.0f,
                UiScale.designWidth() * 0.5f, UiScale.designHeight() * 0.5f,
                CarbonTheme.RED, false);
    }

    private void updateHover(float mouseX, float mouseY) {
        int nextKind = 0;
        int nextCard = -1;
        for (int index = 0; index < cardStatus.length; index++) {
            if (cardStatus[index].contains(mouseX, mouseY)) {
                nextKind = 1;
                nextCard = index;
                break;
            }
        }
        if (nextKind == 0 && (primarySampleButton.contains(mouseX, mouseY)
                || secondarySampleButton.contains(mouseX, mouseY))) {
            nextKind = 2;
        }
        if (nextKind == 0 && sampleSwitch.contains(mouseX, mouseY)) {
            nextKind = 3;
        }
        if (nextKind != hoverKind || nextCard != hoverCard) {
            hoverKind = nextKind;
            hoverCard = nextCard;
            hoverStartedAt = System.nanoTime();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        UiScale.update(Minecraft.getInstance());
        float mouseX = UiScale.toDesign(event.x());
        float mouseY = UiScale.toDesign(event.y());
        if (closeButton.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        for (int index = 0; index < tabs.length; index++) {
            if (tabs[index].contains(mouseX, mouseY)) {
                selectedTab = index;
                return true;
            }
        }
        for (int index = 0; index < navRows.length; index++) {
            if (navRows[index].contains(mouseX, mouseY)) {
                selectedTab = index;
                return true;
            }
        }
        for (int index = 0; index < chips.length; index++) {
            if (chips[index].contains(mouseX, mouseY)) {
                selectedFilter = index;
                return true;
            }
        }
        if (viewGridButton.contains(mouseX, mouseY)) {
            selectedView = 0;
            return true;
        }
        if (viewListButton.contains(mouseX, mouseY)) {
            selectedView = 1;
            return true;
        }
        if (sortButton.contains(mouseX, mouseY)) {
            sortDescending = !sortDescending;
            return true;
        }
        if (profileRow.contains(mouseX, mouseY)) {
            selectedProfile = !selectedProfile;
            return true;
        }
        if (profileAddButton.contains(mouseX, mouseY)) {
            selectedProfile = true;
            return true;
        }
        if (profileDeleteButton.contains(mouseX, mouseY)) {
            selectedProfile = false;
            return true;
        }
        if (sampleSwitch.contains(mouseX, mouseY)) {
            demoSwitch = !demoSwitch;
            return true;
        }
        if (primarySampleButton.contains(mouseX, mouseY)) {
            primaryPressed = !primaryPressed;
            return true;
        }
        if (secondarySampleButton.contains(mouseX, mouseY)) {
            secondaryPressed = !secondaryPressed;
            return true;
        }
        for (int index = 0; index < cardStatus.length; index++) {
            if (cardStatus[index].contains(mouseX, mouseY)) {
                demoEnabled[index] = !demoEnabled[index];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_F6) {
            layoutDebug = !layoutDebug;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Bounds {
        private float x;
        private float y;
        private float width;
        private float height;

        private void set(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }
}
