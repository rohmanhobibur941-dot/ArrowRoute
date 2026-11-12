package com.jubahir.arrowroute

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Dir(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }

data class Cell(val x: Int, val y: Int)

/** An arrow is a snake-like path of grid points. The LAST cell is the head. */
data class Arrow(val id: Int, val cells: List<Cell>) {
    val head: Cell get() = cells.last()

    val dir: Dir
        get() {
            val a = cells[cells.size - 2]
            val b = cells.last()
            return when {
                b.x > a.x -> Dir.RIGHT
                b.x < a.x -> Dir.LEFT
                b.y > a.y -> Dir.DOWN
                else -> Dir.UP
            }
        }
}

/** Board silhouettes: square, circle, the numbers 1 2 3, the letters A B C and a few animals. */
enum class ShapeType { SQUARE, CIRCLE, ONE, LETTER_A, CAT, TWO, LETTER_B, FISH, THREE, LETTER_C, RABBIT, BEAR }

class Level(
    val w: Int,
    val h: Int,
    val mask: BooleanArray,
    val shape: ShapeType,
    val arrows: List<Arrow>
)

/** Difficulty knobs. longMin..longMax = length of the long arrows, keepFree = how many arrows are
 *  allowed to be free at the start (the rest are blocked), straight = chance to keep going straight. */
private class Tier(val longMin: Int, val longMax: Int, val keepFree: Int, val straight: Float)

/** No arrow is shorter than this many cells. */
private const val MIN_LEN = 4

object LevelGen {

    /**
     * Arrow count per level: level 1 = 80, level 2 = 100, level 3 = 120 ... (+20 per level).
     * From level 4 a small deterministic +-8% wobble is added. Capped for very late levels.
     */
    fun arrowTarget(level: Int): Int {
        val base = 80 + 20 * (level - 1)
        val wobble = if (level <= 3) 0.0 else ((level * 7919) % 17 - 8) / 100.0
        return (base * (1.0 + wobble)).toInt().coerceIn(80, 400)
    }

    /** Easy -> Medium -> Hard -> Extreme: longer arrows and fewer free arrows at the start. */
    private fun tierFor(level: Int): Tier = when {
        level <= 4 -> Tier(4, 8, 4, 0.55f)
        level <= 9 -> Tier(5, 10, 4, 0.45f)
        level <= 19 -> Tier(6, 12, 3, 0.40f)
        else -> Tier(7, 14, 3, 0.35f)
    }

    fun shapeFor(level: Int): ShapeType {
        val all = ShapeType.values()
        return all[(level - 1) % all.size]
    }

