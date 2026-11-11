package com.jubahir.arrowroute

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

object Themes {
    const val CLASSIC = 0
    const val NEON = 1
    const val CANDY = 2

    private val NEON_COLORS = listOf(
        0xFFFFD54F, 0xFF64B5F6, 0xFFBA68C8, 0xFFF06292, 0xFF4DD0E1,
        0xFFAED581, 0xFFFF8A65, 0xFF7986CB, 0xFF81C784, 0xFFE57373
    ).map { Color(it) }

    private val CANDY_COLORS = listOf(
        0xFF1E1E24, 0xFF22B14C, 0xFFFFC70F, 0xFF2D8CFF, 0xFFF0453A,
        0xFF8E5BE8, 0xFFFF8A2B, 0xFFFF5FA2, 0xFF14C4B4
    ).map { Color(it) }

    val RED = Color(0xFFE53935)
    val HINT = Color(0xFF2ECC71)

    fun boardBg(t: Int): Color = when (t) {
        CLASSIC -> Color(0xFFF1E4C3)
        NEON -> Color(0xFF141B2B)
        else -> Color(0xFFEAF3FF)
    }

    fun tileColor(t: Int): Color = when (t) {
        CLASSIC -> Color(0xFFE7D6AB)
        NEON -> Color(0xB0141B2B)
        else -> Color(0xFFFFFFFF)
    }

    fun dotColor(t: Int): Color = when (t) {
        CLASSIC -> Color(0xFFB9A57C)
        NEON -> Color(0x66FFFFFF)
        else -> Color(0xFFC3D3EA)
    }

    fun arrowColor(t: Int, id: Int): Color = when (t) {
        CLASSIC -> Color(0xFF1B1B1B)
        NEON -> NEON_COLORS[id % NEON_COLORS.size]
        else -> CANDY_COLORS[id % CANDY_COLORS.size]
    }

    fun arrowWidth(t: Int, cell: Float): Float =
        max(3f, cell * (if (t == CANDY) 0.24f else 0.16f))
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

/** Draws one snake-like arrow in the given theme style. Shared by the board and theme previews. */
internal fun DrawScope.drawArrow(pts: List<Offset>, color: Color, sw: Float, alpha: Float, theme: Int) {
    if (pts.size < 2) return
    val candy = theme == Themes.CANDY
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)

    if (candy) {
        drawPath(
            path,
            shade(color, 0.55f).copy(alpha = alpha),
            style = Stroke(width = sw * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
    drawPath(
        path,
        color.copy(alpha = alpha),
        style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    if (candy) {
        translate(-sw * 0.18f, -sw * 0.18f) {
            drawPath(
                path,
                Color.White.copy(alpha = 0.38f * alpha),
                style = Stroke(width = sw * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
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
    val hl = sw * 1.75f
    val hw = sw * 1.3f
    if (candy) {
        drawPath(
            trianglePath(tip.x - dx * sw * 0.1f, tip.y - dy * sw * 0.1f, dx, dy, hl + sw * 0.25f, hw + sw * 0.2f),
            shade(color, 0.55f).copy(alpha = alpha)
        )
    }
    drawPath(trianglePath(tip.x, tip.y, dx, dy, hl, hw), color.copy(alpha = alpha))
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

    fun colorFor(a: Arrow): Color = when {
        a.id == gs.blockedId -> Themes.RED
        a.id == gs.hintId -> Themes.HINT
        else -> Themes.arrowColor(theme, a.id)
    }

    Canvas(
        modifier
            .clipToBounds()
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
        val tile = Themes.tileColor(theme)
        val dot = Themes.dotColor(theme)
        val pad = lay.cell * 0.03f
        val r = max(1.5f, lay.cell * 0.04f)
        if (maskOk) {
            for (yy in 0 until gs.h) {
                for (xx in 0 until gs.w) {
                    if (!mask[yy * gs.w + xx]) continue
                    val cx = lay.ox + xx * lay.cell
                    val cy = lay.oy + yy * lay.cell
                    drawRoundRect(
                        tile,
                        topLeft = Offset(cx - lay.cell / 2f + pad, cy - lay.cell / 2f + pad),
                        size = Size(lay.cell - 2f * pad, lay.cell - 2f * pad),
                        cornerRadius = CornerRadius(lay.cell * 0.2f)
                    )
                    drawCircle(dot, r, Offset(cx, cy))
                }
            }
        }
        val sw = Themes.arrowWidth(theme, lay.cell)
        for (a in gs.arrows) {
            val pts = a.cells.map { pointOf(it, lay) }
            val thick = if (a.id == gs.hintId) 1.3f else 1f
            drawArrow(pts, colorFor(a), sw * thick, 1f, theme)
        }
        for (g in gs.ghosts.values) {
            drawGhost(g, lay, gs.w, gs.h, colorFor(g.arrow), sw, theme)
        }
    }
}
