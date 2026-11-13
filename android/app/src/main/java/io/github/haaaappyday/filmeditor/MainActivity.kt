package io.github.haaaappyday.filmeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.haaaappyday.filmeditor.ui.theme.FilmEditorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FilmEditorTheme { FoundationScreen() } }
    }
}

@Composable
fun FoundationScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center)
            Text(stringResource(R.string.empty_description), style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center)
            Button(onClick = {}, enabled = false) { Text(stringResource(R.string.import_photo)) }
            Text(stringResource(R.string.foundation_notice), style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center)
        }
    }
}
