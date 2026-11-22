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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.dryearth.Nanaya.ui.components.liquid.triangleLens
import io.github.dryearth.Nanaya.ui.components.liquid.vibrancy
import kotlin.math.floor
import top.yukonga.miuix.kmp.blur.drawBackdrop

/**
 * Transparent liquid-glass back button shaped as a left-pointing rounded triangle.
 *
 * There is no glyph: the triangle itself is the control. The rim carries a
 * rotating rainbow band plus a small white spark that travels around the outline.
 */
@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(90),
        label = "back-scale",
    )
    val transition = rememberInfiniteTransition(label = "glass-back")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "phase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.55f,
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

    val backdrop = LocalGlassBackdrop.current
    val description = contentDescription
    val triangleShape = remember { RoundedTriangleShape(4.5.dp) }

    val rimMeasure = remember { PathMeasure() }
    val sparkA = remember { Path() }
    val sparkB = remember { Path() }

    Box(
        modifier = modifier
            // Align the bead with the title line and nudge it toward the edge.
            .offset(x = (-5).dp, y = (-8).dp)
            .size(44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = description
                role = Role.Button
            },
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
            // The glass body: fully transparent, only refraction and a faint sheen.
            Box(
                Modifier
                    .size(30.dp, 26.dp)
                    .shadow(
                        3.dp,
                        triangleShape,
                        ambientColor = Color.Black.copy(alpha = 0.22f),
                        spotColor = Color.Black.copy(alpha = 0.14f),
                    )
                    .clip(triangleShape)
                    .then(
                        if (backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { triangleShape },
                                effects = {
                                    vibrancy()
                                    triangleLens(
                                        refractionHeight = 22.dp.toPx(),
                                        refractionAmount = 18.dp.toPx(),
                                        cornerRadius = 4.5.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = 0.055f,
                                    )
                                },
                                onDrawSurface = { },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0x1AFFFFFF), Color.Transparent),
                                center = Offset(size.width * 0.30f, size.height * 0.22f),
                                radius = size.width * 0.85f,
                            ),
                            radius = size.width * 0.85f,
                            center = Offset(size.width * 0.30f, size.height * 0.22f),
                        )
                    },
            )

            // Rim light, drawn over the glass edge.
            Canvas(Modifier.matchParentSize()) {
                val gw = 30.dp.toPx()
                val gh = 26.dp.toPx()
                val left = (size.width - gw) / 2f
                val top = (size.height - gh) / 2f
                val points = listOf(
                    Offset(left + gw * 0.14f, top + gh * 0.50f),
                    Offset(left + gw * 0.84f, top + gh * 0.16f),
                    Offset(left + gw * 0.84f, top + gh * 0.84f),
                )
                val rim = roundedPolygonPath(points, 4.5.dp.toPx())

                val stops = (0..24).map { i ->
                    val t = i / 24f
                    t to cyclicRamp(flow, t + phase)
                }.toTypedArray()
                val brush = Brush.sweepGradient(*stops, center = center)

                // Soft colored halo around the whole outline.
                drawPath(
                    path = rim,
                    brush = brush,
                    style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round),
                    alpha = 0.10f * breath,
                )
                // Thin dark bed keeps the light readable on pale backgrounds.
                drawPath(
                    path = rim,
                    color = Color(0x30000000),
                    style = Stroke(width = 1.9.dp.toPx(), join = StrokeJoin.Round),
                )
                // Colored light running around the outline.
                drawPath(
                    path = rim,
                    brush = brush,
                    style = Stroke(width = 1.35.dp.toPx(), join = StrokeJoin.Round),
                    alpha = 0.95f * breath,
                )

                // White spark travelling around the perimeter.
                rimMeasure.setPath(rim, true)
                val length = rimMeasure.length
                if (length > 0f) {
                    val span = length * 0.22f
                    val start = phase * length
                    val end = start + span
                    sparkA.reset()
                    sparkB.reset()
                    if (end <= length) {
                        rimMeasure.getSegment(start, end, sparkA, true)
                    } else {
                        rimMeasure.getSegment(start, length, sparkA, true)
                        val rest = end - length
                        if (rest > 0.01f) {
                            rimMeasure.getSegment(0f, rest, sparkB, true)
                        }
                    }
                    for (spark in listOf(sparkA, sparkB)) {
                        drawPath(
                            path = spark,
                            color = Color.White.copy(alpha = 0.22f),
                            style = Stroke(
                                width = 3.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                        drawPath(
                            path = spark,
                            color = Color.White.copy(alpha = 0.88f),
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

private class RoundedTriangleShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val points = listOf(
            Offset(size.width * 0.14f, size.height * 0.50f),
            Offset(size.width * 0.84f, size.height * 0.16f),
            Offset(size.width * 0.84f, size.height * 0.84f),
        )
        return Outline.Generic(
            roundedPolygonPath(points, with(density) { cornerRadius.toPx() }),
        )
    }
}

private fun roundedPolygonPath(points: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = points.size
    val starts = ArrayList<Offset>(n)
    val ends = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val v = points[i]
        val prev = points[(i - 1 + n) % n]
        val next = points[(i + 1) % n]
        val toPrev = unit(v, prev)
        val toNext = unit(v, next)
        val r = minOf(radius, distance(v, prev) / 2f, distance(v, next) / 2f)
        starts += v + toPrev * r
        ends += v + toNext * r
    }
    path.moveTo(starts[0].x, starts[0].y)
    for (i in 0 until n) {
        path.lineTo(starts[i].x, starts[i].y)
        path.quadraticBezierTo(points[i].x, points[i].y, ends[i].x, ends[i].y)
    }
    path.close()
    return path
}

private fun unit(from: Offset, to: Offset): Offset {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.hypot(dx, dy)
    return if (len <= 0.0001f) Offset.Zero else Offset(dx / len, dy / len)
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.hypot(dx, dy)
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
