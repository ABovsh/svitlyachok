package ua.abovsh.svitlo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lends two files, read-only, by a one-time grant: the shared picture to the app the person
 * picked, and a downloaded update to the system installer. Not exported: no other app can ask
 * for anything here, and no other file is ever served.
 */
public class ShareProvider extends ContentProvider {
    static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".share";

    private static final String APK_TYPE = "application/vnd.android.package-archive";

    static Uri uri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(Share.FILE).build();
    }

    static Uri apkUri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(Updates.APK).build();
    }

    /** The file behind the address, or null when it is not one of the two. */
    private File file(Uri u) {
        if (!AUTHORITY.equals(u.getAuthority())) return null;
        if (("/" + Share.FILE).equals(u.getPath())) return Share.picture(getContext());
        if (("/" + Updates.APK).equals(u.getPath())) return Updates.updateFile(getContext());
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        File f = file(u);
        if (f == null || !"r".equals(mode)) throw new FileNotFoundException(u.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri u) {
        File f = file(u);
        if (f == null) return null;
        return f.getName().equals(Updates.APK) ? APK_TYPE : "image/png";
    }

    /** Name and size: some messengers ask before they read. */
    @Override
    public Cursor query(Uri u, String[] projection, String selection, String[] args, String order) {
        File f = file(u);
        if (f == null) return null;
        MatrixCursor c = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        boolean apk = f.getName().equals(Updates.APK);
        c.addRow(new Object[] {apk ? "Svitlyachok.apk" : "svitlyachok-grafik.png", f.length()});
        return c;
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException("read-only");
    }
}
