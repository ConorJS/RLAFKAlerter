package com.aeimo.afkmovealert;

import com.google.common.annotations.VisibleForTesting;
import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import javax.swing.JFrame;

/**
 * This isn't used currently, but will be useful if {@link java.applet.Applet} stops working.
 * This reports width/height of client correctly, and always reports position as 0,0.
 *
 * At last check, we really wanted to render starting at -5,-20 if we want a rectangle to fit
 * the whole window.
 */
public class WindowUtil {
    private final InternalReferenceFrame referenceFrame = new InternalReferenceFrame();

    public Rectangle getWindowSize() {
        return referenceFrame.getWindowAreaBounds();
    }

    private static class InternalReferenceFrame extends JFrame {
        private static final boolean jdk8231564;

        static {
            try {
                String javaVersion = System.getProperty("java.version");
                jdk8231564 = jdk8231564(javaVersion);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @VisibleForTesting
        static boolean jdk8231564(String javaVersion) {
            int idx = javaVersion.indexOf('_');
            if (idx != -1) {
                javaVersion = javaVersion.substring(0, idx);
            }
            String[] s = javaVersion.split("\\.");
            int major, minor, patch;
            if (s.length == 3) {
                major = Integer.parseInt(s[0]);
                minor = Integer.parseInt(s[1]);
                patch = Integer.parseInt(s[2]);
            } else {
                major = Integer.parseInt(s[0]);
                minor = -1;
                patch = -1;
            }
            if (major == 12 || major == 13 || major == 14) {
                // These versions are since EOL & do not include JDK-8231564, except for 13.0.4+
                return false;
            }
            return major > 11 || (major == 11 && minor > 0) || (major == 11 && minor == 0 && patch >= 8);
        }

        /**
         * Finds the {@link GraphicsConfiguration} of the display the window is currently on. If it's on more than
         * one screen, returns the one it's most on (largest area of intersection)
         */
        private GraphicsConfiguration getCurrentDisplayConfiguration() {
            return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
                    .map(GraphicsDevice::getDefaultConfiguration)
                    .max(Comparator.comparingInt(config ->
                    {
                        Rectangle intersection = config.getBounds().intersection(getBounds());
                        return intersection.width * intersection.height;
                    }))
                    .orElseGet(this::getGraphicsConfiguration);
        }

        /**
         * Calculates the bounds of the window area of the screen.
         * <p>
         * The bounds returned by {@link GraphicsEnvironment#getMaximumWindowBounds} are incorrectly calculated on
         * high-DPI screens.
         */
        private Rectangle getWindowAreaBounds() {
            GraphicsConfiguration config = getCurrentDisplayConfiguration();
            // get screen bounds
            Rectangle bounds = config.getBounds();

            // transform bounds to dpi-independent coordinates
            if (!jdk8231564) {
                // JDK-8231564 fixed setMaximizedBounds to scale the bounds, so this must only be done on <11.0.8
                bounds = config.getDefaultTransform().createTransformedShape(bounds).getBounds();
            }

            // subtract insets (taskbar, etc.)
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
            if (!jdk8231564) {
                // Prior to JDK-8231564, WFramePeer expects the bounds to be relative to the current monitor instead of the
                // primary display.
                bounds.x = bounds.y = 0;
            } else {
                // The insets from getScreenInsets are not scaled, we must convert them to DPI scaled pixels on 11.0.8 due
                // to JDK-8231564 which expects the bounds to be in DPI-aware pixels.
                double scaleX = config.getDefaultTransform().getScaleX();
                double scaleY = config.getDefaultTransform().getScaleY();
                insets.top /= scaleY;
                insets.bottom /= scaleY;
                insets.left /= scaleX;
                insets.right /= scaleX;
            }
            bounds.x += insets.left;
            bounds.y += insets.top;
            bounds.height -= (insets.bottom + insets.top);
            bounds.width -= (insets.right + insets.left);

            return bounds;
        }
    }
}
