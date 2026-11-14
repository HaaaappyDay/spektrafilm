package io.github.haaaappyday.filmeditor.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.EditParams
import kotlin.math.roundToInt

@Composable
fun AdjustmentControls(params: EditParams, enabled: Boolean, onStrength: (Float) -> Unit, onExposure: (Float) -> Unit, onTemperature: (Float) -> Unit) {
    Adjustment("strength", stringResource(R.string.film_strength), stringResource(R.string.strength_value, (params.strength*100).roundToInt()),
        params.strength, 0f..1f, 99, enabled) { onStrength((it*100).roundToInt()/100f) }
    Adjustment("exposure", stringResource(R.string.exposure), stringResource(R.string.exposure_value, params.exposureEv),
        params.exposureEv, -2f..2f, 39, enabled) { onExposure((it*10).roundToInt()/10f) }
    Adjustment("temperature", stringResource(R.string.temperature), stringResource(R.string.temperature_value, params.temperature.roundToInt()),
        params.temperature, -100f..100f, 199, enabled) { onTemperature(it.roundToInt().toFloat()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.cool), style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.warm), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Adjustment(tag: String, label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).semantics {
                contentDescription = label
                stateDescription = valueText
            })
    }
}
