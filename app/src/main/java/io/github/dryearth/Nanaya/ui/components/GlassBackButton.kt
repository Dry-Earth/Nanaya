package io.github.dryearth.Nanaya.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "返回",
) {
    val transition = rememberInfiniteTransition(label = "glass-back")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "angle",
    )
    val breath by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
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
    Box(
        modifier = modifier
            .size(36.dp)
            .shadow(
                9.dp,
                CircleShape,
                ambientColor = Color(0xFF818CF8),
                spotColor = Color(0xFFE879F9),
            )
            .clip(CircleShape)
            .background(Color(0x1AFFFFFF))
            .drawWithContent {
                drawContent()
                val center = Offset(size.width / 2f, size.height / 2f)
                rotate(angle, pivot = center) {
                    drawCircle(
                        brush = Brush.sweepGradient(colors, center = center),
                        radius = size.minDimension / 2f - 1.2.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                        alpha = 0.75f * breath,
                    )
                }
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.ArrowBack,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}
