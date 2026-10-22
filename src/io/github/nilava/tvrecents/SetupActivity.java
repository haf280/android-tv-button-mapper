package io.github.nilava.tvrecents;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * First run: two switches, each one press away, with what it's for. Comes back here by itself
 * when a switch is turned on (where Android allows it), and steps aside once both are on.
 */
public class SetupActivity extends Activity {
    /** Set while setup is open, so the service can bring it back when it's switched on. */
    static volatile boolean waiting;

    static final int BG = 0xFF0B0B0D, CARD = 0xFF17171B, WHITE = 0xFFFFFFFF, TEXT2 = 0xB3FFFFFF, TEXT3 = 0x80FFFFFF,
            GOOD = 0xFF8FD19E, CHIP = 0x1FFFFFFF;

    final Handler h = new Handler(Looper.getMainLooper());
    final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    Step usage, shortcut;
    LinearLayout steps, done;
    TextView doneButton;
    AppOpsManager ops;

    final AppOpsManager.OnOpChangedListener onUsageChanged = (op, pkg) -> {
        if (!getPackageName().equals(pkg)) return;
        h.post(() -> {
            if (AppList.hasUsageAccess(this)) comeBack();
        });
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(dp(720), -2, Gravity.CENTER));

        TextView title = text("Set up Recents", 28, WHITE);
        title.setTypeface(medium);
        col.addView(title);
        TextView sub = text("Two switches and you’re done. Recents never connects to the internet.", 15, TEXT2);
        sub.setPadding(0, dp(10), 0, dp(26));
        col.addView(sub);

        steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        usage = new Step(1, "Allow Usage access",
                "So Recents can see which apps you opened, and when. Find “Recents” in the list and turn it on.",
                v -> openUsageAccess());
        shortcut = new Step(2, "Turn on the Recents shortcut",
                "Lets you hold Home to open Recents, and presses Force stop for you when you close an app. "
                        + "Android will warn that it can see and control the screen: Recents only reads the "
                        + "Settings screens it opens itself, and only when you ask it to close an app.",
                v -> openAccessibility());
        steps.addView(usage.view);
        steps.addView(shortcut.view);
        col.addView(steps);

        TextView hint = text("After switching it on, press Back to return here.", 13, TEXT3);
        hint.setPadding(dp(4), dp(18), 0, 0);
        col.addView(hint);
        hint.setTag("hint");

