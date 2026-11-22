package io.github.dryearth.Nanaya.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.dryearth.Nanaya.ui.components.liquid.vibrancy
import kotlin.math.floor
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

/**
 * Heart-shaped liquid-glass button with a rotating rainbow rim and a travelling
 * white spark. Used for the assistant entry points.
 */
@Composable
fun AiHeartButton(
    backdrop: LayerBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(100),
        label = "heart-scale",
    )
    val transition = rememberInfiniteTransition(label = "heart-rim")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "phase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val cyan = Color(0xFF67E8F9)
    val indigo = Color(0xFF818CF8)
    val pink = Color(0xFFE879F9)
    val mint = Color(0xFF6EE7B7)
    val flow = listOf(cyan, indigo, pink, mint)
    val shape = remember { HeartShape() }
    val measure = remember { PathMeasure() }
    val sparkA = remember { Path() }
    val sparkB = remember { Path() }

    Box(
        modifier = modifier
            .size(56.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .shadow(
                        7.dp,
                        shape,
                        ambientColor = Color.Black.copy(alpha = 0.30f),
                        spotColor = Color.Black.copy(alpha = 0.20f),
                    )
                    .clip(shape)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(6.dp.toPx(), 6.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color(0x14FFFFFF))
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }

            Canvas(Modifier.matchParentSize()) {
                val side = 48.dp.toPx()
                val left = (size.width - side) / 2f
                val top = (size.height - side) / 2f
                val path = heartPath(side, side, left, top)
                val center = Offset(size.width / 2f, size.height / 2f)
                val stops = (0..24).map { index ->
                    val t = index / 24f
                    t to cyclicRamp(flow, t + phase)
                }.toTypedArray()
                val brush = Brush.sweepGradient(*stops, center = center)

                drawPath(
                    path = path,
                    brush = brush,
                    style = Stroke(width = 5.dp.toPx(), join = StrokeJoin.Round),
                    alpha = 0.10f * breath,
                )
                drawPath(
                    path = path,
                    color = Color(0x30000000),
                    style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round),
                )
                drawPath(
                    path = path,
                    brush = brush,
                    style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round),
                    alpha = 0.95f * breath,
                )

                measure.setPath(path, true)
                val length = measure.length
                if (length > 0f) {
                    val span = length * 0.22f
                    val start = phase * length
                    val end = start + span
                    sparkA.reset()
                    sparkB.reset()
                    if (end <= length) {
                        measure.getSegment(start, end, sparkA, true)
                    } else {
                        measure.getSegment(start, length, sparkA, true)
                        val rest = end - length
                        if (rest > 0.01f) measure.getSegment(0f, rest, sparkB, true)
                    }
                    for (spark in listOf(sparkA, sparkB)) {
                        drawPath(
                            path = spark,
                            color = Color.White.copy(alpha = 0.20f),
                            style = Stroke(
                                width = 3.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                        drawPath(
                            path = spark,
                            color = Color.White.copy(alpha = 0.90f),
                            style = Stroke(
                                width = 1.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private class HeartShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(heartPath(size.width, size.height))
}

private fun heartPath(
    width: Float,
    height: Float,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
): Path {
    val w = width
    val h = height
    return Path().apply {
        moveTo(offsetX + w * 0.5f, offsetY + h * 0.94f)
        cubicTo(
            offsetX + w * 0.08f, offsetY + h * 0.64f,
            offsetX + w * 0.01f, offsetY + h * 0.36f,
            offsetX + w * 0.20f, offsetY + h * 0.20f,
        )
        cubicTo(
            offsetX + w * 0.34f, offsetY + h * 0.06f,
            offsetX + w * 0.47f, offsetY + h * 0.12f,
            offsetX + w * 0.50f, offsetY + h * 0.29f,
        )
        cubicTo(
            offsetX + w * 0.53f, offsetY + h * 0.12f,
            offsetX + w * 0.66f, offsetY + h * 0.06f,
            offsetX + w * 0.80f, offsetY + h * 0.20f,
        )
        cubicTo(
            offsetX + w * 0.99f, offsetY + h * 0.36f,
            offsetX + w * 0.92f, offsetY + h * 0.64f,
            offsetX + w * 0.50f, offsetY + h * 0.94f,
        )
        close()
    }
}

private fun cyclicRamp(colors: List<Color>, t: Float): Color {
    val wrapped = t - floor(t)
    val scaled = wrapped * colors.size
    val index = scaled.toInt() % colors.size
    val frac = scaled - floor(scaled)
    val a = colors[index]
    val b = colors[(index + 1) % colors.size]
    return Color(
        red = a.red + (b.red - a.red) * frac,
        green = a.green + (b.green - a.green) * frac,
        blue = a.blue + (b.blue - a.blue) * frac,
        alpha = a.alpha + (b.alpha - a.alpha) * frac,
    )
}