    /** Draws the silhouette with android.graphics and turns it into a grid of cells. */
    fun makeMask(shape: ShapeType, w: Int, h: Int): BooleanArray {
        val sc = 4
        val bw = w * sc
        val bh = h * sc
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.WHITE
        p.style = Paint.Style.FILL
        drawShape(c, p, shape, bw.toFloat(), bh.toFloat())
        val pix = IntArray(bw * bh)
        bmp.getPixels(pix, 0, bw, 0, 0, bw, bh)
        bmp.recycle()
        val m = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0
                for (yy in 0 until sc) {
                    for (xx in 0 until sc) {
                        sum += pix[(y * sc + yy) * bw + x * sc + xx] ushr 24
                    }
                }
                m[y * w + x] = sum >= 128 * sc * sc
            }
        }
        return dropSpecks(m, w, h)
    }

    private fun drawGlyph(c: Canvas, p: Paint, text: String, bw: Float, bh: Float) {
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        p.style = Paint.Style.FILL_AND_STROKE
        p.strokeWidth = bh * 0.035f
        p.textSize = bh
        val b = Rect()
        p.getTextBounds(text, 0, text.length, b)
        val fit = (bh * 0.94f) / b.height()
        p.textSize = bh * fit
        p.getTextBounds(text, 0, text.length, b)
        val gw = b.width().toFloat()
        val sx = (bw * 0.96f / gw).coerceIn(0.8f, 1.5f)
        c.save()
        c.scale(sx, 1f, bw / 2f, bh / 2f)
        val x = (bw - gw) / 2f - b.left
        val y = (bh - b.height()) / 2f - b.top
        c.drawText(text, x, y, p)
        c.restore()
        p.style = Paint.Style.FILL
    }

    private fun drawShape(c: Canvas, p: Paint, shape: ShapeType, bw: Float, bh: Float) {
        // animals are drawn in a box that is at most 1.5 times as tall as wide
        val rh = min(bh, bw * 1.5f)
        val top = (bh - rh) / 2f
        fun ov(cx: Float, cy: Float, rx: Float, ry: Float): RectF =
            RectF((cx - rx) * bw, top + (cy - ry) * rh, (cx + rx) * bw, top + (cy + ry) * rh)
        fun poly(vararg pts: Float) {
            val path = Path()
            path.moveTo(pts[0] * bw, top + pts[1] * rh)
            var i = 2
            while (i < pts.size) {
                path.lineTo(pts[i] * bw, top + pts[i + 1] * rh)
                i += 2
            }
            path.close()
            c.drawPath(path, p)
        }
        when (shape) {
            ShapeType.SQUARE -> c.drawRoundRect(RectF(0f, 0f, bw, bh), bw * 0.05f, bw * 0.05f, p)
            ShapeType.CIRCLE -> c.drawOval(RectF(0f, 0f, bw, bh), p)
            ShapeType.ONE -> drawGlyph(c, p, "1", bw, bh)
            ShapeType.TWO -> drawGlyph(c, p, "2", bw, bh)
            ShapeType.THREE -> drawGlyph(c, p, "3", bw, bh)
            ShapeType.LETTER_A -> drawGlyph(c, p, "A", bw, bh)
            ShapeType.LETTER_B -> drawGlyph(c, p, "B", bw, bh)
            ShapeType.LETTER_C -> drawGlyph(c, p, "C", bw, bh)
            ShapeType.CAT -> {
                c.drawOval(ov(0.5f, 0.6f, 0.42f, 0.34f), p)
                poly(0.08f, 0.52f, 0.12f, 0.04f, 0.46f, 0.28f)
                poly(0.92f, 0.52f, 0.88f, 0.04f, 0.54f, 0.28f)
            }
            ShapeType.FISH -> {
                c.drawOval(ov(0.42f, 0.5f, 0.41f, 0.31f), p)
                poly(0.66f, 0.5f, 0.99f, 0.16f, 0.99f, 0.84f)
                poly(0.30f, 0.24f, 0.48f, 0.04f, 0.62f, 0.26f)
            }
            ShapeType.RABBIT -> {
                c.drawOval(ov(0.36f, 0.19f, 0.10f, 0.19f), p)
                c.drawOval(ov(0.64f, 0.19f, 0.10f, 0.19f), p)
                c.drawOval(ov(0.5f, 0.46f, 0.26f, 0.19f), p)
                c.drawOval(ov(0.5f, 0.78f, 0.34f, 0.21f), p)
            }
            ShapeType.BEAR -> {
                c.drawOval(ov(0.5f, 0.56f, 0.42f, 0.38f), p)
                c.drawOval(ov(0.17f, 0.2f, 0.15f, 0.12f), p)
                c.drawOval(ov(0.83f, 0.2f, 0.15f, 0.12f), p)
            }
        }
    }

    /** Removes tiny islands (fewer than 12 cells) that no arrow could ever use. */
    private fun dropSpecks(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!m[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            val comp = ArrayList<Int>()
            while (sp > 0) {
                val k = stack[--sp]
                comp.add(k)
                val x = k % w
                val y = k / w
                for (d in Dir.values()) {
                    val nx = x + d.dx
                    val ny = y + d.dy
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val nk = ny * w + nx
                    if (m[nk] && !seen[nk]) {
                        seen[nk] = true
                        stack[sp++] = nk
                    }
                }
            }
            if (comp.size < 12) for (k in comp) m[k] = false
        }
        return m
    }

    fun occupancy(arrows: List<Arrow>, w: Int): HashSet<Int> {
        val set = HashSet<Int>()
        for (a in arrows) for (c in a.cells) set.add(c.y * w + c.x)
        return set
    }

    /** Number of steps from the head until the ray leaves the grid (>= 1). */
    fun exitDistance(a: Arrow, w: Int, h: Int): Int {
        val d = a.dir
        var x = a.head.x
        var y = a.head.y
        var n = 0
        while (x in 0 until w && y in 0 until h) {
            x += d.dx
            y += d.dy
            n++
        }
        return n
    }

    /** True if every grid point in front of the head (to the edge) is empty. */
    fun canExit(a: Arrow, w: Int, h: Int, occ: Set<Int>): Boolean {
        val d = a.dir
        var x = a.head.x + d.dx
        var y = a.head.y + d.dy
        while (x in 0 until w && y in 0 until h) {
            if (occ.contains(y * w + x)) return false
            x += d.dx
            y += d.dy
        }
        return true
    }

    /** Smallest grid (with the given width/height ratio) whose silhouette has enough cells. */
    private fun sizeFor(shape: ShapeType, aspect: Float, needed: Int): Pair<Int, Int> {
        var w = 8
        while (w < 90) {
            val h = max(8, (w / aspect).roundToInt())
            val m = makeMask(shape, w, h)
            var n = 0
            for (b in m) if (b) n++
            if (n >= needed) return Pair(w, h)
            w += if (n < needed * 0.8) 3 else 1
        }
        return Pair(90, max(8, (90 / aspect).roundToInt()))
    }

    /** aspect = board width / board height of the play area, so the grid fills the screen.
     *  Runs for up to ~2.5s on big levels, so call it from a background thread when possible. */
    fun generate(level: Int, aspect: Float, optimize: Boolean = true): Level {
        val shape = shapeFor(level)
        val tier = tierFor(level)
        val target = arrowTarget(level)
        val rnd = Random(System.nanoTime())
        val deadline = System.nanoTime() + 2_500_000_000L

        var avg = (tier.longMin + tier.longMax) / 2f * 0.85f
        var scale = 1f
        var best: Level? = null
        var bestDiff = Int.MAX_VALUE
        // Every attempt is solvable (checked while building); we retry only to land near the arrow target.
        for (attempt in 0 until 5) {
            val size = sizeFor(shape, aspect, (target * avg * 1.05f).toInt())
            val w = size.first
            val h = size.second
            val mask = makeMask(shape, w, h)
            val g = Gen(w, h, rnd, tier.straight)
            g.build(mask, tier.longMin, tier.longMax, tier.keepFree, scale)
            if (optimize) g.optimize(tier.keepFree, deadline)
            val list = g.arrows.mapIndexed { i, p -> Arrow(i, p.map { k -> Cell(k % w, k / w) }) }
            val diff = abs(list.size - target)
            if (diff < bestDiff) {
                bestDiff = diff
                best = Level(w, h, mask, shape, list)
            }
            if (diff <= target * 0.08) break
            if (System.nanoTime() > deadline) break
            var cells = 0
            for (a in list) cells += a.cells.size
            if (list.isNotEmpty()) avg = cells.toFloat() / list.size
            scale = (scale * list.size / target).coerceIn(0.5f, 2f)
        }
        return checkNotNull(best)
    }

    /** Never throws: if the fancy generator fails, fall back to a simpler one, then to a tiny level. */
    fun generateSafe(level: Int, aspect: Float, onError: (Throwable) -> Unit = {}): Level {
        try {
            return generate(level, aspect)
        } catch (t: Throwable) {
            onError(t)
        }
        try {
            return generate(level, aspect, optimize = false)
        } catch (t: Throwable) {
            onError(t)
        }
        val w = 4
        val h = 4
        val mask = BooleanArray(w * h) { true }
        val list = listOf(
            Arrow(0, listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0))),
            Arrow(1, listOf(Cell(0, 2), Cell(1, 2), Cell(2, 2), Cell(3, 2)))
        )
        return Level(w, h, mask, ShapeType.SQUARE, list)
    }
}

