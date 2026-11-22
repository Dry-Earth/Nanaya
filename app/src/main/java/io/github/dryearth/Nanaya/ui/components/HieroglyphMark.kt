package io.github.dryearth.Nanaya.ui.components

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The assistant mark 𓆩♡𓆪.
 *
 * The two bird hieroglyphs use the bundled Noto Egyptian font; the heart is drawn
 * with the system sans-serif face. The group is measured and scaled so it fills the
 * icon box like the previous Material icon. When [flowing] is true a moving rainbow
 * light runs across the mark.
 */
@Composable
fun HieroglyphMark(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    flowing: Boolean = false,
    contentDescription: String? = "AI 管家",
) {
    val context = LocalContext.current
    val egypt = remember {
        runCatching {
            Typeface.createFromAsset(
                context.assets,
                "fonts/NotoSansEgyptianHieroglyphs-Regular.ttf",
            )
        }.getOrNull()
    }
    val latin = remember { Typeface.create("sans-serif", Typeface.NORMAL) }
    val description = contentDescription

    val transition = rememberInfiniteTransition(label = "hieroglyph-flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "phase",
    )

    Canvas(
        modifier = if (description != null) {
            modifier.semantics { this.contentDescription = description }
        } else {
            modifier
        },
    ) {
        val side = size.minDimension
        if (side <= 0f) return@Canvas
        val parts = if (egypt == null) {
            listOf("♡" to latin)
        } else {
            listOf(
                "𓆩" to egypt,
                "♡" to latin,
                "𓆪" to egypt,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            style = Paint.Style.FILL_AND_STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val baseSize = 100f
        paint.textSize = baseSize
        val baseWidths = parts.map { (text, typeface) ->
            paint.typeface = typeface
            paint.measureText(text)
        }
        val bounds = Rect()
        var top = 0f
        var bottom = 0f
        parts.forEach { (text, typeface) ->
            paint.typeface = typeface
            paint.getTextBounds(text, 0, text.length, bounds)
            top = minOf(top, bounds.top.toFloat())
            bottom = maxOf(bottom, bounds.bottom.toFloat())
        }
        val measuredWidth = baseWidths.sum().coerceAtLeast(1f)
        val measuredHeight = (bottom - top).coerceAtLeast(1f)
        val scale = minOf(
            side * 0.98f / measuredWidth,
            side * 0.92f / measuredHeight,
        )
        paint.textSize = baseSize * scale
        paint.strokeWidth = paint.textSize * 0.035f

        if (flowing) {
            val shift = (phase * 2f - 0.5f) * side
            paint.shader = LinearGradient(
                shift,
                0f,
                shift + side * 1.25f,
                side,
                intArrayOf(
                    0xFF67E8F9.toInt(),
                    0xFF818CF8.toInt(),
                    0xFFFFFFFF.toInt(),
                    0xFFE879F9.toInt(),
                    0xFF6EE7B7.toInt(),
                    0xFF67E8F9.toInt(),
                ),
                null,
                Shader.TileMode.MIRROR,
            )
        } else {
            paint.color = color.toArgb()
        }

        var x = (size.width - measuredWidth * scale) / 2f
        val baseline = size.height / 2f - ((top + bottom) / 2f) * scale
        parts.forEachIndexed { index, (text, typeface) ->
            paint.typeface = typeface
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, x, baseline, paint)
            }
            x += baseWidths[index] * scale
        }
    }
}
