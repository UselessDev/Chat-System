package client;

import javax.swing.text.StyleConstants;
import java.awt.Color;

/**
 * Emoticon replacement and consistent colors for styled chat.
 */
public final class StyleUtil {

    private StyleUtil() {}

    public static String applyEmoticons(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replace("<3", "\u2764\uFE0F");
        s = s.replace(":)", "\uD83D\uDE0A");
        s = s.replace(":(", "\uD83D\uDE22");
        return s;
    }

    public static Color colorForUsername(String username) {
        if (username == null || username.isEmpty()) {
            return new Color(60, 60, 60);
        }
        int h = Math.abs(username.hashCode());
        float hue = (h % 360) / 360f;
        return Color.getHSBColor(hue, 0.55f, 0.85f);
    }

    public static void setForeground(javax.swing.text.SimpleAttributeSet attrs, Color c) {
        StyleConstants.setForeground(attrs, c);
    }
}
