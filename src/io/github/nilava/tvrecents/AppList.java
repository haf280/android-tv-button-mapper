package io.github.nilava.tvrecents;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which apps to show. Android 14 doesn't tell normal apps what's running, so this shows apps
 * you opened since the TV started, minus the ones closed here since you last opened them.
 */
final class AppList {
    static final class Item {
        String pkg; CharSequence label; Drawable art; boolean banner; Intent launch; long used;
    }

    static final String PREFS = "recents";

    static long bootTime() { return System.currentTimeMillis() - SystemClock.elapsedRealtime(); }

    static Set<String> homes(PackageManager pm) {
        Set<String> s = new HashSet<>();
        for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0))
            s.add(r.activityInfo.packageName);
        return s;
    }

    /** Most recent first. */
    static List<Item> load(Context c, int max) {
        PackageManager pm = c.getPackageManager();
        Set<String> skip = homes(pm);
        skip.add(c.getPackageName());
        SharedPreferences prefs = c.getSharedPreferences(PREFS, 0);

        long now = System.currentTimeMillis();
        Map<String, Long> last = new HashMap<>();
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents ev = usm.queryEvents(bootTime(), now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND && !skip.contains(e.getPackageName()))
                last.put(e.getPackageName(), e.getTimeStamp());
        }
        List<Map.Entry<String, Long>> order = new ArrayList<>(last.entrySet());
        order.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<Item> out = new ArrayList<>();
        for (Map.Entry<String, Long> en : order) {
            if (out.size() >= max) break;
            if (en.getValue() <= prefs.getLong("closed:" + en.getKey(), 0)) continue; // closed since
            Item it = new Item();
            it.pkg = en.getKey();
            it.used = en.getValue();
            it.launch = pm.getLeanbackLaunchIntentForPackage(it.pkg);
            if (it.launch == null) it.launch = pm.getLaunchIntentForPackage(it.pkg);
            if (it.launch == null) continue; // not something you can open
            try {
                it.label = pm.getApplicationLabel(pm.getApplicationInfo(it.pkg, 0));
                it.art = pm.getApplicationBanner(it.pkg);
                it.banner = it.art != null;
                if (it.art == null) it.art = pm.getApplicationIcon(it.pkg);
            } catch (PackageManager.NameNotFoundException x) { continue; }
            out.add(it);
        }
        return out;
    }

    static void markClosed(Context c, Collection<String> pkgs) {
        SharedPreferences.Editor ed = c.getSharedPreferences(PREFS, 0).edit();
        long now = System.currentTimeMillis();
        for (String p : pkgs) ed.putLong("closed:" + p, now);
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

    static boolean hasUsageAccess(Context c) {
        android.app.AppOpsManager ops = (android.app.AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), c.getPackageName()) == android.app.AppOpsManager.MODE_ALLOWED;
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
