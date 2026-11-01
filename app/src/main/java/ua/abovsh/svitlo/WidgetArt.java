package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Shader;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The widget as one bitmap (RemoteViews cannot use our fonts). Each widget shows one place: from
 * 2 x 1 the bulb, what is scheduled now and until when; with room, the place's name and what
 * comes after; at 4 x 2, the next 24 hours. Only clock times, never "in N minutes": the widget is
 * redrawn at changes, not every minute.
 */
final class WidgetArt {
    private WidgetArt() {}

    /** What one widget shows: its place, as it is now. */
    static final class Model {
        Logic.Place place;
        boolean many;  // the app follows several places: name the one shown
        boolean gone;  // the place of this widget was removed
        LocalDate base;
        String tl;
        Logic.Now now;
    }

    /** The place chosen for one widget (by id; the first place when none was chosen). */
    static Model model(Context c, long now, String choice) {
        List<Logic.Place> all = Prefs.places(c);
        Logic.Place p = Logic.widgetPlace(all, choice);
        if (p == null) {
            if (all.isEmpty()) return null;
            Model gone = new Model();  // its place was removed: say so rather than show another
            gone.gone = true;
            return gone;
        }
        Model m = new Model();
        m.place = p;
        m.many = Prefs.places(c).size() > 1;
        m.base = Logic.today(now);
        Logic.Data data = Store.data(c, p.region);
        m.tl = Logic.timeline(data, p.group, m.base);
        m.now = Logic.now(m.tl, m.base, now, data != null && data.none);
        if (data == null) {
            m.now.title = "Немає графіка";
            m.now.big = "відкрийте застосунок";
            m.now.then = "";
        }
        return m;
    }

    /** Same key, same picture: the widget is not redrawn. */
    static String key(Context c, Model m, long now, int wDp, int hDp) {
        StringBuilder b = new StringBuilder().append(wDp).append('x').append(hDp).append('|').append(Ui.pal(c).dark);
        if (m == null) return b.append("|empty").toString();
        if (m.gone) return b.append("|gone").toString();
        Logic.Now n = m.now;
        b.append('|').append(m.many).append('|').append(m.place.name).append('|').append(n.state).append('|')
                .append(n.title).append('|').append(n.big).append('|').append(n.then).append('|').append(n.soon);
        if (Logic.widgetForm(wDp, hDp) == Logic.W_FULL) {  // the bar moves every half hour
            b.append('|').append(m.tl).append('|').append(Logic.index(m.base, now)).append('|').append(n.since);
        }
        return b.toString();
    }

    static String describe(Model m) {
        if (m == null) return "Світлячок: відкрийте застосунок і оберіть чергу";
        if (m.gone) return "Світлячок: місце цього віджета видалено. Утримуйте віджет, щоб обрати інше";
        Logic.Now n = m.now;
        return m.place.name + ", зараз за графіком: " + n.title + (n.big.isEmpty() ? "" : ", " + n.big)
                + (n.then.isEmpty() ? "" : ", " + n.then);
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
        b.r.set(0f, 0f, (float) f.w, (float) f.h);
        if (m != null && !m.gone) {
            b.p.setShader(new LinearGradient(0, 0, f.w * .88f, 0,
                    new int[] {pal.soft(m.now.state), pal.card}, null, Shader.TileMode.CLAMP));
        }
        b.cv.drawRoundRect(b.r, r, r, b.p);
        b.p.setShader(null);
        if (m == null) {
            Paint t = text(Ui.bold(c), 13 * s, pal.muted, Paint.Align.CENTER);
            String msg = "Оберіть чергу в Світлячку";
            b.cv.drawText(msg, f.w / 2f, f.h / 2f + 4 * s, fit(t, msg, f.w - 2 * f.pad));
        } else if (m.gone) {
            Paint t = text(Ui.heavy(c), 14 * s, pal.text, Paint.Align.CENTER);
            Paint u = text(Ui.bold(c), 11 * s, pal.muted, Paint.Align.CENTER);
            String a = "Місце видалено";
            String hint = "утримуйте віджет, щоб обрати інше";
            b.cv.drawText(a, f.w / 2f, f.h / 2f - 2 * s, fit(t, a, f.w - 2 * f.pad));
            b.cv.drawText(hint, f.w / 2f, f.h / 2f + 14 * s, fit(u, hint, f.w - 2 * f.pad));
        } else {
            one(c, b, m, now, f, Logic.widgetForm(wDp, hDp) == Logic.W_FULL);
        }
        return bm;
    }

