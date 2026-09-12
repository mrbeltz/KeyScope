# Key Bro

Real-time musical key detection from the microphone, for Android. Point the phone at a speaker and
it tells you the key, the scale notes, the Camelot / Open Key code, the reference tuning and a
rough BPM — the same job Auto Key does.

Built and laid out for a Galaxy Z Fold: single column on the cover screen, two panes when you open
it, and the listening session survives the fold because the audio engine is a process singleton
rather than a ViewModel.

## Once a reading lands

By default the mic releases itself the moment a key locks — a lock is the answer, and there is no
reason to keep recording past it. The reveal is a pair of rings blooming outward with a haptic tick
on the same frame, so the result registers without looking at the screen. The *Keep listening after
lock* switch in the Analysis card turns that off and keeps it re-reading as the music changes.

From the locked reading you can:

- **Copy** gives one line: `F# minor · 11A · Open Key 4m · 128 BPM`. **Share** gives the whole
  picture — scale, chords, tempo, tuning, compatible keys and what it heard.
- **Transpose** to a target tonic. Shows the semitone move — shortest path, so G from C reads as
  −5 rather than +7 — and the tempo a varispeed pitch drags along with it. Key lock leaves tempo
  alone, and it says so.
- **Sound the tonic**, or walk the scale up to the octave. Both are built at the *detected*
  reference pitch, so on a record cut to A=432 they line up instead of fighting it. The tone stops
  itself when the mic opens, since otherwise it would feed straight into its own reading.
- **Read the chords** — the seven diatonic triads with roman numerals.

## For producing

- **Files.** Point it at samples, loops or bounces and it runs the same pipeline with no speaker
  and no room in between, which is simply a better reading than playing the track out loud. Uses
  the storage access framework, so no storage permission is needed. Each result carries a chord
  timeline and a key per twenty second segment, so a modulation shows up instead of being averaged
  away. Chords get their own 372 ms frame here rather than borrowing the key path's 743 ms window,
  which is why they are markedly better on files than live.
- **Folders, and renaming.** Pick a folder and it analyses everything audio inside, then offers to
  rename each file with what it found: `Loop_01.wav` becomes `Loop_01 Am 128.wav`. This is the only
  thing in the app that changes files you already had, so it never happens on one tap — the full
  before-and-after list is shown and has to be confirmed, the tag is appended so nothing of the
  original name is lost, and an already-tagged file is skipped rather than having a second suffix
  stacked on it.
- **Links.** Paste a YouTube link and the official embed plays it out loud while the mic reads it
  back. Nothing is downloaded — extracting audio from YouTube is against their terms — so this
  goes through a speaker and a room and is correspondingly less certain than the file path. Use
  files for anything you already have.
- **MIDI out.** A file's chord timeline, or any suggested progression, as a `.mid` at the right
  tempo. Files leave through a `FileProvider`, which is what puts Quick Share in the system sheet
  alongside everything else.
- **CSV out.** The whole batch as `file,key,camelot,openkey,bpm,tuning_cents,confidence,seconds`.
  Saving goes through the document picker, so Google Drive is a destination with no sign-in and no
  Drive API.
- **Project target.** Set the key and tempo you are working in, once, and it persists. Every
  reading then answers whether it fits and what the move is, rather than leaving you to work it
  out from the transpose card.
- **Tap tempo.** The only real answer to the half/double ambiguity an onset autocorrelator will
  always have. It uses the median interval, so one late tap is discarded rather than averaged in,
  and it tells you when your taps and the detector are the same tempo an octave apart.
- **Metronome** at the detected, tapped or project tempo. Beats are placed by sample index rather
  than scheduled, so it cannot drift.
- **Progressions** spelled into the detected key and auditionable — `F#m · D · A · E` under
  `i - VI - III - VII`. Major and minor get different sets.
- **Live chords.** The current chord and the recent progression, matched against templates from
  the per-frame chroma the engine already computes. Held to a lower standard than the key readout,
  for reasons in the section below.

## Getting it on a phone

The repo builds itself. Every push to `main` runs `.github/workflows/build.yml`, which produces a
debug APK and attaches it to a rolling `latest` release, so a phone can install it straight from
the releases page with no toolchain anywhere:

```
https://github.com/mrbeltz/KeyScope/releases/latest
```

The APK is published *before* the test step runs, so a failing DSP test turns the run red without
withholding a build you can try.

## Building it locally

No Android SDK is required on your side beyond Android Studio:

1. Open the `KeyScope` folder in Android Studio (Ladybug or newer).
2. Let it sync — it will generate the Gradle wrapper JAR if it is missing.
3. Run on the phone with USB debugging on.

From a terminal, once you have a JDK 17 and the Android SDK:

```bash
gradle wrapper && ./gradlew installDebug
```

Run the DSP tests (pure JVM, no device needed):

```bash
./gradlew test
```

## How the detection works

Signal path, all of it plain Kotlin in `app/src/main/java/com/jonny/keyscope/dsp/`:

1. **Capture** — `AudioRecord` at 44.1 kHz float mono, preferring the `UNPROCESSED` source so the
   phone's AGC, noise suppression and beamforming do not reshape the spectrum. Those are tuned for
   speech and actively harmful here.
2. **Decimate** — 6th-order Butterworth low-pass then 4:1 down to 11.025 kHz (`Decimator.kt`).
   Chroma only cares about roughly 55 Hz to 5 kHz, and dropping the rate quadruples the frequency
   resolution of a fixed-size FFT. That is what lets it separate adjacent bass notes.
