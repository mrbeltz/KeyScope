package com.jonny.keyscope.dsp

/**
 * The analysis geometry, shared by the live microphone path and the file path so that a sample
 * and the room it is played in are measured exactly the same way.
 */
object AnalysisConfig {
    /** 11.025 kHz: chroma only needs 55 Hz to 5 kHz, and a lower rate buys frequency resolution. */
    const val WORK_RATE = 11025

    /** 743 ms of audio per frame. Long, because a key is a property of a passage. */
    const val FFT_SIZE = 8192

    /** 186 ms between frames. */
    const val HOP = 2048
}
