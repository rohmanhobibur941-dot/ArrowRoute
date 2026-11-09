package com.jubahir.arrowroute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

private val TAN = Color(0xFFC9A66B)
private val TAN_DARK = Color(0xFFB98B4A)
private val CREAM = Color(0xFFFFF3D6)
private val GREEN = Color(0xFF4CAF28)
private val RED = Color(0xFFE53935)
private val HINT = Color(0xFF2ECC71)

private val NEON = listOf(
    0xFFFFD54F, 0xFF64B5F6, 0xFFBA68C8, 0xFFF06292, 0xFF4DD0E1,
    0xFFAED581, 0xFFFF8A65, 0xFF7986CB, 0xFF81C784, 0xFFE57373
).map { Color(it) }

// ---------------------------------------------------------------- root + splash

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val fx = remember { Fx(ctx, prefs) }
    var splash by remember { mutableStateOf(true) }
    if (splash) {
        SplashScreen { splash = false }
    } else {
        GameScreen(prefs, fx)
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1800)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E7)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val shadow = Shadow(Color(0xFF4B2E8F), Offset(5f, 6f), 0f)
            Text(
                "Arrow",
                color = Color(0xFFFFC83D),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(shadow = shadow)
            )
            Text(
                "Route \u279C",
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(shadow = shadow)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF3A6FD8))
                    .padding(horizontal = 22.dp, vertical = 6.dp)
            ) {
                Text("Grid Rush", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(90.dp))
            Text(
                "LOADING...",
                color = Color(0xFF4B2E8F),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ---------------------------------------------------------------- game screen

@Composable
fun GameScreen(prefs: Prefs, fx: Fx) {
    val gs = remember { GameState(prefs) }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showWallet by remember { mutableStateOf(false) }

    LaunchedEffect(gs.gen) { zoom = 1f }

    val dark = gs.theme == 1
    val bg = if (dark) Color(0xFF141B2B) else Color(0xFFE9DCBC)

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // top bar: coins + wallet
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CREAM)
                .border(2.dp, TAN_DARK, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("\uD83E\uDE99", fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${gs.coins}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A3B22),
                modifier = Modifier.weight(1f)
            )
            GreenButton("Wallet") { showWallet = true }
        }

        LevelProgress(gs.level)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CREAM)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("Level ${gs.level}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A3B22))
            }
            Spacer(Modifier.weight(1f))
            Text(
                (1..3).joinToString(" ") { if (it <= gs.hearts) "\u2764\uFE0F" else "\uD83E\uDD0D" },
                fontSize = 20.sp
            )
        }

        GameBoard(gs, zoom, fx, Modifier.weight(1f).fillMaxWidth())

        // bottom bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(TAN)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundBtn("\uD83C\uDFA8") { showTheme = true }
            Spacer(Modifier.width(8.dp))
            RoundBtn("\u2699\uFE0F") { showSettings = true }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(CREAM)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "\u2212",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GREEN,
                    modifier = Modifier
                        .clickable { zoom = (zoom - 0.25f).coerceAtLeast(1f) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    valueRange = 1f..3f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = GREEN,
                        activeTrackColor = GREEN,
                        inactiveTrackColor = Color(0xFFD9CDA8)
                    )
                )
                Text(
                    "+",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GREEN,
                    modifier = Modifier
                        .clickable { zoom = (zoom + 0.25f).coerceAtMost(3f) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundBtn("\uD83D\uDCA1") { gs.hint(scope) }
        }
    }

    if (showSettings) {
        var sound by remember { mutableStateOf(prefs.sound) }
        var vibrate by remember { mutableStateOf(prefs.vibrate) }
        GameDialog("Settings", onClose = { showSettings = false }) {
            SwitchRow("\uD83D\uDD0A  Sound", sound) {
                sound = it
                prefs.sound = it
            }
            Spacer(Modifier.height(10.dp))
            SwitchRow("\uD83D\uDCF3  Vibration", vibrate) {
                vibrate = it
                prefs.vibrate = it
            }
            Spacer(Modifier.height(16.dp))
            GreenButton("Restart Level") {
                showSettings = false
                gs.startLevel()
            }
        }
    }

    if (showTheme) {
        GameDialog("Theme", onClose = { showTheme = false }) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                ThemeChoice("Classic", gs.theme == 0, Color(0xFFE9DCBC), Color(0xFF111111)) {
                    gs.setThemeIndex(0)
                }
                ThemeChoice("Neon", gs.theme == 1, Color(0xFF141B2B), Color(0xFF64B5F6)) {
                    gs.setThemeIndex(1)
                }
            }
        }
    }

    if (showWallet) {
        GameDialog("Wallet", onClose = { showWallet = false }) {
            Text("My Coins", fontSize = 14.sp, color = Color(0xFF6B5A3A))
            Text("${gs.coins}", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3F6B1F))
            Spacer(Modifier.height(6.dp))
            Text("Current level: ${gs.level}", fontSize = 14.sp, color = Color(0xFF6B5A3A))
            Spacer(Modifier.height(6.dp))
            // TODO: connect a real rewards/redeem backend here if you add one.
            Text(
                "Earn 2 coins per arrow and a bonus for every level you clear.",
                fontSize = 13.sp,
                color = Color(0xFF6B5A3A),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (gs.status == Status.WON) {
        GameDialog("Level ${gs.level} Complete!", onClose = null) {
            Text("\uD83C\uDF89", fontSize = 44.sp)
            Text("Bonus +${gs.bonus} coins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F6B1F))
            Spacer(Modifier.height(16.dp))
            GreenButton("Next Level") { gs.nextLevel() }
        }
    }

    if (gs.status == Status.LOST) {
        GameDialog("Out of hearts", onClose = null) {
            Text("\uD83D\uDC94", fontSize = 44.sp)
            Spacer(Modifier.height(16.dp))
            GreenButton("Try Again") { gs.startLevel() }
        }
    }
}

// ---------------------------------------------------------------- small widgets

@Composable
fun LevelProgress(level: Int) {
    val base = ((level - 1) / 8) * 8 + 1
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        for (i in 0 until 8) {
            val n = base + i
            val color = when {
                n < level -> Color(0xFF6CB82E)
                n == level -> Color(0xFFFFC83D)
                else -> CREAM
            }
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, TAN_DARK, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (n < level) "\u2713" else "$n",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (n < level) Color.White else Color(0xFF4A3B22)
                )
            }
        }
    }
}

