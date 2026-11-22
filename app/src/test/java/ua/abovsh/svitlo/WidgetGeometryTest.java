package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.Context;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import ua.abovsh.svitlo.MainScreenGeometryFixture.Scenario;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 35, instrumentedPackages = "ua.abovsh.svitlo", shadows = CanvasGeometry.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetGeometryTest {
    @Rule public final CoverageCases coverage = new CoverageCases();

    private record Size(String provider, int width, int height) {}

    // Checks.widgets() in screens.py and rc7-widgets.py, including both providers' extra sizes.
    private static final List<Size> SIZES = List.of(
            new Size("LightWidget", 110, 40), new Size("LightWidget", 150, 70),
            new Size("LightWidget", 180, 80), new Size("LightWidget", 300, 70),
            new Size("LightWidget", 300, 130), new Size("LargeLightWidget", 200, 110),
            new Size("LargeLightWidget", 250, 120), new Size("LargeLightWidget", 300, 176),
            new Size("LargeLightWidget", 360, 150), new Size("LargeLightWidget", 360, 190),
            new Size("LargeLightWidget", 420, 220));

    private enum State {
        NORMAL, POSSIBLE_AFTER_LIGHT, POSSIBLE_AFTER_OFF, UNKNOWN, MISSING_QUEUE,
        TOMORROW_POSSIBLE, TOMORROW_PARTIAL, WAITING, CALM, EMERGENCY, LONG, FIRST_PLACE,
        TOMORROW_CLEAR, NO_TOMORROW, EVENING
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
    public static List<Object[]> profiles() {
        return List.of(new Object[] {"light", 1f}, new Object[] {"dark", 1f},
                new Object[] {"light", 1.3f}, new Object[] {"dark", 1.3f});
    }

    private final String theme;
    private final float scale;

    public WidgetGeometryTest(String theme, float scale) {
        this.theme = theme;
        this.scale = scale;
    }

    @Test
    public void bothProvidersKeepTextSignsAndGlowInsideTheirCards() throws Exception {
        Context context = MainScreenGeometryFixture.profile(411, scale, theme, 160);
        MainScreenGeometryFixture.seed(context, theme, Scenario.NORMAL, 15);
        List<String> failures = new ArrayList<>();
        for (State state : State.values()) {
            long now = switch (state) {
                case TOMORROW_CLEAR -> Scenario.NIGHT.now;
                case NO_TOMORROW, EVENING -> java.time.LocalDate.of(2026, 11, 1).atTime(21, 30)
                        .atZone(Logic.KYIV).toEpochSecond();
                default -> Scenario.NORMAL.now;
            };
            android.os.SystemClock.setCurrentTimeMillis(now * 1000);
            assertEquals(state.toString(), now, Sync.now());
            WidgetArt.Model model = fixture(context, state, now);
            for (Size size : SIZES) {
                try {
                    check(context, model, now, size, state);
                    coverage.report(state.name() + "/" + sizeId(size));
                } catch (AssertionError error) {
                    failures.add(error.getMessage());
                }
            }
        }
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void sharedRemoteViewsLayoutFitsBothProviderSizes() throws Exception {
        Context context = MainScreenGeometryFixture.profile(411, scale, theme, 420);
        MainScreenGeometryFixture.seed(context, theme, Scenario.NORMAL, 15);
        long now = Scenario.NORMAL.now;
        for (Size size : SIZES) {
            Bitmap art = WidgetArt.draw(context, WidgetArt.model(context, now, "p4"), now, size.width, size.height);
            try {
                RemoteViews remote = new RemoteViews(context.getPackageName(), R.layout.widget);
                remote.setImageViewBitmap(R.id.w_img, art);
                AppWidgetHostView host = new AppWidgetHostView(context);
                int inset = AppWidgetHostView.getDefaultPaddingForWidget(context,
                        new ComponentName(context.getPackageName(), "ua.abovsh.svitlo." + size.provider), null).left;
                host.setPadding(inset, inset, inset, inset);
                host.updateAppWidget(remote);
                ViewGeometry.layout(host, Ui.dp(context, size.width), Ui.dp(context, size.height), android.view.View.MeasureSpec.EXACTLY);
                assertEquals(1, host.getChildCount());
                ImageView image = host.findViewById(R.id.w_img);
                assertTrue(size.toString(), image.getWidth() > 0 && image.getHeight() > 0);
                assertTrue(size.toString() + " missing rendered bitmap", image.getDrawable() instanceof BitmapDrawable);
                Bitmap rendered = ((BitmapDrawable) image.getDrawable()).getBitmap();
                assertEquals(size.toString(), art.getWidth(), rendered.getWidth());
                assertEquals(size.toString(), art.getHeight(), rendered.getHeight());
                assertEquals(size.toString() + " unexpected image", art.getPixel(art.getWidth() / 2, art.getHeight() / 2),
                        rendered.getPixel(rendered.getWidth() / 2, rendered.getHeight() / 2));
                assertEquals(size.toString() + " transparent image", 255,
                        Color.alpha(rendered.getPixel(rendered.getWidth() / 2, rendered.getHeight() / 2)));
                coverage.report(sizeId(size));
                String font = scale == 1f ? "1" : "1.3";
                GeometryEvidence.saveView("widgets/" + theme + "-" + font + "/widget-" + size.provider
                        + "-p4-" + size.width + "x" + size.height + ".png", host);
            } finally {
                art.recycle();
            }
        }
    }

    private static String sizeId(Size size) {
        return size.provider + "/" + size.width + "x" + size.height;
    }

    @Test
    public void possibleOutageTomorrowUsesItsOwnCellsAndDayLabels() throws Exception {
        Context context = MainScreenGeometryFixture.profile(411, scale, theme, 160);
        MainScreenGeometryFixture.seed(context, theme, Scenario.TOMORROW_POSSIBLE, 15);
        long now = Sync.now();
        assertEquals("13:00", java.time.Instant.ofEpochSecond(now).atZone(Logic.KYIV).toLocalTime().toString());
        Size size = new Size("LargeLightWidget", 360, 190);
        WidgetArt.Model model = WidgetArt.model(context, now, "p4");
        CanvasGeometry.start();
        Bitmap bitmap;
        List<CanvasGeometry.Frame> frames;
        try {
            bitmap = WidgetArt.draw(context, model, now, size.width, size.height);
        } finally {
            frames = CanvasGeometry.stop();
        }
        try {
            CanvasGeometry.Frame frame = frames.get(0);
            CanvasGeometry.Text today = text(frame, "Сьогодні · 12–23", 0, size.height);
            CanvasGeometry.Text tomorrow = text(frame, "Завтра · 00–11", today.baseline(), size.height);
            Ui.Pal pal = Ui.pal(context);
            assertCellColor(bitmap, frame, "17", today.baseline(), tomorrow.baseline(), Ui.blend(pal.card, pal.slot('0')));
            assertCellColor(bitmap, frame, "20", today.baseline(), tomorrow.baseline(), Ui.blend(pal.card, pal.slot('1')));
            assertCellColor(bitmap, frame, "08", tomorrow.baseline(), size.height, Ui.blend(pal.card, pal.slot('3')));
            assertCellColor(bitmap, frame, "05", tomorrow.baseline(), size.height, Ui.blend(pal.card, pal.slot('1')));
            coverage.report("13:00/today-17-off/today-20-on/tomorrow-08-possible/tomorrow-05-on");
        } finally {
            bitmap.recycle();
        }
    }

    private static CanvasGeometry.Text text(CanvasGeometry.Frame frame, String value, float after, float before) {
        List<CanvasGeometry.Text> matches = frame.text.stream().filter(t -> t.value().equals(value)
                && t.baseline() > after && t.baseline() < before).toList();
        assertEquals("exactly one " + value + " in row", 1, matches.size());
        return matches.get(0);
    }

    private static void assertCellColor(Bitmap bitmap, CanvasGeometry.Frame frame, String hour,
                                        float after, float before, int expected) {
        CanvasGeometry.Text digit = text(frame, hour, after, before);
        int x = Math.round(digit.ink().centerX());
        int y = Math.round(digit.ink().bottom);
        int matching = 0;
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (bitmap.getPixel(x + dx, y + dy) == expected) matching++;
            }
        }
        assertTrue(hour + " cell has no expected fill 0x" + Integer.toHexString(expected), matching > 0);
    }

    private static WidgetArt.Model fixture(Context context, State state, long now) {
        WidgetArt.Model model = WidgetArt.model(context, Scenario.NORMAL.now,
                state == State.FIRST_PLACE ? "p1" : "p4");
        model.tl = switch (state) {
            case POSSIBLE_AFTER_LIGHT -> "1".repeat(24) + "333333" + "1".repeat(66);
            case POSSIBLE_AFTER_OFF -> "0".repeat(24) + "333333" + "0".repeat(66);
            case MISSING_QUEUE -> "2".repeat(48) + model.tl.substring(48);
            case UNKNOWN -> "1".repeat(24) + "222222" + "0".repeat(66);
            case NO_TOMORROW -> model.tl.substring(0, 48) + "2".repeat(48);
            case TOMORROW_CLEAR -> "1".repeat(96);
            case TOMORROW_POSSIBLE -> model.tl.substring(0, 48) + "1".repeat(14)
                    + "3".repeat(6) + "1".repeat(10) + "0".repeat(6) + "1".repeat(12);
            case TOMORROW_PARTIAL -> model.tl.substring(0, 48) + "1".repeat(14)
                    + "0".repeat(6) + "1".repeat(4) + "2".repeat(24);
            case WAITING -> "2".repeat(48) + "1".repeat(48);
            default -> model.tl;
        };
        if (state == State.LONG) model.place.name = "МайстерняВеликогоБудинку";
        model.now = Logic.now(model.tl, model.base, now, false);
        model.lit = false;
        model.ahead = state == State.WAITING;
        model.waiting = state == State.WAITING;
        model.checked = model.waiting ? now : 0;
        model.notice = switch (state) {
            case CALM -> Logic.NOTICE_CALM;  // Renderer supports it; WidgetArt.model only enables emergency.
            case EMERGENCY -> Logic.NOTICE_EMERGENCY;
            default -> Logic.NOTICE_NONE;
        };
        if (state == State.POSSIBLE_AFTER_LIGHT || state == State.POSSIBLE_AFTER_OFF) {
            assertEquals(Logic.MAYBE, model.now.state);
            assertEquals(state == State.POSSIBLE_AFTER_OFF, model.now.afterOff);
        }
        if (state == State.UNKNOWN) assertEquals(Logic.UNKNOWN, model.now.state);
        if (state == State.NO_TOMORROW) assertTrue(model.now.dayEnd);
        return model;
    }

    private void check(Context context, WidgetArt.Model model, long now, Size size, State state) {
        String label = theme + "/" + scale + " " + size + " " + state;
        CanvasGeometry.start();
        Bitmap bitmap = null;
        List<CanvasGeometry.Frame> frames;
        try {
            bitmap = WidgetArt.draw(context, model, now, size.width, size.height);
        } finally {
            frames = CanvasGeometry.stop();
        }
        try {
            assertEquals(label, size.width, bitmap.getWidth());
            assertEquals(label, size.height, bitmap.getHeight());
            assertEquals(label, 1, frames.size());
            CanvasGeometry.Frame frame = frames.get(0);
            assertFalse(label + " text was never drawn", frame.text.isEmpty());
            List<String> failures = new ArrayList<>();
            for (CanvasGeometry.Text text : frame.text) {
                RectF r = text.ink();
                if (r.left < -1 || r.top < -1 || r.right > size.width + 1 || r.bottom > size.height + 1) {
                    failures.add("text outside card " + text.value() + " " + r);
                }
                if (state != State.LONG && text.value().contains("…")) failures.add("unexpected ellipsis " + text.value());
            }
            int sign = model.notice;
            float[] circle = sign == Logic.NOTICE_NONE ? null : model.sign;
            int maxAlpha = maxAlphaOutside(bitmap, circle);
            int overlap = bulbSignOverlap(context, frame, size, sign, circle);
            int allowance = 4;
            if (maxAlpha > allowance) failures.add("alpha outside card=" + maxAlpha + " (limit " + allowance + ")");
            int covered = 0;
            if (overlap > covered) failures.add("opaque bulb pixels covered by sign=" + overlap + " (limit " + covered + ")");
            if ((state == State.NORMAL || state == State.EMERGENCY) && maxAlpha > 4) {
                GeometryEvidence.save("widget-defects/" + theme + "-" + scale + "/" + state + "-"
                        + size.width + "x" + size.height + ".png", bitmap);
            }
            assertColumnColors(context, label, bitmap, model, state);
            assertTrue(label + " " + failures, failures.isEmpty());
        } finally {
            bitmap.recycle();
        }
    }

    private static int bulbSignOverlap(Context context, CanvasGeometry.Frame frame, Size size, int sign, float[] at) {
        if (sign == Logic.NOTICE_NONE) return 0;
        assertFalse(size + " no bulb", frame.bulb.isEmpty());
        Bitmap bulb = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
        Bitmap badge = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
        try {
            Canvas bulbCanvas = new Canvas(bulb);
            for (CanvasGeometry.BulbStroke stroke : frame.bulb) stroke.draw(bulbCanvas);
            Ui.Pal pal = Ui.pal(context);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(Ui.heavy(context));
            Notices.sign(new Canvas(badge), paint, pal, sign, at, pal.card);
            int changed = 0;
            for (int y = 0; y < Math.min(size.height, 32); y++) {
                for (int x = 0; x < size.width; x++) {
                    if (Color.alpha(bulb.getPixel(x, y)) >= 192 && Color.alpha(badge.getPixel(x, y)) >= 128) changed++;
                }
            }
            return changed;
        } finally {
            badge.recycle();
            bulb.recycle();
        }
    }

    private static int maxAlphaOutside(Bitmap bitmap, float[] sign) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        double radius = Math.min(24, height / 2.6);
        int max = 0;
        for (int y = 0; y < (int) radius; y++) {
            for (int x = 0; x < (int) radius; x++) {
                if (Math.hypot(radius - x - .5, radius - y - .5) <= radius + 1.5) continue;
                max = Math.max(max, alphaOutside(bitmap, sign, x, y));
                max = Math.max(max, alphaOutside(bitmap, sign, width - 1 - x, y));
                max = Math.max(max, alphaOutside(bitmap, sign, x, height - 1 - y));
                max = Math.max(max, alphaOutside(bitmap, sign, width - 1 - x, height - 1 - y));
            }
        }
        return max;
    }

    private static int alphaOutside(Bitmap bitmap, float[] sign, int x, int y) {
        if (sign != null && Math.hypot(x + .5 - sign[0], y + .5 - sign[1])
                <= sign[2] + Math.max(1f, sign[2] * .16f) + 1f) return 0;
        return Color.alpha(bitmap.getPixel(x, y));
    }

    private static void assertColumnColors(Context c, String label, Bitmap bitmap, WidgetArt.Model model, State state) {
        Ui.Pal palette = Ui.pal(c);
        int expectedRight = model.now.next;
        if (model.now.dayEnd || state == State.TOMORROW_CLEAR) expectedRight = model.now.state;
        int middle = bitmap.getHeight() / 2;
        if (!model.waiting && model.now.state != Logic.ON) {
            assertColor(label + " left", palette.soft(model.now.state), bitmap.getPixel(0, middle));
        } else if (!model.waiting) {
            // The bulb halo adds gold but does not change the dominant green.
            int pixel = bitmap.getPixel(0, middle);
            assertTrue(label + " expected green glow at left", Color.green(pixel) > Color.red(pixel)
                    && Color.green(pixel) > Color.blue(pixel));
        }
        assertColor(label + " right", model.waiting ? palette.card : palette.soft(expectedRight),
                bitmap.getPixel(bitmap.getWidth() - 1, middle));
    }

    private static void assertColor(String label, int expected, int actual) {
        assertEquals(label + " alpha", 255, Color.alpha(actual));
        for (int shift : new int[] {0, 8, 16}) {
            assertTrue(label + " expected " + Integer.toHexString(expected) + " was " + Integer.toHexString(actual),
                    Math.abs(((actual >> shift) & 255) - ((expected >> shift) & 255)) <= 3);
        }
    }
}
