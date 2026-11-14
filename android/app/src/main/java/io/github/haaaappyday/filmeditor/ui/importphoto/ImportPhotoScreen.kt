package io.github.haaaappyday.filmeditor.ui.importphoto

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.R
import io.github.haaaappyday.filmeditor.core.EditorErrorCode

@Composable
fun ImportPhotoScreen(bitmap: Bitmap?, loading: Boolean, error: EditorErrorCode?,
    onImport: () -> Unit, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            if (bitmap == null) {
                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(stringResource(R.string.empty_description), textAlign = TextAlign.Center)
            } else {
                Image(bitmap.asImageBitmap(), stringResource(R.string.photo_preview),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp)
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height), contentScale = ContentScale.Fit)
                Text(stringResource(R.string.preview_dimensions, bitmap.width, bitmap.height), style = MaterialTheme.typography.bodySmall)
            }
            if (loading) {
                CircularProgressIndicator()
                Text(stringResource(R.string.importing_photo), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (error != null) {
                val message = when (error) {
                    EditorErrorCode.UNSUPPORTED_IMAGE -> R.string.unsupported_image
                    EditorErrorCode.IMAGE_TOO_LARGE -> R.string.image_too_large
                    else -> R.string.image_read_failed
                }
                Text(stringResource(message), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                OutlinedButton(onClick = onRetry, enabled = !loading) { Text(stringResource(R.string.retry)) }
            }
            Button(onClick = onImport, enabled = !loading) { Text(stringResource(R.string.import_photo)) }
        }
    }
}
