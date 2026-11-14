package io.github.haaaappyday.filmeditor.diagnostics

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class PerformanceTraceTest {
    private class Backend(override val enabled: Boolean = true) : TraceBackend {
        val events = Collections.synchronizedList(mutableListOf<String>())
        override fun beginAsync(name: String, cookie: Int) { events += "begin:$name:$cookie" }
        override fun endAsync(name: String, cookie: Int) { events += "end:$name:$cookie" }
        override fun beginSection(name: String) { events += "section:$name" }
        override fun endSection() { events += "section-end" }
    }
    @Test fun concurrentRepeatedFinishEndsAnOperationExactlyOnce() = runBlocking {
        val backend = Backend(); val trace = PerformanceTrace(backend)
        val operation = trace.begin("FilmPreview")
        assertTrue(operation.id > 0)
        (1..20).map { async(Dispatchers.Default) { operation.finish("cancelled") } }.awaitAll()
        assertEquals(1, backend.events.count { it.startsWith("begin:") })
        assertEquals(1, backend.events.count { it.startsWith("end:") })
        assertEquals(1, backend.events.count { it == "section:FilmPreviewOutcome:${operation.id}:cancelled" })
        assertNotEquals(operation.id, trace.begin("FilmPreview").also { it.finish("accepted") }.id)
    }
    @Test fun cancelledSuspendingPhaseEndsWithoutClosingItsParentPrematurely() = runBlocking {
        val backend = Backend(); val operation = PerformanceTrace(backend).begin("FilmExport")
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            operation.phase("FilmExportDecode") { entered.complete(Unit); awaitCancellation() }
        }
        entered.await(); job.cancelAndJoin()
        assertEquals(1, backend.events.count { it == "end:FilmExportDecode:${operation.id}:${operation.id}" })
        assertFalse(backend.events.any { it == "end:FilmExport:${operation.id}:${operation.id}" })
        operation.finish("cancelled")
        assertEquals(1, backend.events.count { it == "end:FilmExport:${operation.id}:${operation.id}" })
    }
    @Test fun disabledTracingPreservesWorkWithoutEventsOrIds() = runBlocking {
        val backend = Backend(false); val trace = PerformanceTrace(backend)
        val operation = trace.begin("FilmPreview")
        assertEquals(0, operation.id)
        assertEquals(42, operation.phase("FilmPreviewRender") { 42 })
        var drawn = false
        trace.draw(operation.id) { drawn = true }
        operation.finish("accepted")
        assertTrue(drawn); assertTrue(backend.events.isEmpty())
    }
    @Test fun drawMarkerClosesOnExceptionAndIdentifiesItsContent() {
        val backend = Backend(); val trace = PerformanceTrace(backend)
        try { trace.draw(17) { error("draw failure") }; fail() } catch (_: IllegalStateException) { }
        assertEquals(listOf("section:FilmPreviewDraw:17", "section-end"), backend.events)
    }
}
