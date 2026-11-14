package io.github.haaaappyday.filmeditor.ui.importphoto

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.core.*
import io.github.haaaappyday.filmeditor.fakes.FakeImageRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ImportPhotoViewModelTest {
    private val source = ImageSource("old", "/private/old", 2, 2, "image/png")
    private fun bitmap() = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private suspend fun ready(vm: ImportPhotoViewModel, id: String) = withTimeout(5000) {
        vm.state.first { !it.loading && it.source?.id == id && it.error == null }
    }

    @Test fun cancelAndFailedImportPreserveOldPhotoAndRetryCanSucceed() = runBlocking {
        val repo = FakeImageRepository(onImport = { source }, onDecode = { _, _ -> bitmap() })
        val store = ViewModelStore()
        lateinit var vm: ImportPhotoViewModel
        main { vm = ViewModelProvider(store, factory(repo))[ImportPhotoViewModel::class.java]; vm.importPhoto(Uri.EMPTY) }
        val old = ready(vm, "old")
        main { vm.importPhoto(null) }
        assertSame(old.bitmap, vm.state.value.bitmap)
        repo.onImport = { throw EditorException(EditorErrorCode.READ_FAILED) }
        main { vm.importPhoto(Uri.parse("content://test/bad")) }
        withTimeout(5000) { vm.state.first { it.error == EditorErrorCode.READ_FAILED } }
        assertSame(old.bitmap, vm.state.value.bitmap)
        assertEquals(source, vm.state.value.source)
        repo.onImport = { source.copy(id = "new") }
        main { vm.retry() }
        ready(vm, "new")
        main { store.clear() }
    }

    @Test fun aLateObsoleteImportCannotOverwriteNewPhotoAndIsCleaned() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val removed = CompletableDeferred<String>()
        val repo = FakeImageRepository(
            onImport = { uri ->
                if (uri.toString().endsWith("slow")) withContext(NonCancellable) {
                    entered.complete(Unit); release.await(); source.copy(id = "slow")
                } else source.copy(id = "new")
            }, onDecode = { _, _ -> bitmap() },
            onRemove = { if (it.id == "slow") removed.complete(it.id) },
        )
        val store = ViewModelStore()
        lateinit var vm: ImportPhotoViewModel
        main { vm = ViewModelProvider(store, factory(repo))[ImportPhotoViewModel::class.java]; vm.importPhoto(Uri.parse("content://test/slow")) }
        withTimeout(5000) { entered.await() }
        main { vm.importPhoto(Uri.parse("content://test/new")) }
        ready(vm, "new")
        release.complete(Unit)
        withTimeout(5000) { removed.await() }
        assertEquals("new", vm.state.value.source?.id)
        main { store.clear() }
    }

    private fun factory(repo: ImageRepository) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ImportPhotoViewModel(repo) as T
    }
}
