package com.jubahir.arrowroute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object Pal {
    val Ink = Color(0xFF4A3B22)
    val Cream = Color(0xFFFFF6DE)
    val CreamDark = Color(0xFFF1D9B0)
    val Panel = Color(0xFF3B2A52)
    val ScreenBg = Color(0xFF241B5A)
    val HeaderTop = Color(0xFF7C5CE6)
    val HeaderBottom = Color(0xFF5B3FC4)
    val BarBg = Color(0xFF3A2E86)
}

enum class BtnStyle(val light: Color, val base: Color, val dark: Color) {
    GREEN(Color(0xFF8BE85A), Color(0xFF3FBF2C), Color(0xFF2A8A1E)),
    BLUE(Color(0xFF6FD0FF), Color(0xFF2D9CF0), Color(0xFF1C6FC0)),
    ORANGE(Color(0xFFFFCB6B), Color(0xFFFF9A2E), Color(0xFFD96F10)),
    RED(Color(0xFFFF8A7A), Color(0xFFE9453A), Color(0xFFB02A22)),
    PURPLE(Color(0xFFB794F6), Color(0xFF7C4DDB), Color(0xFF5530A8))
}

// ------------------------------------------------------------------ icons (all drawn, no emoji)

enum class Ico { COIN, HEART, HEART_OFF, STAR, GEAR, BULB, PALETTE, HOME, RESTART, CLOSE, PLAY }

