package io.github.haaaappyday.filmeditor.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.diagnostics.PerformanceTrace

@Composable
fun BeforeAfterPreview(state: EditorUiState, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = state.displayedBitmap
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.photo_preview),
                Modifier.fillMaxSize().drawWithContent {
                    val id = if (state.previewValid && !state.showOriginal && bitmap === state.preview) state.previewTraceId else 0
                    PerformanceTrace.app.draw(id) { drawContent() }
                }, contentScale = ContentScale.Fit)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(stringResource(R.string.empty_description), textAlign = TextAlign.Center)
            }
        }
        val status = when {
            state.exporting -> R.string.saving_photo
            state.importing -> R.string.importing_photo
            state.initializing -> R.string.restoring_editor
            state.showOriginal -> R.string.original_photo
            state.rendering -> R.string.updating_preview
            state.preview == null -> R.string.original_photo
            else -> R.string.edited_photo
        }
        if (bitmap != null || state.initializing || state.importing) {
            Text(stringResource(status), Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelMedium)
        }
        if (state.rendering || state.importing || state.initializing || state.exporting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}
