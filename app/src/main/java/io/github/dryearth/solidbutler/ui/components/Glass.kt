package io.github.dryearth.solidbutler.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dryearth.solidbutler.ui.theme.Glass
import kotlin.math.max

@Composable
fun AmbientBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF07080B), Color(0xFF0A0D12), Color(0xFF07080B)),
            )
        )
        val w = size.width
        val h = size.height
        val r1 = w * 0.9f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x227E9CFF), Color(0x0A7E9CFF), Color.Transparent),
                center = Offset(w * 0.86f, h * 0.06f),
                radius = r1,
            ),
            radius = r1,
            center = Offset(w * 0.86f, h * 0.06f),
        )
        val r2 = w * 0.75f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x146FE0C8), Color.Transparent),
                center = Offset(w * 0.02f, h * 0.55f),
                radius = r2,
            ),
            radius = r2,
            center = Offset(w * 0.02f, h * 0.55f),
        )
        val step = 46.dp.toPx()
        var x = 0f
        while (x < w) {
            drawLine(Color(0x03FFFFFF), Offset(x, 0f), Offset(x, h), 1f)
            x += step
        }
        var y = 0f
        while (y < h) {
            drawLine(Color(0x025FFFFF), Offset(0f, y), Offset(w, y), 1f)
            y += step
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    tint: Color = Color.White,
    opaque: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier = modifier
            .shadow(16.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            // A solid-ish base keeps the background grid from showing through the glass.
            .background(if (opaque) Color(0xFF0B0E13) else Color(0xDE0B0E13))
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = 0.105f),
                        tint.copy(alpha = 0.032f),
                        tint.copy(alpha = 0.055f),
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color(0x3DFFFFFF), Color(0x12FFFFFF), Color(0x08FFFFFF))
                ),
                shape,
            )
            .drawWithContent {
                drawContent()
                // top bevel highlight
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0x55FFFFFF), Color.Transparent)
                    ),
                    start = Offset(18.dp.toPx(), 0.7f),
                    end = Offset(size.width - 18.dp.toPx(), 0.7f),
                    strokeWidth = 1f,
                )
                // inner bottom shade
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0x14000000), Color.Transparent)
                    ),
                    start = Offset(18.dp.toPx(), size.height - 0.7f),
                    end = Offset(size.width - 18.dp.toPx(), size.height - 0.7f),
                    strokeWidth = 1f,
                )
            }
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Glass.Text,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Glass.Muted,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    color: Color = Glass.Accent,
    onClick: (() -> Unit)? = null,
) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f) else Color(0x0DFFFFFF))
            .border(1.dp, if (selected) color.copy(alpha = 0.45f) else Color(0x12FFFFFF), shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (selected) color else Glass.Faint)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Glass.Text else Glass.Muted,
            maxLines = 1,
        )
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 6.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector? = null,
    accent: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (accent) Glass.Accent.copy(alpha = if (enabled) 0.92f else 0.25f)
    else Color(0x0FFFFFFF)
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(
                1.dp,
                if (accent) Color.Transparent else Color(0x18FFFFFF),
                shape,
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (accent) Color(0xFF0B1220) else Glass.Text,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) Color(0xFF0B1220) else Glass.Text,
        )
    }
}

@Composable
fun StatLine(
    label: String,
    value: String,
    valueColor: Color = Glass.Text,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Meter(
    pct: Float,
    color: Color = Glass.Accent,
    track: Color = Color(0x12FFFFFF),
    height: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0f, 1f))
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.55f), color)
                    )
                )
        )
    }
}

@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Glass.Accent,
    track: Color = Color(0x12FFFFFF),
    stroke: Dp = 10.dp,
    content: @Composable () -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(sw, cap = StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.35f), color, color.copy(alpha = 0.9f))),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(sw, cap = StrokeCap.Round),
                topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw),
            )
        }
        content()
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Glass.Accent,
    filled: Boolean = true,
    maxOverride: Float? = null,
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val maxV = (maxOverride ?: values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minV = 0f
        val stepX = size.width / (values.size - 1).toFloat()
        val path = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val n = ((v - minV) / (maxV - minV)).coerceIn(0f, 1f)
            val x = i * stepX
            val y = size.height - n * size.height
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        if (filled) {
            drawPath(
                fill,
                Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)),
            )
        }
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        val lastX = size.width
        val lastN = ((values.last() - minV) / (maxV - minV)).coerceIn(0f, 1f)
        drawCircle(color, 2.6.dp.toPx(), Offset(lastX, size.height - lastN * size.height))
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    color: Color = Glass.Text,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BigNumber(
    value: String,
    unit: String,
    caption: String? = null,
    color: Color = Glass.Text,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = MaterialTheme.typography.displayMedium,
            color = color,
        )
        if (unit.isNotBlank()) {
            Spacer(Modifier.width(3.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodyMedium,
                color = Glass.Muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
    if (caption != null) {
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Glass.Faint)
    }
}

fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "<1 分钟"
        m < 60 -> "$m 分钟"
        else -> "${m / 60} 小时 ${m % 60} 分"
    }
}

fun humanRate(bps: Long): String {
    val v = max(0, bps)
    return when {
        v < 1024 -> "$v B/s"
        v < 1024 * 1024 -> String.format("%.1f KB/s", v / 1024f)
        else -> String.format("%.1f MB/s", v / 1024f / 1024f)
    }
}
