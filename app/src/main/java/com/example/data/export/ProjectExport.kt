package com.example.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.TargetSignal
import com.example.data.survey.SurveyGeometryType
import com.example.data.survey.SurveyLayer
import com.example.geospatial.GeoSpatialLibrary
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import com.example.geospatial.MeasurementFormat

const val PROJECT_REPORT_SCHEMA_VERSION = 1

/** Sticky field-ethics line used on PDF footers and site-package summaries. */
const val DEFAULT_ETHICS_FOOTER =
    "Ethics: only detect on land you have permission to search. " +
        "LiDAR does not prove buried metal or ownership. " +
        "Verify laws and access before digging."

data class ProjectExportSnapshot(
    val projectName: String,
    val terrainKey: String,
    val summary: String,
    val metadata: GeoSpatialLibrary.GeoSpatialMetadata,
    val terrainBitmap: Bitmap,
    val visualizationLabel: String,
    val targets: List<TargetSignal>,
    val surveyLayers: List<SurveyLayer>,
    val generatedAtMillis: Long = System.currentTimeMillis(),
    /** Open dig / excavation logs included in this export. */
    val digCount: Int = 0,
    /** Survey boundary polygons attached to the project. */
    val boundaryCount: Int = 0,
    /** Recorded breadcrumb trails attached to the project. */
    val trailCount: Int = 0,
    /** Sticky ethics disclaimer drawn on every PDF page footer. */
    val ethicsFooter: String = DEFAULT_ETHICS_FOOTER,
    /** Optional ground-quality / site scorecard lines for the metadata page. */
    val scorecardLines: List<String> = emptyList(),
)

data class ProjectExportFiles(
    val fileStem: String,
    val terrainPng: ByteArray,
    val reportPdf: ByteArray,
)

object ProjectExportRenderer {
    private const val MIN_EXPORT_WIDTH = 1200
    private const val IMAGE_HEADER_HEIGHT = 190
    private const val IMAGE_FOOTER_HEIGHT = 190
    private const val PDF_WIDTH = 595
    private const val PDF_HEIGHT = 842
    private const val PDF_MARGIN = 40f

    fun build(snapshot: ProjectExportSnapshot): ProjectExportFiles {
        val annotated = renderAnnotatedTerrain(snapshot)
        return ProjectExportFiles(
            fileStem = safeFileStem(snapshot.projectName),
            terrainPng = annotated.toPngBytes(),
            reportPdf = renderPdf(snapshot, annotated),
        )
    }

    fun renderAnnotatedTerrain(snapshot: ProjectExportSnapshot): Bitmap {
        require(!snapshot.terrainBitmap.isRecycled) { "Terrain bitmap is unavailable" }
        val scale = max(1f, MIN_EXPORT_WIDTH.toFloat() / snapshot.terrainBitmap.width.coerceAtLeast(1))
        val imageWidth = (snapshot.terrainBitmap.width * scale).toInt()
        val imageHeight = (snapshot.terrainBitmap.height * scale).toInt()
        val output = Bitmap.createBitmap(
            imageWidth,
            IMAGE_HEADER_HEIGHT + imageHeight + IMAGE_FOOTER_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(28, 25, 24))
        val titlePaint = paint(Color.WHITE, 42f, bold = true)
        val bodyPaint = paint(Color.rgb(220, 214, 205), 24f)
        val mutedPaint = paint(Color.rgb(174, 166, 157), 21f)
        canvas.drawText(snapshot.projectName.safeText(), 34f, 58f, titlePaint)
        canvas.drawText(
            "${snapshot.visualizationLabel.safeText()} | ${snapshot.metadata.columns} x " +
                "${snapshot.metadata.rows} | ${formatMeters(snapshot.metadata.resolutionMeters)} per cell",
            34f,
            100f,
            bodyPaint,
        )
        canvas.drawText(metadataCoordinateLabel(snapshot.metadata), 34f, 140f, mutedPaint)
        canvas.drawText(formatTimestamp(snapshot.generatedAtMillis), 34f, 174f, mutedPaint)

        val imageTop = IMAGE_HEADER_HEIGHT.toFloat()
        val destination = android.graphics.RectF(
            0f,
            imageTop,
            imageWidth.toFloat(),
            imageTop + imageHeight,
        )
        canvas.drawBitmap(snapshot.terrainBitmap, null, destination, null)
        drawSurveyFeatures(canvas, snapshot, scale, imageTop)
        drawTargets(canvas, snapshot.targets, imageWidth.toFloat(), imageHeight.toFloat(), imageTop)

        val footerTop = imageTop + imageHeight
        val accent = Color.rgb(0, 210, 224)
        val marker = Color.rgb(255, 179, 0)
        canvas.drawCircle(48f, footerTop + 48f, 13f, paint(marker, 20f, bold = true))
        canvas.drawText("Saved target", 76f, footerTop + 57f, bodyPaint)
        canvas.drawLine(280f, footerTop + 48f, 340f, footerTop + 48f, paint(accent, 20f).apply {
            strokeWidth = 7f
        })
        canvas.drawText("Survey layer", 354f, footerTop + 57f, bodyPaint)
        canvas.drawText(
            "${snapshot.targets.size} targets | ${snapshot.surveyLayers.size} survey layers",
            34f,
            footerTop + 105f,
            bodyPaint,
        )
        canvas.drawText(
            "LiDAR ranks surface morphology and context; it does not identify buried metal or depth.",
            34f,
            footerTop + 150f,
            mutedPaint,
        )
        return output
    }

