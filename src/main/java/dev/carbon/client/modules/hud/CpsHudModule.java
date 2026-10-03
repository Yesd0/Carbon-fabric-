package dev.carbon.client.modules.hud;

import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.HudRenderEvent;
import dev.carbon.client.core.event.MouseClickEvent;
import dev.carbon.client.core.setting.BoolSetting;
import dev.carbon.client.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

public final class CpsHudModule extends HudModule {
    private static final int WINDOW_SIZE = 64;
    private static final long CPS_WINDOW_NANOS = 1_000_000_000L;

    private final long[] leftClickTimes = new long[WINDOW_SIZE];
    private final long[] rightClickTimes = new long[WINDOW_SIZE];
    private final BoolSetting showRight = new BoolSetting(
            "show_right", "Show right clicks", "Include right-button CPS", true);

    private int leftHead;
    private int leftCount;
    private int rightHead;
    private int rightCount;
    private int cachedLeft = -1;
    private int cachedRight = -1;
    private boolean cachedShowRight;
    private String cachedText = "L 0  R 0 CPS";
    private int cachedWidth;

    public CpsHudModule(EventBus eventBus) {
        super(eventBus, "cps", "CPS", "Shows left and right clicks per second", true, 0.02, 0.88);
        addSetting(showRight);
    }

    @Override
    protected void onHudEnable() {
        listen(MouseClickEvent.class, this::onMouseClick);
        refreshText();
    }

    @Override
    protected void onHudDisable() {
        leftHead = leftCount = rightHead = rightCount = 0;
        cachedLeft = cachedRight = -1;
    }

    @Override
    protected void onHudTick() {
        long now = System.nanoTime();
        leftCount = prune(leftClickTimes, leftHead, leftCount, now, true);
        rightCount = prune(rightClickTimes, rightHead, rightCount, now, false);
        refreshText();
    }

    @Override
    protected int width() {
        return cachedWidth;
    }

    @Override
    protected int height() {
        return Minecraft.getInstance().font.lineHeight + 4;
    }

    @Override
    protected void render(HudRenderEvent event, int x, int y) {
        event.graphics().fill(x - 3, y - 2, x + cachedWidth + 3, y + height(), 0x8C101418);
        event.graphics().text(Minecraft.getInstance().font, cachedText, x, y, textColor().get(), true);
    }

    private void onMouseClick(MouseClickEvent event) {
        if (event.button() == MouseClickEvent.Button.LEFT) {
            if (leftCount < WINDOW_SIZE) {
                leftClickTimes[(leftHead + leftCount) % WINDOW_SIZE] = event.timestampNanos();
                leftCount++;
            } else {
                leftClickTimes[leftHead] = event.timestampNanos();
                leftHead = (leftHead + 1) % WINDOW_SIZE;
            }
        } else if (rightCount < WINDOW_SIZE) {
            rightClickTimes[(rightHead + rightCount) % WINDOW_SIZE] = event.timestampNanos();
            rightCount++;
        } else {
            rightClickTimes[rightHead] = event.timestampNanos();
            rightHead = (rightHead + 1) % WINDOW_SIZE;
        }
    }

    private void refreshText() {
        boolean rightVisible = showRight.enabled();
        if (cachedLeft == leftCount && cachedRight == rightCount && cachedShowRight == rightVisible) {
            return;
        }
        cachedLeft = leftCount;
        cachedRight = rightCount;
        cachedShowRight = rightVisible;
        cachedText = rightVisible
                ? "L " + leftCount + "  R " + rightCount + " CPS"
                : "L " + leftCount + " CPS";
        Font font = Minecraft.getInstance().font;
        cachedWidth = font.width(cachedText);
    }

    private int prune(long[] times, int head, int count, long now, boolean leftButton) {
        while (count > 0 && now - times[head] > CPS_WINDOW_NANOS) {
            head = (head + 1) % WINDOW_SIZE;
            count--;
        }
        if (leftButton) {
            leftHead = head;
        } else {
            rightHead = head;
        }
        return count;
    }
}
