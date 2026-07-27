package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.TargetSignal
import com.example.data.VerificationOutcome
import com.example.data.field.BreadcrumbTrack
import com.example.data.field.FieldNavigation
import com.example.data.field.VoiceNoteRecorder
import com.example.data.field.createVoiceNoteFile
import com.example.data.field.deleteVoiceNoteFile
import com.example.geospatial.trueToMagneticBearingDegrees
import com.example.data.export.buildCsv
import com.example.data.export.buildGeoJson
import com.example.data.export.buildGpx
import com.example.data.export.buildKml
import com.example.data.export.ProjectExportFiles
import kotlinx.coroutines.launch

@Composable
fun TargetLoggerPanel(
    loggedSignals: List<TargetSignal>,
    currentSweepX: Float,
    currentSweepY: Float,
    breadcrumbTracks: List<BreadcrumbTrack>,
    isBreadcrumbRecording: Boolean,
    gpsEnabled: Boolean,
    deviceLatitude: Double?,
    deviceLongitude: Double?,
    deviceAccuracyMeters: Float?,
    compassHeadingDegrees: Float?,
    onEnableGps: () -> Unit,
    onSetCompassNavigationActive: (Boolean) -> Unit,
    onStartBreadcrumb: () -> Unit,
    onPauseBreadcrumb: () -> Unit,
    onClearBreadcrumbs: () -> Unit,
    onLogSignal: () -> Unit,
    onDeleteSignal: (TargetSignal) -> Unit,
    onUpdateSignal: (TargetSignal) -> Unit,
    onClearAll: () -> Unit,
    onBuildProjectExport: suspend () -> ProjectExportFiles,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingSignal by remember { mutableStateOf<TargetSignal?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var showProjectExport by remember { mutableStateOf(false) }
    var isBuildingProjectExport by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearBreadcrumbs by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var pendingCsv by remember { mutableStateOf("") }
    var pendingGpx by remember { mutableStateOf("") }
    var pendingKml by remember { mutableStateOf("") }
    var pendingGeoJson by remember { mutableStateOf("") }
    var pendingProjectBytes by remember { mutableStateOf(ByteArray(0)) }
    var navigationTarget by remember { mutableStateOf<TargetSignal?>(null) }

    LaunchedEffect(navigationTarget?.id) {
        onSetCompassNavigationActive(navigationTarget != null)
    }
    LaunchedEffect(loggedSignals) {
        navigationTarget?.let { active ->
            if (loggedSignals.none { it.id == active.id }) navigationTarget = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { onSetCompassNavigationActive(false) }
    }

    val terrainImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(pendingProjectBytes) }
                    ?: error("Could not open the selected destination")
            }.onSuccess { exportMessage = "Full terrain image saved" }
                .onFailure { exportMessage = "Save failed: ${it.localizedMessage}" }
        }
    }
    val projectPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(pendingProjectBytes) }
                    ?: error("Could not open the selected destination")
            }.onSuccess { exportMessage = "PDF field report saved" }
                .onFailure { exportMessage = "Save failed: ${it.localizedMessage}" }
        }
    }

    val buildProjectExport: (Boolean) -> Unit = { pdf ->
        showProjectExport = false
        isBuildingProjectExport = true
        exportMessage = if (pdf) "Building full PDF report…" else "Building full terrain image…"
        scope.launch {
            runCatching { onBuildProjectExport() }
                .onSuccess { files ->
                    pendingProjectBytes = if (pdf) files.reportPdf else files.terrainPng
                    if (pdf) {
                        projectPdfLauncher.launch("${files.fileStem}-field-report.pdf")
                    } else {
                        terrainImageLauncher.launch("${files.fileStem}-terrain.png")
                    }
                }
                .onFailure { exportMessage = "Export failed: ${it.localizedMessage}" }
            isBuildingProjectExport = false
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingCsv) }
                    ?: error("Could not open the selected destination")
            }.onSuccess { exportMessage = "CSV saved" }
                .onFailure { exportMessage = "Save failed: ${it.localizedMessage}" }
        }
    }
    val gpxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingGpx) }
                    ?: error("Could not open the selected destination")
            }.onSuccess { exportMessage = "GPX saved" }
                .onFailure { exportMessage = "Save failed: ${it.localizedMessage}" }
        }
    }
    val kmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingKml) }
                    ?: error("Could not open the selected destination")
            }.onSuccess { exportMessage = "KML saved" }
                .onFailure { exportMessage = "Save failed: ${it.localizedMessage}" }
        }
    }
    val geoJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingGeoJson) }
                    ?: error("Could not open the selected destination")
            }.onSuccess { exportMessage = "GeoJSON saved" }
                .onFailure { exportMessage = "Save failed: ${it.localizedMessage}" }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Field finds", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Current grid position: ${currentSweepX.toInt()}, ${currentSweepY.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onLogSignal,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("log_signal_button"),
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log current position")
                }
                OutlinedButton(
                    onClick = { showProjectExport = true },
                    enabled = !isBuildingProjectExport,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("project_export_button"),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isBuildingProjectExport) "Building export…" else "Export terrain and report")
                }
            }
        }

        val recordedBreadcrumbPoints = breadcrumbTracks.sumOf { it.points.size }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("GPS breadcrumb", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        isBreadcrumbRecording -> "Recording ${recordedBreadcrumbPoints} GPS fix${if (recordedBreadcrumbPoints == 1) "" else "es"}. Trails are saved with this terrain project."
                        recordedBreadcrumbPoints > 0 -> "$recordedBreadcrumbPoints saved GPS fix${if (recordedBreadcrumbPoints == 1) "" else "es"} across ${breadcrumbTracks.size} trail${if (breadcrumbTracks.size == 1) "" else "s"}."
                        else -> "Record a project-scoped path for offline field checking. GPS jitter is filtered automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = if (isBreadcrumbRecording) onPauseBreadcrumb else onStartBreadcrumb,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("breadcrumb_record_button"),
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isBreadcrumbRecording) "Pause trail" else "Start trail")
                    }
                    OutlinedButton(
                        onClick = { confirmClearBreadcrumbs = true },
                        enabled = breadcrumbTracks.isNotEmpty() && !isBreadcrumbRecording,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear trails")
                    }
                }
            }
        }

        navigationTarget?.let { target ->
            FieldNavigationCard(
                target = target,
                currentLatitude = deviceLatitude,
                currentLongitude = deviceLongitude,
                currentAccuracyMeters = deviceAccuracyMeters,
                headingDegrees = compassHeadingDegrees,
                gpsEnabled = gpsEnabled,
                onEnableGps = onEnableGps,
                onStop = { navigationTarget = null },
            )
        }

        if (exportMessage != null) {
            Text(
                exportMessage.orEmpty(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (loggedSignals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No finds logged yet. Sweep the map, then log the current position.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
                Button(
                    onClick = { showExport = true },
                    modifier = Modifier.weight(1.5f).height(48.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export GIS data")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("logged_signals_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(loggedSignals, key = { it.id }) { signal ->
                    SignalCard(
                        signal = signal,
                        onEdit = { editingSignal = signal },
                        onDelete = {
                            signal.voiceNoteUris.forEach { deleteVoiceNoteFile(context, it) }
                            onDeleteSignal(signal)
                        },
                        onNavigate = { navigationTarget = signal },
                    )
                }
            }
        }
    }

    editingSignal?.let { signal ->
        EditSignalDialog(
            signal = signal,
            onDismiss = { editingSignal = null },
            onSave = {
                onUpdateSignal(it)
                editingSignal = null
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all finds?") },
            text = { Text("This permanently removes ${loggedSignals.size} saved record(s).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        loggedSignals.flatMap { it.voiceNoteUris }.forEach { deleteVoiceNoteFile(context, it) }
                        confirmClear = false
                        onClearAll()
                    },
                ) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    if (showExport) {
        ExportGisDialog(
            signals = loggedSignals,
            onDismiss = { showExport = false },
            onSaveCsv = {
                pendingCsv = buildCsv(loggedSignals)
                showExport = false
                csvLauncher.launch("find-it-targets.csv")
            },
            onSaveGpx = {
                pendingGpx = buildGpx(loggedSignals)
                showExport = false
                gpxLauncher.launch("find-it-targets.gpx")
            },
            onSaveKml = {
                pendingKml = buildKml(loggedSignals)
                showExport = false
                kmlLauncher.launch("find-it-targets.kml")
            },
            onSaveGeoJson = {
                pendingGeoJson = buildGeoJson(loggedSignals)
                showExport = false
                geoJsonLauncher.launch("find-it-targets.geojson")
            },
        )
    }

    if (confirmClearBreadcrumbs) {
        AlertDialog(
            onDismissRequest = { confirmClearBreadcrumbs = false },
            title = { Text("Clear saved trails?") },
            text = { Text("This removes ${breadcrumbTracks.size} breadcrumb trail(s) from this terrain project.") },
            confirmButton = {
                TextButton(onClick = { confirmClearBreadcrumbs = false; onClearBreadcrumbs() }) {
                    Text("Clear trails")
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearBreadcrumbs = false }) { Text("Cancel") } },
        )
    }

    if (showProjectExport) {
        AlertDialog(
            onDismissRequest = { showProjectExport = false },
            title = { Text("Export this terrain project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Exports rebuild the complete source footprint, not the current screen crop.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("PNG includes saved targets, survey layers, legend, scale, and source coordinates.")
                    Text("PDF includes the annotated map, metadata, target records, survey provenance, and integrity notes.")
                }
            },
            confirmButton = {
                Button(onClick = { buildProjectExport(true) }) { Text("Save PDF") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showProjectExport = false }) { Text("Cancel") }
                    TextButton(onClick = { buildProjectExport(false) }) { Text("Save PNG") }
                }
            },
        )
    }
}

@Composable
private fun FieldNavigationCard(
    target: TargetSignal,
    currentLatitude: Double?,
    currentLongitude: Double?,
    currentAccuracyMeters: Float?,
    headingDegrees: Float?,
    gpsEnabled: Boolean,
    onEnableGps: () -> Unit,
    onStop: () -> Unit,
) {
    val targetLatitude = target.latitude
    val targetLongitude = target.longitude
    val solution = remember(currentLatitude, currentLongitude, targetLatitude, targetLongitude, headingDegrees) {
        if (currentLatitude == null || currentLongitude == null || targetLatitude == null || targetLongitude == null) {
            null
        } else {
            FieldNavigation.solve(
                currentLatitude = currentLatitude,
                currentLongitude = currentLongitude,
                targetLatitude = targetLatitude,
                targetLongitude = targetLongitude,
                headingDegrees = headingDegrees,
            )
        }
    }
    val magneticTargetBearing = remember(solution, currentLatitude, currentLongitude) {
        if (solution == null || currentLatitude == null || currentLongitude == null) {
            null
        } else {
            trueToMagneticBearingDegrees(
                trueBearingDegrees = solution.targetBearingDegrees,
                latitude = currentLatitude,
                longitude = currentLongitude,
            )
        }
    }
    val compassTurn = remember(headingDegrees, magneticTargetBearing) {
        if (headingDegrees == null || magneticTargetBearing == null) null
        else FieldNavigation.signedTurnDegrees(headingDegrees, magneticTargetBearing)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().testTag("field_navigation_card"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Navigation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Field navigation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${target.metalType.label} · ${target.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                TextButton(onClick = onStop) { Text("Stop") }
            }

            if (solution == null) {
                Text(
                    when {
                        targetLatitude == null || targetLongitude == null ->
                            "This saved target has no geographic coordinate, so it cannot be routed in the field."
                        !gpsEnabled -> "Start GPS to calculate distance and bearing to this target."
                        else -> "Waiting for a current GPS fix…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!gpsEnabled) {
                    Button(onClick = onEnableGps, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Start GPS navigation")
                    }
                }
                return@Column
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Default.Navigation,
                    contentDescription = "Direction to target",
                    modifier = Modifier
                        .size(62.dp)
                        .rotate(compassTurn ?: 0f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        formatNavigationDistance(solution.distanceMeters),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Target true ${FieldNavigation.compassDirection(solution.targetBearingDegrees)} · ${solution.targetBearingDegrees.toInt()}°",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    compassTurn?.let { turn ->
                        Text(
                            FieldNavigation.turnInstruction(turn),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } ?: Text(
                        "Hold the phone flat while the compass initializes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            headingDegrees?.let { heading ->
                Text(
                    "Compass heading ${FieldNavigation.compassDirection(heading)} · ${heading.toInt()}° magnetic",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            currentAccuracyMeters?.takeIf { it.isFinite() && it >= 0f }?.let { accuracy ->
                Text(
                    "Current GPS accuracy ±${"%.1f".format(java.util.Locale.US, accuracy)} m",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Compass guidance is for field checking. Calibrate the phone and verify the target against the terrain before excavating.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun formatNavigationDistance(meters: Double): String = when {
    meters >= 1_000.0 -> "${"%.2f".format(java.util.Locale.US, meters / 1_000.0)} km away"
    meters >= 100.0 -> "${meters.toInt()} m away"
    else -> "${"%.1f".format(java.util.Locale.US, meters)} m away"
}

@Composable
private fun SignalCard(
    signal: TargetSignal,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNavigate: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                tint = Color(signal.metalType.colorHex),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(signal.metalType.label, fontWeight = FontWeight.Bold)
                val depth = signal.depthCm?.let { "$it cm" } ?: "depth unknown"
                Text(
                    "Grid ${signal.gridX.toInt()}, ${signal.gridY.toInt()} · $depth · ${signal.signalStrength.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${signal.source.name.lowercase().replaceFirstChar { it.uppercase() }} · ${signal.status}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                signal.gpsAccuracyMeters?.let { accuracy ->
                    Text(
                        "GPS captured ${"%.1f".format(java.util.Locale.US, accuracy)} m accuracy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (signal.outcome != VerificationOutcome.UNVERIFIED) {
                    Text(
                        signal.outcome.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (signal.outcome) {
                            VerificationOutcome.CONFIRMED_FEATURE -> MaterialTheme.colorScheme.tertiary
                            VerificationOutcome.REJECTED_FALSE_POSITIVE -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (signal.notes.isNotBlank()) {
                    Text(signal.notes, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (signal.photoUris.isNotEmpty()) {
                    Text(
                        "${signal.photoUris.size} photo attachment${if (signal.photoUris.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (signal.voiceNoteUris.isNotEmpty()) {
                    Text(
                        "${signal.voiceNoteUris.size} voice note${if (signal.voiceNoteUris.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit find")
            }
            IconButton(
                onClick = onNavigate,
                enabled = signal.latitude != null && signal.longitude != null,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.Navigation, contentDescription = "Navigate to find")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete find")
            }
        }
    }
}

@Composable
private fun EditSignalDialog(
    signal: TargetSignal,
    onDismiss: () -> Unit,
    onSave: (TargetSignal) -> Unit,
) {
    val context = LocalContext.current
    var photoUris by remember(signal.id) { mutableStateOf(signal.photoUris) }
    var voiceNoteUris by remember(signal.id) { mutableStateOf(signal.voiceNoteUris) }
    var recorder by remember(signal.id) { mutableStateOf<VoiceNoteRecorder?>(null) }
    var isRecordingVoiceNote by remember(signal.id) { mutableStateOf(false) }
    var recordingMessage by remember(signal.id) { mutableStateOf<String?>(null) }
    var mediaPlayer by remember(signal.id) { mutableStateOf<MediaPlayer?>(null) }
    var playingVoiceNoteUri by remember(signal.id) { mutableStateOf<String?>(null) }

    fun stopPlayback() {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            player.release()
        }
        mediaPlayer = null
        playingVoiceNoteUri = null
    }

    fun startVoiceRecording() {
        if (isRecordingVoiceNote || voiceNoteUris.size >= 10) return
        val active = VoiceNoteRecorder(context, createVoiceNoteFile(context, signal))
        runCatching { active.start() }
            .onSuccess {
                recorder = active
                isRecordingVoiceNote = true
                recordingMessage = null
            }
            .onFailure {
                active.cancel()
                recordingMessage = it.localizedMessage ?: "Could not start the voice-note recorder."
            }
    }

    fun stopVoiceRecording() {
        val file = recorder?.stop()
        recorder = null
        isRecordingVoiceNote = false
        if (file == null) {
            recordingMessage = "The voice note was too short or could not be saved."
        } else {
            voiceNoteUris = (voiceNoteUris + Uri.fromFile(file).toString()).distinct().take(10)
            recordingMessage = "Voice note saved offline."
        }
    }

    fun playVoiceNote(uriText: String) {
        if (playingVoiceNoteUri == uriText) {
            stopPlayback()
            return
        }
        stopPlayback()
        val player = MediaPlayer()
        runCatching {
            player.setDataSource(context, Uri.parse(uriText))
            player.setOnCompletionListener {
                it.release()
                if (mediaPlayer === it) {
                    mediaPlayer = null
                    playingVoiceNoteUri = null
                }
            }
            player.prepare()
            player.start()
        }.onSuccess {
            mediaPlayer = player
            playingVoiceNoteUri = uriText
            recordingMessage = null
        }.onFailure {
            player.release()
            recordingMessage = it.localizedMessage ?: "Could not play this voice note."
        }
    }

    DisposableEffect(signal.id) {
        onDispose {
            recorder?.cancel()
            mediaPlayer?.release()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoiceRecording() else {
            recordingMessage = "Microphone permission is required to record a voice note."
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            photoUris = (photoUris + uri.toString()).distinct().take(10)
        }
    }
    var notes by remember(signal.id) { mutableStateOf(signal.notes) }
    var status by remember(signal.id) { mutableStateOf(signal.status) }
    var outcome by remember(signal.id) { mutableStateOf(signal.outcome) }
    val statuses = listOf(
        "AI suggested",
        "Selected",
        "Approaching",
        "Checked",
        "Productive",
        "Rejected",
        "Inconclusive",
        "Follow up",
    )
    AlertDialog(
        onDismissRequest = {
            recorder?.cancel()
            stopPlayback()
            onDismiss()
        },
        title = { Text("Edit find") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${signal.metalType.label} at grid ${signal.gridX.toInt()}, ${signal.gridY.toInt()}")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Photos (${photoUris.size}/10)", style = MaterialTheme.typography.titleSmall)
                photoUris.forEach { photoUri ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            photoUri.substringAfterLast('/').takeLast(32),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { photoUris = photoUris - photoUri }) { Text("Remove") }
                    }
                }
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = photoUris.size < 10,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add photo")
                }
                Text("Voice notes (${voiceNoteUris.size}/10)", style = MaterialTheme.typography.titleSmall)
                voiceNoteUris.forEachIndexed { index, voiceUri ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Voice note ${index + 1}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = { playVoiceNote(voiceUri) }) {
                            Icon(
                                if (playingVoiceNoteUri == voiceUri) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (playingVoiceNoteUri == voiceUri) "Stop voice note" else "Play voice note",
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(if (playingVoiceNoteUri == voiceUri) "Stop" else "Play")
                        }
                        TextButton(
                            onClick = {
                                if (playingVoiceNoteUri == voiceUri) stopPlayback()
                                deleteVoiceNoteFile(context, voiceUri)
                                voiceNoteUris = voiceNoteUris - voiceUri
                            },
                        ) { Text("Remove") }
                    }
                }
                Button(
                    onClick = {
                        if (isRecordingVoiceNote) {
                            stopVoiceRecording()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startVoiceRecording() else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    enabled = isRecordingVoiceNote || voiceNoteUris.size < 10,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("voice_note_record_button"),
                ) {
                    Icon(if (isRecordingVoiceNote) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRecordingVoiceNote) "Stop and save voice note" else "Record voice note")
                }
                recordingMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (message.startsWith("Could") || message.startsWith("Microphone") || message.startsWith("The voice")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                statuses.chunked(2).forEach { rowStatuses ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowStatuses.forEach { item ->
                            val selected = status == item
                            if (selected) {
                                Button(
                                    onClick = { status = item },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) { Text(item) }
                            } else {
                                OutlinedButton(
                                    onClick = { status = item },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) { Text(item) }
                            }
                        }
                    }
                }
                Text("Field verification", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Checked this location? Your answer feeds back into how future terrain analysis of this dataset scores similar candidates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VerificationOutcome.entries.chunked(2).forEach { rowOutcomes ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowOutcomes.forEach { item ->
                            val selected = outcome == item
                            if (selected) {
                                Button(
                                    onClick = { outcome = item },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("outcome_${item.name}"),
                                ) { Text(item.label, maxLines = 2, style = MaterialTheme.typography.labelMedium) }
                            } else {
                                OutlinedButton(
                                    onClick = { outcome = item },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("outcome_${item.name}"),
                                ) { Text(item.label, maxLines = 2, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
                if (signal.datasetKey == null && outcome != VerificationOutcome.UNVERIFIED) {
                    Text(
                        "This find isn't linked to a specific analyzed dataset, so this verification won't influence future scoring.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    stopPlayback()
                    onSave(
                        signal.copy(
                            notes = notes.trim(),
                            photoUris = photoUris,
                            voiceNoteUris = voiceNoteUris,
                            status = status,
                            outcome = outcome,
                        ),
                    )
                },
                enabled = !isRecordingVoiceNote,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    recorder?.cancel()
                    stopPlayback()
                    onDismiss()
                },
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun ExportGisDialog(
    signals: List<TargetSignal>,
    onDismiss: () -> Unit,
    onSaveCsv: () -> Unit,
    onSaveGpx: () -> Unit,
    onSaveKml: () -> Unit,
    onSaveGeoJson: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var format by remember { mutableStateOf(0) }
    val georeferenced = signals.count { it.latitude != null && it.longitude != null }
    val labels = listOf("CSV", "GPX", "KML", "GeoJSON")
    val content = remember(signals, format) {
        when (format) {
            0 -> buildCsv(signals)
            1 -> buildGpx(signals)
            2 -> buildKml(signals)
            else -> buildGeoJson(signals)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export field data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.chunked(2).forEachIndexed { rowIndex, rowLabels ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowLabels.forEachIndexed { columnIndex, label ->
                                val value = rowIndex * 2 + columnIndex
                                if (format == value) {
                                    Button(onClick = { format = value }, modifier = Modifier.weight(1f).height(48.dp)) { Text(label) }
                                } else {
                                    OutlinedButton(onClick = { format = value }, modifier = Modifier.weight(1f).height(48.dp)) { Text(label) }
                                }
                            }
                        }
                    }
                }
                Text(
                    if (format == 0) {
                        "CSV includes all ${signals.size} records. Coordinates remain blank when the source grid has no CRS."
                    } else {
                        "${labels[format]} includes $georeferenced of ${signals.size} records with real WGS84 coordinates."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(content)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy ${labels[format]}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = when (format) {
                    0 -> onSaveCsv
                    1 -> onSaveGpx
                    2 -> onSaveKml
                    else -> onSaveGeoJson
                },
                enabled = format == 0 || georeferenced > 0,
            ) { Text("Save file") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
