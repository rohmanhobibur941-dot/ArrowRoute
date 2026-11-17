package com.jubahir.arrowroute

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Themes {
    const val CLASSIC = 0
    const val NEON = 1
    const val CANDY = 2

    private val NEON_COLORS = listOf(
        0xFFFFE347, 0xFF3DDCFF, 0xFFD36BFF, 0xFFFF5FA8, 0xFF3DFFB0,
        0xFFFF9A3D, 0xFF6C8CFF, 0xFFB6FF3D, 0xFFFF4D6D
    ).map { Color(it) }

    private val CANDY_COLORS = listOf(
        0xFF1E1E24, 0xFF22B14C, 0xFFFFC70F, 0xFF2D8CFF, 0xFFF0453A,
        0xFF8E5BE8, 0xFFFF8A2B, 0xFFFF5FA2, 0xFF14C4B4
    ).map { Color(it) }

    val RED = Color(0xFFE53935)
    val HINT = Color(0xFF2ECC71)

    fun boardBg(t: Int): Color = when (t) {
        NEON -> Color(0xFF141B2B)
        CANDY -> Color(0xFFF4E2F3)   // soft candy pink
        else -> Color(0xFFE9DBBE)    // cream, sampled from the reference image
    }

    fun tileColor(t: Int): Color = when (t) {
        CLASSIC -> Color(0xFFE7D6AB)
        NEON -> Color(0xB0141B2B)
        else -> Color(0xFFFFFFFF)
    }

    fun dotColor(t: Int): Color = when (t) {
        NEON -> Color(0x88FFFFFF)
        CANDY -> Color(0x8C8E4A9E)
        else -> Color(0x8C6B5232)
    }

    /** Colour of the faint pattern that fills the empty parts of the board. */
    fun patternColor(t: Int): Color = when (t) {
        NEON -> Color(0x1F00E5FF)
        CANDY -> Color(0x26FF5FA2)
        else -> Color(0x1F8A5A2B)
    }

    fun arrowColor(t: Int, id: Int): Color = when (t) {
        NEON -> NEON_COLORS[id % NEON_COLORS.size]
        else -> CANDY_COLORS[id % CANDY_COLORS.size]
    }

    // every theme uses the same bold, glossy arrows
    fun arrowWidth(t: Int, cell: Float): Float = max(3f, cell * 0.24f)
}

private fun shade(c: Color, f: Float): Color = Color(c.red * f, c.green * f, c.blue * f, 1f)

private fun trianglePath(tx: Float, ty: Float, dx: Float, dy: Float, hl: Float, hw: Float): Path {
    val p = Path()
    p.moveTo(tx + dx * hl, ty + dy * hl)
    p.lineTo(tx - dy * hw, ty + dx * hw)
    p.lineTo(tx + dy * hw, ty - dx * hw)
    p.close()
    return p
}

private fun distSq(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

/** Polyline with rounded (not 90 degree) corners. r = corner radius in pixels. */
private fun roundedPath(pts: List<Offset>, r: Float): Path {
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size - 1) {
        val p0 = pts[i - 1]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val d1x = p1.x - p0.x
        val d1y = p1.y - p0.y
        val d2x = p2.x - p1.x
        val d2y = p2.y - p1.y
        val l1 = sqrt(d1x * d1x + d1y * d1y)
        val l2 = sqrt(d2x * d2x + d2y * d2y)
        val cross = d1x * d2y - d1y * d2x
        if (l1 < 0.01f || l2 < 0.01f || abs(cross) < 0.01f * l1 * l2) {
            path.lineTo(p1.x, p1.y)   // straight on (or a zero length piece)
            continue
        }
        val rr = min(r, min(l1, l2) / 2f)
        path.lineTo(p1.x - d1x / l1 * rr, p1.y - d1y / l1 * rr)
        path.quadraticBezierTo(p1.x, p1.y, p1.x + d2x / l2 * rr, p1.y + d2y / l2 * rr)
    }
    val last = pts[pts.size - 1]
    path.lineTo(last.x, last.y)
    return path
}

