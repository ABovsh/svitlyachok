package ua.abovsh.svitlo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Whether a newer version is published on GitHub. When the app is opened, at most twice a day,
 * it asks github.com where "latest release" points (a redirect, no body, nothing about the user
 * is sent). The answer is shown on the main screen; the user decides whether to update.
 * "Оновити" opens the app's site, where the new version is downloaded in the browser; the app
 * itself downloads and installs nothing.
 */
final class Updates {
    static final int UNKNOWN = 0;
    static final int CURRENT = 1;
    static final int AVAILABLE = 2;
    static final int FAILED = 3;
    private static final String VERSION = "upd_version";
    private static final String CHECKED = "upd_checked";
    private static final String DISMISSED = "upd_dismissed";

    private Updates() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("updates", Context.MODE_PRIVATE);
    }

    /** The newer release the user has not skipped, or null. */
    static Logic.Release offered(Context c) {
        Logic.Release r = latest(c);
        if (r == null || r.version.equals(p(c).getString(DISMISSED, ""))) return null;
        return r;
    }

    /** The newest release known, if it is newer than this app; null otherwise. */
    static Logic.Release latest(Context c) {
        if (BuildConfig.IS_RC) return null;  // RCs are installed separately, never from the stable feed
        String v = p(c).getString(VERSION, "");
        if (!Logic.newer(v, BuildConfig.VERSION_NAME)) return null;
        try {
            return Logic.releaseAt(Logic.REPO + "releases/tag/v" + v);
        } catch (Logic.BadFile e) {
            return null;
        }
    }

    /** The latest version GitHub named at the last check, or null before any (and in an RC). */
    static String known(Context c) {
        if (BuildConfig.IS_RC) return null;
        String v = p(c).getString(VERSION, "");
        return v.isEmpty() ? null : v;
    }

    /** A skipped version wanted after all: the main screen offers it again. */
    static void undismiss(Context c) {
        p(c).edit().remove(DISMISSED).apply();
    }

    /** "Пропустити цю версію": not offered on the main screen again; a newer one will be. */
    static void dismiss(Context c, String version) {
        p(c).edit().putString(DISMISSED, version).apply();
    }

    /** On opening the app: check in the background when due, then `done` on that thread. */
    static void checkIfDue(Context c, Runnable done) {
        if (BuildConfig.IS_RC) return;
        Context app = c.getApplicationContext();
        // always on: a skipped version stays skipped, a newer one is offered again
        if (!Logic.dueForUpdateCheck(p(app).getLong(CHECKED, 0), Sync.now())) return;
        new Thread(() -> {
            check(app);
            done.run();
        }).start();
    }

    /** Blocking: ask GitHub now. UNKNOWN never comes back from here. */
    static int check(Context c) {
        p(c).edit().putLong(CHECKED, Sync.now()).apply();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(Logic.LATEST_RELEASE).openConnection();
            conn.setInstanceFollowRedirects(false);  // the address it points to is the answer
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Svitlyachok/" + BuildConfig.VERSION_NAME + " (Android)");
            int code = conn.getResponseCode();
            String to = conn.getHeaderField("Location");
            if (code / 100 != 3 || to == null) return FAILED;
            String where = new URL(new URL(Logic.LATEST_RELEASE), to).toString();
            if (where.equals(Logic.REPO + "releases")) {  // nothing published yet
                p(c).edit().putLong(CHECKED, Sync.now()).apply();
                return CURRENT;
            }
            Logic.Release r = Logic.releaseAt(where);
            p(c).edit().putString(VERSION, r.version).putLong(CHECKED, Sync.now()).apply();
            return Logic.newer(r.version, BuildConfig.VERSION_NAME) ? AVAILABLE : CURRENT;
        } catch (IOException | Logic.BadFile | RuntimeException e) {
            return FAILED;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * After an update: the APK that versions up to 1.9.0 downloaded into the cache is not needed.
     * This version downloads nothing; the new one comes from the site, in the browser.
     */
    static void forgetDownload(Context c) {
        File dir = new File(c.getCacheDir(), "update");
        for (String name : new String[] {"update.apk", "update.part"}) deleteQuietly(new File(dir, name));
        deleteQuietly(dir);
        deleteQuietly(new File(c.getCacheDir(), "update.apk"));
    }

    private static void deleteQuietly(File f) {
        try {
            java.nio.file.Files.deleteIfExists(f.toPath());
        } catch (IOException e) {
            f.deleteOnExit();
        }
    }

    /** Open a page of this app's repository or site in the browser; nothing else is ever opened. */
    static void open(Activity a, String url) {
        if (!Logic.opens(url)) return;
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(a, "Немає браузера, щоб відкрити " + url, Toast.LENGTH_LONG).show();
        }
    }
}
