package io.github.haaaappyday.filmeditor.core

/** Stable UUID and immutable app-private source copy. Dimensions are oriented originals,
 * before sampling. Persist this descriptor rather than decoded Bitmap instances.
 */
data class ImageSource(
    val id: String,
    val localPath: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
)