/** Draws one snake-like arrow: glossy body with rounded corners and a big rounded arrow head. */
internal fun DrawScope.drawArrow(pts: List<Offset>, color: Color, sw: Float, alpha: Float, theme: Int) {
    if (pts.size < 2) return
    val path = roundedPath(pts, sw * 1.5f)
    val outline = shade(color, 0.55f).copy(alpha = alpha)
    drawPath(
        path,
        outline,
        style = Stroke(width = sw * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path,
        color.copy(alpha = alpha),
        style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    translate(-sw * 0.18f, -sw * 0.18f) {
        drawPath(
            path,
            Color.White.copy(alpha = 0.38f * alpha),
            style = Stroke(width = sw * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    val tip = pts[pts.size - 1]
    var k = pts.size - 2
    while (k > 0 && distSq(pts[k], tip) < 0.01f) k--
    val prev = pts[k]
    var dx = tip.x - prev.x
    var dy = tip.y - prev.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 0.001f) return
    dx /= len
    dy /= len
    val hl = sw * 2.2f
    val hw = sw * 1.65f
    val tri = trianglePath(tip.x, tip.y, dx, dy, hl, hw)
    // rounded corners: fill plus a thick round-join stroke of the same colour
    drawPath(tri, outline, style = Stroke(width = sw * 0.8f, join = StrokeJoin.Round))
    drawPath(tri, outline)
    drawPath(tri, color.copy(alpha = alpha))
    drawPath(tri, color.copy(alpha = alpha), style = Stroke(width = sw * 0.45f, join = StrokeJoin.Round))
}

// ------------------------------------------------------------------ layout + hit testing

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
    val thr = max(lay.cell * 0.5f, 18f)
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

// ------------------------------------------------------------------ slide-out animation

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

/** The arrow moved `shift` cells along its own path and then straight on in the direction it points. */
private fun slidePoints(a: Arrow, lay: BoardLayout, shift: Float): List<Offset> {
    val len = a.cells.size
    val d = a.dir
    val pts = ArrayList<Offset>()
    for (c in a.cells) pts.add(pointOf(c, lay))
    var x = a.head.x
    var y = a.head.y
    for (i in 0 until 3 + shift.toInt()) {
        x += d.dx
        y += d.dy
        pts.add(pointOf(Cell(x, y), lay))
    }
    return windowPoints(pts, shift, shift + (len - 1))
}

/** Faint pattern so the empty parts of the board never look blank. */
private fun DrawScope.drawBoardPattern(theme: Int) {
    val step = max(40f, size.width / 10f)
    val color = Themes.patternColor(theme)
    val rad = step * 0.16f
    var row = 0
    var y = 0f
    while (y < size.height + step) {
        var x = if (row % 2 == 0) 0f else step / 2f
        while (x < size.width + step) {
            drawCircle(color, rad, Offset(x, y))
            x += step
        }
        y += step * 0.6f
        row++
    }
}

@Composable
private fun hintPulse(): Float {
    val inf = rememberInfiniteTransition(label = "hint")
    val p by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "hintPulse"
    )
    return p
}

private fun DrawScope.drawGhost(g: Ghost, lay: BoardLayout, w: Int, h: Int, color: Color, sw: Float, theme: Int) {
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
    drawArrow(win, color, sw, alpha, theme)
}

// ------------------------------------------------------------------ the board

@Composable
fun GameBoard(gs: GameState, zoom: Float, onZoom: (Float) -> Unit, fx: Fx, modifier: Modifier) {
    var pan by remember(gs.gen) { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(Size.Zero) }
    val scope = rememberCoroutineScope()
    val theme = gs.theme
    val zoomNow by rememberUpdatedState(zoom)
    val onZoomNow by rememberUpdatedState(onZoom)

    // green flash while a hint is shown (the animation only runs while there is a hint)
    val pulse = if (gs.hintId >= 0) hintPulse() else 0f

    fun colorFor(a: Arrow): Color = when {
        a.id == gs.blockedId -> Themes.RED
        a.id == gs.hintId -> Themes.HINT
        else -> Themes.arrowColor(theme, a.id)
    }

    Canvas(
        modifier
            .clipToBounds()
            .background(Themes.boardBg(theme))
            .onSizeChanged { boardSize = it.toSize() }
            .pointerInput(gs.gen) {
                detectTapGestures { p ->
                    val lay = boardLayout(boardSize, gs.w, gs.h, zoomNow, pan)
                    val hit = hitTest(gs, lay, p)
                    if (hit != null) gs.onTap(hit, scope, fx)
                }
            }
            .pointerInput(gs.gen) {
                // two-finger pinch zoom; one or two fingers also pan the board
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val oldZoom = zoomNow
                    val newZoom = (oldZoom * zoomChange).coerceIn(1f, 4f)
                    val ratio = newZoom / oldZoom
                    val cx = boardSize.width / 2f
                    val cy = boardSize.height / 2f
                    val qx = centroid.x - cx - pan.x
                    val qy = centroid.y - cy - pan.y
                    val target = Offset(
                        centroid.x - cx - qx * ratio + panChange.x,
                        centroid.y - cy - qy * ratio + panChange.y
                    )
                    pan = boardLayout(boardSize, gs.w, gs.h, newZoom, target).pan
                    if (newZoom != oldZoom) onZoomNow(newZoom)
                }
            }
    ) {
        val lay = boardLayout(this.size, gs.w, gs.h, zoom, pan)
        val mask = gs.mask
        val maskOk = mask.size == gs.w * gs.h
        drawBoardPattern(theme)
        val dot = Themes.dotColor(theme)
        val pad = lay.cell * 0.03f
        val r = max(2f, lay.cell * 0.07f)
        if (maskOk) {
            for (yy in 0 until gs.h) {
                for (xx in 0 until gs.w) {
                    if (!mask[yy * gs.w + xx]) continue
                    val cx = lay.ox + xx * lay.cell
                    val cy = lay.oy + yy * lay.cell
                    // no tile behind the arrows any more, only the dot of every grid point
                    drawCircle(dot, r, Offset(cx, cy))
                }
            }
        }
        val sw = Themes.arrowWidth(theme, lay.cell)
        val bump = gs.bump
        for (a in gs.arrows) {
            val pts = if (bump != null && bump.arrowId == a.id) {
                slidePoints(a, lay, bump.shift)
            } else {
                a.cells.map { pointOf(it, lay) }
            }
            var col = colorFor(a)
            var thick = 1f
            if (a.id == gs.hintId) {
                // flash green and grow a little so the player sees which arrow to tap
                col = lerp(Themes.HINT, Color(0xFFC8FFDC), pulse)
                thick = 1.3f + 0.3f * pulse
            }
            drawArrow(pts, col, sw * thick, 1f, theme)
        }
        for (g in gs.ghosts.values) {
            drawGhost(g, lay, gs.w, gs.h, colorFor(g.arrow), sw, theme)
        }
    }
}
