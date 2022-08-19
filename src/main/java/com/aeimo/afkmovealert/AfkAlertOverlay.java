package com.aeimo.afkmovealert;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;

public class AfkAlertOverlay extends Overlay {
    private static final int MAX_BRIGHTNESS_ALPHA_LEVEL = 255;

    @Inject
    private AfkAlertPlugin plugin;

    private boolean isRenderingAlertAnimation = false;

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.playerIsAfk()) {
            Color glowColor = plugin.getGlowColor();
            Rectangle clientRectangle = plugin.getAppletClientRect();
            Rectangle fullScreenOverlay = new Rectangle(
                    // Weird offset (5, 20) for frame position reported by Applet
                    // NOTE: Maybe just set these to -5 and -20 (rect x,y is always 0,0?)
                    clientRectangle.x - 5,
                    clientRectangle.y - 20,
                    (int) clientRectangle.getWidth(), (int) clientRectangle.getHeight());

            graphics.setColor(new Color(
                    glowColor.getRed(),
                    glowColor.getGreen(),
                    glowColor.getBlue(),
                    getBreathingAlpha(plugin.getGlowBreathePeriod()))
            );
            graphics.fillRect(fullScreenOverlay.x, fullScreenOverlay.y, fullScreenOverlay.width, fullScreenOverlay.height);
        } else {
            isRenderingAlertAnimation = false;
        }

        return null;
    }

    private int getBreathingAlpha(int breathePeriodMillis) {
        double currentMillisOffset = System.currentTimeMillis() % breathePeriodMillis;
        double fractionCycleComplete = currentMillisOffset / breathePeriodMillis;

        int maxIntensityPc = plugin.getMaxBreatheIntensityPercent();
        double fractionAlpha = Math.sin(fractionCycleComplete * 2 * Math.PI);
        double fractionAlphaPositive = (fractionAlpha + 1) / 2;

        // This check forces the animation to start near the dimmest point of the wave (gives a fade-in effect)
        if (isRenderingAlertAnimation || fractionAlphaPositive < 0.025) {
            isRenderingAlertAnimation = true;
            return ((int) (fractionAlphaPositive * MAX_BRIGHTNESS_ALPHA_LEVEL * (maxIntensityPc / 100.0)));
        }
        return 0;
    }
}
