package io.github.dryearth.Nanaya.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dryearth.Nanaya.ui.components.liquid.lens
import io.github.dryearth.Nanaya.ui.components.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.drawBackdrop

/**
 * Transparent liquid-glass back bead.
 *
 * - Glass disc is exactly icon-sized (32dp), hit target is 44dp.
 * - Refraction is intentionally strong; no surface fill so the page shows through.
 * - A thin rotating rainbow band sits just outside the rim.
 * - The chevron itself is a colored light with a highlight travelling along it.
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
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
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
    val flow = listOf(cyan, indigo, pink, mint, cyan)
    val ringStops = arrayOf(
        0.00f to cyan.copy(alpha = 0.12f),
        0.10f to cyan,
        0.24f to indigo.copy(alpha = 0.22f),
        0.36f to pink,
        0.50f to pink.copy(alpha = 0.16f),
        0.62f to mint,
        0.76f to cyan.copy(alpha = 0.28f),
        0.88f to indigo,
        1.00f to cyan.copy(alpha = 0.12f),
    )

    val backdrop = LocalGlassBackdrop.current
    val description = contentDescription

    val chevron = remember { Path() }
    val measure = remember { PathMeasure() }
    val segmentA = remember { Path() }
    val segmentB = remember { Path() }

    Box(
        modifier = modifier
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
        // Thin rainbow band, just outside the glass rim.
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val rim = 32.dp.toPx() / 2f + 1.dp.toPx()
            rotate(phase * 360f, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(*ringStops, center = c),
                    radius = rim,
                    center = c,
                    style = Stroke(width = 1.2.dp.toPx()),
                    alpha = 0.92f * breath,
                )
                drawCircle(
                    brush = Brush.sweepGradient(*ringStops, center = c),
                    radius = rim + 1.6.dp.toPx(),
                    center = c,
                    style = Stroke(width = 3.2.dp.toPx()),
                    alpha = 0.10f * breath,
                )
            }
        }

        Box(
            Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    3.dp,
                    CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.24f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                )
                .clip(CircleShape)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                lens(
                                    refractionHeight = 22.dp.toPx(),
                                    refractionAmount = 18.dp.toPx(),
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
                    // Keep the disc transparent: only a faint top-left sheen.
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x18FFFFFF), Color.Transparent),
                            center = Offset(size.width * 0.28f, size.height * 0.20f),
                            radius = size.width * 0.85f,
                        ),
                        radius = size.width * 0.85f,
                        center = Offset(size.width * 0.28f, size.height * 0.20f),
                    )
                }
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color(0x99FFFFFF), Color(0x2EFFFFFF), Color(0x12000000)),
                    ),
                    CircleShape,
                ),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height

                chevron.reset()
                chevron.moveTo(w * 0.60f, h * 0.27f)
                chevron.lineTo(w * 0.39f, h * 0.50f)
                chevron.lineTo(w * 0.60f, h * 0.73f)

                // Dark edge so the colored light stays readable on pale pages.
                drawPath(
                    path = chevron,
                    color = Color(0x8C000000),
                    style = Stroke(
                        width = 3.1.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

                // Base: iridescent gradient.
                drawPath(
                    path = chevron,
                    brush = Brush.linearGradient(
                        colors = flow,
                        start = Offset(w * 0.36f, h * 0.20f),
                        end = Offset(w * 0.64f, h * 0.80f),
                    ),
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

                // Travelling highlight along the stroke.
                measure.setPath(chevron, false)
                val length = measure.length
                if (length > 0f) {
                    val span = length * 0.36f
                    val start = phase * length
                    val end = start + span
                    segmentA.reset()
                    segmentB.reset()
                    if (end <= length) {
                        measure.getSegment(start, end, segmentA, true)
                    } else {
                        measure.getSegment(start, length, segmentA, true)
                        val rest = end - length
                        if (rest > 0.01f) {
                            measure.getSegment(0f, rest, segmentB, true)
                        }
                    }
                    val glow = Color.White.copy(alpha = 0.22f)
                    val core = Color.White.copy(alpha = 0.92f)
                    for (segment in listOf(segmentA, segmentB)) {
                        if (segment.isEmpty) continue
                        drawPath(
                            path = segment,
                            color = glow,
                            style = Stroke(
                                width = 3.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                        drawPath(
                            path = segment,
                            color = core,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
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