/**
 * Level builder, in two steps.
 *
 * 1) build(): arrows are placed one by one (placement order = reverse removal order, so the level is
 *    solvable). While more than `keep` arrows are still unblocked, the next arrow is forced to
 *    cover a cell in front of an unblocked arrow, so arrows block each other heavily.
 * 2) optimize(): flips arrows / grows arrow tails into empty cells so that even more arrows are
 *    blocked, and fills holes. Every change is checked with a dependency graph (no deadlocks).
 */
private class Gen(val w: Int, val h: Int, val rnd: Random, val straight: Float) {

    val arrows = ArrayList<List<Int>>()
    private var free = BooleanArray(0)
    private var owner = IntArray(0)

    private fun px(k: Int) = k % w
    private fun py(k: Int) = k / w

    private fun dirOf(p: List<Int>): Dir {
        val a = p[p.size - 2]
        val b = p[p.size - 1]
        val dx = px(b) - px(a)
        val dy = py(b) - py(a)
        return when {
            dx > 0 -> Dir.RIGHT
            dx < 0 -> Dir.LEFT
            dy > 0 -> Dir.DOWN
            else -> Dir.UP
        }
    }

    private fun rayCells(p: List<Int>): List<Int> {
        val d = dirOf(p)
        val head = p[p.size - 1]
        var x = px(head) + d.dx
        var y = py(head) + d.dy
        val out = ArrayList<Int>()
        while (x in 0 until w && y in 0 until h) {
            out.add(y * w + x)
            x += d.dx
            y += d.dy
        }
        return out
    }

