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

    /** One line under the places for the region of the chosen place, or null when its file has no notice. */
    static View strip(Base a, Logic.Data d, String region) {
        if (d == null || d.notice.isEmpty()) return null;
        boolean emergency = Logic.noticeLevel(d.notice) == Logic.NOTICE_EMERGENCY;
        Ui.Pal pal = a.pal;
        String title = Logic.noticeTitle(d.notice, region);
        int ink = emergency ? pal.ink : pal.maybeText;
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6));
        row.setMinimumHeight(Ui.dp(a, 40));
        android.graphics.drawable.GradientDrawable bg = Ui.round(emergency ? pal.off : pal.soft(Logic.MAYBE), Ui.px(a, 100));
        if (!emergency) bg.setStroke(Ui.dp(a, 1), Ui.fade(pal.maybe, .6f));
        row.setBackground(bg);
        SignView sign = emergency ? new SignView(a, pal.ink, pal.off) : new SignView(a, pal.maybe, pal.accentInk);
        row.addView(sign, sign(a, 20, 10));
        // one line; a large font wraps it rather than cutting it
        TextView line = Ui.text(a, 14.5f, Ui.heavy(a), ink);
        line.setText(title);
        row.addView(line, Base.weight());
        ImageView more = new ImageView(a);
        more.setImageResource(R.drawable.ic_chevron);
        more.setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN);
        row.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
        row.setContentDescription(title + ". Відкрити повний текст");
        for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.setFocusable(true);
        row.setOnClickListener(v -> show(a, region));
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
