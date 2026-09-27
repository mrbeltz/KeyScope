package com.jonny.r5monitor.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.jonny.r5monitor.LiveFrame
import com.jonny.r5monitor.ViewPrefs

/**
 * The picture, with everything a monitor draws on top of it.
 *
 * Pinch to punch in and drag to move around; double tap toggles 1x and 3x at the tapped point.
 * Punching in only magnifies the live view JPEG, so it is for checking focus, not for detail.
 */
@Composable
fun LiveView(
    frame: () -> LiveFrame?,
    prefs: ViewPrefs,
    modifier: Modifier = Modifier,
    onZoomChange: (Float) -> Unit = {}
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val shader = remember { if (MonitorShader.supported) MonitorShader() else null }
    val bitmapPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    // How far the picture can move at the current zoom, as last drawn. Not state: the gesture
    // only needs to read it, and writing state from a draw pass would loop.
    val panLimit = remember { FloatArray(2) }

    fun setZoom(next: Float, focus: Offset, size: Size) {
        val clamped = next.coerceIn(1f, 6f)
        // Keep the point under the fingers still while the scale changes.
        val center = Offset(size.width / 2f, size.height / 2f)
        val scale = clamped / zoom
        pan = if (clamped == 1f) Offset.Zero else (pan - (focus - center)) * scale + (focus - center)
        zoom = clamped
        onZoomChange(clamped)
    }

    Box(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, delta, gestureZoom, _ ->
                    val s = Size(size.width.toFloat(), size.height.toFloat())
                    if (gestureZoom != 1f) setZoom(zoom * gestureZoom, centroid, s)
                    if (zoom > 1f) {
                        val next = pan + delta
                        pan = Offset(
                            next.x.coerceIn(-panLimit[0], panLimit[0]),
                            next.y.coerceIn(-panLimit[1], panLimit[1])
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { at ->
                    val s = Size(size.width.toFloat(), size.height.toFloat())
                    setZoom(if (zoom > 1f) 1f else 3f, at, s)
                })
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val current = frame() ?: return@Canvas
            val bmp = current.bitmap
            if (bmp.isRecycled) return@Canvas

            val fitted = fit(bmp.width * prefs.desqueeze, bmp.height.toFloat(), size)
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxPan = Offset(
                (fitted.width * zoom - size.width).coerceAtLeast(0f) / 2f,
                (fitted.height * zoom - size.height).coerceAtLeast(0f) / 2f
            )
            panLimit[0] = maxPan.x
            panLimit[1] = maxPan.y
            val c = center + Offset(pan.x.coerceIn(-maxPan.x, maxPan.x), pan.y.coerceIn(-maxPan.y, maxPan.y))
            val halfW = fitted.width * zoom / 2f
            val halfH = fitted.height * zoom / 2f
            val shown = Rect(c.x - halfW, c.y - halfH, c.x + halfW, c.y + halfH)

            clipRect {
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    nc.save()
                    nc.translate(shown.left, shown.top)
                    nc.scale(shown.width / bmp.width, shown.height / bmp.height)
                    if (prefs.flipH || prefs.flipV) {
                        nc.scale(
                            if (prefs.flipH) -1f else 1f,
                            if (prefs.flipV) -1f else 1f,
                            bmp.width / 2f,
                            bmp.height / 2f
                        )
                    }
                    if (shader != null && prefs.needsShader()) {
                        shader.bind(bmp, prefs)
                        nc.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), shader.paint)
                    } else {
                        nc.drawBitmap(bmp, 0f, 0f, bitmapPaint)
                    }
                    nc.restore()
                }
                drawGuides(shown, prefs)
            }
        }
    }
}

/** Largest rect of the given aspect that fits inside [bounds], centred. */
private fun fit(width: Float, height: Float, bounds: Size): Size {
    if (width <= 0f || height <= 0f) return bounds
    val scale = minOf(bounds.width / width, bounds.height / height)
    return Size(width * scale, height * scale)
}

private fun DrawScope.drawGuides(image: Rect, prefs: ViewPrefs) {
    val line = Color.White.copy(alpha = 0.55f)
    val stroke = 1.2f * density

    var frameRect = image
    prefs.aspect?.let { guide ->
        val imageRatio = image.width / image.height
        frameRect = if (guide.ratio > imageRatio) {
            val h = image.width / guide.ratio
            Rect(image.left, image.center.y - h / 2f, image.right, image.center.y + h / 2f)
        } else {
            val w = image.height * guide.ratio
            Rect(image.center.x - w / 2f, image.top, image.center.x + w / 2f, image.bottom)
        }
        val mask = Color.Black.copy(alpha = 0.62f)
        // Shade outside the frame, then outline it.
        drawRect(mask, Offset(image.left, image.top), Size(image.width, frameRect.top - image.top))
        drawRect(mask, Offset(image.left, frameRect.bottom), Size(image.width, image.bottom - frameRect.bottom))
        drawRect(mask, Offset(image.left, frameRect.top), Size(frameRect.left - image.left, frameRect.height))
        drawRect(mask, Offset(frameRect.right, frameRect.top), Size(image.right - frameRect.right, frameRect.height))
        drawRect(line, frameRect.topLeft, frameRect.size, style = Stroke(stroke))
    }

    val r = frameRect
    if (prefs.thirds) {
        for (i in 1..2) {
            val x = r.left + r.width * i / 3f
            val y = r.top + r.height * i / 3f
            drawLine(line, Offset(x, r.top), Offset(x, r.bottom), stroke)
            drawLine(line, Offset(r.left, y), Offset(r.right, y), stroke)
        }
    }
    if (prefs.center) {
        val arm = 14f * density
        drawLine(line, Offset(r.center.x - arm, r.center.y), Offset(r.center.x + arm, r.center.y), stroke * 1.5f)
        drawLine(line, Offset(r.center.x, r.center.y - arm), Offset(r.center.x, r.center.y + arm), stroke * 1.5f)
    }
    if (prefs.safeArea) {
        // 90% action safe.
        val inset = Offset(r.width * 0.05f, r.height * 0.05f)
        drawRect(
            line.copy(alpha = 0.4f),
            r.topLeft + inset,
            Size(r.width * 0.9f, r.height * 0.9f),
            style = Stroke(stroke)
        )
    }
}
