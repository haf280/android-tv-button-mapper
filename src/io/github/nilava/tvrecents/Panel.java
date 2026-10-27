package io.github.nilava.tvrecents;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** The switcher and the closing screen. Drawn over whatever is on screen. */
final class Panel {
    interface Actions {
        void open(AppList.Item it);
        void close(AppList.Item it);
        void closeAll(List<AppList.Item> items);
        void buttons();
        void dismiss();
    }

    static final int WHITE = 0xFFFFFFFF, TEXT2 = 0xB3FFFFFF, TEXT3 = 0x80FFFFFF, LINE = 0x29FFFFFF,
            CARD = 0xFF1E1E22, CHIP = 0x1FFFFFFF, AMBER = 0xFFF2B84B, COVER = 0xFF0C0C0E;

    final Context c;
    final float d;
    final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    Panel(Context c) { this.c = c; d = c.getResources().getDisplayMetrics().density; }

    int dp(float v) { return Math.round(v * d); }

    // ---------------------------------------------------------------- switcher

    View build(final List<AppList.Item> items, String current, String status, final Actions a) {
        FrameLayout root = new FrameLayout(c) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP) a.dismiss();
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        // A soft rise of dark from the bottom, so the app above stays visible.
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {0xFA000000, 0xF2000000, 0xE0000000, 0x99000000, 0x40000000}));

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPadding(dp(56), 0, dp(56), dp(36));
        root.addView(body, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // Header: title and status on the left, memory on the right.
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.BOTTOM);
        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Recent apps", 22, WHITE);
        title.setTypeface(medium);
        titles.addView(title);
        if (status != null) {
            TextView s = text(status, 14, TEXT2);
            s.setPadding(0, dp(6), 0, 0);
            titles.addView(s);
        }
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(memory());
        body.addView(head);

        // Cards.
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(14), 0, dp(10));
        LinearLayout row = new LinearLayout(c);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setGravity(Gravity.TOP);
        // Room on every side for a focused card to lift (it grows 8%) without being cut off.
        row.setPadding(dp(18), dp(14), dp(40), dp(12));
        scroll.addView(row);
        LinearLayout.LayoutParams scp = new LinearLayout.LayoutParams(-1, -2);
        scp.setMarginStart(-dp(18)); // the row's lift room, so cards still line up with the title
        body.addView(scroll, scp);

        View first = null;
        if (items.isEmpty()) {
            LinearLayout none = new LinearLayout(c);
            none.setOrientation(LinearLayout.VERTICAL);
            none.setPadding(0, dp(28), 0, dp(28));
            none.addView(text("Nothing open", 17, WHITE));
            TextView sub = text("Apps you open will show up here.", 14, TEXT3);
            sub.setPadding(0, dp(6), 0, 0);
            none.addView(sub);
            row.addView(none);
        } else {
            int focus = items.size() > 1 && items.get(0).pkg.equals(current) ? 1 : 0; // the app before this one
            String keep = null;
            for (int i = 0; i < items.size(); i++) {
                final AppList.Item it = items.get(i);
                boolean cur = it.pkg.equals(current);
                if (cur) keep = it.label.toString();
                View card = card(it, cur, v -> a.open(it), () -> a.close(it));
                row.addView(card);
                if (i == focus) first = card;
            }
            row.addView(pill("✕   Close all", keep != null ? "Keeps " + keep + " open" : "Frees memory", v -> a.closeAll(items)));
        }
        View buttons = pill("Buttons", "Remote shortcuts", v -> a.buttons());
        row.addView(buttons);
        if (first == null) first = buttons;

        // Keys.
        LinearLayout keys = new LinearLayout(c);
        keys.setGravity(Gravity.CENTER_VERTICAL);
        if (!items.isEmpty()) {
            key(keys, "OK", "Switch");
            key(keys, "MENU", "Close app");
        }
        key(keys, "BACK", "Hide");
        body.addView(keys);

        root.setAlpha(0f);
        body.setTranslationY(dp(24));
        final View focusMe = first;
        root.post(() -> {
            if (focusMe != null) focusMe.requestFocus();
            final float rise = dp(24);
            animate(root, 220, e -> { root.setAlpha(e); body.setTranslationY(rise * (1 - e)); }, null);
        });
        return root;
    }

    View card(AppList.Item it, boolean current, View.OnClickListener open, final Runnable close) {
        final LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(200), -2);
        lp.setMarginEnd(dp(22));
        card.setLayoutParams(lp);

        final FrameLayout art = art(it.art, it.banner, dp(200), dp(112));
        if (current) {
            TextView pill = text("Current", 11, WHITE);
            pill.setTypeface(medium);
            pill.setLetterSpacing(0.04f);
            pill.setPadding(dp(8), dp(3), dp(8), dp(3));
            pill.setBackground(round(0xB3000000, 99));
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
            pp.setMargins(dp(8), dp(8), 0, 0);
            art.addView(pill, pp);
        }
        View shade = new View(c);
        shade.setBackgroundColor(0x80000000);
        art.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        art.setTag(shade);
        card.addView(art, new LinearLayout.LayoutParams(dp(200), dp(112)));

        final TextView name = text(it.label, 15, WHITE);
        name.setSingleLine(true);
        name.setTypeface(medium);
        name.setPadding(dp(2), dp(12), dp(2), 0);
        card.addView(name);
        final TextView sub = text(current ? "On screen" : ago(it.used), 12, TEXT3);
        sub.setPadding(dp(2), dp(3), dp(2), 0);
        card.addView(sub);

        style(card, art, name, sub, false);
        card.setOnFocusChangeListener((v, f) -> style(card, art, name, sub, f));
        card.setOnClickListener(open);
        card.setOnLongClickListener(v -> { close.run(); return true; });
        card.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_MENU) return false;
            if (e.getAction() == KeyEvent.ACTION_UP) close.run();
            return true;
        });
        return card;
    }

    void style(View card, FrameLayout art, TextView name, TextView sub, boolean f) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(2), WHITE);
        art.setForeground(f ? ring : null);
        art.setElevation(f ? dp(14) : 0);
        ((View) art.getTag()).setVisibility(f ? View.GONE : View.VISIBLE);
        name.setAlpha(f ? 1f : 0.55f);
        sub.setAlpha(f ? 1f : 0.7f);
        card.setPivotX(dp(100));
        card.setPivotY(dp(56));
        card.setScaleX(f ? 1.08f : 1f);
        card.setScaleY(f ? 1.08f : 1f);
    }

    /** A pill, so it reads as an action rather than an app. Sits level with the middle of the banners. */
    View pill(String label, String subText, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(32), dp(18), 0);

        final TextView b = text(label, 15, WHITE);
        b.setTypeface(medium);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding(dp(24), 0, dp(24), 0);
        box.addView(b, new LinearLayout.LayoutParams(-2, dp(48)));
        final TextView sub = text(subText, 12, TEXT3);
        sub.setPadding(0, dp(10), 0, 0);
        box.addView(sub);

        View.OnFocusChangeListener fl = (v, f) -> {
            GradientDrawable g = round(f ? WHITE : 0x00000000, 99);
            if (!f) g.setStroke(dp(1.5f), 0x59FFFFFF);
            b.setBackground(g);
            b.setTextColor(f ? 0xFF111111 : WHITE);
            sub.setAlpha(f ? 1f : 0.7f);
        };
        fl.onFocusChange(b, false);
        b.setOnFocusChangeListener(fl);
        b.setOnClickListener(l);
        return box;
    }

    /** "605 MB free / 1.4 GB" over a slim bar of what's in use. Amber when memory is low. */
    View memory() {
        ActivityManager.MemoryInfo m = AppList.memory(c);
        float free = m.totalMem > 0 ? (float) m.availMem / m.totalMem : 1;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.END);

        TextView eyebrow = text("MEMORY", 10, TEXT3);
        eyebrow.setLetterSpacing(0.14f);
        eyebrow.setTypeface(medium);
        box.addView(eyebrow);

        SpannableStringBuilder s = new SpannableStringBuilder(AppList.size(m.availMem) + " free");
        int end = s.length();
        s.append("  /  ").append(AppList.size(m.totalMem));
        s.setSpan(new ForegroundColorSpan(TEXT3), end, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new RelativeSizeSpan(0.86f), end, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t = text(s, 15, WHITE);
        t.setTypeface(medium);
        t.setPadding(0, dp(4), 0, 0);
        box.addView(t);

        int w = dp(168);
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(round(LINE, 2));
        View fill = new View(c);
        fill.setBackground(round(free < 0.2f ? AMBER : 0xD9FFFFFF, 2));
        bar.addView(fill, new FrameLayout.LayoutParams(Math.max(dp(3), Math.round(w * (1 - free))), -1));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(w, dp(3));
        bp.topMargin = dp(8);
        box.addView(bar, bp);
        return box;
    }

    void key(LinearLayout row, String cap, String what) {
        TextView k = text(cap, 10, WHITE);
        k.setTypeface(medium);
        k.setLetterSpacing(0.08f);
        k.setGravity(Gravity.CENTER);
        k.setMinWidth(dp(22));
        k.setPadding(dp(7), dp(4), dp(7), dp(4));
        k.setBackground(round(CHIP, 5));
        row.addView(k);
        TextView w = text(what, 13, TEXT3);
        w.setPadding(dp(8), 0, dp(22), 0);
        row.addView(w);
    }

    // ---------------------------------------------------------------- closing screen

    /** Full screen, so the Settings screens it works in stay hidden. */
    final class Cover {
        final FrameLayout root = new FrameLayout(c);
        final FrameLayout slot = new FrameLayout(c);
        final TextView title = text("", 20, WHITE), count = text("", 13, TEXT3);
        final View fill = new View(c);
        final int barW = dp(240);
        boolean sweeping;

        Cover() {
            root.setBackgroundColor(COVER);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            root.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

            col.addView(slot, new LinearLayout.LayoutParams(dp(176), dp(99)));
            title.setTypeface(medium);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, dp(26), 0, 0);
            col.addView(title, new LinearLayout.LayoutParams(dp(420), -2));

            FrameLayout bar = new FrameLayout(c);
            bar.setBackground(round(LINE, 2));
            fill.setBackground(round(WHITE, 2));
            bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(barW, dp(3));
            bp.topMargin = dp(18);
            col.addView(bar, bp);
            count.setPadding(0, dp(10), 0, 0);
            col.addView(count);

            LinearLayout keys = new LinearLayout(c);
            keys.setGravity(Gravity.CENTER_VERTICAL);
            key(keys, "BACK", "Stop");
            FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            kp.bottomMargin = dp(40);
            root.addView(keys, kp);
        }

        void update(CharSequence label, Drawable art, boolean banner, int done, int total) {
            slot.removeAllViews();
            if (art != null) slot.addView(art(art, banner, dp(176), dp(99)), new FrameLayout.LayoutParams(-1, -1));
            title.setText("Closing " + label);
            count.setText(total > 1 ? (done + 1) + " of " + total : "");
            if (total > 1) {
                fill.setTranslationX(0);
                fill.getLayoutParams().width = Math.round(barW * (done + 0.5f) / total);
                fill.requestLayout();
            } else if (!sweeping) {
                sweeping = true;
                fill.getLayoutParams().width = barW / 3;
                fill.requestLayout();
                final long start = System.nanoTime();
                Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                    @Override public void doFrame(long now) {
                        if (!root.isAttachedToWindow() && System.nanoTime() - start > 500_000_000L) return;
                        float t = ((System.nanoTime() - start) / 1e6f % 1100) / 1100f;
                        float e = t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; // ease in-out
                        fill.setTranslationX(-barW / 3f + e * (barW + barW / 3f));
                        Choreographer.getInstance().postFrameCallback(this);
                    }
                });
            }
        }
    }

    // ---------------------------------------------------------------- bits

    FrameLayout art(Drawable art, boolean banner, int w, int h) {
        FrameLayout frame = new FrameLayout(c);
        frame.setBackground(round(CARD, 12));
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12)); }
        });
        frame.setClipToOutline(true);
        ImageView img = new ImageView(c);
        img.setImageDrawable(art);
        img.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        int pad = banner ? 0 : Math.round(h * 0.24f);
        img.setPadding(pad, pad, pad, pad);
        frame.addView(img, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    /**
     * Fades and slides a view, frame by frame. System animations are off on this TV (on purpose,
     * for speed), and that turns Android's own animators into instant jumps, so this runs its own.
     */
    interface Step { void at(float e); }

    /**
     * Runs an ease-out animation frame by frame, timed from the first frame that is actually
     * drawn (building the panel can take a moment on this TV, and that shouldn't eat into it).
     * The view is drawn as one layer meanwhile, so cards and shadows fade together without flicker.
     */
    static void animate(final View layer, final long ms, final Step step, final Runnable end) {
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            long start = -1;
            @Override public void doFrame(long now) {
                if (start < 0) start = now;
                float t = Math.min(1f, (now - start) / (ms * 1e6f));
                step.at(1 - (1 - t) * (1 - t) * (1 - t));
                if (t < 1) { Choreographer.getInstance().postFrameCallback(this); return; }
                layer.setLayerType(View.LAYER_TYPE_NONE, null);
                if (end != null) end.run();
            }
        });
    }

    static void fade(final View v, final float a0, final float a1, final long ms, final Runnable end) {
        v.setAlpha(a0);
        animate(v, ms, e -> v.setAlpha(a0 + (a1 - a0) * e), end);
    }

    static String ago(long t) {
        long d = System.currentTimeMillis() - t;
        if (d < DateUtils.MINUTE_IN_MILLIS) return "Just now";
        return DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
    }

    GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }
}
