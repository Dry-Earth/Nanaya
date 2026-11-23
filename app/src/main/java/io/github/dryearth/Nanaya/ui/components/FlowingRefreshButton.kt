package io.github.dryearth.Nanaya.ui.components

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Colorful refresh glyph. The rainbow light always flows; the glyph itself spins
 * while [refreshing] and stops in place when the refresh finishes.
 */
@Composable
fun FlowingRefreshButton(
    refreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "刷新",
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(90),
        label = "refresh-scale",
    )
    val transition = rememberInfiniteTransition(label = "refresh-flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "phase",
    )
    val description = contentDescription

    Canvas(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = description },
    ) {
        val side = size.minDimension
        if (side <= 0f) return@Canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = side * 0.78f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            val shift = (phase * 2f - 0.5f) * side
            shader = LinearGradient(
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
        }
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        val rotation = if (refreshing) phase * 360f else 0f
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.rotate(rotation, size.width / 2f, size.height / 2f)
            native.drawText("↻", size.width / 2f, baseline, paint)
            native.restore()
        }
    }
}
