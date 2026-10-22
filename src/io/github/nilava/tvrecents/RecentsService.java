package io.github.nilava.tvrecents;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * - Hold Home (half a second) shows the switcher over whatever is on screen. A short press still goes Home.
 * - Closing an app: opens its App info, presses "Force stop", then "OK", behind a cover screen.
 *   It only touches Settings screens it opened itself, and gives up on an app after a few seconds.
 * - Keeps track of the app on screen, so the switcher can mark it and you come back to it after closing others.
 */
public class RecentsService extends AccessibilityService {
    static final long HOLD_MS = 500, STEP_TIMEOUT_MS = 4000, POLL_MS = 150, PAGE_SETTLE_MS = 600;
    static final String SETTINGS = "com.android.tv.settings";

    static RecentsService instance;

    final Handler h = new Handler(Looper.getMainLooper());
    WindowManager wm;
    Panel ui;
    View panel;
    Panel.Cover cover;
    String foreground;   // the app on screen; null when it's the home screen
    boolean homeHeld, longFired;

    @Override protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ui = new Panel(this);
        // Just switched on from setup: go back to it, so it can say "All set".
        if (SetupActivity.waiting) {
            startActivity(new Intent(this, SetupActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
    }

    @Override public boolean onUnbind(Intent i) {
        hidePanel();
        hideCover();
        instance = null;
        h.removeCallbacksAndMessages(null);
        return super.onUnbind(i);
    }

    @Override public void onInterrupt() { }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null || busy()) return;
        String p = e.getPackageName().toString();
        if (p.equals(getPackageName()) || p.equals(SETTINGS) || p.equals("com.android.systemui") || p.equals("android")) return;
        if (getPackageManager().getLaunchIntentForPackage(p) == null
                && getPackageManager().getLeanbackLaunchIntentForPackage(p) == null
                && !AppList.homes(getPackageManager()).contains(p)) return; // keyboards, dialogs, overlays
        foreground = AppList.homes(getPackageManager()).contains(p) ? null : p;
        if (foreground != null && !foreground.equals(lastNoted)) AppList.opened(this, foreground);
        lastNoted = foreground;
    }

    String lastNoted; // the last app written to the list, so switching windows inside one app doesn't rewrite it

    // ---- Remote ----

