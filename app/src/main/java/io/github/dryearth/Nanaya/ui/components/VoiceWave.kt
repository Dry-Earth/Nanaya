package io.github.dryearth.Nanaya.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Soft aurora wave for the listening state. It keeps breathing even when the
 * recognition engine does not report RMS values, and lifts with the voice when
 * levels are available.
 */
@Composable
fun VoiceWave(
    active: Boolean,
    level: Float,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        Color(0xFF4F8BFF),
        Color(0xFFA78BFA),
        Color(0xFFFF7AC6),
        Color(0xFFFFE4B5),
    ),
) {
    val transition = rememberInfiniteTransition(label = "voice-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "phase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath",
    )
    if (!active) return
    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val w = size.width
        val h = size.height
        val energy = level.coerceIn(0f, 1f)
        val amplitude = h * (0.16f + 0.30f * energy)
        val centerY = h * 0.62f
        val gradient = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                colors[0].copy(alpha = 0.55f),
                colors[1].copy(alpha = 0.80f),
                colors[2].copy(alpha = 0.70f),
                colors[3].copy(alpha = 0.45f),
                Color.Transparent,
            ),
        )
        for (i in 0 until 3) {
            val path = Path()
            val phaseOffset = phase + i * 0.9f
            val amp = amplitude * (1f - i * 0.20f)
            val steps = 72
            for (step in 0..steps) {
                val x = w * step / steps
                val t = step.toFloat() / steps
                val envelope = sin(t * PI).toFloat().coerceAtLeast(0f)
                val y = centerY +
                    sin(t * PI * 2.2f + phaseOffset).toFloat() * amp * envelope +
                    sin(t * PI * 4.7f - phaseOffset * 0.6f).toFloat() * amp * 0.22f * envelope
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                brush = gradient,
                style = Stroke(width = (2.2f - i * 0.5f).dp.toPx(), cap = StrokeCap.Round),
                alpha = (0.55f + 0.35f * energy) * breath * (1f - i * 0.18f),
            )
        }
        val glowRadius = w * (0.28f + 0.10f * energy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors[1].copy(alpha = 0.22f * breath),
                    colors[2].copy(alpha = 0.10f * breath),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.78f),
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = Offset(w * 0.5f, h * 0.78f),
        )
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    colors[0].copy(alpha = 0.28f * breath),
                    colors[2].copy(alpha = 0.34f * breath),
                    colors[3].copy(alpha = 0.20f * breath),
                    Color.Transparent,
                ),
            ),
            start = Offset(w * 0.06f, h - 1.dp.toPx()),
            end = Offset(w * 0.94f, h - 1.dp.toPx()),
            strokeWidth = 1.2.dp.toPx(),
        )
    }
}
