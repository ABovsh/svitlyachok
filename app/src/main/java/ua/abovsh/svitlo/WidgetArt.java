package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.time.LocalDate;
import java.util.List;

/**
 * The widget as one bitmap (RemoteViews cannot use our fonts). Each widget shows one place: at
 * 2 x 1 the bulb and the coming event; at 4 x 2
 * both halves, today's light and the bars of the days. Only clock times, never "in N minutes": the widget is
 * redrawn at changes, not every minute.
 */
final class WidgetArt {
    private static final float SECTION_HEAD = .33f;

    private WidgetArt() {}

    /** What one widget shows: its place, as it is now. */
    static final class Model {
        Logic.Place place;
        boolean many;  // the app follows several places: name the one shown
        boolean stale;
        boolean gone;  // the place of this widget was removed
        LocalDate base;
        String tl;
        Logic.Now now;
        String lastDay = "";  // the last day with a schedule, for a widget without one
        boolean ahead;  // tomorrow is published while today is not
        boolean lit;  // the event is close: its half is filled with its colour, as on the card
        int notice = Logic.NOTICE_NONE;  // the oblenergo's notice of the region: a sign in a free corner
        boolean waiting;  // no schedule of today yet: the firefly waits for it instead of an empty card
        long checked;  // the last check of the source, said while waiting
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
        m.stale = !dataFresh(c, p, data, now);
        m.tl = Logic.timeline(data, p.group, m.base);
        m.lastDay = Logic.lastScheduleDay(data, m.base);
        m.ahead = Logic.scheduleAhead(data, p.group, m.base);
        m.now = Logic.nowWithHistory(m.tl, m.base, now, data != null && data.none, Prefs.knownHistory(c, p, data));
        if (data == null) {
            m.now.title = "Немає графіка";
            m.now.big = "відкрийте застосунок";
            m.now.then = "";
        }
        m.lit = Logic.highlight(m.now, Prefs.alerts(c, p.id), now);
        m.notice = data != null && Logic.noticeLevel(data.notice) == Logic.NOTICE_EMERGENCY
                ? Logic.NOTICE_EMERGENCY : Logic.NOTICE_NONE;
        m.waiting = Logic.waitingForToday(data, p.group, m.base);
        m.checked = m.waiting ? Store.checkedAt(c, p.region) : 0;
        return m;
    }

    private static boolean dataFresh(Context c, Logic.Place p, Logic.Data d, long now) {
        long checked = Store.checkedAt(c, p.region);
        // a site that does not answer leaves its last schedule standing: that alone is no warning
        return d != null && checked > 0 && now - checked <= Logic.STALE_S
                && (d.sourceTime <= 0 || now - d.sourceTime <= 48 * 3600L || Logic.currentData(d, now));
    }

    private static String widgetName(Model m) { return (m.stale ? "⚠ " : "") + m.place.name; }

    /** Same key, same picture: the widget is not redrawn. */
    static String key(Context c, Model m, long now, int wDp, int hDp) {
        StringBuilder b = new StringBuilder().append(wDp).append('x').append(hDp).append('|').append(Ui.pal(c).dark);
        if (m == null) return b.append("|empty").toString();
        if (m.gone) return b.append("|gone").toString();
        Logic.Now n = m.now;
        b.append('|').append(m.stale).append('|').append(m.many).append('|').append(m.place.name).append('|').append(m.place.group).append('|').append(n.state).append('|')
                .append(n.title).append('|').append(n.big).append('|').append(n.then).append('|').append(n.soon).append('|').append(m.lit).append('|').append(m.lastDay).append('|').append(m.ahead).append('|').append(m.notice)
                .append('|').append(m.waiting).append('|').append(m.checked > 0 ? Logic.hm(m.checked) : "");
        b.append('|').append(n.hint).append('|').append(Logic.dayPercent(dayOf(m.tl, 0))).append('|').append(n.next).append('|').append(n.change);
        if (Logic.widgetForm(wDp, hDp) != Logic.W_ROW) {  // the bar moves every half hour
            b.append('|').append(m.tl).append('|').append(Logic.index(m.base, now)).append('|').append(n.since);
        }
        return b.toString();
    }

    static String describe(Model m) {
        if (m == null) return "Світлячок: відкрийте застосунок і оберіть чергу";
        if (m.gone) return "Світлячок: місце цього віджета видалено. Утримуйте віджет, щоб обрати інше";
        Logic.Now n = m.now;
        if (m.waiting) {
            String line = Logic.waitingLine(m.lastDay, m.ahead);
            return m.place.name + ": графіка на сьогодні ще немає" + (line.isEmpty() ? "" : ", " + line);
        }
        String notice = "";
        if (m.notice == Logic.NOTICE_EMERGENCY) notice = "Екстрені відключення в області. ";
        return notice + (m.stale ? "Дані можуть бути застарілі. " : "") + m.place.name + ", зараз за графіком: " + n.title + (n.since.isEmpty() ? "" : ", " + n.since) + (n.big.isEmpty() ? "" : ", " + n.big)
                + (n.then.isEmpty() ? "" : ", " + n.then) + ". " + Logic.widgetTomorrow(dayOf(m.tl, 48))[0];
    }

