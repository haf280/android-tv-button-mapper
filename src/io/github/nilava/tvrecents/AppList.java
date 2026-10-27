package io.github.nilava.tvrecents;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which apps to show: the ones you opened since the TV started, most recent first, minus the ones
 * closed here since you last opened them. The Recents service writes this list itself as apps come
 * to the screen. It's timed with the TV's uptime, not its clock, so a clock that jumps (a TV that
 * starts with the wrong date and fixes it later) can't empty it. Android's own usage log can.
 */
final class AppList {
    static final class Item {
        String pkg; CharSequence label; Drawable art; boolean banner; Intent launch; long used;
    }

    static final String PREFS = "recents";
    static final int KEEP = 30;

    /** How to open an app: its TV entry first, then the phone one. Null if it can't be opened. */
    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    static Set<String> homes(PackageManager pm) {
        Set<String> s = new HashSet<>();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            s.add(r.activityInfo.packageName);
        return s;
    }

    /** Changes every time the TV starts, so the list starts empty after a restart. */
    static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
    }

    /** The saved list, cleared if it's from before this start. */
    static SharedPreferences history(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        int boot = bootCount(c);
        if (p.getInt("boot", -1) != boot) p.edit().clear().putInt("boot", boot).apply();
        return p;
    }

    static List<String> order(SharedPreferences p) {
        String s = p.getString("order", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    /** An app came to the screen. */
    static void opened(Context c, String pkg) {
        SharedPreferences p = history(c);
        List<String> o = order(p);
        o.remove(pkg);
        o.add(0, pkg);
        while (o.size() > KEEP) o.remove(o.size() - 1);
        p.edit().putString("order", String.join(",", o)).putLong("t:" + pkg, SystemClock.elapsedRealtime()).apply();
    }

    /** Most recent first. */
    static List<Item> load(Context c, int max) {
        PackageManager pm = c.getPackageManager();
        Set<String> skip = homes(pm);
        skip.add(c.getPackageName());
        SharedPreferences prefs = history(c);
        long up = SystemClock.elapsedRealtime(), now = System.currentTimeMillis();

        List<Item> out = new ArrayList<>();
        for (String pkg : order(prefs)) {
            if (out.size() >= max) break;
            long t = prefs.getLong("t:" + pkg, 0);
            if (skip.contains(pkg) || t <= prefs.getLong("closed:" + pkg, 0)) continue; // closed since
            Item it = new Item();
            it.pkg = pkg;
            it.used = now - (up - t);
            it.launch = launchIntent(pm, pkg);
            if (it.launch == null) continue; // uninstalled, or not something you can open
            try {
                it.label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                it.art = pm.getApplicationBanner(pkg);
                it.banner = it.art != null;
                if (it.art == null) it.art = pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException x) { continue; }
            out.add(it);
        }
        return out;
    }

    static void markClosed(Context c, Collection<String> pkgs) {
        SharedPreferences.Editor ed = history(c).edit();
        long up = SystemClock.elapsedRealtime();
        for (String p : pkgs) ed.putLong("closed:" + p, up);
        ed.apply();
    }

    /**
     * The listed apps, then every other app you can open that you installed or that got updated
     * (Netflix, YouTube...), since some start themselves in the background without being opened.
     * Never stock system apps (Live TV, Settings), home screens, the keyboard, or apps with an
     * accessibility service on (force stop would switch that service off).
     */
    static List<String> closeAllTargets(Context c, List<Item> items) {
        PackageManager pm = c.getPackageManager();
        Set<String> skip = homes(pm);
        skip.add(c.getPackageName());
        String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null) skip.add(ime.split("/")[0]);
        String a11y = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (a11y != null) for (String s : a11y.split(":")) skip.add(s.split("/")[0]);

        List<String> out = new ArrayList<>();
        for (Item it : items) if (!skip.contains(it.pkg) && !out.contains(it.pkg) && !isTvMaker(it.pkg)) out.add(it.pkg);
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = r.activityInfo.packageName;
                int f = r.activityInfo.applicationInfo.flags;
                boolean stockSystem = (f & ApplicationInfo.FLAG_SYSTEM) != 0 && (f & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || (f & ApplicationInfo.FLAG_PERSISTENT) != 0 || skip.contains(p) || out.contains(p)
                        || isTvMaker(p)) continue;
                out.add(p);
            }
        }
        return out;
    }

    /** TV makers' and chip makers' own apps. Close all leaves them alone: some keep the TV itself running. */
    static final String[] TV_MAKERS = {
            "com.haier.", "com.haierubic.", "it.hon.", "com.tcl.", "com.sony.", "com.xiaomi.", "com.mitv.",
            "com.hisense.", "com.philips.", "com.tpvision.", "com.skyworth.", "com.vu.", "com.onida.",
            "com.nvidia.", "com.amazon.tv.", "com.mediatek.", "com.mstar.", "com.realtek.", "com.amlogic.",
            "com.droidlogic.", "com.rockchip.", "com.cvte.",
    };

    static boolean isTvMaker(String p) {
        for (String m : TV_MAKERS) if (p.startsWith(m)) return true;
        return false;
    }

    static ActivityManager.MemoryInfo memory(Context c) {
        ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(m);
        return m;
    }

    static String size(long bytes) {
        if (bytes >= 1000L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024f * 1024 * 1024));
        return (bytes / (1024 * 1024)) + " MB";
    }
}
