package io.github.haaaappyday.filmeditor.image

import android.graphics.*
import android.os.Build
import io.github.haaaappyday.filmeditor.core.*
import java.io.DataInputStream
import java.io.File
import java.io.IOException

internal data class ImageMetadata(val width: Int, val height: Int, val mimeType: String)

/** Synchronous decoding primitive; ImageRepository always invokes it off the main thread. */
internal class ImageNormalizer {
    fun inspect(file: File): ImageMetadata = translateReadFailure {
        checkContainer(file)
        var metadata: ImageMetadata? = null
        // A 1px validation decode checks truncated/corrupt data without expanding a large source.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            metadata = header(decoder, info, 1)
        }.let { bitmap ->
            try { useSdrBase(bitmap) } finally { bitmap.recycle() }
        }
        checkNotNull(metadata)
    }

    fun decode(file: File, maxLongEdge: Int): Bitmap = translateReadFailure {
        checkContainer(file)
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            header(decoder, info, maxLongEdge)
        }
        var output: Bitmap? = null
        try {
            useSdrBase(decoded)
            // Drawing into an explicit sRGB target performs color conversion, never relabeling.
            // An opaque white destination composites alpha in encoded sRGB.
            output = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888,
                false, ColorSpace.get(ColorSpace.Named.SRGB))
            output.density = Bitmap.DENSITY_NONE
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(decoded, null, Rect(0, 0, output.width, output.height), null)
            output.setHasAlpha(false)
            output
        } catch (error: Throwable) {
            output?.recycle()
            throw error
        } finally { decoded.recycle() }
    }

    private fun header(decoder: ImageDecoder, info: ImageDecoder.ImageInfo, cap: Int): ImageMetadata {
        if (info.mimeType !in setOf("image/jpeg", "image/png") || info.isAnimated) {
            throw EditorException(EditorErrorCode.UNSUPPORTED_IMAGE)
        }
        if (Build.VERSION.SDK_INT >= 34 && info.colorSpace in setOf(
                ColorSpace.get(ColorSpace.Named.BT2020_HLG), ColorSpace.get(ColorSpace.Named.BT2020_PQ))) {
            throw EditorException(EditorErrorCode.UNSUPPORTED_IMAGE)
        }
        val (width, height) = ImageSizing.fit(info.size.width, info.size.height, cap)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        decoder.setTargetSize(width, height)
        decoder.setOnPartialImageListener { false }
        // ImageDecoder includes EXIF rotation/mirroring in ImageInfo and decoded pixels.
        // Do not apply a second ExifInterface transform. All eight orientations are tested.
        return ImageMetadata(info.size.width, info.size.height, info.mimeType)
    }

    private fun useSdrBase(bitmap: Bitmap) {
        // These freshly decoded bitmaps are exclusively owned here. Keep the JPEG's
        // SDR base and detach its HDR enhancement before drawing into our SDR target.
        // The same native fixture verifies base pixels on API29 and API36.
        if (Build.VERSION.SDK_INT >= 34 && bitmap.hasGainmap()) bitmap.gainmap = null
    }

    private fun checkContainer(file: File) {
        DataInputStream(file.inputStream().buffered()).use { stream ->
            val signature = ByteArray(8)
            stream.readFully(signature)
            if (signature[0] == 0xff.toByte() && signature[1] == 0xd8.toByte() && signature[2] == 0xff.toByte()) return
            if (!signature.contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) {
                throw EditorException(EditorErrorCode.UNSUPPORTED_IMAGE)
            }
            // Some Android decoders expose APNG's first frame as a static PNG. Reject its
            // animation-control chunk ourselves, before the first IDAT per the PNG specification.
            repeat(4096) {
                val length = stream.readInt()
                if (length < 0 || length > MAX_SOURCE_BYTES) throw EditorException(EditorErrorCode.IMAGE_TOO_LARGE)
                val type = stream.readInt()
                if (type == 0x6163544c) throw EditorException(EditorErrorCode.UNSUPPORTED_IMAGE) // acTL
                if (type == 0x49444154) return // IDAT, the platform validates its compressed payload
                var remaining = length.toLong() + 4 // payload + CRC
                while (remaining > 0) {
                    val skipped = stream.skip(remaining)
                    if (skipped <= 0) throw IOException("Truncated PNG header")
                    remaining -= skipped
                }
            }
            throw EditorException(EditorErrorCode.UNSUPPORTED_IMAGE)
        }
    }

    companion object { const val MAX_SOURCE_BYTES = 100 * 1024 * 1024 }
}

internal inline fun <T> translateReadFailure(block: () -> T): T = try {
    block()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (error: EditorException) {
    throw error
} catch (error: OutOfMemoryError) {
    throw EditorException(EditorErrorCode.IMAGE_TOO_LARGE, error)
} catch (error: IOException) {
    throw EditorException(EditorErrorCode.READ_FAILED, error)
} catch (error: SecurityException) {
    throw EditorException(EditorErrorCode.READ_FAILED, error)
} catch (error: IllegalArgumentException) {
    throw EditorException(EditorErrorCode.READ_FAILED, error)
}
