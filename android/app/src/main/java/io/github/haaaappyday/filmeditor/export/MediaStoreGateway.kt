package io.github.haaaappyday.filmeditor.export

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.OutputStream

/** Metadata is read from MediaStore, not trusted from the recovery journal. */
data class MediaRow(val uri: Uri, val name: String, val path: String, val owner: String?, val pending: Boolean)
interface MediaStoreGateway {
    fun insert(name: String): Uri
    fun open(uri: Uri): OutputStream
    fun publish(uri: Uri, name: String)
    fun rows(): List<MediaRow>
    fun delete(row: MediaRow, onlyPending: Boolean)
}

/** Blocking provider operations; the exporter invokes them on IO. No broad storage permissions. */
class AndroidMediaStoreGateway(context: Context) : MediaStoreGateway {
    private val resolver = context.applicationContext.contentResolver
    private val owner = context.applicationContext.packageName
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val ownerSelection = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
    private fun ownerArgs() = arrayOf(owner, PendingExport.RELATIVE_PATH)
    override fun insert(name: String): Uri = resolver.insert(collection, ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, PendingExport.RELATIVE_PATH)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }) ?: throw IOException("MediaStore insert returned no URI")

    override fun open(uri: Uri): OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(
        resolver.openFileDescriptor(uri, "w") ?: throw IOException("MediaStore output unavailable"))

    @Suppress("DEPRECATION")
    override fun publish(uri: Uri, name: String) {
        val selection = "$ownerSelection AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 1"
        val args = ownerArgs() + name
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        val updated = if (Build.VERSION.SDK_INT >= 30) resolver.update(uri, values, queryArgs(selection, args))
            else resolver.update(MediaStore.setIncludePending(uri), values, selection, args)
        if (updated != 1) throw IOException("MediaStore did not publish this output")
    }
    @Suppress("DEPRECATION")
    override fun rows(): List<MediaRow> {
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.OWNER_PACKAGE_NAME, MediaStore.MediaColumns.IS_PENDING)
        val cursor = if (Build.VERSION.SDK_INT >= 30) resolver.query(collection, columns, queryArgs(ownerSelection, ownerArgs()), null)
            else resolver.query(MediaStore.setIncludePending(collection), columns, ownerSelection, ownerArgs(), null)
        return (cursor ?: throw IOException("MediaStore query unavailable")).use {
            buildList {
                while (it.moveToNext()) add(MediaRow(ContentUris.withAppendedId(collection, it.getLong(0)),
                    it.getString(1).orEmpty(), it.getString(2).orEmpty(), it.getString(3), it.getInt(4) != 0))
            }
        }
    }
    @Suppress("DEPRECATION")
    override fun delete(row: MediaRow, onlyPending: Boolean) {
        require(row.owner == owner && row.path == PendingExport.RELATIVE_PATH && PendingExport.isManagedName(row.name))
        if (onlyPending && !row.pending) return
        val selection = "$ownerSelection AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?" +
            if (onlyPending) " AND ${MediaStore.MediaColumns.IS_PENDING} = 1" else ""
        val args = ownerArgs() + row.name
        val deleted = if (Build.VERSION.SDK_INT >= 30) resolver.delete(row.uri, queryArgs(selection, args))
            else resolver.delete(MediaStore.setIncludePending(row.uri), selection, args)
        if (deleted == 0 && rows().any { it.uri == row.uri && (!onlyPending || it.pending) }) {
            throw IOException("MediaStore did not remove this output")
        }
    }
    @RequiresApi(30)
    private fun queryArgs(selection: String, args: Array<String>) = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
    }
}