        // All set.
        done = new LinearLayout(this);
        done.setOrientation(LinearLayout.VERTICAL);
        TextView ok = text("✓  All set", 22, GOOD);
        ok.setTypeface(medium);
        done.addView(ok);
        TextView tip = text("Hold Home on your remote, from any app, to open Recents.", 16, TEXT2);
        tip.setPadding(0, dp(10), 0, dp(24));
        done.addView(tip);
        doneButton = text("Open Recents", 16, WHITE);
        doneButton.setTypeface(medium);
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setPadding(dp(28), 0, dp(28), 0);
        doneButton.setFocusable(true);
        doneButton.setClickable(true);
        doneButton.setOnFocusChangeListener((v, f) -> pill(doneButton, f));
        pill(doneButton, false);
        doneButton.setOnClickListener(v -> {
            if (RecentsService.instance != null) RecentsService.instance.showPanel(null);
            finish();
        });
        done.addView(doneButton, new LinearLayout.LayoutParams(-2, dp(48)));
        done.setVisibility(View.GONE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = dp(30);
        col.addView(done, dlp);

        setContentView(root);
        ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        ops.startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, getPackageName(), onUsageChanged);
    }

    @Override protected void onResume() {
        super.onResume();
        waiting = true;
        refresh();
    }

    @Override protected void onDestroy() {
        waiting = false;
        ops.stopWatchingMode(onUsageChanged);
        super.onDestroy();
    }

    static boolean complete(Activity a) { return AppList.hasUsageAccess(a) && RecentsService.instance != null; }

    void refresh() {
        boolean u = AppList.hasUsageAccess(this), s = RecentsService.instance != null;
        usage.set(u);
        shortcut.set(s);
        boolean all = u && s;
        done.setVisibility(all ? View.VISIBLE : View.GONE);
        View hint = getWindow().getDecorView().findViewWithTag("hint");
        if (hint != null) hint.setVisibility(all ? View.GONE : View.VISIBLE);
        View target = all ? doneButton : !u ? usage.view : shortcut.view;
        target.post(target::requestFocus);
    }

    /** Back on top: from Settings, once a switch is on. */
    void comeBack() {
        if (!waiting) return;
        startActivity(new Intent(this, SetupActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    void openUsageAccess() {
        open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    void openAccessibility() {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        // Some TVs also list an empty placeholder for this screen; go to the real Settings app.
        for (ResolveInfo r : getPackageManager().queryIntentActivities(i, 0)) {
            String p = r.activityInfo.packageName;
            if (p.contains("stub")) continue;
            i.setClassName(p, r.activityInfo.name);
            break;
        }
        open(i);
    }

    void open(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { Toast.makeText(this, "Open this in Settings yourself: it isn’t reachable directly on this TV", Toast.LENGTH_LONG).show(); }
    }

    // ---------------------------------------------------------------- a step

    final class Step {
        final LinearLayout view = new LinearLayout(SetupActivity.this);
        final TextView num, title, body, state;
        final int n;
        boolean on;

        Step(int n, String t, String b, View.OnClickListener l) {
            this.n = n;
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(dp(22), dp(18), dp(22), dp(18));
            view.setFocusable(true);
            view.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(12);
            view.setLayoutParams(lp);

            num = text(String.valueOf(n), 15, WHITE);
            num.setTypeface(medium);
            num.setGravity(Gravity.CENTER);
            view.addView(num, new LinearLayout.LayoutParams(dp(32), dp(32)));

            LinearLayout texts = new LinearLayout(SetupActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(18), 0, dp(18), 0);
            title = text(t, 17, WHITE);
            title.setTypeface(medium);
            texts.addView(title);
            body = text(b, 13, TEXT3);
            body.setPadding(0, dp(6), 0, 0);
            body.setLineSpacing(0, 1.15f);
            texts.addView(body);
            view.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

            state = text("", 13, WHITE);
            state.setTypeface(medium);
            state.setPadding(dp(14), dp(6), dp(14), dp(6));
            view.addView(state);

            view.setOnClickListener(l);
            view.setOnFocusChangeListener((v, f) -> style(f));
            style(false);
        }

        void set(boolean on) {
            this.on = on;
            style(view.hasFocus());
        }

        void style(boolean f) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(16));
            bg.setColor(f ? 0xFF24242A : CARD);
            if (f) bg.setStroke(dp(2), WHITE);
            view.setBackground(bg);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(on ? GOOD : CHIP);
            num.setBackground(dot);
            num.setText(on ? "✓" : String.valueOf(n));
            num.setTextColor(on ? 0xFF0B0B0D : WHITE);
            state.setText(on ? "On" : f ? "Open settings" : "Off");
            state.setTextColor(on ? GOOD : f ? 0xFF111111 : TEXT2);
            GradientDrawable chip = new GradientDrawable();
            chip.setCornerRadius(dp(99));
            chip.setColor(on ? 0x268FD19E : f ? WHITE : CHIP);
            state.setBackground(chip);
            body.setAlpha(f || !on ? 1f : 0.7f);
        }
    }

    void pill(TextView b, boolean f) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(99));
        g.setColor(f ? WHITE : 0x00000000);
        if (!f) g.setStroke(dp(1.5f), 0x59FFFFFF);
        b.setBackground(g);
        b.setTextColor(f ? 0xFF111111 : WHITE);
    }

    TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
