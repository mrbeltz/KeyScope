package com.jonny.r5monitor

import com.jonny.r5monitor.scopes.ScopeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeMathTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun lumaUsesRec709Weights() {
        assertEquals(0, ScopeMath.luma(0, 0, 0))
        assertEquals(255, ScopeMath.luma(255, 255, 255))
        // Green carries most of the luma, blue the least.
        assertTrue(ScopeMath.luma(0, 255, 0) > ScopeMath.luma(255, 0, 0))
        assertTrue(ScopeMath.luma(255, 0, 0) > ScopeMath.luma(0, 0, 255))
    }

    @Test
    fun leftBlackRightWhiteLandsInTheRightColumnsAndLevels() {
        val w = 100
        val h = 10
        val pixels = IntArray(w * h) { i -> if (i % w < 50) rgb(0, 0, 0) else rgb(255, 255, 255) }
        val d = ScopeMath.analyze(pixels, w, h, step = 1, columns = 10, levels = 16)

        assertEquals(1000, d.samples)
        assertEquals(0.5f, d.clippedHigh, 1e-6f)
        assertEquals(0.5f, d.clippedLow, 1e-6f)
        // Columns 0-4 only at level 0, columns 5-9 only at the top level.
        for (c in 0 until 10) {
            val bottom = d.waveform[0 * d.columns + c]
            val top = d.waveform[15 * d.columns + c]
            if (c < 5) {
                assertEquals(100, bottom); assertEquals(0, top)
            } else {
                assertEquals(0, bottom); assertEquals(100, top)
            }
        }
        assertEquals(500, d.luma[0])
        assertEquals(500, d.luma[ScopeMath.HISTOGRAM_BINS - 1])
    }

    @Test
    fun waveformPixelsPutWhiteAtTheTop() {
        val pixels = IntArray(20 * 4) { rgb(255, 255, 255) }
        val d = ScopeMath.analyze(pixels, 20, 4, step = 1, columns = 4, levels = 8)
        val out = ScopeMath.waveformPixels(d)
        // Row 0 of the output is 100; the bottom row is empty.
        for (c in 0 until 4) {
            assertTrue(out[c] ushr 24 > 0)
            assertEquals(0, out[7 * 4 + c])
        }
    }
}
