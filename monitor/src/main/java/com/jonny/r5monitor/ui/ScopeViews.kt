package com.jonny.r5monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonny.r5monitor.Scopes

private val ScopeBackground = Color(0xE0000000)
private val Graticule = Color(0x40FFFFFF)

/** Luma waveform with a 0/50/100 graticule. The top line is where the camera clips. */
@Composable
fun Waveform(scopes: () -> Scopes?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(ScopeBackground)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
            for (level in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val y = size.height * (1f - level)
                drawLine(
                    if (level == 1f || level == 0f) Graticule.copy(alpha = 0.45f) else Graticule,
                    Offset(0f, y), Offset(size.width, y), 1f
                )
            }
            val s = scopes() ?: return@Canvas
            drawImage(
                s.waveform.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(s.waveform.width, s.waveform.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low
            )
        }
        ScopeLabel("WFM", Modifier.padding(6.dp))
    }
}

/** RGB histogram over a grey luma fill, drawn additively so overlapping channels go white. */
@Composable
fun Histogram(scopes: () -> Scopes?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(ScopeBackground)) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            for (i in 1..3) {
                val x = size.width * i / 4f
                drawLine(Graticule, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            val s = scopes() ?: return@Canvas
            val d = s.data
            val peak = maxOf(d.luma.max(), d.red.max(), d.green.max(), d.blue.max()).coerceAtLeast(1)
            drawPath(histogramPath(d.luma, peak, size, closed = true), Color(0x66D0D0D8))
            val stroke = Stroke(1.4f * density)
            drawPath(histogramPath(d.red, peak, size), Color(0xCCFF4040), style = stroke, blendMode = BlendMode.Plus)
            drawPath(histogramPath(d.green, peak, size), Color(0xCC40FF60), style = stroke, blendMode = BlendMode.Plus)
            drawPath(histogramPath(d.blue, peak, size), Color(0xCC4080FF), style = stroke, blendMode = BlendMode.Plus)
        }
        ScopeLabel("HIST", Modifier.padding(6.dp))
    }
}

private fun histogramPath(bins: IntArray, peak: Int, size: Size, closed: Boolean = false): Path {
    val path = Path()
    val step = size.width / (bins.size - 1)
    // Square root keeps a small highlight region visible next to a big mid-tone lump.
    fun y(v: Int) = size.height * (1f - kotlin.math.sqrt(v.toFloat() / peak))
    if (closed) path.moveTo(0f, size.height) else path.moveTo(0f, y(bins[0]))
    for (i in bins.indices) path.lineTo(i * step, y(bins[i]))
    if (closed) {
        path.lineTo(size.width, size.height)
        path.close()
    }
    return path
}

@Composable
private fun ScopeLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 9.sp,
        style = MaterialTheme.typography.labelSmall
    )
}
