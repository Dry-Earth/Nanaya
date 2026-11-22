package io.github.dryearth.Nanaya.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.Nanaya.ui.components.liquid.lens
import io.github.dryearth.Nanaya.ui.components.liquid.vibrancy
import top.yukonga.miuix.kmp.blur.drawBackdrop

@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
) {
    val transition = rememberInfiniteTransition(label = "glass-back")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "phase",
    )
    val breath by transition.animateFloat(
        initialValue = 0.60f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing),
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
            .size(32.dp)
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
                                refractionHeight = 14.dp.toPx(),
                                refractionAmount = 10.dp.toPx(),
                                depthEffect = true,
                                chromaticAberration = 0.04f,
                            )
                        },
                        onDrawSurface = { },
                    )
                } else {
                    Modifier
                },
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color(0x99FFFFFF), Color(0x2AFFFFFF), Color(0x12FFFFFF)),
                ),
                CircleShape,
            )
            .clickable { onClick() }
            .semantics {
                this.contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "⇦",
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(-40f + phase * 90f, 0f),
                    end = Offset(40f + phase * 90f, 40f),
                ),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Box(
            Modifier.matchParentSize().drawWithContent {
                drawContent()
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x55FFFFFF), Color(0x08FFFFFF), Color(0x22000000)),
                    ),
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
        )
    }
}
