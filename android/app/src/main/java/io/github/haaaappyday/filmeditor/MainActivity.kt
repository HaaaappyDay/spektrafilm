package io.github.haaaappyday.filmeditor

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.haaaappyday.filmeditor.image.AndroidImageRepository
import io.github.haaaappyday.filmeditor.export.ExportCoordinator
import io.github.haaaappyday.filmeditor.export.MediaStorePhotoExporter
import io.github.haaaappyday.filmeditor.image.rememberPhotoPicker
import io.github.haaaappyday.filmeditor.render.AssetPresetRepository
import io.github.haaaappyday.filmeditor.render.GlFilmRenderer
import io.github.haaaappyday.filmeditor.ui.editor.*
import io.github.haaaappyday.filmeditor.ui.theme.FilmEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val editor = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == EditorViewModel::class.java)
                val presets = AssetPresetRepository(applicationContext)
                val images = AndroidImageRepository(applicationContext)
                val renderer = GlFilmRenderer(applicationContext, presets)
                val exporter = MediaStorePhotoExporter(applicationContext, images, renderer)
                val coordinator = ExportCoordinator(exporter, exporter::recover)
                return EditorViewModel(images, presets, renderer, EditorSessionStore(applicationContext),
                    coordinator, coordinator::recover) as T
            }
        })[EditorViewModel::class.java]
        setContent {
            FilmEditorTheme {
                val state = editor.state.collectAsStateWithLifecycle().value
                val pick = rememberPhotoPicker(editor::importPhoto)
                EditorScreen(state, pick, editor::setPreset, editor::setStrength, editor::setExposure,
                    editor::setTemperature, editor::reset, editor::toggleOriginal, editor::retry, editor::export) { uri ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.open_photo_failed, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}