fun starPath(cx: Float, cy: Float, r: Float): Path {
    val p = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = -PI / 2.0 + i * PI / 5.0
        val x = cx + (rad * cos(a)).toFloat()
        val y = cy + (rad * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

@Composable
fun GameIcon(kind: Ico, sizeDp: Dp, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier.size(sizeDp)) {
        val w = this.size.width
        val h = this.size.height
        when (kind) {
            Ico.COIN -> {
                drawCircle(Color(0xFFD99A00), radius = w * 0.5f)
                drawCircle(Color(0xFFFFD24A), radius = w * 0.42f)
                drawCircle(Color(0xFFF2B600), radius = w * 0.3f, style = Stroke(width = w * 0.06f))
                drawRoundRect(
                    Color(0xFFF2B600),
                    topLeft = Offset(w * 0.45f, h * 0.3f),
                    size = Size(w * 0.1f, h * 0.4f),
                    cornerRadius = CornerRadius(w * 0.05f)
                )
                drawCircle(Color.White.copy(alpha = 0.55f), radius = w * 0.06f, center = Offset(w * 0.3f, h * 0.28f))
            }
            Ico.HEART, Ico.HEART_OFF -> {
                val p = Path()
                p.moveTo(w * 0.5f, h * 0.88f)
                p.cubicTo(w * 0.1f, h * 0.58f, w * 0.0f, h * 0.25f, w * 0.25f, h * 0.15f)
                p.cubicTo(w * 0.4f, h * 0.09f, w * 0.5f, h * 0.2f, w * 0.5f, h * 0.3f)
                p.cubicTo(w * 0.5f, h * 0.2f, w * 0.6f, h * 0.09f, w * 0.75f, h * 0.15f)
                p.cubicTo(w * 1.0f, h * 0.25f, w * 0.9f, h * 0.58f, w * 0.5f, h * 0.88f)
                p.close()
                if (kind == Ico.HEART) {
                    drawPath(p, Color(0xFFE5395A))
                    drawOval(
                        Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(w * 0.16f, h * 0.2f),
                        size = Size(w * 0.2f, h * 0.11f)
                    )
                } else {
                    drawPath(p, Color(0xFFB8B8C0), style = Stroke(width = w * 0.09f, join = StrokeJoin.Round))
                }
            }
            Ico.STAR -> {
                val p = starPath(w / 2f, h * 0.53f, w * 0.48f)
                drawPath(p, Color(0xFFFFC928))
                drawPath(p, Color(0xFFE09A00), style = Stroke(width = w * 0.07f, join = StrokeJoin.Round))
            }
            Ico.GEAR -> {
                val c = Offset(w / 2f, h / 2f)
                for (i in 0 until 8) {
                    val a = i * PI / 4.0
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    drawLine(
                        tint,
                        Offset(c.x + dx * w * 0.3f, c.y + dy * w * 0.3f),
                        Offset(c.x + dx * w * 0.46f, c.y + dy * w * 0.46f),
                        strokeWidth = w * 0.16f
                    )
                }
                drawCircle(tint, radius = w * 0.3f, style = Stroke(width = w * 0.16f))
            }
            Ico.BULB -> {
                drawCircle(Color(0xFFFFD43B), radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.4f))
                drawCircle(Color.White.copy(alpha = 0.6f), radius = w * 0.07f, center = Offset(w * 0.4f, h * 0.3f))
                drawRoundRect(
                    Color(0xFFEDEDED),
                    topLeft = Offset(w * 0.36f, h * 0.66f),
                    size = Size(w * 0.28f, h * 0.22f),
                    cornerRadius = CornerRadius(w * 0.05f)
                )
            }
            Ico.PALETTE -> {
                drawCircle(Color(0xFFFFE2B0), radius = w * 0.46f)
                drawCircle(Color(0xFFE53935), radius = w * 0.08f, center = Offset(w * 0.35f, h * 0.35f))
                drawCircle(Color(0xFF3A6FD8), radius = w * 0.08f, center = Offset(w * 0.62f, h * 0.32f))
                drawCircle(Color(0xFF43A047), radius = w * 0.08f, center = Offset(w * 0.7f, h * 0.58f))
                drawCircle(Color(0xFF8E5BE8), radius = w * 0.1f, center = Offset(w * 0.4f, h * 0.68f))
            }
            Ico.HOME -> {
                val roof = Path()
                roof.moveTo(w * 0.5f, h * 0.14f)
                roof.lineTo(w * 0.94f, h * 0.52f)
                roof.lineTo(w * 0.06f, h * 0.52f)
                roof.close()
                drawPath(roof, tint)
                drawRect(tint, topLeft = Offset(w * 0.2f, h * 0.5f), size = Size(w * 0.6f, h * 0.36f))
                drawRect(
                    Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(w * 0.43f, h * 0.62f),
                    size = Size(w * 0.14f, h * 0.24f)
                )
            }
            Ico.RESTART -> {
                val r = w * 0.3f
                val c = Offset(w / 2f, h / 2f)
                drawArc(
                    tint,
                    startAngle = 50f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(2f * r, 2f * r),
                    style = Stroke(width = w * 0.13f, cap = StrokeCap.Round)
                )
                val a = 320.0 * PI / 180.0
                val ex = c.x + (r * cos(a)).toFloat()
                val ey = c.y + (r * sin(a)).toFloat()
                val tx = (-sin(a)).toFloat()
                val ty = cos(a).toFloat()
                val nx = cos(a).toFloat()
                val ny = sin(a).toFloat()
                val head = Path()
                head.moveTo(ex + tx * w * 0.2f, ey + ty * w * 0.2f)
                head.lineTo(ex + nx * w * 0.16f, ey + ny * w * 0.16f)
                head.lineTo(ex - nx * w * 0.16f, ey - ny * w * 0.16f)
                head.close()
                drawPath(head, tint)
            }
            Ico.CLOSE -> {
                drawLine(
                    tint, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f),
                    strokeWidth = w * 0.14f, cap = StrokeCap.Round
                )
                drawLine(
                    tint, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f),
                    strokeWidth = w * 0.14f, cap = StrokeCap.Round
                )
            }
            Ico.PLAY -> {
                val t = Path()
                t.moveTo(w * 0.32f, h * 0.2f)
                t.lineTo(w * 0.82f, h * 0.5f)
                t.lineTo(w * 0.32f, h * 0.8f)
                t.close()
                drawPath(t, tint)
            }
        }
    }
}

// ------------------------------------------------------------------ buttons

@Composable
fun GlossyButton(
    text: String,
    style: BtnStyle,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(modifier.clickable { onClick() }) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 5.dp)
                .clip(shape)
                .background(style.dark)
        )
        Box(
            Modifier
                .padding(bottom = 5.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(style.light, style.base)))
                .border(2.5.dp, Color.White.copy(alpha = 0.85f), shape)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.8f)
                    .height(5.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.35f))
            )
            Text(
                text,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(shadow = Shadow(style.dark, Offset(0f, 4f), 2f))
            )
        }
    }
}

@Composable
fun RoundIconButton(kind: Ico, style: BtnStyle, sizeDp: Dp = 46.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(sizeDp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 3.dp)
                .clip(CircleShape)
                .background(style.dark)
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = 3.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(style.light, style.base)))
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GameIcon(kind, sizeDp * 0.52f)
        }
    }
}

