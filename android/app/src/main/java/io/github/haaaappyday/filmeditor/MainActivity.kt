package io.github.haaaappyday.filmeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.haaaappyday.filmeditor.image.AndroidImageRepository
import io.github.haaaappyday.filmeditor.image.rememberPhotoPicker
import io.github.haaaappyday.filmeditor.ui.importphoto.ImportPhotoScreen
import io.github.haaaappyday.filmeditor.ui.importphoto.ImportPhotoViewModel
import io.github.haaaappyday.filmeditor.ui.theme.FilmEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ImportPhotoViewModel(AndroidImageRepository(applicationContext)) as T
        })[ImportPhotoViewModel::class.java]
        setContent {
            FilmEditorTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                val pick = rememberPhotoPicker(viewModel::importPhoto)
                ImportPhotoScreen(state.bitmap, state.loading, state.error, pick, viewModel::retry)
            }
        }
    }
}
