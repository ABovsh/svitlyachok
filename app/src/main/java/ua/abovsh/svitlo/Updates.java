package ua.abovsh.svitlo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.IntConsumer;

/**
 * Whether a newer version is published on GitHub. When the app is opened, at most twice a day,
 * it asks github.com where "latest release" points (a redirect, no body, nothing about the user
 * is sent). The answer is shown on the main screen; the user decides whether to download it.
 * "Завантажити" downloads the APK of that release into the app and hands it to the system
 * installer, which installs it only over the same signature.
 */
final class Updates {
    static final int UNKNOWN = 0;
    static final int CURRENT = 1;
    static final int AVAILABLE = 2;
    static final int FAILED = 3;
    private static final String VERSION = "upd_version";
    private static final String CHECKED = "upd_checked";
    private static final String DISMISSED = "upd_dismissed";
    private static final String WAITING = "upd_waiting";
    static final String APK = "update.apk";
    private static final long MAX_APK = 30L * 1024 * 1024;

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
        String v = p(c).getString(VERSION, "");
        if (!Logic.newer(v, BuildConfig.VERSION_NAME)) return null;
        try {
            return Logic.releaseAt(Logic.REPO + "releases/tag/v" + v);
        } catch (Logic.BadFile e) {
            return null;
        }
    }

    /** "Пропустити цю версію": not offered on the main screen again; a newer one will be. */
    static void dismiss(Context c, String version) {
        p(c).edit().putString(DISMISSED, version).apply();
    }

    /** On opening the app: check in the background when due, then `done` on that thread. */
    static void checkIfDue(Context c, Runnable done) {
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

    /** The one downloaded APK, in the app's own cache. */
    static File apk(Context c) {
        File dir = new File(c.getCacheDir(), "update");
        if (!dir.isDirectory() && !dir.mkdirs()) return new File(c.getCacheDir(), APK);
        return new File(dir, APK);
    }

    /**
     * Blocking: downloads the APK of `r` with the percent so far, following only redirects to
     * GitHub's file hosts. Kept only if it is this app in exactly that version; else null.
     */
    static File download(Context c, Logic.Release r, IntConsumer percent) {
        File f = apk(c);
        String url = r.apk;
        try {
            for (int hop = 0; hop < 5; hop++) {
                if (!Logic.trustedDownload(url)) return null;
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                try {
                    conn.setInstanceFollowRedirects(false);  // every address is checked first
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(20000);
                    conn.setRequestProperty("User-Agent", "Svitlyachok/" + BuildConfig.VERSION_NAME + " (Android)");
                    int code = conn.getResponseCode();
                    if (code / 100 == 3) {
                        String to = conn.getHeaderField("Location");
                        if (to == null) return null;
                        url = new URL(new URL(url), to).toString();
                        continue;
                    }
                    if (code != 200) return null;
                    if (!save(conn, f, percent)) return null;
                    return check(c, f, r.version) ? f : null;
                } finally {
                    conn.disconnect();
                }
            }
        } catch (IOException | RuntimeException e) {
            // no network, or GitHub answered something else: the browser is the way then
        }
        deleteQuietly(f);
        return null;
    }

    private static boolean save(HttpURLConnection conn, File f, IntConsumer percent) throws IOException {
        long total = conn.getContentLengthLong();
        if (total > MAX_APK) return false;
        long got = 0;
        int shown = -1;
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            for (int n; (n = in.read(buf)) > 0; ) {
                got += n;
                if (got > MAX_APK) return false;
                out.write(buf, 0, n);
                int p = total > 0 ? (int) (got * 100 / total) : 0;
                if (p != shown) percent.accept(shown = p);
            }
        }
        return total <= 0 || got == total;
    }

    /** The file is this app, in the version the release names. */
    private static boolean check(Context c, File f, String version) {
        PackageInfo info = c.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
        boolean ok = info != null && c.getPackageName().equals(info.packageName) && version.equals(info.versionName);
        if (!ok) deleteQuietly(f);
        return ok;
    }

    private static void deleteQuietly(File f) {
        if (f.exists() && !f.delete()) f.deleteOnExit();
    }

    /**
     * "Завантажити": in the background, with the percent on the UI thread; then the installer, or,
     * if the download failed, the release file in the browser as before. `done` runs at the end.
     */
    static void get(Activity a, Logic.Release r, IntConsumer progress, Runnable done) {
        Context app = a.getApplicationContext();
        new Thread(() -> {
            File f = download(app, r, p -> a.runOnUiThread(() -> progress.accept(p)));
            a.runOnUiThread(() -> {
                done.run();
                if (a.isFinishing()) return;
                if (f != null) {
                    install(a);
                } else {
                    Toast.makeText(a, "Не вдалося завантажити — відкриваю сторінку випуску", Toast.LENGTH_LONG).show();
                    open(a, r.apk);
                }
            });
        }).start();
    }

    /**
     * Hand the downloaded APK to the system installer. The first time, Android asks to allow this
     * app to install apps; the install goes on when the person comes back (`resume`).
     */
    static void install(Activity a) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            p(a).edit().putBoolean(WAITING, true).apply();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (RuntimeException e) {
                Toast.makeText(a, "Дозвольте Світлячку встановлювати застосунки в налаштуваннях телефона",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        p(a).edit().putBoolean(WAITING, false).apply();
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(ShareProvider.apkUri(), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(a, "Не вдалося відкрити встановлення", Toast.LENGTH_LONG).show();
        }
    }

    /** Back from the permission screen: install if it was allowed and the file is still there. */
    static void resume(Activity a) {
        if (!p(a).getBoolean(WAITING, false)) return;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            p(a).edit().putBoolean(WAITING, false).apply();
            return;
        }
        if (apk(a).isFile()) install(a);
        else p(a).edit().putBoolean(WAITING, false).apply();
    }

    /** Open a page of this app's repository in the browser; nothing else is ever opened. */
    static void open(Activity a, String url) {
        if (!url.startsWith(Logic.REPO)) return;
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(a, "Немає браузера, щоб відкрити " + url, Toast.LENGTH_LONG).show();
        }
    }
}
