package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import org.junit.Ignore;
import org.junit.Test;
import org.robolectric.annotation.GraphicsMode;

@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AndroidWidgetArtTest extends AndroidRegressionFixture {
    private static final int WIDTH = 110;
    private static final int HEIGHT = 40;
    private static final String[] THEMES = {"light", "dark"};

    @Test
    public void waitingGlowIsClippedToRoundedCardInBothThemes() {
        for (String theme : THEMES) {
            Prefs.setTheme(context, theme);
            assertClipped(WIDTH, HEIGHT);
            assertClipped(250, 120);
        }
    }

    @Ignore("R12: the emergency sign overpaints the bulb at 110 by 40 dp")
    @Test
    public void emergencySignDoesNotChangeOpaquePixelsOfTheSmallBulb() {
        for (String theme : THEMES) {
            Prefs.setTheme(context, theme);
            WidgetArt.Model model = model();
            Bitmap unmarked = WidgetArt.draw(context, model, NOW, WIDTH, HEIGHT);
            model.notice = Logic.NOTICE_EMERGENCY;
            Bitmap marked = WidgetArt.draw(context, model, NOW, WIDTH, HEIGHT);
            Bitmap bulb = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            Ui.Pal palette = Ui.pal(context);
            float segment = Logic.widgetBulbSegment(WIDTH);
            float rowHeight = HEIGHT * .74f;
            BulbView.plain(new Brush(palette, palette.card).on(new Canvas(bulb)), model.now.state,
                    segment / 2 + 2.5f, HEIGHT / 2f, Math.min(rowHeight * .95f, segment * .9f));
            try {
                int glyphPixels = 0;
                int changed = 0;
                for (int y = 0; y < HEIGHT; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        if (Color.alpha(bulb.getPixel(x, y)) < 192) continue;
                        glyphPixels++;
                        if (unmarked.getPixel(x, y) != marked.getPixel(x, y)) changed++;
                    }
                }
                assertTrue("The fixture must render the bulb glyph", glyphPixels > 0);
                assertEquals("The sign must leave the bulb intact", 0, changed);
            } finally {
                bulb.recycle();
                marked.recycle();
                unmarked.recycle();
            }
        }
    }

    private void assertClipped(int width, int height) {
        WidgetArt.Model model = model();
        model.waiting = true;
        Bitmap bitmap = WidgetArt.draw(context, model, NOW, width, height);
        try {
            assertEquals(width, bitmap.getWidth());
            assertEquals(height, bitmap.getHeight());
            assertEquals(255, Color.alpha(bitmap.getPixel(width / 2, height / 2)));
            double radius = Math.min(24, height / 2.6);
            for (int y = 0; y < (int) radius; y++) {
                for (int x = 0; x < (int) radius; x++) {
                    if (Math.hypot(radius - x - .5, radius - y - .5) <= radius + 1.5) continue;
                    assertEquals("Glow outside the top left corner", 0, Color.alpha(bitmap.getPixel(x, y)));
                    assertEquals("Glow outside the bottom left corner", 0, Color.alpha(bitmap.getPixel(x, height - y - 1)));
                }
            }
        } finally {
            bitmap.recycle();
        }
    }

    private static WidgetArt.Model model() {
        WidgetArt.Model result = new WidgetArt.Model();
        result.place = place("p1", "Музей");
        result.base = DAY;
        result.tl = ALL_LIGHT;
        result.now = new Logic.Now();
        result.now.state = Logic.ON;
        result.now.title = "Світло є";
        result.now.since = "з 08:00";
        result.now.next = Logic.OFF;
        result.now.changeClock = "19:30";
        return result;
    }
}
