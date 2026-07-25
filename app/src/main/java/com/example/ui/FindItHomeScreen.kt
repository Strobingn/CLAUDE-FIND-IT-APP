package com.example.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

private val ClayDark = Color(0xFF2A1A14)
private val ClayMid = Color(0xFF694634)
private val ClayLight = Color(0xFFC79470)
private val ClayHighlight = Color(0xFFF0C8A8)

/**
 * App-level landing experience. The Android back button returns here from the full workspace.
 */
@Composable
fun FindItAppRoot(viewModel: HillshadeViewModel) {
    var workspaceOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = workspaceOpen) {
        workspaceOpen = false
    }

    if (workspaceOpen) {
        MainScreen(viewModel = viewModel)
    } else {
        FindItHomeScreen(
            viewModel = viewModel,
            onOpenWorkspace = { workspaceOpen = true },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindItHomeScreen(
    viewModel: HillshadeViewModel,
    onOpenWorkspace: () -> Unit,
) {
    val bitmap by viewModel.hillshadeBitmap.collectAsStateWithLifecycle()
    val isRendering by viewModel.isRendering.collectAsStateWithLifecycle()
    val grid by viewModel.elevationGrid.collectAsStateWithLifecycle()
    val terrainSummary by viewModel.activeTerrainSummary.collectAsStateWithLifecycle()
    val signals by viewModel.loggedSignals.collectAsStateWithLifecycle()
    val palette by viewModel.paletteType.collectAsStateWithLifecycle()
    val gpsEnabled by viewModel.gpsEnabled.collectAsStateWithLifecycle()
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle()
    val canRefine by viewModel.canRefineTerrain.collectAsStateWithLifecycle()
    val isDetailed by viewModel.isDetailedTerrain.collectAsStateWithLifecycle()
    val currentLat by viewModel.currentLat.collectAsStateWithLifecycle()
    val currentLon by viewModel.currentLon.collectAsStateWithLifecycle()

    val preview = remember(bitmap) { bitmap.safeImageBitmap() }
    val paletteName = when (palette) {
        0 -> "Clay"
        1 -> "Copper"
        2 -> "Terrain"
        else -> "Custom"
    }
    val widthMeters = (grid.width - 1).coerceAtLeast(1) * grid.cellSizeMeters
    val heightMeters = (grid.height - 1).coerceAtLeast(1) * grid.cellSizeMeters

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FIND IT",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            text = "LiDAR field intelligence",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeroCard(
                    paletteName = paletteName,
                    isRendering = isRendering,
                    onOpenWorkspace = onOpenWorkspace,
                )
            }

            item {
                TerrainPreviewCard(
                    preview = preview,
                    isRendering = isRendering,
                    terrainSummary = terrainSummary,
                    gridLabel = String.format(
                        Locale.US,
                        "%d×%d cells · %.2f m/cell",
                        grid.width,
                        grid.height,
                        grid.cellSizeMeters,
                    ),
                    onOpenWorkspace = onOpenWorkspace,
                )
            }

            item {
                Text(
                    text = "FIELD OVERVIEW",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        title = "Coverage",
                        value = String.format(Locale.US, "%.0f × %.0f m", widthMeters, heightMeters),
                        subtitle = if (isDetailed) "Detailed viewport" else "Current raster",
                        icon = CenterFocusStrong,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        title = "Finds",
                        value = signals.size.toString(),
                        subtitle = if (signals.isEmpty()) "No targets logged" else "Saved field targets",
                        icon = Flag,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        title = "Image style",
                        value = paletteName,
                        subtitle = if (palette == 0) "Default relief palette" else "Selected palette",
                        icon = Landscape,
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        title = "GPS",
                        value = when {
                            gpsEnabled && hasLocationPermission -> "Tracking"
                            gpsEnabled -> "Permission"
                            else -> "Off"
                        },
                        subtitle = currentLat?.let { lat ->
                            currentLon?.let { lon -> String.format(Locale.US, "%.4f, %.4f", lat, lon) }
                        } ?: "No active position",
                        icon = GpsFixed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Text(
                    text = "QUICK START",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionCard(
                        title = "Terrain",
                        subtitle = "Inspect relief, slope, curvature and disturbance",
                        icon = Landscape,
                        onClick = onOpenWorkspace,
                        modifier = Modifier.weight(1f),
                    )
                    ActionCard(
                        title = "Import",
                        subtitle = "Open LAZ, LAS, GeoTIFF and local terrain",
                        icon = UploadFile,
                        onClick = onOpenWorkspace,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionCard(
                        title = "Map",
                        subtitle = "Align terrain with geographic imagery",
                        icon = Layers,
                        onClick = onOpenWorkspace,
                        modifier = Modifier.weight(1f),
                    )
                    ActionCard(
                        title = "AI analysis",
                        subtitle = "Review the active terrain with cloud or local context",
                        icon = AutoAwesome,
                        onClick = onOpenWorkspace,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Active terrain",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = terrainSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = when {
                                isDetailed -> "High-detail viewport loaded from the source point cloud."
                                canRefine -> "Zoom into the workspace to load original point-cloud detail."
                                else -> "Import a point cloud to enable source-detail refinement."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HeroCard(
    paletteName: String,
    isRendering: Boolean,
    onOpenWorkspace: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(ClayDark, ClayMid, ClayLight),
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "READ THE GROUND",
                        color = ClayHighlight,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Professional terrain analysis built for field decisions.",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Landscape,
                            contentDescription = null,
                            tint = ClayHighlight,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeStatusPill(text = "$paletteName image style")
                HomeStatusPill(text = if (isRendering) "Rendering" else "Ready")
            }

            Button(
                onClick = onOpenWorkspace,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Landscape, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open terrain workspace", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TerrainPreviewCard(
    preview: androidx.compose.ui.graphics.ImageBitmap?,
    isRendering: Boolean,
    terrainSummary: String,
    gridLabel: String,
    onOpenWorkspace: () -> Unit,
) {
    Card(
        onClick = onOpenWorkspace,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.85f)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF241914), Color(0xFF684531), Color(0xFFB8815B)),
                        ),
                    ),
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = "Current terrain preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.66f)),
                                ),
                            ),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.58f),
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                ) {
                    Text(
                        text = if (preview == null) "DEMO TERRAIN" else "ACTIVE TERRAIN",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                ) {
                    Text(
                        text = terrainSummary,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = gridLabel,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            if (isRendering) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Terrain preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Tap to inspect, zoom and analyze",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onOpenWorkspace) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ClayLight,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ClayMid.copy(alpha = 0.2f),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ClayLight,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeStatusPill(text: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun Bitmap?.safeImageBitmap(): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        this?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }?.asImageBitmap()
    }.getOrNull()
