package io.github.haf280.tvrecents;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What each spare remote button does. Add one by pressing it, then pick an app or an action.
 * Opened from the Buttons pill in Recents. Changes work at once; they're kept on the TV.
 */
public class ButtonsActivity extends Activity {
    /**
     * While on, the service hands this screen the next spare button it sees. It has to: some buttons
     * (Sony's app buttons, for one) are taken by the TV before any screen gets them.
     */
    static volatile boolean listening;
    static ButtonsActivity shown; // this screen while it's on top

    static final int BG = 0xFF0B0B0D, ROW = 0xFF17171B, ROW_FOCUS = 0xFF24242A, WHITE = 0xFFFFFFFF,
            TEXT2 = 0xB3FFFFFF, TEXT3 = 0x80FFFFFF;

    final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    TextView title, sub;
    LinearLayout list;
    int editing = -1; // the button whose action is being picked

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(48), 0, dp(32));
        root.addView(col, new FrameLayout.LayoutParams(dp(820), -1, Gravity.CENTER_HORIZONTAL));

        title = text("", 28, WHITE);
        title.setTypeface(medium);
        col.addView(title);
        sub = text("", 15, TEXT2);
        sub.setPadding(0, dp(10), 0, dp(22));
        col.addView(sub);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(24));
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        showButtons();
    }

    @Override protected void onResume() {
        super.onResume();
        shown = this;
    }

    @Override protected void onPause() {
        listening = false; // never leave the service handing buttons to a screen that's gone
        shown = null;
        super.onPause();
    }

    /** The service caught the button. */
    void captured(int key) {
        if (!listening) return;
        listening = false;
        pick(key);
    }

    // ---------------------------------------------------------------- the three steps

    void showButtons() {
        listening = false;
        editing = -1;
        title.setText("Buttons");
        sub.setText(RecentsService.instance != null
                ? "What your remote's spare buttons do. Changes work at once."
                : "Turn on Recents in Accessibility first: until then these buttons do nothing.");
        list.removeAllViews();
        View add = row("+   Add button", "Then press it on the remote", v -> listen());
        for (Map.Entry<Integer, String> e : Buttons.all(this).entrySet()) {
            final int key = e.getKey();
            row(Buttons.name(key), Buttons.describe(this, e.getValue()), v -> pick(key));
        }
        add.post(add::requestFocus);
    }

    void listen() {
        listening = true;
        title.setText("Press a button on the remote");
        sub.setText("Back cancels. OK, the arrows, Back, Home, Menu, power and volume stay as they are.");
        list.removeAllViews();
    }

    void pick(final int key) {
        editing = key;
        String now = Buttons.prefs(this).getString("k:" + key, null);
        title.setText(Buttons.name(key));
        sub.setText(now != null ? "Now: " + Buttons.describe(this, now) + ". Pick what it should do." : "Pick what it should do.");
        list.removeAllViews();
        View first = row("Recent apps", "Show the switcher, or hide it", v -> save(Buttons.RECENTS));
        row("Home", null, v -> save(Buttons.HOME));
        row("Back", null, v -> save(Buttons.BACK));
        row("Close all apps", "Everything except the app on screen", v -> save(Buttons.CLOSE_ALL));
        header("Open an app");
        for (final String[] app : apps()) row(app[1], null, v -> save(Buttons.APP + app[0]));
        if (now != null) {
            header("");
            row("Remove this button", "It goes back to its normal job", v -> { Buttons.remove(this, key); showButtons(); });
        }
        first.post(first::requestFocus);
    }

    void save(String action) {
        Buttons.set(this, editing, action);
        showButtons();
    }

    /** The press that names the button. Everything else goes to the screen as usual. */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (!listening) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_UP) return true;
        int key = e.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK) showButtons();
        else if (!Buttons.allowed(key)) sub.setText(Buttons.name(key) + " stays as it is: you need it to use the TV. Try another, or press Back.");
        else { listening = false; pick(key); }
        return true;
    }

    @Override public void onBackPressed() {
        if (editing != -1) showButtons(); else super.onBackPressed();
    }

    /** Apps you can open: package and name, by name. */
    List<String[]> apps() {
        PackageManager pm = getPackageManager();
        List<String[]> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = r.activityInfo.packageName;
                if (seen.contains(p) || p.equals(getPackageName())) continue;
                seen.add(p);
                out.add(new String[] {p, r.activityInfo.applicationInfo.loadLabel(pm).toString()});
            }
        }
        Collections.sort(out, (a, b) -> a[1].compareToIgnoreCase(b[1]));
        return out;
    }

    // ---------------------------------------------------------------- bits

    View row(CharSequence t, CharSequence s, View.OnClickListener l) {
        final LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(22), dp(14), dp(22), dp(14));
        v.setFocusable(true);
        v.setClickable(true);
        TextView a = text(t, 17, WHITE);
        a.setTypeface(medium);
        v.addView(a);
        if (s != null) {
            TextView b = text(s, 13, TEXT3);
            b.setPadding(0, dp(5), 0, 0);
            v.addView(b);
        }
        v.setOnClickListener(l);
        v.setOnFocusChangeListener((x, f) -> style(v, f));
        style(v, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        list.addView(v, lp);
        return v;
    }

    void style(View v, boolean f) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(f ? ROW_FOCUS : ROW);
        if (f) bg.setStroke(dp(2), WHITE);
        v.setBackground(bg);
    }

    void header(String s) {
        TextView h = text(s, 13, TEXT3);
        h.setTypeface(medium);
        h.setLetterSpacing(0.08f);
        h.setPadding(dp(4), dp(18), 0, dp(8));
        list.addView(h);
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
