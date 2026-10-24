package com.extreme.audioamplifier

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.extreme.audioamplifier.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var service: AudioService? = null
    private var bound = false
    private var isRunning = false

    private val params = FilterParams()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            startServiceAndBind()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as AudioService.LocalBinder
            service = local.getService()
            bound = true
            service?.getEngine()?.onStatus = { status ->
                runOnUiThread { binding.tvStatus.text = status }
            }
            service?.getEngine()?.onError = { err ->
                runOnUiThread {
                    binding.tvStatus.text = "Error: $err"
                    Toast.makeText(this@MainActivity, err, Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSliders()
        binding.btnToggle.setOnClickListener { toggleAmplifier() }

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startServiceAndBind()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startServiceAndBind() {
        val intent = Intent(this, AudioService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun toggleAmplifier() {
        if (!bound || service == null) {
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show()
            return
        }
        if (isRunning) {
            service?.stopAmplifier()
            binding.btnToggle.text = getString(R.string.start)
            binding.btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.success))
            isRunning = false
        } else {
            service?.updateParams(params)
            service?.startAmplifier()
            binding.btnToggle.text = getString(R.string.stop)
            binding.btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.danger))
            isRunning = true
        }
    }

    private fun setupSliders() {
        // Helper to keep UI + params in sync and push live to engine
        fun update() {
            service?.updateParams(params)
        }

        // Gain 0-40 dB
        binding.seekGain.setOnSeekBarChangeListener(simpleListener { p ->
            params.gainDb = p / 10f
            binding.tvGainValue.text = "%.1f dB".format(params.gainDb)
            update()
        })

        // Volume 0-30 dB
        binding.seekVolume.setOnSeekBarChangeListener(simpleListener { p ->
            params.volumeDb = p / 10f
            binding.tvVolumeValue.text = "%.1f dB".format(params.volumeDb)
            update()
        })

        // Loudness 0-25 dB
        binding.seekLoudness.setOnSeekBarChangeListener(simpleListener { p ->
            params.loudnessDb = p / 10f
            binding.tvLoudnessValue.text = "%.1f dB".format(params.loudnessDb)
            update()
        })

        // Clarity 0-18 dB
        binding.seekClarity.setOnSeekBarChangeListener(simpleListener { p ->
            params.clarityDb = p / 10f
            binding.tvClarityValue.text = "%.1f dB".format(params.clarityDb)
            update()
        })

        // Presence 0-20 dB
        binding.seekPresence.setOnSeekBarChangeListener(simpleListener { p ->
            params.presenceDb = p / 10f
            binding.tvPresenceValue.text = "%.1f dB".format(params.presenceDb)
            update()
        })

        // Noise Cancel 0-30
        binding.seekNoiseCancel.setOnSeekBarChangeListener(simpleListener { p ->
            params.noiseCancel = p / 10f
            binding.tvNoiseCancelValue.text = "%.1f".format(params.noiseCancel)
            update()
        })

        // Noise (add) 0-1.0
        binding.seekNoise.setOnSeekBarChangeListener(simpleListener { p ->
            params.noiseAmount = p / 200f
            binding.tvNoiseValue.text = "%.2f".format(params.noiseAmount)
            update()
        })

        // Treble -12 .. +24  (progress 0-240 → -12 to +24)
        binding.seekTreble.setOnSeekBarChangeListener(simpleListener { p ->
            params.trebleDb = (p - 120) / 5f
            binding.tvTrebleValue.text = "%.1f dB".format(params.trebleDb)
            update()
        })

        // Bass -12 .. +24
        binding.seekBass.setOnSeekBarChangeListener(simpleListener { p ->
            params.bassDb = (p - 120) / 5f
            binding.tvBassValue.text = "%.1f dB".format(params.bassDb)
            update()
        })

        // Pitch 0.5 .. 2.0 (progress 0-150 → 0.5 to 2.0)
        binding.seekPitch.setOnSeekBarChangeListener(simpleListener { p ->
            params.pitchFactor = 0.5f + (p / 100f)
            binding.tvPitchValue.text = "%.2fx".format(params.pitchFactor)
            update()
        })

        // Echoes 0-1
        binding.seekEchoSlap.setOnSeekBarChangeListener(simpleListener { p ->
            params.echoSlap = p / 100f
            binding.tvEchoSlapValue.text = "%.2f".format(params.echoSlap)
            update()
        })
        binding.seekEcho1x.setOnSeekBarChangeListener(simpleListener { p ->
            params.echo1x = p / 100f
            binding.tvEcho1xValue.text = "%.2f".format(params.echo1x)
            update()
        })
        binding.seekEcho2x.setOnSeekBarChangeListener(simpleListener { p ->
            params.echo2x = p / 100f
            binding.tvEcho2xValue.text = "%.2f".format(params.echo2x)
            update()
        })
        binding.seekEchoLoop.setOnSeekBarChangeListener(simpleListener { p ->
            params.echoLoop = p / 100f
            binding.tvEchoLoopValue.text = "%.2f".format(params.echoLoop)
            update()
        })
        binding.seekEchoHall.setOnSeekBarChangeListener(simpleListener { p ->
            params.echoHall = p / 100f
            binding.tvEchoHallValue.text = "%.2f".format(params.echoHall)
            update()
        })
    }

    private fun simpleListener(onProgress: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