    fun renderComparisonPng(
        left: Bitmap,
        leftLabel: String,
        right: Bitmap,
        rightLabel: String,
        projectName: String,
    ): ByteArray {
        require(!left.isRecycled && !right.isRecycled) { "Comparison layers are unavailable" }
        val paneWidth = max(max(left.width, right.width), 600)
        val paneHeight = max(
            600,
            max(
                (left.height * (paneWidth / left.width.toFloat())).toInt(),
                (right.height * (paneWidth / right.width.toFloat())).toInt(),
            ),
        )
        val header = 130
        val output = Bitmap.createBitmap(paneWidth * 2, paneHeight + header, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(25, 23, 22))
        canvas.drawText(projectName.safeText(), 28f, 45f, paint(Color.WHITE, 34f, bold = true))
        canvas.drawText(leftLabel.safeText(), 28f, 100f, paint(Color.rgb(0, 210, 224), 28f, bold = true))
        canvas.drawText(
            rightLabel.safeText(),
            paneWidth + 28f,
            100f,
            paint(Color.rgb(255, 179, 0), 28f, bold = true),
        )
        val leftDestination = fitContainRect(left, 0, header, paneWidth, paneHeight)
        val rightDestination = fitContainRect(right, paneWidth, header, paneWidth, paneHeight)
        canvas.drawBitmap(
            left,
            null,
            leftDestination,
            null,
        )
        canvas.drawBitmap(
            right,
            null,
            rightDestination,
            null,
        )
        canvas.drawRect(
            paneWidth - 2f,
            header.toFloat(),
            paneWidth + 2f,
            output.height.toFloat(),
            paint(Color.WHITE, 1f),
        )
        return output.toPngBytes()
    }

    private fun fitContainRect(
        bitmap: Bitmap,
        paneLeft: Int,
        paneTop: Int,
        paneWidth: Int,
        paneHeight: Int,
    ): android.graphics.Rect {
        val scale = min(
            paneWidth / bitmap.width.toFloat(),
            paneHeight / bitmap.height.toFloat(),
        )
        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()
        val left = paneLeft + (paneWidth - width) / 2
        val top = paneTop + (paneHeight - height) / 2
        return android.graphics.Rect(left, top, left + width, top + height)
    }

    private fun renderPdf(snapshot: ProjectExportSnapshot, annotated: Bitmap): ByteArray {
        val document = PdfDocument()
        var pageNumber = 0

        fun newPage(title: String): Pair<PdfDocument.Page, Canvas> {
            pageNumber++
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create(),
            )
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawRect(0f, 0f, PDF_WIDTH.toFloat(), 72f, paint(Color.rgb(55, 47, 43), 1f))
            page.canvas.drawText(title.safeText(), PDF_MARGIN, 45f, paint(Color.WHITE, 22f, bold = true))
            page.canvas.drawText(
                "Find It field report | schema $PROJECT_REPORT_SCHEMA_VERSION",
                PDF_MARGIN,
                65f,
                paint(Color.rgb(220, 214, 205), 9f),
            )
            return page to page.canvas
        }

