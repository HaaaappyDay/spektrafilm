package io.github.haaaappyday.filmeditor.core

import android.graphics.Bitmap

/** Shared preview/export math defined in docs/android/v1-contract.md.
 * Implementations serialize GL work on a dedicated thread, validate parameters and preserve
 * cancellation. Inputs must already satisfy ImageRepository's normalized bitmap contract.
 */
interface FilmRenderer {
    /** Never mutate/recycle input. Return a NEW exclusively caller-owned opaque sRGB
     * software ARGB_8888 bitmap with identical dimensions. Do not silently downsize.
     */
    suspend fun render(input: Bitmap, params: EditParams): Bitmap

    /** Idempotent resource cleanup, serialized with active renders. Owned by the session. */
    suspend fun release()
}
