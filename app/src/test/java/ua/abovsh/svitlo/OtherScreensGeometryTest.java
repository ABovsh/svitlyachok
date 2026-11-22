package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import ua.abovsh.svitlo.MainScreenGeometryFixture.Scenario;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 35, instrumentedPackages = "ua.abovsh.svitlo", shadows = CanvasGeometry.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OtherScreensGeometryTest {
    @Rule public final CoverageCases coverage = new CoverageCases();

    private static final int DPI = 420;
    private static final int WIDGET = 8504;
    private static final String WEEK = "тиждень";
    private static final String WEEKS_TOGGLE = "Графік за 4 тижні";

    @ParameterizedRobolectricTestRunner.Parameters(name = "w{0}-f{1}-{2}")
    public static List<Object[]> profiles() {
        return MainScreenGeometryTest.profiles().stream()
                .filter(p -> (float) p[1] == 1f || (float) p[1] >= 1.3f
                        || List.of(320, 360, 393).contains(p[0])).toList();
    }

    private final int width;
    private final float scale;
    private final String theme;
    private final String profile;
    private Context context;
    private final List<String> failures = new ArrayList<>();

    public OtherScreensGeometryTest(int width, float scale, String theme) {
        this.width = width;
        this.scale = scale;
        this.theme = theme;
        this.profile = width + "dp/" + scale + "/" + theme;
    }

    @Before
    public void seed() throws Exception {
        context = MainScreenGeometryFixture.profile(width, scale, theme, DPI);
        MainScreenGeometryFixture.seed(context, theme,
                width == 393 && scale == 1.3f ? Scenario.LONG : Scenario.NORMAL, 15);
    }

    @Test
    public void otherAdaptationScreensFit() throws Exception {
        try (var settings = Robolectric.buildActivity(SettingsActivity.class).setup()) {
            View root = layoutScreen(settings.get());
            text(root, "Налаштування");
            inspect("settings", root);
        }
        try (var setup = Robolectric.buildActivity(SetupActivity.class).setup()) {
            View root = layoutScreen(setup.get());
            inspect("setup", root);
            View region = text(root, "Київська");
            while (!region.isClickable() && region.getParent() instanceof View parent) region = parent;
            assertTrue(profile + " choose Kyiv region", region.performClick());
            layoutScreen(setup.get());
            assertTrue(profile + " queue choices", !ViewGeometry.visible(root, TextView.class,
                    v -> "1.1".contentEquals(v.getText())).isEmpty());
            inspect("setup-queue", root);
        }
        widgetSetup();
        try (var host = Robolectric.buildActivity(AndroidNoticesTest.NoticeHost.class).setup()) {
            Logic.Place place = Prefs.place(context, "p4");
            AlertsSheet.show(host.get(), place, () -> {});
            checkDialog("alerts", "Перед вимкненням");
            AlertsSheet.help(host.get(), place);
            checkDialog("help", "Світла немає, хоча за графіком є");
        }
        MainScreenGeometryFixture.seed(context, theme, Scenario.LONG, 15);
        try (var edit = Robolectric.buildActivity(SetupActivity.class,
                new Intent(context, SetupActivity.class).putExtra("id", "p4")).setup()) {
            View root = layoutScreen(edit.get());
            text(root, "Місце");
            text(root, "Будинок на другій вулиці");
            inspect("edit-place", root);
        }
        assertTrue(profile + " " + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void statisticsWeekAndFourWeeksFit() {
        try (var activity = Robolectric.buildActivity(StatsActivity.class).setup()) {
            View root = layoutScreen(activity.get());
            text(root, "Статистика");
            inspectStatistics("week", root);
            View four = ViewGeometry.one(root, View.class, v -> WEEKS_TOGGLE.equals(v.getContentDescription()), profile);
            assertTrue(profile + " switch to four completed weeks", four.performClick());
            layoutScreen(activity.get());
            inspectStatistics("four-weeks", root);
            StatsChart chart = ViewGeometry.one(root, StatsChart.class, v -> true, profile);
            assertTrue(profile + " actual four-week chart", chart.getContentDescription().toString().contains("із 7"));
        }
        assertTrue(profile + " " + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void shortNumbersStayInTheirChartColumnsAndAverageLeavesPercentagesReadable() {
        Ui.Pal palette = Ui.pal(context);
        StatsChart chart = new StatsChart(context, palette);
        HorizontalScrollView viewport = new HorizontalScrollView(context);
        viewport.setFillViewport(true);
        viewport.addView(chart);
        LocalDate today = Logic.today(Sync.now());
        Map<LocalDate, String> history = new LinkedHashMap<>();
        int[] off = {41, 47, 48, 1, 0, 22, 2};
        for (int i = 0; i < off.length; i++) {
            int count = off[i];
            history.put(today.minusDays(7L - i), "0".repeat(count) + "1".repeat(48 - count));
        }
        List<Logic.DayStat> days = Logic.window(history, today.minusDays(1), 7);
        chart.set(days);
        ViewGeometry.layout(viewport, Ui.dp(context, width - 48), 0, View.MeasureSpec.UNSPECIFIED);
        CanvasGeometry.Frame daily = drawChart(chart);
        for (int i = 0; i < off.length; i++) {
            Logic.DayStat day = days.get(i);
            float slot = daily.width / 7f;
            RectF column = new RectF(i * slot, 0, (i + 1) * slot, daily.height);
            if (off[i] == 0) {
                assertEquals(profile + " no zero-hour daily segment", "", Logic.statLabel(day, Logic.OFF));
                continue;
            }
            CanvasGeometry.Text value = chartText(daily, column, Logic.statLabel(day, Logic.OFF));
            RectF segment = daily.rectangles.stream().filter(shape -> shape.color() == palette.off
                    && column.contains(shape.bounds()) && shape.bounds().height() > 0)
                    .map(CanvasGeometry.Shape::bounds).findFirst().orElseThrow();
            assertTrue(profile + " number crosses segment edge " + value + " / " + segment,
                    segment.contains(value.ink()) || (value.color() == palette.offText && value.ink().bottom < segment.top));

        }

        int[] on = {0, 1, 10, 48};
        for (int week = 0; week < on.length; week++) {
            int count = on[week];
            for (int day = 0; day < 7; day++) {
                history.put(today.minusDays(28L - 7L * week - day), "1".repeat(count) + "0".repeat(48 - count));
            }
        }
        List<Logic.WeekStat> weeks = Logic.fourWeeks(history, today);
        chart.setWeeks(weeks);
        ViewGeometry.layout(viewport, Ui.dp(context, width - 48), 0, View.MeasureSpec.UNSPECIFIED);
        CanvasGeometry.Frame month = drawChart(chart);
        assertEquals(profile + " average line", 1, month.lines.size());
        CanvasGeometry.Shape average = month.lines.get(0);
        int crossings = 0;
        for (int i = 0; i < weeks.size(); i++) {
            Logic.WeekStat weekStat = weeks.get(i);
            float slot = month.width / 4f;
            RectF column = new RectF(i * slot, 0, (i + 1) * slot, month.height);
            CanvasGeometry.Text percent = chartText(month, column, weekStat.onPercent + " %");
            chartText(month, column, String.format(java.util.Locale.ROOT, "%.1f", weekStat.onMinutes / 60f).replace('.', ','));
            if (RectF.intersects(percent.ink(), average.bounds())) {
                crossings++;
                assertTrue(profile + " average must be masked where it crosses the percentage " + percent,
                        month.text.stream().anyMatch(t -> t.value().equals(percent.value()) && t.ink().equals(percent.ink())
                                && t.color() == palette.card && t.strokeWidth() >= Ui.px(context, 3)
                                && t.order() > average.order() && t.order() < percent.order()));
            }
        }
        assertTrue(profile + " fixture must bring the average line behind a percentage", crossings > 0);
    }

    private CanvasGeometry.Text chartText(CanvasGeometry.Frame frame, RectF column, String words) {
        List<CanvasGeometry.Text> matches = frame.text.stream().filter(t -> words.equals(t.value())
                && t.strokeWidth() == 0 && column.contains(t.ink().centerX(), t.ink().centerY())).toList();
        assertEquals(profile + " chart text " + words + " in " + column, 1, matches.size());
        CanvasGeometry.Text text = matches.get(0);
        assertTrue(profile + " chart text crosses column " + column + ": " + text, column.contains(text.ink()));
        return text;
    }

    private static CanvasGeometry.Frame drawChart(StatsChart chart) {
        Bitmap bitmap = Bitmap.createBitmap(chart.getWidth(), chart.getHeight(), Bitmap.Config.ARGB_8888);
        CanvasGeometry.start();
        List<CanvasGeometry.Frame> frames;
        try {
            chart.onDraw(new Canvas(bitmap));
        } finally {
            frames = CanvasGeometry.stop();
            bitmap.recycle();
        }
        assertEquals(1, frames.size());
        return frames.get(0);
    }

    private void widgetSetup() {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(context, LightWidget.class);
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(WIDGET, info);
        Prefs.setWidgetChoice(context, WIDGET, "p4");
        Intent intent = new Intent(context, WidgetSetupActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET);
        try (var widget = Robolectric.buildActivity(WidgetSetupActivity.class, intent).setup()) {
            View root = layoutScreen(widget.get());
            text(root, "Віджет");
            text(root, "на віджеті");
            inspect("widget-setup", root);
        }
    }

    private void checkDialog(String name, String marker) {
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(profile + " " + name, dialog);
        try {
            assertTrue(profile + " " + name, dialog.isShowing());
            View root = dialog.findViewById(android.R.id.content);
            ViewGeometry.layout(root, Ui.dp(context, width), Ui.dp(context, 780), View.MeasureSpec.AT_MOST);
            text(root, marker);
            inspect(name, root);
        } finally {
            dialog.dismiss();
        }
    }

    private void inspectStatistics(String name, View root) {
        inspect(name, root);
        HorizontalScrollView scroll = ViewGeometry.one(root, HorizontalScrollView.class, v -> true, profile);
        int viewport = scroll.getWidth() - scroll.getPaddingLeft() - scroll.getPaddingRight();
        int scrollPx = scroll.getChildAt(0).getWidth() - viewport;
        if ((width >= 360 && scale <= 1.3f) || (width == 320 && scale == 1.3f)) {
            assertEquals(profile + " " + name + " chart scrollPx", 0, scrollPx);
        }
        List<TextView> headings = ViewGeometry.visible(root, TextView.class,
                view -> WEEK.equals(view.getContentDescription()));
        assertTrue(profile + " statistic captions", !headings.isEmpty());
        for (TextView view : headings) {
            assertEquals(profile + " week caption must remain intact", 1, view.getLineCount());
        }
        for (TextView title : ViewGeometry.visible(root, TextView.class,
                v -> "Світла за графіком".contentEquals(v.getText()))) {
            TextView week = (TextView) ((ViewGroup) title.getParent()).getChildAt(1);
            float edge = 0;
            for (int line = 0; line < title.getLineCount(); line++) {
                edge = Math.max(edge, title.getLayout().getLineRight(line));
            }
            float gap = week.getLeft() + week.getCompoundPaddingLeft()
                    - title.getLeft() - title.getCompoundPaddingLeft() - edge;
            assertTrue(profile + " statistics column captions touch: " + gap, gap >= Ui.px(context, 4) - 1);
        }
    }

    private View layoutScreen(Base activity) {
        assertEquals(profile + " effective width", width, activity.getResources().getConfiguration().screenWidthDp);
        assertEquals(profile + " effective font", scale, activity.getResources().getConfiguration().fontScale, 0f);
        View root = activity.findViewById(android.R.id.content);
        ViewGeometry.layout(root, Ui.dp(context, width), 2340, View.MeasureSpec.EXACTLY);
        return root;
    }

    private TextView text(View root, String words) {
        return ViewGeometry.one(root, TextView.class, v -> words.contentEquals(v.getText()), profile + " " + words);
    }

    private void inspect(String name, View root) {
        coverage.report(name);
        List<String> issues = new ArrayList<>();
        ViewGeometry.inspect(root, issues);
        for (String issue : issues) failures.add(name + " " + issue);
        if (width >= 320 && width <= 412 && scale <= 1.3f) {
            String font = scale == 1f ? "1" : Float.toString(scale);
            GeometryEvidence.saveView("screens/w" + width + "-f" + font + "-" + theme + "/" + name + ".png", root);
        }
    }
}