    /** One place: the bulb and the words; at 4 x 2, the next 24 hours under them. */
    private static void one(Context c, Brush b, Model m, long now, Frame f, boolean full) {
        if (full && f.w / f.s >= 300 && f.h / f.s >= 160 && large(c, b, m, now, f)) return;
        Logic.Now n = m.now;
        float s = f.s;
        float barBlock = full ? 34 * s : 0;  // bar + hour labels
        float rowH = f.h - 2 * f.pad - barBlock;
        float bulb = Math.min(Math.min(rowH * 0.85f, 60 * s), f.w * 0.27f);  // narrow: the words come first
        BulbView.draw(b, n.state, f.pad + bulb / 2f, f.pad + rowH / 2f, bulb, 1f);

        float tx = f.pad + bulb + Math.max(8 * s, bulb * 0.18f);
        float tw = f.w - tx - f.pad * 0.8f;
        float ts = Math.max(12f, Math.min(19f, f.h / s * (full ? 0.15f : 0.24f))) * s;
        // with several places the name is always there: a widget must say whose schedule it shows
        boolean head = m.many;
        boolean then = !n.then.isEmpty() && rowH >= (head ? 84 : 62) * s;
        Ui.Pal pal = b.pal;
        Paint ph = fit(text(Ui.heavy(c), ts * 0.8f, Ui.fade(pal.text, 0.8f), Paint.Align.LEFT), m.place.name, tw);
        Paint pt = fit(text(Ui.heavy(c), ts, pal.status(n.state), Paint.Align.LEFT), n.title, tw);
        Paint pb = fit(text(Ui.display(c), ts * 1.05f, pal.text, Paint.Align.LEFT), n.big, tw);
        Paint pn = fit(text(Ui.bold(c), ts * 0.68f, n.soon ? pal.maybeText : pal.muted, Paint.Align.LEFT), n.then, tw);
        float g = ts * 0.32f;
        float block = (head ? ph.getTextSize() + g : 0) + pt.getTextSize()
                + (n.big.isEmpty() ? 0 : g + pb.getTextSize()) + (then ? g + pn.getTextSize() : 0);
        if (block > rowH * 0.94f) {  // a low widget: every line shrinks alike, none is dropped
            float k = rowH * 0.94f / block;
            for (Paint p : new Paint[] {ph, pt, pb, pn}) p.setTextSize(p.getTextSize() * k);
            g *= k;
            block *= k;
        }
        float y = f.pad + (rowH - block) / 2f;
        if (head) y = line(b, m.place.name, tx, y, ph) + g;
        y = line(b, n.title, tx, y, pt);
        if (!n.big.isEmpty()) y = line(b, n.big, tx, y + g, pb);
        if (then) line(b, n.then, tx, y + g, pn);
        if (full) {
            float top = f.h - f.pad - barBlock + 8 * s;
            b.r2.set(f.pad, top, f.w - f.pad, top + 11 * s);
            bar(c, b, m.base, m.tl, now, b.r2, s);
        }
    }

