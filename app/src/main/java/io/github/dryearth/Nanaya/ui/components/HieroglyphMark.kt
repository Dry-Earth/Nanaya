package io.github.dryearth.Nanaya.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
 * with the system sans-serif face because the hieroglyph font does not contain it.
 */
@Composable
fun HieroglyphMark(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
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

    Canvas(
        modifier = if (description != null) {
            modifier.semantics { this.contentDescription = description }
        } else {
            modifier
        },
    ) {
        val side = size.minDimension
        if (side <= 0f) return@Canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textAlign = Paint.Align.LEFT
            textSize = side * 0.58f
        }
        val parts = if (egypt == null) {
            listOf("♡" to latin)
        } else {
            listOf(
                "𓆩" to egypt,
                "♡" to latin,
                "𓆪" to egypt,
            )
        }
        val widths = parts.map { (text, typeface) ->
            paint.typeface = typeface
            paint.measureText(text)
        }
        var x = (size.width - widths.sum()) / 2f
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        parts.forEachIndexed { index, (text, typeface) ->
            paint.typeface = typeface
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(text, x, baseline, paint)
            }
            x += widths[index]
        }
    }
}
