package com.extreme.audioamplifier

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core real-time-ish audio engine.
 * Captures from mic → writes short WAV chunks → FFmpeg processes → plays via AudioTrack.
 * Latency target: 200-500 ms (acceptable per requirements).
 */
class AudioEngine(private val cacheDir: File) {

    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // ~200 ms chunks (good balance for FFmpeg overhead + latency)
        private const val CHUNK_MS = 200
        private val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MS / 1000
        private val CHUNK_BYTES = CHUNK_SAMPLES * 2 // 16-bit
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val isRunning = AtomicBoolean(false)
    private var processingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var params = FilterParams()

    var onStatus: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        try {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
            val bufSize = maxOf(minBuf * 2, CHUNK_BYTES * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // less AGC / less processing
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufSize
            )

            val outMin = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AUDIO_FORMAT)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(outMin * 2, CHUNK_BYTES * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord init failed")
            }
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                throw IllegalStateException("AudioTrack init failed")
            }

            audioRecord?.startRecording()
            audioTrack?.play()

            onStatus?.invoke("Amplifying…")
            processingJob = scope.launch { processLoop() }

        } catch (e: Exception) {
            Log.e(TAG, "Start failed", e)
            onError?.invoke(e.message ?: "Start failed")
            stop()
        }
    }

    fun stop() {
        isRunning.set(false)
        processingJob?.cancel()
        processingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        onStatus?.invoke("Ready")
    }

    private suspend fun processLoop() {
        val inBuf = ByteArray(CHUNK_BYTES)
        val wavHeaderSize = 44

        while (isRunning.get()) {
            try {
                val read = audioRecord?.read(inBuf, 0, inBuf.size) ?: -1
                if (read <= 0) {
                    delay(10)
                    continue
                }

                // Write temporary input WAV
                val inFile = File(cacheDir, "in_${System.nanoTime()}.wav")
                val outFile = File(cacheDir, "out_${System.nanoTime()}.wav")

                writeWav(inFile, inBuf, read)

                val filter = params.buildFilterGraph(SAMPLE_RATE)
                val cmd = "-y -i \"${inFile.absolutePath}\" -af \"$filter\" -acodec pcm_s16le -ar $SAMPLE_RATE -ac 1 \"${outFile.absolutePath}\""

                val session = FFmpegKit.execute(cmd)
                if (ReturnCode.isSuccess(session.returnCode)) {
                    // Read processed PCM (skip WAV header)
                    if (outFile.exists() && outFile.length() > wavHeaderSize) {
                        FileInputStream(outFile).use { fis ->
                            fis.skip(wavHeaderSize.toLong())
                            val pcm = fis.readBytes()
                            if (pcm.isNotEmpty()) {
                                audioTrack?.write(pcm, 0, pcm.size)
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "FFmpeg failed: ${session.failStackTrace}")
                    // Fallback: play original so audio doesn't go silent
                    audioTrack?.write(inBuf, 0, read)
                }

                // Cleanup
                inFile.delete()
                outFile.delete()

            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Process error", e)
                delay(50)
            }
        }
    }

    private fun writeWav(file: File, pcm: ByteArray, length: Int) {
        FileOutputStream(file).use { fos ->
            val totalDataLen = length + 36
            val byteRate = SAMPLE_RATE * 1 * 16 / 8

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // subchunk1 size
            header.putShort(1) // PCM
            header.putShort(1) // mono
            header.putInt(SAMPLE_RATE)
            header.putInt(byteRate)
            header.putShort(2) // block align
            header.putShort(16) // bits
            header.put("data".toByteArray())
            header.putInt(length)

            fos.write(header.array())
            fos.write(pcm, 0, length)
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
