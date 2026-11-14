package io.github.haaaappyday.filmeditor.image

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/** AndroidX uses ACTION_OPEN_DOCUMENT when the system photo picker is unavailable. */
@Composable
fun rememberPhotoPicker(onSelected: (Uri?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onSelected)
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
