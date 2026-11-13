package io.github.haaaappyday.filmeditor.core

import android.graphics.Bitmap
import android.net.Uri

/** Implementations move copying/decoding off main and propagate CancellationException. */
interface ImageRepository {
    /** Return only after a durable private copy exists. Failure leaves the prior source intact. */
    suspend fun importImage(uri: Uri): ImageSource

    /** New, caller-owned, software ARGB_8888 opaque sRGB bitmap, orientation normalized once.
     * Preserve aspect ratio, cap the long edge, never upscale. Caller must not recycle while
     * displayed/in use. Preview uses 1600; export re-decodes the source at 4096.
     */
    suspend fun decode(source: ImageSource, maxLongEdge: Int): Bitmap

    /** Only repository-owned files. The session owner first ensures no render/export uses it. */
    suspend fun remove(source: ImageSource)
}
