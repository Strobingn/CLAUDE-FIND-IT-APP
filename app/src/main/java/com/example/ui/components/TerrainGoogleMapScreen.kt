package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.ElevationGrid
import com.example.data.survey.SurveyFeature
import com.example.data.survey.SurveyGeometryType
import com.example.geospatial.GeoSpatialLibrary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.GroundOverlay
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import java.security.MessageDigest
import kotlin.math.cos

@Composable
fun TerrainGoogleMapScreen(
    terrainBitmap: Bitmap?,
    grid: ElevationGrid,
    metadata: GeoSpatialLibrary.GeoSpatialMetadata,
    terrainKey: String,
    surveyFeatures: List<SurveyFeature> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = rememberManagedMapView()
    val alignmentStore = remember(context) { TerrainMapAlignmentStore(context.applicationContext) }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    var overlay by remember { mutableStateOf<GroundOverlay?>(null) }
    var surveyMapObjects by remember { mutableStateOf<List<Any>>(emptyList()) }
    var cameraCenter by remember { mutableStateOf(LatLng(39.5, -98.35)) }
    val naturalSize = remember(metadata.bounds, grid.width, grid.height, grid.cellSizeMeters) {
        naturalOverlaySize(metadata, grid)
    }
    val defaultAlignment = remember(metadata.bounds) { metadata.bounds?.toDefaultAlignment() }
    var alignment by remember(terrainKey, defaultAlignment) {
        mutableStateOf(alignmentStore.load(terrainKey) ?: defaultAlignment)
    }
    var hasSavedAlignment by remember(terrainKey) { mutableStateOf(alignmentStore.contains(terrainKey)) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.72f) }
    var mapType by rememberSaveable { mutableStateOf(GoogleMap.MAP_TYPE_HYBRID) }
    var alignmentMode by rememberSaveable(terrainKey) { mutableStateOf(false) }
    var editBounds by rememberSaveable { mutableStateOf(false) }
    var lastFramedTerrainKey by remember { mutableStateOf<String?>(null) }
    val surveyPoints = remember(surveyFeatures) {
        surveyFeatures.flatMap { feature ->
            feature.coordinates.map { LatLng(it.latitude, it.longitude) }
        }
    }

    fun updateAlignment(updated: TerrainMapAlignment) {
        alignment = updated
        alignmentStore.save(terrainKey, updated)
        hasSavedAlignment = true
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            googleMap = map
            map.mapType = mapType
            map.uiSettings.isCompassEnabled = true
            map.uiSettings.isMapToolbarEnabled = false
            map.uiSettings.isZoomControlsEnabled = false
            map.setOnCameraIdleListener { cameraCenter = map.cameraPosition.target }
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (hasLocationPermission) {
                runCatching {
                    map.isMyLocationEnabled = true
                    map.uiSettings.isMyLocationButtonEnabled = true
                }
            }
        }
        onDispose {
            overlay?.remove()
            surveyMapObjects.forEach(::removeMapObject)
            googleMap = null
        }
    }

    LaunchedEffect(googleMap, mapType) {
        googleMap?.mapType = mapType
    }

    LaunchedEffect(googleMap, terrainBitmap, alignment, naturalSize, opacity, terrainKey) {
        val map = googleMap ?: return@LaunchedEffect
        overlay?.remove()
        overlay = null
        val bitmap = terrainBitmap?.takeIf { !it.isRecycled } ?: return@LaunchedEffect
        val placement = alignment ?: return@LaunchedEffect
        val widthMeters = naturalSize.widthMeters * placement.widthScale
        val heightMeters = naturalSize.heightMeters * placement.heightScale
        overlay = map.addGroundOverlay(
            GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromBitmap(bitmap))
                .position(placement.center, widthMeters, heightMeters)
                .bearing(placement.bearingDegrees)
                .transparency(1f - opacity.coerceIn(0.1f, 1f))
                .zIndex(4f),
        )
        if (lastFramedTerrainKey != terrainKey) {
            lastFramedTerrainKey = terrainKey
            val frameBounds = boundsCenteredAt(placement.center, widthMeters, heightMeters).toLatLngBounds()
            mapView.post {
                runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(frameBounds, 72)) }
                    .onFailure { map.moveCamera(CameraUpdateFactory.newLatLngZoom(placement.center, 16f)) }
            }
        }
    }

    LaunchedEffect(googleMap, surveyFeatures) {
        val map = googleMap ?: return@LaunchedEffect
        surveyMapObjects.forEach(::removeMapObject)
        surveyMapObjects = surveyFeatures.mapNotNull { feature ->
            val points = feature.coordinates.map { LatLng(it.latitude, it.longitude) }
            when (feature.geometryType) {
                SurveyGeometryType.POINT -> points.firstOrNull()?.let { point ->
                    map.addMarker(
                        MarkerOptions()
                            .position(point)
                            .title(feature.name ?: "Survey waypoint"),
                    )
                }
                SurveyGeometryType.LINE -> if (points.size >= 2) {
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(android.graphics.Color.CYAN)
                            .width(6f)
                            .zIndex(6f),
                    )
                } else {
                    null
                }
                SurveyGeometryType.POLYGON -> if (points.size >= 3) {
                    map.addPolygon(
                        PolygonOptions()
                            .addAll(points)
                            .strokeColor(android.graphics.Color.CYAN)
                            .fillColor(android.graphics.Color.argb(42, 0, 229, 255))
                            .strokeWidth(5f)
                            .zIndex(5f),
                    )
                } else {
                    null
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        OverlayHeader(
            mapType = mapType,
            onMapTypeChanged = { mapType = it },
            status = when {
                BuildConfig.MAPS_API_KEY.isBlank() -> "MAPS_API_KEY is missing from .env/local.properties"
                terrainBitmap == null -> "Render or import a terrain layer first"
                alignment == null -> "Pan the map, then place the LAZ image at the center crosshair"
                hasSavedAlignment -> "Alignment saved for this terrain file"
                else -> "Using geographic bounds from the terrain file"
            },
            isError = BuildConfig.MAPS_API_KEY.isBlank(),
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(0.96f),
        )

        OverlayControls(
            opacity = opacity,
            onOpacityChanged = { opacity = it },
            alignmentMode = alignmentMode,
            onAlignmentModeChanged = { alignmentMode = it },
            alignment = alignment,
            canPlace = terrainBitmap != null,
            onPlaceAtCenter = {
                updateAlignment(
                    (alignment ?: TerrainMapAlignment(cameraCenter)).copy(center = cameraCenter),
                )
            },
            onWidthScaleChanged = { value ->
                alignment?.let { updateAlignment(it.copy(widthScale = value)) }
            },
            onHeightScaleChanged = { value ->
                alignment?.let { updateAlignment(it.copy(heightScale = value)) }
            },
            onBearingChanged = { value ->
                alignment?.let { updateAlignment(it.copy(bearingDegrees = value)) }
            },
            onNudge = { eastFraction, northFraction ->
                alignment?.let {
                    updateAlignment(
                        it.copy(
                            center = nudgeCenter(
                                center = it.center,
                                eastMeters = naturalSize.widthMeters * it.widthScale * eastFraction,
                                northMeters = naturalSize.heightMeters * it.heightScale * northFraction,
                            ),
                        ),
                    )
                }
            },
            onEditBounds = { editBounds = true },
            canShowSurvey = surveyPoints.isNotEmpty(),
            onShowSurvey = {
                val map = googleMap ?: return@OverlayControls
                val first = surveyPoints.firstOrNull() ?: return@OverlayControls
                mapView.post {
                    if (surveyPoints.size == 1) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(first, 17f))
                    } else {
                        val bounds = LatLngBounds.builder().apply {
                            surveyPoints.forEach(::include)
                        }.build()
                        runCatching {
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 88))
                        }.onFailure {
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(first, 16f))
                        }
                    }
                }
            },
            canReset = hasSavedAlignment,
            onReset = {
                alignmentStore.clear(terrainKey)
                alignment = defaultAlignment
                hasSavedAlignment = false
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(0.96f),
        )

        if (alignmentMode) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = "Map center",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(7.dp).size(28.dp),
                )
            }
        }
    }

    if (editBounds) {
        BoundsEditorDialog(
            initial = alignment?.toBounds(naturalSize),
            onDismiss = { editBounds = false },
            onApply = {
                updateAlignment(it.toAlignment(naturalSize, alignment?.bearingDegrees ?: 0f))
                editBounds = false
            },
        )
    }
}

