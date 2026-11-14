package io.github.haaaappyday.filmeditor.image

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/** Holds IO's return continuation until the resource exists, then cancels before delivery. */
class ReturnCancellationTest {
    private class CallerQueue : CoroutineDispatcher() {
        private val queue = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.put(block) }
        fun next(): Runnable = checkNotNull(queue.poll(10, TimeUnit.SECONDS)) { "No continuation arrived" }
        fun finish(job: Job) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!job.isCompleted && System.nanoTime() < deadline) queue.poll(100, TimeUnit.MILLISECONDS)?.run()
            assertTrue("Cancellation/cleanup did not finish", job.isCompleted)
        }
    }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun bytes() = instrumentation.context.assets.open("import-fixtures/orientation-1.jpg").use { it.readBytes() }
    private fun directory() = File(context.noBackupFilesDir, "return-${UUID.randomUUID()}").apply { mkdirs() }

    @Test fun cancelledDeliveryDeletesAnAlreadyFinalizedSource() {
        val root = directory()
        val enteredSuspension = CountDownLatch(1)
        val repo = AndroidImageRepository(context, directory = { root }, openInput = {
            check(enteredSuspension.await(10, TimeUnit.SECONDS))
            ByteArrayInputStream(bytes())
        })
        val caller = CallerQueue()
        val scope = CoroutineScope(SupervisorJob() + caller)
        val result = scope.async { repo.importImage(Uri.EMPTY) }
        try {
            caller.next().run() // enter import and suspend on IO
            enteredSuspension.countDown()
            val delivery = caller.next() // IO finished; do not deliver yet
            assertEquals(1, root.listFiles()!!.count { it.extension == "source" })
            result.cancel()
            delivery.run()
            caller.finish(result)
            assertTrue(result.isCancelled)
            assertTrue(root.listFiles()!!.isEmpty())
        } finally { enteredSuspension.countDown(); scope.cancel(); root.deleteRecursively() }
    }

    @Test fun cancelledDeliveryRecyclesDecodedBitmapWithoutDeletingItsSource() = runBlocking {
        val root = directory()
        val original = AndroidImageRepository(context, directory = { root }, openInput = { ByteArrayInputStream(bytes()) })
        val source = original.importImage(Uri.EMPTY)
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        val enteredSuspension = CountDownLatch(1)
        val repo = AndroidImageRepository(context, directory = { root }, decodeBitmap = { _, _ ->
            // withContext may finish inline if this fake returns before getResult suspends.
            // Hold IO until the caller's initial runnable has actually returned.
            check(enteredSuspension.await(10, TimeUnit.SECONDS))
            bitmap
        })
        val caller = CallerQueue()
        val scope = CoroutineScope(SupervisorJob() + caller)
        val result = scope.async { repo.decode(source, 1600) }
        try {
            caller.next().run()
            enteredSuspension.countDown()
            val delivery = caller.next()
            assertFalse(bitmap.isRecycled)
            result.cancel()
            delivery.run()
            caller.finish(result)
            assertTrue(bitmap.isRecycled)
            assertTrue(File(source.localPath).exists())
        } finally { enteredSuspension.countDown(); scope.cancel(); if (!bitmap.isRecycled) bitmap.recycle(); root.deleteRecursively() }
    }
}
