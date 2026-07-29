package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoricMapOverlayRepositoryTest {
    private lateinit var context: Context
    private lateinit var storeDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("historic_map_overlays", Context.MODE_PRIVATE).edit().clear().commit()
        storeDirectory = File(context.cacheDir, "historic-maps-test").also { it.deleteRecursively() }
    }

    private fun sourceImageUri(widthPx: Int = 200, heightPx: Int = 100): Uri {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val source = File(context.cacheDir, "source-${System.nanoTime()}.png")
        FileOutputStream(source).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return Uri.fromFile(source)
    }

    @Test
    fun importedOverlayIsPersistedWithDefaultAlignmentAndAspectRatio() {
        val repository = HistoricMapOverlayRepository(context, storeDirectory)

        val imported = repository.importImage(
            context = context,
            uri = sourceImageUri(widthPx = 200, heightPx = 100),
            requestedName = "1890 Plat Map.png",
            defaultLatitude = 41.5,
            defaultLongitude = -74.0,
            defaultBaseWidthMeters = 300f,
        )

        assertEquals(41.5, imported.latitude, 1e-9)
        assertEquals(-74.0, imported.longitude, 1e-9)
        assertEquals(2f, imported.aspectRatio, 1e-6f)
        assertEquals(300f, imported.baseWidthMeters, 1e-6f)
        assertEquals(1f, imported.widthScale, 1e-6f)
        assertTrue(imported.visible)
        assertTrue(imported.file.exists())

        val reopened = HistoricMapOverlayRepository(context, storeDirectory)
        assertEquals(listOf(imported.id), reopened.list().map { it.id })
    }

    @Test
    fun updateAlignmentPersistsAcrossRepositoryInstances() {
        val repository = HistoricMapOverlayRepository(context, storeDirectory)
        val imported = repository.importImage(
            context = context,
            uri = sourceImageUri(),
            requestedName = "map.png",
            defaultLatitude = 0.0,
            defaultLongitude = 0.0,
            defaultBaseWidthMeters = 100f,
        )

        repository.update(
            imported.copy(
                latitude = 10.0,
                longitude = 20.0,
                widthScale = 2f,
                bearingDegrees = 45f,
                opacity = 0.4f,
                visible = false,
            ),
        )

        val reloaded = HistoricMapOverlayRepository(context, storeDirectory).list().single()
        assertEquals(10.0, reloaded.latitude, 1e-9)
        assertEquals(20.0, reloaded.longitude, 1e-9)
        assertEquals(2f, reloaded.widthScale, 1e-6f)
        assertEquals(45f, reloaded.bearingDegrees, 1e-6f)
        assertEquals(0.4f, reloaded.opacity, 1e-6f)
        assertFalse(reloaded.visible)
    }

    @Test
    fun deleteRemovesFileAndPersistedRecord() {
        val repository = HistoricMapOverlayRepository(context, storeDirectory)
        val imported = repository.importImage(
            context = context,
            uri = sourceImageUri(),
            requestedName = "map.png",
            defaultLatitude = 0.0,
            defaultLongitude = 0.0,
            defaultBaseWidthMeters = 100f,
        )

        repository.delete(imported)

        assertFalse(imported.file.exists())
        assertTrue(repository.list().isEmpty())
        assertNull(HistoricMapOverlayRepository(context, storeDirectory).list().firstOrNull())
    }

    @Test
    fun anEmptySourceIsRejectedAndLeavesNothingBehind() {
        val repository = HistoricMapOverlayRepository(context, storeDirectory)
        val empty = File(context.cacheDir, "empty-${System.nanoTime()}.png").apply { writeBytes(ByteArray(0)) }

        val failure = runCatching {
            repository.importImage(context, Uri.fromFile(empty), "empty.png", 0.0, 0.0, 100f)
        }

        assertTrue(failure.isFailure)
        assertTrue("no record should be stored", repository.list().isEmpty())
        assertTrue(
            "no partial file should remain",
            storeDirectory.listFiles().orEmpty().none { it.name.startsWith("empty") },
        )
    }

    // The undecodable-image guard in importImage is deliberately not covered here: Robolectric's
    // BitmapFactory shadow does not parse file contents, so decodeFile returns a synthetic bitmap
    // with positive dimensions for any path and the guard can never trip. Verifying it needs an
    // instrumented test on a real device.

    @Test
    fun deletingOneOverlayLeavesTheOthersIntact() {
        val repository = HistoricMapOverlayRepository(context, storeDirectory)
        val first = repository.importImage(context, sourceImageUri(), "first.png", 1.0, 2.0, 100f)
        val second = repository.importImage(context, sourceImageUri(), "second.png", 3.0, 4.0, 200f)

        repository.delete(first)

        val remaining = HistoricMapOverlayRepository(context, storeDirectory).list()
        assertEquals(listOf(second.id), remaining.map { it.id })
        assertEquals(3.0, remaining.single().latitude, 1e-9)
        assertTrue(second.file.exists())
    }

    @Test
    fun distinctFilesWithSameRequestedNameAreNotOverwritten() {
        val repository = HistoricMapOverlayRepository(context, storeDirectory)
        val first = repository.importImage(context, sourceImageUri(), "map.png", 0.0, 0.0, 100f)
        val second = repository.importImage(context, sourceImageUri(), "map.png", 0.0, 0.0, 100f)

        assertTrue(first.file.exists())
        assertTrue(second.file.exists())
        assertFalse(first.file.absolutePath == second.file.absolutePath)
        assertEquals(2, repository.list().size)
    }
}
