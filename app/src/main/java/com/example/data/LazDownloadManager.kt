package com.example.data

import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.math.min

/**
 * Downloads LAZ/LAS files into persistent app storage.
 *
 * Bytes are streamed directly to a resumable temporary file and atomically promoted only after the
 * complete download succeeds, so multi-gigabyte datasets never need to fit in the app heap. Large
 * NYS/USGS transfers occasionally close a mobile socket mid-stream; retryable failures retain the
 * partial bytes and continue with an HTTP Range request instead of discarding the whole download.
 */
class LazDownloadManager {

    companion object {
        const val MAX_IMPORT_BYTES: Long = 10L * 1024L * 1024L * 1024L
        private const val MAX_REDIRECTS = 5
        private const val MAX_ATTEMPTS = 5
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 120_000
        private const val BUFFER_BYTES = 1024 * 1024
        private const val PROGRESS_STEP_BYTES = 4L * 1024L * 1024L
        private const val RETRY_BASE_DELAY_MS = 1_000L
        private const val RETRY_MAX_DELAY_MS = 8_000L

        /** Shared by LAS and LAZ; LAZ is LAS with compressed point data. */
        private val LIDAR_SIGNATURE = "LASF".toByteArray(Charsets.US_ASCII)
    }

    fun download(
        sourceUrl: String,
        destinationDirectory: File,
        progress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
        shouldContinue: () -> Boolean = { !Thread.currentThread().isInterrupted },
    ): File {
        destinationDirectory.mkdirs()
        require(destinationDirectory.isDirectory) { "Unable to create LAZ storage directory" }

        val originalUrl = validateRemoteUrl(URL(sourceUrl.trim()))
        var destination: File? = runCatching {
            val urlName = originalUrl.path.substringAfterLast('/').ifBlank { "lidar-download.laz" }
            uniqueDestination(destinationDirectory, sanitizeAndValidateName(urlName))
        }.getOrNull()
        var partial: File? = destination?.let { File(destinationDirectory, ".${it.name}.part") }
        var expectedTotal = -1L
        var lastFailure: Throwable? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            if (!shouldContinue()) throw CancellationException("LAZ download cancelled")

            var connection: HttpURLConnection? = null
            try {
                val existingBytes = partial?.takeIf(File::isFile)?.length()?.coerceAtLeast(0L) ?: 0L
                val opened = openFollowingRedirects(originalUrl, existingBytes)
                connection = opened.connection
                val status = opened.status

                if (status == HTTP_RANGE_NOT_SATISFIABLE) {
                    val serverLength = parseUnsatisfiedRangeTotal(connection.getHeaderField("Content-Range"))
                    val completedPartial = partial
                    val completedDestination = destination
                    if (existingBytes > 0L && serverLength == existingBytes &&
                        completedPartial != null && completedDestination != null
                    ) {
                        // Same gate as the streaming path: a retained partial holding the wrong
                        // payload must not be promoted just because its length matches.
                        validatePointCloudSignature(completedPartial)
                        promote(completedPartial, completedDestination)
                        progress?.invoke(existingBytes, existingBytes)
                        return completedDestination
                    }
                    completedPartial?.delete()
                    throw RetryableHttpException(status, "Server rejected the saved download range")
                }

                if (status == 408 || status == 425 || status == 429 || status in 500..599) {
                    throw RetryableHttpException(status, "Temporary download server error HTTP $status")
                }
                check(status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_PARTIAL) {
                    "Server returned HTTP $status"
                }

                if (destination == null || partial == null) {
                    val responseName = contentDispositionFileName(connection.getHeaderField("Content-Disposition"))
                    val urlName = opened.url.path.substringAfterLast('/').ifBlank { "lidar-download.laz" }
                    val finalName = sanitizeAndValidateName(responseName ?: urlName)
                    destination = uniqueDestination(destinationDirectory, finalName)
                    partial = File(destinationDirectory, ".${requireNotNull(destination).name}.part")
                }

                val target = requireNotNull(destination)
                val workingFile = requireNotNull(partial)
                var resumeFrom = workingFile.takeIf(File::isFile)?.length()?.coerceAtLeast(0L) ?: 0L
                var append = status == HttpURLConnection.HTTP_PARTIAL && resumeFrom > 0L

                if (status == HttpURLConnection.HTTP_PARTIAL) {
                    val range = parseContentRange(connection.getHeaderField("Content-Range"))
                        ?: throw IOException("Partial response had no valid Content-Range")
                    if (range.start != resumeFrom) {
                        workingFile.delete()
                        throw RetryableHttpException(
                            status,
                            "Server resumed at byte ${range.start}, expected $resumeFrom",
                        )
                    }
                    expectedTotal = range.total
                } else {
                    // Some servers ignore Range and return a complete 200 response. Restart this
                    // attempt from byte zero rather than appending a second copy to the partial file.
                    if (resumeFrom > 0L) {
                        FileOutputStream(workingFile, false).use { }
                        resumeFrom = 0L
                    }
                    append = false
                    expectedTotal = connection.contentLengthLong
                }

                check(expectedTotal <= 0L || expectedTotal <= MAX_IMPORT_BYTES) {
                    "LAZ file exceeds the 10 GB import limit"
                }

                var downloaded = resumeFrom
                var lastReported = downloaded
                progress?.invoke(downloaded, expectedTotal)

                connection.inputStream.use { rawInput ->
                    rawInput.buffered(BUFFER_BYTES).use { input ->
                        FileOutputStream(workingFile, append).buffered(BUFFER_BYTES).use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                if (!shouldContinue()) throw CancellationException("LAZ download cancelled")
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue

                                downloaded += count
                                check(downloaded <= MAX_IMPORT_BYTES) {
                                    "LAZ file exceeds the 10 GB import limit"
                                }
                                output.write(buffer, 0, count)

                                if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                                    lastReported = downloaded
                                    progress?.invoke(downloaded, expectedTotal)
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (!shouldContinue()) throw CancellationException("LAZ download cancelled")
                check(downloaded > 0L) { "Downloaded file was empty" }
                if (expectedTotal > 0L && downloaded < expectedTotal) {
                    throw EOFException("Connection ended at $downloaded of $expectedTotal bytes")
                }
                if (expectedTotal > 0L && downloaded > expectedTotal) {
                    workingFile.delete()
                    error("Downloaded more data than the server declared")
                }
                validatePointCloudSignature(workingFile)

                promote(workingFile, target)
                progress?.invoke(downloaded, if (expectedTotal > 0L) expectedTotal else downloaded)
                return target
            } catch (cancelled: CancellationException) {
                // Deliberately retain the .part file. A later tap resumes instead of starting over.
                throw cancelled
            } catch (failure: Throwable) {
                lastFailure = failure
                val canRetry = attempt < MAX_ATTEMPTS - 1 && isRetryable(failure)
                if (!canRetry) {
                    val retained = partial?.takeIf(File::isFile)?.length() ?: 0L
                    val suffix = if (retained > 0L) {
                        " ($retained bytes saved; tap again to resume)"
                    } else {
                        ""
                    }
                    throw IOException(
                        (failure.localizedMessage ?: "LAZ download failed") + suffix,
                        failure,
                    )
                }
                progress?.invoke(partial?.takeIf(File::isFile)?.length() ?: 0L, expectedTotal)
                waitForRetry(attempt, shouldContinue)
            } finally {
                connection?.disconnect()
            }
        }

        throw IOException(lastFailure?.localizedMessage ?: "LAZ download failed", lastFailure)
    }

    private fun openFollowingRedirects(source: URL, resumeFrom: Long): OpenedConnection {
        var currentUrl = source
        var redirects = 0
        while (true) {
            val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("User-Agent", "Find-It-Android/1.0")
                setRequestProperty("Accept", "application/octet-stream,*/*")
                setRequestProperty("Accept-Encoding", "identity")
                if (resumeFrom > 0L) setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            val status = connection.responseCode
            if (status !in 300..399) return OpenedConnection(currentUrl, connection, status)

            if (redirects++ >= MAX_REDIRECTS) {
                connection.disconnect()
                error("Too many redirects")
            }
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(!location.isNullOrBlank()) { "Redirect had no destination" }
            currentUrl = validateRemoteUrl(URL(currentUrl, location))
        }
    }

    private fun waitForRetry(attempt: Int, shouldContinue: () -> Boolean) {
        val delayMillis = min(RETRY_MAX_DELAY_MS, RETRY_BASE_DELAY_MS shl attempt.coerceAtMost(3))
        var remaining = delayMillis
        while (remaining > 0L) {
            if (!shouldContinue()) throw CancellationException("LAZ download cancelled")
            val step = min(250L, remaining)
            Thread.sleep(step)
            remaining -= step
        }
    }

    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is RetryableHttpException,
        is SocketTimeoutException,
        is SocketException,
        is EOFException,
        is IOException,
        -> true
        else -> false
    }