    /** No schedule of today: the firefly in a warm glow says so, calmly, with what there is instead. */
    private static final class Waiting {
        private static final String NONE_YET = "Графіка ще немає";

        private Waiting() {}

        static void draw(Context c, Brush b, Model m, Frame f, int form) {
            // the glow stays inside the rounded card: its corners are the launcher's
            float corner = Math.min(24 * f.s, f.h / 2.6f);
            b.path.reset();
            b.r.set(0f, 0f, (float) f.w, (float) f.h);
            b.path.addRoundRect(b.r, corner, corner, android.graphics.Path.Direction.CW);
            b.cv.save();
            b.cv.clipPath(b.path);
            if (form == Logic.W_FULL) large(c, b, m, f);
            else small(c, b, m, f);
            b.cv.restore();
        }

        /** The app's own mark: the firefly on its gold square, in a soft glow of the same gold. */
        private static void logo(Context c, Brush b, float cx, float cy, float size) {
            float glow = size * 1.35f;
            b.p.setShader(new android.graphics.RadialGradient(cx, cy, glow,
                    new int[] {Ui.fade(b.pal.accent, b.pal.dark ? .42f : .5f), Ui.fade(b.pal.accent, 0f)}, null, Shader.TileMode.CLAMP));
            b.cv.drawCircle(cx, cy, glow, b.p);
            float h = size / 2f;
            b.p.setShader(new LinearGradient(cx - h, cy - h, cx + h, cy + h, 0xFFFFDD7A, 0xFFF5A800, Shader.TileMode.CLAMP));
            b.r.set(cx - h, cy - h, cx + h, cy + h);
            b.cv.drawRoundRect(b.r, size * .3f, size * .3f, b.p);
            b.p.setShader(null);
            android.graphics.drawable.Drawable mark = c.getDrawable(R.drawable.ic_logo);
            if (mark == null) return;
            int inset = Math.round(size * .14f);
            mark.setBounds(Math.round(cx - h) + inset, Math.round(cy - h) + inset, Math.round(cx + h) - inset, Math.round(cy + h) - inset);
            mark.draw(b.cv);
        }

        private static void small(Context c, Brush b, Model m, Frame f) {
            float s = f.s;
            float pad = f.h * .13f;
            float rowH = f.h - 2 * pad;
            float left = Logic.widgetBulbSegment(Math.round(f.w / s)) * s;
            logo(c, b, left / 2 + 2.5f * s, f.h / 2f, Math.min(rowH * .62f, left * .62f));
            b.p.setColor(Ui.fade(b.pal.text, .18f));
            b.cv.drawRect(left + 3 * s, pad, left + 4 * s, f.h - pad, b.p);
            float tx = left + 11 * s;
            float tw = f.w - tx - pad * .8f;
            Paint main = text(Ui.heavy(c), rowH * .22f, b.pal.accentText, Paint.Align.LEFT);
            if (!Logic.widgetSmallWords(rowH / s)) {  // too low for three lines: the words that matter
                String only = NONE_YET;
                fit(main, only, tw).setTextSize(Math.min(main.getTextSize(), rowH * .42f));
                b.cv.drawText(only, tx, f.h / 2f - (main.ascent() + main.descent()) / 2, main);
                return;
            }
            Paint name = text(Ui.heavy(c), rowH * .27f, b.pal.text, Paint.Align.LEFT);
            String place = readable(name, widgetName(m), tw, 9.5f * s);
            fit(main, NONE_YET, tw);
            String line = Logic.waitingLine(m.lastDay, m.ahead);
            Paint detail = fit(text(Ui.bold(c), rowH * .19f, b.pal.muted, Paint.Align.LEFT), line, tw);
            float gap = rowH * .08f;
            float block = -name.ascent() + gap + -main.ascent() + (line.isEmpty() ? 0 : gap - detail.ascent());
            float y = (f.h - block) / 2f - name.ascent();
            b.cv.drawText(place, tx, y, name);
            y += gap - main.ascent();
            b.cv.drawText(NONE_YET, tx, y, main);
            if (!line.isEmpty()) b.cv.drawText(line, tx, y + gap - detail.ascent(), detail);
        }

