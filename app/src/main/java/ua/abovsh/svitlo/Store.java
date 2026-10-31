package ua.abovsh.svitlo;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
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
    static final int FAILED = 0, SAME = 1, NEW = 2;
    private static final int MAX_BYTES = 2_000_000;  // the region files are 10–60 KB
    private static final Map<String, Logic.Data> parsed = new HashMap<>();
    private static final Map<String, Integer> parsedHash = new HashMap<>();

    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("feed", Context.MODE_PRIVATE);
    }

    static synchronized Logic.Data data(Context c, String region) {
        String raw = prefs(c).getString("json_" + region, null);
        if (raw == null) return null;
        Integer h = parsedHash.get(region);
        if (h != null && h == raw.hashCode()) return parsed.get(region);
        try {
            Logic.Data d = Logic.parse(raw);
            parsed.put(region, d);
            parsedHash.put(region, raw.hashCode());
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** Last moment the source answered (new data or "not modified"). */
    static long checkedAt(Context c, String region) {
        return prefs(c).getLong("checked_" + region, 0);
    }

    static synchronized int fetch(Context c, String region) {
        Logic.Region r = Logic.region(region);
        if (r == null) return FAILED;
        SharedPreferences p = prefs(c);
        String etag = p.getString("etag_" + region, null);
        boolean haveData = p.contains("json_" + region);
        for (String url : r.urls) {
            if (!url.startsWith("https://")) continue;  // never plain HTTP
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Svitlyachok/" + BuildConfig.VERSION_NAME + " (Android)");
                // the ETag belongs to the URL that gave it: only the first, preferred one
                if (etag != null && haveData && url.equals(p.getString("url_" + region, ""))) {
                    conn.setRequestProperty("If-None-Match", etag);
                }
                int code = conn.getResponseCode();
                long now = System.currentTimeMillis() / 1000;
                if (code == 304) {
                    p.edit().putLong("checked_" + region, now).apply();
                    return SAME;
                }
                if (code != 200) continue;
                String raw = read(conn);
                Logic.parse(raw);  // reject garbage before it replaces good data
                boolean same = raw.equals(p.getString("json_" + region, null));
                p.edit().putString("json_" + region, raw)
                        .putString("etag_" + region, conn.getHeaderField("ETag"))
                        .putString("url_" + region, url)
                        .putLong("checked_" + region, now)
                        .apply();
                return same ? SAME : NEW;
            } catch (Exception ignored) {
                // try the next source
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return FAILED;
    }

    private static String read(HttpURLConnection conn) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new Exception("too big");
            }
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
