package com.jonny.r5monitor.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import com.jonny.r5monitor.ViewPrefs

/**
 * Zebras, focus peaking and false colour, done per pixel on the GPU in one AGSL pass.
 *
 * Coordinates arrive in bitmap pixels because the canvas is scaled to the bitmap before drawing,
 * so the Sobel neighbours are real source pixels however large the picture is on screen.
 */
private const val MONITOR_SHADER = """
uniform shader image;
uniform float zebra;
uniform float peaking;
uniform half4 peakColor;
uniform float falseColor;

half luma(half3 c) {
    return dot(c, half3(0.2126, 0.7152, 0.0722));
}

half lumaAt(float2 p) {
    return luma(image.eval(p).rgb);
}

// Bands in percent of full scale: crushed, deep shadow, 18% grey, one stop over grey
// (skin), near clip and clipped. Everything between them stays greyscale so the colours read.
half3 falseColorOf(half y) {
    half v = y * 100.0;
    if (v < 2.5) return half3(0.50, 0.00, 0.60);
    if (v < 10.0) return half3(0.05, 0.25, 0.95);
    if (v >= 40.0 && v < 46.0) return half3(0.20, 0.80, 0.25);
    if (v >= 54.0 && v < 60.0) return half3(1.00, 0.55, 0.70);
    if (v >= 93.0 && v < 98.0) return half3(1.00, 0.90, 0.10);
    if (v >= 98.0) return half3(1.00, 0.10, 0.10);
    return half3(y * 0.85);
}

half4 main(float2 p) {
    half4 c = image.eval(p);
    half y = luma(c.rgb);
    half3 outColor = c.rgb;

    if (falseColor > 0.5) {
        outColor = falseColorOf(y);
    }

    if (peaking > 0.0) {
        half tl = lumaAt(p + float2(-1.0, -1.0));
        half t  = lumaAt(p + float2( 0.0, -1.0));
        half tr = lumaAt(p + float2( 1.0, -1.0));
        half l  = lumaAt(p + float2(-1.0,  0.0));
        half r  = lumaAt(p + float2( 1.0,  0.0));
        half bl = lumaAt(p + float2(-1.0,  1.0));
        half b  = lumaAt(p + float2( 0.0,  1.0));
        half br = lumaAt(p + float2( 1.0,  1.0));
        half gx = (tr + 2.0 * r + br) - (tl + 2.0 * l + bl);
        half gy = (bl + 2.0 * b + br) - (tl + 2.0 * t + tr);
        if (sqrt(gx * gx + gy * gy) > peaking) {
            outColor = peakColor.rgb;
        }
    }

    if (zebra > 0.0 && y >= zebra) {
        if (mod(p.x + p.y, 8.0) < 4.0) {
            outColor = half3(0.0);
        }
    }

    return half4(outColor, 1.0);
}
"""

/** True when the prefs ask for anything the shader does. */
fun ViewPrefs.needsShader() = zebra || peaking || falseColor

/** Peaking sensitivity 0..1 to a Sobel threshold: more sensitive means a lower threshold. */
fun peakingThreshold(sensitivity: Float): Float = 0.75f - 0.6f * sensitivity.coerceIn(0f, 1f)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MonitorShader {
    private val shader = RuntimeShader(MONITOR_SHADER)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = this@MonitorShader.shader }

    fun bind(bitmap: Bitmap, prefs: ViewPrefs) {
        val input = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
        shader.setInputShader("image", input)
        shader.setFloatUniform("zebra", if (prefs.zebra) prefs.zebraLevel / 100f else 0f)
        shader.setFloatUniform("peaking", if (prefs.peaking) peakingThreshold(prefs.peakingSensitivity) else 0f)
        val pc = Color(prefs.peakingColor.argb)
        shader.setFloatUniform("peakColor", pc.red, pc.green, pc.blue, 1f)
        shader.setFloatUniform("falseColor", if (prefs.falseColor) 1f else 0f)
    }

    companion object {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}

/** The false colour key, in the same order as the bands in the shader. */
val falseColorLegend = listOf(
    "Clip" to Color(0xFFFF1A1A),
    "93+" to Color(0xFFFFE619),
    "Skin 55" to Color(0xFFFF8CB3),
    "Grey 43" to Color(0xFF33CC40),
    "< 10" to Color(0xFF0D40F2),
    "Crush" to Color(0xFF800099)
)
