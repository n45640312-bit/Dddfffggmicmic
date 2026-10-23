# Extreme Audio Amplifier

Advanced real-time microphone amplifier for Android powered by **FFmpeg**.

## Features

- Mic → FFmpeg processing → Speaker (latency target ≤ 500 ms)
- Extreme range sliders:
  - **Gain** (0–40 dB)
  - **Volume** (0–30 dB)
  - **Loudness** (0–25 dB)
  - **Clarity** (0–18 dB)
  - **Presence** (clear voice without reducing overall loudness)
  - **Noise Cancellation**
  - **Noise** (opposite of cancellation – adds / boosts noise character)
  - **Treble** / **Bass** (extreme ± ranges)
  - **Pitch Shift** (0.5x – 2.0x)
  - Multiple independent **Echo** types:
    - Slapback (short)
    - 1× / Medium
    - 2× / Long
    - Loop / Heavy feedback
    - Hall / Reverb-like
- No hard limiter (clipping allowed for maximum loudness)
- Foreground service + wake lock (works with screen off)
- Live parameter updates while running

## Requirements

- Android 8.0+ (API 26)
- Microphone permission

## Build with GitHub Actions

1. Push this repository to GitHub
2. Go to **Actions** tab → **Build APK** workflow
3. Download the APK artifact after successful build

Manual local build:

```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/
  src/main/
    java/com/extreme/audioamplifier/
      MainActivity.kt      # UI + slider binding
      AudioService.kt      # Foreground service
      AudioEngine.kt       # Capture → FFmpeg → Playback
      FilterParams.kt      # Slider values → FFmpeg -af graph
    res/layout/activity_main.xml
```

## FFmpeg Filter Notes

The filter graph is built dynamically in `FilterParams.buildFilterGraph()`.

Key filters used:
- `volume=XdB`
- `bass` / `treble`
- `equalizer` (presence & clarity)
- `afftdn` (noise reduction)
- `asetrate` + `aresample` (pitch)
- Multiple `aecho` instances (different delay/decay combinations)

Chunk size is ~200 ms. This keeps latency acceptable while giving FFmpeg enough data for stable processing.

## Important Notes

- **Very high gain settings will clip heavily** – this is intentional for “extreme loud”.
- On some devices FFmpeg processing of short chunks may cause higher latency or CPU load. 500 ms is the design target.
- First run may take a moment while FFmpeg native libraries load.
- For production, consider signing the release APK and testing on real devices with headphones (to avoid feedback).

## License

MIT (or your preferred license). FFmpegKit is under LGPL / GPL depending on the package used.

---

Made for extreme amplification use cases. Use responsibly (hearing protection recommended).