        private static void large(Context c, Brush b, Model m, Frame f) {
            float s = f.s;
            float pad = 12 * s;
            float hDp = f.h / s;
            float size = Math.min(48, hDp * .27f) * s;
            logo(c, b, pad + size / 2, pad + size / 2, size);
            float tx = pad + size + 12 * s;
            float tw = f.w - tx - pad - signRoom(m, s);
            Paint where = fit(text(Ui.bold(c), 12.5f * s, b.pal.muted, Paint.Align.LEFT), Logic.withQueue(m.place, widgetName(m)), tw);
            float y = pad - where.ascent();
            b.cv.drawText(Logic.withQueue(m.place, widgetName(m)), tx, y, where);
            // the main words in the display face: one line when they fit large enough, two otherwise
            String whole = "Графік ще не опубліковано";
            Paint title = text(Ui.display(c), 20 * s, b.pal.text, Paint.Align.LEFT);
            // a tall widget has room for the words in two large lines; a low one keeps them on one
            boolean oneLine = hDp < 140 ? sizeToFit(title, whole, tw) >= 15 * s : sizeToFit(title, whole, tw) >= 22 * s;
            if (!oneLine) title.setTextSize(Math.min(26, hDp * .14f) * s);
            if (oneLine) {
                fit(title, whole, tw);
                y += 6 * s - title.ascent();
                b.cv.drawText(whole, tx, y, title);
            } else {
                fit(title, "опубліковано", tw);
                y += 5 * s - title.ascent();
                b.cv.drawText("Графік ще не", tx, y, title);
                y += title.getFontSpacing() * .92f;
                b.cv.drawText("опубліковано", tx, y, title);
            }
            String detail = Logic.waitingDetail(m.lastDay, m.ahead, m.checked);
            Paint small = fit(text(Ui.bold(c), 12 * s, b.pal.muted, Paint.Align.LEFT), detail, f.w - 2 * pad);
            float detailY = Math.max(y + 16 * s, pad + size + 18 * s) - small.ascent();
            b.cv.drawText(detail, pad, detailY, small);
            float cellsTop = detailY + 10 * s;
            float cellsBottom = f.h - pad - 16 * s;
            if (cellsBottom - cellsTop < 14 * s) return;  // a low widget: no room for the row of the day
            float cell = (f.w - 2 * pad) / 24f;
            b.p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 24; i++) {
                b.r.set(pad + i * cell + .8f * s, cellsTop, pad + (i + 1) * cell - .8f * s, Math.min(cellsBottom, cellsTop + 26 * s));
                b.p.setColor(Ui.blend(b.pal.card, Ui.fade(b.pal.line, .35f)));
                b.cv.drawRoundRect(b.r, 4 * s, 4 * s, b.p);
            }
            Paint label = text(Ui.heavy(c), 11 * s, b.pal.muted, Paint.Align.LEFT);
            b.cv.drawText("Сьогодні · 00–23", pad, Math.min(cellsBottom, cellsTop + 26 * s) + 14 * s, label);
        }
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
        boolean live = m != null && !m.gone;
        int form = Logic.widgetForm(wDp, hDp);
        boolean halves = form != Logic.W_ROW;
        boolean colours = live && !m.waiting;
        int left = colours ? pal.soft(m.now.state) : pal.card;
        int right = colours ? pal.soft(Logic.widgetRightState(m.now, future(m)[1])) : pal.card;
        float centre = halves ? .5f : (Logic.widgetBulbSegment(wDp) + 3.5f) / wDp;
        Brush b = new Brush(pal, Logic.widgetGlow(left, right, pal.card, centre, halves ? .06f : centre * .5f)).on(new Canvas(bm));
        float r = Math.min(24 * s, f.h / 2.6f);
        b.p.setShader(glow(f.w, left, right, pal.card, centre));
        b.p.setDither(true);
        b.r.set(0f, 0f, (float) f.w, (float) f.h);
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
            if (m.waiting) Waiting.draw(c, b, m, f, form);
            else one(c, b, m, now, f, form);
            if (m.notice != Logic.NOTICE_NONE) {
                float[] at = signAt(form, (float) f.w, s, m.notice);
                Paint sign = new Paint(Paint.ANTI_ALIAS_FLAG);
                sign.setTypeface(Ui.heavy(c));
                Notices.sign(b.cv, sign, pal, m.notice, at, pal.card);
            }
        }
        return bm;
    }

    /** Centre and radius of the sign: above the bulb of a small widget, right of the name of a large one. */
    static float[] signAt(int form, float width, float s, int level) {
        float r = (level == Logic.NOTICE_EMERGENCY ? 8.5f : 6.5f) * s;
        float inset = 3.5f * s + r;
        return new float[] {form == Logic.W_FULL ? width - inset : inset, inset, r};
    }

    /** The room the sign of a large widget takes from its name. */
    private static float signRoom(Model m, float s) {
        if (m.notice == Logic.NOTICE_NONE) return 0;
        return (m.notice == Logic.NOTICE_EMERGENCY ? 24 : 20) * s;
    }

    /** What is now glows from the left edge, what comes next from the right, the card between them. */
    private static Shader glow(int width, int left, int right, int middle, float centre) {
        int stops = 17;
        int[] colours = new int[stops];
        float[] at = new float[stops];
        for (int i = 0; i < stops; i++) {
            at[i] = i / (stops - 1f);
            colours[i] = Logic.widgetGlow(left, right, middle, centre, at[i]);
        }
        return new LinearGradient(0, 0, (float) width, 0, colours, at, Shader.TileMode.CLAMP);
    }

    /** Two segments, as the large widget has: on the left what is now, on the right the place and what comes next. */
    private static void one(Context c, Brush b, Model m, long now, Frame f, int form) {
        if (form == Logic.W_FULL) { large(c, b, m, now, f); return; }
        Logic.Now n = m.now;
        float s = f.s;
        Ui.Pal pal = b.pal;
        float pad = f.h * .13f;
        float rowH = f.h - 2 * pad;
        float left = Logic.widgetBulbSegment(Math.round(f.w / s)) * s;
        String[] next = future(m);

        if (m.lit) litBehind(b, n, f, left, pad);
        int lit = onFill(pal, n.next);
        float tx = left + 11 * s;
        float tw = f.w - tx - pad * .8f;
        boolean clock = next[1].contains(":");
        if (!Logic.widgetSmallWords(rowH / s)) {  // too low for words that read: the bulb and the clock alone
            bulbAndClock(c, b, m, f, new float[] {left, pad, rowH, tx, tw}, next[1]);
            return;
        }
        Paint value = valuePaint(c, b.pal, m, next[1]);
        float[] sized = Logic.widgetRowSizes(rowH, clock ? sizeToFit(value, next[1], tw) : Float.MAX_VALUE);
        value.setTextSize(clock ? sized[2] : sized[0] * 1.2f);
        fit(value, next[1], tw);
        String percent = Logic.dayPercent(dayOf(m.tl, 0));
        Paint share = text(Ui.heavy(c), sized[0] * .8f, m.lit ? lit : pal.muted, Paint.Align.RIGHT);
        Paint name = text(Ui.heavy(c), sized[0], m.lit ? lit : pal.text, Paint.Align.LEFT);
        String place = readable(name, widgetName(m), Math.max(1, tw - share.measureText(percent) - 4 * s), 9.5f * s);
        int eventInk = pal.status(Logic.futureTitleState(n));
        Paint event = fit(text(Ui.heavy(c), sized[1], m.lit ? lit : eventInk, Paint.Align.LEFT), next[0], tw);
        float block = Logic.widgetRowBlock(sized);
        float y = (f.h - block) / 2f;
        smallNow(c, b, n, new float[] {left, y, y + block, sized[1], s});
        float nameBaseline = capLine(b, place, tx, y, name);
        b.cv.drawText(percent, f.w - pad * .8f, nameBaseline, share);
        float eventTop = clock ? .74f * sized[0] + sized[3]
                : Logic.widgetEventTop(block, name.getTextSize(), event.getTextSize(), value.getTextSize());
        capLine(b, next[0], tx, y + eventTop, event);
        b.cv.drawText(next[1], tx, y + block, value);
        if (!m.lit) smallLine(b, n, now, new float[] {tx, f.w - pad * .8f, f.h - Math.max(pad * .45f, 3.5f * s), s});
    }

    /** The fill of what comes, behind the right segment, when it comes soon and a reminder is due. */
    private static void litBehind(Brush b, Logic.Now n, Frame f, float left, float pad) {
        b.p.setColor(b.pal.fill(n.next));
        b.r2.set(left + 5 * f.s, pad * .45f, f.w - pad * .45f, f.h - pad * .45f);
        b.cv.drawRoundRect(b.r2, 12 * f.s, 12 * f.s, b.p);
    }

    /** What comes next, in the colour of that state, or on its fill when it comes soon. */
    private static Paint valuePaint(Context c, Ui.Pal pal, Model m, String value) {
        Logic.Now n = m.now;
        boolean clock = value.contains(":");
        int ink = m.lit ? onFill(pal, n.next) : pal.status(Logic.widgetRightState(n, value));
        return text(clock ? Ui.display(c) : Ui.heavy(c), 100, ink, Paint.Align.LEFT);
    }

    /** A long name is cut short, never shrunk past `least`: below it the words no longer read. */
    private static String readable(Paint p, String words, float room, float least) {
        if (sizeToFit(p, words, room) >= least) {
            fit(p, words, room);
            return words;
        }
        p.setTextSize(Math.min(p.getTextSize(), least));
        return android.text.TextUtils.ellipsize(words, new android.text.TextPaint(p), room, android.text.TextUtils.TruncateAt.END).toString();
    }

    /** box: segment width, padding, row height, left and width of the text. The bulb, and what comes next as the clock. */
    private static void bulbAndClock(Context c, Brush b, Model m, Frame f, float[] box, String value) {
        Logic.Now n = m.now;
        Ui.Pal pal = b.pal;
        float left = box[0];
        float pad = box[1];
        float rowH = box[2];
        float half = f.h / 2f;
        BulbView.plain(b, n.state, left / 2 + 2.5f * f.s, half, Math.min(rowH * .95f, left * .9f));
        b.p.setColor(Ui.fade(pal.text, .18f));
        b.cv.drawRect(left + 3 * f.s, pad, left + 4 * f.s, f.h - pad, b.p);
        boolean clock = value.contains(":");
        int ink = m.lit ? onFill(pal, n.next) : pal.status(Logic.widgetRightState(n, value));
        Paint only = fit(text(clock ? Ui.display(c) : Ui.heavy(c), rowH * .62f, ink, Paint.Align.CENTER), value, box[4]);
        b.cv.drawText(value, box[3] + box[4] / 2, half - (only.ascent() + only.descent()) / 2, only);
    }

    /** line: left, right, centre and scale. How much of the state now on has gone, along the bottom, as on the card. */
    private static void smallLine(Brush b, Logic.Now n, long now, float[] line) {
        float done = Logic.progress(n, now);
        if (done < 0) return;
        float s = line[3];
        float h = 2.2f * s;
        float cy = line[2];
        b.p.setColor(Ui.fade(b.pal.fill(n.state), .18f));
        b.r2.set(line[0], cy - h / 2, line[1], cy + h / 2);
        b.cv.drawRoundRect(b.r2, h / 2, h / 2, b.p);
        float at = line[0] + (line[1] - line[0]) * done;
        b.p.setColor(b.pal.fill(n.state));
        b.r2.right = Math.max(line[0] + h, at);
        b.cv.drawRoundRect(b.r2, h / 2, h / 2, b.p);
        b.p.setColor(Ui.Pal.MARKER_EDGE);
        b.cv.drawCircle(at, cy, h * 1.3f + .8f * s, b.p);
        b.p.setColor(Ui.Pal.MARKER);
        b.cv.drawCircle(at, cy, h * 1.3f, b.p);
    }

    /** box: segment width, top and bottom of the lines beside it, size of the event, scale. The word stands on the clock's line. */
    private static void smallNow(Context c, Brush b, Logic.Now n, float[] box) {
        int state = n.state;
        float s = box[4];
        float left = box[0];
        float centre = left / 2 + 2.5f * s;
        Paint word = text(Ui.heavy(c), box[3] * .92f, b.pal.status(state), Paint.Align.CENTER);
        String full = n.afterOff ? n.title : Logic.title(state);  // the app's words while they stay readable, else the short ones
        String words = Logic.stateWords(full, state, sizeToFit(word, full, left - 11 * s) >= box[3] * .92f * .8f);
        fit(word, words, left - 11 * s);
        float room = box[2] - .74f * word.getTextSize() - box[1] - 5 * s;
        BulbView.plain(b, state, centre, box[1] + room / 2, Math.min(room / .66f, left * .92f));
        b.cv.drawText(words, centre, box[2], word);
        b.p.setColor(Ui.fade(b.pal.text, .18f));
        b.cv.drawRect(left + 3 * s, box[1], left + 4 * s, box[2], b.p);
    }

    /** What comes next, for both forms of the widget: a day of no schedule names the last one it knows. */
    private static String[] future(Model m) {
        if (m.now.big.equals("відкрийте застосунок")) return new String[] {m.now.title, m.now.big, ""};
        String[] unknown = Logic.unknownFuture(m.now, m.lastDay, m.ahead);
        return unknown.length > 0 ? unknown : Logic.widgetFuture(m.now);
    }

    /** A line whose capitals start at y, measured by the size it was meant to have; returns its baseline. */
    private static float capLine(Brush b, String words, float x, float y, Paint p) {
        float baseline = y + p.getTextSize() * .74f;
        b.cv.drawText(words, x, baseline, p);
        return baseline;
    }

    /** Words on the fill of a state, as on the card of the main screen. */
    private static int onFill(Ui.Pal pal, int state) {
        return state == Logic.MAYBE ? pal.accentInk : pal.ink;
    }

    /** One full day, or the afternoon and next morning once tomorrow is published. */
    private static void large(Context c, Brush b, Model m, long now, Frame f) {
        float s = f.s;
        float pad = 8 * s;
        float width = f.w - 2 * pad;
        int hourSlot = Logic.index(m.base, now);
        boolean split = Logic.widgetSplitTomorrow(Math.round(f.h / s), dayOf(m.tl, 48), hourSlot);
        boolean oneRow = Logic.widgetOneRow(Math.round(f.h / s), split);  // two rows would not read: now's half
        float[] layout = Logic.widgetGridLayout(Math.round(f.h / s), split, oneRow);
        header(c, b, m, new float[] {pad, pad, width - signRoom(m, s), 13 * s});
        new Panes(c, b, m, future(m), new float[] {pad, layout[0] * s, (width - 8 * s) / 2, layout[1] * s, s}, .30f).at(now).draw();
        float first = (layout[0] + layout[1]) * s;
        int half = split || (oneRow && hourSlot >= 24) ? 12 : 0;
        dayGrid(c, b, new GridDay(dayOf(m.tl, 0), false, split || oneRow, (float) hourSlot, half),
                new RectF(pad, first, f.w - pad, first + layout[3] * s), s);
        if (split) {
            dayGrid(c, b, new GridDay(dayOf(m.tl, 48), true, true, -1, 0),
                    new RectF(pad, first + layout[3] * s, f.w - pad, f.h - pad), s);
        }
    }

    private static final class GridDay {
        final String slots;
        final String title;
        final boolean partial;
        final float now;
        final int firstHour;

        GridDay(String slots, boolean tomorrow, boolean partial, float now, int firstHour) {
            this.slots = slots;
            this.title = partial ? Logic.widgetHalfTitle(tomorrow, firstHour) : Logic.widgetDayTitle(false, false);
            this.partial = partial;
            this.now = now;
            this.firstHour = partial ? firstHour : 0;
        }
    }

    private static void dayGrid(Context c, Brush b, GridDay day, RectF box, float s) {
        boolean partial = day.partial;
        float font = (partial ? 10 : 11) * s;
        Paint label = text(Ui.heavy(c), font, b.pal.text, Paint.Align.LEFT);
        String total = day.slots == null ? "графіка ще немає" : Logic.daySummaries(day.slots).get(2);
        Paint side = text(Ui.bold(c), font, day.slots == null ? b.pal.muted : b.pal.onText, Paint.Align.RIGHT);
        float room = box.width() - label.measureText(day.title) - 5 * s;
        fit(side, total, room);
        float baseline = box.top + (partial ? 11 : 12) * s;
        b.cv.drawText(day.title, box.left, baseline, label);
        b.cv.drawText(total, box.right, baseline, side);
        if (day.slots == null) return;
        RectF cells = new RectF(box.left, box.top + (partial ? 14 : 16) * s, box.right, box.bottom - 2 * s);
        Paint hours = text(Ui.heavy(c), 13 * s, b.pal.text, Paint.Align.CENTER);
        if (partial) BarView.row(b, day.slots, day.now, cells, hours, day.firstHour);
        else BarView.grid(b, day.slots, day.now, cells, hours, -1, -1);
    }

    private static String dayOf(String tl, int from) {
        String day = tl.substring(from, from + 48);
        return day.replace("2", "").isEmpty() ? null : day;
    }

    /** The place and queue; the daily summary appears only beside its day. */
    private static void header(Context c, Brush b, Model m, float[] box) {
        String title = Logic.withQueue(m.place, widgetName(m));
        Paint name = fit(text(Ui.heavy(c), box[3], b.pal.text, Paint.Align.LEFT), title, box[2]);
        b.cv.drawText(title, box[0], box[1] - name.ascent(), name);
    }

    /** The largest size at which `words` fit `width`. */
    private static float sizeToFit(Paint p, String words, float width) {
        if (words.isEmpty()) return Float.MAX_VALUE;
        return p.getTextSize() * width / p.measureText(words);
    }

    /** The two halves, box = x, y, column width, height, scale: first the largest sizes that fit, then the drawing. Both columns share their rows, so the clocks stand on one line. */
    private static final class Panes {
        private final Brush b;
        private final Logic.Now now;
        private final String[] start;
        private final String[] next;
        private final float x;
        private final float top;
        private final float width;
        private final float room;
        private final float s;
        private final float gapX;
        private final Paint small;
        private final boolean inline;
        private final Paint title;
        private final Paint label;
        private final Paint left;
        private final Paint right;
        private final float leftSpare;
        private final float rightSpare;
        private final boolean lit;
        private final Paint smallRight;
        private final float reserve;   // what each clock gives up beside the divider for the arrow
        private final float arrowR;
        private final float[] clockEnds = new float[2];  // where the left clock ends and the right one starts
        private final android.graphics.Rect glyph = new android.graphics.Rect();
        private float done = -1;

        Panes(Context c, Brush b, Model m, String[] next, float[] box, float headShare) {
            this.b = b;
            lit = m.lit;
            this.now = m.now;
            this.next = next;
            start = Logic.widgetStart(m.now);
            x = box[0];
            top = box[1];
            width = box[2];
            room = box[3];
            s = box[4];
            gapX = 6 * s;
            float detail = Math.max(12 * s, Math.min(16 * s, room * .2f));
            small = text(Ui.bold(c), detail, b.pal.muted, Paint.Align.LEFT);
            smallRight = text(Ui.bold(c), detail, lit ? onFill(b.pal, m.now.next) : b.pal.muted, Paint.Align.LEFT);
            leftSpare = spare(start[1]);
            rightSpare = spare(next[2]);
            float[] arrow = lit ? new float[] {0, 0} : Logic.paneArrow(s, room);
            reserve = arrow[0];
            arrowR = arrow[1];
            boolean leftClock = start[0].contains(":");
            boolean rightClock = next[1].contains(":");
            Paint clock = text(Ui.display(c), 100, b.pal.text, Paint.Align.LEFT);
            float beside = Math.min(room * .54f, Math.min(clockSize(clock, leftClock, start[0], leftSpare + reserve),
                    clockSize(clock, rightClock, next[1], rightSpare + reserve)));
            float stacked = Math.min(room * .42f, Math.min(clockSize(clock, leftClock, start[0], reserve),
                    clockSize(clock, rightClock, next[1], reserve)));
            inline = leftSpare + rightSpare == 0 || beside >= stacked || headShare == SECTION_HEAD;  // the card's own order
            float value = inline ? beside : stacked;
            float head = Math.min(value * .8f, room * (inline ? headShare : .24f));
            title = text(Ui.heavy(c), head, b.pal.status(now.state), Paint.Align.LEFT);
            int labelInk = b.pal.status(Logic.futureTitleState(m.now));
            label = text(Ui.heavy(c), head, lit ? onFill(b.pal, m.now.next) : labelInk, Paint.Align.LEFT);
            left = text(leftClock ? Ui.display(c) : Ui.bold(c), leftClock ? value : head, b.pal.status(now.state), Paint.Align.LEFT);
            right = text(rightClock ? Ui.display(c) : Ui.bold(c), rightClock ? value : head, lit ? onFill(b.pal, m.now.next) : b.pal.status(Logic.widgetRightState(now, next[1])), Paint.Align.LEFT);
        }

        /** The moment drawn, for the line of the interval. */
        Panes at(long time) {
            done = Logic.progress(now, time);
            return this;
        }

        private float spare(String words) {
            return words.isEmpty() ? 0 : small.measureText(words) + gapX;
        }

        /** The size at which a clock fits beside `spare`; words that are no clock do not limit it. */
        private float clockSize(Paint clock, boolean isClock, String words, float spare) {
            return isClock ? sizeToFit(clock, words, Math.max(1, width - spare)) : Float.MAX_VALUE;
        }

        void draw() {
            float tall = Math.max(height(left), height(right));
            float first = height(title);
            float third = inline ? 0 : height(small);
            // the line of the interval: in the free third row on the left, else a little room under the clocks
            float line = done >= 0 ? Math.max(2.4f * s, Math.min(4 * s, room * .045f)) : 0;
            boolean underClocks = line > 0 && (third == 0 || leftSpare > 0);
            float knob = line * 1.15f + .8f * s;  // the mark of now: under the clock it needs its whole height free
            float used = first + tall + third + (underClocks ? 2 * knob + 2 * s : 0);
            if (used > room) {  // never past the box: everything shrinks alike
                float k = room / used;
                for (Paint p : new Paint[] {title, label, left, right, small, smallRight}) p.setTextSize(p.getTextSize() * k);
                first *= k;
                tall *= k;
                third *= k;
                used = room;
            }
            float spare = room - used;
            int gaps = third > 0 ? 2 : 1;
            float gap = Math.min(spare / (gaps + 2), first * .35f);
            float y = top + (spare - gap * gaps) / 2;
            float rightX = x + width + 8 * s;
            if (lit) {
                b.p.setColor(b.pal.fill(now.next));
                b.r2.set(rightX - 5 * s, top - s, x + 2 * width + 8 * s + 4 * s, top + room + s);
                b.cv.drawRoundRect(b.r2, 12 * s, 12 * s, b.p);
            }
            firstRow(y, first, rightX);
            y += first + gap;
            float baseline = y + tall - Math.max(left.descent(), right.descent());
            clocks(baseline, rightX);
            if (third > 0) details(y + tall + gap, rightX);
            if (line > 0) {
                float cy = underClocks ? baseline + knob + 1.5f * s : y + tall + gap + third / 2;
                progress(cy, line);
            }
            if (!lit) divider(rightX, baseline);
        }

        /** The line between the halves and, between the clocks as in the app, the arrow where they leave it room. */
        private void divider(float rightX, float baseline) {
            b.p.setColor(b.pal.line);
            b.cv.drawRect(rightX - 5 * s, top, rightX - 4 * s, top + room, b.p);
            float cx = rightX - 4.5f * s;
            if (arrowR > 0 && Logic.arrowFits(cx, arrowR, s * .5f, clockEnds[0], clockEnds[1])) {
                left.getTextBounds("0", 0, 1, glyph);
                arrow(cx, baseline + glyph.top / 2f, arrowR);
            }
        }

        /** How much of the state now on has gone, under the left clock. */
        private void progress(float cy, float h) {
            float l = x + width * .12f;
            float r = x + width * .88f;
            b.p.setStyle(Paint.Style.FILL);
            b.p.setColor(Ui.fade(b.pal.fill(now.state), .18f));
            b.r2.set(l, cy - h / 2, r, cy + h / 2);
            b.cv.drawRoundRect(b.r2, h / 2, h / 2, b.p);
            float at = l + (r - l) * done;
            b.p.setColor(b.pal.fill(now.state));
            b.r2.right = Math.max(l + h, at);
            b.cv.drawRoundRect(b.r2, h / 2, h / 2, b.p);
            if (now.next != Logic.UNKNOWN) {
                b.p.setColor(b.pal.fill(now.next));
                b.cv.drawCircle(r - h / 2, cy, h, b.p);
            }
            b.p.setColor(Ui.Pal.MARKER_EDGE);
            b.cv.drawCircle(at, cy, h * 1.15f + s * .8f, b.p);
            b.p.setColor(Ui.Pal.MARKER);
            b.cv.drawCircle(at, cy, h * 1.15f, b.p);
        }

        /** "now, then" on the line between the halves, beside the names of the states: the clocks fill their halves. */
        private void arrow(float cx, float cy, float r) {
            int ink = b.pal.status(Logic.widgetRightState(now, next[1]));
            Paint p = b.p;
            p.setStyle(Paint.Style.FILL);
            p.setColor(b.pal.card);
            b.cv.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, r * .12f));
            p.setColor(Ui.fade(ink, .45f));
            b.cv.drawCircle(cx, cy, r - p.getStrokeWidth() / 2, p);
            float a = r * .42f;
            float h = r * .33f;
            p.setStrokeWidth(Math.max(1.2f, r * .18f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(ink);
            b.cv.drawLine(cx - a, cy, cx + a, cy, p);
            b.cv.drawLine(cx + a - h, cy - h, cx + a, cy, p);
            b.cv.drawLine(cx + a - h, cy + h, cx + a, cy, p);
            p.setStrokeCap(Paint.Cap.BUTT);
            p.setStyle(Paint.Style.FILL);
        }

        /** The bulb and the state on the left, what comes next on the right. */
        private void firstRow(float y, float rowHeight, float rightX) {
            float bulb = Math.min(rowHeight * 1.25f, room * .4f);
            float titleRoom = width - bulb * 1.16f;
            String state = Logic.stateWords(now.title, now.state, sizeToFit(title, now.title, titleRoom) >= 13 * s);
            fit(title, state, titleRoom);
            float lx = x + (width - bulb * 1.16f - title.measureText(state)) / 2;  // the bulb and the state, centred in their half
            BulbView.plain(b, now.state, lx + bulb / 2, y + rowHeight / 2, bulb);
            drawCentred(b, state, lx + bulb * 1.16f, y, rowHeight, title);
            String event = next[0];
            if (sizeToFit(label, event, width) < 12 * s && now.next != Logic.UNKNOWN) {
                event = Logic.compactEvent(now) + (now.tomorrow ? " завтра" : "");
            }
            fit(label, event, width);
            drawCentred(b, event, rightX + (width - label.measureText(event)) / 2, y, rowHeight, label);
        }

        private void clocks(float baseline, float rightX) {
            float span = width - reserve;
            fit(left, start[0], span - (inline && leftSpare > 0 ? leftSpare : 0));
            fit(right, next[1], span - (inline && rightSpare > 0 ? rightSpare : 0));
            float lw = left.measureText(start[0]) + (inline && leftSpare > 0 ? gapX + small.measureText(start[1]) : 0);
            float rw = right.measureText(next[1]) + (inline && rightSpare > 0 ? gapX + smallRight.measureText(next[2]) : 0);
            float lx = x + Math.max(0, (span - lw) / 2);
            float rx = rightX + reserve + Math.max(0, (span - rw) / 2);
            clockEnds[0] = lx + lw;
            clockEnds[1] = rx;
            b.cv.drawText(start[0], lx, baseline, left);
            b.cv.drawText(next[1], rx, baseline, right);
            if (!inline) return;
            if (leftSpare > 0) b.cv.drawText(start[1], lx + left.measureText(start[0]) + gapX, baseline, small);
            if (rightSpare > 0) b.cv.drawText(next[2], rx + right.measureText(next[1]) + gapX, baseline, smallRight);
        }

        private void details(float y, float rightX) {
            float span = width - reserve;  // under its clock, which stands clear of the arrow
            if (leftSpare > 0) line(b, start[1], x + (span - fit(small, start[1], span).measureText(start[1])) / 2, y, small);
            if (rightSpare > 0) line(b, next[2], rightX + reserve + (span - fit(smallRight, next[2], span).measureText(next[2])) / 2, y, smallRight);
        }
        private static float height(Paint p) {
            return p.descent() - p.ascent();
        }


        private static void drawCentred(Brush b, String words, float x, float y, float rowHeight, Paint p) {
            b.cv.drawText(words, x, y + (rowHeight - height(p)) / 2 - p.ascent(), p);
        }

        /** A line of text whose top is at y; returns the y under it. */
        private static float line(Brush b, String text, float x, float y, Paint p) {
            b.cv.drawText(text, x, y - p.ascent(), p);
            return y + p.descent() - p.ascent();
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
