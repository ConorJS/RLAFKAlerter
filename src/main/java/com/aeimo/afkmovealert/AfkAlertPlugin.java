package com.aeimo.afkmovealert;

import com.google.inject.Provides;
import java.applet.Applet;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(name = "AFK Alerter (Movement only)", description = "Gives a visual indicator when your character stops moving", tags = {"afk", "runecrafting", "rc", "agil", "agility", "alert", "alerter"}, enabledByDefault = false)
public class AfkAlertPlugin extends Plugin {
    //== attributes ===================================================================================================================

    @Inject
    private AfkAlertConfig config;

    @Inject
    private AfkAlertOverlay overlay;

    @Inject
    private Client client;

    @Inject
    private Applet appletClient;

    @Inject
    private OverlayManager overlayManager;

    private CachedSlidingWindow<LocalPoint> squaresLastStoodOn;

    //== setup =======================================================================================================================

    @Provides
    AfkAlertConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(AfkAlertConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        resetLocationHistory();
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
    }

    //== methods =====================================================================================================================

    public boolean playerIsAfk() {
        if (isBankInterfaceOpen()) {
            return squaresLastStoodOn.allEqual(afkDurationBankTicks());
        } else {
            return squaresLastStoodOn.allEqual(afkDurationTicks());
        }
    }

    public int getGlowBreathePeriod() {
        return config.glowSpeedMs();
    }

    public int getMaxBreatheIntensityPercent() {
        return config.maxBreatheIntensityPercent();
    }

    public Color getGlowColor() {
        return config.glowColor();
    }

    public int afkDurationTicks() {
        return secondsToTicksRoundNearest(config.afkDurationThreshold());
    }

    public int afkDurationBankTicks() {
        return secondsToTicksRoundNearest(config.afkDurationThresholdBank());
    }

    public Rectangle getAppletClientRect() {
        return appletClient.getBounds();
    }

    //== subscriptions ===============================================================================================================

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        resetLocationHistory();
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        squaresLastStoodOn.push(client.getLocalPlayer().getLocalLocation());
    }

    //== helpers =====================================================================================================================

    private void resetLocationHistory() {
        squaresLastStoodOn = new CachedSlidingWindow<>(Math.max(afkDurationTicks(), afkDurationBankTicks()));
    }

    private boolean isBankInterfaceOpen() {
        Widget widgetBankTitleBar = this.client.getWidget(WidgetInfo.BANK_TITLE_BAR);
        Widget coxPublicChest = this.client.getWidget(550, 1);
        Widget coxPrivateChest = this.client.getWidget(271, 1);
        return !(
                (widgetBankTitleBar == null || widgetBankTitleBar.isHidden()) &&
                        (coxPublicChest == null || coxPublicChest.isHidden()) &&
                        (coxPrivateChest == null || coxPrivateChest.isHidden())
        );
    }

    private static int secondsToTicksRoundNearest(int ticks) {
        return (int) Math.round(ticks / 0.6);
    }

    private static class CachedSlidingWindow<T> {
        private final Object[] slidingWindowItems;

        private Map<Integer, Boolean> allEqualCachedResult = null;

        public CachedSlidingWindow(int size) {
            slidingWindowItems = new Object[size];
        }

        /**
         * Appends obj to the internal array.
         * obj takes the last (length-1) index position, while everything
         * else gets pushed towards index 0, and the previous occupier of
         * index 0 is evicted.
         *
         * @param obj The object being appended to the sliding window.
         */
        void push(T obj) {
            allEqualCachedResult = null;

            if (slidingWindowItems.length - 1 >= 0) {
                System.arraycopy(slidingWindowItems, 1, slidingWindowItems, 0, slidingWindowItems.length - 1);
            }
            slidingWindowItems[slidingWindowItems.length - 1] = obj;
        }

        boolean allEqual(int n) {
            if (allEqualCachedResult == null) {
                allEqualCachedResult = new HashMap<>();
            }
            return allEqualCachedResult.computeIfAbsent(n, this::lastNEqual);
        }

        private Boolean lastNEqual(int n) {
            if (n > slidingWindowItems.length) {
                throw new RuntimeException(String.format(
                        "lastNEqual called with n=%d, slidingWindowItems.length=%d", n, slidingWindowItems.length));
            }

            // End on [endIndex+1], as we check backwards [i-1] (so last comparison is [endIndex+1] vs [endIndex]).
            int endIndex = slidingWindowItems.length - n;
            for (int i = slidingWindowItems.length - 1; i >= endIndex + 1; i--) {
                if (!Objects.equals(slidingWindowItems[i], slidingWindowItems[i - 1])) {
                    return false;
                }
            }
            return true;
        }
    }
}
