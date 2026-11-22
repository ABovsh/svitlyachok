package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
public class DaySharingGeometryTest {
    private static final String SLOTS = "3311".repeat(12);
    private static final String PLACE_NAME = "Майстерня в будинку біля старого парку";
    private static final int DPI = 420;

    @ParameterizedRobolectricTestRunner.Parameters(name = "w{0}-f{1}-{2}")
    public static List<Object[]> profiles() {
        return List.of(new Object[] {360, 2f, "light"}, new Object[] {360, 2f, "dark"},
                new Object[] {393, 1f, "light"}, new Object[] {393, 1f, "dark"});
    }

    private final int width;
    private final float scale;
    private final String theme;
    private final String profile;

    public DaySharingGeometryTest(int width, float scale, String theme) {
        this.width = width;
        this.scale = scale;
        this.theme = theme;
        profile = width + "dp/" + scale + "/" + theme;
    }

    @Test
    public void twentyFourIntervalRowsScrollUnderFixedDateAndClose() throws Exception {
        Context context = MainScreenGeometryFixture.profile(width, scale, theme, DPI);
        MainScreenGeometryFixture.seed(context, theme, Scenario.NORMAL, 15);
        Logic.Place place = Prefs.places(context).get(3);
        place.name = PLACE_NAME;
        try (var host = Robolectric.buildActivity(AndroidNoticesTest.NoticeHost.class).setup()) {
            LocalDate day = Logic.today(Scenario.NORMAL.now);
            Dialog previous = ShadowDialog.getLatestDialog();
            DayIntervals.show(host.get(), place, day, () -> SLOTS, () -> {});
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            assertTrue(profile, dialog != previous && dialog.isShowing());
            try {
                checkIntervals(dialog);
            } finally {
                dialog.dismiss();
            }
        }
    }

    private void checkIntervals(Dialog dialog) {
        View root = dialog.<View>findViewById(android.R.id.content);
        ViewGeometry.layout(root, Math.round(width * DPI / 160f), Ui.dp(root.getContext(), 780), View.MeasureSpec.AT_MOST);
        shadowOf(Looper.getMainLooper()).idle();
        ScrollView scroll = ViewGeometry.one(root, ScrollView.class, v -> true, profile);
        TextView date = ViewGeometry.one(root, TextView.class,
                v -> v.isAccessibilityHeading() && v.getText().toString().startsWith("Сьогодні"), profile);
        TextView name = ViewGeometry.one(root, TextView.class,
                v -> v.getText().toString().startsWith(PLACE_NAME), profile);
        View close = ViewGeometry.one(root, View.class, v -> "Закрити".contentEquals(
                v.getContentDescription() == null ? "" : v.getContentDescription()), profile);

        assertEquals(profile + " interval count", 24, ViewGeometry.visible(scroll, View.class,
                v -> v.getContentDescription() != null
                        && v.getContentDescription().toString().matches("\\d\\d:\\d\\d–\\d\\d:\\d\\d\\..*")).size());
        assertTrue(profile + " must reveal the current interval", scroll.getScrollY() > 0);
        for (View header : List.of(date, name, close)) {
            assertFalse(profile + " header must not belong to the scrolling subtree",
                    ViewGeometry.visible(scroll, View.class, v -> v == header).contains(header));
            assertTrue(profile + " header must be inside the sheet",
                    relativeY(header, root) >= 0 && relativeY(header, root) + header.getHeight() <= root.getHeight());
        }
        int originalDate = relativeY(date, root);
        int originalClose = relativeY(close, root);
        int originalName = relativeY(name, root);
        int before = scroll.getScrollY();
        scroll.scrollTo(0, Integer.MAX_VALUE);
        assertTrue(profile + " must be able to reach the bottom", scroll.getScrollY() > before);
        assertEquals(profile + " date moved", originalDate, relativeY(date, root));
        assertEquals(profile + " close moved", originalClose, relativeY(close, root));
        assertEquals(profile + " place name moved", originalName, relativeY(name, root));
        Layout text = name.getLayout();
        if (scale == 2f) assertTrue(profile + " long place must wrap", text.getLineCount() > 1);
        for (int i = 1; i < text.getLineCount(); i++) {
            int end = text.getLineEnd(i - 1);
            assertTrue(profile + " break mid-word at " + end,
                    !Character.isLetterOrDigit(name.getText().charAt(end - 1))
                            || !Character.isLetterOrDigit(name.getText().charAt(end)));
        }
        List<String> failures = new ArrayList<>();
        ViewGeometry.inspect(root, failures);
        assertTrue(profile + " " + failures, failures.isEmpty());
        assertTrue(profile + " close button", close.performClick());
        assertFalse(dialog.isShowing());
    }

