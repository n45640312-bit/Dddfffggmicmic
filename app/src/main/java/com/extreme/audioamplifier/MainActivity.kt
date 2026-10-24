package com.extreme.audioamplifier

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
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
        if (results.values.all { it }) startServiceAndBind()
        else Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as AudioService.LocalBinder).getService()
            bound = true
            service?.getEngine()?.onStatus = { s -> runOnUiThread { binding.tvStatus.text = s } }
            service?.getEngine()?.onError = { e ->
                runOnUiThread {
                    binding.tvStatus.text = "Error: $e"
                    Toast.makeText(this@MainActivity, e, Toast.LENGTH_SHORT).show()
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackgroundVideo()
        setupSliders()
        binding.btnToggle.setOnClickListener { toggleAmplifier() }
        checkPermissionsAndStart()
    }

    private fun setupBackgroundVideo() {
        try {
            val vv = binding.bgVideo
            val uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.bgvideo)
            vv.setVideoURI(uri)
            vv.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                vv.start()
            }
            vv.setOnErrorListener { _, _, _ ->
                binding.bgVideo.visibility = View.GONE
                true
            }
        } catch (_: Exception) {
            binding.bgVideo.visibility = View.GONE
        }
    }

    private fun checkPermissionsAndStart() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startServiceAndBind()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startServiceAndBind() {
        val intent = Intent(this, AudioService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun toggleAmplifier() {
        if (!bound) {
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show()
            return
        }
        if (isRunning) {
            service?.stopAmplifier()
            isRunning = false
            binding.btnToggle.text = getString(R.string.start)
        } else {
            service?.updateParams(params)
            service?.startAmplifier()
            isRunning = true
            binding.btnToggle.text = getString(R.string.stop)
        }
    }

    private fun bindThumb(seek: SeekBar, thumb: LottieAnimationView) {
        thumb.setAnimation("border_green.json")
        thumb.playAnimation()
        thumb.loop(true)
        seek.thumb.mutate().alpha = 0

        val updatePos = {
            seek.post {
                val max = seek.width - seek.paddingStart - seek.paddingEnd
                if (max <= 0) return@post
                val ratio = seek.progress.toFloat() / seek.max.coerceAtLeast(1)
                val x = seek.paddingStart + ratio * max - thumb.width / 2f
                thumb.translationX = x.coerceAtLeast(0f)
            }
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                updatePos()
                if (fromUser) onSliderChanged(seek, progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        seek.post { updatePos() }
    }

    private fun onSliderChanged(seek: SeekBar, p: Int) {
        when (seek.id) {
            R.id.seekGain -> {
                params.gainDb = p * 0.4f
                binding.tvGainValue.text = String.format("%.1f dB", params.gainDb)
            }
            R.id.seekVolume -> {
                params.volumeDb = p * 0.3f
                binding.tvVolumeValue.text = String.format("%.1f dB", params.volumeDb)
            }
            R.id.seekLoudness -> {
                params.loudnessDb = p * 0.25f
                binding.tvLoudnessValue.text = String.format("%.1f", params.loudnessDb)
            }
            R.id.seekClarity -> {
                params.clarityDb = p * 0.18f
                binding.tvClarityValue.text = String.format("%.1f", params.clarityDb)
            }
            R.id.seekPresence -> {
                params.presenceDb = p * 0.2f
                binding.tvPresenceValue.text = String.format("%.1f", params.presenceDb)
            }
            R.id.seekNoiseCancel -> {
                params.noiseCancel = p * 0.3f
                binding.tvNoiseCancelValue.text = String.format("%.1f", params.noiseCancel)
            }
            R.id.seekNoise -> {
                params.noiseAmount = p / 100f
                binding.tvNoiseValue.text = String.format("%.2f", params.noiseAmount)
            }
            R.id.seekBass -> {
                params.bassDb = (p - 50) / 2f
                binding.tvBassValue.text = String.format("%.1f dB", params.bassDb)
            }
            R.id.seekTreble -> {
                params.trebleDb = (p - 50) / 2f
                binding.tvTrebleValue.text = String.format("%.1f dB", params.trebleDb)
            }
            R.id.seekPitch -> {
                params.pitchFactor = 0.5f + p / 100f
                binding.tvPitchValue.text = String.format("%.2fx", params.pitchFactor)
            }
            R.id.seekEchoSlap -> {
                params.echoSlap = p / 100f
                binding.tvEchoSlapValue.text = String.format("%.2f", params.echoSlap)
            }
            R.id.seekEcho1x -> {
                params.echo1x = p / 100f
                binding.tvEcho1xValue.text = String.format("%.2f", params.echo1x)
            }
            R.id.seekEcho2x -> {
                params.echo2x = p / 100f
                binding.tvEcho2xValue.text = String.format("%.2f", params.echo2x)
            }
            R.id.seekEchoLoop -> {
                params.echoLoop = p / 100f
                binding.tvEchoLoopValue.text = String.format("%.2f", params.echoLoop)
            }
            R.id.seekEchoHall -> {
                params.echoHall = p / 100f
                binding.tvEchoHallValue.text = String.format("%.2f", params.echoHall)
            }
        }
        service?.updateParams(params)
    }

    private fun setupSliders() {
        bindThumb(binding.seekGain, binding.thumbGain)
        bindThumb(binding.seekVolume, binding.thumbVolume)
        bindThumb(binding.seekLoudness, binding.thumbLoudness)
        bindThumb(binding.seekClarity, binding.thumbClarity)
        bindThumb(binding.seekPresence, binding.thumbPresence)
        bindThumb(binding.seekNoiseCancel, binding.thumbNoiseCancel)
        bindThumb(binding.seekNoise, binding.thumbNoise)
        bindThumb(binding.seekBass, binding.thumbBass)
        bindThumb(binding.seekTreble, binding.thumbTreble)
        bindThumb(binding.seekPitch, binding.thumbPitch)
        bindThumb(binding.seekEchoSlap, binding.thumbEchoSlap)
        bindThumb(binding.seekEcho1x, binding.thumbEcho1x)
        bindThumb(binding.seekEcho2x, binding.thumbEcho2x)
        bindThumb(binding.seekEchoLoop, binding.thumbEchoLoop)
        bindThumb(binding.seekEchoHall, binding.thumbEchoHall)
    }

    override fun onResume() {
        super.onResume()
        try { binding.bgVideo.start() } catch (_: Exception) {}
    }

    override fun onPause() {
        try { binding.bgVideo.pause() } catch (_: Exception) {}
        super.onPause()
    }

    override fun onDestroy() {
        try { binding.bgVideo.stopPlayback() } catch (_: Exception) {}
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
