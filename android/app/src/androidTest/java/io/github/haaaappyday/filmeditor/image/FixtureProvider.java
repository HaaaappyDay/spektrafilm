package io.github.haaaappyday.filmeditor.image;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.HashSet;
import java.util.Set;

/** Test-APK-only provider. Java keeps its separate process independent of target-app Kotlin libraries. */
public final class FixtureProvider extends ContentProvider {
    private final Set<String> revoked = new HashSet<>();
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!"r".equals(mode) || name == null || name.contains("/") || revoked.contains(name)) throw new FileNotFoundException();
        File file = new File(getContext().getCacheDir(), "provider-" + name);
        if (!file.exists()) {
            try (InputStream input = getContext().getAssets().open("import-fixtures/" + name);
                 OutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public synchronized int delete(Uri uri, String selection, String[] args) {
        String name = uri.getLastPathSegment();
        if (name == null) return 0;
        revoked.add(name);
        return new File(getContext().getCacheDir(), "provider-" + name).delete() ? 1 : 0;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
