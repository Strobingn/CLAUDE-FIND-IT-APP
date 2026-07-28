package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.analysis.TerrainCellInspection
import com.example.geospatial.GeoSpatialLibrary
import com.example.geospatial.MeasurementFormat
import java.util.Locale

@Composable
fun TerrainCellInspectionPanel(
    inspection: TerrainCellInspection,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = modifier.widthIn(max = 420.dp),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 470.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Exact terrain cell", fontWeight = FontWeight.Bold)
                    Text(
                        "Column ${inspection.column}, row ${inspection.row}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close cell inspection")
                }
            }
            Text(
                if (inspection.valid) "Valid source cell" else "No-data source cell",
                color = if (inspection.valid) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.labelLarge,
            )
            HorizontalDivider()
            if (inspection.valid) {
                InspectionValue("Elevation", MeasurementFormat.feet(inspection.elevationMeters, 2))
                InspectionValue("Bare earth", MeasurementFormat.feet(inspection.bareEarthMeters, 2))
                InspectionValue("Canopy height", MeasurementFormat.length(inspection.canopyHeightMeters))
                InspectionValue("Slope", decimal(inspection.slopeDegrees, "°"))
                InspectionValue(
                    "Aspect",
                    inspection.aspectDegrees?.let {
                        "${decimal(it, "°")} ${compassDirection(it)}"
                    } ?: "Flat",
                )
                InspectionValue("Curvature", MeasurementFormat.perFoot(inspection.curvaturePerMeter))
                InspectionValue("Local relief", MeasurementFormat.signedLength(inspection.localReliefMeters))
                InspectionValue("Ruggedness", MeasurementFormat.length(inspection.ruggednessMeters))
                InspectionValue("Depression depth", MeasurementFormat.length(inspection.depressionDepthMeters))
                InspectionValue("Positive openness", decimal(inspection.positiveOpennessDegrees, "°"))
                InspectionValue("Negative openness", decimal(inspection.negativeOpennessDegrees, "°"))
                InspectionValue("Linearity response", MeasurementFormat.perFoot(inspection.linearityResponse))
            } else {
                Text(
                    "This raster location contains no valid source measurement. Select a nearby " +
                        "valid cell to inspect terrain values.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            InspectionValue("Cell resolution", "${MeasurementFormat.resolution(inspection.cellSizeMeters)} square")
            InspectionValue(
                "Neighborhood",
                "${MeasurementFormat.length(inspection.neighborhoodRadiusMeters)} · " +
                    "${inspection.validNeighborhoodCells} valid cells",
            )
            val latitude = inspection.latitude
            val longitude = inspection.longitude
            if (latitude != null && longitude != null) {
                InspectionValue(
                    "Coordinate",
                    "${GeoSpatialLibrary.formatDms(latitude, true)}\n" +
                        GeoSpatialLibrary.formatDms(longitude, false),
                )
            } else {
                InspectionValue("Coordinate", "Local grid · geographic CRS unavailable")
            }
        }
    }
}

@Composable
private fun InspectionValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.44f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.56f),
        )
    }
}

private fun decimal(value: Float, suffix: String, digits: Int = 2): String =
    String.format(Locale.US, "%.${digits}f%s", value, suffix)

private fun compassDirection(degrees: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return directions[((degrees + 22.5f) / 45f).toInt() % directions.size]
}
