package io.github.haaaappyday.filmeditor.ui.editor

import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.EditorErrorCode
import io.github.haaaappyday.filmeditor.image.ImageSizing

@Composable
fun EditorScreen(state: EditorUiState, onImport: () -> Unit, onPreset: (String) -> Unit,
    onStrength: (Float) -> Unit, onExposure: (Float) -> Unit, onTemperature: (Float) -> Unit,
    onReset: () -> Unit, onToggleOriginal: () -> Unit, onRetry: () -> Unit, onExport: () -> Unit, onOpenExport: (Uri) -> Unit) {
    val scroll = rememberScrollState()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onImport, enabled = state.canImport, modifier = Modifier.heightIn(min = 48.dp).testTag("import")) {
                    Text(stringResource(R.string.import_photo))
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val controls: @Composable (Modifier) -> Unit = { modifier ->
                    EditorControls(state, scroll, modifier, onPreset, onStrength, onExposure, onTemperature,
                        onReset, onToggleOriginal, onRetry, onExport, onOpenExport)
                }
                if (maxWidth >= 600.dp || maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize()) {
                        BeforeAfterPreview(state, Modifier.weight(1f).fillMaxHeight())
                        controls(Modifier.width((maxWidth * .45f).coerceAtMost(360.dp)).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        BeforeAfterPreview(state, Modifier.weight(.45f).fillMaxWidth())
                        controls(Modifier.weight(.55f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorControls(state: EditorUiState, scroll: ScrollState, modifier: Modifier,
    onPreset: (String) -> Unit, onStrength: (Float) -> Unit, onExposure: (Float) -> Unit, onTemperature: (Float) -> Unit,
    onReset: () -> Unit, onToggleOriginal: () -> Unit, onRetry: () -> Unit, onExport: () -> Unit, onOpenExport: (Uri) -> Unit) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.error != null) {
                Text(stringResource(errorMessage(state)), Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onRetry, enabled = !state.initializing && !state.importing && !state.exporting,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("retry")) { Text(stringResource(R.string.retry)) }
            }
            PresetSelector(state.presets, state.params.presetId, state.editingEnabled, onPreset)
            AdjustmentControls(state.params, state.editingEnabled, onStrength, onExposure, onTemperature)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleOriginal, enabled = state.editingEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("original").semantics { selected = state.showOriginal }) {
                    Text(stringResource(if (state.showOriginal) R.string.return_to_edit else R.string.compare_original))
                }
                TextButton(onClick = onReset, enabled = state.editingEnabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("reset")) { Text(stringResource(R.string.reset_adjustments)) }
            }
            state.source?.let { source ->
                val (width, height) = ImageSizing.fit(source.width, source.height, 4096)
                Text(stringResource(R.string.export_dimensions, width, height), style = MaterialTheme.typography.labelMedium)
            }
            Button(onClick = onExport, enabled = state.canExport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export")) {
                Text(stringResource(if (state.exporting) R.string.saving_photo else R.string.export_photo))
            }
            state.exportResult?.let { result ->
                Text(stringResource(R.string.photo_saved), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                OutlinedButton(onClick = { onOpenExport(result.uri) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.open_photo)) }
            }
        }
    }
}

private fun errorMessage(state: EditorUiState): Int = when {
    state.errorStage == EditorErrorStage.INITIALIZE && state.error != EditorErrorCode.PRESET_UNAVAILABLE -> R.string.restore_failed
    state.errorStage == EditorErrorStage.SESSION -> R.string.session_save_failed
    state.error == EditorErrorCode.PRESET_UNAVAILABLE -> R.string.preset_unavailable
    state.error == EditorErrorCode.RENDER_FAILED || state.error == EditorErrorCode.INVALID_PARAMS -> R.string.preview_failed
    state.error == EditorErrorCode.EXPORT_FAILED -> R.string.export_failed
    state.error == EditorErrorCode.UNSUPPORTED_IMAGE -> R.string.unsupported_image
    state.error == EditorErrorCode.IMAGE_TOO_LARGE -> R.string.image_too_large
    else -> R.string.image_read_failed
}
