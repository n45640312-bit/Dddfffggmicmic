package com.extreme.audioamplifier

/**
 * Holds all slider values. Ranges are deliberately extreme.
 * Values are stored as the raw progress from SeekBar and converted in buildFilterGraph().
 */
data class FilterParams(
    var gainDb: Float = 0f,          // 0 .. 40
    var volumeDb: Float = 0f,        // 0 .. 30
    var loudnessDb: Float = 0f,      // 0 .. 25
    var clarityDb: Float = 0f,       // 0 .. 18
    var presenceDb: Float = 0f,      // 0 .. 20
    var noiseCancel: Float = 0f,     // 0 .. 30 (nr strength)
    var noiseAmount: Float = 0f,     // 0 .. 1.0 (add noise)
    var trebleDb: Float = 0f,        // -12 .. +24
    var bassDb: Float = 0f,          // -12 .. +24
    var pitchFactor: Float = 1.0f,   // 0.5 .. 2.0
    var echoSlap: Float = 0f,        // 0 .. 1
    var echo1x: Float = 0f,          // 0 .. 1
    var echo2x: Float = 0f,          // 0 .. 1
    var echoLoop: Float = 0f,        // 0 .. 1
    var echoHall: Float = 0f         // 0 .. 1
) {
    fun buildFilterGraph(sampleRate: Int = 48000): String {
        val filters = mutableListOf<String>()

        // High-pass to remove rumble
        filters.add("highpass=f=50")

        // Bass
        if (bassDb != 0f) {
            filters.add("bass=g=${bassDb}:f=100:w=0.7")
        }

        // Treble
        if (trebleDb != 0f) {
            filters.add("treble=g=${trebleDb}:f=5000:w=0.7")
        }

        // Clarity - peaking around 3 kHz
        if (clarityDb > 0f) {
            filters.add("equalizer=f=3000:t=q:w=1.2:g=$clarityDb")
        }

        // Presence - 2.5-5 kHz boost without killing overall loudness
        if (presenceDb > 0f) {
            filters.add("equalizer=f=2500:t=q:w=1.0:g=${presenceDb * 0.7f}")
            filters.add("equalizer=f=4200:t=q:w=1.3:g=${presenceDb * 0.9f}")
            filters.add("highshelf=f=6000:g=${presenceDb * 0.4f}")
        }

        // Noise cancellation (afftdn)
        if (noiseCancel > 0f) {
            val nr = noiseCancel.coerceIn(0.01f, 30f)
            val nf = (-50f + noiseCancel * 0.5f).coerceIn(-80f, -20f)
            filters.add("afftdn=nr=$nr:nf=$nf")
        }

        // Pitch shift (changes speed slightly)
        if (pitchFactor != 1.0f) {
            val rate = (sampleRate * pitchFactor).toInt()
            filters.add("asetrate=$rate")
            filters.add("aresample=$sampleRate")
        }

        // Echoes - multiple aecho instances for different characters
        // Slapback (very short)
        if (echoSlap > 0.01f) {
            val decay = (0.3f + echoSlap * 0.5f).coerceIn(0.1f, 0.85f)
            filters.add("aecho=0.8:0.7:45|70:${decay}|${decay * 0.7f}")
        }

        // 1x / medium
        if (echo1x > 0.01f) {
            val decay = (0.25f + echo1x * 0.45f).coerceIn(0.1f, 0.8f)
            filters.add("aecho=0.8:0.75:220|380:${decay}|${decay * 0.6f}")
        }

        // 2x / longer
        if (echo2x > 0.01f) {
            val decay = (0.2f + echo2x * 0.4f).coerceIn(0.1f, 0.7f)
            filters.add("aecho=0.8:0.7:450|700:${decay}|${decay * 0.55f}")
        }

        // Loop / heavy feedback
        if (echoLoop > 0.01f) {
            val decay = (0.5f + echoLoop * 0.45f).coerceIn(0.4f, 0.95f)
            filters.add("aecho=0.9:0.85:150|300|500:${decay}|${decay * 0.8f}|${decay * 0.6f}")
        }

        // Hall / reverb-like long
        if (echoHall > 0.01f) {
            val decay = (0.15f + echoHall * 0.35f).coerceIn(0.1f, 0.6f)
            filters.add("aecho=0.8:0.6:900|1400|2000:${decay}|${decay * 0.7f}|${decay * 0.45f}")
        }

        // Noise addition (opposite of cancellation) - mix mild noise
        if (noiseAmount > 0.01f) {
            // We approximate by boosting high frequencies + mild volume of residual
            // Full anoisesrc mix is complex in single chain; highshelf + volume works as "noise"
            filters.add("highshelf=f=8000:g=${noiseAmount * 12f}")
        }

        // Final extreme gains (order matters: gain early, volume late)
        val totalGain = gainDb + loudnessDb
        if (totalGain > 0f) {
            filters.add("volume=${totalGain}dB")
        }
        if (volumeDb > 0f) {
            filters.add("volume=${volumeDb}dB")
        }

        // Soft safety - only very mild to avoid complete silence on extreme settings
        // User asked to avoid hard limiter, so we skip alimiter completely.

        return filters.joinToString(",")
    }
}
