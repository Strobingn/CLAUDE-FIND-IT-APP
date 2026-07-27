package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.analysis.TerrainElevationProfile
import java.util.Locale

/** Compact chart for an exact raster-cell profile selected on the terrain canvas. */
@Composable
fun TerrainElevationProfilePanel(
    profile: TerrainElevationProfile,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Elevation profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${profile.samples.count { it.valid }}/${profile.samples.size} valid raster samples",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ProfileMetric("Distance", formatMeters(profile.horizontalDistanceMeters))
                ProfileMetric("Rise", "+${formatMeters(profile.ascentMeters)}")
                ProfileMetric("Drop", "−${formatMeters(profile.descentMeters)}")
                ProfileMetric(
                    "Range",
                    "${formatMeters(profile.minimumElevationMeters)}–${formatMeters(profile.maximumElevationMeters)}",
                )
            }
            Canvas(Modifier.fillMaxWidth().height(104.dp)) {
                val samples = profile.samples
                if (samples.isEmpty()) return@Canvas
                val minimum = profile.minimumElevationMeters
                val maximum = profile.maximumElevationMeters
                val range = (maximum - minimum).takeIf { it > 0.001f } ?: 1f
                drawLine(
                    color = Color(0x334D8C88),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - 1f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - 1f),
                    strokeWidth = 1f,
                )
                val path = Path()
                var inSegment = false
                samples.forEachIndexed { index, sample ->
                    if (!sample.valid) {
                        inSegment = false
                        return@forEachIndexed
                    }
                    val x = if (samples.size == 1) size.width / 2f else index * size.width / (samples.size - 1)
                    val y = size.height - ((sample.elevationMeters - minimum) / range * size.height)
                    if (inSegment) path.lineTo(x, y) else {
                        path.moveTo(x, y)
                        inSegment = true
                    }
                }
                drawPath(path, color = Color(0xFF00A59A), style = Stroke(width = 3f))
            }
            Text(
                "Tap Profile, then tap the terrain for the start and end points. Values are sampled from the underlying terrain grid at any zoom.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowScope.ProfileMetric(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun formatMeters(value: Float): String = when {
    value >= 1_000f -> String.format(Locale.US, "%.2f km", value / 1_000f)
    value >= 10f -> String.format(Locale.US, "%.0f m", value)
    else -> String.format(Locale.US, "%.1f m", value)
}
