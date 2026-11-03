package ua.abovsh.svitlo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded requests. A check costs little while the schedule stands: the end of a file shows the source's
 * hash of it, and the file comes only when that changes. The schedule is written then too; everything a
 * check updates lives apart, in a small file. An obsolete primary source never hides a usable fallback.
 */
final class Store {
    static final int FAILED = 0;
    static final int SAME = 1;
    static final int NEW = 2;
    private static final String JSON = "json_";
    private static final String CHECKED = "checked_";
    private static final String ETAG = "etag_";
    private static final String URL_OF = "url_";
    private static final String WHOLE = "full_";
    private static final String SOURCE = "source_";
    private static final int MAX_BYTES = 2_000_000;
    private static final int TAIL_BYTES = 600;
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
        private final java.util.concurrent.atomic.AtomicReference<Thread> worker = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicReference<HttpURLConnection> connection = new java.util.concurrent.atomic.AtomicReference<>();
        void cancel() {
            stopped = true;
            Thread thread = worker.get(); if (thread != null) thread.interrupt();
            HttpURLConnection c = connection.getAndSet(null); if (c != null) DEADLINES.execute(c::disconnect);
        }
        void connect(HttpURLConnection c) throws IOException {
            connection.set(c);
            if (stopped) { c.disconnect(); throw new IOException("stopped"); }
        }
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("feed", Context.MODE_PRIVATE); }
    private static SharedPreferences state(Context c) { return c.getSharedPreferences("feed_state", Context.MODE_PRIVATE); }
    static synchronized Logic.Data data(Context c, String region) {
        String raw = prefs(c).getString(JSON + region, null);
        if (raw == null) return null;
        Logic.Data d = parsed.get(region);
        if (!raw.equals(parsedRaw.get(region))) {
            try { d = Logic.parse(raw); parsed.put(region, d); parsedRaw.put(region, raw); }
            catch (Logic.BadFile e) { return null; }
        }
        Logic.touch(d, state(c).getLong(SOURCE + region, 0));
        return Logic.validAt(d, Sync.now());
    }
    /** Versions before 1.7.1 kept these with the schedule: they are read there until the next check. */
    static long checkedAt(Context c, String region) {
        return state(c).getLong(CHECKED + region, prefs(c).getLong(CHECKED + region, 0));
    }
    private static String saved(Context c, String key) {
        return state(c).getString(key, prefs(c).getString(key, null));
    }
    /** Regions no place uses leave the phone. */
    static synchronized void keepOnly(Context c, Set<String> regions) {
        for (SharedPreferences p : new SharedPreferences[] {prefs(c), state(c)}) {
            SharedPreferences.Editor e = p.edit();
            for (String key : Logic.cacheKeysOutside(p.getAll().keySet(), regions)) e.remove(key);
            e.apply();
        }
        parsed.keySet().retainAll(regions);
        parsedRaw.keySet().retainAll(regions);
    }
    static boolean online(Context c) {
        ConnectivityManager m = c.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = m.getNetworkCapabilities(m.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    static int fetch(Context c, String region, long deadlineMs, boolean always, Request request) {
        if (!online(c) || request.stopped) return FAILED;
        long remaining = deadlineMs == Long.MAX_VALUE ? 30_000 : Math.min(30_000, deadlineMs - System.currentTimeMillis());
        if (remaining <= 0) return FAILED;
        long end = SystemClock.elapsedRealtime() + remaining;
        request.worker.set(Thread.currentThread());
        boolean locked = false;
        try {
            locked = FETCHING.tryLock(remaining, TimeUnit.MILLISECONDS);
            if (!locked || request.stopped) return FAILED;
            return fetchLocked(c, region, end, always, request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return FAILED;
        } finally { request.worker.compareAndSet(Thread.currentThread(), null); if (locked) FETCHING.unlock(); }
    }
    private static int fetchLocked(Context c, String region, long end, boolean always, Request request) {
        if (!always && !Logic.dueForFetch(checkedAt(c, region), Sync.now())) return SAME;
        Logic.Region source = Logic.region(region);
        if (source == null) return FAILED;
        int best = FAILED;
        for (String url : source.urls) {
            if (expired(request, end)) break;
            int result = fetchFrom(c, region, url, end, request);
            if (result != FAILED) {
                best = best == NEW || result == NEW ? NEW : SAME;
                if (Logic.currentData(data(c, region), Sync.now())) return best;
            }
        }
        return best;
    }
    private static boolean expired(Request request, long end) {
        return request.stopped || SystemClock.elapsedRealtime() >= end;
    }
    private static int fetchFrom(Context c, String region, String url, long end, Request request) {
        if (!url.startsWith("https://")) return FAILED;
        return unchanged(c, region, url, end, request) ? SAME : download(c, region, url, end, request);
    }

    /** The last bytes of the file: true when they prove the stored schedule is still the source's. */
    private static boolean unchanged(Context c, String region, String url, long end, Request request) {
        Logic.Data cached = data(c, region);
        long now = Sync.now();
        if (cached == null || !url.equals(saved(c, URL_OF + region))
                || Logic.needsWholeFile(cached.hash, state(c).getLong(WHOLE + region, 0), now)) return false;
        try (Call call = new Call(url, end, request)) {
            call.conn.setRequestProperty("Accept-Encoding", "identity");
            call.conn.setRequestProperty("Range", "bytes=-" + TAIL_BYTES);
            if (call.conn.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) return false;
            Logic.Tail tail = Logic.tail(call.body(end));
            if (tail == null || !tail.hash.equals(cached.hash) || expired(request, end)) return false;
            state(c).edit().putLong(CHECKED + region, Sync.now()).putLong(SOURCE + region, tail.at).apply();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;  // the whole file decides, or says what is wrong
        }
    }

    private static int download(Context c, String region, String url, long end, Request request) {
        try (Call call = new Call(url, end, request)) {
            String etag = saved(c, ETAG + region);
            boolean validCache = data(c, region) != null;
            if (etag != null && validCache && url.equals(saved(c, URL_OF + region))) {
                call.conn.setRequestProperty("If-None-Match", etag);
            }
            int code = call.conn.getResponseCode();
            if (expired(request, end)) return FAILED;
            if (code == 304 && validCache) {
                call.finishEmpty();
                state(c).edit().putLong(CHECKED + region, Sync.now()).apply();
                return SAME;
            }
            if (code != 200) return FAILED;
            String raw = call.body(end);
            Logic.Data candidate = Logic.parse(raw);
            if (expired(request, end)) return FAILED;
            if (!Logic.preferData(candidate, data(c, region), Sync.now())) {
                state(c).edit().putLong(CHECKED + region, Sync.now()).apply();
                return SAME;
            }
            String kept = Logic.compactFile(raw);
            boolean same = kept.equals(prefs(c).getString(JSON + region, null));
            if (!same) prefs(c).edit().putString(JSON + region, kept).remove(CHECKED + region).remove(ETAG + region).remove(URL_OF + region).apply();
            state(c).edit().putString(ETAG + region, call.conn.getHeaderField("ETag")).putString(URL_OF + region, url)
                    .putLong(CHECKED + region, Sync.now()).putLong(WHOLE + region, Sync.now()).remove(SOURCE + region).apply();
            return same ? SAME : NEW;
        } catch (IOException | Logic.BadFile | RuntimeException e) {
            return FAILED;
        }
    }

    /** One request within its deadline. A connection read to its end stays open for the next region. */
    private static final class Call implements Closeable {
        final HttpURLConnection conn;
        private final Request request;
        private final ScheduledFuture<?> watchdog;
        private boolean reusable;

        Call(String url, long end, Request request) throws IOException {
            this.request = request;
            int remaining = (int) Math.max(1, end - SystemClock.elapsedRealtime());
            conn = (HttpURLConnection) new URL(url).openConnection();
            request.connect(conn);
            watchdog = DEADLINES.schedule(conn::disconnect, remaining, TimeUnit.MILLISECONDS);
            conn.setConnectTimeout(Math.min(8000, remaining));
            conn.setReadTimeout(Math.min(10000, remaining));
            conn.setRequestProperty("User-Agent", "Svitlyachok/" + BuildConfig.VERSION_NAME + " (Android)");
        }

        String body(long end) throws IOException {
            String text = read(conn, end, request);
            reusable = true;
            return text;
        }

        /** A 304 has no body: closing it hands the connection back. */
        void finishEmpty() {
            try { conn.getInputStream().close(); reusable = true; }
            catch (IOException | RuntimeException ignored) { reusable = false; }
        }

        @Override public void close() {
            watchdog.cancel(false);
            request.connection.compareAndSet(conn, null);
            if (!reusable) conn.disconnect();
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