        fun finishPage(page: PdfDocument.Page, canvas: Canvas) {
            // Sticky ethics disclaimer above the page chrome so every handoff page carries it.
            canvas.drawLine(PDF_MARGIN, 788f, PDF_WIDTH - PDF_MARGIN, 788f, paint(Color.LTGRAY, 1f))
            drawWrappedText(
                canvas,
                snapshot.ethicsFooter.safeText().ifBlank { DEFAULT_ETHICS_FOOTER },
                PDF_MARGIN,
                800f,
                PDF_WIDTH - PDF_MARGIN * 2,
                paint(Color.DKGRAY, 7f),
                9f,
            )
            canvas.drawText(
                "Page $pageNumber | Generated ${formatTimestamp(snapshot.generatedAtMillis)}",
                PDF_MARGIN,
                829f,
                paint(Color.DKGRAY, 8f),
            )
            document.finishPage(page)
        }

        run {
            val (page, canvas) = newPage(snapshot.projectName)
            var y = 98f
            y = drawWrappedText(
                canvas,
                snapshot.summary.safeText(),
                PDF_MARGIN,
                y,
                PDF_WIDTH - PDF_MARGIN * 2,
                paint(Color.DKGRAY, 10f),
                14f,
            ) + 10f
            val availableHeight = 710f - y
            val imageScale = min(
                (PDF_WIDTH - PDF_MARGIN * 2) / annotated.width.toFloat(),
                availableHeight / annotated.height.toFloat(),
            )
            val imageWidth = annotated.width * imageScale
            val imageHeight = annotated.height * imageScale
            canvas.drawBitmap(
                annotated,
                null,
                android.graphics.RectF(
                    (PDF_WIDTH - imageWidth) / 2f,
                    y,
                    (PDF_WIDTH + imageWidth) / 2f,
                    y + imageHeight,
                ),
                null,
            )
            val noteY = y + imageHeight + 18f
            drawWrappedText(
                canvas,
                "Interpretation notice: terrain morphology is screening evidence. Field verification, " +
                    "permission, and source-quality review remain required.",
                PDF_MARGIN,
                noteY,
                PDF_WIDTH - PDF_MARGIN * 2,
                paint(Color.DKGRAY, 9f),
                12f,
            )
            finishPage(page, canvas)
        }

        run {
            val (page, canvas) = newPage("Project metadata")
            var y = 100f
            val labelPaint = paint(Color.rgb(74, 61, 53), 10f, bold = true)
            val valuePaint = paint(Color.DKGRAY, 10f)
            val metadataRows = listOf(
                "Project" to snapshot.projectName,
                "Terrain source" to snapshot.terrainKey,
                "Coordinate system" to snapshot.metadata.crs,
                "Datum" to snapshot.metadata.datum,
                "Bounds" to metadataCoordinateLabel(snapshot.metadata),
                "Raster" to "${snapshot.metadata.columns} x ${snapshot.metadata.rows}",
                "Resolution" to "${formatMeters(snapshot.metadata.resolutionMeters)} per cell",
                "Visualization" to snapshot.visualizationLabel,
                "Saved targets" to snapshot.targets.size.toString(),
                "Survey layers" to snapshot.surveyLayers.size.toString(),
                "Dig logs" to snapshot.digCount.toString(),
                "Survey boundaries" to snapshot.boundaryCount.toString(),
                "Trail tracks" to snapshot.trailCount.toString(),
            )
            metadataRows.forEach { (label, value) ->
                canvas.drawText(label.safeText(), PDF_MARGIN, y, labelPaint)
                y = drawWrappedText(
                    canvas,
                    value.safeText(),
                    170f,
                    y,
                    PDF_WIDTH - 170f - PDF_MARGIN,
                    valuePaint,
                    13f,
                ) + 8f
            }
            if (snapshot.scorecardLines.isNotEmpty()) {
                y += 10f
                canvas.drawText(
                    "Ground / site scorecard",
                    PDF_MARGIN,
                    y,
                    paint(Color.rgb(74, 61, 53), 12f, bold = true),
                )
                y += 16f
                snapshot.scorecardLines.forEach { line ->
                    if (y > 760f) return@forEach
                    y = drawWrappedText(
                        canvas,
                        "- ${line.safeText()}",
                        PDF_MARGIN,
                        y,
                        PDF_WIDTH - PDF_MARGIN * 2,
                        valuePaint,
                        12f,
                    ) + 3f
                }
            }
            finishPage(page, canvas)
        }

