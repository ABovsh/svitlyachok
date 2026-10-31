package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The widget as one bitmap (RemoteViews cannot use our fonts). One place from 2 x 1: the bulb,
 * what is scheduled now and until when; with room, what comes after; at 4 x 2, the next 24 hours.
 * Several places: a row each, with a small bar of the next 24 hours when the widget is wide.
 * Only clock times, never "in N minutes": the widget is redrawn at changes, not every minute.
 */
final class WidgetArt {
    private WidgetArt() {}

    /** One place on the widget. */
    static final class Item {
        Logic.Place place;
        String tl;
        Logic.Now now;
    }

    /** What one widget shows. */
    static final class Model {
        List<Item> items = new ArrayList<>();
        boolean many;  // the app follows several places: name the one shown
        LocalDate base;
    }

    /** The places chosen for one widget ("" = all), as they are now. */
    static Model model(Context c, long now, String choice) {
        List<Logic.Place> places = Prefs.places(c);
        if (places.isEmpty()) return null;
        Model m = new Model();
        m.many = places.size() > 1;
        m.base = Logic.today(now);
        for (Logic.Place p : Logic.widgetPlaces(places, choice)) {
            Item it = new Item();
            it.place = p;
            Logic.Data data = Store.data(c, p.region);
            it.tl = Logic.timeline(data, p.group, m.base);
            it.now = Logic.now(it.tl, m.base, now, data != null && data.none);
            if (data == null) {
                it.now.title = "Немає графіка";
                it.now.big = "відкрийте застосунок";
                it.now.then = "";
            }
            m.items.add(it);
        }
        return m;
    }

    /** Same key, same picture: the widget is not redrawn. */
    static String key(Context c, Model m, long now, int wDp, int hDp) {
        StringBuilder b = new StringBuilder().append(wDp).append('x').append(hDp).append('|').append(Ui.pal(c).dark);
        if (m == null) return b.append("|empty").toString();
        boolean bars = bars(m, wDp, hDp);
        b.append('|').append(m.many);
        for (Item it : m.items) {
            Logic.Now n = it.now;
            b.append('|').append(it.place.name).append('|').append(n.state).append('|').append(n.title).append('|')
                    .append(n.big).append('|').append(n.then).append('|').append(n.soon);
            if (bars) b.append('|').append(it.tl);
        }
        if (bars) b.append('|').append(Logic.index(m.base, now));
        return b.toString();
    }

    /** Whether the picture holds bars of the next 24 hours (they move every half hour). */
    private static boolean bars(Model m, int wDp, int hDp) {
        return m.items.size() == 1 ? Logic.widgetForm(wDp, hDp) == Logic.W_FULL : wDp >= 250;
    }

    static String describe(Model m) {
        if (m == null) return "Світлячок: відкрийте застосунок і оберіть чергу";
        StringBuilder b = new StringBuilder("Зараз за графіком");
        for (Item it : m.items) {
            Logic.Now n = it.now;
            b.append(". ").append(it.place.name).append(": ").append(n.title)
                    .append(n.big.isEmpty() ? "" : ", " + n.big).append(n.then.isEmpty() ? "" : ", " + n.then);
        }
        return b.toString();
    }

    /** The size of the picture and its scale; `pad` is the margin inside the rounded card. */
    private static final class Frame {
        final int w;
        final int h;
        final float s;
        final float pad;

        Frame(int w, int h, float s, float pad) {
            this.w = w;
            this.h = h;
            this.s = s;
            this.pad = pad;
        }
    }

    static Bitmap draw(Context c, Model m, long now, int wDp, int hDp) {
        Ui.Pal pal = Ui.pal(c);
        float d = c.getResources().getDisplayMetrics().density;
        float s = Math.min(d, 1100f / wDp);  // keep the bitmap small
        Frame f = new Frame(Math.round(wDp * s), Math.round(hDp * s), s, Math.max(8f, Math.min(14f, hDp * 0.14f)) * s);
        Bitmap bm = Bitmap.createBitmap(f.w, f.h, Bitmap.Config.ARGB_8888);
        Brush b = new Brush(pal, pal.card).on(new Canvas(bm));
        float r = Math.min(24 * s, f.h / 2.6f);
        b.p.setColor((pal.card & 0x00FFFFFF) | 0xF5000000);
        b.r.set(0, 0, f.w, f.h);
        b.cv.drawRoundRect(b.r, r, r, b.p);
        if (m == null) {
            Paint t = text(Ui.bold(c), 13 * s, pal.muted, Paint.Align.CENTER);
            String msg = "Оберіть чергу в Світлячку";
            b.cv.drawText(msg, f.w / 2f, f.h / 2f + 4 * s, fit(t, msg, f.w - 2 * f.pad));
        } else if (m.items.size() > 1) {
            rows(c, b, m, now, f, wDp >= 250);
        } else {
            one(c, b, m, now, f, Logic.widgetForm(wDp, hDp) == Logic.W_FULL);
        }
        return bm;
    }

