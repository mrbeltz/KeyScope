# R5 Monitor

A Wi-Fi field monitor for the Canon EOS R5, for Android. It is built for a Galaxy Z Fold: the
picture sits over the controls on the cover screen, fills the whole screen when you turn the cover
screen sideways, and shares the unfolded screen with scopes and settings.

It lives in this repo as a second app module, separate from KeyScope. It has its own package
(`com.jonny.r5monitor`), so the two install side by side.

## What it does

- **Live view** from the camera over Wi-Fi. It uses CCAPI's chunked stream, and falls back to
  requesting one frame at a time on firmware that does not stream.
- **Exposure and focus aids**, drawn on the GPU (Android 13+):
  - zebras, 70–100%
  - focus peaking in four colours
  - false colour with a key: crushed, <10, 18% grey, skin, 93+ and clipped
- **Scopes:** a luma waveform with a 0/25/50/75/100 graticule, an RGB histogram, and the share of
  the frame that is clipped or crushed.
- **Framing:**
  - thirds, centre mark and 90% safe area
  - frame lines for 2.39, 1.85, 16:9, 1:1, 4:5 and 9:16. The last one is for checking vertical
    cutdowns while shooting horizontal.
  - anamorphic desqueeze from 1.33x to 2x
  - mirror and upside-down, for rigs
- **Punch-in:** pinch to zoom up to 6x, or double tap for 3x at the spot you tap.
- **Camera control:**
  - start and stop recording, with a local running timer
  - one-shot AF
  - take a still
  - tap Tv, Av, ISO, exposure compensation or WB to pick a new value from the list the camera
    offers. Every other setting the camera reports is under *More settings*.
- **Status:** battery, free card space, frame rate, and the live view size (small is faster, medium
  is sharper).
- **Keeps the screen awake** while it is monitoring. It reconnects on its own if the signal drops.

## Setting up the camera (once)

The R5 has no open video stream over Wi-Fi. Canon's own Camera Connect app uses a private
protocol. What the camera does offer is **CCAPI (Camera Control API)**, an HTTP API that has to be
activated once:

1. Register on the Canon Developer Community (free) and request CCAPI access.
2. Download the CCAPI activation tool and run it with the camera connected to your computer. The
   R5 needs firmware 1.1.0 or later.
3. After activation, connect the camera to a network from its Wi-Fi menu. You can use a router the
   phone is also on, or the phone's own hotspot. The camera then shows its IP address.

## Connecting

Type the IP address the camera shows, or tap **Find camera** to sweep the local /24 for anything
answering CCAPI on port 8080. The last address is remembered.

Some Wi-Fi details:

- **Camera or router with no internet:** Android keeps mobile data as the default network. The app
  binds its sockets to the Wi-Fi network itself, so leaving mobile data on is fine.
- **Phone hotspot:** the camera is on the hotspot's subnet and is reached directly. This setup
  usually gives the steadiest frame rate on location, since nothing else is on the network.

## Limits

- Live view is a JPEG stream, roughly 10–25 fps depending on size and signal, with a short delay.
  It is for framing, exposure and focus checks, not a replacement for an HDMI monitor on a
  timing-critical focus pull.
- There is no audio. CCAPI does not carry it.
- The frame shows what the camera sends for live view. It is not the recorded image, so a Canon
  Log picture looks flat here as it does on the camera without View Assist. Zebras and false colour
  are measured on that picture.
- The camera decides which settings can be changed, and when. Anything it refuses shows as a
  message with the camera's own reason.

## Getting it on the phone

Every push that touches `monitor/` runs `.github/workflows/monitor.yml`:

- **Any branch:** the run's artifacts include `r5-monitor-debug-apk`.
- **`main`:** the APK is also attached to the `monitor-latest` release.

To build locally:

```bash
./gradlew :monitor:installDebug
./gradlew :monitor:testDebugUnitTest
```

## Layout

```
monitor/src/main/java/com/jonny/r5monitor/
  MainActivity.kt          wiring, screen-on, immersive mode
  MonitorSession.kt        connection, video loop, status polling, controls
  MonitorPrefs.kt          overlay and guide settings, persisted
  WifiLink.kt              picking the Wi-Fi network, finding the camera
  ccapi/
    CcapiClient.kt         HTTP against the camera, bound to the right network
    CcapiModel.kt          endpoint list, JSON parsing, value formatting
    LiveViewFrameReader.kt the scroll stream's framing
  scopes/ScopeMath.kt      waveform and histogram from a frame
  ui/
    MonitorScreen.kt       the three layouts
    LiveView.kt            picture, zoom, guides
    Overlays.kt            AGSL zebra / peaking / false colour
    ScopeViews.kt          waveform and histogram drawing
    Panels.kt              HUD, controls, settings cards
    ConnectScreen.kt       address entry, search, setup help
```
