package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.FilterChip
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
import com.example.data.LazTerrainCache
import com.example.data.LazTerrainDiskCache
import com.example.data.LazTerrainMemoryCache
import com.example.data.LidarImportOptions
import com.example.data.MosaicTerrainBuilder
import com.example.data.MosaicTerrainTile
import com.example.data.LidarSearchRequest
import com.example.data.NortheastLidarRegion
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
import com.example.data.download.LazDownloadTask
import com.example.data.download.LazDownloadState
import com.example.data.download.LazDownloadService
import com.example.data.download.LazDownloadQueue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.LinearProgressIndicator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Public LiDAR tile resolver and downloader for historic-site work.
 *
 * Lookups run against USGS 3DEP and are not limited to one state; the region chips seed a search
 * box for the Northeast states this app is used in most.
 */
@Composable
fun NysLazTilePicker(
    onCustomTerrainLoaded: (DemGenerator.TerrainLoadResult, TerrainImportSource?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { NysHistoricLazTileCatalog() }
    val store = remember(context) { LazDownloadQueue.store(context) }
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
    var selectedRegion by remember { mutableStateOf<NortheastLidarRegion?>(null) }
    var mosaicProjectName by remember { mutableStateOf("") }
    var tiles by remember { mutableStateOf<List<NysHistoricLazTileCatalog.Tile>>(emptyList()) }
    var savedMosaicProjects by remember { mutableStateOf<List<MosaicProject>>(emptyList()) }
    var selectedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadEstimate by remember { mutableStateOf<NysHistoricLazTileCatalog.DownloadEstimate?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    var isEstimatingDownload by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Downloads live in LazDownloadService, so they survive leaving this screen. The picker only
    // observes them and opens a tile once its bytes have landed.
    val downloadTasks by LazDownloadQueue.tasks.collectAsStateWithLifecycle()
    var awaitingOpenUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Declined only costs the progress notification; the transfer itself still runs. */ }

    LaunchedEffect(mosaicProjectDao) {
        mosaicProjectDao.observeAll().collect { stored ->
            savedMosaicProjects = stored.map { it.toDomain() }.filter { it.tiles.isNotEmpty() }
        }
    }

    /**
     * Seeds the area box with a state extent. Deliberately does not run the search: a whole state
     * resolves to far more tiles than anyone wants to download, so the box is a starting point the
     * user narrows first.
     */
    fun applyRegion(region: NortheastLidarRegion) {
        selectedRegion = region
        west = region.west.toString()
        south = region.south.toString()
        east = region.east.toString()
        north = region.north.toString()
        error = null
        status = "${region.displayName} bounds loaded. Narrow the box, then find tiles in the area."
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
        status = "Finding the exact LiDAR tile…"
        scope.launch {
            try {
                tiles = catalog.tilesAt(lon, lat)
                selectedUrls = tiles.map { it.downloadUrl }.toSet()
                status = if (tiles.isEmpty()) {
                    "No published LiDAR tile covers that coordinate."
                } else {
                    "Found ${tiles.size} matching tile${if (tiles.size == 1) "" else "s"}."
                }
            } catch (t: Throwable) {
                tiles = emptyList()
                error = t.localizedMessage ?: "Tile lookup failed."
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
                status = when {
                    tiles.isEmpty() -> "No published LiDAR tiles intersect that area."
                    tiles.size >= NysHistoricLazTileCatalog.MAX_NATIONAL_MAP_RESULTS ->
                        "Found ${tiles.size} tiles — the per-search cap. Narrow the box to see the rest."
                    else -> "Found ${tiles.size} intersecting tiles. Select files, then build one mosaic."
                }
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

    // A box handed over from the map arrives here after the tab switch. Consuming it means
    // returning to this tab later does not silently repeat the search.
    val mapSearchBounds by LidarSearchRequest.pending.collectAsStateWithLifecycle()
    LaunchedEffect(mapSearchBounds) {
        val bounds = LidarSearchRequest.consume() ?: return@LaunchedEffect
        selectedRegion = null
        west = formatDegrees(bounds.minLon)
        south = formatDegrees(bounds.minLat)
        east = formatDegrees(bounds.maxLon)
        north = formatDegrees(bounds.maxLat)
        lookupArea()
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

    /** Decodes an already-downloaded file and hands the terrain to the workspace. */
    fun openDownloadedFile(file: File, displayName: String) {
        scope.launch {
            try {
                status = "Building bare-earth terrain from source ground classes…"
                val options = LidarImportOptions(
                    groundMode = GroundSurfaceMode.SOURCE_CLASSIFIED,
                    rasterResolution = 1_024,
                    smoothingRadius = 0,
                )
                val outcome = decodeCoordinator.decode(
                    file = file,
                    displayName = displayName,
                    options = options,
                    onStage = { stage -> scope.launch { status = stage } },
                )
                TerrainPerformanceSession.publish(outcome.gpuScene)
                onCustomTerrainLoaded(
                    outcome.terrain,
                    TerrainImportSource(
                        uri = Uri.fromFile(file).toString(),
                        displayName = displayName,
                        options = options,
                    ),
                )
                status = "Opened $displayName using ASPRS ground class 2 with class 8 fallback."
            } catch (_: CancellationException) {
                status = null
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Tile decode failed."
                status = null
            }
        }
    }

    /**
     * A copy of this tile already on disk, matched by the URL it came from.
     *
     * Tile names are only unique within a survey, so matching on name alone could hand back an
     * unrelated project's file of the same name. Files stored before provenance was recorded have
     * no index entry; those still fall back to the name, and the match is recorded so the
     * association is explicit from then on.
     */
    fun reusableFile(tile: NysHistoricLazTileCatalog.Tile): File? {
        store.fileForSource(tile.downloadUrl)?.let { return it }
        val byName = store.list().firstOrNull { it.displayName == tile.name }?.file ?: return null
        store.recordSource(tile.downloadUrl, byName)
        return byName
    }

    fun downloadAndOpen(tile: NysHistoricLazTileCatalog.Tile) {
        error = null
        // Already on disk from an earlier background download - skip straight to decoding.
        val existing = reusableFile(tile)
        if (existing != null) {
            openDownloadedFile(existing, tile.name)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Open this one automatically when its bytes land, but only if the user is still here.
        awaitingOpenUrl = tile.downloadUrl
        LazDownloadService.enqueue(context, tile.downloadUrl, tile.name)
        status = "Downloading ${tile.name} in the background. You can leave this screen."
    }

    // The service owns the transfer, so completion can arrive while this screen is composed or
    // long after it was left and re-entered. Either way, act on it exactly once.
    LaunchedEffect(downloadTasks, awaitingOpenUrl) {
        val url = awaitingOpenUrl ?: return@LaunchedEffect
        val task = downloadTasks.firstOrNull { it.url == url } ?: return@LaunchedEffect
        when (task.state) {
            LazDownloadState.COMPLETED -> {
                awaitingOpenUrl = null
                LazDownloadQueue.dismiss(url)
                task.filePath?.let { openDownloadedFile(File(it), task.displayName) }
            }
            LazDownloadState.FAILED -> {
                awaitingOpenUrl = null
                error = task.error ?: "Tile download failed."
                status = null
                // Deliberately left in the queue: dismissing here dropped the only record of the
                // failure, so a part-transferred tile could not be retried without resolving the
                // whole area again. The row below owns retrying and dismissing it.
            }
            LazDownloadState.CANCELLED -> {
                awaitingOpenUrl = null
                status = "Tile download cancelled."
                LazDownloadQueue.dismiss(url)
            }
            LazDownloadState.QUEUED, LazDownloadState.RUNNING -> Unit
        }
    }

    /**
     * Returns the tile's local file, downloading it through the background service first if
     * needed. The transfer itself is owned by the service, so leaving this screen mid-mosaic
     * keeps the bytes coming; returning and tapping again picks up the already-finished files
     * instead of starting over.
     */
    suspend fun awaitDownloadedFile(
        tile: NysHistoricLazTileCatalog.Tile,
        onProgress: (LazDownloadTask) -> Unit,
    ): File {
        reusableFile(tile)?.let { return it }
        LazDownloadService.enqueue(context, tile.downloadUrl, tile.name)
        val finished = LazDownloadQueue.tasks
            .map { list -> list.firstOrNull { it.url == tile.downloadUrl } }
            .onEach { task -> task?.let(onProgress) }
            // A null task means something else already dismissed the entry, so fall through to
            // the disk check below rather than waiting on a record that no longer exists.
            .first { it == null || it.isFinished }
        if (finished != null && finished.state == LazDownloadState.COMPLETED) {
            LazDownloadQueue.dismiss(tile.downloadUrl)
            finished.filePath?.let { return File(it) }
        }
        if (finished != null && finished.state == LazDownloadState.CANCELLED) {
            LazDownloadQueue.dismiss(tile.downloadUrl)
            throw CancellationException("Download cancelled")
        }
        reusableFile(tile)?.let { return it }
        // The failed entry stays in the queue so the retry row can resume it. Dismissing here meant
        // one bad tile in a large mosaic discarded its own partial transfer along with any way to
        // resume it, forcing the whole area to be resolved and fetched again.
        error(finished?.error ?: "Download of ${tile.name} did not complete")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                    val file = awaitDownloadedFile(tile) { task ->
                        status = task.fraction?.let {
                            "${index + 1}/${selected.size}: ${tile.name} ${(it * 100).toInt()}%"
                        } ?: "${index + 1}/${selected.size}: ${formatBytesCompact(task.downloadedBytes)} downloaded"
                    }
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
                    "USGS ${decodedTiles.size}-tile project"
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
                    Text("Public LiDAR tiles", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "USGS 3DEP point clouds · nationwide coverage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Enter a coordinate in the woods. The app finds every published LiDAR tile whose footprint covers it, downloads the file, and opens the source-classified bare-earth surface.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Area workflow: enter a geographic box to resolve every intersecting official tile, choose the files, then open one georeferenced terrain mosaic.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Jump to a state, then narrow the box to the area you care about — a whole state returns far more tiles than you want to download. Or pan the Map tab to an area and use its search button to bring the visible bounds straight here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                NortheastLidarRegion.entries.forEach { region ->
                    FilterChip(
                        selected = selectedRegion == region,
                        onClick = { applyRegion(region) },
                        label = { Text(region.displayName) },
                        modifier = Modifier.testTag("lidar_region_${region.name}"),
                    )
                }
            }
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
                enabled = !isLookingUp,
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
                enabled = !isLookingUp && downloadJob?.isActive != true,
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
                        enabled = downloadJob?.isActive != true,
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(tile.name, maxLines = 1)
                            Text(
                                tile.project.ifBlank { "Open this one tile" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        if (downloadTasks.any { it.url == tile.downloadUrl && !it.isFinished }) {
                            CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            if (tiles.isNotEmpty()) {
                OutlinedButton(
                    onClick = ::estimateSelectedDownload,
                    enabled = downloadJob?.isActive != true &&
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
                    enabled = downloadJob?.isActive != true &&
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
                        enabled = downloadJob?.isActive != true,
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

            val activeDownloads = downloadTasks.filterNot(LazDownloadTask::isFinished)
            if (activeDownloads.isNotEmpty()) {
                Text(
                    "Background downloads",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "These keep running if you leave this screen or close the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                activeDownloads.forEach { task ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(task.displayName, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    when (task.state) {
                                        LazDownloadState.QUEUED -> "Waiting for the current download to finish"
                                        else -> task.fraction
                                            ?.let { "${(it * 100).toInt()}% of ${formatBytesCompact(task.totalBytes)}" }
                                            ?: "${formatBytesCompact(task.downloadedBytes)} downloaded"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { LazDownloadService.cancel(context, task.url) },
                            ) { Text("Cancel") }
                        }
                        val fraction = task.fraction
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            val failedDownloads = downloadTasks.filter { it.state == LazDownloadState.FAILED }
            if (failedDownloads.isNotEmpty()) {
                Text(
                    "Failed downloads",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Retrying resumes from the bytes already on disk rather than starting the file over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                failedDownloads.forEach { task ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(task.displayName, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                task.error ?: "Download failed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(
                            onClick = {
                                error = null
                                status = "Retrying ${task.displayName}…"
                                LazDownloadService.enqueue(context, task.url, task.displayName)
                            },
                            modifier = Modifier.testTag("retry_failed_download"),
                        ) { Text("Retry") }
                        TextButton(onClick = { LazDownloadQueue.dismiss(task.url) }) { Text("Dismiss") }
                    }
                }
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}


private fun formatBytesCompact(bytes: Long): String {
    val mib = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MiB", mib)
}

/** Six decimals is roughly 0.1 m of longitude, finer than any tile footprint. */
private fun formatDegrees(value: Double): String = String.format(Locale.US, "%.6f", value)
