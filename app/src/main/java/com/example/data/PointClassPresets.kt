package com.example.data

/**
 * ASPRS LAS classification filter presets for LiDAR import / class-filter UI.
 *
 * [classes] is null for "all returns" (no class filter). Non-null sets restrict elevation
 * binning to the listed ASPRS class codes once the rasterizer honors [LidarImportOptions.allowedClasses].
 */
enum class PointClassPreset(val label: String, val classes: Set<Int>?) {
    ALL("All returns", null),
    GROUND("Ground 2/8", setOf(2, 8)),
    VEGETATION("Vegetation 3–5", setOf(3, 4, 5)),
    BUILDING("Building 6", setOf(6)),
    UNCLASSIFIED("Unclassified 1", setOf(1)),
}
