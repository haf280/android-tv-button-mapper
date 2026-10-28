package io.github.haf280.tvrecents;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.Map;
import java.util.TreeMap;

/**
 * What the remote's spare buttons do, kept on the TV. An action is "recents", "home", "back",
 * "closeall", or "app:" followed by a package name. Starts with the blue button opening Recents.
 */
final class Buttons {
    static final String PREFS = "buttons", RECENTS = "recents", HOME = "home", BACK = "back", CLOSE_ALL = "closeall",
            APP = "app:";

    static SharedPreferences prefs(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        if (!p.getBoolean("init", false)) {
            p.edit().putBoolean("init", true).putString("k:" + KeyEvent.KEYCODE_PROG_BLUE, RECENTS).apply();
        }
        return p;
    }

    static String get(Context c, int key) {
        return allowed(key) ? prefs(c).getString("k:" + key, null) : null;
    }

    static void set(Context c, int key, String action) { prefs(c).edit().putString("k:" + key, action).apply(); }

    static void remove(Context c, int key) { prefs(c).edit().remove("k:" + key).apply(); }

    /** Button to action, in key-code order. */
    static Map<Integer, String> all(Context c) {
        Map<Integer, String> out = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet())
            if (e.getKey().startsWith("k:")) out.put(Integer.parseInt(e.getKey().substring(2)), (String) e.getValue());
        return out;
    }

    /** The buttons you need to use the TV at all stay as they are, so a mapping can't lock you out. */
    static boolean allowed(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_TV_POWER: case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE: case KeyEvent.KEYCODE_UNKNOWN:
                return false;
            default:
                return true;
        }
    }

    /** "Blue button", "Button 4 (191)", "Channel up (166)". */
    static String name(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_PROG_RED: return "Red button";
            case KeyEvent.KEYCODE_PROG_GREEN: return "Green button";
            case KeyEvent.KEYCODE_PROG_YELLOW: return "Yellow button";
            case KeyEvent.KEYCODE_PROG_BLUE: return "Blue button";
        }
        String s = KeyEvent.keyCodeToString(key).replace("KEYCODE_", "").replace('_', ' ').toLowerCase(java.util.Locale.US);
        return (s.isEmpty() ? "Key" : Character.toUpperCase(s.charAt(0)) + s.substring(1)) + " (" + key + ")";
    }

    /** "Open Stremio", "Recent apps"... */
    static String describe(Context c, String action) {
        if (action == null) return "Nothing";
        switch (action) {
            case RECENTS: return "Recent apps";
            case HOME: return "Home";
            case BACK: return "Back";
            case CLOSE_ALL: return "Close all apps";
        }
        if (!action.startsWith(APP)) return action;
        String pkg = action.substring(APP.length());
        try { return "Open " + c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)); }
        catch (Exception x) { return "Open " + pkg + " (not installed)"; }
    }
}
