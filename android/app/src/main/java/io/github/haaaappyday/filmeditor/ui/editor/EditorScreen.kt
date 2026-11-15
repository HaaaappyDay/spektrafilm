package io.github.haaaappyday.filmeditor.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.EditorErrorCode
import io.github.haaaappyday.filmeditor.image.ImageSizing

@Composable
fun EditorScreen(state: EditorUiState, onImport: () -> Unit, onPreset: (String) -> Unit,
    onStrength: (Float) -> Unit, onExposure: (Float) -> Unit, onTemperature: (Float) -> Unit,
    onReset: () -> Unit, onToggleOriginal: () -> Unit, onRetry: () -> Unit, onExport: () -> Unit, onOpenExport: (Uri) -> Unit,
    onContrast: (Float) -> Unit = {}, onHighlights: (Float) -> Unit = {}, onShadows: (Float) -> Unit = {},
    onSaturation: (Float) -> Unit = {}, onTint: (Float) -> Unit = {}) {
    var adjustment by rememberSaveable { mutableStateOf("strength") }
    BackHandler(state.source != null && adjustment != "strength" && !state.exporting) {
        adjustment = "strength"
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val film = state.presets.firstOrNull { it.id == state.params.presetId }
                Text(film?.displayName ?: stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.source != null) {
                    val importDescription = stringResource(R.string.import_photo)
                    val compareDescription = stringResource(if (state.showOriginal) R.string.return_to_edit else R.string.compare_original)
                    TextButton(onClick = onImport, enabled = state.canImport,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("import").semantics { contentDescription = importDescription }) {
                        Text(stringResource(R.string.open_image))
                    }
                    TextButton(onClick = onToggleOriginal, enabled = state.editingEnabled,
                        colors = ButtonDefaults.textButtonColors(containerColor = if (state.showOriginal)
                            MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("original").semantics {
                            selected = state.showOriginal; contentDescription = compareDescription
                        }) { Text(stringResource(R.string.compare_short)) }
                }
            }
            if (state.error != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(errorMessage(state)), Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Assertive },
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry, enabled = !state.initializing && !state.importing && !state.exporting,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("retry")) { Text(stringResource(R.string.retry)) }
                }
            }
            if (state.source == null) {
                EmptyEditor(state, onImport, Modifier.weight(1f).fillMaxWidth())
            } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val tools: @Composable (Modifier) -> Unit = { modifier ->
                    EditorTools(state, adjustment, { adjustment = it }, modifier,
                        onPreset, onStrength, onExposure, onTemperature, onReset, onExport, onOpenExport,
                        onContrast, onHighlights, onShadows, onSaturation, onTint)
                }
                val frame = MaterialTheme.colorScheme.primary
                val photoModifier = Modifier.drawWithContent {
                    drawContent()
                    drawRect(frame, style = Stroke(1.dp.toPx()))
                }
                if (maxWidth >= 600.dp || maxWidth > maxHeight) {
                    val panelWidth = (maxWidth * .42f).coerceIn(280.dp, 360.dp)
                    Row(Modifier.fillMaxSize()) {
                        Box(photoModifier.weight(1f).fillMaxHeight().testTag("photo-workspace")) {
                            BeforeAfterPreview(state, Modifier.fillMaxSize())
                        }
                        tools(Modifier.width(panelWidth).fillMaxHeight())
                    }
                } else {
                    val panelHeight = (maxHeight * .34f).coerceIn(176.dp, 240.dp).coerceAtMost(maxHeight * .42f)
                    Column(Modifier.fillMaxSize()) {
                        Box(photoModifier.weight(1f).fillMaxWidth().testTag("photo-workspace")) {
                            BeforeAfterPreview(state, Modifier.fillMaxSize())
                        }
                        tools(Modifier.fillMaxWidth().height(panelHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEditor(state: EditorUiState, onImport: () -> Unit, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(72.dp)) {
            val stroke = Stroke(2.dp.toPx())
            drawRoundRect(ink, Offset(8.dp.toPx(), 12.dp.toPx()), Size(size.width - 16.dp.toPx(), size.height - 24.dp.toPx()),
                CornerRadius(6.dp.toPx()), style = stroke)
            drawCircle(ink, 4.dp.toPx(), Offset(size.width * .68f, size.height * .36f), style = stroke)
            drawPath(Path().apply {
                moveTo(size.width * .2f, size.height * .7f); lineTo(size.width * .42f, size.height * .47f)
                lineTo(size.width * .58f, size.height * .63f); lineTo(size.width * .7f, size.height * .53f)
                lineTo(size.width * .82f, size.height * .7f)
            }, ink, style = stroke)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.empty_description), style = MaterialTheme.typography.bodyMedium,
            color = ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        if (state.initializing || state.importing) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(if (state.importing) R.string.importing_photo else R.string.restoring_editor),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Button(onClick = onImport, enabled = state.canImport, modifier = Modifier.heightIn(min = 48.dp).testTag("import")) {
            Text(stringResource(R.string.import_photo))
        }
    }
}

@Composable
private fun EditorTools(state: EditorUiState, adjustment: String, onAdjustment: (String) -> Unit, modifier: Modifier,
    onPreset: (String) -> Unit, onStrength: (Float) -> Unit, onExposure: (Float) -> Unit, onTemperature: (Float) -> Unit,
    onReset: () -> Unit, onExport: () -> Unit, onOpenExport: (Uri) -> Unit, onContrast: (Float) -> Unit,
    onHighlights: (Float) -> Unit, onShadows: (Float) -> Unit, onSaturation: (Float) -> Unit, onTint: (Float) -> Unit) {
    val scroll = rememberScrollState()
    Surface(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PresetSelector(state.presets, state.params.presetId, state.editingEnabled, onPreset,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            AdjustmentControls(state.params, state.editingEnabled,
                onStrength, onExposure, onTemperature, adjustment, onAdjustment, onReset,
                onContrast, onHighlights, onShadows, onSaturation, onTint,
                Modifier.padding(horizontal = 16.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.source?.let { source ->
                    val (width, height) = ImageSizing.fit(source.width, source.height, 4096)
                    Text(stringResource(R.string.export_dimensions, width, height), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onExport, enabled = state.canExport,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export")) {
                    Text(stringResource(if (state.exporting) R.string.saving_photo else R.string.export_photo))
                }
                state.exportResult?.let { saved ->
                    Text(stringResource(R.string.photo_saved), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { onOpenExport(saved.uri) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.open_photo))
                    }
                }
            }
        }
    }
}

private fun errorMessage(state: EditorUiState): Int = when {
    state.errorStage == EditorErrorStage.INITIALIZE && state.error == EditorErrorCode.EXPORT_FAILED -> R.string.export_recovery_failed
    state.errorStage == EditorErrorStage.INITIALIZE && state.error != EditorErrorCode.PRESET_UNAVAILABLE -> R.string.restore_failed
    state.errorStage == EditorErrorStage.SESSION -> R.string.session_save_failed
    state.error == EditorErrorCode.PRESET_UNAVAILABLE -> R.string.preset_unavailable
    state.error == EditorErrorCode.RENDER_FAILED || state.error == EditorErrorCode.INVALID_PARAMS -> R.string.preview_failed
    state.error == EditorErrorCode.EXPORT_FAILED -> R.string.export_failed
    state.error == EditorErrorCode.UNSUPPORTED_IMAGE -> R.string.unsupported_image
    state.error == EditorErrorCode.IMAGE_TOO_LARGE -> R.string.image_too_large
    else -> R.string.image_read_failed
}
