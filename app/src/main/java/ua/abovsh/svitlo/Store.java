package ua.abovsh.svitlo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Bounded conditional requests; an obsolete primary source never hides a usable fallback. */
final class Store {
    static final int FAILED = 0;
    static final int SAME = 1;
    static final int NEW = 2;
    private static final int MAX_BYTES = 2_000_000;
    private static final Map<String, Logic.Data> parsed = new HashMap<>();
    private static final Map<String, String> parsedRaw = new HashMap<>();
    private static final ReentrantLock FETCHING = new ReentrantLock();
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "request-deadline"); t.setDaemon(true); return t;
    });
    private Store() {}

    /** A job owns its cancellation; stopping one job cannot disconnect another caller. */
    static final class Request {
        volatile boolean stopped;
        private volatile HttpURLConnection connection;
        void cancel() { stopped = true; HttpURLConnection c = connection; if (c != null) c.disconnect(); }
        void connect(HttpURLConnection c) throws IOException {
            connection = c;
            if (stopped) { c.disconnect(); throw new IOException("stopped"); }
        }
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("feed", Context.MODE_PRIVATE); }
    static synchronized Logic.Data data(Context c, String region) {
        String raw = prefs(c).getString("json_" + region, null);
        if (raw == null) return null;
        Logic.Data d = parsed.get(region);
        if (!raw.equals(parsedRaw.get(region))) {
            try { d = Logic.parse(raw); parsed.put(region, d); parsedRaw.put(region, raw); }
            catch (Logic.BadFile e) { return null; }
        }
        return Logic.validAt(d, Sync.now());
    }
    static long checkedAt(Context c, String region) { return prefs(c).getLong("checked_" + region, 0); }
    static boolean online(Context c) {
        ConnectivityManager m = c.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = m.getNetworkCapabilities(m.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    static int fetch(Context c, String region) { return fetch(c, region, Long.MAX_VALUE); }
    static int fetch(Context c, String region, long deadlineMs) {
        return fetch(c, region, deadlineMs, true, new Request());
    }
    static int fetch(Context c, String region, long deadlineMs, boolean always, Request request) {
        if (!online(c) || request.stopped) return FAILED;
        long remaining = deadlineMs == Long.MAX_VALUE ? 30_000 : Math.min(30_000, deadlineMs - System.currentTimeMillis());
        if (remaining <= 0) return FAILED;
        long end = SystemClock.elapsedRealtime() + remaining;
        boolean locked = false;
        try {
            locked = FETCHING.tryLock(remaining, TimeUnit.MILLISECONDS);
            if (!locked || request.stopped) return FAILED;
            if (!always && !Logic.dueForFetch(checkedAt(c, region), Sync.now())) return SAME;
            Logic.Region source = Logic.region(region);
            if (source == null) return FAILED;
            int best = FAILED;
            for (String url : source.urls) {
                if (request.stopped || SystemClock.elapsedRealtime() >= end) break;
                int result = fetchFrom(c, region, url, end, request);
                if (result != FAILED) {
                    best = best == NEW || result == NEW ? NEW : SAME;
                    if (Logic.currentData(data(c, region), Sync.now())) break;
                }
            }
            return best;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return FAILED;
        } finally { if (locked) FETCHING.unlock(); }
    }
    private static int fetchFrom(Context c, String region, String url, long end, Request request) {
        if (!url.startsWith("https://")) return FAILED;
        SharedPreferences p = prefs(c);
        HttpURLConnection conn = null;
        ScheduledFuture<?> watchdog = null;
        try {
            int remaining = (int) Math.max(1, end - SystemClock.elapsedRealtime());
            conn = (HttpURLConnection) new URL(url).openConnection();
            request.connect(conn);
            HttpURLConnection active = conn;
            watchdog = DEADLINES.schedule(active::disconnect, remaining, TimeUnit.MILLISECONDS);
            conn.setConnectTimeout(Math.min(8000, remaining));
            conn.setReadTimeout(Math.min(10000, remaining));
            conn.setRequestProperty("User-Agent", "Svitlyachok/" + BuildConfig.VERSION_NAME + " (Android)");
            String etag = p.getString("etag_" + region, null);
            boolean validCache = data(c, region) != null;
            if (etag != null && validCache && url.equals(p.getString("url_" + region, ""))) {
                conn.setRequestProperty("If-None-Match", etag);
            }
            int code = conn.getResponseCode();
            if (request.stopped || SystemClock.elapsedRealtime() >= end) return FAILED;
            if (code == 304 && validCache) {
                p.edit().putLong("checked_" + region, Sync.now()).apply(); return SAME;
            }
            if (code != 200) return FAILED;
            String raw = read(conn, end, request);
            Logic.Data candidate = Logic.parse(raw);
            if (request.stopped || SystemClock.elapsedRealtime() >= end) return FAILED;
            if (!Logic.preferData(candidate, data(c, region), Sync.now())) {
                p.edit().putLong("checked_" + region, Sync.now()).apply(); return SAME;
            }
            boolean same = raw.equals(p.getString("json_" + region, null));
            p.edit().putString("json_" + region, raw).putString("etag_" + region, conn.getHeaderField("ETag"))
                    .putString("url_" + region, url).putLong("checked_" + region, Sync.now()).apply();
            return same ? SAME : NEW;
        } catch (IOException | Logic.BadFile | RuntimeException e) { return FAILED; }
        finally {
            if (watchdog != null) watchdog.cancel(false);
            request.connection = null;
            if (conn != null) conn.disconnect();
        }
    }
    private static String read(HttpURLConnection conn, long end, Request request) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1;) {
                if (request.stopped || SystemClock.elapsedRealtime() >= end) throw new IOException("deadline");
                if (out.size() + n > MAX_BYTES) throw new IOException("too large");
                out.write(buf, 0, n);
            }
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