    private fun rayFree(p: List<Int>, occ: BooleanArray): Boolean {
        for (k in rayCells(p)) {
            if (occ[k] || p.contains(k)) return false
        }
        return true
    }

    private fun selfBlock(p: List<Int>): Boolean {
        for (k in rayCells(p)) {
            if (p.contains(k)) return true
        }
        return false
    }

    /** Number of still-empty cells in front of the head (these can be filled by a blocker later). */
    private fun freeAhead(p: List<Int>, fr: BooleanArray): Int {
        var n = 0
        for (k in rayCells(p)) if (fr[k]) n++
        return n
    }

    private fun onRay(a: List<Int>, c: Int): Boolean {
        val d = dirOf(a)
        val head = a[a.size - 1]
        val hx = px(head)
        val hy = py(head)
        val cx = px(c)
        val cy = py(c)
        return if (d.dy == 0) {
            cy == hy && (cx - hx) * d.dx > 0
        } else {
            cx == hx && (cy - hy) * d.dy > 0
        }
    }

    private fun freeDegree(x: Int, y: Int, cells: List<Int>): Int {
        var n = 0
        for (e in Dir.values()) {
            val ax = x + e.dx
            val ay = y + e.dy
            if (ax in 0 until w && ay in 0 until h) {
                val k = ay * w + ax
                if (free[k] && !cells.contains(k)) n++
            }
        }
        return n
    }

    private fun growFrom(body: MutableList<Int>, target: Int): MutableList<Int> {
        val cells = body
        var d: Dir? = null
        while (cells.size < target) {
            val cur = cells[cells.size - 1]
            val cx = px(cur)
            val cy = py(cur)
            val opts = ArrayList<Dir>()
            for (dd in Dir.values()) {
                val nx = cx + dd.dx
                val ny = cy + dd.dy
                if (nx in 0 until w && ny in 0 until h) {
                    val k = ny * w + nx
                    if (free[k] && !cells.contains(k)) opts.add(dd)
                }
            }
            if (opts.isEmpty()) break
            val prev = d
            val nd: Dir
            if (opts.size > 1 && rnd.nextFloat() < 0.5f) {
                // step towards the most cramped neighbour first, so dead ends do not stay empty
                var bestDeg = Int.MAX_VALUE
                val cand = ArrayList<Dir>()
                for (o in opts) {
                    val dg = freeDegree(cx + o.dx, cy + o.dy, cells)
                    if (dg < bestDeg) {
                        bestDeg = dg
                        cand.clear()
                        cand.add(o)
                    } else if (dg == bestDeg) {
                        cand.add(o)
                    }
                }
                nd = cand[rnd.nextInt(cand.size)]
            } else if (prev != null && opts.contains(prev) && rnd.nextFloat() < straight) {
                nd = prev
            } else {
                nd = opts[rnd.nextInt(opts.size)]
            }
            d = nd
            cells.add((cy + nd.dy) * w + (cx + nd.dx))
        }
        return cells
    }

    /** An arrow whose HEAD is cell c and points in direction d; its body grows behind the head. */
    private fun headAt(c: Int, d: Dir, occ: BooleanArray, target: Int): List<Int>? {
        val bx = px(c) - d.dx
        val by = py(c) - d.dy
        if (bx < 0 || bx >= w || by < 0 || by >= h) return null
        val b = by * w + bx
        if (!free[b]) return null
        val cells = growFrom(mutableListOf(c, b), target)
        val q = cells.reversed()
        if (q.size < MIN_LEN) return null
        if (!rayFree(q, occ)) return null
        if (freeAhead(q, free) < 1) return null
        return q
    }

    private fun pickLen(longMin: Int, longMax: Int, scale: Float): Int {
        val t = ((longMin + rnd.nextInt(longMax - longMin + 1)) * scale).roundToInt()
        return max(MIN_LEN, t)
    }

