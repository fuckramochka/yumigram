package org.yumigram.app.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): Boolean {
        try {
            val audioDir = File(context.cacheDir, "audio_notes").apply { if (!exists()) mkdirs() }
            val file = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            startTimeMs = System.currentTimeMillis()
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Failed to start audio recording", e)
            cancelRecording()
            return false
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun stopRecording(): Pair<File?, Int> {
        val recorder = mediaRecorder ?: return Pair(null, 0)
        return try {
            val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt().coerceAtLeast(1)
            recorder.stop()
            recorder.release()
            mediaRecorder = null
            Pair(currentOutputFile, durationSec)
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Failed to stop recording", e)
            cancelRecording()
            Pair(null, 0)
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        currentOutputFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        currentOutputFile = null
    }
}