    /** One place: the bulb and the words; at 4 x 2, the next 24 hours under them. */
    private static void one(Context c, Brush b, Model m, long now, Frame f, boolean full) {
        Item it = m.items.get(0);
        Logic.Now n = it.now;
        float s = f.s;
        float barBlock = full ? 34 * s : 0;  // bar + hour labels
        float rowH = f.h - 2 * f.pad - barBlock;
        float bulb = Math.min(Math.min(rowH * 0.85f, 60 * s), f.w * 0.27f);  // narrow: the words come first
        BulbView.draw(b, n.state, f.pad + bulb / 2f, f.pad + rowH / 2f, bulb, 1f);

        float tx = f.pad + bulb + Math.max(8 * s, bulb * 0.18f);
        float tw = f.w - tx - f.pad * 0.8f;
        float ts = Math.max(12f, Math.min(19f, f.h / s * (full ? 0.15f : 0.24f))) * s;
        // with several places the name comes first: a widget must say whose schedule it shows
        boolean head = m.many && rowH >= 62 * s;
        boolean then = !n.then.isEmpty() && rowH >= (head ? 84 : 62) * s;
        Ui.Pal pal = b.pal;
        Paint ph = fit(text(Ui.bold(c), ts * 0.68f, pal.muted, Paint.Align.LEFT), it.place.name, tw);
        Paint pt = fit(text(Ui.heavy(c), ts, pal.status(n.state), Paint.Align.LEFT), n.title, tw);
        Paint pb = fit(text(Ui.display(c), ts * 1.05f, pal.text, Paint.Align.LEFT), n.big, tw);
        Paint pn = fit(text(Ui.bold(c), ts * 0.68f, n.soon ? pal.maybeText : pal.muted, Paint.Align.LEFT), n.then, tw);
        float g = ts * 0.32f;
        float block = (head ? ph.getTextSize() + g : 0) + pt.getTextSize()
                + (n.big.isEmpty() ? 0 : g + pb.getTextSize()) + (then ? g + pn.getTextSize() : 0);
        float y = f.pad + (rowH - block) / 2f;
        if (head) y = line(b, it.place.name, tx, y, ph) + g;
        y = line(b, n.title, tx, y, pt);
        if (!n.big.isEmpty()) y = line(b, n.big, tx, y + g, pb);
        if (then) line(b, n.then, tx, y + g, pn);
        if (full) {
            float top = f.h - f.pad - barBlock + 8 * s;
            b.r2.set(f.pad, top, f.w - f.pad, top + 11 * s);
            bar(c, b, m.base, it.tl, now, b.r2, s);
        }
    }

    /** A line of text whose top is at y; returns the y under it. */
    private static float line(Brush b, String text, float x, float y, Paint p) {
        b.cv.drawText(text, x, y + p.getTextSize() * 0.8f, p);
        return y + p.getTextSize();
    }

    /** Several places, a row each: a small bulb, the name, the state, until when; a bar when wide. */
    private static void rows(Context c, Brush b, Model m, long now, Frame f, boolean withBars) {
        int k = m.items.size();
        float s = f.s;
        float top = f.pad * 0.8f;
        float rowH = (f.h - 2 * top) / k;
        float ts = Math.max(9f, Math.min(16f, rowH / s * 0.36f)) * s;
        float bulb = Math.min(rowH * 0.8f, 30 * s);
        float textW = withBars ? (f.w - 2 * f.pad) * 0.58f : f.w - 2 * f.pad;
        for (int i = 0; i < k; i++) {
            Item it = m.items.get(i);
            float cy = top + rowH * (i + 0.5f);
            if (i > 0) {
                b.p.setColor(b.pal.line);
                b.cv.drawRect(f.pad, top + rowH * i - 0.5f * s, f.w - f.pad, top + rowH * i + 0.5f * s, b.p);
            }
            BulbView.draw(b, it.now.state, f.pad + bulb / 2f, cy, bulb, 1f);
            float x = f.pad + bulb + 6 * s;
            rowText(c, b, it, new float[] {x, cy, f.pad + textW - x, ts}, rowH >= ts * 2.6f);
            if (withBars) {
                float bx = f.pad + textW + 8 * s;
                float bh = Math.min(10 * s, rowH * 0.3f);
                int cur = Math.max(0, Logic.index(m.base, now));
                float frac = (Logic.z(now).getMinute() % 30) / 30f;
                b.r2.set(bx, cy - bh / 2f, f.w - f.pad, cy + bh / 2f);
                BarView.runs(b, it.tl, cur, 48, frac, b.r2, 1.2f * s);
            }
        }
    }

