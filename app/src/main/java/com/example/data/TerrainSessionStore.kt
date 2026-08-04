package com.example.data

import android.content.Context
import java.io.File

/**
 * Remembers which imported LiDAR source was last opened so process death can restore it.
 * The LAZ file itself lives in [LazDatasetStore]; this only stores the session pointer +
 * decode options. Source files never go in cacheDir.
 */
data class LastOpenedTerrain(
    val absolutePath: String,
    val displayName: String,
    val options: LidarImportOptions,
) {
    val file: File get() = File(absolutePath)
}

class TerrainSessionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(source: TerrainImportSource) {
        val path = filePathFromUri(source.uri) ?: return
        prefs.edit()
            .putString(KEY_PATH, path)
            .putString(KEY_DISPLAY_NAME, source.displayName)
            .putString(KEY_GROUND_MODE, source.options.groundMode.name)
            .putInt(KEY_RESOLUTION, source.options.rasterResolution)
            .putInt(KEY_SMOOTHING, source.options.smoothingRadius)
            .apply()
    }

    fun saveFile(file: File, displayName: String, options: LidarImportOptions) {
        prefs.edit()
            .putString(KEY_PATH, file.absolutePath)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_GROUND_MODE, options.groundMode.name)
            .putInt(KEY_RESOLUTION, options.rasterResolution)
            .putInt(KEY_SMOOTHING, options.smoothingRadius)
            .apply()
    }

    fun load(): LastOpenedTerrain? {
        val path = prefs.getString(KEY_PATH, null)?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.isFile) return null
        val ground = prefs.getString(KEY_GROUND_MODE, null)
            ?.let { name -> GroundSurfaceMode.entries.firstOrNull { it.name == name } }
            ?: GroundSurfaceMode.SOURCE_CLASSIFIED
        return LastOpenedTerrain(
            absolutePath = path,
            displayName = prefs.getString(KEY_DISPLAY_NAME, file.name) ?: file.name,
            options = LidarImportOptions(
                groundMode = ground,
                rasterResolution = prefs.getInt(
                    KEY_RESOLUTION,
                    LidarImportOptions.DEFAULT_OVERVIEW_RESOLUTION,
                ),
                smoothingRadius = prefs.getInt(KEY_SMOOTHING, 0),
            ).sanitized(),
        )
    }

    /** Keeps the pointer valid after a user rename in the library. */
    fun rewritePathIfMatches(oldPath: String, newPath: String, newDisplayName: String) {
        val current = prefs.getString(KEY_PATH, null) ?: return
        if (current != oldPath) return
        prefs.edit()
            .putString(KEY_PATH, newPath)
            .putString(KEY_DISPLAY_NAME, newDisplayName)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun filePathFromUri(uri: String): String? {
        return when {
            uri.startsWith("file:", ignoreCase = true) ->
                android.net.Uri.parse(uri).path
            uri.startsWith("/") -> uri
            else -> null
        }
    }

    private companion object {
        const val PREFS = "terrain_session"
        const val KEY_PATH = "last_path"
        const val KEY_DISPLAY_NAME = "last_display_name"
        const val KEY_GROUND_MODE = "last_ground_mode"
        const val KEY_RESOLUTION = "last_resolution"
        const val KEY_SMOOTHING = "last_smoothing"
    }
}

/** Durable (non-cache) locations for LiDAR sources and decoded terrain rasters. */
object AppTerrainStorage {
    fun lidarStore(context: Context): LazDatasetStore = LazDatasetStore.open(context)

    fun decodedTerrainCache(context: Context): LazTerrainDiskCache {
        val dir = File(context.applicationContext.filesDir, "decoded-terrain")
        dir.mkdirs()
        // Also pull any older cacheDir entries once so restarts keep working after the move.
        val legacy = File(context.applicationContext.cacheDir, "decoded-terrain")
        if (legacy.isDirectory &&
            runCatching { legacy.canonicalPath != dir.canonicalPath }.getOrDefault(true)
        ) {
            legacy.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val dest = File(dir, file.name)
                if (!dest.exists()) runCatching { file.copyTo(dest, overwrite = false) }
            }
        }
        return LazTerrainDiskCache(dir)
    }
}
