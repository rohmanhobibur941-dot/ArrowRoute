package com.jubahir.arrowroute

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

/** Board silhouettes. Levels cycle through these so the board is not always a square. */
enum class ShapeType { RECT, CIRCLE, OCTAGON, PLUS, HEART, RING }

class Level(
    val w: Int,
    val h: Int,
    val mask: BooleanArray,
    val shape: ShapeType,
    val arrows: List<Arrow>
)

/** Difficulty knobs. avgLen = average arrow length, straight = chance to keep going straight,
 *  blockBias = chance that an arrow is aimed at still-empty cells (so later arrows block it). */
private class Tier(val avgLen: Float, val straight: Float, val blockBias: Float)

object LevelGen {

    /**
     * Arrow count per level: level 1 = 50, level 2 = 65, level 3 = 80 ... (+15 per level).
     * From level 4 a small deterministic +-8% wobble is added so it does not feel mechanical.
     * Capped so very late levels stay playable on a phone screen.
     */
    fun arrowTarget(level: Int): Int {
        val base = 50 + 15 * (level - 1)
        val wobble = if (level <= 3) 0.0 else ((level * 7919) % 17 - 8) / 100.0
        return (base * (1.0 + wobble)).toInt().coerceIn(50, 420)
    }

    /** Easy -> Medium -> Hard -> Extreme. Longer, twistier arrows that are aimed at blocked cells. */
    private fun tierFor(level: Int): Tier = when {
        level <= 4 -> Tier(3.0f, 0.70f, 0.35f)
        level <= 9 -> Tier(3.6f, 0.55f, 0.60f)
        level <= 19 -> Tier(4.2f, 0.40f, 0.80f)
        else -> Tier(5.0f, 0.30f, 0.95f)
    }

    fun shapeFor(level: Int): ShapeType {
        val all = ShapeType.values()
        return all[(level - 1) % all.size]
    }

    fun makeMask(shape: ShapeType, w: Int, h: Int): BooleanArray {
        val m = BooleanArray(w * h)
        val cx = (w - 1) / 2.0
        val cy = (h - 1) / 2.0
        val rx = w / 2.0
        val ry = h / 2.0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                val ok = when (shape) {
                    ShapeType.RECT -> !(abs(dx) > 0.85 && abs(dy) > 0.85)
                    ShapeType.CIRCLE -> abs(dx).pow(2.6) + abs(dy).pow(2.6) <= 1.0
                    ShapeType.OCTAGON -> abs(dx) + abs(dy) <= 1.45
                    ShapeType.HEART -> {
                        val u = dx * 1.2
                        val v = -dy * 1.15 + 0.1
                        val a = u * u + v * v - 1.0
                        a * a * a - u * u * v * v * v <= 0.0
                    }
                    ShapeType.PLUS -> abs(dx) <= 0.55 || abs(dy) <= 0.55
                    ShapeType.RING -> {
                        val r2 = dx * dx + dy * dy
                        r2 <= 1.0 && r2 >= 0.07
                    }
                }
                m[y * w + x] = ok
            }
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
        var w = 6
        while (w < 70) {
            val h = max(6, (w / aspect).roundToInt())
            val m = makeMask(shape, w, h)
            var n = 0
            for (b in m) if (b) n++
            if (n >= needed) return Pair(w, h)
            w++
        }
        return Pair(70, max(6, (70 / aspect).roundToInt()))
    }

    /** aspect = board width / board height of the play area, so the grid fills the screen. */
    fun generate(level: Int, aspect: Float): Level {
        val shape = shapeFor(level)
        val tier = tierFor(level)
        val target = arrowTarget(level)
        val size = sizeFor(shape, aspect, (target * tier.avgLen).toInt())
        val w = size.first
        val h = size.second
        val mask = makeMask(shape, w, h)
        val rnd = Random(System.nanoTime())

        var mean = tier.avgLen * 1.05f
        var best: Level? = null
        var bestDiff = Int.MAX_VALUE
        // Every attempt is solvable by construction; we retry only to land close to the arrow target.
        for (attempt in 0 until 6) {
            val g = Gen(w, h, rnd, tier.straight, tier.blockBias)
            val paths = g.build(mask, mean)
            val arrows = paths.mapIndexed { i, p -> Arrow(i, p.map { k -> Cell(k % w, k / w) }) }
            val diff = abs(arrows.size - target)
            if (diff < bestDiff) {
                bestDiff = diff
                best = Level(w, h, mask, shape, arrows)
            }
            if (diff <= target * 0.05) break
            mean = (mean * arrows.size / target).coerceIn(2f, 16f)
        }
        return checkNotNull(best)
    }
}