    /**
     * Rejects a completed transfer whose bytes are not a point cloud.
     *
     * Size checks alone pass a payload that is the wrong thing entirely — typically an HTML error
     * or maintenance page served with 200 and an honest Content-Length. Without this the file is
     * promoted, stored, and counted as a tile, and the mistake only surfaces much later when the
     * decoder rejects it, potentially after fetching the rest of a large mosaic around it.
     *
     * LAZ is LAS with compressed point data, so both carry the same signature.
     */
    internal fun validatePointCloudSignature(file: File) {
        val signature = ByteArray(LIDAR_SIGNATURE.size)
        val read = FileInputStream(file).use { it.read(signature) }
        if (read == LIDAR_SIGNATURE.size && signature.contentEquals(LIDAR_SIGNATURE)) return
        // Resuming a wrong payload would only ever append more of the wrong file, so drop it and
        // let a retry start the transfer cleanly.
        file.delete()
        error("Downloaded file is not a LAS/LAZ point cloud")
    }

    private fun promote(partial: File, destination: File) {
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = false)
            partial.delete()
        }
    }

    private fun parseContentRange(header: String?): ContentRange? {
        if (header.isNullOrBlank()) return null
        val match = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
            .find(header.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        if (start < 0L || end < start || total <= end) return null
        return ContentRange(start, end, total)
    }

    private fun parseUnsatisfiedRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        return Regex("bytes\\s+\\*/(\\d+)", RegexOption.IGNORE_CASE)
            .find(header.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
    }

    private fun sanitizeAndValidateName(rawName: String): String {
        val stripped = rawName.substringAfterLast('/').substringBefore('?').trim(' ', '"', '\'')
        val safe = stripped.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "lidar-download.laz" }
        val extension = safe.substringAfterLast('.', "").lowercase(Locale.US)
        require(extension == "laz" || extension == "las") {
            "The URL must resolve to a direct LAZ or LAS file"
        }
        return safe
    }

    private fun uniqueDestination(directory: File, requestedName: String): File {
        val first = File(directory, requestedName)
        if (!first.exists()) return first

        val extension = requestedName.substringAfterLast('.', "")
        val base = requestedName.removeSuffix(if (extension.isBlank()) "" else ".$extension")
        var index = 2
        while (true) {
            val candidate = File(directory, "$base-$index.$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun contentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val encoded = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.getOrNull(1)
        if (!encoded.isNullOrBlank()) return java.net.URLDecoder.decode(encoded, Charsets.UTF_8.name())

        return Regex("filename=\\\"?([^;\\\"]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    private fun validateRemoteUrl(url: URL): URL {
        require(url.protocol.equals("https", ignoreCase = true)) { "Only HTTPS downloads are allowed" }
        require(url.userInfo == null) { "URLs containing credentials are not allowed" }
        val host = url.host.lowercase(Locale.US)
        require(host.isNotBlank() && host != "localhost" && !host.endsWith(".localhost")) {
            "Invalid download host"
        }
        InetAddress.getAllByName(host).forEach { address ->
            val bytes = address.address
            val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
            require(
                !address.isAnyLocalAddress &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isSiteLocalAddress &&
                    !address.isMulticastAddress &&
                    !uniqueLocalV6,
            ) { "Private and local network downloads are blocked" }
        }
        return url
    }

    private data class OpenedConnection(
        val url: URL,
        val connection: HttpURLConnection,
        val status: Int,
    )

    private data class ContentRange(
        val start: Long,
        val end: Long,
        val total: Long,
    )

    private class RetryableHttpException(status: Int, message: String) :
        IOException("$message (HTTP $status)")
}
