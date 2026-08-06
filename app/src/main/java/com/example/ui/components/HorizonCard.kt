package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.analysis.TerrainHorizon
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Compact floating card for skyline status — the most-open and most-blocked directions around
 * the observer, not the full 72-sample list. Mirrors [ViewshedCard]'s never-cover-the-map shape.
 */
@Composable
fun HorizonCard(
    horizon: TerrainHorizon?,
    isComputing: Boolean,
    onClear: () -> Unit,
    onDismiss: () -> Unit = onClear,
    modifier: Modifier = Modifier,
) {
    if (horizon == null && !isComputing) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .widthIn(max = 260.dp)
            .testTag("horizon_card"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (isComputing) "Computing horizon…" else "Horizon",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (isComputing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "Skyline scan on the terrain grid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (horizon != null && horizon.samples.isNotEmpty()) {
                val openest = horizon.samples.minBy { it.elevationAngleDegrees }
                val blocked = horizon.samples.maxBy { it.elevationAngleDegrees }
                Text(
                    "Most open: ${compassLabel(openest.azimuthDegrees)} " +
                        "(${angle(openest.elevationAngleDegrees)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Most blocked: ${compassLabel(blocked.azimuthDegrees)} " +
                        "(${angle(blocked.elevationAngleDegrees)}, ${distance(blocked.distanceMeters)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.testTag("horizon_clear_button"),
                    ) { Text("Clear") }
                    TextButton(onClick = onDismiss) { Text("Hide") }
                }
            }
        }
    }
}

private fun angle(degrees: Float): String = String.format(Locale.US, "%.1f°", degrees)

private fun distance(meters: Float): String = if (meters >= 1000f) {
    String.format(Locale.US, "%.1f km", meters / 1000f)
} else {
    "${meters.roundToInt()} m"
}

private fun compassLabel(azimuth: Float): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((azimuth % 360f) + 360f) % 360f
    return directions[((normalized / 45f).roundToInt()).mod(directions.size)]
}
