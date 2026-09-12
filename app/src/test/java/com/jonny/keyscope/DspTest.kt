package com.jonny.keyscope

import com.jonny.keyscope.dsp.ChordDetector
import com.jonny.keyscope.dsp.ChordQuality
import com.jonny.keyscope.dsp.ChromaAccumulator
import com.jonny.keyscope.dsp.ChromaExtractor
import com.jonny.keyscope.dsp.Fft
import com.jonny.keyscope.dsp.KeyDetector
import com.jonny.keyscope.dsp.MelodyExtractor
import com.jonny.keyscope.dsp.Resampler
import com.jonny.keyscope.audio.FileAnalyzer
import com.jonny.keyscope.audio.FolderScanner
import com.jonny.keyscope.midi.MidiExport
import com.jonny.keyscope.midi.MidiWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The analysis chain is plain Kotlin with no Android dependencies, so the interesting half of
 * the app can be tested on the JVM: run ./gradlew test.
 */
class DspTest {

    private val sampleRate = 11025
    private val fftSize = 8192
    private val hop = 2048

    // ------------------------------------------------------------- FFT

    @Test
    fun `fft matches a naive dft`() {
        val n = 256
        val random = Random(7)
        val signal = FloatArray(n) { random.nextFloat() * 2f - 1f }

        val re = signal.copyOf()
        val im = FloatArray(n)
        Fft(n).forward(re, im)

        for (k in intArrayOf(0, 1, 5, 37, 128, 200)) {
            var dre = 0.0
            var dim = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                dre += signal[t] * cos(angle)
                dim += signal[t] * sin(angle)
            }
            assertEquals("real bin $k", dre, re[k].toDouble(), 1e-2)
            assertEquals("imag bin $k", dim, im[k].toDouble(), 1e-2)
        }
    }

    // ------------------------------------------------------------- key detection

    @Test
    fun `detects a major progression`() {
        // I - V - vi - IV in C major.
        val signal = render(
            listOf(
                triad(60, MAJOR) to 3.0,
                triad(55, MAJOR) to 2.0,
                triad(57, MINOR) to 2.0,
                triad(53, MAJOR) to 2.0
            )
        )
        val key = detect(signal)
        assertEquals("C major", key.name)
        assertEquals("8B", key.camelot)
    }

    @Test
    fun `detects a minor progression`() {
        // i - iv - v - i in A minor.
        val signal = render(
            listOf(
                triad(57, MINOR) to 3.0,
                triad(62, MINOR) to 2.0,
                triad(64, MINOR) to 2.0,
                triad(57, MINOR) to 3.0
            )
        )
        val key = detect(signal)
        assertEquals("A minor", key.name)
        assertEquals("8A", key.camelot)
    }

    @Test
    fun `finds the tonic of a transposed progression`() {
        // The same major progression moved up three semitones should land on Eb major.
        val signal = render(
            listOf(
                triad(63, MAJOR) to 3.0,
                triad(58, MAJOR) to 2.0,
                triad(60, MINOR) to 2.0,
                triad(56, MAJOR) to 2.0
            )
        )
        assertEquals("Eb major", detect(signal).name)
    }

    @Test
    fun `estimates a flat reference tuning`() {
        val signal = render(
            listOf(triad(60, MAJOR) to 3.0, triad(55, MAJOR) to 3.0),
            reference = 432.0
        )
        val cents = tuningOf(signal)
        // 1200 * log2(432/440) is about -31.8 cents.
        assertTrue("estimated $cents cents, expected about -31.8", abs(cents + 31.8f) < 9f)
    }

    @Test
    fun `estimates a sharp reference tuning`() {
        // Pins the sign in the other direction too. Getting this backwards is silent damage:
        // fold() would shift the chroma further off instead of correcting it.
        val signal = render(
            listOf(triad(60, MAJOR) to 3.0, triad(55, MAJOR) to 3.0),
            reference = 449.0
        )
        val cents = tuningOf(signal)
        // 1200 * log2(449/440) is about +35.1 cents.
        assertTrue("estimated $cents cents, expected about +35.1", abs(cents - 35.1f) < 9f)
    }

    @Test
    fun `a recording cut flat still lands on the right key`() {
        val signal = render(
            listOf(
                triad(60, MAJOR) to 3.0,
                triad(55, MAJOR) to 2.0,
                triad(57, MINOR) to 2.0,
                triad(53, MAJOR) to 2.0
            ),
            reference = 432.0
        )
        assertEquals("C major", detect(signal).name)
    }

    // ------------------------------------------------------------- naming

    @Test
    fun `spells scales with one letter per degree`() {
        assertEquals(
            listOf("F#", "G#", "A", "B", "C#", "D", "E"),
            MusicalKey(6, Mode.MINOR).scaleNotes
        )
        assertEquals(
            listOf("Eb", "F", "G", "Ab", "Bb", "C", "D"),
            MusicalKey(3, Mode.MAJOR).scaleNotes
        )
        assertEquals(
            listOf("C", "D", "E", "F", "G", "A", "B"),
            MusicalKey(0, Mode.MAJOR).scaleNotes
        )
    }

    @Test
    fun `camelot and open key codes follow the wheel`() {
        assertEquals("8B", MusicalKey(0, Mode.MAJOR).camelot)   // C major
        assertEquals("8A", MusicalKey(9, Mode.MINOR).camelot)   // A minor
        assertEquals("11A", MusicalKey(6, Mode.MINOR).camelot)  // F# minor
        assertEquals("1d", MusicalKey(0, Mode.MAJOR).openKey)
        assertEquals("1m", MusicalKey(9, Mode.MINOR).openKey)

        // Every key must map to a distinct wheel position.
        val codes = (0 until 12).flatMap { pc ->
            listOf(MusicalKey(pc, Mode.MAJOR).camelot, MusicalKey(pc, Mode.MINOR).camelot)
        }
        assertEquals(24, codes.toSet().size)
    }

    @Test
    fun `builds the diatonic chords of a key`() {
        assertEquals(
            listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"),
            MusicalKey(0, Mode.MAJOR).diatonicChords.map { it.name }
        )
        assertEquals(
            listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"),
            MusicalKey(0, Mode.MAJOR).diatonicChords.map { it.numeral }
        )
        assertEquals(
            listOf("Am", "Bdim", "C", "Dm", "Em", "F", "G"),
            MusicalKey(9, Mode.MINOR).diatonicChords.map { it.name }
        )
        assertEquals(
            listOf("i", "ii°", "III", "iv", "v", "VI", "VII"),
            MusicalKey(9, Mode.MINOR).diatonicChords.map { it.numeral }
        )
    }

    @Test
    fun `transposition takes the shortest path`() {
        val cMajor = MusicalKey(0, Mode.MAJOR)
        assertEquals(0, cMajor.semitonesTo(cMajor))
        assertEquals(2, cMajor.semitonesTo(MusicalKey(2, Mode.MAJOR)))   // up to D
        assertEquals(-5, cMajor.semitonesTo(MusicalKey(7, Mode.MAJOR)))  // G is nearer downward
        assertEquals(-1, cMajor.semitonesTo(MusicalKey(11, Mode.MAJOR))) // down to B

        // Every target stays inside the range that will not wreck a track.
        for (pc in 0 until 12) {
            val shift = cMajor.semitonesTo(MusicalKey(pc, Mode.MAJOR))
            assertTrue("shift $shift for pc $pc", shift in -6..5)
        }
    }

    @Test
    fun `varispeed percentage matches the semitone move`() {
        val cMajor = MusicalKey(0, Mode.MAJOR)
        // One semitone up on a turntable is the classic +5.95%.
        val up = cMajor.varispeedPercentTo(MusicalKey(1, Mode.MAJOR))
        assertTrue("got $up", abs(up - 5.946f) < 0.01f)
        // And the reciprocal move downward is a slightly smaller number, not the same one.
        val down = cMajor.varispeedPercentTo(MusicalKey(11, Mode.MAJOR))
        assertTrue("got $down", abs(down + 5.613f) < 0.01f)
    }

    @Test
    fun `note names round-trip to pitch classes`() {
        assertEquals(0, MusicalKey.pitchClassOf("C"))
        assertEquals(6, MusicalKey.pitchClassOf("F#"))
        assertEquals(10, MusicalKey.pitchClassOf("Bb"))
        for (pc in 0 until 12) {
            for (mode in Mode.entries) {
                val key = MusicalKey(pc, mode)
                assertEquals(key.name, pc, MusicalKey.pitchClassOf(key.tonicName))
            }
        }
    }

    @Test
    fun `reference frequencies follow the detected tuning`() {
        // A in the octave above middle C is A4 = 440 at standard pitch.
        assertEquals(440.0, MusicalKey.frequencyOf(9, 440f).toDouble(), 0.01)
        assertEquals(261.626, MusicalKey.frequencyOf(0, 440f).toDouble(), 0.01)
        // A record cut to A=432 should sound its tonic proportionally flat.
        assertEquals(432.0, MusicalKey.frequencyOf(9, 432f).toDouble(), 0.01)
    }

    // ------------------------------------------------------------- chords

    private fun chromaOf(vararg pitchClasses: Int): FloatArray {
        val chroma = FloatArray(12)
        pitchClasses.forEach { chroma[Math.floorMod(it, 12)] = 1f }
        return chroma
    }

    @Test
    fun `matches plain triads`() {
        val detector = ChordDetector()
        val cMajor = detector.match(chromaOf(0, 4, 7), null)!!
        assertEquals(0, cMajor.root)
        assertEquals(ChordQuality.MAJOR, cMajor.quality)

        val aMinor = detector.match(chromaOf(9, 0, 4), null)!!
        assertEquals(9, aMinor.root)
        assertEquals(ChordQuality.MINOR, aMinor.quality)

        val gDominant = detector.match(chromaOf(7, 11, 2, 5), null)!!
        assertEquals(7, gDominant.root)
        assertEquals(ChordQuality.DOMINANT7, gDominant.quality)
    }

    @Test
    fun `does not invent a chord out of noise`() {
        val detector = ChordDetector()
        // Every pitch class at once fits nothing in particular.
        assertEquals(null, detector.match(FloatArray(12) { 1f }, null))
        assertEquals(null, detector.match(FloatArray(12), null))
    }

    @Test
    fun `chord names are spelled inside the key`() {
        val detector = ChordDetector()
        val fMajor = MusicalKey(5, Mode.MAJOR)
        // The fourth degree of F major is Bb, and must not be shown as A#.
        val bFlat = detector.match(chromaOf(10, 2, 5), fMajor)!!
        assertEquals("Bb", bFlat.name(fMajor))
        assertEquals("A#", bFlat.name(null))
    }

    @Test
    fun `tracking needs agreement before it commits`() {
        val detector = ChordDetector()
        val c = chromaOf(0, 4, 7)
        // One frame is not enough to put something on screen.
        assertEquals(null, detector.track(c, null))
        assertEquals(0, detector.track(c, null)?.root)
    }

    // ------------------------------------------------------------- resampling

    @Test
    fun `resampling preserves the pitch of a tone`() {
        // A 440 Hz tone crosses zero 880 times a second whatever the sample rate.
        val source = 48000
        val input = FloatArray(source) { sin(2.0 * PI * 440.0 * it / source).toFloat() }
        val resampler = Resampler(source, sampleRate)
        val output = FloatArray(resampler.maxOutput(input.size))
        val written = resampler.process(input, input.size, output)

        val expectedLength = sampleRate
        assertTrue("wrote $written, expected about $expectedLength", abs(written - expectedLength) < 20)

        var crossings = 0
        for (i in 1 until written) {
            if ((output[i - 1] < 0f) != (output[i] < 0f)) crossings++
        }
        assertTrue("counted $crossings crossings, expected about 880", abs(crossings - 880) < 12)
    }

    @Test
    fun `resampling attenuates content above the new nyquist`() {
        // 7 kHz would fold down to 4 kHz as a phantom tone in the middle of the chroma range, so
        // it has to be attenuated on the way through rather than aliased.
        //
        // The cutoff is 0.40 of the destination rate, matching the live microphone path exactly,
        // which puts 7 kHz two thirds of an octave up. A 6th-order Butterworth gives about 27 dB
        // there. That is the real figure; asserting anything much tighter would be asserting a
        // filter this is not.
        val source = 48000
        val input = FloatArray(source) { sin(2.0 * PI * 7000.0 * it / source).toFloat() }
        val inputRms = sqrt(input.sumOf { it * it.toDouble() } / input.size)

        val resampler = Resampler(source, sampleRate)
        val output = FloatArray(resampler.maxOutput(input.size))
        val written = resampler.process(input, input.size, output)

        var energy = 0.0
        // Skip the filter's start-up transient.
        for (i in 500 until written) energy += output[i] * output[i].toDouble()
        val rms = sqrt(energy / (written - 500))

        assertTrue("survived at rms $rms", rms < 0.05)
        assertTrue("only attenuated to ${rms / inputRms} of input", rms / inputRms < 0.07)
    }

    @Test
    fun `resampling passes content the chroma actually needs`() {
        // The other half of the trade: 1 kHz is squarely inside the range the chroma reads, and
        // must come through essentially untouched.
        val source = 48000
        val input = FloatArray(source) { sin(2.0 * PI * 1000.0 * it / source).toFloat() }
        val inputRms = sqrt(input.sumOf { it * it.toDouble() } / input.size)

        val resampler = Resampler(source, sampleRate)
        val output = FloatArray(resampler.maxOutput(input.size))
        val written = resampler.process(input, input.size, output)

        var energy = 0.0
        for (i in 500 until written) energy += output[i] * output[i].toDouble()
        val rms = sqrt(energy / (written - 500))
        assertTrue("passed at ${rms / inputRms} of input", rms / inputRms > 0.85)
    }

    @Test
    fun `tap tempo reads the median interval`() {
        val tapper = TapTempo()
        var now = 10_000L
        assertEquals(null, tapper.tap(now))
        repeat(5) {
            now += 500 // 120 BPM
            tapper.tap(now)
        }
        val steady = tapper.tap(now + 500)!!
        assertTrue("got $steady", abs(steady - 120f) < 0.5f)

        // One late tap should not drag the answer, because the median throws it away.
        val wobbled = tapper.tap(now + 500 + 900)!!
        assertTrue("got $wobbled", abs(wobbled - 120f) < 6f)
    }

    @Test
    fun `tap tempo restarts after a pause`() {
        val tapper = TapTempo(resetAfterMs = 2000L)
        tapper.tap(0)
        tapper.tap(500)
        assertEquals(2, tapper.tapCount)
        // A long gap is a new count-in, not a very slow tempo.
        assertEquals(null, tapper.tap(10_000))
        assertEquals(1, tapper.tapCount)
    }

    @Test
    fun `octave relationships between tapped and detected tempo`() {
        assertTrue(TapTempo.isOctaveOf(140f, 70f))
        assertTrue(TapTempo.isOctaveOf(70f, 140f))
        assertTrue(!TapTempo.isOctaveOf(140f, 100f))
        assertTrue(TapTempo.isSameTempo(128f, 128.4f))
        assertTrue(!TapTempo.isSameTempo(128f, 140f))
    }

    @Test
    fun `progressions spell into the detected key`() {
        val cMajor = MusicalKey(0, Mode.MAJOR)
        val pop = Progressions.forKey(cMajor).first { it.name == "Pop" }
        assertEquals(listOf("C", "G", "Am", "F"), pop.chordNames(cMajor))
        assertEquals("I - V - vi - IV", pop.numerals(cMajor))

        val aMinor = MusicalKey(9, Mode.MINOR)
        val epic = Progressions.forKey(aMinor).first { it.name == "Epic" }
        assertEquals(listOf("Am", "F", "C", "G"), epic.chordNames(aMinor))

        // Major and minor keys must not be offered each other's progressions.
        assertTrue(Progressions.forKey(cMajor).none { it.name == "Epic" })
    }

    @Test
    fun `progression voicings are playable triads`() {
        val key = MusicalKey(6, Mode.MINOR) // F# minor
        val progression = Progressions.forKey(key).first()
        val voiced = Progressions.voicing(key, progression, 440f)

        assertEquals(progression.degrees.size, voiced.size)
        voiced.forEach { chord ->
            assertEquals(3, chord.size)
            chord.forEach { frequency ->
                // Inside the octave the tone engine and a phone speaker can both manage.
                assertTrue("$frequency Hz", frequency > 200f && frequency < 1100f)
            }
        }
    }

    @Test
    fun `relative keys are reciprocal`() {
        for (pc in 0 until 12) {
            for (mode in Mode.entries) {
                val key = MusicalKey(pc, mode)
                assertEquals(key, key.relative.relative)
            }
        }
    }

    // ------------------------------------------------------------- melody

    private fun tone(midi: Int, seconds: Double, reference: Double = 440.0): FloatArray {
        val frequency = reference * Math.pow(2.0, (midi - 69) / 12.0)
        val count = (seconds * sampleRate).toInt()
        return FloatArray(count) { i ->
            val t = i.toDouble() / sampleRate
            // A couple of harmonics, because a bare sine is an unrealistically easy target.
            ((sin(2 * PI * frequency * t) + 0.4 * sin(4 * PI * frequency * t)) * 0.3).toFloat()
        }
    }

    @Test
    fun `tracks a held note at the right pitch`() {
        val notes = MelodyExtractor.extract(tone(69, 1.0), sampleRate)  // A4
        assertTrue("got ${notes.size} notes", notes.isNotEmpty())
        assertEquals(69, notes.first().midi)
    }

    @Test
    fun `does not drop an octave on a low note`() {
        // The classic autocorrelation failure is reporting half the frequency, so a low note with
        // strong harmonics is the case worth pinning.
        val notes = MelodyExtractor.extract(tone(45, 1.0), sampleRate)  // A2, 110 Hz
        assertTrue("got nothing", notes.isNotEmpty())
        assertEquals(45, notes.first().midi)
    }

    @Test
    fun `separates a sequence of notes`() {
        val line = listOf(60, 62, 64, 65)
        var samples = FloatArray(0)
        line.forEach { samples += tone(it, 0.45) }

        val notes = MelodyExtractor.extract(samples, sampleRate)
        // Boundaries between notes can produce a brief spurious frame, so match on the run of
        // distinct pitches rather than on an exact count.
        val distinct = notes.map { it.midi }.fold(mutableListOf<Int>()) { acc, midi ->
            if (acc.lastOrNull() != midi) acc.add(midi)
            acc
        }
        assertEquals(line, distinct)
    }

    @Test
    fun `reports nothing for silence`() {
        assertTrue(MelodyExtractor.extract(FloatArray(sampleRate), sampleRate).isEmpty())
    }

    @Test
    fun `melody midi keeps the played timing`() {
        val notes = listOf(
            MelodyExtractor.Note(60, 0f, 0.5f),
            MelodyExtractor.Note(64, 0.5f, 1.0f)
        )
        val midi = MidiExport.fromMelody(notes, 120f)
        assertEquals(2, midi.size)
        assertEquals(60, midi[0].pitch)
        // At 120 BPM a beat is half a second, so the second note starts one beat in.
        assertEquals(MidiWriter.TICKS_PER_QUARTER, midi[1].startTick)
    }

    // ------------------------------------------------------------- renaming

    private fun resultFor(name: String, key: MusicalKey?, bpm: Float) =
        FileAnalyzer.Result(name = name, key = key, bpm = bpm)

    @Test
    fun `proposes a name that keeps the original`() {
        val result = resultFor("Loop_01.wav", MusicalKey(9, Mode.MINOR), 128f)
        assertEquals("Loop_01 Am 128.wav", FolderScanner.proposedName("Loop_01.wav", result))
    }

    @Test
    fun `does not stack suffixes on an already tagged file`() {
        val result = resultFor("Loop_01 Am 128.wav", MusicalKey(9, Mode.MINOR), 128f)
        assertEquals(null, FolderScanner.proposedName("Loop_01 Am 128.wav", result))
    }

    @Test
    fun `leaves a file alone when there is no key to add`() {
        val result = resultFor("Drums.wav", null, 120f)
        assertEquals(null, FolderScanner.proposedName("Drums.wav", result))
    }

    @Test
    fun `handles a missing tempo and a missing extension`() {
        val noTempo = resultFor("Pad.wav", MusicalKey(0, Mode.MAJOR), 0f)
        assertEquals("Pad C.wav", FolderScanner.proposedName("Pad.wav", noTempo))

        val noExtension = resultFor("Pad", MusicalKey(0, Mode.MAJOR), 90f)
        assertEquals("Pad C 90", FolderScanner.proposedName("Pad", noExtension))
    }

    // ------------------------------------------------------------- midi

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    private fun readShort(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

    @Test
    fun `writes a structurally valid midi file`() {
        val bytes = MidiWriter.write(listOf(MidiWriter.Note(60, 0, 480)), 120f)

        assertEquals("MThd", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(6, readInt(bytes, 4))
        assertEquals(0, readShort(bytes, 8))    // format 0
        assertEquals(1, readShort(bytes, 10))   // one track
        assertEquals(480, readShort(bytes, 12))
        assertEquals("MTrk", String(bytes, 14, 4, Charsets.US_ASCII))

        // The declared track length has to match what actually follows, or a DAW rejects the file.
        assertEquals(bytes.size, 22 + readInt(bytes, 18))
    }

    @Test
    fun `long delta times survive the variable length encoding`() {
        // A note eight bars in needs a multi-byte delta, which is where hand-rolled MIDI usually
        // goes wrong.
        val far = MidiWriter.TICKS_PER_QUARTER * 32
        val bytes = MidiWriter.write(listOf(MidiWriter.Note(60, far, far + 480)), 120f)
        assertEquals(bytes.size, 22 + readInt(bytes, 18))
    }

    @Test
    fun `a progression exports one triad per bar`() {
        val key = MusicalKey(0, Mode.MAJOR)
        val progression = Progressions.forKey(key).first { it.name == "Pop" }
        val notes = MidiExport.fromProgression(key, progression)

        assertEquals(progression.degrees.size * 3, notes.size)
        // C major, first chord, root position starting on C3.
        assertEquals(listOf(48, 52, 55), notes.take(3).map { it.pitch })
        // Each chord occupies its own bar.
        assertEquals(MidiWriter.TICKS_PER_QUARTER * 4, notes[3].startTick)
    }

    // ------------------------------------------------------------- helpers

    private val MAJOR = intArrayOf(0, 4, 7)
    private val MINOR = intArrayOf(0, 3, 7)

    private fun triad(rootMidi: Int, quality: IntArray) = quality.map { rootMidi + it }

    /** Additive synthesis: six harmonics per note, so the whitening and harmonic summing get used. */
    private fun render(chords: List<Pair<List<Int>, Double>>, reference: Double = 440.0): FloatArray {
        val out = ArrayList<Float>()
        for ((pitches, seconds) in chords) {
            val samples = (seconds * sampleRate).toInt()
            for (i in 0 until samples) {
                var value = 0.0
                val t = i.toDouble() / sampleRate
                // Short fades stop chord boundaries from splattering broadband clicks.
                val fade = minOf(1.0, i / (sampleRate * 0.02), (samples - i) / (sampleRate * 0.02))
                for (midi in pitches) {
                    val f0 = reference * Math.pow(2.0, (midi - 69) / 12.0)
                    for (h in 1..6) {
                        val f = f0 * h
                        if (f > sampleRate * 0.45) break
                        value += sin(2.0 * PI * f * t) / h
                    }
                }
                out.add((value * fade * 0.06).toFloat())
            }
        }
        return out.toFloatArray()
    }

    private fun chroma(signal: FloatArray): FloatArray {
        val extractor = ChromaExtractor(sampleRate, fftSize)
        val accumulator = ChromaAccumulator(4096)
        val frame = FloatArray(fftSize)
        var position = 0
        while (position + fftSize <= signal.size) {
            System.arraycopy(signal, position, frame, 0, fftSize)
            if (extractor.process(frame)) accumulator.add(extractor.profile)
            position += hop
        }
        return accumulator.snapshot()
    }

    private fun tuningOf(signal: FloatArray) = ChromaAccumulator.estimateTuningCents(chroma(signal))

    private fun detect(signal: FloatArray): MusicalKey {
        val profile36 = chroma(signal)
        val folded = ChromaAccumulator.fold(profile36, ChromaAccumulator.estimateTuningCents(profile36))
        return KeyDetector().rank(folded).first().key
    }
}