        var targetPage: PdfDocument.Page? = null
        var targetCanvas: Canvas? = null
        var y = 0f
        fun startTargetPage() {
            val (page, canvas) = newPage("Saved targets")
            targetPage = page
            targetCanvas = canvas
            y = 102f
            canvas.drawText(
                "Type / source",
                PDF_MARGIN,
                y,
                paint(Color.rgb(74, 61, 53), 9f, bold = true),
            )
            canvas.drawText("Grid / lat, lon", 245f, y, paint(Color.rgb(74, 61, 53), 9f, bold = true))
            canvas.drawText("Status", 445f, y, paint(Color.rgb(74, 61, 53), 9f, bold = true))
            y += 14f
        }
        startTargetPage()
        if (snapshot.targets.isEmpty()) {
            targetCanvas?.drawText("No targets saved for this terrain.", PDF_MARGIN, y + 20f, paint(Color.DKGRAY, 10f))
        } else {
            snapshot.targets.forEachIndexed { index, target ->
                if (y > 750f) {
                    finishPage(requireNotNull(targetPage), requireNotNull(targetCanvas))
                    startTargetPage()
                }
                val canvas = requireNotNull(targetCanvas)
                canvas.drawLine(PDF_MARGIN, y, PDF_WIDTH - PDF_MARGIN, y, paint(Color.LTGRAY, 1f))
                canvas.drawText(
                    "${index + 1}. ${target.metalType.label.safeText()}",
                    PDF_MARGIN,
                    y + 17f,
                    paint(Color.DKGRAY, 9f, bold = true),
                )
                canvas.drawText(target.source.name.safeText(), PDF_MARGIN, y + 32f, paint(Color.GRAY, 8f))
                canvas.drawText(
                    "Grid ${target.gridX.toInt()}, ${target.gridY.toInt()}",
                    245f,
                    y + 17f,
                    paint(Color.DKGRAY, 9f),
                )
                // Explicit lat/lon line when present so site-package PDFs hand off coordinates cleanly.
                canvas.drawText(targetCoordinate(target), 245f, y + 32f, paint(Color.GRAY, 8f))
                canvas.drawText(target.status.safeText(), 445f, y + 17f, paint(Color.DKGRAY, 9f))
                canvas.drawText(target.outcome.label.safeText(), 445f, y + 32f, paint(Color.GRAY, 8f))
                var rowHeight = 45f
                if (target.notes.isNotBlank()) {
                    val noteBottom = drawWrappedText(
                        canvas,
                        "Notes: ${target.notes.safeText()}",
                        PDF_MARGIN,
                        y + 48f,
                        PDF_WIDTH - PDF_MARGIN * 2,
                        paint(Color.DKGRAY, 8f),
                        11f,
                    )
                    rowHeight = noteBottom - y + 5f
                }
                y += rowHeight
            }
        }
        finishPage(requireNotNull(targetPage), requireNotNull(targetCanvas))

        run {
            val (page, canvas) = newPage("Survey layers and provenance")
            var surveyY = 102f
            if (snapshot.surveyLayers.isEmpty()) {
                canvas.drawText("No GPX or KML survey layers attached.", PDF_MARGIN, surveyY, paint(Color.DKGRAY, 10f))
            } else {
                snapshot.surveyLayers.forEachIndexed { index, layer ->
                    canvas.drawText(
                        "${index + 1}. ${layer.displayName.safeText()}",
                        PDF_MARGIN,
                        surveyY,
                        paint(Color.DKGRAY, 10f, bold = true),
                    )
                    surveyY += 16f
                    canvas.drawText(
                        "${layer.format.name} | ${layer.features.size} features | " +
                            "${layer.features.sumOf { it.coordinates.size }} coordinates",
                        PDF_MARGIN + 14f,
                        surveyY,
                        paint(Color.GRAY, 9f),
                    )
                    surveyY += 24f
                }
            }
            surveyY += 16f
            canvas.drawText("Data-integrity notes", PDF_MARGIN, surveyY, paint(Color.rgb(74, 61, 53), 12f, bold = true))
            surveyY += 22f
            val notes = listOf(
                "Original source files are not modified by this export.",
                "Coordinates appear only when the terrain or saved target has real georeferencing.",
                "Local-grid measurements remain local and are not presented as latitude/longitude.",
                "LiDAR does not directly identify metal type, artifact age, or target depth.",
                "Processing and report schema versions should be retained with archived exports.",
            )
            notes.forEach { note ->
                surveyY = drawWrappedText(
                    canvas,
                    "- $note",
                    PDF_MARGIN,
                    surveyY,
                    PDF_WIDTH - PDF_MARGIN * 2,
                    paint(Color.DKGRAY, 9f),
                    13f,
                ) + 4f
            }
            finishPage(page, canvas)
        }

