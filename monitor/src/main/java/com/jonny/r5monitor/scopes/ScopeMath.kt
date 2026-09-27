package com.jonny.r5monitor.scopes

import kotlin.math.ln
import kotlin.math.min

/**
 * What the scopes draw, for one frame.
 *
 * Luma is Rec.709 Y' taken straight from the gamma-encoded JPEG, which is what a waveform on a
 * camera or an external monitor shows too, so 100 here is the same 100 as on set.
 */
class ScopeData(
    val luma: IntArray,
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    /** Column-major counts: `waveform[level * columns + column]`, level 0 is black. */
    val waveform: IntArray,
    val columns: Int,
    val levels: Int,
    val samples: Int,
    /** Share of samples at 100 and at 0, for the clipping readout. */
    val clippedHigh: Float,
    val clippedLow: Float
)

object ScopeMath {

    const val HISTOGRAM_BINS = 64

    fun luma(r: Int, g: Int, b: Int): Int = (r * 2126 + g * 7152 + b * 722 + 5000) / 10000

    /**
     * Samples every [step]th pixel in each direction. At the size CCAPI sends live view that is
     * still tens of thousands of samples, which is plenty for a scope and cheap enough per frame.
     */
    fun analyze(
        pixels: IntArray,
        width: Int,
        height: Int,
        step: Int = 3,
        columns: Int = 180,
        levels: Int = 128
    ): ScopeData {
        val luma = IntArray(HISTOGRAM_BINS)
        val red = IntArray(HISTOGRAM_BINS)
        val green = IntArray(HISTOGRAM_BINS)
        val blue = IntArray(HISTOGRAM_BINS)
        val cols = min(columns, width).coerceAtLeast(1)
        val wave = IntArray(cols * levels)
        var samples = 0
        var high = 0
        var low = 0
        var y = 0
        while (y < height) {
            val row = y * width
            var x = 0
            while (x < width) {
                val p = pixels[row + x]
                val r = p ushr 16 and 255
                val g = p ushr 8 and 255
                val b = p and 255
                val l = luma(r, g, b)
                luma[l * HISTOGRAM_BINS / 256]++
                red[r * HISTOGRAM_BINS / 256]++
                green[g * HISTOGRAM_BINS / 256]++
                blue[b * HISTOGRAM_BINS / 256]++
                val col = x * cols / width
                val level = l * (levels - 1) / 255
                wave[level * cols + col]++
                if (l >= 254) high++
                if (l <= 1) low++
                samples++
                x += step
            }
            y += step
        }
        val n = samples.coerceAtLeast(1).toFloat()
        return ScopeData(luma, red, green, blue, wave, cols, levels, samples, high / n, low / n)
    }

    /**
     * Renders the waveform to ARGB, top row = 100. Intensity is logarithmic in the count so a thin
     * highlight still shows next to a large flat area, the way a phosphor trace would.
     */
    fun waveformPixels(data: ScopeData, color: Int = 0x9CFFB0): IntArray {
        val cols = data.columns
        val levels = data.levels
        val out = IntArray(cols * levels)
        val perColumn = data.samples.toFloat() / cols
        val norm = ln(1f + (perColumn / 6f).coerceAtLeast(1f))
        for (level in 0 until levels) {
            val outRow = (levels - 1 - level) * cols
            val inRow = level * cols
            for (c in 0 until cols) {
                val count = data.waveform[inRow + c]
                if (count == 0) continue
                val a = (ln(1f + count) / norm).coerceIn(0.18f, 1f)
                out[outRow + c] = ((a * 255).toInt() shl 24) or color
            }
        }
        return out
    }
}