    /**
     * The words of one row; at = {x, centre y, width, text size}. Name over the state when there
     * is height, side by side otherwise.
     */
    private static void rowText(Context c, Brush b, Item it, float[] at, boolean twoLines) {
        Logic.Now n = it.now;
        float x = at[0];
        float cy = at[1];
        float avail = at[2];
        float ts = at[3];
        String state = n.big.isEmpty() ? n.title : n.title + " · " + n.big;
        Paint pn = text(Ui.bold(c), ts * 0.82f, b.pal.muted, Paint.Align.LEFT);
        Paint pt = text(Ui.heavy(c), ts, b.pal.status(n.state), Paint.Align.LEFT);
        Paint pb = text(Ui.display(c), ts, b.pal.text, Paint.Align.LEFT);
        float sx;
        float sy;
        if (twoLines) {
            fit(pn, it.place.name, avail);
            fit(pt, state, avail);
            float y = cy - (pn.getTextSize() + ts * 0.25f + pt.getTextSize()) / 2f;
            b.cv.drawText(it.place.name, x, y + pn.getTextSize() * 0.8f, pn);
            sx = x;
            sy = y + pn.getTextSize() + ts * 0.25f + pt.getTextSize() * 0.8f;
        } else {
            fit(pn, it.place.name, avail * 0.38f);
            b.cv.drawText(it.place.name, x, cy + pn.getTextSize() * 0.35f, pn);
            fit(pt, state, avail * 0.6f);
            sx = x + avail * 0.4f;
            sy = cy + pt.getTextSize() * 0.35f;
        }
        pb.setTextSize(pt.getTextSize());
        b.cv.drawText(n.title, sx, sy, pt);
        if (!n.big.isEmpty()) b.cv.drawText(" · " + n.big, sx + pt.measureText(n.title), sy, pb);
    }

    /** The next 24 hours from now in `area`, with the hours on the clock under it and midnight marked. */
    private static void bar(Context c, Brush b, LocalDate base, String tl, long now, RectF area, float s) {
        Paint p = b.p;
        float x = area.left;
        float y = area.top;
        float bw = area.width();
        float bh = area.height();
        float sw = bw / 48f;
        int cur = Math.max(0, Logic.index(base, now));
        float frac = (Logic.z(now).getMinute() % 30) / 30f;
        BarView.runs(b, tl, cur, 48, frac, area, 1.5f * s);
        Paint lab = text(Ui.bold(c), 10 * s, b.pal.dim, Paint.Align.CENTER);
        float lh = lab.measureText("00") / 2;
        for (int t = 1; t <= 48; t++) {
            int slot = cur + t;
            float lx = x + (t - frac) * sw;
            if (slot % 12 == 0 && lx >= x + lh && lx <= x + bw - lh) {
                b.cv.drawText(String.format(Locale.ROOT, "%02d", (slot % 48) / 2), lx, y + bh + 13 * s, lab);
            }
        }
        int mid = 48 - (cur % 48);  // slots from now to midnight
        float mx = x + (mid - frac) * sw;
        if (mx > x + 4 * s && mx < x + bw - 4 * s) {
            p.setColor((b.pal.card & 0x00FFFFFF) | 0xFF000000);
            b.cv.drawRect(mx - 1.5f * s, y - 2 * s, mx + 1.5f * s, y + bh + 2 * s, p);
            p.setColor(b.pal.muted);
            b.cv.drawRect(mx - 0.75f * s, y - 2 * s, mx + 0.75f * s, y + bh + 2 * s, p);
        }
    }

    private static Paint text(Typeface tf, float size, int colour, Paint.Align a) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(colour);
        p.setTextAlign(a);
        return p;
    }

    private static Paint fit(Paint p, String s, float max) {
        float wd = p.measureText(s);
        if (wd > max && max > 0) p.setTextSize(p.getTextSize() * max / wd);
        return p;
    }
}
