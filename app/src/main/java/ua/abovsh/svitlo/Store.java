package ua.abovsh.svitlo;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The last good file of each region, straight from its public source on GitHub. A
 * conditional GET (ETag) answers 304 with no body while nothing changed, so a check
 * every 15 minutes costs almost no traffic. No server of ours sits in between.
 */
final class Store {
    static final int FAILED = 0;
    static final int SAME = 1;
    static final int NEW = 2;
    private static final int MAX_BYTES = 2_000_000;  // the region files are 10–60 KB
    private static final Map<String, Logic.Data> parsed = new HashMap<>();
    private static final Map<String, Integer> parsedHash = new HashMap<>();

    private Store() {}

    private static final String JSON = "json_";
    private static final String ETAG = "etag_";
    private static final String URL_OF = "url_";
    private static final String CHECKED = "checked_";
    private static final int NEXT = -1;  // this address did not answer usefully: try the next one

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("feed", Context.MODE_PRIVATE);
    }

    static synchronized Logic.Data data(Context c, String region) {
        String raw = prefs(c).getString(JSON + region, null);
        if (raw == null) return null;
        Integer h = parsedHash.get(region);
        if (h != null && h == raw.hashCode()) return parsed.get(region);
        try {
            Logic.Data d = Logic.parse(raw);
            parsed.put(region, d);
            parsedHash.put(region, raw.hashCode());
            return d;
        } catch (Logic.BadFile e) {
            return null;
        }
    }

    /** Last moment the source answered (new data or "not modified"). */
    static long checkedAt(Context c, String region) {
        return prefs(c).getLong(CHECKED + region, 0);
    }

    /** Network calls queue here, apart from `data`: the screen never waits for a slow answer. */
    private static final Object FETCHING = new Object();

    static int fetch(Context c, String region) {
        return fetch(c, region, Long.MAX_VALUE);
    }

    /** The next address is not tried after `deadlineMs`. */
    static int fetch(Context c, String region, long deadlineMs) {
        synchronized (FETCHING) {
            return fetchAny(c, region, deadlineMs);
        }
    }

    private static int fetchAny(Context c, String region, long deadlineMs) {
        Logic.Region r = Logic.region(region);
        if (r == null) return FAILED;
        int result = NEXT;
        for (int i = 0; i < r.urls.length && result == NEXT && System.currentTimeMillis() <= deadlineMs; i++) {
            if (r.urls[i].startsWith("https://")) result = fetchFrom(c, region, r.urls[i]);  // never plain HTTP
        }
        return result == NEXT ? FAILED : result;
    }

    /** One address: SAME, NEW, or NEXT when it failed or sent something that is not a schedule. */
    private static int fetchFrom(Context c, String region, String url) {
        SharedPreferences p = prefs(c);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Svitlyachok/" + BuildConfig.VERSION_NAME + " (Android)");
            // the ETag belongs to the address that gave it
            String etag = p.getString(ETAG + region, null);
            if (etag != null && p.contains(JSON + region) && url.equals(p.getString(URL_OF + region, ""))) {
                conn.setRequestProperty("If-None-Match", etag);
            }
            int code = conn.getResponseCode();
            long now = System.currentTimeMillis() / 1000;
            if (code == 304) {
                p.edit().putLong(CHECKED + region, now).apply();
                return SAME;
            }
            if (code != 200) return NEXT;
            String raw = read(conn);
            Logic.parse(raw);  // reject garbage before it replaces good data
            boolean same = raw.equals(p.getString(JSON + region, null));
            p.edit().putString(JSON + region, raw)
                    .putString(ETAG + region, conn.getHeaderField("ETag"))
                    .putString(URL_OF + region, url)
                    .putLong(CHECKED + region, now)
                    .apply();
            return same ? SAME : NEW;
        } catch (IOException | Logic.BadFile | RuntimeException e) {
            return NEXT;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String read(HttpURLConnection conn) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            for (int n = in.read(buf); n > 0; n = in.read(buf)) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new IOException("larger than " + MAX_BYTES + " bytes");
            }
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
