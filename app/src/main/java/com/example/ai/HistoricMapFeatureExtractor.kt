package com.example.ai

import android.graphics.Bitmap
import com.example.data.historicmap.MapFeatureType
import java.io.ByteArrayOutputStream

/**
 * One AI-proposed feature traced from a scanned historic-map image, in normalized (0-1) image
 * coordinates - not yet georeferenced to lat/lon, and never persisted without the user accepting
 * it explicitly. Distinct from [com.example.data.historicmap.HistoricMapFeature], which is the
 * persisted, georeferenced record this becomes only after acceptance.
 */
data class ProposedMapFeature(
    val type: MapFeatureType,
    val normalizedPoints: List<Pair<Float, Float>>,
    val description: String,
)

/**
 * Sends a scanned historic-map image to the configured cloud AI provider and asks it to trace
 * visible roads, structures, walls, and boundaries as normalized-coordinate polylines. Purely a
 * proposal source - georeferencing, terrain-agreement scoring, and persistence stay the caller's
 * job; nothing here writes to the database or claims a feature that isn't visibly in the image.
 */
internal object HistoricMapFeatureExtractor {
    private const val MAX_SIDE = 1_280
    private const val MAX_INLINE_BYTES = 3 * 1024 * 1024

    suspend fun extract(
        gateway: TerrainAiGateway,
        bitmap: Bitmap,
        requestedProvider: TerrainAiProvider? = null,
        onStage: (String) -> Unit = {},
    ): List<ProposedMapFeature> {
        val image = encode(bitmap) ?: return emptyList()
        val prompt = """
            This is a scanned historic map. Identify visible roads or tracks, structures or
            buildings, walls, and property or survey boundaries.

            Reply with exactly one line per feature you can actually see, in this format:
            FEATURE|TYPE|x1,y1;x2,y2;x3,y3|short description

            TYPE must be exactly one of: ROAD, STRUCTURE, WALL, BOUNDARY.
            Each x,y is a point traced along the feature, normalized 0.0 to 1.0 within the image
            (0,0 is the top-left corner, 1,1 is the bottom-right corner). Use at least 2 points,
            more for a curved or irregular feature. Only trace what is visibly drawn on the map -
            never invent a feature that isn't there. If nothing is legible, reply with NONE and
            nothing else.
        """.trimIndent()
        val answer = gateway.generate(
            conversation = listOf(GeminiConversationTurn("user", prompt)),
            systemContext = """
                You are tracing real cartographic features from a scanned historic map image for a
                metal-detecting research app. Report only what is visibly present in the image.
            """.trimIndent(),
            image = image,
            requestedProvider = requestedProvider,
            onProviderStage = onStage,
        )
        return parse(answer.text)
    }

    internal fun parse(text: String): List<ProposedMapFeature> =
        text.lineSequence()
            .filter { it.trimStart().startsWith("FEATURE|") }
            .mapNotNull(::parseLine)
            .toList()

    private fun parseLine(line: String): ProposedMapFeature? {
        val parts = line.trim().split('|', limit = 4)
        if (parts.size < 3) return null
        val type = enumValues<MapFeatureType>().firstOrNull { it.name == parts[1].trim().uppercase() }
            ?: return null
        val points = parts[2].trim().split(';').mapNotNull { pair ->
            val coords = pair.split(',')
            if (coords.size != 2) return@mapNotNull null
            val x = coords[0].trim().toFloatOrNull() ?: return@mapNotNull null
            val y = coords[1].trim().toFloatOrNull() ?: return@mapNotNull null
            if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return@mapNotNull null
            x to y
        }
        if (points.size < 2) return null
        val description = parts.getOrNull(3)?.trim().orEmpty()
            .ifBlank { "AI-suggested ${type.label.lowercase()}" }
        return ProposedMapFeature(type, points, description)
    }

    private fun encode(bitmap: Bitmap): GeminiImageInput? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val largestSide = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largestSide > MAX_SIDE) {
            val factor = MAX_SIDE.toFloat() / largestSide.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * factor).toInt().coerceAtLeast(1),
                (bitmap.height * factor).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val bytes = ByteArrayOutputStream().use { output ->
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 86, output)) return null
            output.toByteArray()
        }
        return bytes.takeIf { it.size <= MAX_INLINE_BYTES }
            ?.let { GeminiImageInput(it, "image/jpeg", "scanned historic map") }
    }
}
