package io.github.haaaappyday.filmeditor.image

import io.github.haaaappyday.filmeditor.core.*
import org.junit.Assert.*
import org.junit.Test

class ImageSizingTest {
    @Test fun capsPreserveAspectRatioAndNeverUpscale() {
        assertEquals(1600 to 1200, ImageSizing.fit(4000, 3000, 1600))
        assertEquals(4000 to 3000, ImageSizing.fit(4000, 3000, 4096))
        assertEquals(4096 to 2731, ImageSizing.fit(6000, 4000, 4096))
        assertEquals(2731 to 4096, ImageSizing.fit(4000, 6000, 4096))
        assertEquals(1 to 1, ImageSizing.fit(1, 1, 1600))
        assertEquals(1 to 1600, ImageSizing.fit(1, 32768, 1600))
        assertEquals(1600 to 1200, ImageSizing.fit(8000, 6000, 1600))
    }

    @Test fun invalidLimitsAndUnsafeHeadersFailBeforeAllocation() {
        for (cap in listOf(0, -1, 4097, Int.MAX_VALUE)) {
            val error = assertThrows(EditorException::class.java) { ImageSizing.fit(100, 100, cap) }
            assertEquals(EditorErrorCode.INVALID_PARAMS, error.code)
        }
        for ((width, height) in listOf(0 to 1, -1 to 10, 40000 to 1,
                20000 to 20000, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val error = assertThrows(EditorException::class.java) { ImageSizing.fit(width, height, 1600) }
            assertEquals(EditorErrorCode.IMAGE_TOO_LARGE, error.code)
        }
    }
}
