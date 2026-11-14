package io.github.haaaappyday.filmeditor.diagnostics

import android.os.Trace
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal interface TraceBackend {
    val enabled: Boolean
    fun beginAsync(name: String, cookie: Int)
    fun endAsync(name: String, cookie: Int)
    fun beginSection(name: String)
    fun endSection()
}

class PerformanceTrace internal constructor(private val backend: TraceBackend) {
    internal fun begin(kind: String): TraceSpan = span(kind,
        if (backend.enabled) sequence.updateAndGet { if (it == Int.MAX_VALUE) 1 else it + 1 } else 0)
    internal fun span(kind: String, id: Int): TraceSpan {
        if (id != 0) backend.beginAsync("$kind:$id", id)
        return TraceSpan(this, kind, id)
    }
    internal fun end(kind: String, id: Int, outcome: String) {
        if (id == 0) return
        backend.beginSection("${kind}Outcome:$id:$outcome")
        backend.endSection()
        backend.endAsync("$kind:$id", id)
    }
    internal fun mark(kind: String, id: Int) {
        if (id == 0) return
        backend.beginSection("$kind:$id")
        backend.endSection()
    }
    internal fun draw(id: Int, block: () -> Unit) {
        if (id == 0 || !backend.enabled) return block()
        backend.beginSection("FilmPreviewDraw:$id")
        try { block() } finally { backend.endSection() }
    }
    companion object {
        private val sequence = AtomicInteger()
        val app = PerformanceTrace(object : TraceBackend {
            override val enabled get() = Trace.isEnabled()
            override fun beginAsync(name: String, cookie: Int) = Trace.beginAsyncSection(name, cookie)
            override fun endAsync(name: String, cookie: Int) = Trace.endAsyncSection(name, cookie)
            override fun beginSection(name: String) = Trace.beginSection(name)
            override fun endSection() = Trace.endSection()
        })
    }
}

internal class TraceSpan(private val owner: PerformanceTrace, private val kind: String, val id: Int) {
    private val ended = AtomicBoolean()
    fun finish(outcome: String) {
        if (ended.compareAndSet(false, true)) owner.end(kind, id, outcome)
    }
    fun mark(kind: String) = owner.mark(kind, id)
    suspend fun <T> phase(kind: String, block: suspend () -> T): T {
        val phase = owner.span(kind, id)
        try { return block() } finally { phase.finish("ended") }
    }
}

/** Carries a button operation across the coordinator, IO dispatcher and renderer. */
internal class ExportTraceContext(val span: TraceSpan) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ExportTraceContext>
}
