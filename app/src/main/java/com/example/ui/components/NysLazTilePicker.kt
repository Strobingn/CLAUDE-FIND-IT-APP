package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.DemGenerator
import com.example.data.GroundSurfaceMode
import com.example.data.LazDatasetStore
import com.example.data.LazDownloadManager
import com.example.data.LazImportRepository
import com.example.data.LazTerrainCache
import com.example.data.LazTerrainDiskCache
import com.example.data.LazTerrainMemoryCache
import com.example.data.LidarImportOptions
import com.example.data.MosaicTerrainBuilder
import com.example.data.MosaicTerrainTile
import com.example.data.NysHistoricLazTileCatalog
import com.example.data.TerrainDecodeCoordinator
import com.example.data.TerrainImportSource
import com.example.data.TerrainPerformanceSession
import com.example.data.local.AppDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.mosaic.MosaicProject
import com.example.data.mosaic.MosaicProjectTile
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect

/** Visible NYS Southeast 4 County tile resolver and downloader for historic-site work. */
@Composable
fun NysLazTilePicker(
    onCustomTerrainLoaded: (DemGenerator.TerrainLoadResult, TerrainImportSource?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { NysHistoricLazTileCatalog() }
    val downloader = remember { LazImportRepository(LazDownloadManager()) }
    val store = remember(context) {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        LazDatasetStore(File(base, "lidar"))
    }
    val diskCache = remember(context) { LazTerrainDiskCache(File(context.cacheDir, "decoded-terrain")) }
    val terrainCache = remember(diskCache) { LazTerrainCache(LazTerrainMemoryCache(), diskCache) }
    val decodeCoordinator = remember(terrainCache) { TerrainDecodeCoordinator(terrainCache) }
    val mosaicProjectDao = remember(context) { AppDatabase.get(context).mosaicProjectDao() }

    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var west by remember { mutableStateOf("") }
    var south by remember { mutableStateOf("") }
    var east by remember { mutableStateOf("") }
    var north by remember { mutableStateOf("") }
    var mosaicProjectName by remember { mutableStateOf("") }
    var tiles by remember { mutableStateOf<List<NysHistoricLazTileCatalog.Tile>>(emptyList()) }
    var savedMosaicProjects by remember { mutableStateOf<List<MosaicProject>>(emptyList()) }
    var selectedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadEstimate by remember { mutableStateOf<NysHistoricLazTileCatalog.DownloadEstimate?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    var isEstimatingDownload by remember { mutableStateOf(false) }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mosaicProjectDao) {
        mosaicProjectDao.observeAll().collect { stored ->
            savedMosaicProjects = stored.map { it.toDomain() }.filter { it.tiles.isNotEmpty() }
        }
    }

    fun lookup() {
        val lat = latitude.trim().toDoubleOrNull()
        val lon = longitude.trim().toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            error = "Enter valid latitude and longitude coordinates."
            return
        }
        isLookingUp = true
        error = null
        downloadEstimate = null
        status = "Finding the exact NYS LiDAR tile…"
        scope.launch {
            try {
                tiles = catalog.tilesAt(lon, lat)
                selectedUrls = tiles.map { it.downloadUrl }.toSet()
                status = if (tiles.isEmpty()) {
                    "No Southeast 4 County 2022 tile covers that coordinate."
                } else {
                    "Found ${tiles.size} matching tile${if (tiles.size == 1) "" else "s"}."
                }
            } catch (t: Throwable) {
                tiles = emptyList()
                error = t.localizedMessage ?: "NYS tile lookup failed."
                status = null
            } finally {
                isLookingUp = false
            }
        }
    }

    fun lookupArea() {
        val westValue = west.trim().toDoubleOrNull()
        val southValue = south.trim().toDoubleOrNull()
        val eastValue = east.trim().toDoubleOrNull()
        val northValue = north.trim().toDoubleOrNull()
        if (westValue == null || southValue == null || eastValue == null || northValue == null ||
            westValue !in -180.0..180.0 || eastValue !in -180.0..180.0 ||
            southValue !in -90.0..90.0 || northValue !in -90.0..90.0 ||
            westValue >= eastValue || southValue >= northValue
        ) {
            error = "Enter west < east and south < north geographic bounds."
            return
        }
        isLookingUp = true
        error = null
        downloadEstimate = null
        status = "Resolving every LiDAR tile intersecting this area…"
        scope.launch {
            try {
                tiles = catalog.tilesInBounds(westValue, southValue, eastValue, northValue)
                selectedUrls = tiles.map { it.downloadUrl }.toSet()
                status = if (tiles.isEmpty()) "No NYS/USGS LiDAR tiles intersect that area." else
                    "Found ${tiles.size} intersecting tiles. Select files, then build one mosaic."
            } catch (t: Throwable) {
                tiles = emptyList()
                selectedUrls = emptySet()
                error = t.localizedMessage ?: "Area tile lookup failed."
                status = null
            } finally {
                isLookingUp = false
            }
        }
    }

    fun estimateSelectedDownload() {
        if (isEstimatingDownload || downloadJob?.isActive == true) return
        val selected = tiles.filter { it.downloadUrl in selectedUrls }
        if (selected.isEmpty()) {
            error = "Select at least one tile before estimating storage."
            return
        }
        val existingNames = store.list().mapTo(mutableSetOf()) { it.displayName }
        val needed = selected.filterNot { it.name in existingNames }
        error = null
        isEstimatingDownload = true
        status = if (needed.isEmpty()) {
            "All selected source files are already stored offline."
        } else {
            "Checking source-file sizes without downloading terrain…"
        }
        scope.launch {
            try {
                val estimate = if (needed.isEmpty()) {
                    NysHistoricLazTileCatalog.DownloadEstimate(knownBytes = 0L, unknownTileCount = 0)
                } else {
                    catalog.estimateDownloadBytes(needed)
                }
                downloadEstimate = estimate
                status = when {
                    estimate.unknownTileCount == 0 ->
                        "Storage needed: ${formatBytesCompact(estimate.knownBytes)} for ${needed.size} new file${if (needed.size == 1) "" else "s"}."
                    estimate.knownBytes > 0L ->
                        "Known storage: ${formatBytesCompact(estimate.knownBytes)}. ${estimate.unknownTileCount} file size${if (estimate.unknownTileCount == 1) " is" else "s are"} unavailable."
                    else ->
                        "The server did not report sizes for ${estimate.unknownTileCount} selected file${if (estimate.unknownTileCount == 1) "" else "s"}."
                }
            } catch (t: Throwable) {
                downloadEstimate = null
                error = t.localizedMessage ?: "Could not estimate source-file storage."
                status = null
            } finally {
                isEstimatingDownload = false
            }
        }
    }

    fun downloadAndOpen(tile: NysHistoricLazTileCatalog.Tile) {
        if (downloadingUrl != null) return
        downloadingUrl = tile.downloadUrl
        error = null
        status = "Downloading ${tile.name}…"
        scope.launch {
            try {
                val file = downloader.importFromUrl(
                    url = tile.downloadUrl,
                    store = store,
                    onProgress = { downloaded, total ->
                        scope.launch {
                            status = if (total > 0L) {
                                "Downloading ${tile.name}: ${percent(downloaded, total)}%"
                            } else {
                                "Downloading ${tile.name}: ${formatBytesCompact(downloaded)}"
                            }
                        }
                    },
                )
                status = "Building bare-earth terrain from source ground classes…"
                val options = LidarImportOptions(
                    groundMode = GroundSurfaceMode.SOURCE_CLASSIFIED,
                    rasterResolution = 1_024,
                    smoothingRadius = 0,
                )
                val outcome = decodeCoordinator.decode(
                    file = file,
                    displayName = tile.name,
                    options = options,
                    onStage = { stage ->
                        scope.launch { status = stage }
                    },
                )
                TerrainPerformanceSession.publish(outcome.gpuScene)
                onCustomTerrainLoaded(
                    outcome.terrain,
                    TerrainImportSource(
                        uri = Uri.fromFile(file).toString(),
                        displayName = tile.name,
                        options = options,
                    ),
                )
                status = "Opened ${tile.name} using ASPRS ground class 2 with class 8 fallback."
            } catch (_: CancellationException) {
                status = "Tile download cancelled."
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Tile download or decode failed."
                status = null
            } finally {
                downloadingUrl = null
            }
        }
    }

    fun downloadSelectedMosaic() {
        if (downloadJob?.isActive == true) return
        val selected = tiles.filter { it.downloadUrl in selectedUrls }
        if (selected.isEmpty()) {
            error = "Select at least one tile for the mosaic."
            return
        }
        val estimate = downloadEstimate
        if (estimate == null || estimate.unknownTileCount != 0) {
            error = "Estimate the selected download before starting the mosaic. Every selected source file must report its size."
            return
        }
        if (selected.any { it.minLongitude == null || it.minLatitude == null || it.maxLongitude == null || it.maxLatitude == null }) {
            error = "One or more selected tiles have no geographic footprint, so they cannot form a safe mosaic."
            return
        }
        error = null
        downloadJob = scope.launch {
            try {
                val options = LidarImportOptions(
                    groundMode = GroundSurfaceMode.SOURCE_CLASSIFIED,
                    rasterResolution = 1_024,
                    smoothingRadius = 0,
                )
                val projectTiles = mutableListOf<MosaicProjectTile>()
                val decodedTiles = selected.mapIndexed { index, tile ->
                    status = "${index + 1}/${selected.size}: preparing ${tile.name}…"
                    val existing = store.list().firstOrNull { it.displayName == tile.name }?.file
                    val file = existing ?: downloader.importFromUrl(
                        url = tile.downloadUrl,
                        store = store,
                        onProgress = { downloaded, total ->
                            status = if (total > 0L) {
                                "${index + 1}/${selected.size}: ${tile.name} ${percent(downloaded, total)}%"
                            } else {
                                "${index + 1}/${selected.size}: ${formatBytesCompact(downloaded)} downloaded"
                            }
                        },
                    )
                    status = "${index + 1}/${selected.size}: decoding ${tile.name}…"
                    val outcome = decodeCoordinator.decode(file, tile.name, options) { stage -> status = stage }
                    val bounds = com.example.geospatial.GeoSpatialLibrary.GeographicBounds(
                        minLat = requireNotNull(tile.minLatitude),
                        maxLat = requireNotNull(tile.maxLatitude),
                        minLon = requireNotNull(tile.minLongitude),
                        maxLon = requireNotNull(tile.maxLongitude),
                    )
                    projectTiles += MosaicProjectTile(
                        displayName = tile.name,
                        localFileName = file.name,
                        sourceUrl = tile.downloadUrl,
                        bounds = bounds,
                    )
                    MosaicTerrainTile(
                        displayName = tile.name,
                        terrain = outcome.terrain,
                        bounds = bounds,
                    )
                }
                status = "Mosaicking ${decodedTiles.size} source tiles without filling gaps…"
                val projectName = mosaicProjectName.trim().ifBlank {
                    "NYS/USGS ${decodedTiles.size}-tile project"
                }
                val mosaic = withContext(Dispatchers.Default) {
                    MosaicTerrainBuilder.build(projectName, decodedTiles)
                }
                val now = System.currentTimeMillis()
                mosaicProjectDao.upsert(
                    MosaicProject(
                        id = UUID.randomUUID().toString(),
                        displayName = projectName,
                        tiles = projectTiles,
                        createdAtMillis = now,
                        updatedAtMillis = now,
                    ).toEntity(),
                )
                TerrainPerformanceSession.publish(com.example.data.TerrainGpuSceneBuilder.build(mosaic.grid))
                onCustomTerrainLoaded(mosaic, null)
                status = "Saved and opened ${decodedTiles.size}-tile georeferenced mosaic. Source files remain offline."
            } catch (_: CancellationException) {
                status = "Mosaic download cancelled. Completed source files remain available for retry."
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Mosaic download or decode failed."
                status = null
            } finally {
                downloadJob = null
            }
        }
    }

    fun openMosaicProject(project: MosaicProject) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            try {
                val options = LidarImportOptions(
                    groundMode = GroundSurfaceMode.SOURCE_CLASSIFIED,
                    rasterResolution = 1_024,
                    smoothingRadius = 0,
                )
                val decodedTiles = project.tiles.mapIndexed { index, tile ->
                    val file = File(store.directory, tile.localFileName)
                    require(store.contains(file)) { "Source file is unavailable: ${tile.localFileName}" }
                    status = "Reopening ${index + 1}/${project.tiles.size}: ${tile.displayName}…"
                    val outcome = decodeCoordinator.decode(file, tile.displayName, options) { stage -> status = stage }
                    MosaicTerrainTile(tile.displayName, outcome.terrain, tile.bounds)
                }
                status = "Rebuilding ${project.displayName}…"
                val mosaic = withContext(Dispatchers.Default) {
                    MosaicTerrainBuilder.build(project.displayName, decodedTiles)
                }
                mosaicProjectDao.upsert(project.copy(updatedAtMillis = System.currentTimeMillis()).toEntity())
                TerrainPerformanceSession.publish(com.example.data.TerrainGpuSceneBuilder.build(mosaic.grid))
                onCustomTerrainLoaded(mosaic, null)
                status = "Reopened ${project.displayName}."
            } catch (_: CancellationException) {
                status = "Project reopening cancelled."
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Could not reopen mosaic project."
                status = null
            } finally {
                downloadJob = null
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth().testTag("nys_laz_tile_picker"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("NYS historic LAZ tiles", style = MaterialTheme.typography.titleLarge)
                    Text(
                        NysHistoricLazTileCatalog.PROJECT_NAME,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Enter a coordinate in the woods. The app checks the official NYS tile polygons, identifies the exact file, downloads it, and opens the source-classified bare-earth surface.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Area workflow: enter a geographic box to resolve every intersecting official tile, choose the files, then open one georeferenced terrain mosaic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = mosaicProjectName,
                onValueChange = { mosaicProjectName = it.take(80) },
                label = { Text("Mosaic project name") },
                placeholder = { Text("North woods survey") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it.take(16); error = null },
                    label = { Text("Latitude") },
                    placeholder = { Text("41.43") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("nys_tile_latitude"),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it.take(17); error = null },
                    label = { Text("Longitude") },
                    placeholder = { Text("-74.04") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("nys_tile_longitude"),
                )
            }
            Button(
                onClick = ::lookup,
                enabled = !isLookingUp && downloadingUrl == null,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("find_nys_laz_tiles"),
            ) {
                if (isLookingUp) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Find exact LAZ tile")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = west,
                    onValueChange = { west = it.take(17); error = null },
                    label = { Text("West lon") },
                    placeholder = { Text("-74.05") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = east,
                    onValueChange = { east = it.take(17); error = null },
                    label = { Text("East lon") },
                    placeholder = { Text("-74.03") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = south,
                    onValueChange = { south = it.take(16); error = null },
                    label = { Text("South lat") },
                    placeholder = { Text("41.42") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = north,
                    onValueChange = { north = it.take(16); error = null },
                    label = { Text("North lat") },
                    placeholder = { Text("41.44") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = ::lookupArea,
                enabled = !isLookingUp && downloadingUrl == null && downloadJob?.isActive != true,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("find_nys_laz_area"),
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Find tiles in area")
            }

            tiles.forEach { tile ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = tile.downloadUrl in selectedUrls,
                        onCheckedChange = { checked ->
                            selectedUrls = if (checked) selectedUrls + tile.downloadUrl else selectedUrls - tile.downloadUrl
                            downloadEstimate = null
                        },
                    )
                    OutlinedButton(
                        onClick = { downloadAndOpen(tile) },
                        enabled = downloadingUrl == null && downloadJob?.isActive != true,
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(tile.name, maxLines = 1)
                            Text("Open this one tile", style = MaterialTheme.typography.bodySmall)
                        }
                        if (downloadingUrl == tile.downloadUrl) {
                            CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            if (tiles.isNotEmpty()) {
                OutlinedButton(
                    onClick = ::estimateSelectedDownload,
                    enabled = downloadingUrl == null && downloadJob?.isActive != true &&
                        !isLookingUp && !isEstimatingDownload && selectedUrls.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("estimate_nys_mosaic_download"),
                ) {
                    if (isEstimatingDownload) {
                        CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isEstimatingDownload -> "Checking download size…"
                            downloadEstimate?.unknownTileCount == 0 ->
                                "Storage: ${formatBytesCompact(downloadEstimate?.knownBytes ?: 0L)}"
                            else -> "Estimate selected download"
                        },
                    )
                }
                Button(
                    onClick = ::downloadSelectedMosaic,
                    enabled = downloadingUrl == null && downloadJob?.isActive != true &&
                        !isEstimatingDownload && selectedUrls.isNotEmpty() &&
                        downloadEstimate?.unknownTileCount == 0,
                    modifier = Modifier.fillMaxWidth().height(54.dp).testTag("download_nys_mosaic"),
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download ${selectedUrls.size} tile${if (selectedUrls.size == 1) "" else "s"} and open mosaic")
                }
                if (downloadJob?.isActive == true) {
                    OutlinedButton(
                        onClick = { downloadJob?.cancel() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Cancel mosaic download") }
                }
            }

            if (savedMosaicProjects.isNotEmpty()) {
                Text("Saved multi-tile projects", style = MaterialTheme.typography.titleMedium)
                savedMosaicProjects.forEach { project ->
                    OutlinedButton(
                        onClick = { openMosaicProject(project) },
                        enabled = downloadingUrl == null && downloadJob?.isActive != true,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    ) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(project.displayName, maxLines = 1)
                            Text(
                                "${project.tiles.size} source tile${if (project.tiles.size == 1) "" else "s"} · tap to reopen",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    TextButton(
                        onClick = { scope.launch { mosaicProjectDao.deleteById(project.id) } },
                        enabled = downloadJob?.isActive != true,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Remove project entry") }
                }
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun percent(downloaded: Long, total: Long): Int =
    ((downloaded.coerceAtLeast(0L) * 100L) / total.coerceAtLeast(1L)).toInt().coerceIn(0, 100)

private fun formatBytesCompact(bytes: Long): String {
    val mib = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MiB", mib)
}