    /** The compact form above is unchanged: the larger form adds two columns and a bar. */
    private static boolean large(Context c, Brush b, Model m, long now, Frame f) {
        Logic.Now n = m.now;
        String[] next = Logic.widgetNext(n);
        if (next[0].isEmpty()) return false;
        float s = f.s;
        float gap = 8 * s;
        float column = (f.w - 2 * f.pad - 10 * s) / 2;
        float bulb = 40 * s;
        float textX = f.pad + bulb + 7 * s;
        float room = column - bulb - 7 * s;
        float start = f.pad + 23 * s;
        float barBlock = 48 * s;
        float rowH = f.h - f.pad - barBlock - start;
        Paint title = text(Ui.heavy(c), 19 * s, b.pal.status(n.state), Paint.Align.LEFT);
        String first = n.title;
        String second = "";
        if (title.measureText(first) > room) {
            int space = first.indexOf(' ');
            if (space > 0) { second = first.substring(space + 1); first = first.substring(0, space); }
        }
        fit(title, first, room);
        if (!second.isEmpty()) fit(title, second, room);
        String clock = n.since.isEmpty() ? n.big : n.since;
        String day = "";
        if (clock.endsWith(" учора")) { day = "учора"; clock = clock.substring(0, clock.length() - 6); }
        else if (clock.startsWith("до завтра ")) { day = "завтра"; clock = "до " + clock.substring(10); }
        Paint time = fit(text(clock.contains(":") ? Ui.display(c) : Ui.bold(c),
                (clock.contains(":") ? 19.95f : 14) * s, b.pal.text, Paint.Align.LEFT), clock, room);
        Paint context = text(Ui.bold(c), 13 * s, b.pal.muted, Paint.Align.LEFT);
        float height = title.getTextSize() * (second.isEmpty() ? 1 : 2) + 5 * s + time.getTextSize()
                + (day.isEmpty() ? 0 : 3 * s + context.getTextSize());
        if (height > rowH || title.getTextSize() < 14 * s || time.getTextSize() < 13 * s) return false;
        Paint name = fit(text(Ui.heavy(c), 15.2f * s, b.pal.text, Paint.Align.LEFT),
                m.place.name + " · черга " + Logic.queue(m.place.group), f.w - 2 * f.pad);
        line(b, m.place.name + " · черга " + Logic.queue(m.place.group), f.pad, f.pad, name);
        BulbView.draw(b, n.state, f.pad + bulb / 2, start + rowH / 2, bulb, 1);
        float y = start + (rowH - height) / 2;
        y = line(b, first, textX, y, title);
        if (!second.isEmpty()) y = line(b, second, textX, y, title);
        y = line(b, clock, textX, y + 5 * s, time);
        if (!day.isEmpty()) line(b, day, textX, y + 3 * s, context);
        float right = f.pad + column + gap;
        float rightW = f.w - f.pad - right;
        Paint event = fit(text(Ui.bold(c), 14 * s, b.pal.muted, Paint.Align.LEFT), next[0], rightW);
        Paint at = fit(text(Ui.display(c), 19.95f * s, b.pal.status(n.next), Paint.Align.LEFT), next[1], rightW);
        Paint until = fit(text(Ui.bold(c), 13 * s, b.pal.muted, Paint.Align.LEFT), next[2], rightW);
        float rightH = event.getTextSize() + 4 * s + at.getTextSize() + (next[2].isEmpty() ? 0 : 3 * s + until.getTextSize());
        float ry = start + (rowH - rightH) / 2;
        b.p.setColor(b.pal.line);
        b.cv.drawRect(right - 6 * s, ry, right - 5 * s, ry + rightH, b.p);
        ry = line(b, next[0], right, ry, event);
        ry = line(b, next[1], right, ry + 4 * s, at);
        if (!next[2].isEmpty()) line(b, next[2], right, ry + 3 * s, until);
        float top = f.h - f.pad - barBlock;
        line(b, "Найближчі 24 години", f.pad, top, text(Ui.bold(c), 12 * s, b.pal.muted, Paint.Align.LEFT));
        b.r2.set(f.pad, top + 18 * s, f.w - f.pad, top + 36 * s);
        bar(c, b, m.base, m.tl, now, b.r2, s);
        return true;
    }

    /** A line of text whose top is at y; returns the y under it. */
    private static float line(Brush b, String text, float x, float y, Paint p) {
        b.cv.drawText(text, x, y + p.getTextSize() * 0.8f, p);
        return y + p.getTextSize();
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
