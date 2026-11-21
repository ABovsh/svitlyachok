package ua.abovsh.svitlo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import org.junit.Test;
import org.robolectric.Robolectric;

public class AndroidNoticesTest extends AndroidRegressionFixture {
    public static class NoticeHost extends Base {
        @Override void render() {
            // The test drives the notice strip without a full screen.
        }
    }

    @Test
    public void selectedRegionOnlyShowsItsOwnNotice() {
        var controller = Robolectric.buildActivity(NoticeHost.class).create();
        try {
            NoticeHost screen = controller.get();
            Logic.Data empty = new Logic.Data();
            Logic.Data city = new Logic.Data();
            city.notice = "У місті діють екстрені відключення.";
            Logic.Data region = new Logic.Data();
            region.notice = "Діють стабілізаційні відключення.";

            assertNull(Notices.strip(screen, empty, RegionalFeed.CV));
            View cityStrip = Notices.strip(screen, city, "kyiv");
            View regionStrip = Notices.strip(screen, region, "kyiv_region");
            assertNotNull(cityStrip);
            assertNotNull(regionStrip);
            String cityText = cityStrip.getContentDescription().toString();
            String regionText = regionStrip.getContentDescription().toString();
            assertTrue(cityText.contains("Екстрені відключення в місті"));
            assertTrue(regionText.contains("Стабілізаційні відключення"));
            assertFalse(regionText.contains("Екстрені"));
        } finally {
            controller.destroy();
        }
    }
}