@Composable
fun RoundBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CREAM)
            .border(1.dp, TAN_DARK, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 22.sp)
    }
}

@Composable
fun GreenButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF7CC53A), Color(0xFF3F8E1E))))
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CREAM)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, color = Color(0xFF4A3B22), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ThemeChoice(name: String, selected: Boolean, bg: Color, accent: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(if (selected) 4.dp else 1.dp, if (selected) GREEN else TAN_DARK, RoundedCornerShape(14.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("\u279C\u279C", fontSize = 26.sp, color = accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 13.sp, color = Color(0xFF4A3B22))
    }
}

@Composable
fun GameDialog(title: String, onClose: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { onClose?.invoke() }) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFFF6DE))
                .border(3.dp, TAN, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3F6B1F))
            Spacer(Modifier.height(14.dp))
            content()
            if (onClose != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Close",
                    color = Color(0xFF6B5A3A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- board

private class BoardLayout(val cell: Float, val ox: Float, val oy: Float, val pan: Offset)

private fun boardLayout(size: Size, w: Int, h: Int, zoom: Float, pan: Offset): BoardLayout {
    if (size.width <= 0f || size.height <= 0f) return BoardLayout(1f, 0f, 0f, Offset.Zero)
    val base = minOf(size.width / (w + 0.5f), size.height / (h + 0.5f))
    val cell = base * zoom
    val gw = cell * (w - 1)
    val gh = cell * (h - 1)
    val limX = max(0f, (gw - size.width) / 2f + cell)
    val limY = max(0f, (gh - size.height) / 2f + cell)
    val p = Offset(pan.x.coerceIn(-limX, limX), pan.y.coerceIn(-limY, limY))
    return BoardLayout(cell, (size.width - gw) / 2f + p.x, (size.height - gh) / 2f + p.y, p)
}

private fun pointOf(c: Cell, lay: BoardLayout) = Offset(lay.ox + c.x * lay.cell, lay.oy + c.y * lay.cell)

private fun distToSeg(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val len2 = abx * abx + aby * aby
    val t = if (len2 == 0f) 0f else (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
    val px = a.x + abx * t
    val py = a.y + aby * t
    val dx = p.x - px
    val dy = p.y - py
    return sqrt(dx * dx + dy * dy)
}

private fun hitTest(gs: GameState, lay: BoardLayout, p: Offset): Arrow? {
    val thr = max(lay.cell * 0.55f, 34f)
    var best: Arrow? = null
    var bestD = Float.MAX_VALUE
    for (a in gs.arrows) {
        val pts = a.cells.map { pointOf(it, lay) }
        for (i in 0 until pts.size - 1) {
            val d = distToSeg(p, pts[i], pts[i + 1])
            if (d < bestD) {
                bestD = d
                best = a
            }
        }
    }
    return if (bestD <= thr) best else null
}

private fun windowPoints(pts: List<Offset>, a: Float, b: Float): List<Offset> {
    fun at(f: Float): Offset {
        val i = floor(f).toInt().coerceIn(0, pts.size - 2)
        val t = f - i
        return Offset(
            pts[i].x + (pts[i + 1].x - pts[i].x) * t,
            pts[i].y + (pts[i + 1].y - pts[i].y) * t
        )
    }
    val out = ArrayList<Offset>()
    out.add(at(a))
    var i = floor(a).toInt() + 1
    while (i < b && i < pts.size) {
        out.add(pts[i])
        i++
    }
    out.add(at(b))
    return out
}

private fun DrawScope.drawSnake(pts: List<Offset>, color: Color, sw: Float, alpha: Float) {
    if (pts.size < 2) return
    val c = color.copy(alpha = alpha)
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    drawPath(path, c, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val tip = pts.last()
    var k = pts.size - 2
    while (k > 0 && distSq(pts[k], tip) < 0.01f) k--
    val prev = pts[k]
    var dx = tip.x - prev.x
    var dy = tip.y - prev.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 0.001f) return
    dx /= len
    dy /= len
    val hl = sw * 1.8f
    val hw = sw * 1.3f
    val head = Path()
    head.moveTo(tip.x + dx * hl, tip.y + dy * hl)
    head.lineTo(tip.x - dy * hw, tip.y + dx * hw)
    head.lineTo(tip.x + dy * hw, tip.y - dx * hw)
    head.close()
    drawPath(head, c)
}

private fun distSq(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

private fun DrawScope.drawGhost(g: Ghost, lay: BoardLayout, w: Int, h: Int, color: Color, sw: Float) {
    val a = g.arrow
    val len = a.cells.size
    val d = a.dir
    val pts = ArrayList<Offset>()
    for (c in a.cells) pts.add(pointOf(c, lay))
    var x = a.head.x
    var y = a.head.y
    var outside = 0
    while (outside < len + 1) {
        x += d.dx
        y += d.dy
        pts.add(pointOf(Cell(x, y), lay))
        if (x !in 0 until w || y !in 0 until h) outside++
    }
    val rayCount = pts.size - len
    val s = g.progress * rayCount
    val win = windowPoints(pts, s, s + (len - 1))
    val alpha = (1f - max(0f, (g.progress - 0.6f) / 0.4f)).coerceIn(0f, 1f)
    drawSnake(win, color, sw, alpha)
}

@Composable
fun GameBoard(gs: GameState, zoom: Float, fx: Fx, modifier: Modifier) {
    var pan by remember(gs.gen) { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(Size.Zero) }
    val scope = rememberCoroutineScope()
    val dark = gs.theme == 1

    fun colorFor(a: Arrow): Color = when {
        a.id == gs.blockedId -> RED
        a.id == gs.hintId -> HINT
        dark -> NEON[a.id % NEON.size]
        else -> Color(0xFF1B1B1B)
    }

    Canvas(
        modifier
            .onSizeChanged { boardSize = it.toSize() }
            .pointerInput(gs.gen, zoom) {
                detectTapGestures { p ->
                    val lay = boardLayout(boardSize, gs.w, gs.h, zoom, pan)
                    val hit = hitTest(gs, lay, p)
                    if (hit != null) gs.onTap(hit, scope, fx)
                }
            }
            .pointerInput(gs.gen, zoom) {
                detectDragGestures { change, drag ->
                    change.consume()
                    pan = boardLayout(boardSize, gs.w, gs.h, zoom, pan + drag).pan
                }
            }
    ) {
        val lay = boardLayout(this.size, gs.w, gs.h, zoom, pan)
        val dotColor = if (dark) Color(0x55FFFFFF) else Color(0xFFB9A57C)
        val r = max(1.5f, lay.cell * 0.04f)
        for (yy in 0 until gs.h) {
            for (xx in 0 until gs.w) {
                drawCircle(dotColor, r, Offset(lay.ox + xx * lay.cell, lay.oy + yy * lay.cell))
            }
        }
        val sw = max(3f, lay.cell * 0.16f)
        for (a in gs.arrows) {
            val pts = a.cells.map { pointOf(it, lay) }
            val thick = if (a.id == gs.hintId) 1.4f else 1f
            drawSnake(pts, colorFor(a), sw * thick, 1f)
        }
        for (g in gs.ghosts.values) {
            drawGhost(g, lay, gs.w, gs.h, colorFor(g.arrow), sw)
        }
    }
}
