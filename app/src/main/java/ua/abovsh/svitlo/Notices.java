package ua.abovsh.svitlo;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The oblenergo's own notice above its schedule: one line on the main screen, the whole text in a panel.
 * Stabilisation outages are announced almost daily, so that line is quiet; emergency outages somewhere in
 * the region are red. The app never knows the district of a place: the schedule is shown as it is.
 */
final class Notices {
    private Notices() {}

    /** The line for one region, or null when its file has no notice. */
    static View strip(Base a, Logic.Data d, String region, boolean manyRegions, long now) {
        if (d == null || d.notice.isEmpty()) return null;
        boolean emergency = Logic.noticeLevel(d.notice) == Logic.NOTICE_EMERGENCY;
        String title = Logic.noticeTitle(d.notice, region, manyRegions);
        String meta = Logic.noticeMeta(d, region, now);
        LinearLayout row = emergency ? redLine(a, title, meta) : quietLine(a, title, meta);
        ImageView more = new ImageView(a);
        more.setImageResource(R.drawable.ic_chevron);
        more.setColorFilter(emergency ? a.pal.ink : a.pal.maybeText, android.graphics.PorterDuff.Mode.SRC_IN);
        row.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
        row.setContentDescription(title + ". " + meta + ". Відкрити повний текст");
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.setFocusable(true);
        row.setOnClickListener(v -> show(a, region));
        return row;
    }

