package io.github.haaaappyday.filmeditor.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.FilmPreset

@Composable
fun PresetSelector(presets: List<FilmPreset>, selected: String, enabled: Boolean, onSelect: (String) -> Unit,
    thumbnails: Map<String, Bitmap> = emptyMap()) {
    val shape = RoundedCornerShape(8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (preset in presets) {
                val chosen = preset.id == selected
                Column(Modifier.width(104.dp).selectable(chosen, enabled = enabled, role = Role.RadioButton,
                    onClick = { onSelect(preset.id) }).testTag("preset-${preset.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(68.dp).clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape),
                        contentAlignment = Alignment.Center) {
                        val thumbnail = thumbnails[preset.id]
                        if (thumbnail != null) Image(thumbnail.asImageBitmap(), null,
                            Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Fit)
                        else Text(preset.displayName.substringAfterLast(' '), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(preset.displayName, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(stringResource(R.string.film_preview_hint), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
