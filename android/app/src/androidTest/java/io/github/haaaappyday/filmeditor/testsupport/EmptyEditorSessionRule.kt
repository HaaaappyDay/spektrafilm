package io.github.haaaappyday.filmeditor.testsupport

import androidx.test.platform.app.InstrumentationRegistry
import io.github.haaaappyday.filmeditor.ui.editor.EditorSessionStore
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** Clear only the test app's recovery descriptor before/after Activity integration tests. */
class EmptyEditorSessionRule : ExternalResource() {
    override fun before() = clear()
    override fun after() = clear()
    private fun clear() = runBlocking {
        EditorSessionStore(InstrumentationRegistry.getInstrumentation().targetContext).save(null)
    }
}
