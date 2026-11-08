package dev.anyu.tiebapure

import android.content.Context
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

internal class PrivateVoiceRecorder(private val context: Context) {
    var recording by mutableStateOf(false); private set
    var attachment by mutableStateOf<File?>(null); private set
    var duration by mutableStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = 0L

    @Suppress("DEPRECATION")
    fun start() {
        discard()
        error = null
        val output = File.createTempFile("private-voice-", ".amr", context.cacheDir)
        val next = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            next.setAudioSamplingRate(8000)
            next.setAudioChannels(1)
            next.setMaxDuration(60_000)
            next.setOutputFile(output.absolutePath)
            next.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) runCatching { stop() }.onFailure { error = it.message }
            }
            next.setOnErrorListener { _, _, _ -> discard(); error = "录音未能完成，请重新录制" }
            next.prepare(); next.start()
            recorder = next; file = output; started = android.os.SystemClock.elapsedRealtime(); recording = true
        } catch (e: Exception) {
            next.release(); output.delete(); throw e
        }
    }

    fun stop() {
        val current = recorder ?: return
        try {
            current.stop()
            duration = ((android.os.SystemClock.elapsedRealtime() - started + 999) / 1000).toInt().coerceIn(1, 60)
            attachment = file
        } catch (e: RuntimeException) {
            file?.delete(); attachment = null; throw IllegalStateException("录音太短，请重新录制", e)
        } finally {
            current.release(); recorder = null; recording = false
        }
    }

    fun discard() {
        runCatching { recorder?.stop() }
        recorder?.release(); recorder = null; recording = false
        file?.delete(); file = null; attachment = null; duration = 0
    }
}
