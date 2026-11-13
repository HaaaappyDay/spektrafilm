package io.github.haaaappyday.filmeditor.core

import org.junit.Assert.*
import org.junit.Test

class ContractTest {
    @Test fun newEditUsesManualNeutralDefaults() {
        val params = EditParams("kodak_portra_400")
        assertEquals(1f, params.strength, 0f)
        assertEquals(0f, params.exposureEv, 0f)
        assertEquals(0f, params.temperature, 0f)
    }

    @Test fun exportSnapshotDoesNotChangeWhenEditingContinues() {
        val source = ImageSource("id", "/private/source.jpg", 4000, 3000, "image/jpeg")
        val initial = EditParams("kodak_portra_400", 0.6f, 0.4f, 30f)
        val snapshot = ExportRequest(source, initial)
        val next = initial.copy(presetId = "kodak_gold_200", exposureEv = -1f)
        assertEquals(initial, snapshot.params)
        assertEquals("kodak_portra_400", snapshot.params.presetId)
        assertEquals(-1f, next.exposureEv, 0f)
        assertEquals(4000, snapshot.source.width)
    }

    @Test fun errorsPreserveCauseAndStableCode() {
        val cause = IllegalStateException("disk unavailable")
        val error = EditorException(EditorErrorCode.READ_FAILED, cause)
        assertSame(cause, error.cause)
        assertEquals(EditorErrorCode.READ_FAILED, error.code)
        assertEquals("READ_FAILED", error.message)
        assertEquals(setOf("UNSUPPORTED_IMAGE", "IMAGE_TOO_LARGE", "READ_FAILED",
            "INVALID_PARAMS", "PRESET_UNAVAILABLE", "RENDER_FAILED", "EXPORT_FAILED"),
            EditorErrorCode.entries.map { it.name }.toSet())
    }
}
