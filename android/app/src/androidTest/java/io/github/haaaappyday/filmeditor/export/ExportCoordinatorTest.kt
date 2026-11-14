package io.github.haaaappyday.filmeditor.export

import android.net.Uri
import io.github.haaaappyday.filmeditor.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ExportCoordinatorTest {
    private val request = ExportRequest(ImageSource("source", "/source", 4, 3, "image/jpeg"), EditParams("kodak_gold_200", .37f, -.7f, 31f))
    @Test fun oneActiveAcrossInstancesAndRecoveryWaitsForItsSnapshot() = runBlocking {
        val entered = CompletableDeferred<ExportRequest>(); val finish = CompletableDeferred<Unit>()
        val calls = mutableListOf<ExportRequest>()
        val delegate = object : PhotoExporter {
            override suspend fun export(request: ExportRequest): ExportResult {
                calls.add(request); entered.complete(request); finish.await()
                return ExportResult(Uri.parse("content://media/external_primary/images/media/123"), 4, 3)
            }
        }
        var recoveries = 0
        val first = ExportCoordinator(delegate) { recoveries++ }
        val other = ExportCoordinator(delegate) { recoveries++ }
        val running = async { first.export(request) }
        try {
            assertEquals(request, entered.await())
            try { other.export(request.copy(params = EditParams("kodak_portra_400"))); fail("Queued duplicate save") }
            catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
            val recovering = async { other.recover() }
            yield(); assertEquals(0, recoveries)
            finish.complete(Unit)
            assertEquals(4, running.await().width); recovering.await()
            assertEquals(1, recoveries); assertEquals(listOf(request), calls)
        } finally { finish.complete(Unit); running.cancelAndJoin() }
    }
    @Test fun cancellationAndFailureReleaseGateForRetry() = runBlocking {
        var fail = false; var block = true
        val entered = CompletableDeferred<Unit>()
        val delegate = object : PhotoExporter {
            override suspend fun export(request: ExportRequest): ExportResult {
                entered.complete(Unit)
                if (block) awaitCancellation()
                if (fail) throw EditorException(EditorErrorCode.EXPORT_FAILED)
                return ExportResult(Uri.parse("content://media/external_primary/images/media/124"), 4, 3)
            }
        }
        val coordinator = ExportCoordinator(delegate) {}
        val cancelled = launch { coordinator.export(request) }
        entered.await(); cancelled.cancelAndJoin()
        block = false; fail = true
        try { coordinator.export(request); fail("Swallowed export failure") }
        catch (error: EditorException) { assertEquals(EditorErrorCode.EXPORT_FAILED, error.code) }
        fail = false
        assertEquals(3, coordinator.export(request).height)
        coordinator.recover()
    }
}