        return ByteArrayOutputStream().use { output ->
            document.writeTo(output)
            document.close()
            output.toByteArray()
        }
    }

    private fun drawSurveyFeatures(
        canvas: Canvas,
        snapshot: ProjectExportSnapshot,
        scale: Float,
        imageTop: Float,
    ) {
        if (snapshot.metadata.bounds == null) return
        val linePaint = paint(Color.rgb(0, 210, 224), 1f).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }
        val fillPaint = paint(Color.argb(55, 0, 210, 224), 1f).apply {
            style = Paint.Style.FILL
        }
        snapshot.surveyLayers.flatMap { it.features }.forEach { feature ->
            val points = feature.coordinates.mapNotNull { coordinate ->
                GeoSpatialLibrary.geographicToGrid(
                    coordinate.latitude,
                    coordinate.longitude,
                    snapshot.metadata,
                )?.let { (xPercent, yPercent) ->
                    android.graphics.PointF(
                        xPercent / 100f * snapshot.terrainBitmap.width * scale,
                        imageTop + yPercent / 100f * snapshot.terrainBitmap.height * scale,
                    )
                }
            }
            when (feature.geometryType) {
                SurveyGeometryType.POINT -> points.firstOrNull()?.let {
                    canvas.drawCircle(it.x, it.y, 12f, fillPaint)
                    canvas.drawCircle(it.x, it.y, 12f, linePaint)
                }
                SurveyGeometryType.LINE -> if (points.size >= 2) {
                    canvas.drawPath(points.toPath(close = false), linePaint)
                }
                SurveyGeometryType.POLYGON -> if (points.size >= 3) {
                    val path = points.toPath(close = true)
                    canvas.drawPath(path, fillPaint)
                    canvas.drawPath(path, linePaint)
                }
            }
        }
    }

    private fun drawTargets(
        canvas: Canvas,
        targets: List<TargetSignal>,
        imageWidth: Float,
        imageHeight: Float,
        imageTop: Float,
    ) {
        val markerPaint = paint(Color.rgb(255, 179, 0), 1f)
        val outlinePaint = paint(Color.BLACK, 1f).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val labelPaint = paint(Color.BLACK, 18f, bold = true).apply { textAlign = Paint.Align.CENTER }
        targets.forEachIndexed { index, target ->
            val x = target.gridX.coerceIn(0f, 100f) / 100f * imageWidth
            val y = imageTop + target.gridY.coerceIn(0f, 100f) / 100f * imageHeight
            canvas.drawCircle(x, y, 16f, markerPaint)
            canvas.drawCircle(x, y, 16f, outlinePaint)
            canvas.drawText((index + 1).toString(), x, y + 6f, labelPaint)
        }
    }

    private fun List<android.graphics.PointF>.toPath(close: Boolean): Path = Path().apply {
        firstOrNull()?.let { moveTo(it.x, it.y) }
        drop(1).forEach { lineTo(it.x, it.y) }
        if (close) close()
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
    ): Float {
        var y = startY
        var line = ""
        text.safeText().split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = candidate
            } else {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
                line = word
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun Bitmap.toPngBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        check(compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encoding failed" }
        output.toByteArray()
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun metadataCoordinateLabel(metadata: GeoSpatialLibrary.GeoSpatialMetadata): String {
        val bounds = metadata.bounds ?: return "Local grid - geographic coordinates unavailable"
        return String.format(
            Locale.US,
            "%.6f, %.6f to %.6f, %.6f | %s",
            bounds.minLat,
            bounds.minLon,
            bounds.maxLat,
            bounds.maxLon,
            metadata.crs.safeText(),
        )
    }

    private fun targetCoordinate(target: TargetSignal): String =
        if (target.latitude != null && target.longitude != null) {
            String.format(Locale.US, "lat %.6f, lon %.6f", target.latitude, target.longitude)
        } else {
            "Local grid (no lat/lon)"
        }

    private fun formatMeters(value: Double): String = MeasurementFormat.length(value)

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).format(Date(timestamp))

    private fun safeFileStem(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(60)
            .ifBlank { "find-it-project" }

    private fun String.safeText(): String = replace('\u2013', '-')
        .replace('\u2014', '-')
        .replace('\u2011', '-')
        .replace('\u00b7', '|')
        .replace('\n', ' ')
}
