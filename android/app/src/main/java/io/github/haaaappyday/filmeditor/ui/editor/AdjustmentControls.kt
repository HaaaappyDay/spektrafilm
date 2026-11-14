package io.github.haaaappyday.filmeditor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.EditParams
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentControls(params: EditParams, enabled: Boolean, onStrength: (Float) -> Unit,
    onExposure: (Float) -> Unit, onTemperature: (Float) -> Unit,
    selectedAdjustment: String = "strength", onAdjustment: (String) -> Unit = {}, onReset: () -> Unit = {}) {
    val choices = listOf("strength" to R.string.film_strength, "exposure" to R.string.exposure, "temperature" to R.string.temperature)
    val chosen = choices.firstOrNull { it.first == selectedAdjustment } ?: choices.first()
    val value = when (chosen.first) { "exposure" -> params.exposureEv; "temperature" -> params.temperature; else -> params.strength }
    val range = when (chosen.first) { "exposure" -> -2f..2f; "temperature" -> -100f..100f; else -> 0f..1f }
    val steps = when (chosen.first) { "exposure" -> 39; "temperature" -> 199; else -> 99 }
    val label = stringResource(chosen.second)
    val valueText = when (chosen.first) {
        "exposure" -> stringResource(R.string.exposure_value, params.exposureEv)
        "temperature" -> stringResource(R.string.temperature_value, params.temperature.roundToInt())
        else -> stringResource(R.string.strength_value, (params.strength * 100).roundToInt())
    }
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.outline
    val sliderColors = SliderDefaults.colors(activeTrackColor = active, inactiveTrackColor = inactive,
        activeTickColor = active, inactiveTickColor = inactive)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            for ((tag, name) in choices) {
                val selected = tag == chosen.first
                TextButton(onClick = { onAdjustment(tag) }, enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(containerColor = if (selected)
                        MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("adjust-$tag").semantics { this.selected = selected }) {
                    Text(stringResource(name), style = MaterialTheme.typography.labelMedium)
                }
            }

        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val resetLabel = stringResource(R.string.reset_adjustments)
            TextButton(onClick = onReset, enabled = enabled, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp).testTag("reset").semantics { contentDescription = resetLabel }) {
                Text("↺", fontSize = 24.sp)
            }
            Slider(value = value, onValueChange = {
                when (chosen.first) {
                    "exposure" -> onExposure((it * 10).roundToInt() / 10f)
                    "temperature" -> onTemperature(it.roundToInt().toFloat())
                    else -> onStrength((it * 100).roundToInt() / 100f)
                }
            }, valueRange = range, steps = steps, enabled = enabled,
                colors = sliderColors,
                track = { sliderState ->
                    SliderDefaults.Track(sliderState, enabled = enabled, colors = sliderColors,
                        modifier = Modifier.drawWithContent {
                            drawContent()
                            if (chosen.first != "strength") drawLine(marker,
                                Offset(size.width / 2, size.height / 2 - 3.dp.toPx()),
                                Offset(size.width / 2, size.height / 2 + 3.dp.toPx()), 1.dp.toPx())
                        })
                },
                thumb = { Box(Modifier.size(20.dp).shadow(1.dp, CircleShape).background(
                    if (enabled) active else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f), CircleShape)) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(chosen.first).semantics {
                    contentDescription = label; stateDescription = valueText
                })
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        if (chosen.first == "temperature") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.cool), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.warm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
