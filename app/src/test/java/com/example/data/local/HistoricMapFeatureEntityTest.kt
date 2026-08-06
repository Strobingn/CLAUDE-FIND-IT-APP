package com.example.data.local

import com.example.data.field.BoundaryVertex
import com.example.data.historicmap.HistoricMapFeature
import com.example.data.historicmap.MapFeatureType
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoricMapFeatureEntityTest {
    @Test
    fun featureGeometryAndTypeSurviveDatabaseMapping() {
        val feature = HistoricMapFeature(
            id = "feature-1",
            mapId = "map-1",
            type = MapFeatureType.STRUCTURE,
            points = listOf(
                BoundaryVertex(41.123, -73.987),
                BoundaryVertex(41.124, -73.986),
                BoundaryVertex(41.125, -73.985),
            ),
            confidence = 0.72f,
            note = "Cellar hole rim traced from 1880s plat",
            createdAtMillis = 1_700_000_000_000L,
        )

        val restored = feature.toEntity().toDomain()

        assertEquals(feature, restored)
    }

    @Test
    fun unknownTypeFallsBackToRoadRatherThanCrashing() {
        val entity = HistoricMapFeatureEntity(
            id = "feature-2",
            mapId = "map-1",
            type = "SOMETHING_NEW",
            pointsText = "",
            confidence = 0.5f,
            note = "",
            createdAtMillis = 0L,
        )

        assertEquals(MapFeatureType.ROAD, entity.toDomain().type)
    }
}