/**
 * Level builder. Arrows are placed from the middle outwards and each new arrow's exit ray must
 * not touch anything placed before it, so removing arrows in reverse placement order always works
 * (every level is solvable). Leftover cells are then merged into 2-cell arrows or tails of
 * existing arrows (with the same guarantee) so the board stays dense.
 */
private class Gen(val w: Int, val h: Int, val rnd: Random, val straight: Float, val bias: Float) {

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

    private fun rayFree(p: List<Int>, occ: BooleanArray): Boolean {
        val d = dirOf(p)
        val head = p[p.size - 1]
        var x = px(head) + d.dx
        var y = py(head) + d.dy
        while (x in 0 until w && y in 0 until h) {
            val k = y * w + x
            if (occ[k] || p.contains(k)) return false
            x += d.dx
            y += d.dy
        }
        return true
    }

    /** Number of still-empty cells in front of the head (these will block the arrow later). */
    private fun freeAhead(p: List<Int>, free: BooleanArray): Int {
        val d = dirOf(p)
        val head = p[p.size - 1]
        var x = px(head) + d.dx
        var y = py(head) + d.dy
        var n = 0
        while (x in 0 until w && y in 0 until h) {
            if (free[y * w + x]) n++
            x += d.dx
            y += d.dy
        }
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

    private fun freeDegree(x: Int, y: Int, free: BooleanArray, cells: List<Int>): Int {
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

    private fun grow(start: Int, free: BooleanArray, target: Int): MutableList<Int> {
        val cells = mutableListOf(start)
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
                    val dg = freeDegree(cx + o.dx, cy + o.dy, free, cells)
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

    fun build(mask: BooleanArray, meanLen: Float): List<List<Int>> {
        val free = mask.copyOf()
        val occ = BooleanArray(w * h)

        val cx = (w - 1) / 2.0
        val cy = (h - 1) / 2.0
        val maxd = hypot(cx, cy)
        val order = (0 until w * h)
            .filter { mask[it] }
            .map { k -> Pair(k, hypot(px(k) - cx, py(k) - cy) + rnd.nextDouble() * maxd * 0.5) }
            .sortedBy { it.second }
            .map { it.first }

        val arrows = ArrayList<List<Int>>()
        val hardMax = max(8, (meanLen * 2f).toInt())

        for (c in order) {
            if (!free[c]) continue
            var placed = false
            var attempt = 0
            while (attempt < 8 && !placed) {
                val target = if (attempt >= 5) {
                    2
                } else {
                    val t = (meanLen * (0.5f + rnd.nextFloat())).roundToInt()
                    min(hardMax, max(2, t))
                }
                val p = grow(c, free, target)
                if (p.size < 2) break
                val rev = p.reversed()
                val a = freeAhead(p, free)
                val b = freeAhead(rev, free)
                val preferBlocked = rnd.nextFloat() < bias
                val first: List<Int> = if ((a >= b) == preferBlocked) p else rev
                val second: List<Int> = if (first === p) rev else p
                for (q in listOf(first, second)) {
                    if (rayFree(q, occ)) {
                        arrows.add(q)
                        for (k in q) {
                            occ[k] = true
                            free[k] = false
                        }
                        placed = true
                        break
                    }
                }
                attempt++
            }
        }

        // Repair: fill leftover cells so there are no big empty holes.
        val dirs = Dir.values().toMutableList()
        for (pass in 0 until 3) {
            for (c in order) {
                if (!free[c]) continue
                var done = false

                // 1) new 2-cell arrow with a free neighbour (placed last, so only its own ray matters)
                dirs.shuffle(rnd)
                for (dd in dirs) {
                    val nx = px(c) + dd.dx
                    val ny = py(c) + dd.dy
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val n = ny * w + nx
                    if (!free[n]) continue
                    for (q in listOf(listOf(c, n), listOf(n, c))) {
                        if (rayFree(q, occ)) {
                            arrows.add(q)
                            occ[c] = true
                            occ[n] = true
                            free[c] = false
                            free[n] = false
                            done = true
                            break
                        }
                    }
                    if (done) break
                }
                if (done) continue

                // 2) extend the tail of a neighbouring arrow, if no ray that matters crosses the cell
                for (i in arrows.indices) {
                    val a = arrows[i]
                    val t = a[0]
                    if (abs(px(t) - px(c)) + abs(py(t) - py(c)) != 1) continue
                    if (onRay(a, c)) continue
                    var ok = true
                    for (j in i + 1 until arrows.size) {
                        if (onRay(arrows[j], c)) {
                            ok = false
                            break
                        }
                    }
                    if (!ok) continue
                    val na = ArrayList<Int>(a.size + 1)
                    na.add(c)
                    na.addAll(a)
                    arrows[i] = na
                    occ[c] = true
                    free[c] = false
                    break
                }
            }
        }
        return arrows
    }
}
