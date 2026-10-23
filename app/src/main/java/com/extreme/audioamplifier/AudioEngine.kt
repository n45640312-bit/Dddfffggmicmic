package com.extreme.audioamplifier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_MS = 200
    }

    private val isRunning = AtomicBoolean(false)
    private val paramsRef = AtomicReference(FilterParams())
    private var engineJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    var onError: ((String) -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null

    fun updateParams(params: FilterParams) {
        paramsRef.set(params)
    }

    fun start() {
        if (isRunning.get()) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            onError?.invoke("Mic permission not granted")
            return
        }

        try {
            FFmpegKitConfig.setLogLevel(Level.AV_LOG_ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "FFmpegKitConfig failed", e)
        }

        isRunning.set(true)
        engineJob = scope.launch {
            try {
                processLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Engine crashed", e)
                onError?.invoke("Engine error: ${e.message}")
            } finally {
                isRunning.set(false)
                cleanup()
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        engineJob?.cancel()
        engineJob = null
        cleanup()
        onStatus?.invoke("Stopped")
    }

    private fun cleanup() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
    }

    private suspend fun processLoop() {
        val minRec = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        val minTrack = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)

        if (minRec <= 0 || minTrack <= 0) {
            onError?.invoke("Audio buffer error: $minRec / $minTrack")
            return
        }

        val chunkBytes = (SAMPLE_RATE * CHUNK_MS / 1000) * 2
        val bufferSize = maxOf(minRec * 2, chunkBytes * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: Exception) {
            onError?.invoke("AudioRecord create failed: ${e.message}")
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onError?.invoke("AudioRecord not initialized")
            return
        }

        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT,
                maxOf(minTrack * 2, chunkBytes * 2),
                AudioTrack.MODE_STREAM
            )
        } catch (e: Exception) {
            onError?.invoke("AudioTrack create failed: ${e.message}")
            return
        }

        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            onError?.invoke("AudioTrack not initialized")
            return
        }

        val inputBuffer = ByteArray(chunkBytes)
        val cacheDir = context.cacheDir

        try {
            audioRecord?.startRecording()
            audioTrack?.play()
            onStatus?.invoke("Running")
        } catch (e: Exception) {
            onError?.invoke("Start failed: ${e.message}")
            return
        }

        var chunkIndex = 0L

        while (isRunning.get()) {
            val read = try {
                audioRecord?.read(inputBuffer, 0, inputBuffer.size) ?: -1
            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                -1
            }

            if (read <= 0) {
                delay(10)
                continue
            }

            val inFile = File(cacheDir, "in_$chunkIndex.pcm")
            val outFile = File(cacheDir, "out_$chunkIndex.pcm")

            try {
                inFile.writeBytes(inputBuffer.copyOf(read))

                val filter = paramsRef.get().toFilterString()
                val cmd = "-y -f s16le -ar $SAMPLE_RATE -ac 1 -i ${inFile.absolutePath} " +
                        "-af \"$filter\" -f s16le -ar $SAMPLE_RATE -ac 1 ${outFile.absolutePath}"

                val session = FFmpegKit.execute(cmd)

                if (ReturnCode.isSuccess(session.returnCode) && outFile.exists()) {
                    val processed = outFile.readBytes()
                    if (processed.isNotEmpty()) {
                        audioTrack?.write(processed, 0, processed.size)
                    }
                } else {
                    // Fallback: play original if filter fails
                    audioTrack?.write(inputBuffer, 0, read)
                    Log.w(TAG, "FFmpeg failed: ${session.failStackTrace}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chunk error", e)
                try { audioTrack?.write(inputBuffer, 0, read) } catch (_: Exception) {}
            } finally {
                inFile.delete()
                outFile.delete()
            }

            chunkIndex++
            if (chunkIndex > 100000) chunkIndex = 0
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
