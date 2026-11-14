package io.github.haaaappyday.filmeditor.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.FilmPreset

@Composable
fun PresetSelector(presets: List<FilmPreset>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.film_preset), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && presets.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("preset-selector")) {
                Text(presets.find { it.id == selected }?.displayName ?: stringResource(R.string.choose_preset))
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                presets.forEach { preset ->
                    DropdownMenuItem(text = { Text(preset.displayName) }, onClick = { expanded = false; onSelect(preset.id) })
                }
            }
        }
    }
}