3. **Chroma** — 8192-point FFT (743 ms window, 186 ms hop), spectral whitening, peak picking with
   parabolic interpolation, then each peak votes for the pitch class of every fundamental it could
   plausibly be a harmonic of (`ChromaExtractor.kt`). Whitening is the part that matters: without
   it a bright synth and a dull piano playing the same chord give very different profiles, because
   raw magnitudes are dominated by timbre rather than by which notes are sounding.
4. **Accumulate** — a rolling peak-normalised average over the last 6 / 14 / 30 seconds
   (`ChromaAccumulator.kt`). A key is a property of a passage, not of one window, and normalising
   each frame stops a loud chorus outvoting a quiet intro.
5. **Tuning** — the phase of the period-3 component of the 36-bin profile gives the reference pitch
   to within a couple of cents, so records cut at A=432 or A=443 still land on the right bins.
6. **Key** — Pearson correlation of the folded 12-bin chroma against all 24 rotated key profiles
   (`KeyDetector.kt`). Confidence is the absolute fit times the margin over the runner-up; a chroma
   that fits C major and A minor equally well is reported as low confidence rather than as a coin
   flip.

Three published profiles are selectable in the app:

| Profile | Origin | Best for |
| --- | --- | --- |
| Shaath (default) | Tuned on electronic and pop | Club and pop records |
| Krumhansl | Probe-tone experiments | Classical |
| Temperley | Derived from notated scores | Clearly diatonic material |

Tempo is a separate path: log-compressed spectral flux at a 256-sample hop, autocorrelated over
12 seconds with a log-normal prior around 120 BPM (`TempoTracker.kt`).

## What it will and will not do

- It listens to the **room**, through the mic. Android does not let one app capture another app's
  audio output without that app opting in, so it cannot tap Spotify directly. Play the track out
  loud, or see the extension note below.
- Relative major / minor is the classic hard case — C major and A minor contain the same notes and
  differ only in emphasis. When it is genuinely ambiguous the confidence bar drops and the
  runners-up line shows you the alternative. Trust a reading more once the LOCK badge appears.
- Modal and chromatic material (a lot of jazz, a lot of film score) does not have one answer, and a
  24-way major/minor classifier will force one anyway.
- Tempo is half/double-time prone, as every onset autocorrelator is. Tap it if it looks wrong.
- **Chords are the weakest readout and are meant to be.** They move faster than the 743 ms window,
  so a frame straddling a change sees both at once; a seventh shares three of its four notes with
  its relative triad; and an inversion is identical to root position in a chroma, which threw the
  octave away by construction. Read it as a strong hint, not a transcription.

## Tuning knobs

Everything worth adjusting is a constant near the top of its file:

- `KeyScopeEngine.LOCK_HOPS` — how long a reading must hold before it is called stable. This is
  also what decides how quickly the mic lets go, so shorten it if locks feel slow.
- `KeyScopeEngine.SILENCE_RMS` — the gate below which frames are ignored.
- `ChromaExtractor.HARMONIC_DECAY` / `HARMONICS` — how strongly upper partials vote for their root.
  Raising the decay helps on harmonically rich material and hurts on sparse material.
- `ChromaExtractor.F_MIN` / `F_MAX` — narrow this if room rumble or cymbals are throwing it off.
- `KeyDetector.confidence` — the 0.18 margin constant sets how decisive a win has to be.

## Possible extensions

- **Tabletop layout.** Add `androidx.window:window` and read `FoldingFeature` to put the readout
  above the hinge and the meters below when the phone is half-open.
- **Internal audio capture.** `AudioPlaybackCaptureConfiguration` plus a `MediaProjection` consent
  prompt would let it analyse audio playing on the phone itself, for apps that allow capture. Many
  music apps set `ALLOW_CAPTURE_BY_NONE`, so it works for some sources and not others.
- **Shorter window for live chords.** Files got their own 372 ms frame; the live path still shares
  the key path's 743 ms window, which is the main thing limiting it.
- **Persisting file results.** The batch list dies with the process. Keeping it would make a
  sample library searchable rather than a single sitting.
- **Line input.** `AudioRecord.setPreferredDevice` could target a USB interface instead of the
  built-in mic, which would be close to file quality but live.

## Layout

```
app/src/main/java/com/jonny/keyscope/
  MainActivity.kt            permissions, clipboard, share, file picking, wiring
  MusicalKey.kt              naming, scale spelling, Camelot / Open Key, chords, transposition
  Progressions.kt            common progressions as scale degrees, plus their voicings
  TapTempo.kt                median-interval tempo from taps
  audio/
    KeyScopeEngine.kt        microphone, analysis thread, state, auto-release on lock
    ListeningService.kt      foreground service so it survives app switching
    ReferenceTone.kt         polyphonic tonic, scale and chord playback at the detected pitch
    Metronome.kt             drift-free click track
    ProjectSettings.kt       the key and tempo you are working in, persisted
    AudioFileDecoder.kt      MediaExtractor + MediaCodec, streamed and resampled
    FileAnalyzer.kt          the live pipeline run over a file
    FileAnalysisController.kt  batch queue and CSV export
    EngineState.kt           the state the UI reads
  dsp/
    AnalysisConfig.kt        geometry shared by the live and file paths
    Fft.kt                   allocation-free radix-2 FFT
    Resampler.kt             arbitrary-rate resampling for decoded files
    ChordDetector.kt         chord templates, key-biased, with commit smoothing
    Decimator.kt             Butterworth low-pass + 4:1 decimation
    ChromaExtractor.kt       HPCP
    ChromaAccumulator.kt     rolling average + tuning estimation
    KeyDetector.kt           key profiles and correlation
    TempoTracker.kt          onset flux + autocorrelation
  ui/                        Compose screen and theme
app/src/test/java/com/jonny/keyscope/DspTest.kt
```
