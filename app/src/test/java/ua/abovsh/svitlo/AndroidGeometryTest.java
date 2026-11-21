package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AndroidGeometryTest extends AndroidRegressionFixture {
    private static final String SHARE = "69 %";
    private static final boolean[] THEMES = {false, true};

    @Test
    public void threeTilesKeepTheirNamesAndSharesInsideSeparateInkBounds() {
        for (boolean dark : THEMES) {
            Context sized = profile(360, dark);
            Ui.Pal palette = new Ui.Pal(dark);
            AdaptiveGrid grid = new AdaptiveGrid(sized, 2, 8);
            for (String name : new String[] {"Бібліотека", "Клуб", "Музей"}) {
                grid.addView(new PlaceTile(sized, palette, place("p1", name), powerNow(),
                        false, false, SHARE));
            }
            layOut(grid, Ui.dp(sized, 328));
            assertEquals(3, grid.getChildCount());
            assertEquals(grid.getChildAt(0).getWidth(), grid.getChildAt(1).getWidth());
            assertEquals(grid.getChildAt(0).getTop(), grid.getChildAt(1).getTop());
            assertTrue(grid.getChildAt(2).getTop() >= grid.getChildAt(0).getBottom());
            ViewGroup tile = (ViewGroup) grid.getChildAt(0);
            TextView name = text(tile, "Бібліотека");
            TextView share = text(tile, SHARE);
            assertEquals(1, name.getLineCount());
            assertComplete(name);
            assertComplete(share);
            assertFalse("Name and percentage ink must not overlap",
                    RectF.intersects(inkBounds(name), inkBounds(share)));
        }
    }

    @Test
    public void heroFitsPairedClocksAndTitlesAtBothWidthsAndThemes() {
        String until = "до 19:30";
        for (boolean dark : THEMES) {
            for (int width : new int[] {360, 280}) {
                Context sized = profile(width, dark);
                Logic.Now now = powerNow();
                String future = "Вимкнуть";
                HeroCard card = new HeroCard(sized, new Ui.Pal(dark), "Музей", now,
                        new String[] {future, until, ""}, false);
                layOut(card, Ui.dp(sized, width - 32));
                TextView leftClock = text(card, now.since);
                TextView rightClock = text(card, until);
                TextView leftTitle = text(card, now.title);
                TextView rightTitle = text(card, future);
                assertEquals(leftClock.getTextSize(), rightClock.getTextSize(), 0.01f);
                assertEquals(leftTitle.getTextSize(), rightTitle.getTextSize(), 0.01f);
                for (TextView view : new TextView[] {leftClock, rightClock, leftTitle, rightTitle}) {
                    assertComplete(view);
                    assertTrue("Text must lie within the card", view.getLeft() >= 0 && view.getRight() <= card.getWidth());
                }
            }
        }
    }

    @Test
    public void pictureGrowsForTwentyFourOutagesAndKeepsTheFooter() throws Exception {
        String slots = "31".repeat(24);
        assertEquals(24, Logic.outages(slots, -1).size());
        Logic.Place place = place("p1", "Музей");
        for (boolean dark : THEMES) {
            Context sized = profile(360, dark);
            var prefs = sized.getSharedPreferences(FEED, Context.MODE_PRIVATE);
            prefs.edit().putString(STORED_FEED, feed(DAY, ALL_LIGHT)).apply();
            Bitmap single = Share.draw(sized, place, DAY, NOW);
            prefs.edit().putString(STORED_FEED, feed(DAY, slots)).apply();
            Bitmap many = Share.draw(sized, place, DAY, NOW);
            try {
                assertEquals(single.getWidth(), many.getWidth());
                assertTrue("The outage tray needs additional rows", many.getHeight() > single.getHeight() + 150);
                int signature = Ui.pal(sized).accentText;
                int count = 0;
                for (int y = Math.max(0, many.getHeight() - 100); y < many.getHeight(); y++) {
                    for (int x = many.getWidth() / 2; x < many.getWidth(); x++) {
                        int pixel = many.getPixel(x, y);
                        if (Color.alpha(pixel) == 255 && pixel == signature) count++;
                    }
                }
                assertTrue("The signature must still be painted near the bottom", count > 10);
            } finally {
                single.recycle();
                many.recycle();
            }
        }
    }

    @Test
    public void intervalListNamesPossibleRestorationAfterAnOutage() {
        String slots = "0".repeat(24) + "3".repeat(4) + "1".repeat(20);
        var controller = Robolectric.buildActivity(AndroidNoticesTest.NoticeHost.class).create();
        try {
            DayIntervals.show(controller.get(), place("p1", "Музей"), DAY, () -> slots,
                    controller.get()::finish);
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            try {
                View row = findInterval(dialog.getWindow().getDecorView(), "12:00–14:00");
                assertNotNull("Missing the interval after the outage", row);
                assertTrue("Expected possible restoration in: " + row.getContentDescription(),
                        containsText(row, "Можливе увімкнення"));
            } finally {
                dialog.dismiss();
            }
        } finally {
            controller.destroy();
        }
    }

    private static View findInterval(View root, String interval) {
        CharSequence description = root.getContentDescription();
        if (description != null && description.toString().startsWith(interval)) return root;
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View row = findInterval(group.getChildAt(i), interval);
                if (row != null) return row;
            }
        }
        return null;
    }

    private static boolean containsText(View root, String expected) {
        if (root instanceof TextView label) return label.getText().toString().contains(expected);
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), expected)) return true;
            }
        }
        return false;
    }

    private Context profile(int width, boolean dark) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.screenWidthDp = width;
        configuration.fontScale = 2.0f;
        configuration.uiMode &= ~Configuration.UI_MODE_NIGHT_MASK;
        configuration.uiMode |= dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        return context.createConfigurationContext(configuration);
    }

    private static Logic.Now powerNow() {
        Logic.Now now = new Logic.Now();
        now.state = Logic.ON;
        now.title = "Світло є";
        now.since = "з 18:00";
        now.next = Logic.OFF;
        now.already = "уже годину";
        return now;
    }

    private static void layOut(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static TextView text(ViewGroup view, String exact) {
        for (int i = 0; i < view.getChildCount(); i++) {
            if (view.getChildAt(i) instanceof TextView visible && exact.contentEquals(visible.getText())) return visible;
        }
        throw new AssertionError("Text missing from view: " + exact);
    }

    private static void assertComplete(TextView view) {
        assertEquals(View.VISIBLE, view.getVisibility());
        Layout layout = view.getLayout();
        assertNotNull(layout);
        assertTrue(layout.getLineCount() > 0);
        assertEquals(view.getText().length(), layout.getLineEnd(layout.getLineCount() - 1));
        for (int i = 0; i < layout.getLineCount(); i++) {
            assertEquals(0, layout.getEllipsisCount(i));
            assertTrue(layout.getLineWidth(i) <= view.getWidth() + 1f);
        }
    }

    private static RectF inkBounds(TextView view) {
        Layout layout = view.getLayout();
        float x = view.getLeft() + view.getCompoundPaddingLeft();
        float y = view.getTop() + view.getTotalPaddingTop();
        return new RectF(x + layout.getLineLeft(0), y + layout.getLineTop(0),
                x + layout.getLineRight(0), y + layout.getLineBottom(0));
    }
}