    private static int relativeY(View view, View root) {
        int y = 0;
        while (view != root && view.getParent() instanceof View parent) {
            y += view.getTop() - parent.getScrollY();
            view = parent;
        }
        return y;
    }

    @Test
    public void twentyFourOutageChipsAndFooterFitInActualPng() throws Exception {
        Context context = MainScreenGeometryFixture.profile(width, scale, theme, DPI);
        MainScreenGeometryFixture.seed(context, theme, Scenario.NORMAL, 15);
        Logic.Place place = AndroidRegressionFixture.place("p1", PLACE_NAME);
        long now = Scenario.NORMAL.now;
        LocalDate day = Logic.today(now);
        String outages = "31".repeat(24);
        context.getSharedPreferences(AndroidRegressionFixture.FEED, 0).edit()
                .putString(AndroidRegressionFixture.STORED_FEED, AndroidRegressionFixture.feed(day, outages, now)).commit();
        assertEquals(profile + " stored day", outages, Logic.day(Store.data(context, place.region), place.group, day));
        List<Logic.Outage> expected = Logic.outages(outages, Logic.index(day, now));
        assertEquals(24, expected.size());
        try (var host = Robolectric.buildActivity(AndroidNoticesTest.NoticeHost.class).setup()) {
            List<CanvasGeometry.Frame> frames;
            CanvasGeometry.start();
            try {
                Share.send(host.get(), place, day);
            } finally {
                frames = CanvasGeometry.stop();
            }
            assertEquals(profile + " measure and bitmap passes", 2, frames.size());
            CanvasGeometry.Frame frame = frames.get(1);
            Bitmap png = BitmapFactory.decodeFile(Share.picture(host.get()).getPath());
            assertNotNull(profile, png);
            try {
                assertEquals(profile, 1080, png.getWidth());
                assertEquals(profile, frame.height, png.getHeight());
                assertOutagesAndFooter(png, frame, expected);
            } finally {
                png.recycle();
            }
        }
    }

    private void assertOutagesAndFooter(Bitmap png, CanvasGeometry.Frame frame, List<Logic.Outage> expected) {
        for (Logic.Outage outage : expected) assertPngText(png, frame, Logic.shareChip(outage));
        CanvasGeometry.Text signature = assertPngText(png, frame, Logic.SHARE_SIGNATURE);
        assertTrue(profile + " footer follows final time",
                signature.ink().top >= assertPngText(png, frame, Logic.shareChip(expected.get(23))).ink().bottom);
        for (CanvasGeometry.Text text : frame.text) {
            RectF box = text.ink();
            assertTrue(profile + " " + text, box.left >= 0 && box.top >= 0
                    && box.right <= frame.width && box.bottom <= frame.height);
            assertFalse(profile + " PNG exposes the private place name", text.value().contains(PLACE_NAME));
        }
    }

    private CanvasGeometry.Text assertPngText(Bitmap png, CanvasGeometry.Frame frame, String value) {
        List<CanvasGeometry.Text> matches = frame.text.stream().filter(t -> value.equals(t.value())).toList();
        assertEquals(profile + " missing or duplicate " + value, 1, matches.size());
        CanvasGeometry.Text text = matches.get(0);
        RectF box = text.ink();
        assertTrue(profile + " PNG clips " + value + " at " + box + " vs " + png.getHeight(),
                box.left >= 0 && box.top >= 0 && box.right <= png.getWidth() && box.bottom <= png.getHeight());
        int ink = 0;
        for (int y = (int) box.top; y < (int) box.bottom; y++) {
            for (int x = (int) box.left; x < (int) box.right; x++) {
                if (png.getPixel(x, y) == text.color()) ink++;
            }
        }
        assertTrue(profile + " PNG does not contain the expected ink for " + value, ink > 10);
        return text;
    }
}
