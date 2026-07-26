package com.example.data.ai

import android.graphics.Bitmap

/**
 * State machine for AI terrain analysis operations.
 */
sealed interface AiAnalysisState {
    data object Idle : AiAnalysisState
    data object Analyzing : AiAnalysisState
    data class Streaming(val text: String) : AiAnalysisState
    data class Complete(val text: String) : AiAnalysisState
    data class Error(val message: String) : AiAnalysisState
}

/**
 * A clustered anomaly region extracted from the disturbance-candidate analysis.
 */
data class AnomalyRegion(
    /** Column of the region centroid in the elevation grid. */
    val centerCol: Int,
    /** Row of the region centroid in the elevation grid. */
    val centerRow: Int,
    /** Bounding box in grid coordinates: left, top, right, bottom (inclusive). */
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    /** Mean disturbance score (0–1) of all cells in this region. */
    val meanScore: Float,
    /** Number of cells in the cluster. */
    val cellCount: Int,
    /** Cropped terrain bitmap of this region with context padding. */
    val croppedBitmap: Bitmap? = null,
)

/**
 * Gemini's classification result for a single anomaly region.
 */
data class AnomalyClassification(
    val region: AnomalyRegion,
    val label: String,
    val confidence: Float,
    val description: String,
)

/**
 * A single message in the AI field assistant chat.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class ChatRole { USER, ASSISTANT }

/**
 * Snapshot of the current terrain session, sent as context to the chat assistant.
 */
data class TerrainSessionContext(
    val gridWidth: Int,
    val gridHeight: Int,
    val cellSizeMeters: Float,
    val visualizationMode: Int,
    val sunAzimuth: Float,
    val sunAltitude: Float,
    val vegetationFilter: Float,
    val contrast: Float,
    val zScale: Float,
    val terrainSummary: String,
    val hasCoordinates: Boolean,
    val signalCount: Int,
    val signalSummary: String,
)
