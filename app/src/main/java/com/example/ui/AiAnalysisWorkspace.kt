package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.analysis.MetalDetectingTarget
import com.example.analysis.MetalDetectingTargetRefiner
import com.example.analysis.TerrainDerivedLayer
import com.example.analysis.VerifiedFeedback
import com.example.data.DetectionSource
import com.example.data.MetalType
import com.example.data.NormalizedRasterBounds
import com.example.data.TargetSignal
import com.example.data.local.buildAnalyzedDatasetEntity
import com.example.geospatial.GeoSpatialLibrary
import com.example.ui.components.LidarCanvasMode
import com.example.ui.components.LidarMapCanvas
import com.example.ui.components.LidarOverlayTarget
import java.nio.ByteBuffer
import java.security.MessageDigest
internal const val AI_HISTORIC_TARGETS_DEFAULT_VISIBLE = true

/**
 * One-map AI workspace tailored to historic-site reconnaissance for metal detecting.
 * LiDAR ranks occupation and travel features; it cannot directly identify a silver coin.
 */
@Composable
fun AiAnalysisWorkspace(
    viewModel: HillshadeViewModel,
    padding: PaddingValues,
    assistantViewModel: AiTerrainViewModel = viewModel(key = "ai_analysis_workspace"),
) {
    val summary by viewModel.activeTerrainSummary.collectAsStateWithLifecycle()
    val grid by viewModel.elevationGrid.collectAsStateWithLifecycle()
    val metadata by viewModel.activeGeoMetadata.collectAsStateWithLifecycle()
    val sourceBitmap by viewModel.hillshadeBitmap.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val isRefining by viewModel.isRefiningTerrain.collectAsStateWithLifecycle()
    val refinementProgress by viewModel.terrainRefinementProgress.collectAsStateWithLifecycle()
    val canRefine by viewModel.canRefineTerrain.collectAsStateWithLifecycle()
    val signals by viewModel.loggedSignals.collectAsStateWithLifecycle()
    val terrainKey by viewModel.activeTerrainKey.collectAsStateWithLifecycle()
    val gridSpacing by viewModel.gridSpacing.collectAsStateWithLifecycle()
    val aiState by assistantViewModel.state.collectAsStateWithLifecycle()

    val visibleBounds = remember { mutableStateOf(NormalizedRasterBounds.Full) }
    val zoomLevel = rememberSaveable { mutableStateOf(1f) }
    val centerMarkerMode = rememberSaveable { mutableStateOf(false) }
    val showTargetDetails = rememberSaveable { mutableStateOf(false) }
    val showHistoricTargets = rememberSaveable { mutableStateOf(AI_HISTORIC_TARGETS_DEFAULT_VISIBLE) }
    val showCloudTargets = rememberSaveable { mutableStateOf(true) }
    val showDatasetComparison = rememberSaveable { mutableStateOf(false) }
    val analyzedDatasets by viewModel.analyzedDatasets.collectAsStateWithLifecycle()
    val analysisBitmap = if (aiState.showSourceHillshade) {
        sourceBitmap
    } else {
        aiState.localLayerBitmap ?: sourceBitmap
    }
    // Re-derives live from the current logged finds (not just at "Analyze" time) so marking a
    // find CONFIRMED/REJECTED in the Finds tab immediately re-scores historic targets here too,
    // without needing to re-run the full (much more expensive) derived-layer analysis.
    val historicTargets = remember(aiState.localResult, signals) {
        val result = aiState.localResult ?: return@remember emptyList()
        val feedbackPoints = VerifiedFeedback.derive(signals, result.datasetKey)
        MetalDetectingTargetRefiner.refine(result, feedbackPoints)
    }
    val targetOverlays = remember(historicTargets) {
        historicTargets
            .sortedByDescending { it.score }
            .mapIndexed { index, target ->
                LidarOverlayTarget(
                    xPercent = target.xPercent,
                    yPercent = target.yPercent,
                    label = "${index + 1}. ${target.type.label} · ${(target.score * 100f).toInt()}%",
                )
            }
    }
    val savedCloudTargets = remember(signals) {
        signals.filter { it.source == DetectionSource.CLOUD_AI }.map { signal ->
            CloudMapTarget(
                xPercent = signal.gridX,
                yPercent = signal.gridY,
                label = signal.notes.substringAfter(CLOUD_AI_NOTE_PREFIX).substringBefore(" ·").ifBlank { "AI target" },
                confidence = (signal.signalStrength / 100f).coerceIn(0f, 1f),
            )
        }
    }
    val currentCloudTargets = cloudTargetsForTerrain(aiState, terrainKey)
    val visibleCloudTargets = remember(savedCloudTargets, currentCloudTargets) {
        (savedCloudTargets + currentCloudTargets)
            .distinctBy { cloudTargetIdentity(it) }
    }
    val cloudTargetOverlays = remember(visibleCloudTargets, showCloudTargets.value) {
        if (!showCloudTargets.value) return@remember emptyList()
        visibleCloudTargets.mapIndexed { index, target ->
            LidarOverlayTarget(
                xPercent = target.xPercent,
                yPercent = target.yPercent,
                label = "Cloud AI ${index + 1}. ${target.label} · ${(target.confidence * 100f).toInt()}%",
            )
        }
    }

    LaunchedEffect(currentCloudTargets, terrainKey, metadata) {
        currentCloudTargets.forEach { target ->
            val coordinate = GeoSpatialLibrary.gridToGeographic(target.xPercent, target.yPercent, metadata)
            viewModel.updateLoggedSignal(
                TargetSignal(
                    id = stableCloudTargetId(terrainKey, target),
                    gridX = target.xPercent,
                    gridY = target.yPercent,
                    metalType = MetalType.MAGNETIC_ANOMALY,
                    signalStrength = target.confidence * 100f,
                    latitude = coordinate?.first,
                    longitude = coordinate?.second,
                    source = DetectionSource.CLOUD_AI,
                    notes = "$CLOUD_AI_NOTE_PREFIX${target.label} · Generated from the attached AI viewport; terrain evidence only.",
                    status = "AI suggested",
                    datasetKey = aiState.localResult?.datasetKey,
                    terrainKey = terrainKey,
                ),
            )
        }
    }

    // Persists a stable (feedback-free) snapshot of this dataset's targets whenever a fresh
    // analysis result arrives, so it can later be cross-compared against a different dataset -
    // without this, there is nothing for multi-dataset comparison to compare against once the
    // app moves on to a different import.
    LaunchedEffect(aiState.localResult) {
        val result = aiState.localResult ?: return@LaunchedEffect
        val rawTargets = MetalDetectingTargetRefiner.refine(result)
        viewModel.saveDatasetSnapshot(
            buildAnalyzedDatasetEntity(
                datasetKey = result.datasetKey,
                displayName = summary.take(60).ifBlank { result.datasetKey },
                metadata = metadata,
                targets = rawTargets,
            ),
        )
    }

    LaunchedEffect(grid, summary, isRendering) {
        // The ViewModel is recreated after an update or process death, but the expensive derived
        // layers remain in the on-disk cache. Restore them as soon as the real terrain is ready.
        if (!isRendering && grid.width > 2 && grid.height > 2) {
            assistantViewModel.restoreLocalAnalysis(grid, summary)
        }
    }

    fun saveMarker(
        x: Float,
        y: Float,
        metalType: MetalType,
        source: DetectionSource,
        strength: Float,
        notes: String,
    ) {
        val coordinate = GeoSpatialLibrary.gridToGeographic(x, y, metadata)
        viewModel.updateLoggedSignal(
            TargetSignal(
                gridX = x.coerceIn(0f, 100f),
                gridY = y.coerceIn(0f, 100f),
                metalType = metalType,
                signalStrength = strength.coerceIn(0f, 100f),
                latitude = coordinate?.first,
                longitude = coordinate?.second,
                source = source,
                notes = notes,
                status = if (source == DetectionSource.AI_ANALYSIS) "AI suggested" else "Logged",
                // Ties this find back to the exact analyzed dataset, so a later verified outcome
                // (confirmed/rejected in the Finds tab) feeds back into re-scoring this dataset's
                // candidates instead of being unattributable.
                datasetKey = aiState.localResult?.datasetKey,
                terrainKey = terrainKey,
            ),
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (aiState.showSourceHillshade) {
                                "Hillshade"
                            } else {
                                aiState.localResult?.let { aiState.selectedLayer.label } ?: "Hillshade"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when {
                                centerMarkerMode.value -> "Pan/zoom until the target is centered, then save it"
                                isRefining -> "Reloading original LAZ detail without changing your zoom…"
                                canRefine -> "${"%.1f".format(zoomLevel.value)}× · tap Refine for source detail"
                                else -> "Pre-1900 silver-site profile · pinch and drag"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            val requested = visibleBounds.value.sanitized()
                            viewModel.refineTerrain(requested, viewModel.recommendedAiRefineResolution())
                        },
                        enabled = canRefine && !isRefining && !centerMarkerMode.value,
                        modifier = Modifier.testTag("ai_refine_now_button"),
                    ) { Text(if (!canRefine) "No LAZ source" else if (isRefining) "Refining…" else "Refine") }
                    Button(
                        onClick = { assistantViewModel.runLocalAnalysis(grid, summary, signals) },
                        enabled = !aiState.isLocalAnalyzing,
                        modifier = Modifier.testTag("ai_run_local_analysis_button"),
                    ) {
                        if (aiState.isLocalAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (aiState.localResult == null) "Analyze" else "Re-run")
                        }
                    }
                }

                if (aiState.localResult != null) {
                    Text(
                        "Historic silver profile: homesites, cellar holes, refuse/privy pits, wagon roads, camps and stone walls",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = aiState.showSourceHillshade,
                            onClick = { assistantViewModel.selectSourceHillshade() },
                            label = { Text("Hillshade") },
                        )
                        TerrainDerivedLayer.entries.forEach { layer ->
                            FilterChip(
                                selected = !aiState.showSourceHillshade && aiState.selectedLayer == layer,
                                onClick = { assistantViewModel.selectLocalLayer(layer) },
                                label = { Text(layer.label) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { centerMarkerMode.value = !centerMarkerMode.value },
                        modifier = Modifier.testTag("ai_marker_mode_button"),
                    ) { Text(if (centerMarkerMode.value) "Cancel marker" else "Mark map center") }
                    Button(
                        onClick = {
                            val bounds = visibleBounds.value.sanitized()
                            val centerX = ((bounds.left + bounds.right) * 50.0).toFloat()
                            val centerY = ((bounds.top + bounds.bottom) * 50.0).toFloat()
                            saveMarker(
                                centerX,
                                centerY,
                                MetalType.MANUAL_MARKER,
                                DetectionSource.MANUAL,
                                0f,
                                "Manual historic-site marker placed at the center of the zoomed AI viewport.",
                            )
                            centerMarkerMode.value = false
                        },
                        enabled = centerMarkerMode.value,
                        modifier = Modifier.testTag("ai_save_manual_marker_button"),
                    ) { Text("Save center") }
                    Button(
                        onClick = {
                            showHistoricTargets.value = !showHistoricTargets.value
                        },
                        enabled = historicTargets.isNotEmpty(),
                        modifier = Modifier.testTag("ai_add_target_markers_button"),
                    ) { Text(if (showHistoricTargets.value) "Hide historic targets" else "Mark historic targets") }
                    if (historicTargets.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showTargetDetails.value = !showTargetDetails.value },
                            modifier = Modifier.testTag("ai_show_target_details_button"),
                        ) { Text(if (showTargetDetails.value) "Hide details" else "Show details") }
                    }
                    if (visibleCloudTargets.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showCloudTargets.value = !showCloudTargets.value },
                            modifier = Modifier.testTag("ai_toggle_cloud_targets_button"),
                        ) {
                            Text(if (showCloudTargets.value) "Hide cloud AI (${visibleCloudTargets.size})" else "Show cloud AI (${visibleCloudTargets.size})")
                        }
                    }
                    if (analyzedDatasets.size >= 2) {
                        OutlinedButton(
                            onClick = { showDatasetComparison.value = true },
                            modifier = Modifier.testTag("ai_compare_datasets_button"),
                        ) { Text("Compare datasets") }
                    }
                    Text("${signals.size} saved", style = MaterialTheme.typography.labelMedium)
                }

                if (showTargetDetails.value && historicTargets.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .testTag("ai_target_details_list"),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(historicTargets.sortedByDescending { it.score }, key = { "${it.type}-${it.xPercent}-${it.yPercent}" }) { target ->
                            TargetDetailCard(target)
                        }
                    }
                }
            }
        }

        LidarMapCanvas(
            bitmap = analysisBitmap,
            isRendering = isRendering || aiState.isLocalAnalyzing,
            sweepX = 50f,
            sweepY = 50f,
            loggedSignals = signals.filterNot { it.source == DetectionSource.CLOUD_AI },
            onSweepPositionChanged = { _, _ -> },
            onStopSweeping = {},
            gridSpacing = gridSpacing,
            geoMetadata = metadata,
            currentLat = null,
            currentLon = null,
            mode = LidarCanvasMode.EXPLORE,
            viewportResetKey = 0,
            showSurveyCursor = false,
            showCoordinateHud = false,
            overlayTargets = cloudTargetOverlays + if (showHistoricTargets.value) targetOverlays else emptyList(),
            onViewportChanged = { bounds, zoom, _, _ ->
                visibleBounds.value = bounds
                zoomLevel.value = zoom
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("ai_single_analysis_map"),
        )

        if (isRefining) {
            val progress = refinementProgress
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress?.fraction?.coerceIn(0f, 1f) ?: 0f },
                    modifier = Modifier.fillMaxWidth().testTag("ai_refine_progress"),
                )
                Text(
                    progress?.message ?: "Preparing refinement…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (aiState.isLocalAnalyzing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AiCloudPanel(
            terrainSummary = summary,
            grid = grid,
            metadata = metadata,
            terrainKey = terrainKey,
            assistantViewModel = assistantViewModel,
            // weight(1f), not fillMaxSize(): this Column isn't scrollable, and the header +
            // map above already claim their own height, so a fillMaxSize() panel here asked
            // for the full column height on top of that and pushed its own internal chat
            // list - including the text input at the bottom of it - past the visible screen
            // with no way to scroll to it. weight(1f) bounds it to the actual remaining space.
            modifier = Modifier.weight(1f),
        )
    }

    if (showDatasetComparison.value) {
        DatasetComparisonDialog(
            datasets = analyzedDatasets,
            onDismiss = { showDatasetComparison.value = false },
        )
    }
}

private const val CLOUD_AI_NOTE_PREFIX = "Cloud AI target: "

internal fun cloudTargetIdentity(target: CloudMapTarget): String =
    "${target.xPercent.toInt()}:${target.yPercent.toInt()}:${target.label.trim().lowercase()}"

internal fun cloudTargetsForTerrain(state: AiTerrainState, terrainKey: String): List<CloudMapTarget> =
    if (state.cloudTerrainKey == terrainKey) state.cloudMapTargets else emptyList()

internal fun stableCloudTargetId(terrainKey: String, target: CloudMapTarget): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$terrainKey|${cloudTargetIdentity(target)}".toByteArray())
    return ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long and Long.MAX_VALUE
}

@Composable
private fun TargetDetailCard(target: MetalDetectingTarget) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${target.type.label} · ${(target.score * 100f).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (target.verifiedNearby) {
                    Text(
                        "Field-verified nearby",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                target.evidence.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            target.cautionReasons.forEach { reason ->
                Text(
                    "⚠ $reason",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
