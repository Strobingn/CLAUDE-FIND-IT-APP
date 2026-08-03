package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.GeminiApiClient
import com.example.ai.OpenAiApiClient
import com.example.ai.TerrainAiProvider
import com.example.ai.TerrainVisionSession
import com.example.data.ElevationGrid
import com.example.geospatial.GeoSpatialLibrary.GeoSpatialMetadata

internal val AI_BUILT_IN_QUESTIONS = listOf(
    "Analyze the visible viewport image",
    "Compare the local detector results with the image",
    "Identify road traces, walls, foundations, and depressions",
    "Rank the strongest field-check locations",
    "Explain what should be verified on site",
)

private val CompactButtonHeight = 32.dp
private val CompactButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

/** Cloud controls and conversation only. The interactive analysis map lives above this panel. */
@Composable
fun AiCloudPanel(
    terrainSummary: String,
    grid: ElevationGrid,
    metadata: GeoSpatialMetadata,
    terrainKey: String,
    assistantViewModel: AiTerrainViewModel,
    modifier: Modifier = Modifier,
) {
    val state by assistantViewModel.state.collectAsStateWithLifecycle()
    val viewport by TerrainVisionSession.snapshot.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var openAiKey by rememberSaveable { mutableStateOf("") }
    var geminiKey by rememberSaveable { mutableStateOf("") }
    var showKeys by rememberSaveable { mutableStateOf(!state.openAiConfigured && !state.geminiConfigured) }
    var attachImage by rememberSaveable { mutableStateOf(true) }
    val imageReady = viewport.bitmap?.let { !it.isRecycled && it.width > 0 && it.height > 0 } == true

    LaunchedEffect(imageReady) {
        if (!imageReady) attachImage = false
    }

    val terrainContext = remember(terrainSummary, grid, metadata, viewport.bounds, viewport.zoom, state.localResult) {
        buildString {
            appendLine("Terrain summary: $terrainSummary")
            appendLine("Raster: ${grid.width} x ${grid.height} cells")
            appendLine("Cell size: ${grid.cellSizeMeters} meters")
            appendLine("CRS: ${metadata.crs}")
            appendLine("Visible zoom: ${"%.2f".format(viewport.zoom)}x")
            appendLine("Visible bounds: left=${viewport.bounds.left}, top=${viewport.bounds.top}, right=${viewport.bounds.right}, bottom=${viewport.bounds.bottom}")
            state.localResult?.let { result ->
                appendLine("Local analysis recommendation: ${result.recommendation}")
                appendLine("Strongest candidate locations:")
                result.candidates.take(12).forEach {
                    appendLine("- ${it.type.label}: ${"%.0f".format(it.score * 100f)}%, x=${"%.1f".format(it.xPercent)}%, y=${"%.1f".format(it.yPercent)}%")
                }
            }
            append("Analyze only the visible rendered surface. Treat suggested dig locations as field-check priorities, not proof of a buried object.")
        }
    }

    LazyColumn(
        modifier = modifier
            .imePadding()
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AI analysis", fontWeight = FontWeight.Bold)
                            Text(
                                when (state.activeProvider) {
                                    TerrainAiProvider.OPENAI -> "OpenAI ${OpenAiApiClient.configuredModel()} primary · Gemini fallback ready"
                                    TerrainAiProvider.GEMINI -> "Gemini ${GeminiApiClient.configuredModel()} active"
                                    null -> "Add an OpenAI or Gemini key"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilterChip(
                            selected = attachImage && imageReady,
                            onClick = { attachImage = !attachImage },
                            enabled = imageReady && !state.isSending,
                            label = { Text(if (attachImage && imageReady) "Map attached" else "Attach map", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.height(16.dp)) },
                            modifier = Modifier.height(CompactButtonHeight),
                        )
                        TextButton(
                            onClick = { showKeys = !showKeys },
                            modifier = Modifier.height(CompactButtonHeight),
                            contentPadding = CompactButtonPadding,
                        ) { Text("Keys", style = MaterialTheme.typography.labelSmall) }
                        IconButton(onClick = assistantViewModel::clearConversation, modifier = Modifier.height(CompactButtonHeight)) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear conversation", modifier = Modifier.height(18.dp))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.providerPreference == null,
                            onClick = { assistantViewModel.selectCloudProvider(null) },
                            enabled = !state.isSending,
                            label = { Text("Auto", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(CompactButtonHeight),
                        )
                        FilterChip(
                            selected = state.providerPreference == TerrainAiProvider.OPENAI,
                            onClick = { assistantViewModel.selectCloudProvider(TerrainAiProvider.OPENAI) },
                            enabled = state.openAiConfigured && !state.isSending,
                            label = { Text("OpenAI", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(CompactButtonHeight),
                        )
                        FilterChip(
                            selected = state.providerPreference == TerrainAiProvider.GEMINI,
                            onClick = { assistantViewModel.selectCloudProvider(TerrainAiProvider.GEMINI) },
                            enabled = state.geminiConfigured && !state.isSending,
                            label = { Text("Gemini", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(CompactButtonHeight),
                        )
                    }
                }
            }
        }

        if (showKeys) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = openAiKey,
                            onValueChange = { openAiKey = it.trim().take(256) },
                            label = { Text("OpenAI API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    assistantViewModel.saveOpenAiKey(openAiKey)
                                    openAiKey = ""
                                },
                                enabled = openAiKey.length >= 20,
                                modifier = Modifier.height(CompactButtonHeight),
                                contentPadding = CompactButtonPadding,
                            ) { Text("Save OpenAI", style = MaterialTheme.typography.labelSmall) }
                            if (state.hasDeviceOpenAiKey) {
                                OutlinedButton(
                                    onClick = assistantViewModel::clearOpenAiKey,
                                    modifier = Modifier.height(CompactButtonHeight),
                                    contentPadding = CompactButtonPadding,
                                ) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it.trim().take(256) },
                            label = { Text("Gemini API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    assistantViewModel.saveGeminiKey(geminiKey)
                                    geminiKey = ""
                                },
                                enabled = geminiKey.length >= 20,
                                modifier = Modifier.height(CompactButtonHeight),
                                contentPadding = CompactButtonPadding,
                            ) { Text("Save Gemini", style = MaterialTheme.typography.labelSmall) }
                            if (state.hasDeviceGeminiKey) {
                                OutlinedButton(
                                    onClick = assistantViewModel::clearGeminiKey,
                                    modifier = Modifier.height(CompactButtonHeight),
                                    contentPadding = CompactButtonPadding,
                                ) { Text("Remove", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "Quick questions",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AI_BUILT_IN_QUESTIONS.forEach { question ->
                        AssistChip(
                            onClick = {
                                draft = question
                                if (imageReady) attachImage = true
                            },
                            label = { Text(question, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.height(16.dp))
                            },
                            enabled = !state.isSending,
                            modifier = Modifier.height(CompactButtonHeight),
                        )
                    }
                }
            }
        }

        items(state.messages, key = AiMessage::id) { message ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.role == AiMessageRole.USER) Arrangement.End else Arrangement.Start,
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (message.role == AiMessageRole.USER) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    modifier = Modifier.fillMaxWidth(0.94f),
                ) {
                    Column(Modifier.padding(11.dp)) {
                        Text(
                            if (message.role == AiMessageRole.USER) "You" else message.provider?.label ?: "Terrain intelligence",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(message.text)
                    }
                }
            }
        }

        if (state.isSending) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    Text(state.cloudStage)
                }
            }
        }

        state.cloudError?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(4_000) },
                    label = { Text("Ask AI to analyze or mark targets") },
                    minLines = 1,
                    maxLines = 4,
                    enabled = !state.isSending,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        assistantViewModel.send(
                            prompt = draft,
                            terrainContext = terrainContext,
                            viewport = viewport,
                            attachViewportImage = attachImage && imageReady,
                            terrainKey = terrainKey,
                        )
                        draft = ""
                    },
                    enabled = draft.isNotBlank() && !state.isSending && state.activeProvider != null,
                    modifier = Modifier.height(CompactButtonHeight),
                    contentPadding = CompactButtonPadding,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.height(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Send", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