    fun build(mask: BooleanArray, longMin: Int, longMax: Int, keep: Int, scale: Float) {
        arrows.clear()
        free = mask.copyOf()
        val occ = BooleanArray(w * h)
        val dead = BooleanArray(w * h)

        val cx = (w - 1) / 2.0
        val cy = (h - 1) / 2.0
        val maxd = hypot(cx, cy)
        val order = (0 until w * h)
            .filter { mask[it] }
            .map { k -> Pair(k, hypot(px(k) - cx, py(k) - cy) + rnd.nextDouble() * maxd * 0.35) }
            .sortedBy { it.second }
            .map { it.first }

        var ptr = 0
        var unblocked = ArrayList<Int>()
        var guard = 0
        while (guard < w * h * 4) {
            guard++
            // seed cells: first the cells in front of unblocked arrows (so they get blocked), then centre-out
            val seeds = ArrayList<Int>()
            if (unblocked.size > keep) {
                val cov = HashMap<Int, Int>()
                for (i in unblocked) {
                    for (k in rayCells(arrows[i])) {
                        if (free[k] && !dead[k]) cov[k] = (cov[k] ?: 0) + 1
                    }
                }
                if (cov.isNotEmpty()) {
                    val scored = cov.entries
                        .map { Pair(it.key, -it.value + rnd.nextDouble() * 1.5) }
                        .sortedBy { it.second }
                        .take(6)
                    for (p in scored) seeds.add(p.first)
                }
            }
            while (ptr < order.size && (!free[order[ptr]] || dead[order[ptr]])) ptr++
            if (ptr < order.size) seeds.add(order[ptr])
            if (seeds.isEmpty()) break

            for (c in seeds) {
                var cand: List<Int>? = null
                var attempt = 0
                while (attempt < 8 && cand == null) {
                    attempt++
                    val t = pickLen(longMin, longMax, scale)
                    val dirs = Dir.values().toMutableList()
                    dirs.shuffle(rnd)
                    for (d in dirs) {
                        val q = headAt(c, d, occ, t)
                        if (q != null) {
                            cand = q
                            break
                        }
                    }
                    if (cand == null) {
                        val p = growFrom(mutableListOf(c), t)
                        if (p.size >= MIN_LEN) {
                            val rev = p.reversed()
                            val pair = if (freeAhead(p, free) >= freeAhead(rev, free)) listOf(p, rev) else listOf(rev, p)
                            for (q in pair) {
                                if (rayFree(q, occ) && freeAhead(q, free) >= 1) {
                                    cand = q
                                    break
                                }
                            }
                        }
                    }
                }
                if (cand != null) {
                    arrows.add(cand)
                    for (k in cand) {
                        occ[k] = true
                        free[k] = false
                    }
                    val nf = ArrayList<Int>()
                    for (i in unblocked) {
                        var blocked = false
                        for (k in cand) {
                            if (onRay(arrows[i], k)) {
                                blocked = true
                                break
                            }
                        }
                        if (!blocked) nf.add(i)
                    }
                    nf.add(arrows.size - 1)
                    unblocked = nf
                    break
                } else {
                    dead[c] = true
                }
            }
        }
    }

    // ---------------------------------------------------------------- dependency graph

    /** deps[i] = arrows that currently sit in front of arrow i (they must leave first). */
    private fun computeDeps(): Array<IntArray> {
        val n = arrows.size
        val stamp = IntArray(n) { -1 }
        val tmp = IntArray(n + 1)
        return Array(n) { i ->
            val a = arrows[i]
            val d = dirOf(a)
            val head = a[a.size - 1]
            var x = px(head) + d.dx
            var y = py(head) + d.dy
            var cnt = 0
            while (x in 0 until w && y in 0 until h) {
                val o = owner[y * w + x]
                if (o >= 0 && stamp[o] != i) {
                    stamp[o] = i
                    tmp[cnt++] = o
                }
                x += d.dx
                y += d.dy
            }
            tmp.copyOf(cnt)
        }
    }

    /** True if arrows can all be removed one after another (no two arrows block each other in a loop). */
    private fun acyclic(deps: Array<IntArray>): Boolean {
        val n = deps.size
        val indeg = IntArray(n) { deps[it].size }
        val rev = Array(n) { ArrayList<Int>() }
        for (i in 0 until n) for (j in deps[i]) rev[j].add(i)
        val stack = IntArray(n + 1)
        var sp = 0
        for (i in 0 until n) if (indeg[i] == 0) stack[sp++] = i
        var seen = 0
        while (sp > 0) {
            val i = stack[--sp]
            seen++
            for (u in rev[i]) {
                indeg[u]--
                if (indeg[u] == 0) stack[sp++] = u
            }
        }
        return seen == n
    }