    final Runnable onHold = () -> {
        longFired = true;
        if (panel != null) dismissPanel(); else showPanel(null);
    };

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (busy()) {                                    // closing apps: keep the remote off Settings
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) queue.clear();
            return true;
        }
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (!homeHeld) {
                homeHeld = true;
                longFired = false;
                h.postDelayed(onHold, HOLD_MS);
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            homeHeld = false;
            h.removeCallbacks(onHold);
            if (!longFired) {
                hidePanel();
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        }
        return true;
    }

    // ---- Switcher ----

    void showPanel(String status) {
        hidePanel();
        List<AppList.Item> items = AppList.load(this, 12);
        panel = ui.build(items, foreground, status, actions);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.windowAnimations = 0; // the panel animates itself
        wm.addView(panel, lp);
    }

    void hidePanel() {
        if (panel != null) { wm.removeView(panel); panel = null; }
    }

    /** Back: a quick fade instead of vanishing. Switching apps still hides it at once. */
    void dismissPanel() {
        if (panel == null) return;
        final View v = panel;
        panel = null;
        Panel.fade(v, 1, 0, 140, () -> { if (v.isAttachedToWindow()) wm.removeView(v); });
    }

    final Panel.Actions actions = new Panel.Actions() {
        @Override public void open(AppList.Item it) {
            hidePanel();
            if (it.pkg.equals(foreground)) return; // already on screen
            startActivity(new Intent(it.launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        @Override public void close(AppList.Item it) { closeApps(Collections.singletonList(it.pkg), it.label); }
        @Override public void closeAll(List<AppList.Item> items) {
            List<String> pkgs = AppList.closeAllTargets(RecentsService.this, items);
            pkgs.remove(foreground); // never the app you're in
            closeApps(pkgs, null);
        }
        @Override public void dismiss() { dismissPanel(); }
    };

    // ---- Closing apps ----

    final ArrayDeque<String> queue = new ArrayDeque<>();
    final List<String> done = new ArrayList<>();
    String current, returnTo;
    CharSequence singleLabel;
    int step, total, stopped;     // step: 0 idle, 1 waiting for App info, 2 waiting for the OK prompt
    long stepStarted, pageSeen, memBefore;

    boolean busy() { return step != 0; }

    void closeApps(List<String> pkgs, CharSequence label) {
        if (busy() || pkgs.isEmpty()) return;
        hidePanel();
        returnTo = foreground != null && !pkgs.contains(foreground) ? foreground : null;
        singleLabel = label;
        memBefore = AppList.memory(this).availMem;
        queue.clear();
        queue.addAll(pkgs);
        done.clear();
        stopped = 0;
        total = pkgs.size();
        showCover();
        next();
    }

    void next() {
        current = queue.poll();
        if (current == null) { finishClosing(); return; }
        step = 1;
        stepStarted = SystemClock.uptimeMillis();
        pageSeen = 0;
        showStep(current);
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", current, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        h.postDelayed(poll, POLL_MS);
    }

    final Runnable poll = new Runnable() {
        @Override public void run() {
            if (step == 0) return;
            if (SystemClock.uptimeMillis() - stepStarted > STEP_TIMEOUT_MS) { next(); return; } // give up on this one
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && SETTINGS.contentEquals(String.valueOf(root.getPackageName()))) {
                if (step == 1 && hasText(root, current)) {           // App info for the right app
                    if (pageSeen == 0) pageSeen = SystemClock.uptimeMillis();
                    AccessibilityNodeInfo fs = find(root, "Force stop");
                    if (fs != null) {
                        if (!fs.isEnabled()) { done.add(current); next(); return; } // not running
                        if (click(fs)) { step = 2; stepStarted = SystemClock.uptimeMillis(); }
                    } else if (SystemClock.uptimeMillis() - pageSeen > PAGE_SETTLE_MS) {
                        done.add(current); next(); return;             // no Force stop shown: not running
                    }
                } else if (step == 2) {                                // the "Force stop?" prompt
                    AccessibilityNodeInfo ok = find(root, "OK");
                    if (ok != null && click(ok)) { done.add(current); stopped++; next(); return; }
                }
            }
            h.postDelayed(this, POLL_MS);
        }
    };

    void finishClosing() {
        step = 0;
        AppList.markClosed(this, done);
        // Back to where you were: the app you were watching, or the home screen.
        if (returnTo != null) {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(returnTo);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(returnTo);
            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            else performGlobalAction(GLOBAL_ACTION_HOME);
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        foreground = returnTo;
        h.postDelayed(() -> {
            hideCover();
            long freed = Math.max(0, AppList.memory(this).availMem - memBefore);
            String what = singleLabel != null ? "Closed " + singleLabel
                    : stopped == 0 ? "Nothing else was running"
                    : "Closed " + stopped + (stopped == 1 ? " app" : " apps");
            showPanel(freed >= 10L * 1024 * 1024 ? what + "  ·  freed " + AppList.size(freed) : what);
        }, 900);
    }

    void showStep(String pkg) {
        android.graphics.drawable.Drawable art = null;
        boolean banner = false;
        try {
            art = getPackageManager().getApplicationBanner(pkg);
            banner = art != null;
            if (art == null) art = getPackageManager().getApplicationIcon(pkg);
        } catch (Exception x) { /* no picture, just the name */ }
        cover.update(label(pkg), art, banner, done.size(), total);
    }

    void showCover() {
        hideCover();
        cover = ui.new Cover();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Not focusable, so Settings underneath stays the active window to press buttons in.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        wm.addView(cover.root, lp);
        Panel.fade(cover.root, 0, 1, 160, null);
    }

    void hideCover() {
        if (cover == null) return;
        final View v = cover.root;
        cover = null;
        Panel.fade(v, 1, 0, 160, () -> { if (v.isAttachedToWindow()) wm.removeView(v); });
    }

    CharSequence label(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)); }
        catch (Exception x) { return pkg; }
    }

    static boolean hasText(AccessibilityNodeInfo root, String s) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByText(s);
        return l != null && !l.isEmpty();
    }

    /** A node whose text is exactly this, ignoring case. */
    static AccessibilityNodeInfo find(AccessibilityNodeInfo root, String label) {
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByText(label);
        if (l == null) return null;
        for (AccessibilityNodeInfo n : l)
            if (n.getText() != null && n.getText().toString().trim().equalsIgnoreCase(label)) return n;
        return null;
    }

    /** Clicks the node, or the nearest parent that can be clicked. */
    static boolean click(AccessibilityNodeInfo n) {
        for (int i = 0; n != null && i < 6; i++, n = n.getParent())
            if (n.isClickable()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return false;
    }
}
