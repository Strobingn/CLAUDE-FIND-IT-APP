package com.example.data

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Result from the Phase 2 decode pipeline. */
data class TerrainDecodeOutcome(
    val terrain: DemGenerator.TerrainLoadResult,
    val cacheHit: LazTerrainCache.Hit,
    val gpuScene: TerrainGpuScene,
    /**
     * When non-null, [terrain] is a full-resolution sparse preview and this deferred completes
     * with the exact all-return product (or null if cancelled/failed).
     */
    val exactOutcome: Deferred<TerrainDecodeOutcome?>? = null,
    val isPreview: Boolean = false,
)

/**
 * Serializes duplicate work per source/options key while allowing unrelated datasets to decode in
 * parallel. File/cache I/O runs on Dispatchers.IO; GPU mesh construction runs on
 * Dispatchers.Default.
 *
 * Large full-footprint LAZ opens may return a **full-resolution sparse chunk preview** first
 * (same raster size, fewer points), then upgrade to the exact product in the background. Focused
 * refinements, small files, and non-LAZ formats stay exact-first. Product grid quality never
 * drops below the requested resolution (typically ≥ 1,024).
 */
class TerrainDecodeCoordinator(
    private val cache: LazTerrainCache,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val exactJobs = ConcurrentHashMap<String, Deferred<TerrainDecodeOutcome?>>()
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun decode(
        file: File,
        displayName: String = file.name,
        options: LidarImportOptions,
        onPreview: (suspend (TerrainDecodeOutcome) -> Unit)? = null,
        onStage: suspend (String) -> Unit = {},
    ): TerrainDecodeOutcome {
        val sanitized = options.sanitized()
        val fullKey = decodeKey(file, sanitized)
        val lock = locks.getOrPut(fullKey) { Mutex() }
        try {
            return lock.withLock {
                currentCoroutineContext().ensureActive()
                val fullLookup = withContext(Dispatchers.IO) { cache.get(file, sanitized) }
                if (fullLookup.result != null) {
                    onStage(
                        when (fullLookup.hit) {
                            LazTerrainCache.Hit.MEMORY -> "Opening decoded terrain from memory cache…"
                            LazTerrainCache.Hit.DISK -> "Opening decoded terrain from disk cache…"
                            LazTerrainCache.Hit.MISS -> "Reading point cloud…"
                        },
                    )
                    // Cache hit: single GPU build, no intermediate mesh (no double-build).
                    val scene = buildGpuScene(fullLookup.result.grid, fastTiles = false)
                    LazSpatialIndex.ensureBuiltAsync(file)
                    val outcome = TerrainDecodeOutcome(fullLookup.result, fullLookup.hit, scene)
                    onPreview?.invoke(outcome)
                    return@withLock outcome
                }

                val canSparsePreview = isLazFile(file, displayName) &&
                    sanitized.focusBounds == null &&
                    sanitized.rasterResolution >= GPU_PREVIEW_MAX_DIMENSION

                if (canSparsePreview) {
                    onStage("Building full-resolution sparse preview…")
                    val previewTerrain = decodeSparsePreview(file, sanitized)
                    if (previewTerrain != null) {
                        currentCoroutineContext().ensureActive()
                        LazSpatialIndex.ensureBuiltAsync(file)
                        val previewScene = buildGpuScene(previewTerrain.grid, fastTiles = true)
                        val exact = exactJobs[fullKey] ?: startExactJob(
                            key = fullKey,
                            file = file,
                            displayName = displayName,
                            options = sanitized,
                        ).also { exactJobs[fullKey] = it }
                        val previewOutcome = TerrainDecodeOutcome(
                            terrain = previewTerrain,
                            cacheHit = LazTerrainCache.Hit.MISS,
                            gpuScene = previewScene,
                            exactOutcome = exact,
                            isPreview = true,
                        )
                        onPreview?.invoke(previewOutcome)
                        onStage("Preview ready — exact terrain decoding in background…")
                        return@withLock previewOutcome
                    }
                }

                onStage("Decoding LAZ/LAS at ${sanitized.rasterResolution} px…")
                val decoded = decodeFile(file, displayName, sanitized)
                    ?: error("Could not decode ${file.name}")
                currentCoroutineContext().ensureActive()
                cache.put(file, sanitized, decoded)
                LazSpatialIndex.ensureBuiltAsync(file)

                currentCoroutineContext().ensureActive()
                if (onPreview != null) {
                    onStage("Terrain ready — finishing GPU mesh…")
                    onPreview(
                        TerrainDecodeOutcome(
                            terrain = decoded,
                            cacheHit = LazTerrainCache.Hit.MISS,
                            gpuScene = buildGpuScene(decoded.grid, fastTiles = true),
                            isPreview = false,
                        ),
                    )
                } else {
                    onStage("Preparing detailed GPU terrain…")
                }
                // Final return: one standard-tile GPU scene (no second build if onPreview was null).
                val scene = if (onPreview != null) {
                    buildGpuScene(decoded.grid, fastTiles = false)
                } else {
                    buildGpuScene(decoded.grid, fastTiles = false)
                }
                TerrainDecodeOutcome(decoded, LazTerrainCache.Hit.MISS, scene)
            }
        } finally {
            if (!lock.isLocked) locks.remove(fullKey, lock)
        }
    }

    private fun startExactJob(
        key: String,
        file: File,
        displayName: String,
        options: LidarImportOptions,
    ): Deferred<TerrainDecodeOutcome?> = maintenanceScope.async {
        try {
            val decoded = decodeFile(file, displayName, options) ?: return@async null
            cache.put(file, options, decoded)
            val scene = buildGpuScene(decoded.grid, fastTiles = false)
            TerrainDecodeOutcome(
                terrain = decoded,
                cacheHit = LazTerrainCache.Hit.MISS,
                gpuScene = scene,
                isPreview = false,
            )
        } finally {
            exactJobs.remove(key)
        }
    }

    private suspend fun decodeSparsePreview(
        file: File,
        options: LidarImportOptions,
    ): DemGenerator.TerrainLoadResult? = withContext(Dispatchers.IO) {
        val decodeContext = currentCoroutineContext()
        val preview = LazTerrainReader.readSparsePreview(
            file = file,
            options = options,
            shouldContinue = { decodeContext.isActive },
        ) ?: return@withContext null
        DemGenerator.TerrainLoadResult(
            grid = preview.grid,
            summary = preview.note,
            isBareEarth = preview.appliedGroundMode != GroundSurfaceMode.SURFACE_MODEL,
            geoMetadata = null,
        )
    }

    suspend fun decodeRemoteCopc(
        url: String,
        cacheDirectory: File,
        options: LidarImportOptions,
        onStage: suspend (String) -> Unit = {},
    ): TerrainDecodeOutcome {
        val stableAssetUrl = url.substringBefore('?')
        val safeName = MessageDigest.getInstance("SHA-256")
            .digest(stableAssetUrl.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) } + ".copc.range-cache"
        val rangeCache = File(cacheDirectory.apply { mkdirs() }, safeName)
        onStage("Streaming selected COPC byte ranges…")
        val terrain = withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            val laz = LazTerrainReader.readRemote(
                url = url,
                rangeCacheFile = rangeCache,
                options = options,
                shouldContinue = { context.isActive },
            ) ?: error("Could not stream COPC point cloud")
            DemGenerator.TerrainLoadResult(
                grid = laz.grid,
                summary = "COPC range stream · ${laz.note}",
                isBareEarth = laz.appliedGroundMode != GroundSurfaceMode.SURFACE_MODEL,
            )
        }
        onStage("Preparing detailed GPU terrain…")
        val scene = buildGpuScene(terrain.grid, fastTiles = false)
        return TerrainDecodeOutcome(terrain, LazTerrainCache.Hit.MISS, scene)
    }

    private suspend fun buildGpuScene(grid: ElevationGrid, fastTiles: Boolean): TerrainGpuScene =
        withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            TerrainGpuSceneBuilder.build(
                source = grid,
                maxFinestDimension = GPU_PREVIEW_MAX_DIMENSION,
                tileSize = if (fastTiles) GPU_FAST_TILE_SIZE else GPU_PREVIEW_TILE_SIZE,
            )
        }

    private suspend fun decodeFile(
        file: File,
        displayName: String,
        options: LidarImportOptions,
    ): DemGenerator.TerrainLoadResult? = withContext(Dispatchers.IO) {
        val decodeContext = currentCoroutineContext()
        decodeContext.ensureActive()
        if (isLazFile(file, displayName)) {
            val laz = LazTerrainReader.read(
                file = file,
                options = options,
                shouldContinue = { decodeContext.isActive },
            ) ?: return@withContext null
            DemGenerator.TerrainLoadResult(
                grid = laz.grid,
                summary = laz.note,
                isBareEarth = laz.appliedGroundMode != GroundSurfaceMode.SURFACE_MODEL,
            )
        } else {
            FileInputStream(file).buffered(256 * 1024).use { input ->
                DemGenerator.parseFromStreamDetailed(displayName, input, options)
            }
        }
    }

    private fun isLazFile(file: File, displayName: String): Boolean =
        displayName.substringAfterLast('.', "").equals("laz", ignoreCase = true) ||
            file.extension.equals("laz", ignoreCase = true)

    private fun decodeKey(file: File, options: LidarImportOptions): String {
        val sanitized = options.sanitized()
        return buildString {
            append(runCatching { file.canonicalPath }.getOrDefault(file.absolutePath))
            append('|').append(file.length())
            append('|').append(file.lastModified())
            append('|').append(sanitized.groundMode)
            append('|').append(sanitized.rasterResolution)
            append('|').append(sanitized.smoothingRadius)
            append('|').append(sanitized.focusBounds)
        }
    }

    companion object {
        internal const val GPU_PREVIEW_MAX_DIMENSION = 1_024
        internal const val GPU_PREVIEW_TILE_SIZE = 128
        /** Larger tiles = fewer GPU batches for intermediate first-paint publish. */
        internal const val GPU_FAST_TILE_SIZE = 256
    }
}

/** App-wide current GPU terrain session consumed by the Compose/OpenGL renderer. */
object TerrainPerformanceSession {
    private val _gpuScene = MutableStateFlow<TerrainGpuScene?>(null)
    val gpuScene: StateFlow<TerrainGpuScene?> = _gpuScene.asStateFlow()

    fun publish(scene: TerrainGpuScene) {
        _gpuScene.value = scene
    }

    fun clear() {
        _gpuScene.value = null
    }
}
