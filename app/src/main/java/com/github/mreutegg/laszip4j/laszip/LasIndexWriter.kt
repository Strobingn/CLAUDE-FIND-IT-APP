package com.github.mreutegg.laszip4j.laszip

import java.io.File
import java.io.RandomAccessFile

/**
 * Bridges two [LASindex] members that laszip4j only exposes in forms unusable from outside its
 * package.
 *
 * [LASindex.write]'s public, filename-based overload cannot actually write: its internal file-open
 * strips "b" from the C-style mode "wb", leaving "w" - not a legal [RandomAccessFile] mode, so
 * every call throws `IllegalArgumentException`. Separately, it closes the raw file handle directly
 * rather than the [ByteStreamOutFile] wrapping it, bypassing that wrapper's own `close()` and
 * leaving its last buffered writes (up to 256 KB) never flushed to disk. The package-private
 * overload that takes a stream sidesteps both.
 *
 * [LASindex.complete]'s public overload hardcodes `verbose = true`, printing several lines to
 * stderr on every index build; the package-private 3-argument overload lets that be turned off.
 */
object LasIndexWriter {
    @JvmStatic
    fun write(index: LASindex, destination: File): Boolean {
        val file = RandomAccessFile(destination, "rw")
        file.setLength(0)
        // ByteStreamOutFile.close() flushes the buffer and closes this same file; wrapping both
        // in separate `use` blocks would close it twice.
        return ByteStreamOutFile(file).use { stream -> index.write(stream) }
    }

    @JvmStatic
    fun complete(index: LASindex, minimumPoints: Int, maximumIntervals: Int) {
        index.complete(minimumPoints, maximumIntervals, false)
    }
}
