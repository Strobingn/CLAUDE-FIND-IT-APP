package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.analysis.TerrainCellInspector
import com.example.analysis.TerrainViewshedAnalyzer
import com.example.analysis.TerrainElevationProfiler
import com.example.data.LidarSearchRequest
import com.example.data.NormalizedRasterBounds
import com.example.geospatial.GeoSpatialLibrary
import com.example.geospatial.MeasurementFormat
import com.example.ui.components.CustomFileLoader
import com.example.ui.components.LidarCanvasMode
import com.example.ui.components.LidarAreaPickerMapScreen
import com.example.ui.components.LidarControlPanel
import com.example.ui.components.LidarMapCanvas
import com.example.ui.components.NysLazTilePicker
import com.example.ui.components.OfflineBasemapManager
import com.example.ui.components.TargetLoggerPanel
import com.example.ui.components.TerrainCellInspectionPanel
import com.example.ui.components.TerrainElevationProfilePanel
import com.example.ui.components.TerrainGoogleMapScreen
import com.example.ui.components.SurveyLayerImporter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class AppTab(
    val label: String,
    val title: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    AppTab("Terrain", "Terrain workspace", Icons.Default.Landscape),
    AppTab("Map", "Google Maps + historic overlays", Icons.Default.Layers),
    AppTab("LiDAR", "Select LiDAR area", Icons.Default.CenterFocusStrong),
    AppTab("Gemini", "Gemini field assistant", Icons.Default.AutoAwesome),
    AppTab("Compare", "Layer comparison", Icons.Default.Compare),
    AppTab("Finds", "Field finds", Icons.Default.Flag),
    AppTab("Import", "Terrain library", Icons.Default.UploadFile),
)

/**
 * Phones need more map space than tablets; six labelled navigation items are too tall there.
 * Uses the smallest-width bucket (stable across rotation) rather than the current screen width,
 * which becomes the larger dimension in landscape and would otherwise flip a phone into the
 * "tablet" layout - full-height labelled items - exactly when landscape height is tightest.
 */
internal fun usesCompactBottomNavigation(smallestScreenWidthDp: Int): Boolean = smallestScreenWidthDp < 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: HillshadeViewModel, modifier: Modifier = Modifier) {
    val selectedTab = rememberSaveable { mutableIntStateOf(0) }
    val terrainFocusMode = rememberSaveable { mutableStateOf(false) }
    val terrainSelected = selectedTab.intValue == 0
    val activeTab = tabs[selectedTab.intValue]
    val compactBottomNavigation = usesCompactBottomNavigation(
        LocalConfiguration.current.smallestScreenWidthDp,
    )
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (!terrainSelected && !terrainFocusMode.value) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            activeTab.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
        bottomBar = {
            if (!terrainFocusMode.value) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (compactBottomNavigation) 20.dp else 28.dp,
                        topEnd = if (compactBottomNavigation) 20.dp else 28.dp,
                    ),
                    shadowElevation = 14.dp,
                    tonalElevation = 5.dp,
                ) {
                    NavigationBar(
                        modifier = if (compactBottomNavigation) Modifier.height(64.dp) else Modifier,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab.intValue == index,
                                onClick = {
                                    selectedTab.intValue = index
                                    terrainFocusMode.value = false
                                },
                                icon = {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier
                                            .width(if (compactBottomNavigation) 30.dp else 38.dp)
                                            .height(if (compactBottomNavigation) 30.dp else 38.dp),
                                    )
                                },
                                label = if (compactBottomNavigation) null else {
                                    {
                                        Text(
                                            tab.label,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selectedTab.intValue == index) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    }
                                },
                                alwaysShowLabel = !compactBottomNavigation,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (selectedTab.intValue) {
            0 -> TerrainTab(
                viewModel = viewModel,
                focusMode = terrainFocusMode.value,
                onFocusModeChanged = { terrainFocusMode.value = it },
            )
            1 -> GoogleMapTab(viewModel, padding) { bounds ->
                // The picker lives on the Import tab, so hand the box over and follow it there.
                LidarSearchRequest.request(bounds)
                selectedTab.intValue = tabs.lastIndex
            }
            2 -> LidarAreaTab(padding) { bounds ->
                LidarSearchRequest.request(bounds)
                selectedTab.intValue = tabs.lastIndex
            }
            3 -> GeminiTab(viewModel, padding)
            4 -> CompareTab(viewModel, padding)
            5 -> FindsTab(viewModel, padding)
            else -> ImportTab(viewModel, padding) {
                selectedTab.intValue = 0
                terrainFocusMode.value = false
            }
        }
    }
}

