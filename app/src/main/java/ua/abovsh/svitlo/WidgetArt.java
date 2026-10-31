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

    static Bitmap draw(Context c, Model m, long now, int wDp, int hDp) {
        Ui.Pal pal = Ui.pal(c);
        float d = c.getResources().getDisplayMetrics().density;
        float s = Math.min(d, 1100f / wDp);  // keep the bitmap small
        int w = Math.round(wDp * s), h = Math.round(hDp * s);
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = Math.min(24 * s, h / 2.6f);
        int bg = (pal.card & 0x00FFFFFF) | 0xF5000000;
        p.setColor(bg);
        cv.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
        float pad = Math.max(8, Math.min(14, hDp * 0.14f)) * s;

        if (m == null) {
            Paint t = text(Ui.bold(c), 13 * s, pal.muted, Paint.Align.CENTER);
            String msg = "Оберіть чергу в Світлячку";
            cv.drawText(msg, w / 2f, h / 2f + 4 * s, fit(t, msg, w - 2 * pad));
            return bm;
        }
        if (m.items.size() > 1) {
            rows(c, cv, p, pal, m, now, w, h, pad, s, wDp >= 250);
            return bm;
        }
        Item it = m.items.get(0);
        Logic.Now n = it.now;
        boolean full = Logic.widgetForm(wDp, hDp) == Logic.W_FULL;
        float barBlock = full ? 34 * s : 0;  // bar + hour labels
        float rowH = h - 2 * pad - barBlock;

        // the bulb
        float bulb = Math.min(Math.min(rowH * 0.85f, 60 * s), w * 0.27f);  // narrow: the words come first
        BulbView.draw(cv, p, pal, n.state, pad + bulb / 2f, pad + rowH / 2f, bulb, 1f, pal.card, new BulbView.Scratch());

        // the words
        float tx = pad + bulb + Math.max(8 * s, bulb * 0.18f), tw = w - tx - pad * 0.8f;
        float ts = Math.max(12, Math.min(19, hDp * (full ? 0.15f : 0.24f))) * s;
        // with several places the name comes first: a widget must say whose schedule it shows
        boolean head = m.many && rowH >= 62 * s;
        boolean then = !n.then.isEmpty() && rowH >= (head ? 84 : 62) * s;
        Paint ph = text(Ui.bold(c), ts * 0.68f, pal.muted, Paint.Align.LEFT);
        Paint pt = text(Ui.heavy(c), ts, pal.status(n.state), Paint.Align.LEFT);
        Paint pb = text(Ui.display(c), ts * 1.05f, pal.text, Paint.Align.LEFT);
        Paint pn = text(Ui.bold(c), ts * 0.68f, n.soon ? pal.maybeText : pal.muted, Paint.Align.LEFT);
        fit(pt, n.title, tw);
        fit(pb, n.big, tw);
        fit(ph, it.place.name, tw);
        fit(pn, n.then, tw);
        float g = ts * 0.32f;
        float block = (head ? ph.getTextSize() + g : 0) + pt.getTextSize()
                + (n.big.isEmpty() ? 0 : g + pb.getTextSize()) + (then ? g + pn.getTextSize() : 0);
        float y = pad + (rowH - block) / 2f;
        if (head) {
            cv.drawText(it.place.name, tx, y + ph.getTextSize() * 0.8f, ph);
            y += ph.getTextSize() + g;
        }
        cv.drawText(n.title, tx, y + pt.getTextSize() * 0.8f, pt);
        y += pt.getTextSize();
        if (!n.big.isEmpty()) {
            y += g;
            cv.drawText(n.big, tx, y + pb.getTextSize() * 0.8f, pb);
            y += pb.getTextSize();
        }
        if (then) cv.drawText(n.then, tx, y + g + pn.getTextSize() * 0.8f, pn);

        if (full) bar(c, cv, p, pal, m.base, it.tl, now, pad, h - pad - barBlock + 8 * s, w - 2 * pad, s);
        return bm;
    }

    /** Several places, a row each: a small bulb, the name, the state, until when; a bar when wide. */
    private static void rows(Context c, Canvas cv, Paint p, Ui.Pal pal, Model m, long now, int w, int h, float pad,
            float s, boolean withBars) {
        int k = m.items.size();
        float top = pad * 0.8f, rowH = (h - 2 * top) / k;
        float ts = Math.max(9, Math.min(16, rowH / s * 0.36f)) * s;
        float bulb = Math.min(rowH * 0.8f, 30 * s);
        float textW = withBars ? (w - 2 * pad) * 0.58f : w - 2 * pad;
        for (int i = 0; i < k; i++) {
            Item it = m.items.get(i);
            Logic.Now n = it.now;
            float cy = top + rowH * (i + 0.5f);
            if (i > 0) {
                p.setColor(pal.line);
                cv.drawRect(pad, top + rowH * i - 0.5f * s, w - pad, top + rowH * i + 0.5f * s, p);
            }
            BulbView.draw(cv, p, pal, n.state, pad + bulb / 2f, cy, bulb, 1f, pal.card, new BulbView.Scratch());
            float x = pad + bulb + 6 * s, avail = pad + textW - x;
            Paint pn = text(Ui.bold(c), ts * 0.82f, pal.muted, Paint.Align.LEFT);
            Paint pt = text(Ui.heavy(c), ts, pal.status(n.state), Paint.Align.LEFT);
            Paint pb = text(Ui.display(c), ts * 0.95f, pal.text, Paint.Align.LEFT);
            // name over the state when there is height, else side by side
            boolean twoLines = rowH >= ts * 2.6f;
            String state = n.big.isEmpty() ? n.title : n.title + " · " + n.big;
            if (twoLines) {
                fit(pn, it.place.name, avail);
                Paint ps = text(Ui.heavy(c), ts, pal.status(n.state), Paint.Align.LEFT);
                fit(ps, state, avail);
                float block = pn.getTextSize() + ts * 0.25f + ps.getTextSize();
                float y = cy - block / 2f;
                cv.drawText(it.place.name, x, y + pn.getTextSize() * 0.8f, pn);
                y += pn.getTextSize() + ts * 0.25f;
                cv.drawText(n.title, x, y + ps.getTextSize() * 0.8f, ps);
                if (!n.big.isEmpty()) {
                    pb.setTextSize(ps.getTextSize());
                    cv.drawText(" · " + n.big, x + ps.measureText(n.title), y + ps.getTextSize() * 0.8f, pb);
                }
            } else {
                fit(pn, it.place.name, avail * 0.38f);
                cv.drawText(it.place.name, x, cy + pn.getTextSize() * 0.35f, pn);
                float sx = x + avail * 0.4f, sw = avail * 0.6f;
                fit(pt, state, sw);
                pb.setTextSize(pt.getTextSize());
                cv.drawText(n.title, sx, cy + pt.getTextSize() * 0.35f, pt);
                if (!n.big.isEmpty()) cv.drawText(" · " + n.big, sx + pt.measureText(n.title), cy + pt.getTextSize() * 0.35f, pb);
            }
            if (withBars) {
                float bx = pad + textW + 8 * s, bw = w - pad - bx, bh = Math.min(10 * s, rowH * 0.3f);
                int cur = Math.max(0, Logic.index(m.base, now));
                float frac = (Logic.z(now).getMinute() % 30) / 30f;
                BarView.runs(cv, p, pal, it.tl, cur, 48, frac, new RectF(bx, cy - bh / 2f, bx + bw, cy + bh / 2f), 1.2f * s);
            }
        }
    }

    /** The next 24 hours from now, with the hours on the clock under it and midnight marked. */
    private static void bar(Context c, Canvas cv, Paint p, Ui.Pal pal, LocalDate base, String tl, long now, float x, float y,
            float bw, float s) {
        float bh = 11 * s, sw = bw / 48f;
        int cur = Math.max(0, Logic.index(base, now));
        float frac = (Logic.z(now).getMinute() % 30) / 30f;
        BarView.runs(cv, p, pal, tl, cur, 48, frac, new RectF(x, y, x + bw, y + bh), 1.5f * s);
        Paint lab = text(Ui.bold(c), 10 * s, pal.dim, Paint.Align.CENTER);
        for (int t = 1; t <= 48; t++) {
            int slot = cur + t;
            if (slot % 12 != 0) continue;
            float lx = x + (t - frac) * sw, lh = lab.measureText("00") / 2;
            if (lx < x + lh || lx > x + bw - lh) continue;
            cv.drawText(String.format(Locale.ROOT, "%02d", (slot % 48) / 2), lx, y + bh + 13 * s, lab);
        }
        int mid = 48 - (cur % 48);  // slots from now to midnight
        float mx = x + (mid - frac) * sw;
        if (mx > x + 4 * s && mx < x + bw - 4 * s) {
            p.setColor((pal.card & 0x00FFFFFF) | 0xFF000000);
            cv.drawRect(mx - 1.5f * s, y - 2 * s, mx + 1.5f * s, y + bh + 2 * s, p);
            p.setColor(pal.muted);
            cv.drawRect(mx - 0.75f * s, y - 2 * s, mx + 0.75f * s, y + bh + 2 * s, p);
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
