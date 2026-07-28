package com.example.data

import com.github.mreutegg.laszip4j.laslib.SeekableLaszipReader
import com.github.mreutegg.laszip4j.laszip.LASzip.LASZIP_DECOMPRESS_SELECTIVE_CLASSIFICATION
import com.github.mreutegg.laszip4j.laszip.LASzip.LASZIP_DECOMPRESS_SELECTIVE_FLAGS
import com.github.mreutegg.laszip4j.laszip.LASzip.LASZIP_DECOMPRESS_SELECTIVE_Z
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val FIELDS = LASZIP_DECOMPRESS_SELECTIVE_Z or
    LASZIP_DECOMPRESS_SELECTIVE_CLASSIFICATION or LASZIP_DECOMPRESS_SELECTIVE_FLAGS

/**
 * Exercises the real laszip4j spatial-index classes against a real point-cloud file, rather than
 * mocking them, because two genuine library bugs surfaced only that way:
 *
 * 1. [com.github.mreutegg.laszip4j.laszip.LASinterval.merge_intervals] reads the first key of a
 *    `TreeMap` it built without checking whether that map is empty, which it is whenever every
 *    spatial cell's points already collapsed to one contiguous interval - the ordinary case for a
 *    single, spatially coherent flight strip like this fixture. [LazSpatialIndex] disables the
 *    interval-count cap entirely to avoid ever entering that method.
 * 2. [com.github.mreutegg.laszip4j.laszip.LASindex.write] opens its destination file with mode
 *    "w", which is not a legal [java.io.RandomAccessFile] mode and always throws. [LazSpatialIndex]
 *    routes around it via `LasIndexWriter`, which opens the file itself and calls the
 *    package-private stream-based overload directly.
 */
class LazSpatialIndexTest {
    @Test
    fun noIndexIsReportedBeforeOneIsBuilt() {
        val file = LasFixture.write(File.createTempFile("laz_index", ".las"), pointCount = 20_000)
        try {
            assertFalse(LazSpatialIndex.exists(file))
            assertNull(LazSpatialIndex.load(file))
        } finally {
            file.delete()
            LazSpatialIndex.indexFileFor(file).delete()
        }
    }

    @Test
    fun buildingProducesAUsableIndexAndIsIdempotent() {
        val file = LasFixture.write(File.createTempFile("laz_index", ".las"), pointCount = 200_000)
        try {
            assertTrue("index should build successfully", LazSpatialIndex.build(file))
            assertTrue(LazSpatialIndex.exists(file))
            assertNotNull(LazSpatialIndex.load(file))

            val sidecar = LazSpatialIndex.indexFileFor(file)
            assertTrue(sidecar.length() > 0L)
            assertFalse(
                "no .tmp file should remain after a successful build",
                File(sidecar.parentFile, "${sidecar.name}.tmp").exists(),
            )

            // Rebuilding an already-current index is a cheap no-op, not a second full pass.
            val sidecarModifiedAt = sidecar.lastModified()
            assertTrue(LazSpatialIndex.build(file))
            assertEquals(sidecarModifiedAt, sidecar.lastModified())
        } finally {
            file.delete()
            LazSpatialIndex.indexFileFor(file).delete()
        }
    }

    @Test
    fun aStaleIndexOlderThanItsDataIsTreatedAsAbsent() {
        val file = LasFixture.write(File.createTempFile("laz_index", ".las"), pointCount = 20_000)
        try {
            assertTrue(LazSpatialIndex.build(file))
            assertTrue(LazSpatialIndex.exists(file))

            // Touch the data file to a later timestamp, simulating the source being replaced.
            file.setLastModified(System.currentTimeMillis() + 60_000)
            assertFalse(LazSpatialIndex.exists(file))
            assertNull(LazSpatialIndex.load(file))
        } finally {
            file.delete()
            LazSpatialIndex.indexFileFor(file).delete()
        }
    }

    @Test
    fun indexedAndUnindexedReadsOfTheSameRectangleReturnIdenticalPoints() {
        val file = LasFixture.write(File.createTempFile("laz_index", ".las"), pointCount = 300_000)
        try {
            assertTrue(LazSpatialIndex.build(file))
            val index = requireNotNull(LazSpatialIndex.load(file))

            // A small rectangle well inside the fixture's 2000x2000 extent - the case a zoomed
            // "Refine" actually issues.
            val rectangle = doubleArrayOf(900.0, 900.0, 1100.0, 1100.0)

            fun readRectangle(useIndex: Boolean): List<Triple<Double, Double, Double>> {
                val points = mutableListOf<Triple<Double, Double, Double>>()
                SeekableLaszipReader.open(file, FIELDS)!!.use { reader ->
                    if (useIndex) reader.set_index(index)
                    reader.inside_rectangle(rectangle[0], rectangle[1], rectangle[2], rectangle[3])
                    while (reader.read_point()) {
                        points += Triple(reader.get_x(), reader.get_y(), reader.get_z())
                    }
                }
                return points
            }

            val withoutIndex = readRectangle(useIndex = false).sortedBy { it.first }
            val withIndex = readRectangle(useIndex = true).sortedBy { it.first }

            assertTrue("the rectangle should actually contain points", withoutIndex.isNotEmpty())
            assertEquals(withoutIndex, withIndex)
            for ((x, y, _) in withIndex) {
                assertTrue(x in rectangle[0]..rectangle[2])
                assertTrue(y in rectangle[1]..rectangle[3])
            }
        } finally {
            file.delete()
            LazSpatialIndex.indexFileFor(file).delete()
        }
    }

    @Test
    fun buildReturnsFalseRatherThanThrowingForAMissingFile() {
        val missing = File.createTempFile("laz_index_missing", ".las")
        missing.delete()
        assertFalse(LazSpatialIndex.build(missing))
        assertFalse(LazSpatialIndex.exists(missing))
    }
}
