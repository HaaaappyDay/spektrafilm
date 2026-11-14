package io.github.haaaappyday.filmeditor.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.diagnostics.PerformanceTrace

@Composable
fun BeforeAfterPreview(state: EditorUiState, modifier: Modifier = Modifier) {
    var holdingOriginal by remember(state.source?.id, state.input) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle, state.source?.id, state.input) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) holdingOriginal = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); holdingOriginal = false }
    }
    val holdEnabled = state.editingEnabled && state.input != null && resumed
    val comparisonGesture = if (holdEnabled) Modifier.pointerInput(state.source?.id, state.input) {
        try {
            awaitEachGesture {
                val down = awaitFirstDown()
                val pressed = awaitLongPressOrCancellation(down.id)
                if (pressed != null) {
                    pressed.consume()
                    holdingOriginal = true
                    try { waitForUpOrCancellation() } finally { holdingOriginal = false }
                }
            }
        } finally { holdingOriginal = false }
    } else Modifier
    val showingOriginal = state.showOriginal || (holdEnabled && holdingOriginal)
    Column(modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = if (showingOriginal) state.input else state.displayedBitmap
        Box(Modifier.weight(1f).fillMaxWidth().then(comparisonGesture), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), stringResource(R.string.photo_preview),
                Modifier.fillMaxSize().drawWithContent {
                    val id = if (state.previewValid && !showingOriginal && bitmap === state.preview) state.previewTraceId else 0
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
            showingOriginal -> R.string.original_photo
            state.rendering -> R.string.updating_preview
            state.preview == null -> R.string.original_photo
            else -> R.string.edited_photo
        }
        if (bitmap != null || state.initializing || state.importing) {
            Text(stringResource(status), Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Keep the image's measurement space identical when progress appears or disappears.
        Box(Modifier.fillMaxWidth().height(10.dp), contentAlignment = Alignment.BottomCenter) {
            if (state.rendering || state.importing || state.initializing || state.exporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