@Composable
private fun OverlayHeader(
    mapType: Int,
    onMapTypeChanged: (Int) -> Unit,
    status: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Rendered LiDAR overlay", fontWeight = FontWeight.Bold)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "Map" to GoogleMap.MAP_TYPE_NORMAL,
                    "Satellite" to GoogleMap.MAP_TYPE_SATELLITE,
                    "Hybrid" to GoogleMap.MAP_TYPE_HYBRID,
                    "Terrain" to GoogleMap.MAP_TYPE_TERRAIN,
                ).forEach { (label, type) ->
                    FilterChip(
                        selected = mapType == type,
                        onClick = { onMapTypeChanged(type) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayControls(
    opacity: Float,
    onOpacityChanged: (Float) -> Unit,
    alignmentMode: Boolean,
    onAlignmentModeChanged: (Boolean) -> Unit,
    alignment: TerrainMapAlignment?,
    canPlace: Boolean,
    onPlaceAtCenter: () -> Unit,
    onWidthScaleChanged: (Float) -> Unit,
    onHeightScaleChanged: (Float) -> Unit,
    onBearingChanged: (Float) -> Unit,
    onNudge: (eastFraction: Float, northFraction: Float) -> Unit,
    onEditBounds: () -> Unit,
    canShowSurvey: Boolean,
    onShowSurvey: () -> Unit,
    canReset: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onAlignmentModeChanged(!alignmentMode) }, enabled = canPlace) {
                    Icon(Icons.Default.EditLocationAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (alignmentMode) "Done aligning" else "Align LAZ")
                }
                OutlinedButton(onClick = onPlaceAtCenter, enabled = canPlace) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Center here")
                }
                OutlinedButton(onClick = onShowSurvey, enabled = canShowSurvey) {
                    Icon(Icons.Default.Layers, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Show survey")
                }
                if (canReset) {
                    OutlinedButton(onClick = onReset) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reset")
                    }
                }
            }
            if (alignmentMode) {
                AlignmentSlider(
                    label = "Width",
                    valueLabel = "${((alignment?.widthScale ?: 1f) * 100).toInt()}%",
                    value = alignment?.widthScale ?: 1f,
                    onValueChange = onWidthScaleChanged,
                    range = 0.2f..5f,
                    enabled = alignment != null,
                )
                AlignmentSlider(
                    label = "Height",
                    valueLabel = "${((alignment?.heightScale ?: 1f) * 100).toInt()}%",
                    value = alignment?.heightScale ?: 1f,
                    onValueChange = onHeightScaleChanged,
                    range = 0.2f..5f,
                    enabled = alignment != null,
                )
                AlignmentSlider(
                    label = "Rotation",
                    valueLabel = "${(alignment?.bearingDegrees ?: 0f).toInt()}°",
                    value = alignment?.bearingDegrees ?: 0f,
                    onValueChange = onBearingChanged,
                    range = -180f..180f,
                    enabled = alignment != null,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "←" to (-0.02f to 0f),
                        "↑" to (0f to 0.02f),
                        "↓" to (0f to -0.02f),
                        "→" to (0.02f to 0f),
                    ).forEach { (label, direction) ->
                        OutlinedButton(
                            onClick = { onNudge(direction.first, direction.second) },
                            enabled = alignment != null,
                        ) { Text(label) }
                    }
                    OutlinedButton(onClick = onEditBounds, enabled = alignment != null) {
                        Text("Exact bounds")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacity", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = opacity, onValueChange = onOpacityChanged, valueRange = 0.1f..1f)
        }
    }
}

