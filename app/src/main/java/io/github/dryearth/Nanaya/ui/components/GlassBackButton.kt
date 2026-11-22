package io.github.dryearth.Nanaya.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dryearth.Nanaya.ui.components.liquid.lens
import io.github.dryearth.Nanaya.ui.components.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.drawBackdrop

@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(90),
        label = "back-scale",
    )
    val transition = rememberInfiniteTransition(label = "glass-back")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "phase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.60f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val colors = listOf(
        Color(0xFF67E8F9),
        Color(0xFF818CF8),
        Color(0xFFE879F9),
        Color(0xFF6EE7B7),
        Color(0xFF67E8F9),
    )
    val backdrop = LocalGlassBackdrop.current
    val description = contentDescription

    Box(
        modifier = modifier
            .size(48.dp)
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
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    6.dp,
                    CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.32f),
                    spotColor = Color.Black.copy(alpha = 0.20f),
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
                                    refractionHeight = 16.dp.toPx(),
                                    refractionAmount = 12.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = 0.03f,
                                )
                            },
                            onDrawSurface = { drawRect(Color(0x2E0B0E13)) },
                        )
                    } else {
                        Modifier.background(Color(0x2E0B0E13))
                    },
                )
                .drawBehind {
                    // Dark smoked body with a raised top-left bevel.
                    drawCircle(Color(0x18000000))
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x30FFFFFF), Color.Transparent),
                            center = Offset(size.width * 0.28f, size.height * 0.22f),
                            radius = size.width * 0.68f,
                        ),
                        radius = size.width * 0.68f,
                        center = Offset(size.width * 0.28f, size.height * 0.22f),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.Transparent, Color(0x33000000)),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.width * 0.55f,
                        ),
                        radius = size.width * 0.55f,
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                    )
                }
                .border(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(Color(0xAAFFFFFF), Color(0x33FFFFFF), Color(0x14000000)),
                    ),
                    CircleShape,
                ),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                // Colored light passing through the glass body.
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = colors.map { it.copy(alpha = 0.11f * breath) },
                        start = Offset(-w * 0.6f + phase * w * 1.4f, 0f),
                        end = Offset(phase * w * 1.4f, w),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x22FFFFFF), Color.Transparent),
                        center = Offset(w * 0.32f, w * 0.24f),
                        radius = w * 0.5f,
                    ),
                    radius = w * 0.5f,
                    center = Offset(w * 0.32f, w * 0.24f),
                )
            }

            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val chevron = Path().apply {
                    moveTo(w * 0.58f, h * 0.30f)
                    lineTo(w * 0.40f, h * 0.50f)
                    lineTo(w * 0.58f, h * 0.70f)
                }
                // Soft shadow under the chevron.
                drawPath(
                    path = chevron,
                    color = Color(0x99000000),
                    style = Stroke(
                        width = 3.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawPath(
                    path = chevron,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFE0F2FE),
                            Color(0xFFC7D2FE),
                        ),
                        start = Offset(w * 0.34f, h * 0.30f),
                        end = Offset(w * 0.60f, h * 0.70f),
                    ),
                    style = Stroke(
                        width = 2.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}