@Composable
private fun TerrainTab(
    viewModel: HillshadeViewModel,
    focusMode: Boolean,
    onFocusModeChanged: (Boolean) -> Unit,
) {
    val site by viewModel.currentSiteIndex.collectAsStateWithLifecycle()
    val bitmap by viewModel.hillshadeBitmap.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val sweepX by viewModel.sweepX.collectAsStateWithLifecycle()
    val sweepY by viewModel.sweepY.collectAsStateWithLifecycle()
    val signals by viewModel.loggedSignals.collectAsStateWithLifecycle()
    val surveyLayers by viewModel.surveyLayers.collectAsStateWithLifecycle()
    val metadata by viewModel.activeGeoMetadata.collectAsStateWithLifecycle()
    val elevationGrid by viewModel.elevationGrid.collectAsStateWithLifecycle()
    val azimuth by viewModel.sunAzimuth.collectAsStateWithLifecycle()
    val altitude by viewModel.sunAltitude.collectAsStateWithLifecycle()
    val vegetation by viewModel.vegetationFilter.collectAsStateWithLifecycle()
    val palette by viewModel.paletteType.collectAsStateWithLifecycle()
    val contrast by viewModel.contrast.collectAsStateWithLifecycle()
    val visualization by viewModel.visualizationMode.collectAsStateWithLifecycle()
    val overlay by viewModel.overlayType.collectAsStateWithLifecycle()
    val overlayOpacity by viewModel.overlayOpacity.collectAsStateWithLifecycle()
    val grid by viewModel.gridSpacing.collectAsStateWithLifecycle()
    val zScale by viewModel.zScale.collectAsStateWithLifecycle()
    val featureScale by viewModel.featureScaleMeters.collectAsStateWithLifecycle()
    val sensitivity by viewModel.analysisSensitivity.collectAsStateWithLifecycle()
    val contourInterval by viewModel.contourIntervalMeters.collectAsStateWithLifecycle()
    val canRefine by viewModel.canRefineTerrain.collectAsStateWithLifecycle()
    val isRefining by viewModel.isRefiningTerrain.collectAsStateWithLifecycle()
    val isDetailed by viewModel.isDetailedTerrain.collectAsStateWithLifecycle()
    val gpsEnabled by viewModel.gpsEnabled.collectAsStateWithLifecycle()
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle()
    val devicePosition by viewModel.deviceGridPosition.collectAsStateWithLifecycle()
    val breadcrumbTracks by viewModel.breadcrumbTracks.collectAsStateWithLifecycle()
    val isBreadcrumbRecording by viewModel.isBreadcrumbRecording.collectAsStateWithLifecycle()
    val heatmapEnabled by viewModel.heatmapEnabled.collectAsStateWithLifecycle()
    val basemapEnabled by viewModel.basemapEnabled.collectAsStateWithLifecycle()
    val basemapOpacity by viewModel.basemapOpacity.collectAsStateWithLifecycle()
    val basemapBitmap by viewModel.basemapBitmap.collectAsStateWithLifecycle()
    val basemapStatus by viewModel.basemapStatus.collectAsStateWithLifecycle()
    val vmViewportReset by viewModel.viewportResetKey.collectAsStateWithLifecycle()
    val vmViewportZoom by viewModel.viewportZoom.collectAsStateWithLifecycle()
    val vmViewportPanX by viewModel.viewportPanX.collectAsStateWithLifecycle()
    val vmViewportPanY by viewModel.viewportPanY.collectAsStateWithLifecycle()
    val vmViewportRestoreToken by viewModel.viewportRestoreToken.collectAsStateWithLifecycle()

    val visibleBounds = remember { mutableStateOf(NormalizedRasterBounds.Full) }
    val zoomLevel = rememberSaveable { mutableStateOf(1f) }
    val inspectedCell = remember { mutableStateOf<com.example.analysis.TerrainCellInspection?>(null) }
    val viewshedState = remember { mutableStateOf<com.example.analysis.TerrainViewshed?>(null) }
    val viewshedComputing = remember { mutableStateOf(false) }
    var isSelectingProfile by rememberSaveable { mutableStateOf(false) }
    var profileStartPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var profileEndPoint by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val elevationProfile = remember(elevationGrid, profileStartPoint, profileEndPoint, vegetation) {
        val start = profileStartPoint
        val end = profileEndPoint
        if (start == null || end == null) null else TerrainElevationProfiler.sample(
            grid = elevationGrid,
            startXPercent = start.first,
            startYPercent = start.second,
            endXPercent = end.first,
            endYPercent = end.second,
            vegetationFilter = vegetation,
        )
    }
    val showControls = rememberSaveable { mutableStateOf(false) }
    val localViewportResetKey = rememberSaveable { mutableIntStateOf(0) }
    val viewportResetKey = vmViewportReset + localViewportResetKey.intValue
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingScreenshot by remember { mutableStateOf(ByteArray(0)) }
    var screenshotMessage by remember { mutableStateOf<String?>(null) }
    var isExportingScreenshot by remember { mutableStateOf(false) }
    val screenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri == null) {
            isExportingScreenshot = false
            screenshotMessage = "Screenshot canceled"
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(pendingScreenshot) }
                ?: error("Could not open the selected destination")
        }.onSuccess {
            screenshotMessage = "Terrain PNG saved"
        }.onFailure {
            screenshotMessage = "Screenshot failed: ${it.localizedMessage}"
        }
        isExportingScreenshot = false
    }
    fun exportScreenshot() {
        if (isExportingScreenshot || bitmap == null || isRendering) return
        isExportingScreenshot = true
        screenshotMessage = "Building terrain PNG…"
        scope.launch {
            runCatching { viewModel.buildProjectExportFiles() }
                .onSuccess { files ->
                    pendingScreenshot = files.terrainPng
                    screenshotLauncher.launch("${files.fileStem}-terrain.png")
                }
                .onFailure {
                    isExportingScreenshot = false
                    screenshotMessage = "Screenshot failed: ${it.localizedMessage}"
                }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onLocationPermissionResult(granted) }
    val breadcrumbPaths = remember(breadcrumbTracks, metadata) {
        breadcrumbTracks.mapNotNull { track ->
            track.points.mapNotNull { point ->
                GeoSpatialLibrary.geographicToGrid(point.latitude, point.longitude, metadata)
            }.takeIf { it.size >= 2 }
        }
    }

    // The Terrain workspace is the primary bare-earth inspection surface. Always enter it with
    // the source hillshade visible; users can still choose another analysis layer while here.
    LaunchedEffect(Unit) {
        if (visualization != 0) viewModel.updateVisualizationMode(0)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val controlsMaxHeight = maxHeight * 0.76f

        LidarMapCanvas(
            bitmap = bitmap,
            isRendering = isRendering,
            sweepX = sweepX,
            sweepY = sweepY,
            loggedSignals = signals,
            onSweepPositionChanged = viewModel::setSweepPosition,
            onStopSweeping = {},
            gridSpacing = grid,
            geoMetadata = metadata,
            currentLat = null,
            currentLon = null,
            mode = LidarCanvasMode.EXPLORE,
            viewportResetKey = viewportResetKey,
            showSurveyCursor = false,
            showCoordinateHud = false,
            initialZoom = vmViewportZoom,
            initialPanX = vmViewportPanX,
            initialPanY = vmViewportPanY,
            viewportRestoreToken = vmViewportRestoreToken,
            onViewportChanged = { bounds, zoom, panX, panY ->
                visibleBounds.value = bounds
                zoomLevel.value = zoom
                viewModel.updateViewport(zoom, panX, panY)
            },
            showHeatmap = heatmapEnabled,
            basemapBitmap = basemapBitmap,
            showBasemap = basemapEnabled,
            basemapOpacity = basemapOpacity,
            basemapStatus = basemapStatus,
            deviceGridPosition = devicePosition,
            breadcrumbPaths = breadcrumbPaths,
            surveyFeatures = surveyLayers.flatMap { it.features },
            inspectionPoint = inspectedCell.value?.let { it.xPercent to it.yPercent },
            profileStartPoint = profileStartPoint,
            profileEndPoint = profileEndPoint,
            onInspectPosition = { xPercent, yPercent ->
                if (isSelectingProfile) {
                    if (profileStartPoint == null || profileEndPoint != null) {
                        profileStartPoint = xPercent to yPercent
                        profileEndPoint = null
                        inspectedCell.value = null
                    } else {
                        profileEndPoint = xPercent to yPercent
                        isSelectingProfile = false
                    }
                } else {
                    viewshedState.value = null
                    inspectedCell.value = TerrainCellInspector.inspect(
                        grid = elevationGrid,
                        metadata = metadata,
                        xPercent = xPercent,
                        yPercent = yPercent,
                        vegetationFilter = vegetation,
                        featureScaleMeters = featureScale,
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 76.dp, bottom = 58.dp)
                .testTag("terrain_workspace"),
        )

        inspectedCell.value?.let { inspection ->
            TerrainCellInspectionPanel(
                inspection = inspection,
                viewshed = viewshedState.value,
                gridWidth = elevationGrid.width,
                gridHeight = elevationGrid.height,
                isComputingViewshed = viewshedComputing.value,
                onComputeViewshed = {
                    if (!viewshedComputing.value) {
                        viewshedComputing.value = true
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                TerrainViewshedAnalyzer.sample(
                                    grid = elevationGrid,
                                    observerXPercent = inspection.xPercent,
                                    observerYPercent = inspection.yPercent,
                                    maxWorkers = 4,
                                )
                            }
                            viewshedState.value = result
                            viewshedComputing.value = false
                        }
                    }
                },
                onClearViewshed = { viewshedState.value = null },
                onDismiss = {
                    inspectedCell.value = null
                    viewshedState.value = null
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 72.dp)
                    .fillMaxWidth(0.94f),
            )
        }

        elevationProfile?.let { profile ->
            TerrainElevationProfilePanel(
                profile = profile,
                onClear = {
                    profileStartPoint = null
                    profileEndPoint = null
                    isSelectingProfile = false
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 72.dp)
                    .fillMaxWidth(0.94f),
            )
        }

        Surface(
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 4.dp,
            shadowElevation = 7.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp),
                ) {
                    Icon(
                        Icons.Default.WbSunny,
                        contentDescription = "Sun direction",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(19.dp).rotate(azimuth),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${compassLabel(azimuth)} ${azimuth.roundToInt()}°",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TerrainQuickAction(
                    "Hillshade",
                    Icons.Default.Landscape,
                    active = visualization == 0,
                ) { viewModel.updateVisualizationMode(0) }
                TerrainQuickAction("Light -", Icons.AutoMirrored.Filled.RotateLeft) { viewModel.rotateSunAzimuth(-45f) }
                TerrainQuickAction("Light +", Icons.AutoMirrored.Filled.RotateRight) { viewModel.rotateSunAzimuth(45f) }
                TerrainQuickAction("Fit", Icons.Default.CenterFocusStrong) { localViewportResetKey.intValue++ }
                TerrainQuickAction(
                    if (isExportingScreenshot) "Saving…" else "Screenshot",
                    Icons.Default.Save,
                    enabled = bitmap != null && !isRendering && !isExportingScreenshot,
                    modifier = Modifier.testTag("terrain_screenshot_button"),
                    onClick = ::exportScreenshot,
                )
                TerrainQuickAction(
                    when {
                        !canRefine -> "No LAZ source"
                        isRefining -> "Loading"
                        isDetailed -> "Refresh"
                        else -> "Detail"
                    },
                    Icons.Default.ZoomInMap,
                    active = isDetailed,
                    enabled = canRefine && !isRefining,
                ) { viewModel.refineTerrain(visibleBounds.value) }
                if (canRefine && isDetailed) {
                    TerrainQuickAction("Whole", Icons.Default.ZoomOutMap) { viewModel.showWholeTerrain() }
                }
                TerrainQuickAction(
                    if (showControls.value) "Close" else "Analyze",
                    Icons.Default.Tune,
                    active = showControls.value,
                ) { showControls.value = !showControls.value }
                TerrainQuickAction(
                    when {
                        isSelectingProfile && profileStartPoint == null -> "Profile: start"
                        isSelectingProfile -> "Profile: end"
                        else -> "Profile"
                    },
                    Icons.AutoMirrored.Filled.ShowChart,
                    active = isSelectingProfile,
                ) {
                    if (isSelectingProfile) {
                        isSelectingProfile = false
                    } else {
                        isSelectingProfile = true
                        profileStartPoint = null
                        profileEndPoint = null
                        inspectedCell.value = null
                    }
                }
                TerrainQuickAction(
                    if (gpsEnabled) "GPS on" else "GPS",
                    if (gpsEnabled && hasLocationPermission) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                    active = gpsEnabled && hasLocationPermission,
                ) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted && !gpsEnabled) {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    viewModel.toggleGpsTracking(!gpsEnabled)
                }
                TerrainQuickAction(
                    if (isBreadcrumbRecording) "Pause trail" else "Trail",
                    Icons.Default.Flag,
                    active = isBreadcrumbRecording,
                ) {
                    if (isBreadcrumbRecording) {
                        viewModel.pauseBreadcrumbRecording()
                    } else {
                        viewModel.startBreadcrumbRecording()
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
                TerrainQuickAction(
                    if (focusMode) "Exit" else "Full",
                    if (focusMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                ) { onFocusModeChanged(!focusMode) }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        ) {
            val widthMeters = (elevationGrid.width - 1).coerceAtLeast(1) * elevationGrid.cellSizeMeters
            val heightMeters = (elevationGrid.height - 1).coerceAtLeast(1) * elevationGrid.cellSizeMeters
            Text(
                String.format(
                    Locale.US,
                    "%d×%d · %s×%s · %s/cell · %s",
                    elevationGrid.width,
                    elevationGrid.height,
                    MeasurementFormat.length(widthMeters),
                    MeasurementFormat.length(heightMeters),
                    MeasurementFormat.resolution(elevationGrid.cellSizeMeters),
                    if (showControls.value) "tools open" else "pinch / drag",
                ),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }

        AnimatedVisibility(
            visible = showControls.value,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(10.dp),
        ) {
            LidarControlPanel(
                selectedSiteIndex = site,
                onSiteSelected = viewModel::selectSite,
                siteLatitude = metadata?.bounds?.let { (it.minLat + it.maxLat) / 2.0 },
                sunAzimuth = azimuth,
                onSunAzimuthChanged = viewModel::updateSunAzimuth,
                sunAltitude = altitude,
                onSunAltitudeChanged = viewModel::updateSunAltitude,
                vegetationFilter = vegetation,
                onVegetationFilterChanged = viewModel::updateVegetationFilter,
                paletteType = palette,
                onPaletteTypeChanged = viewModel::updatePalette,
                contrast = contrast,
                onContrastChanged = viewModel::updateContrast,
                visualizationMode = visualization,
                onVisualizationModeChanged = viewModel::updateVisualizationMode,
                overlayType = overlay,
                onOverlayTypeChanged = viewModel::updateOverlayType,
                overlayOpacity = overlayOpacity,
                onOverlayOpacityChanged = viewModel::updateOverlayOpacity,
                gridSpacing = grid,
                onGridSpacingChanged = viewModel::updateGridSpacing,
                zScale = zScale,
                onZScaleChanged = viewModel::updateZScale,
                featureScaleMeters = featureScale,
                onFeatureScaleChanged = viewModel::updateFeatureScale,
                analysisSensitivity = sensitivity,
                onAnalysisSensitivityChanged = viewModel::updateAnalysisSensitivity,
                contourIntervalMeters = contourInterval,
                onContourIntervalChanged = viewModel::updateContourInterval,
                heatmapEnabled = heatmapEnabled,
                onHeatmapEnabledChanged = viewModel::setHeatmapEnabled,
                basemapEnabled = basemapEnabled,
                onBasemapEnabledChanged = viewModel::setBasemapEnabled,
                basemapOpacity = basemapOpacity,
                onBasemapOpacityChanged = viewModel::setBasemapOpacity,
                basemapStatus = basemapStatus,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = controlsMaxHeight)
                    .verticalScroll(rememberScrollState()),
            )
        }

        screenshotMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 70.dp),
            ) {
                Text(message, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun TerrainQuickAction(
    label: String,
    icon: ImageVector,
    active: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.width(19.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun GoogleMapTab(
    viewModel: HillshadeViewModel,
    padding: PaddingValues,
    onFindLidarTiles: (GeoSpatialLibrary.GeographicBounds) -> Unit,
) {
    val bitmap by viewModel.hillshadeBitmap.collectAsStateWithLifecycle()
    val grid by viewModel.elevationGrid.collectAsStateWithLifecycle()
    val metadata by viewModel.activeGeoMetadata.collectAsStateWithLifecycle()
    val terrainKey by viewModel.activeTerrainKey.collectAsStateWithLifecycle()
    val surveyLayers by viewModel.surveyLayers.collectAsStateWithLifecycle()
    val breadcrumbTracks by viewModel.breadcrumbTracks.collectAsStateWithLifecycle()
    val plannedRoute by viewModel.plannedRoute.collectAsStateWithLifecycle()
    TerrainGoogleMapScreen(
        terrainBitmap = bitmap,
        grid = grid,
        metadata = metadata,
        terrainKey = terrainKey,
        surveyFeatures = surveyLayers.flatMap { it.features },
        breadcrumbTracks = breadcrumbTracks,
        onFindLidarTiles = onFindLidarTiles,
        routeWaypoints = plannedRoute?.waypoints ?: emptyList(),
        routeTotalMeters = plannedRoute?.totalDistanceMeters?.toFloat() ?: 0f,
        onClearRoute = { viewModel.setPlannedRoute(null) },
        modifier = Modifier.fillMaxSize().padding(padding),
    )
}

@Composable
private fun LidarAreaTab(
    padding: PaddingValues,
    onAreaSelected: (GeoSpatialLibrary.GeographicBounds) -> Unit,
) {
    LidarAreaPickerMapScreen(
        onAreaSelected = onAreaSelected,
        modifier = Modifier.fillMaxSize().padding(padding),
    )
}

@Composable
private fun GeminiTab(viewModel: HillshadeViewModel, padding: PaddingValues) {
    AiAnalysisWorkspace(viewModel = viewModel, padding = padding)
}

@Composable
private fun CompareTab(viewModel: HillshadeViewModel, padding: PaddingValues) {
    LayerComparisonWorkspace(viewModel = viewModel, padding = padding)
}

@Composable
private fun FindsTab(viewModel: HillshadeViewModel, padding: PaddingValues) {
    val signals by viewModel.loggedSignals.collectAsStateWithLifecycle()
    val sweepX by viewModel.sweepX.collectAsStateWithLifecycle()
    val sweepY by viewModel.sweepY.collectAsStateWithLifecycle()
    val breadcrumbTracks by viewModel.breadcrumbTracks.collectAsStateWithLifecycle()
    val isBreadcrumbRecording by viewModel.isBreadcrumbRecording.collectAsStateWithLifecycle()
    val gpsEnabled by viewModel.gpsEnabled.collectAsStateWithLifecycle()
    val deviceLatitude by viewModel.deviceLatitude.collectAsStateWithLifecycle()
    val deviceLongitude by viewModel.deviceLongitude.collectAsStateWithLifecycle()
    val deviceAccuracyMeters by viewModel.deviceLocationAccuracyMeters.collectAsStateWithLifecycle()
    val compassHeadingDegrees by viewModel.compassHeadingDegrees.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onLocationPermissionResult(granted) }
    TargetLoggerPanel(
        loggedSignals = signals,
        currentSweepX = sweepX,
        currentSweepY = sweepY,
        breadcrumbTracks = breadcrumbTracks,
        isBreadcrumbRecording = isBreadcrumbRecording,
        gpsEnabled = gpsEnabled,
        deviceLatitude = deviceLatitude,
        deviceLongitude = deviceLongitude,
        deviceAccuracyMeters = deviceAccuracyMeters,
        compassHeadingDegrees = compassHeadingDegrees,
        onEnableGps = {
            viewModel.toggleGpsTracking(true)
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onSetCompassNavigationActive = viewModel::setCompassNavigationActive,
        onStartBreadcrumb = {
            viewModel.startBreadcrumbRecording()
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onPauseBreadcrumb = viewModel::pauseBreadcrumbRecording,
        onClearBreadcrumbs = viewModel::clearBreadcrumbTracks,
        onLogSignal = viewModel::logCurrentSignal,
        onDeleteSignal = viewModel::deleteLoggedSignal,
        onUpdateSignal = viewModel::updateLoggedSignal,
        onClearAll = viewModel::clearLoggedSignals,
        onBuildProjectExport = viewModel::buildProjectExportFiles,
        onRoutePlanned = viewModel::setPlannedRoute,
        modifier = Modifier.fillMaxSize().padding(padding),
    )
}

@Composable
private fun ImportTab(
    viewModel: HillshadeViewModel,
    padding: PaddingValues,
    onImported: () -> Unit,
) {
    val surveyLayers by viewModel.surveyLayers.collectAsStateWithLifecycle()
    val metadata by viewModel.activeGeoMetadata.collectAsStateWithLifecycle()
    val offlineRegions by viewModel.offlineBasemapRegions.collectAsStateWithLifecycle()
    val offlinePlan by viewModel.offlineBasemapPlan.collectAsStateWithLifecycle()
    val offlineProgress by viewModel.offlineBasemapProgress.collectAsStateWithLifecycle()
    val offlineMessage by viewModel.offlineBasemapMessage.collectAsStateWithLifecycle()
    val offlineDownloading by viewModel.offlineBasemapDownloading.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OfflineBasemapManager(
            suggestedName = metadata.siteName,
            regions = offlineRegions,
            plan = offlinePlan,
            progress = offlineProgress,
            isDownloading = offlineDownloading,
            message = offlineMessage,
            onEstimate = viewModel::estimateOfflineBasemapRegion,
            onDownload = viewModel::downloadOfflineBasemapRegion,
            onCancel = viewModel::cancelOfflineBasemapDownload,
            onOpen = {
                viewModel.openOfflineBasemapRegion(it)
                onImported()
            },
            onRetry = viewModel::retryOfflineBasemapRegion,
            onDelete = viewModel::deleteOfflineBasemapRegion,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
        )

        SurveyLayerImporter(
            layers = surveyLayers,
            onImported = {
                viewModel.importSurveyLayer(it)
                onImported()
            },
            onDelete = viewModel::deleteSurveyLayer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
        )

        NysLazTilePicker(
            onCustomTerrainLoaded = { result, source ->
                viewModel.setCustomTerrain(result, source)
                onImported()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp),
        )

        CustomFileLoader(
            onCustomTerrainLoaded = { result, source ->
                viewModel.setCustomTerrain(result, source)
                onImported()
            },
        )
    }
}

private fun compassLabel(azimuth: Float): String {
    val normalized = ((azimuth % 360f) + 360f) % 360f
    return when {
        normalized < 22.5f || normalized >= 337.5f -> "N"
        normalized < 67.5f -> "NE"
        normalized < 112.5f -> "E"
        normalized < 157.5f -> "SE"
        normalized < 202.5f -> "S"
        normalized < 247.5f -> "SW"
        normalized < 292.5f -> "W"
        else -> "NW"
    }
}