    /** Stabilisation and other notices: one amber line that wraps at a large font, who and when lighter. */
    private static LinearLayout quietLine(Base a, String title, String meta) {
        Ui.Pal pal = a.pal;
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6));
        row.setMinimumHeight(Ui.dp(a, 40));
        android.graphics.drawable.GradientDrawable bg = Ui.round(pal.soft(Logic.MAYBE), Ui.px(a, 100));
        bg.setStroke(Ui.dp(a, 1), Ui.fade(pal.maybe, .6f));
        row.setBackground(bg);
        row.addView(new SignView(a, pal.maybe, pal.accentInk), sign(a, 18, 8));
        TextView line = Ui.text(a, 13.5f, Ui.heavy(a), pal.maybeText);
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder(title);
        if (!meta.isEmpty()) {
            int from = s.length();
            s.append("  ·  ").append(meta);
            s.setSpan(new android.text.style.ForegroundColorSpan(pal.muted), from, s.length(), 0);
            s.setSpan(new android.text.style.RelativeSizeSpan(.9f), from, s.length(), 0);
        }
        line.setText(s);
        row.addView(line, Base.weight());
        return row;
    }

    /** Emergency outages: a red line in two rows, what it means for the schedule under the title. */
    private static LinearLayout redLine(Base a, String title, String meta) {
        Ui.Pal pal = a.pal;
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 8), Ui.dp(a, 9));
        row.setMinimumHeight(Ui.dp(a, 52));
        row.setBackground(Ui.round(pal.off, Ui.px(a, 18)));
        row.addView(new SignView(a, pal.ink, pal.off), sign(a, 24, 10));
        LinearLayout words = Ui.column(a);
        TextView head = Ui.text(a, 15, Ui.heavy(a), pal.ink);
        head.setText(title);
        words.addView(head, Base.fill());
        TextView sub = Ui.text(a, 12.5f, Ui.bold(a), pal.ink);
        sub.setText(meta);
        sub.setPadding(0, Ui.dp(a, 2), 0, 0);
        words.addView(sub, Base.fill());
        row.addView(words, Base.weight());
        return row;
    }

    private static LinearLayout.LayoutParams sign(Base a, int sizeDp, int gapDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(a, sizeDp), Ui.dp(a, sizeDp));
        l.rightMargin = Ui.dp(a, gapDp);
        return l;
    }

    /** The whole notice of one region, word for word, with what it means for the schedule. */
    static void show(Base a, String region) {
        Logic.Data d = Store.data(a, region);
        if (d == null || d.notice.isEmpty()) return;
        String source = Logic.noticeSource(region);

        LinearLayout top = Ui.column(a);
        BottomSheet.grip(a, top);
        LinearLayout header = Ui.row(a);
        TextView title = Ui.text(a, 18, Ui.heavy(a), a.pal.text);
        title.setText("Повідомлення " + source);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        header.addView(title, Base.weight());
        ImageView close = a.iconButton(R.drawable.ic_close, "Закрити", null);
        header.addView(close);
        top.addView(header, Base.fill());

        LinearLayout col = Ui.column(a);
        col.addView(labels(a, d, region), labelsAt(a));
        TextView text = Ui.text(a, 15, Ui.body(a), a.pal.text);
        text.setLineSpacing(0, 1.15f);
        text.setText(d.notice);
        text.setTextIsSelectable(true);
        text.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        text.setBackground(Ui.card(a, a.pal, 0));
        col.addView(text, Base.fill());
        if (Logic.noticeLevel(d.notice) == Logic.NOTICE_EMERGENCY) col.addView(scheduleMayDiffer(a), Base.fill());
        String site = Logic.noticeSite(region);
        if (!site.isEmpty()) col.addView(a.primary("Відкрити сайт " + source, v -> open(a, site)), a.gapTop(16));
        TextView note = Ui.text(a, 13, Ui.body(a), a.pal.muted);
        note.setLineSpacing(0, 1.1f);
        note.setText("Текст наведено дослівно з сайту " + source + ". Район місця застосунок не визначає.");
        note.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6), 0);
        col.addView(note, Base.fill());

        android.app.Dialog dialog = BottomSheet.show(a, top, col, () -> { });
        dialog.setCanceledOnTouchOutside(true);
        close.setOnClickListener(v -> dialog.dismiss());
    }

    /** The level as a chip, then the region and the time the text was on the site. */
    private static View labels(Base a, Logic.Data d, String region) {
        boolean emergency = Logic.noticeLevel(d.notice) == Logic.NOTICE_EMERGENCY;
        FlowRow labels = new FlowRow(a, Ui.dp(a, 8), Ui.dp(a, 4));
        TextView chip = Ui.text(a, 13, Ui.heavy(a), emergency ? a.pal.ink : a.pal.accentInk);
        chip.setText(Logic.noticeChip(d.notice));
        chip.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
        chip.setBackground(Ui.round(emergency ? a.pal.off : a.pal.maybe, Ui.px(a, 100)));
        labels.addView(chip);
        TextView where = Ui.text(a, 13, Ui.body(a), a.pal.muted);
        where.setText(Logic.noticeWhere(region, d.sourceTime, Sync.now()));
        where.setGravity(Gravity.CENTER_VERTICAL);
        where.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        labels.addView(where);
        return labels;
    }

    private static LinearLayout.LayoutParams labelsAt(Base a) {
        LinearLayout.LayoutParams l = Base.fill();
        l.topMargin = Ui.dp(a, 6);
        l.bottomMargin = Ui.dp(a, 12);
        return l;
    }

    private static View scheduleMayDiffer(Base a) {
        LinearLayout why = Ui.row(a);
        why.setGravity(Gravity.TOP);
        why.setPadding(Ui.dp(a, 6), Ui.dp(a, 14), Ui.dp(a, 6), 0);
        LinearLayout.LayoutParams sl = sign(a, 18, 10);
        sl.topMargin = Ui.dp(a, 1);
        why.addView(new SignView(a, a.pal.maybe, a.pal.accentInk), sl);
        TextView words = Ui.text(a, 14, Ui.body(a), a.pal.muted);
        words.setLineSpacing(0, 1.1f);
        words.setText("Графік у застосунку — останній опублікований. Поки діють екстрені, він може не збігатися зі світлом у вашому будинку.");
        why.addView(words, Base.weight());
        return why;
    }

    private static void open(Base a, String site) {
        try {
            a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(site)));
        } catch (RuntimeException ignored) {
            // no browser: the text is in the panel already
        }
    }

    /** The round sign with "!", circle = centre x, centre y and radius; `ring` a margin of that colour, or 0. */
    static void sign(Canvas cv, Paint p, Ui.Pal pal, int level, float[] circle, int ring) {
        boolean emergency = level == Logic.NOTICE_EMERGENCY;
        if (ring != 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(ring);
            cv.drawCircle(circle[0], circle[1], circle[2] + Math.max(1f, circle[2] * .16f), p);
        }
        mark(cv, p, emergency ? pal.off : pal.maybe, emergency ? pal.ink : pal.accentInk, circle);
    }

    private static void mark(Canvas cv, Paint p, int fill, int ink, float[] circle) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(fill);
        cv.drawCircle(circle[0], circle[1], circle[2], p);
        p.setColor(ink);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(circle[2] * 1.45f);
        p.setFakeBoldText(p.getTypeface() == null);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText("!", circle[0], circle[1] - (fm.ascent + fm.descent) / 2, p);
    }

    /** The sign as a view, in the colours given: white on the red line. */
    static final class SignView extends View {
        private final int fill;
        private final int ink;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] circle = new float[3];

        SignView(android.content.Context c, int fill, int ink) {
            super(c);
            this.fill = fill;
            this.ink = ink;
            paint.setTypeface(Ui.heavy(c));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas cv) {
            circle[0] = getWidth() / 2f;
            circle[1] = getHeight() / 2f;
            circle[2] = Math.min(getWidth(), getHeight()) / 2f;
            mark(cv, paint, fill, ink, circle);
        }
    }
}
