package com.example.data.field

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.example.data.TargetSignal
import java.io.File

/**
 * Small wrapper around the platform recorder. Files stay in app-private storage so field notes
 * are available offline without exposing a file URI to another app.
 */
class VoiceNoteRecorder(
    private val context: Context,
    private val output: File,
) {
    private var recorder: MediaRecorder? = null

    fun start() {
        check(recorder == null) { "Voice-note recording is already active." }
        output.parentFile?.mkdirs()
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioEncodingBitRate(64_000)
            created.setAudioSamplingRate(44_100)
            created.setOutputFile(output.absolutePath)
            created.prepare()
            created.start()
            recorder = created
        } catch (error: Throwable) {
            created.release()
            output.delete()
            throw error
        }
    }

    /** Stops and returns the saved file. An invalid/very short recording is discarded. */
    fun stop(): File? {
        val active = recorder ?: return null
        recorder = null
        val stopped = runCatching { active.stop() }.isSuccess
        active.reset()
        active.release()
        return output.takeIf { stopped && it.isFile && it.length() > 0L }
            ?: run {
                output.delete()
                null
            }
    }

    fun cancel() {
        val active = recorder ?: return
        recorder = null
        runCatching { active.stop() }
        active.reset()
        active.release()
        output.delete()
    }
}

fun createVoiceNoteFile(context: Context, target: TargetSignal): File {
    val directory = File(context.filesDir, "field-voice-notes").apply { mkdirs() }
    return File(directory, "target-${target.id}-${System.currentTimeMillis()}.m4a")
}

/** Deletes only app-private voice-note files; arbitrary URIs are never touched. */
fun deleteVoiceNoteFile(context: Context, uriText: String) {
    val uri = Uri.parse(uriText)
    if (uri.scheme != "file") return
    val root = File(context.filesDir, "field-voice-notes").canonicalFile
    val candidate = runCatching { File(requireNotNull(uri.path)).canonicalFile }.getOrNull() ?: return
    if (candidate.parentFile == root) candidate.delete()
}
