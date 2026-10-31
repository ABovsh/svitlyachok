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
 * Lends the one shared picture to the app the person picked, read-only, for as long as that app
 * holds the grant. Not exported: no other app can ask for anything here, and no other file is
 * ever served.
 */
public class ShareProvider extends ContentProvider {
    static final String AUTHORITY = "ua.abovsh.svitlo.share";

    static Uri uri() {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(Share.FILE).build();
    }

    private boolean ours(Uri u) {
        return AUTHORITY.equals(u.getAuthority()) && ("/" + Share.FILE).equals(u.getPath());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        if (!ours(u) || !"r".equals(mode)) throw new FileNotFoundException(u.toString());
        return ParcelFileDescriptor.open(Share.file(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri u) {
        return ours(u) ? "image/png" : null;
    }

    /** Name and size: some messengers ask before they read. */
    @Override
    public Cursor query(Uri u, String[] projection, String selection, String[] args, String order) {
        if (!ours(u)) return null;
        File f = Share.file(getContext());
        MatrixCursor c = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[] {"svitlyachok-grafik.png", f.length()});
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