@Composable
fun OnOffToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .width(92.dp)
            .height(36.dp)
            .clip(shape)
            .background(Pal.CreamDark)
            .clickable { onChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(if (!checked) Color(0xFFE9453A) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "off",
                color = if (!checked) Color.White else Color(0xFFB89A6A),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(if (checked) Color(0xFF3FBF2C) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "on",
                color = if (checked) Color.White else Color(0xFFB89A6A),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ------------------------------------------------------------------ pills, bars, ribbons

@Composable
fun CoinPill(coins: Int) {
    Box(contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .padding(start = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(Pal.Panel)
                .padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$coins", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        GameIcon(Ico.COIN, 34.dp)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.height(24.dp)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(Color(0xFF4A3036), size = Size(w, h), cornerRadius = CornerRadius(h / 2f))
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF86E6FF), Color(0xFF2FA8DD))),
                size = Size(max(w * f, h), h),
                cornerRadius = CornerRadius(h / 2f)
            )
        }
        for (i in 1..3) {
            val cx = w * i / 3f - h * 0.7f
            val reached = f >= i / 3f - 0.001f
            drawPath(starPath(cx, h / 2f, h * 0.42f), if (reached) Color(0xFFFFC928) else Color(0xFF8A6A50))
        }
    }
}

@Composable
fun RibbonTitle(text: String, style: BtnStyle, modifier: Modifier = Modifier, fontSize: TextUnit = 20.sp) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val ear = w * 0.1f
            val left = Path()
            left.moveTo(0f, h * 0.2f)
            left.lineTo(ear * 2.2f, h * 0.2f)
            left.lineTo(ear * 2.2f, h)
            left.lineTo(0f, h)
            left.lineTo(ear * 0.8f, h * 0.6f)
            left.close()
            drawPath(left, style.dark)
            val right = Path()
            right.moveTo(w, h * 0.2f)
            right.lineTo(w - ear * 2.2f, h * 0.2f)
            right.lineTo(w - ear * 2.2f, h)
            right.lineTo(w, h)
            right.lineTo(w - ear * 0.8f, h * 0.6f)
            right.close()
            drawPath(right, style.dark)
            drawRoundRect(
                Brush.verticalGradient(listOf(style.light, style.base)),
                topLeft = Offset(ear * 1.2f, 0f),
                size = Size(w - ear * 2.4f, h * 0.84f),
                cornerRadius = CornerRadius(h * 0.22f)
            )
        }
        Text(
            text,
            modifier = Modifier.padding(start = 44.dp, end = 44.dp, top = 10.dp, bottom = 16.dp),
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(shadow = Shadow(style.dark, Offset(0f, 3f), 2f))
        )
    }
}

@Composable
fun StarBadge(gold: Boolean, sizeDp: Dp) {
    Canvas(Modifier.size(sizeDp)) {
        val w = this.size.width
        val p = starPath(w / 2f, w * 0.53f, w * 0.48f)
        drawPath(p, if (gold) Color(0xFFFFC928) else Color(0xFF6B4226))
        drawPath(
            p,
            if (gold) Color(0xFFE09A00) else Color(0xFF4A2C17),
            style = Stroke(width = w * 0.07f, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun ResultBanner(win: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            StarBadge(win, 46.dp)
            StarBadge(win, 62.dp)
            StarBadge(win, 46.dp)
        }
        RibbonTitle(if (win) "VICTORY!" else "DEFEAT", BtnStyle.GREEN, fontSize = 28.sp)
    }
}

@Composable
fun TitleText(text: String, fontSize: TextUnit, top: Color, bottom: Color, outline: Color) {
    Box(contentAlignment = Alignment.Center) {
        val offs = listOf(
            Offset(-4f, 0f), Offset(4f, 0f), Offset(0f, -4f), Offset(0f, 7f),
            Offset(-3f, -3f), Offset(3f, 3f), Offset(-3f, 3f), Offset(3f, -3f)
        )
        for (o in offs) {
            Text(
                text,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                color = outline,
                modifier = Modifier.offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
            )
        }
        Text(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            style = TextStyle(brush = Brush.verticalGradient(listOf(top, bottom)))
        )
    }
}

// ------------------------------------------------------------------ panels

@Composable
fun GamePanel(
    title: String,
    onClose: (() -> Unit)?,
    ribbon: BtnStyle = BtnStyle.BLUE,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = { onClose?.invoke() }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .padding(top = 26.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Pal.Cream)
                    .border(4.dp, ribbon.base, RoundedCornerShape(26.dp))
                    .padding(start = 18.dp, end = 18.dp, top = 36.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
            RibbonTitle(title, ribbon)
            if (onClose != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 22.dp, end = 4.dp)
                ) {
                    RoundIconButton(Ico.CLOSE, BtnStyle.RED, 36.dp) { onClose() }
                }
            }
        }
    }
}
