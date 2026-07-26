package com.example.data.survey

import java.io.ByteArrayInputStream
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

enum class SurveyFormat { GPX, KML }

enum class SurveyGeometryType { POINT, LINE, POLYGON }

data class SurveyCoordinate(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
)

data class SurveyFeature(
    val id: String,
    val name: String?,
    val geometryType: SurveyGeometryType,
    val coordinates: List<SurveyCoordinate>,
)

data class SurveyLayer(
    val id: String = UUID.randomUUID().toString(),
    val terrainKey: String = "",
    val displayName: String,
    val format: SurveyFormat,
    val sourceXml: String,
    val importedAtMillis: Long = System.currentTimeMillis(),
    val features: List<SurveyFeature>,
)

object SurveyDocumentParser {
    private const val MAX_COORDINATES = 250_000

    fun parse(sourceXml: String, displayName: String = "Survey layer"): SurveyLayer {
        require(sourceXml.isNotBlank()) { "Survey file is empty" }
        require(!sourceXml.contains("<!DOCTYPE", ignoreCase = true)) {
            "Survey XML document types are not allowed"
        }
        require(!sourceXml.contains("<!ENTITY", ignoreCase = true)) {
            "Survey XML entities are not allowed"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // The Android XML provider does not implement these setters on all
            // releases. DOCTYPE and ENTITY are rejected above regardless.
            runCatching { isXIncludeAware = false }
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val document = ByteArrayInputStream(sourceXml.toByteArray(Charsets.UTF_8)).use {
            factory.newDocumentBuilder().parse(it)
        }
        val root = document.documentElement ?: error("Survey XML has no root element")
        val format = when (root.localName?.lowercase() ?: root.tagName.substringAfter(':').lowercase()) {
            "gpx" -> SurveyFormat.GPX
            "kml" -> SurveyFormat.KML
            else -> error("Only GPX and KML survey documents are supported")
        }
        val features = when (format) {
            SurveyFormat.GPX -> parseGpx(document)
            SurveyFormat.KML -> parseKml(document)
        }
        require(features.isNotEmpty()) { "Survey file contains no supported points, tracks, routes, or polygons" }
        require(features.sumOf { it.coordinates.size } <= MAX_COORDINATES) {
            "Survey layer exceeds the $MAX_COORDINATES coordinate safety limit"
        }
        return SurveyLayer(
            displayName = displayName,
            format = format,
            sourceXml = sourceXml,
            features = features,
        )
    }

    private fun parseGpx(document: org.w3c.dom.Document): List<SurveyFeature> = buildList {
        var featureIndex = 0
        document.elements("wpt").forEach { waypoint ->
            parseLatLonElement(waypoint)?.let { coordinate ->
                add(
                    SurveyFeature(
                        id = "gpx-wpt-${featureIndex++}",
                        name = waypoint.directChildText("name"),
                        geometryType = SurveyGeometryType.POINT,
                        coordinates = listOf(coordinate),
                    ),
                )
            }
        }
        document.elements("trkseg").forEach { segment ->
            val coordinates = segment.descendantElements("trkpt").mapNotNull(::parseLatLonElement)
            if (coordinates.size >= 2) {
                val track = segment.parentNode as? Element
                add(
                    SurveyFeature(
                        id = "gpx-trk-${featureIndex++}",
                        name = track?.directChildText("name"),
                        geometryType = SurveyGeometryType.LINE,
                        coordinates = coordinates,
                    ),
                )
            }
        }
        document.elements("rte").forEach { route ->
            val coordinates = route.descendantElements("rtept").mapNotNull(::parseLatLonElement)
            if (coordinates.size >= 2) {
                add(
                    SurveyFeature(
                        id = "gpx-rte-${featureIndex++}",
                        name = route.directChildText("name"),
                        geometryType = SurveyGeometryType.LINE,
                        coordinates = coordinates,
                    ),
                )
            }
        }
    }

    private fun parseKml(document: org.w3c.dom.Document): List<SurveyFeature> = buildList {
        var featureIndex = 0
        document.elements("Placemark").forEach { placemark ->
            val name = placemark.directChildText("name")
            placemark.descendantElements("Point").forEach { geometry ->
                val coordinates = geometry.firstDescendantText("coordinates").parseKmlCoordinates()
                coordinates.firstOrNull()?.let {
                    add(SurveyFeature("kml-point-${featureIndex++}", name, SurveyGeometryType.POINT, listOf(it)))
                }
            }
            placemark.descendantElements("LineString").forEach { geometry ->
                val coordinates = geometry.firstDescendantText("coordinates").parseKmlCoordinates()
                if (coordinates.size >= 2) {
                    add(SurveyFeature("kml-line-${featureIndex++}", name, SurveyGeometryType.LINE, coordinates))
                }
            }
            placemark.descendantElements("Polygon").forEach { geometry ->
                val ring = geometry.descendantElements("LinearRing").firstOrNull()
                val coordinates = ring?.firstDescendantText("coordinates").parseKmlCoordinates()
                if (coordinates.size >= 3) {
                    add(SurveyFeature("kml-polygon-${featureIndex++}", name, SurveyGeometryType.POLYGON, coordinates))
                }
            }
        }
    }

    private fun parseLatLonElement(element: Element): SurveyCoordinate? {
        val latitude = element.getAttribute("lat").toDoubleOrNull() ?: return null
        val longitude = element.getAttribute("lon").toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return SurveyCoordinate(
            latitude = latitude,
            longitude = longitude,
            elevationMeters = element.directChildText("ele")?.toDoubleOrNull(),
        )
    }

    private fun String?.parseKmlCoordinates(): List<SurveyCoordinate> =
        this.orEmpty().trim().split(Regex("\\s+")).mapNotNull { token ->
            val values = token.split(',')
            val longitude = values.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val latitude = values.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
            SurveyCoordinate(latitude, longitude, values.getOrNull(2)?.toDoubleOrNull())
        }

    private fun org.w3c.dom.Document.elements(name: String): List<Element> =
        getElementsByTagNameNS("*", name).asElements()

    private fun Element.descendantElements(name: String): List<Element> =
        getElementsByTagNameNS("*", name).asElements()

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun Element.directChildText(name: String): String? {
        for (index in 0 until childNodes.length) {
            val child = childNodes.item(index)
            if (child.nodeType == Node.ELEMENT_NODE &&
                (child.localName ?: child.nodeName.substringAfter(':')).equals(name, ignoreCase = true)
            ) {
                return child.textContent?.trim()?.takeIf(String::isNotEmpty)
            }
        }
        return null
    }

    private fun Element.firstDescendantText(name: String): String? =
        descendantElements(name).firstOrNull()?.textContent?.trim()
}