    /** Paths through empty cells from `start` to a cell next to the TAIL of some arrow. */
    private fun pathsToTail(start: Int, maxd: Int): ArrayList<Pair<List<Int>, Int>> {
        val prev = HashMap<Int, Int>()
        val depth = HashMap<Int, Int>()
        prev[start] = -1
        depth[start] = 0
        val q = ArrayList<Int>()
        q.add(start)
        val out = ArrayList<Pair<List<Int>, Int>>()
        var qi = 0
        while (qi < q.size) {
            val v = q[qi]
            qi++
            val vx = px(v)
            val vy = py(v)
            for (d in Dir.values()) {
                val nx = vx + d.dx
                val ny = vy + d.dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                val k = ny * w + nx
                val o = owner[k]
                if (o >= 0 && arrows[o][0] == k) {
                    val p = ArrayList<Int>()
                    var c = v
                    while (c != -1) {
                        p.add(c)
                        c = prev[c] ?: -1
                    }
                    out.add(Pair(p, o))
                }
            }
            val dv = depth[v] ?: 0
            if (dv < maxd) {
                for (d in Dir.values()) {
                    val nx = vx + d.dx
                    val ny = vy + d.dy
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val k = ny * w + nx
                    if (free[k] && !prev.containsKey(k)) {
                        prev[k] = v
                        depth[k] = dv + 1
                        q.add(k)
                    }
                }
            }
        }
        out.shuffle(rnd)
        out.sortBy { it.first.size }
        return out
    }

    /** Grow the tail of arrow b through the empty cells in `path` (listed from the tail side back to the start). */
    private fun tryExtend(path: List<Int>, b: Int): Boolean {
        val newCells = path.reversed()
        val old = arrows[b]
        val nb = ArrayList<Int>(newCells.size + old.size)
        nb.addAll(newCells)
        nb.addAll(old)
        if (selfBlock(nb)) return false
        arrows[b] = nb
        for (k in newCells) owner[k] = b
        if (acyclic(computeDeps())) {
            for (k in newCells) free[k] = false
            return true
        }
        arrows[b] = old
        for (k in newCells) owner[k] = -1
        return false
    }

    fun optimize(keep: Int, deadline: Long) {
        owner = IntArray(w * h) { -1 }
        for (i in arrows.indices) for (k in arrows[i]) owner[k] = i

        var stall = 0
        var round = 0
        while (round < 120 && System.nanoTime() < deadline) {
            round++
            val deps = computeDeps()
            val fr = ArrayList<Int>()
            for (i in deps.indices) if (deps[i].isEmpty()) fr.add(i)
            if (fr.size <= keep) break
            fr.shuffle(rnd)
            var prog = false
            for (idx in keep until fr.size) {
                if (System.nanoTime() > deadline) break
                val a = fr[idx]

                // 1) point the arrow the other way if that side is blocked (and nothing deadlocks)
                val old = arrows[a]
                val rev = old.reversed()
                if (!selfBlock(rev)) {
                    arrows[a] = rev
                    val d2 = computeDeps()
                    if (d2[a].isNotEmpty() && acyclic(d2)) {
                        prog = true
                        continue
                    }
                    arrows[a] = old
                }

                // 2) grow the tail of another arrow into an empty cell in front of this arrow
                val gaps = ArrayList<Int>()
                for (k in rayCells(arrows[a])) if (free[k]) gaps.add(k)
                gaps.shuffle(rnd)
                var done = false
                var gi = 0
                while (gi < gaps.size && gi < 12 && !done) {
                    val paths = pathsToTail(gaps[gi], 12)
                    var pi = 0
                    while (pi < paths.size && pi < 16) {
                        val pr = paths[pi]
                        if (pr.second != a && tryExtend(pr.first, pr.second)) {
                            done = true
                            break
                        }
                        pi++
                    }
                    gi++
                }
                if (done) prog = true
            }
            if (!prog) {
                stall++
                if (stall >= 3) break
            } else {
                stall = 0
            }
        }

        // fill the remaining holes by growing tails (no new arrows, so no new free arrows)
        for (pass in 0 until 2) {
            for (c in 0 until w * h) {
                if (!free[c]) continue
                val paths = pathsToTail(c, 2)
                var pi = 0
                while (pi < paths.size && pi < 6) {
                    if (tryExtend(paths[pi].first, paths[pi].second)) break
                    pi++
                }
            }
        }
    }
}