@Composable
private fun AlignmentSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Text(valueLabel, style = MaterialTheme.typography.labelMedium)
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        valueRange = range,
        enabled = enabled,
    )
}

@Composable
private fun BoundsEditorDialog(
    initial: GeoSpatialLibrary.GeographicBounds?,
    onDismiss: () -> Unit,
    onApply: (GeoSpatialLibrary.GeographicBounds) -> Unit,
) {
    var south by remember(initial) { mutableStateOf(initial?.minLat?.toString().orEmpty()) }
    var north by remember(initial) { mutableStateOf(initial?.maxLat?.toString().orEmpty()) }
    var west by remember(initial) { mutableStateOf(initial?.minLon?.toString().orEmpty()) }
    var east by remember(initial) { mutableStateOf(initial?.maxLon?.toString().orEmpty()) }
    val bounds = remember(south, north, west, east) {
        val s = south.toDoubleOrNull()
        val n = north.toDoubleOrNull()
        val w = west.toDoubleOrNull()
        val e = east.toDoubleOrNull()
        if (s != null && n != null && w != null && e != null &&
            s in -90.0..90.0 && n in -90.0..90.0 &&
            w in -180.0..180.0 && e in -180.0..180.0 && n > s && e > w
        ) GeoSpatialLibrary.GeographicBounds(s, n, w, e) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Align LAZ overlay") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the WGS84 south, north, west, and east footprint for the LAZ image.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoordinateField("South", south, { south = it }, Modifier.weight(1f))
                    CoordinateField("North", north, { north = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoordinateField("West", west, { west = it }, Modifier.weight(1f))
                    CoordinateField("East", east, { east = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { bounds?.let(onApply) }, enabled = bounds != null) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CoordinateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(16)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun rememberManagedMapView(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context) { MapView(context).apply { onCreate(Bundle()) } }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { mapView.onPause() }
            runCatching { mapView.onStop() }
            runCatching { mapView.onDestroy() }
        }
    }
    return mapView
}

private fun GeoSpatialLibrary.GeographicBounds.toLatLngBounds(): LatLngBounds =
    LatLngBounds(LatLng(minLat, minLon), LatLng(maxLat, maxLon))

private fun boundsCenteredAt(
    center: LatLng,
    widthMeters: Float,
    heightMeters: Float,
): GeoSpatialLibrary.GeographicBounds {
    val halfLat = heightMeters.coerceAtLeast(1f) / 111_320.0 / 2.0
    val metersPerLongitudeDegree = (111_320.0 * cos(Math.toRadians(center.latitude))).coerceAtLeast(10_000.0)
    val halfLon = widthMeters.coerceAtLeast(1f) / metersPerLongitudeDegree / 2.0
    return GeoSpatialLibrary.GeographicBounds(
        minLat = (center.latitude - halfLat).coerceAtLeast(-90.0),
        maxLat = (center.latitude + halfLat).coerceAtMost(90.0),
        minLon = (center.longitude - halfLon).coerceAtLeast(-180.0),
        maxLon = (center.longitude + halfLon).coerceAtMost(180.0),
    )
}

private data class NaturalOverlaySize(
    val widthMeters: Float,
    val heightMeters: Float,
)

private data class TerrainMapAlignment(
    val center: LatLng,
    val widthScale: Float = 1f,
    val heightScale: Float = 1f,
    val bearingDegrees: Float = 0f,
)

private fun naturalOverlaySize(
    metadata: GeoSpatialLibrary.GeoSpatialMetadata,
    grid: ElevationGrid,
): NaturalOverlaySize {
    val bounds = metadata.bounds
    if (bounds != null) {
        val centerLat = (bounds.minLat + bounds.maxLat) / 2.0
        val centerLon = (bounds.minLon + bounds.maxLon) / 2.0
        return NaturalOverlaySize(
            widthMeters = GeoSpatialLibrary.calculateGeodesicDistance(
                centerLat,
                bounds.minLon,
                centerLat,
                bounds.maxLon,
            ).toFloat().coerceAtLeast(1f),
            heightMeters = GeoSpatialLibrary.calculateGeodesicDistance(
                bounds.minLat,
                centerLon,
                bounds.maxLat,
                centerLon,
            ).toFloat().coerceAtLeast(1f),
        )
    }
    return NaturalOverlaySize(
        widthMeters = ((grid.width - 1).coerceAtLeast(1) * grid.cellSizeMeters).coerceAtLeast(1f),
        heightMeters = ((grid.height - 1).coerceAtLeast(1) * grid.cellSizeMeters).coerceAtLeast(1f),
    )
}

private fun GeoSpatialLibrary.GeographicBounds.toDefaultAlignment() = TerrainMapAlignment(
    center = LatLng((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0),
)

private fun TerrainMapAlignment.toBounds(size: NaturalOverlaySize): GeoSpatialLibrary.GeographicBounds =
    boundsCenteredAt(
        center = center,
        widthMeters = size.widthMeters * widthScale,
        heightMeters = size.heightMeters * heightScale,
    )

private fun GeoSpatialLibrary.GeographicBounds.toAlignment(
    naturalSize: NaturalOverlaySize,
    bearingDegrees: Float,
): TerrainMapAlignment {
    val center = LatLng((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    val requestedSize = NaturalOverlaySize(
        widthMeters = GeoSpatialLibrary.calculateGeodesicDistance(
            center.latitude,
            minLon,
            center.latitude,
            maxLon,
        ).toFloat(),
        heightMeters = GeoSpatialLibrary.calculateGeodesicDistance(
            minLat,
            center.longitude,
            maxLat,
            center.longitude,
        ).toFloat(),
    )
    return TerrainMapAlignment(
        center = center,
        widthScale = (requestedSize.widthMeters / naturalSize.widthMeters).coerceIn(0.2f, 5f),
        heightScale = (requestedSize.heightMeters / naturalSize.heightMeters).coerceIn(0.2f, 5f),
        bearingDegrees = bearingDegrees,
    )
}

private fun nudgeCenter(center: LatLng, eastMeters: Float, northMeters: Float): LatLng {
    val latitude = (center.latitude + northMeters / 111_320.0).coerceIn(-90.0, 90.0)
    val metersPerLongitudeDegree =
        (111_320.0 * cos(Math.toRadians(center.latitude))).coerceAtLeast(10_000.0)
    val longitude = (center.longitude + eastMeters / metersPerLongitudeDegree).coerceIn(-180.0, 180.0)
    return LatLng(latitude, longitude)
}

private class TerrainMapAlignmentStore(context: Context) {
    private val preferences = context.getSharedPreferences("terrain_map_alignments", Context.MODE_PRIVATE)

    fun contains(terrainKey: String): Boolean {
        val prefix = prefix(terrainKey)
        return preferences.getString("$prefix.terrainKey", null) == terrainKey &&
            preferences.contains("$prefix.latitude")
    }

    fun load(terrainKey: String): TerrainMapAlignment? {
        val prefix = prefix(terrainKey)
        if (preferences.getString("$prefix.terrainKey", null) != terrainKey) return null
        val latitude = preferences.getString("$prefix.latitude", null)?.toDoubleOrNull() ?: return null
        val longitude = preferences.getString("$prefix.longitude", null)?.toDoubleOrNull() ?: return null
        val widthScale = preferences.getFloat("$prefix.widthScale", 1f)
        val heightScale = preferences.getFloat("$prefix.heightScale", 1f)
        val bearing = preferences.getFloat("$prefix.bearing", 0f)
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return TerrainMapAlignment(
            center = LatLng(latitude, longitude),
            widthScale = widthScale.coerceIn(0.2f, 5f),
            heightScale = heightScale.coerceIn(0.2f, 5f),
            bearingDegrees = bearing.coerceIn(-180f, 180f),
        )
    }

    fun save(terrainKey: String, alignment: TerrainMapAlignment) {
        val prefix = prefix(terrainKey)
        preferences.edit()
            .putString("$prefix.terrainKey", terrainKey)
            .putString("$prefix.latitude", alignment.center.latitude.toString())
            .putString("$prefix.longitude", alignment.center.longitude.toString())
            .putFloat("$prefix.widthScale", alignment.widthScale)
            .putFloat("$prefix.heightScale", alignment.heightScale)
            .putFloat("$prefix.bearing", alignment.bearingDegrees)
            .apply()
    }

    fun clear(terrainKey: String) {
        val prefix = prefix(terrainKey)
        preferences.edit()
            .remove("$prefix.terrainKey")
            .remove("$prefix.latitude")
            .remove("$prefix.longitude")
            .remove("$prefix.widthScale")
            .remove("$prefix.heightScale")
            .remove("$prefix.bearing")
            .apply()
    }

    private fun prefix(terrainKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(terrainKey.toByteArray())
        return "alignment_" + digest.take(12).joinToString("") { "%02x".format(it) }
    }
}

private fun removeMapObject(value: Any) {
    when (value) {
        is Marker -> value.remove()
        is Polyline -> value.remove()
        is Polygon -> value.remove()
    }
}
