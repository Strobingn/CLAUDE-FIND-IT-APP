package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.survey.SurveyDocumentParser
import com.example.data.survey.SurveyLayer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SURVEY_FILE_BYTES = 25 * 1024 * 1024

@Composable
fun SurveyLayerImporter(
    layers: List<SurveyLayer>,
    onImported: (SurveyLayer) -> Unit,
    onDelete: (SurveyLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || isWorking) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        isWorking = true
        message = "Reading survey layer…"
        isError = false
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = surveyDisplayName(context.contentResolver, uri)
                    val source = readSurveyText(context.contentResolver, uri)
                    SurveyDocumentParser.parse(source, name)
                }
            }.onSuccess { layer ->
                onImported(layer)
                message = "Imported ${layer.displayName}: ${layer.features.size} feature" +
                    if (layer.features.size == 1) "." else "s."
                isError = false
            }.onFailure { error ->
                message = error.localizedMessage ?: "Survey import failed"
                isError = true
            }
            isWorking = false
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Route, contentDescription = null)
                Column(Modifier.padding(start = 10.dp)) {
                    Text("GPX / KML survey layers", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Import real waypoints, routes, tracks, and polygons into this terrain project.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = {
                    // Android document providers frequently label GPX/KML as
                    // application/octet-stream. Let the secure parser validate
                    // the selected document instead of hiding valid files.
                    picker.launch(arrayOf("*/*"))
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isWorking) "Importing…" else "Import GPX or KML")
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            if (layers.isEmpty()) {
                Text(
                    "No survey layers attached to this terrain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                layers.forEach { layer ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(layer.displayName, maxLines = 1)
                            Text(
                                "${layer.format.name} · ${layer.features.size} features · " +
                                    "${layer.features.sumOf { it.coordinates.size }} coordinates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onDelete(layer) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${layer.displayName}")
                        }
                    }
                }
            }
        }
    }
}

private fun surveyDisplayName(
    resolver: android.content.ContentResolver,
    uri: Uri,
): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "survey-layer.xml"
}

private fun readSurveyText(
    resolver: android.content.ContentResolver,
    uri: Uri,
): String {
    val output = ByteArrayOutputStream()
    resolver.openInputStream(uri)?.buffered()?.use { input ->
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= MAX_SURVEY_FILE_BYTES) { "Survey file exceeds the 25 MiB safety limit" }
            output.write(buffer, 0, count)
        }
    } ?: error("Could not open selected survey file")
    return output.toString(Charsets.UTF_8.name())
}
