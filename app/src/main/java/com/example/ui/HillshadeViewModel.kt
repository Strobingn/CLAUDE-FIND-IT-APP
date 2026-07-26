package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DemGenerator
import com.example.data.DetectionSource
import com.example.data.ElevationGrid
import com.example.data.GroundSurfaceMode
import com.example.data.basemap.OfflineBasemapRegion
import com.example.data.basemap.OfflineBasemapStatus
import com.example.data.LazDatasetStore
import com.example.data.LazTerrainDiskCache
import com.example.data.LidarImportOptions
import com.example.data.MetalType
import com.example.data.NormalizedRasterBounds
import com.example.data.TerrainGpuSceneBuilder
import com.example.data.TerrainImportSource
import com.example.data.TerrainPerformanceSession
import com.example.data.TargetSignal
import com.example.data.export.ProjectExportFiles
import com.example.data.export.ProjectExportRenderer
import com.example.data.export.ProjectExportSnapshot
import com.example.data.targetsForTerrain
import com.example.data.survey.SurveyLayer
import com.example.data.local.AnalyzedDatasetEntity
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsRepository
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.geospatial.GeoSpatialLibrary
import com.example.geospatial.GeoSpatialLibrary.GeoSpatialMetadata
import com.example.geospatial.LocationTracker
import com.example.geospatial.BasemapTileRepository
import com.example.geospatial.BasemapDownloadProgress
import com.example.geospatial.BasemapPlan
import com.example.geospatial.SlippyTileMath
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class HillshadeViewModel(application: Application) : AndroidViewModel(application) {
    private val signalDao = AppDatabase.get(application).targetSignalDao()
    private val settingsRepo = SettingsRepository(AppDatabase.get(application).settingDao())
    private val analyzedDatasetDao = AppDatabase.get(application).analyzedDatasetDao()
    private val surveyLayerDao = AppDatabase.get(application).surveyLayerDao()
    private val offlineBasemapRegionDao = AppDatabase.get(application).offlineBasemapRegionDao()

    // Guard flag to prevent saveSettings() from overwriting DB values with defaults before loading completes
    private var isSettingsLoaded = false
    private var restoreImportedTerrainOnStart = false

    private val _currentSiteIndex = MutableStateFlow(0)
    val currentSiteIndex: StateFlow<Int> = _currentSiteIndex.asStateFlow()
    private val _activeTerrainKey = MutableStateFlow("builtin:0")
    val activeTerrainKey: StateFlow<String> = _activeTerrainKey.asStateFlow()
    // Keep ViewModel construction cheap so Compose can produce its first frame immediately.
    // The real demo terrain is generated on Dispatchers.Default from loadSettings().
    private val _elevationGrid = MutableStateFlow(
        ElevationGrid(
            width = 2,
            height = 2,
            bareEarth = FloatArray(4),
            canopySpikes = FloatArray(4),
        ),
    )
    val elevationGrid: StateFlow<ElevationGrid> = _elevationGrid.asStateFlow()
    private var customGrid: ElevationGrid? = null

    private val _sunAzimuth = MutableStateFlow(315f)
    val sunAzimuth = _sunAzimuth.asStateFlow()
    private val _sunAltitude = MutableStateFlow(35f)
    val sunAltitude = _sunAltitude.asStateFlow()
    private val _vegetationFilter = MutableStateFlow(0.8f)
    val vegetationFilter = _vegetationFilter.asStateFlow()
    private val _paletteType = MutableStateFlow(1)
    val paletteType = _paletteType.asStateFlow()
    private val _contrast = MutableStateFlow(1.5f)
    val contrast = _contrast.asStateFlow()
    private val _visualizationMode = MutableStateFlow(0)
    val visualizationMode = _visualizationMode.asStateFlow()
    private val _overlayType = MutableStateFlow(0)
    val overlayType = _overlayType.asStateFlow()
    private val _overlayOpacity = MutableStateFlow(0.4f)
    val overlayOpacity = _overlayOpacity.asStateFlow()
    private val _gridSpacing = MutableStateFlow(0f)
    val gridSpacing = _gridSpacing.asStateFlow()
    private val _zScale = MutableStateFlow(1f)
    val zScale = _zScale.asStateFlow()
    private val _featureScaleMeters = MutableStateFlow(6f)
    val featureScaleMeters = _featureScaleMeters.asStateFlow()
    private val _analysisSensitivity = MutableStateFlow(1.2f)
    val analysisSensitivity = _analysisSensitivity.asStateFlow()
    private val _contourIntervalMeters = MutableStateFlow(0f)
    val contourIntervalMeters = _contourIntervalMeters.asStateFlow()
    private val _activeTerrainSummary = MutableStateFlow("Built-in demonstration terrain")
    val activeTerrainSummary = _activeTerrainSummary.asStateFlow()
    private val _canRefineTerrain = MutableStateFlow(false)
    val canRefineTerrain = _canRefineTerrain.asStateFlow()
    private val _isRefiningTerrain = MutableStateFlow(false)
    val isRefiningTerrain = _isRefiningTerrain.asStateFlow()
    private val _isDetailedTerrain = MutableStateFlow(false)
    val isDetailedTerrain = _isDetailedTerrain.asStateFlow()
    private val _terrainDetailMessage = MutableStateFlow<String?>(null)
    val terrainDetailMessage = _terrainDetailMessage.asStateFlow()
    private var terrainSource: TerrainImportSource? = null
    private var overviewTerrain: DemGenerator.TerrainLoadResult? = null
    private var currentSourceBounds = NormalizedRasterBounds.Full

    private val _hillshadeBitmap = MutableStateFlow<Bitmap?>(null)
    val hillshadeBitmap = _hillshadeBitmap.asStateFlow()
    // Starts true because the lightweight placeholder is replaced and rendered during init.
    private val _isRendering = MutableStateFlow(true)
    val isRendering = _isRendering.asStateFlow()
    private val renderMutex = Mutex()
    private var renderJob: Job? = null
    private var siteGenerationJob: Job? = null
    private var renderGeneration = 0L

    // Viewport persistence
    private val _viewportZoom = MutableStateFlow(1f)
    val viewportZoom: StateFlow<Float> = _viewportZoom.asStateFlow()
    private val _viewportPanX = MutableStateFlow(0f)
    val viewportPanX: StateFlow<Float> = _viewportPanX.asStateFlow()
    private val _viewportPanY = MutableStateFlow(0f)
    val viewportPanY: StateFlow<Float> = _viewportPanY.asStateFlow()
    // Bumped exactly once, right after loadSettings() finishes reading the persisted viewport,
    // so the terrain canvas can seed itself from the restored zoom/pan a single time rather than
    // fighting with live user interaction on every subsequent update.
    private val _viewportRestoreToken = MutableStateFlow(0)
    val viewportRestoreToken: StateFlow<Int> = _viewportRestoreToken.asStateFlow()

    private var saveSettingsJob: Job? = null

    private val _sweepX = MutableStateFlow(50f)
    val sweepX = _sweepX.asStateFlow()
    private val _sweepY = MutableStateFlow(50f)
    val sweepY = _sweepY.asStateFlow()

    private var allLoggedSignals: List<TargetSignal> = emptyList()
    private val _loggedSignals = MutableStateFlow<List<TargetSignal>>(emptyList())
    val loggedSignals = _loggedSignals.asStateFlow()

    private val _analyzedDatasets = MutableStateFlow<List<AnalyzedDatasetEntity>>(emptyList())
    val analyzedDatasets: StateFlow<List<AnalyzedDatasetEntity>> = _analyzedDatasets.asStateFlow()
    private val _surveyLayers = MutableStateFlow<List<SurveyLayer>>(emptyList())
    val surveyLayers: StateFlow<List<SurveyLayer>> = _surveyLayers.asStateFlow()
    private var surveyLayerJob: Job? = null

    private val _activeGeoMetadata = MutableStateFlow(GeoSpatialLibrary.SITES_METADATA.first())
    val activeGeoMetadata: StateFlow<GeoSpatialMetadata> = _activeGeoMetadata.asStateFlow()
    private val _currentLat = MutableStateFlow<Double?>(null)
    val currentLat: StateFlow<Double?> = _currentLat.asStateFlow()
    private val _currentLon = MutableStateFlow<Double?>(null)
    val currentLon: StateFlow<Double?> = _currentLon.asStateFlow()

    private val locationTracker = LocationTracker(application)
    private val _gpsEnabled = MutableStateFlow(false)
    val gpsEnabled: StateFlow<Boolean> = _gpsEnabled.asStateFlow()
    private val _hasLocationPermission = MutableStateFlow(locationTracker.hasLocationPermission())
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()
    private val _deviceGridPosition = MutableStateFlow<Pair<Float, Float>?>(null)
    val deviceGridPosition: StateFlow<Pair<Float, Float>?> = _deviceGridPosition.asStateFlow()
    private val _deviceLocationAccuracyMeters = MutableStateFlow<Float?>(null)
    val deviceLocationAccuracyMeters: StateFlow<Float?> = _deviceLocationAccuracyMeters.asStateFlow()
    private var locationJob: Job? = null

    // Bumped after successful refine / show-whole so the canvas forces zoom=1 + pan=0
    // against the new high-res (or full) bitmap.
    private val _viewportResetKey = MutableStateFlow(0)
    val viewportResetKey: StateFlow<Int> = _viewportResetKey.asStateFlow()

    // Zoom threshold for auto-rendering
    private val AUTO_RENDER_ZOOM_THRESHOLD = 2.5f

    init {
        observeSurveyLayers(_activeTerrainKey.value)
        observeOfflineBasemapRegions(_activeTerrainKey.value)
        // loadSettings must finish before the first scheduleRender — scheduleRender saves the
        // *current* StateFlow values back to disk, and if that runs while loadSettings' reads are
        // still in flight, it stomps the just-persisted settings with hardcoded defaults on every
        // single app start. Awaiting it here (rather than firing both as separate launches) is
        // what actually fixes that.
        viewModelScope.launch {
            loadSettings()
            updateCoordinates()
            scheduleRender(immediate = true)
            if (restoreImportedTerrainOnStart) restoreLastCachedTerrain()
        }
        viewModelScope.launch {
            signalDao.observeAll().collect { stored ->
                allLoggedSignals = stored.map { it.toDomain() }
                refreshVisibleSignals()
            }
        }
        viewModelScope.launch {
            analyzedDatasetDao.observeAll().collect { stored ->
                _analyzedDatasets.value = stored
            }
        }
    }

    /** Persists a snapshot of this dataset's targets so it can later be cross-compared with another. */
    fun saveDatasetSnapshot(entity: AnalyzedDatasetEntity) {
        viewModelScope.launch { analyzedDatasetDao.upsert(entity) }
    }

    /** Called by the UI after a runtime permission dialog resolves. */
    fun onLocationPermissionResult(granted: Boolean) {
        _hasLocationPermission.value = granted || locationTracker.hasLocationPermission()
        if (_gpsEnabled.value && _hasLocationPermission.value) startLocationUpdates()
    }

    fun toggleGpsTracking(enabled: Boolean) {
        _gpsEnabled.value = enabled
        viewModelScope.launch { settingsRepo.saveBoolean(SettingsRepository.Keys.GPS_ENABLED, enabled) }
        if (enabled && _hasLocationPermission.value) {
            startLocationUpdates()
        } else if (!enabled) {
            stopLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            locationTracker.locationUpdates()
                .catch { /* provider unavailable or a platform SecurityException — stop tracking */ }
                .collect { fix ->
                    _deviceLocationAccuracyMeters.value = fix.accuracyMeters
                    _deviceGridPosition.value =
                        GeoSpatialLibrary.geographicToGrid(fix.latitude, fix.longitude, _activeGeoMetadata.value)
                }
        }
    }

    private fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
        _deviceGridPosition.value = null
        _deviceLocationAccuracyMeters.value = null
    }

    private val _heatmapEnabled = MutableStateFlow(false)
    val heatmapEnabled: StateFlow<Boolean> = _heatmapEnabled.asStateFlow()

    fun setHeatmapEnabled(enabled: Boolean) {
        _heatmapEnabled.value = enabled
        viewModelScope.launch { settingsRepo.saveBoolean(SettingsRepository.Keys.HEATMAP_ENABLED, enabled) }
    }

    private val basemapTileRepository = BasemapTileRepository(application)
    private val _basemapEnabled = MutableStateFlow(false)
    val basemapEnabled: StateFlow<Boolean> = _basemapEnabled.asStateFlow()
    private val _basemapOpacity = MutableStateFlow(0.6f)
    val basemapOpacity: StateFlow<Float> = _basemapOpacity.asStateFlow()
    private val _basemapBitmap = MutableStateFlow<Bitmap?>(null)
    val basemapBitmap: StateFlow<Bitmap?> = _basemapBitmap.asStateFlow()
    private val _basemapStatus = MutableStateFlow<String?>(null)
    val basemapStatus: StateFlow<String?> = _basemapStatus.asStateFlow()
    private var basemapJob: Job? = null
    private val _offlineBasemapRegions = MutableStateFlow<List<OfflineBasemapRegion>>(emptyList())
    val offlineBasemapRegions: StateFlow<List<OfflineBasemapRegion>> = _offlineBasemapRegions.asStateFlow()
    private val _offlineBasemapPlan = MutableStateFlow<BasemapPlan?>(null)
    val offlineBasemapPlan: StateFlow<BasemapPlan?> = _offlineBasemapPlan.asStateFlow()
    private val _offlineBasemapProgress = MutableStateFlow<BasemapDownloadProgress?>(null)
    val offlineBasemapProgress: StateFlow<BasemapDownloadProgress?> = _offlineBasemapProgress.asStateFlow()
    private val _offlineBasemapMessage = MutableStateFlow<String?>(null)
    val offlineBasemapMessage: StateFlow<String?> = _offlineBasemapMessage.asStateFlow()
    private val _offlineBasemapDownloading = MutableStateFlow(false)
    val offlineBasemapDownloading: StateFlow<Boolean> = _offlineBasemapDownloading.asStateFlow()
    private var offlineBasemapRegionJob: Job? = null
    private var offlineBasemapDownloadJob: Job? = null
    private var activeOfflineDownloadId: String? = null

    fun setBasemapEnabled(enabled: Boolean) {
        _basemapEnabled.value = enabled
        viewModelScope.launch { settingsRepo.saveBoolean(SettingsRepository.Keys.BASEMAP_ENABLED, enabled) }
        if (enabled) {
            refreshBasemapTiles()
        } else {
            basemapJob?.cancel()
            _basemapBitmap.value = null
            _basemapStatus.value = null
        }
    }

    fun setBasemapOpacity(value: Float) {
        _basemapOpacity.value = value.coerceIn(0.1f, 1f)
        viewModelScope.launch { settingsRepo.saveFloat(SettingsRepository.Keys.BASEMAP_OPACITY, _basemapOpacity.value) }
    }

    private fun refreshBasemapTiles() {
        basemapJob?.cancel()
        basemapJob = null
        val bounds = _activeGeoMetadata.value.bounds
        if (bounds == null) {
            _basemapBitmap.value = null
            _basemapStatus.value = "This terrain has no geographic coordinates — basemap unavailable."
            return
        }
        basemapJob = viewModelScope.launch {
            val offline = _offlineBasemapRegions.value.firstOrNull {
                it.status == OfflineBasemapStatus.READY
            }
            _basemapStatus.value = if (offline != null) {
                "Opening saved offline basemap…"
            } else {
                "Loading basemap tiles…"
            }
            val result = runCatching {
                if (offline != null) {
                    basemapTileRepository.loadBasemap(
                        bounds = offline.bounds,
                        fixedZoom = offline.zoom,
                        maxTiles = offline.tileCount,
                        allowNetwork = false,
                    )
                } else {
                    basemapTileRepository.loadBasemap(bounds)
                }
            }.getOrNull()
            _basemapBitmap.value = result?.bitmap
            _basemapStatus.value = when {
                result?.bitmap != null && offline != null &&
                    result.loadedTiles == result.expectedTiles -> "Saved offline basemap ready."
                result?.bitmap != null -> null
                result?.blockedByServer == true ->
                    "The USGS Topo service rejected these requests — basemap unavailable here."
                else -> "Couldn't load basemap tiles — showing terrain view only."
            }
        }
    }

    private fun scheduleRender(immediate: Boolean = false, currentZoom: Float? = null) {
        // Skip render if zoom is below threshold and not immediate
        if (currentZoom != null && currentZoom < AUTO_RENDER_ZOOM_THRESHOLD && !immediate) {
            return
        }

        val generation = ++renderGeneration
        saveSettings()
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            if (!immediate) delay(80)
            _isRendering.value = true
            try {
                renderMutex.withLock {
                    val grid = _elevationGrid.value
                    val bitmap = withContext(Dispatchers.Default) {
                        grid.renderHillshade(
                            sunAzimuth = _sunAzimuth.value,
                            sunAltitude = _sunAltitude.value,
                            vegetationFilter = _vegetationFilter.value,
                            palette = _paletteType.value,
                            contrast = _contrast.value,
                            visualizationMode = _visualizationMode.value,
                            overlayType = _overlayType.value,
                            overlayOpacity = _overlayOpacity.value,
                            zScale = _zScale.value,
                            featureScaleMeters = _featureScaleMeters.value,
                            analysisSensitivity = _analysisSensitivity.value,
                            contourIntervalMeters = _contourIntervalMeters.value,
                        )
                    }
                    if (generation == renderGeneration) _hillshadeBitmap.value = bitmap
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                if (generation == renderGeneration) _isRendering.value = false
            }
        }
    }

    fun selectSite(index: Int) {
        if (index !in 0..3 || index == 3 && customGrid == null) return
        _currentSiteIndex.value = index
        if (index in 0..2) {
            setActiveTerrainKey("builtin:$index")
        }
        siteGenerationJob?.cancel()
        if (index in 0..2) {
            siteGenerationJob = viewModelScope.launch {
                _isRendering.value = true
                val generatedGrid = withContext(Dispatchers.Default) {
                    DemGenerator.generateSite(index)
                }
                // Ignore an obsolete result if the user selected another site while this
                // terrain was being generated.
                if (_currentSiteIndex.value != index) return@launch
                _elevationGrid.value = generatedGrid
                _activeGeoMetadata.value = GeoSpatialLibrary.SITES_METADATA[index]
                _activeTerrainSummary.value = "Built-in simulated terrain"
                updateCoordinates()
                scheduleRender(immediate = true)
                if (_basemapEnabled.value) refreshBasemapTiles()
            }
        } else {
            _elevationGrid.value = requireNotNull(customGrid)
            updateCoordinates()
            scheduleRender(immediate = true)
            if (_basemapEnabled.value) refreshBasemapTiles()
        }
    }

    fun setCustomTerrain(
        result: DemGenerator.TerrainLoadResult,
        source: TerrainImportSource? = null,
    ) {
        terrainSource = source
        setActiveTerrainKey(
            source?.let { "lidar:${it.uri}" }
                ?: "custom:${com.example.analysis.TerrainIntelligenceEngine.terrainSignature(result.grid)}",
        )
        overviewTerrain = result.takeIf { source != null }
        currentSourceBounds = NormalizedRasterBounds.Full
        _canRefineTerrain.value = source != null
        _isDetailedTerrain.value = false
        _terrainDetailMessage.value = null
        applyCustomTerrain(result, resetViewport = true)
    }

    private fun applyCustomTerrain(result: DemGenerator.TerrainLoadResult, resetViewport: Boolean = false) {
        siteGenerationJob?.cancel()
        val grid = result.grid
        customGrid = result.grid
        _elevationGrid.value = result.grid
        _currentSiteIndex.value = 3
        _activeGeoMetadata.value = result.geoMetadata ?: GeoSpatialLibrary.localGrid(
            name = "Custom imported layer",
            columns = grid.width,
            rows = grid.height,
            resolutionMeters = grid.cellSizeMeters.toDouble(),
        )
        _activeTerrainSummary.value = result.summary
        updateCoordinates()
        scheduleRender(immediate = true)
        if (_basemapEnabled.value) refreshBasemapTiles()
        if (resetViewport) {
            _viewportResetKey.value = _viewportResetKey.value + 1
        }
    }

    /**
     * Called by UI during pinch-to-zoom when current zoom scale reaches or exceeds 2.5x.
     */
    fun onZoomThresholdReached(viewport: NormalizedRasterBounds, scale: Float) {
        if (scale >= 2.5f && _canRefineTerrain.value && !_isDetailedTerrain.value && !_isRefiningTerrain.value) {
            refineTerrain(viewport)
        }
    }

    fun refineTerrain(viewport: NormalizedRasterBounds) {
        val source = terrainSource ?: return
        if (_isRefiningTerrain.value) return
        // Refine works at any zoom level, including the full extent - it always re-reads the
        // requested viewport from the original point cloud, so there's no reason to force the
        // user to pinch in first even when that viewport happens to be the whole dataset.
        val absoluteBounds = viewport.sanitized().inside(currentSourceBounds)
        _isRefiningTerrain.value = true
        _terrainDetailMessage.value = "Reading original returns for this viewport…"
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                getApplication<Application>().contentResolver.openInputStream(Uri.parse(source.uri))?.buffered()?.use { input ->
                    DemGenerator.parseFromStreamDetailed(
                        fileName = source.displayName,
                        inputStream = input,
                        options = source.options.copy(
                            rasterResolution = 1_024,
                            focusBounds = absoluteBounds,
                        ),
                    )
                }
            }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                _isRefiningTerrain.value = false
                if (result == null) {
                    _terrainDetailMessage.value = "Could not load detail from the original LAZ/LAS document."
                } else {
                    currentSourceBounds = absoluteBounds
                    _isDetailedTerrain.value = true
                    _terrainDetailMessage.value = "Detailed viewport loaded from the original point cloud."
                    applyCustomTerrain(result, resetViewport = true)
                }
            }
        }
    }

    fun showWholeTerrain() {
        val overview = overviewTerrain ?: return
        currentSourceBounds = NormalizedRasterBounds.Full
        _isDetailedTerrain.value = false
        _terrainDetailMessage.value = "Showing the complete point-cloud footprint."
        applyCustomTerrain(overview, resetViewport = true)
    }

    fun setCustomGrid(grid: ElevationGrid) {
        setCustomTerrain(
            DemGenerator.TerrainLoadResult(
                grid = grid,
                summary = "Custom ${grid.width}×${grid.height} elevation grid",
                isBareEarth = true,
            ),
        )
    }

    fun updateSunAzimuth(value: Float) {
        _sunAzimuth.value = value.coerceIn(0f, 360f)
        scheduleRender()
    }
    fun rotateSunAzimuth(deltaDegrees: Float) {
        val value = _sunAzimuth.value + deltaDegrees
        _sunAzimuth.value = ((value % 360f) + 360f) % 360f
        scheduleRender()
    }
    fun updateSunAltitude(value: Float) { _sunAltitude.value = value.coerceIn(5f, 85f); scheduleRender() }
    fun updateVegetationFilter(value: Float) { _vegetationFilter.value = value.coerceIn(0f, 1f); scheduleRender() }
    fun updatePalette(value: Int) { _paletteType.value = value.coerceIn(0, 2); scheduleRender() }
    fun updateContrast(value: Float) { _contrast.value = value.coerceIn(1f, 2.5f); scheduleRender() }
    fun updateVisualizationMode(value: Int) { _visualizationMode.value = value.coerceIn(0, 8); scheduleRender() }
    fun updateOverlayType(value: Int) { _overlayType.value = value.coerceIn(0, 2); scheduleRender() }
    fun updateOverlayOpacity(value: Float) { _overlayOpacity.value = value.coerceIn(0.1f, 0.9f); scheduleRender() }
    fun updateGridSpacing(value: Float) { _gridSpacing.value = value.coerceIn(0f, 20f) }
    fun updateZScale(value: Float) { _zScale.value = value.coerceIn(0.5f, 4f); scheduleRender() }
    fun updateFeatureScale(value: Float) {
        _featureScaleMeters.value = value.coerceIn(1f, 40f)
        scheduleRender()
    }
    fun updateAnalysisSensitivity(value: Float) {
        _analysisSensitivity.value = value.coerceIn(0.4f, 2.5f)
        scheduleRender()
    }
    fun updateContourInterval(value: Float) {
        _contourIntervalMeters.value = value.coerceIn(0f, 5f)
        scheduleRender()
    }

    fun setSweepPosition(x: Float, y: Float) {
        _sweepX.value = x.coerceIn(0f, 100f)
        _sweepY.value = y.coerceIn(0f, 100f)
        updateCoordinates()
    }

    // Update viewport zoom and pan. Persists (debounced) but intentionally does not trigger a
    // hillshade re-render - re-rendering on every pinch/pan tick is what caused zoom jank before.
    fun updateViewport(zoom: Float, panX: Float, panY: Float) {
        _viewportZoom.value = zoom
        _viewportPanX.value = panX
        _viewportPanY.value = panY
        saveSettings()
    }

    private fun updateCoordinates() {
        val coordinate = GeoSpatialLibrary.gridToGeographic(
            _sweepX.value,
            _sweepY.value,
            _activeGeoMetadata.value,
        )
        _currentLat.value = coordinate?.first
        _currentLon.value = coordinate?.second
    }

    fun logCurrentSignal() {
        val signal = TargetSignal(
            gridX = _sweepX.value,
            gridY = _sweepY.value,
            metalType = MetalType.MANUAL_MARKER,
            signalStrength = 0f,
            depthCm = null,
            latitude = _currentLat.value,
            longitude = _currentLon.value,
            source = DetectionSource.MANUAL,
            terrainKey = _activeTerrainKey.value,
        )
        viewModelScope.launch { signalDao.upsert(signal.toEntity()) }
    }

    fun updateLoggedSignal(signal: TargetSignal) {
        viewModelScope.launch { signalDao.upsert(signal.toEntity()) }
    }

    fun deleteLoggedSignal(signal: TargetSignal) {
        viewModelScope.launch { signalDao.deleteById(signal.id) }
    }

    fun clearLoggedSignals() {
        val terrainKey = _activeTerrainKey.value
        viewModelScope.launch { signalDao.deleteByTerrainKey(terrainKey) }
    }

    private fun setActiveTerrainKey(terrainKey: String) {
        _activeTerrainKey.value = terrainKey
        refreshVisibleSignals()
        observeSurveyLayers(terrainKey)
        observeOfflineBasemapRegions(terrainKey)
        _offlineBasemapPlan.value = null
        _offlineBasemapMessage.value = null
    }

    private fun observeSurveyLayers(terrainKey: String) {
        surveyLayerJob?.cancel()
        surveyLayerJob = viewModelScope.launch {
            surveyLayerDao.observeByTerrainKey(terrainKey).collect { stored ->
                _surveyLayers.value = stored.mapNotNull { it.toDomain() }
            }
        }
    }

    fun importSurveyLayer(layer: SurveyLayer) {
        val terrainKey = _activeTerrainKey.value
        viewModelScope.launch {
            surveyLayerDao.upsert(layer.toEntity(terrainKey))
        }
    }

    fun deleteSurveyLayer(layer: SurveyLayer) {
        viewModelScope.launch { surveyLayerDao.deleteById(layer.id) }
    }

    suspend fun buildProjectExportFiles(): ProjectExportFiles = renderMutex.withLock {
        withContext(Dispatchers.Default) {
            // A refined viewport replaces the active grid, but project export must still cover the
            // complete source footprint. overviewTerrain retains that full raster for LAZ imports.
            val fullResult = overviewTerrain
            val exportGrid = fullResult?.grid ?: _elevationGrid.value
            val exportMetadata = (fullResult?.geoMetadata ?: _activeGeoMetadata.value).copy(
                columns = exportGrid.width,
                rows = exportGrid.height,
                resolutionMeters = exportGrid.cellSizeMeters.toDouble(),
            )
            val bitmap = exportGrid.renderHillshade(
                sunAzimuth = _sunAzimuth.value,
                sunAltitude = _sunAltitude.value,
                vegetationFilter = _vegetationFilter.value,
                palette = _paletteType.value,
                contrast = _contrast.value,
                visualizationMode = _visualizationMode.value,
                overlayType = _overlayType.value,
                overlayOpacity = _overlayOpacity.value,
                zScale = _zScale.value,
                featureScaleMeters = _featureScaleMeters.value,
                analysisSensitivity = _analysisSensitivity.value,
                contourIntervalMeters = _contourIntervalMeters.value,
            )
            ProjectExportRenderer.build(
                ProjectExportSnapshot(
                    projectName = exportMetadata.siteName,
                    terrainKey = _activeTerrainKey.value,
                    summary = fullResult?.summary ?: _activeTerrainSummary.value,
                    metadata = exportMetadata,
                    terrainBitmap = bitmap,
                    visualizationLabel = visualizationLabel(_visualizationMode.value),
                    targets = _loggedSignals.value,
                    surveyLayers = _surveyLayers.value,
                ),
            )
        }
    }

    private fun observeOfflineBasemapRegions(terrainKey: String) {
        offlineBasemapRegionJob?.cancel()
        offlineBasemapRegionJob = viewModelScope.launch {
            offlineBasemapRegionDao.observeByTerrainKey(terrainKey).collect { stored ->
                val regions = stored.map { it.toDomain() }
                _offlineBasemapRegions.value = regions
                regions.filter {
                    it.status == OfflineBasemapStatus.DOWNLOADING && it.id != activeOfflineDownloadId
                }.forEach { interrupted ->
                    offlineBasemapRegionDao.upsert(
                        interrupted.copy(
                            status = OfflineBasemapStatus.CANCELED,
                            lastError = "Download was interrupted. Retry keeps completed tiles.",
                            updatedAtMillis = System.currentTimeMillis(),
                        ).toEntity(),
                    )
                }
                if (_basemapEnabled.value &&
                    stored.any { it.status == OfflineBasemapStatus.READY.name } &&
                    _basemapBitmap.value == null
                ) {
                    refreshBasemapTiles()
                }
            }
        }
    }

    fun estimateOfflineBasemapRegion() {
        val bounds = _activeGeoMetadata.value.bounds
        if (bounds == null) {
            _offlineBasemapPlan.value = null
            _offlineBasemapMessage.value =
                "This terrain has no real geographic bounds, so an offline map cannot be placed safely."
            return
        }
        val plan = basemapTileRepository.planOfflineRegion(bounds)
        _offlineBasemapPlan.value = plan
        _offlineBasemapMessage.value = null
    }

    fun downloadOfflineBasemapRegion(displayName: String? = null) {
        if (_offlineBasemapDownloading.value) return
        val plan = _offlineBasemapPlan.value ?: run {
            estimateOfflineBasemapRegion()
            _offlineBasemapPlan.value
        } ?: return
        val now = System.currentTimeMillis()
        val region = OfflineBasemapRegion(
            id = UUID.randomUUID().toString(),
            terrainKey = _activeTerrainKey.value,
            displayName = displayName?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "${_activeGeoMetadata.value.siteName} offline map",
            bounds = plan.bounds,
            zoom = plan.zoom,
            tileCount = plan.tileCount,
            completedTiles = plan.cachedTiles,
            estimatedBytes = plan.estimatedDownloadBytes,
            storedBytes = plan.cachedBytes,
            status = OfflineBasemapStatus.PLANNED,
            lastError = null,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        startOfflineBasemapDownload(region, plan)
    }

    fun retryOfflineBasemapRegion(region: OfflineBasemapRegion) {
        if (_offlineBasemapDownloading.value) return
        val plan = basemapTileRepository.planOfflineRegion(region.bounds, fixedZoom = region.zoom)
        startOfflineBasemapDownload(
            region.copy(
                completedTiles = plan.cachedTiles,
                storedBytes = plan.cachedBytes,
                estimatedBytes = plan.estimatedDownloadBytes,
                lastError = null,
                updatedAtMillis = System.currentTimeMillis(),
            ),
            plan,
        )
    }

    private fun startOfflineBasemapDownload(region: OfflineBasemapRegion, plan: BasemapPlan) {
        offlineBasemapDownloadJob?.cancel()
        activeOfflineDownloadId = region.id
        offlineBasemapDownloadJob = viewModelScope.launch {
            _offlineBasemapDownloading.value = true
            _offlineBasemapProgress.value = BasemapDownloadProgress(
                completedTiles = plan.cachedTiles,
                totalTiles = plan.tileCount,
                downloadedBytes = 0L,
            )
            var current = region.copy(
                status = OfflineBasemapStatus.DOWNLOADING,
                updatedAtMillis = System.currentTimeMillis(),
            )
            offlineBasemapRegionDao.upsert(current.toEntity())
            try {
                val result = basemapTileRepository.downloadOfflineRegion(plan) { progress ->
                    _offlineBasemapProgress.value = progress
                }
                val ready = result.failedTiles == 0 && result.completedTiles == plan.tileCount
                current = current.copy(
                    completedTiles = result.completedTiles,
                    storedBytes = result.storedBytes,
                    status = if (ready) OfflineBasemapStatus.READY else OfflineBasemapStatus.FAILED,
                    lastError = when {
                        ready -> null
                        result.blockedByServer -> "The tile server rejected one or more requests."
                        else -> "${result.failedTiles} tile download(s) failed. Retry to fetch only missing tiles."
                    },
                    updatedAtMillis = System.currentTimeMillis(),
                )
                offlineBasemapRegionDao.upsert(current.toEntity())
                if (ready) {
                    _offlineBasemapMessage.value = "Offline map saved. It can now reopen without service."
                    _basemapEnabled.value = true
                    settingsRepo.saveBoolean(SettingsRepository.Keys.BASEMAP_ENABLED, true)
                    refreshBasemapTiles()
                } else {
                    _offlineBasemapMessage.value = current.lastError
                }
            } catch (cancelled: CancellationException) {
                current = current.copy(
                    status = OfflineBasemapStatus.CANCELED,
                    lastError = "Download canceled. Retry keeps completed tiles.",
                    updatedAtMillis = System.currentTimeMillis(),
                )
                withContext(NonCancellable) {
                    offlineBasemapRegionDao.upsert(current.toEntity())
                }
                throw cancelled
            } finally {
                if (activeOfflineDownloadId == region.id) {
                    activeOfflineDownloadId = null
                    _offlineBasemapDownloading.value = false
                    _offlineBasemapProgress.value = null
                }
            }
        }
    }

    fun cancelOfflineBasemapDownload() {
        offlineBasemapDownloadJob?.cancel()
    }

    fun openOfflineBasemapRegion(region: OfflineBasemapRegion) {
        if (region.status != OfflineBasemapStatus.READY) return
        _basemapEnabled.value = true
        viewModelScope.launch {
            settingsRepo.saveBoolean(SettingsRepository.Keys.BASEMAP_ENABLED, true)
            val result = basemapTileRepository.loadBasemap(
                bounds = region.bounds,
                fixedZoom = region.zoom,
                maxTiles = region.tileCount,
                allowNetwork = false,
            )
            _basemapBitmap.value = result.bitmap
            _basemapStatus.value = if (
                result.bitmap != null && result.loadedTiles == result.expectedTiles
            ) {
                "Saved offline basemap ready."
            } else {
                "Saved region is incomplete. Retry its missing tiles."
            }
        }
    }

    fun deleteOfflineBasemapRegion(region: OfflineBasemapRegion) {
        viewModelScope.launch(Dispatchers.IO) {
            if (activeOfflineDownloadId == region.id) offlineBasemapDownloadJob?.cancel()
            val retainedEntities = offlineBasemapRegionDao.getAll().filterNot { it.id == region.id }
            val retained = retainedEntities.map {
                SlippyTileMath.boundsToTileRange(
                    GeoSpatialLibrary.GeographicBounds(it.minLat, it.maxLat, it.minLon, it.maxLon),
                    it.zoom,
                )
            }
            basemapTileRepository.deleteTilesUsedOnlyBy(
                SlippyTileMath.boundsToTileRange(region.bounds, region.zoom),
                retained,
            )
            offlineBasemapRegionDao.deleteById(region.id)
            if (region.terrainKey == _activeTerrainKey.value &&
                retainedEntities.none {
                    it.terrainKey == region.terrainKey && it.status == OfflineBasemapStatus.READY.name
                }
            ) {
                _basemapBitmap.value = null
                _basemapStatus.value = "Offline region removed."
            }
        }
    }

    private fun refreshVisibleSignals() {
        _loggedSignals.value = targetsForTerrain(allLoggedSignals, _activeTerrainKey.value)
    }

    private fun visualizationLabel(mode: Int): String = when (mode) {
        0 -> "Standard hillshade"
        1 -> "Multi-directional hillshade"
        2 -> "Slope"
        3 -> "Local relief"
        4 -> "Curvature"
        5 -> "Disturbance screening"
        6 -> "Aspect"
        7 -> "Elevation"
        8 -> "Canopy height"
        else -> "Terrain"
    }

    private suspend fun loadSettings() {
        _sunAzimuth.value = settingsRepo.getFloat(SettingsRepository.Keys.SUN_AZIMUTH, 315f)
        _sunAltitude.value = settingsRepo.getFloat(SettingsRepository.Keys.SUN_ALTITUDE, 35f)
        _vegetationFilter.value = settingsRepo.getFloat(SettingsRepository.Keys.VEGETATION_FILTER, 0.8f)
        _paletteType.value = settingsRepo.getInt(SettingsRepository.Keys.PALETTE_TYPE, 1)
        _contrast.value = settingsRepo.getFloat(SettingsRepository.Keys.CONTRAST, 1.5f)
        _visualizationMode.value = settingsRepo.getInt(SettingsRepository.Keys.VISUALIZATION_MODE, 0)
        _overlayType.value = settingsRepo.getInt(SettingsRepository.Keys.OVERLAY_TYPE, 0)
        _overlayOpacity.value = settingsRepo.getFloat(SettingsRepository.Keys.OVERLAY_OPACITY, 0.4f)
        _gridSpacing.value = settingsRepo.getFloat(SettingsRepository.Keys.GRID_SPACING, 0f)
        _zScale.value = settingsRepo.getFloat(SettingsRepository.Keys.Z_SCALE, 1f)
        _featureScaleMeters.value = settingsRepo.getFloat(SettingsRepository.Keys.FEATURE_SCALE_METERS, 6f)
        _analysisSensitivity.value = settingsRepo.getFloat(SettingsRepository.Keys.ANALYSIS_SENSITIVITY, 1.2f)
        _contourIntervalMeters.value = settingsRepo.getFloat(SettingsRepository.Keys.CONTOUR_INTERVAL_METERS, 0f)
        
        val savedSite = settingsRepo.getInt(SettingsRepository.Keys.CURRENT_SITE_INDEX, 0)
        val recoveryPreferences = getApplication<Application>().getSharedPreferences(
            "terrain_recovery",
            0,
        )
        val needsLegacyRecovery = !recoveryPreferences.getBoolean("checked_cached_terrain_v1", false)
        restoreImportedTerrainOnStart = savedSite == 3 || needsLegacyRecovery
        recoveryPreferences.edit().putBoolean("checked_cached_terrain_v1", true).apply()
        val site = savedSite.takeIf { it in 0..2 } ?: 0
        _currentSiteIndex.value = site
        _elevationGrid.value = withContext(Dispatchers.Default) {
            DemGenerator.generateSite(site)
        }
        _activeGeoMetadata.value = GeoSpatialLibrary.SITES_METADATA[site]
        if (savedSite in 0..2) {
            _activeTerrainSummary.value = "Built-in simulated terrain"
        } else {
            _activeTerrainSummary.value = "Built-in demonstration terrain"
        }

        _sweepX.value = settingsRepo.getFloat(SettingsRepository.Keys.SWEEP_X, 50f)
        _sweepY.value = settingsRepo.getFloat(SettingsRepository.Keys.SWEEP_Y, 50f)
        _gpsEnabled.value = settingsRepo.getBoolean(SettingsRepository.Keys.GPS_ENABLED, false)
        _heatmapEnabled.value = settingsRepo.getBoolean(SettingsRepository.Keys.HEATMAP_ENABLED, false)
        _basemapEnabled.value = settingsRepo.getBoolean(SettingsRepository.Keys.BASEMAP_ENABLED, false)
        _basemapOpacity.value = settingsRepo.getFloat(SettingsRepository.Keys.BASEMAP_OPACITY, 0.6f)

        // Load viewport settings
        _viewportZoom.value = settingsRepo.getFloat(SettingsRepository.Keys.VIEWPORT_ZOOM, 1f)
        _viewportPanX.value = settingsRepo.getFloat(SettingsRepository.Keys.VIEWPORT_PAN_X, 0f)
        _viewportPanY.value = settingsRepo.getFloat(SettingsRepository.Keys.VIEWPORT_PAN_Y, 0f)
        _viewportRestoreToken.value = _viewportRestoreToken.value + 1

        // Mark settings as loaded so subsequent saveSettings() calls are permitted
        isSettingsLoaded = true

        if (_gpsEnabled.value && _hasLocationPermission.value) startLocationUpdates()
        if (_basemapEnabled.value) refreshBasemapTiles()
    }

    /**
     * Restores the most recently imported LAZ/LAS after process death, but only from an existing
     * decoded cache. Startup never reparses a multi-hundred-megabyte point cloud unexpectedly.
     */
    private suspend fun restoreLastCachedTerrain() {
        val application = getApplication<Application>()
        val storageRoot = application.getExternalFilesDir(null) ?: application.filesDir
        val dataset = withContext(Dispatchers.IO) {
            LazDatasetStore(File(storageRoot, "lidar")).list().firstOrNull()
        } ?: return
        val diskCache = LazTerrainDiskCache(File(application.cacheDir, "decoded-terrain"))
        val optionCandidates = listOf(512, 1_024, 320).map { resolution ->
            LidarImportOptions(
                groundMode = GroundSurfaceMode.SOURCE_CLASSIFIED,
                rasterResolution = resolution,
                smoothingRadius = 0,
            )
        }
        val cached = withContext(Dispatchers.IO) {
            optionCandidates.firstNotNullOfOrNull { options ->
                diskCache.get(dataset.file, options)?.let { terrain -> options to terrain }
            }
        } ?: return
        val (options, terrain) = cached
        val scene = withContext(Dispatchers.Default) {
            TerrainGpuSceneBuilder.build(terrain.grid)
        }
        TerrainPerformanceSession.publish(scene)
        setCustomTerrain(
            result = terrain,
            source = TerrainImportSource(
                uri = Uri.fromFile(dataset.file).toString(),
                displayName = dataset.displayName,
                options = options,
            ),
        )
    }

    private fun saveSettings() {
        // Prevent saving defaults over user settings until loadSettings() completes
        if (!isSettingsLoaded) return

        saveSettingsJob?.cancel() // Cancel pending save
        saveSettingsJob = viewModelScope.launch {
            delay(500) // Debounce delay
            settingsRepo.saveFloat(SettingsRepository.Keys.SUN_AZIMUTH, _sunAzimuth.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.SUN_ALTITUDE, _sunAltitude.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.VEGETATION_FILTER, _vegetationFilter.value)
            settingsRepo.saveInt(SettingsRepository.Keys.PALETTE_TYPE, _paletteType.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.CONTRAST, _contrast.value)
            settingsRepo.saveInt(SettingsRepository.Keys.VISUALIZATION_MODE, _visualizationMode.value)
            settingsRepo.saveInt(SettingsRepository.Keys.OVERLAY_TYPE, _overlayType.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.OVERLAY_OPACITY, _overlayOpacity.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.GRID_SPACING, _gridSpacing.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.Z_SCALE, _zScale.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.FEATURE_SCALE_METERS, _featureScaleMeters.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.ANALYSIS_SENSITIVITY, _analysisSensitivity.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.CONTOUR_INTERVAL_METERS, _contourIntervalMeters.value)
            settingsRepo.saveInt(SettingsRepository.Keys.CURRENT_SITE_INDEX, _currentSiteIndex.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.SWEEP_X, _sweepX.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.SWEEP_Y, _sweepY.value)
            settingsRepo.saveBoolean(SettingsRepository.Keys.GPS_ENABLED, _gpsEnabled.value)
            settingsRepo.saveBoolean(SettingsRepository.Keys.HEATMAP_ENABLED, _heatmapEnabled.value)
            settingsRepo.saveBoolean(SettingsRepository.Keys.BASEMAP_ENABLED, _basemapEnabled.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.BASEMAP_OPACITY, _basemapOpacity.value)

            // Save viewport settings
            settingsRepo.saveFloat(SettingsRepository.Keys.VIEWPORT_ZOOM, _viewportZoom.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.VIEWPORT_PAN_X, _viewportPanX.value)
            settingsRepo.saveFloat(SettingsRepository.Keys.VIEWPORT_PAN_Y, _viewportPanY.value)
        }
    }

    override fun onCleared() {
        renderJob?.cancel()
        locationJob?.cancel()
        basemapJob?.cancel()
        surveyLayerJob?.cancel()
        offlineBasemapRegionJob?.cancel()
        offlineBasemapDownloadJob?.cancel()
        saveSettingsJob?.cancel()
        super.onCleared()
    }
}
