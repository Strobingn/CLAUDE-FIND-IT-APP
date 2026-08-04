package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.analysis.TerrainCellInspection
import com.example.analysis.TerrainViewshed
import com.example.geospatial.GeoSpatialLibrary
import com.example.geospatial.MeasurementFormat
import java.util.Locale

@Composable
fun TerrainCellInspectionPanel(
    inspection: TerrainCellInspection,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewshed: TerrainViewshed? = null,
    gridWidth: Int = 0,
    gridHeight: Int = 0,
    isComputingViewshed: Boolean = false,
    onComputeViewshed: () -> Unit = {},
    onClearViewshed: () -> Unit = {},
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
            HorizontalDivider()
            ViewshedSection(
                viewshed = viewshed,
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                isComputing = isComputingViewshed,
                enabled = inspection.valid,
                cellSizeMeters = inspection.cellSizeMeters,
                onCompute = onComputeViewshed,
                onClear = onClearViewshed,
            )
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


/**
 * Line-of-sight preview from the inspected cell, computed on the real elevation grid by
 * [com.example.analysis.TerrainViewshedAnalyzer] (eye height 1.7 m). Shows the visible/blocked
 * mask with the observer marked, plus the visible share of the grid and its area.
 */
@Composable
private fun ViewshedSection(
    viewshed: TerrainViewshed?,
    gridWidth: Int,
    gridHeight: Int,
    isComputing: Boolean,
    enabled: Boolean,
    cellSizeMeters: Float,
    onCompute: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (viewshed == null) {
            OutlinedButton(
                onClick = onCompute,
                enabled = enabled && !isComputing && gridWidth > 0 && gridHeight > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isComputing) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Computing viewshed…")
                } else {
                    Text("Viewshed from this cell")
                }
            }
            Text(
                "What a person standing here could see — line-of-sight on the real elevation grid, eye at 1.7 m.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val visibleAreaSquareMeters = viewshed.visibleCells * cellSizeMeters * cellSizeMeters
            Text(
                "Visible: ${(viewshed.visibilityRatio * 100f).toInt()}% of the grid · " +
                    viewshedAreaText(visibleAreaSquareMeters),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Image(
                bitmap = remember(viewshed) {
                    renderViewshedPreview(viewshed, gridWidth, gridHeight).asImageBitmap()
                },
                contentDescription = "Viewshed preview — visible and blocked cells",
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp),
            )
            Text(
                "Green = visible · dark = blocked · blue dot = this cell",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) {
                Text("Clear viewshed")
            }
        }
    }
}

private fun renderViewshedPreview(
    viewshed: TerrainViewshed,
    gridWidth: Int,
    gridHeight: Int,
): Bitmap {
    val maxSide = 220
    val block = maxOf(1, kotlin.math.ceil(maxOf(gridWidth, gridHeight).toDouble() / maxSide).toInt())
    val outWidth = (gridWidth + block - 1) / block
    val outHeight = (gridHeight + block - 1) / block
    val pixels = IntArray(outWidth * outHeight)
    for (outY in 0 until outHeight) {
        val y0 = outY * block
        val y1 = minOf(gridHeight, y0 + block)
        for (outX in 0 until outWidth) {
            val x0 = outX * block
            val x1 = minOf(gridWidth, x0 + block)
            var anyVisible = false
            var anyCell = false
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val index = y * gridWidth + x
                    if (index < viewshed.visibility.size) {
                        anyCell = true
                        if (viewshed.visibility[index]) anyVisible = true
                    }
                }
            }
            pixels[outY * outWidth + outX] = when {
                anyVisible -> 0xCC2ECC71.toInt()
                anyCell -> 0x55303A46.toInt()
                else -> 0
            }
        }
    }
    // Mark the observer cell so the preview is anchored to what the user tapped.
    val observerColumn = (
        viewshed.observerXPercent.coerceIn(0f, 100f) / 100f * (gridWidth - 1)
        ).toInt() / block
    val observerRow = (
        viewshed.observerYPercent.coerceIn(0f, 100f) / 100f * (gridHeight - 1)
        ).toInt() / block
    for (dy in -1..1) {
        for (dx in -1..1) {
            val x = observerColumn + dx
            val y = observerRow + dy
            if (x in 0 until outWidth && y in 0 until outHeight) {
                pixels[y * outWidth + x] = 0xFF29B6F6.toInt()
            }
        }
    }
    return Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888)
}

private fun viewshedAreaText(areaSquareMeters: Float): String =
    if (areaSquareMeters >= 1_000_000f) {
        String.format(Locale.US, "%.2f km²", areaSquareMeters / 1_000_000f)
    } else {
        "${areaSquareMeters.toInt()} m²"
    }
