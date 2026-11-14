package io.github.haaaappyday.filmeditor.image

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Installed in the test APK only. Simulates real content URIs and later revoked access. */
class FixtureProvider : ContentProvider() {
    private val revoked = mutableSetOf<String>()
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/octet-stream" // deliberately misleading
    @Synchronized override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (mode != "r" || name.contains('/') || name in revoked) throw FileNotFoundException()
        val file = File(context!!.cacheDir, "provider-$name")
        if (!file.exists()) context!!.assets.open("import-fixtures/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    @Synchronized override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
        val name = uri.lastPathSegment ?: return 0
        revoked.add(name)
        return if (File(context!!.cacheDir, "provider-$name").delete()) 1 else 0
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
