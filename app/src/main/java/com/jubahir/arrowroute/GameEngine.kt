package com.jubahir.arrowroute

import kotlin.math.abs
import kotlin.math.hypot
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
enum class ShapeType { RECT, CIRCLE, DIAMOND, HEART, PLUS, RING }

class Level(
    val w: Int,
    val h: Int,
    val mask: BooleanArray,
    val shape: ShapeType,
    val arrows: List<Arrow>
)

object LevelGen {

    fun sizeFor(level: Int): Pair<Int, Int> {
        val w = minOf(7 + level, 14)
        val h = minOf(10 + (level * 3) / 2, 21)
        return w to h
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
                    ShapeType.RECT -> !(abs(dx) > 0.8 && abs(dy) > 0.8)
                    ShapeType.CIRCLE -> dx * dx + dy * dy <= 1.0
                    ShapeType.DIAMOND -> abs(dx) + abs(dy) <= 1.0
                    ShapeType.HEART -> {
                        val u = dx * 1.2
                        val v = -dy * 1.15 + 0.1
                        val a = u * u + v * v - 1.0
                        a * a * a - u * u * v * v * v <= 0.0
                    }
                    ShapeType.PLUS -> abs(dx) <= 0.40 || abs(dy) <= 0.40
                    ShapeType.RING -> {
                        val r2 = dx * dx + dy * dy
                        r2 <= 1.0 && r2 >= 0.2
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

    fun generate(level: Int): Level {
        val shape = shapeFor(level)
        val size = sizeFor(level)
        val w = size.first
        val h = size.second
        val mask = makeMask(shape, w, h)
        val g = Gen(w, h, Random(System.nanoTime()))
        val paths = g.build(mask, minOf(5 + level, 16))
        val arrows = paths.mapIndexed { i, p -> Arrow(i, p.map { k -> Cell(k % w, k / w) }) }
        return Level(w, h, mask, shape, arrows)
    }
}

/**
 * Level builder. Arrows are placed from the middle outwards and each new arrow's exit ray must
 * not touch anything placed before it, so every level is solvable by construction. Leftover
 * single cells are merged into neighbours (verified by simulation) so the board stays dense.
 */
private class Gen(val w: Int, val h: Int, val rnd: Random) {

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

    private fun rayFree(p: List<Int>, occ: Set<Int>): Boolean {
        val d = dirOf(p)
        val head = p[p.size - 1]
        var x = px(head) + d.dx
        var y = py(head) + d.dy
        while (x in 0 until w && y in 0 until h) {
            val k = y * w + x
            if (occ.contains(k) || p.contains(k)) return false
            x += d.dx
            y += d.dy
        }
        return true
    }

    /** Greedy removal simulation. Returns how many arrows can never leave (0 = solvable). */
    private fun stuckCount(arrows: List<List<Int>>): Int {
        val remaining = ArrayList<List<Int>>(arrows)
        val occ = HashSet<Int>()
        for (a in remaining) occ.addAll(a)
        var progress = true
        while (progress) {
            progress = false
            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val a = iter.next()
                if (rayFree(a, occ)) {
                    occ.removeAll(a.toSet())
                    iter.remove()
                    progress = true
                }
            }
        }
        return remaining.size
    }

    private fun grow(start: Int, free: Set<Int>, target: Int): MutableList<Int> {
        val cells = mutableListOf(start)
        val seen = HashSet<Int>()
        seen.add(start)
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
                    if (free.contains(k) && !seen.contains(k)) opts.add(dd)
                }
            }
            if (opts.isEmpty()) break
            val prev = d
            val nd = if (prev != null && opts.contains(prev) && rnd.nextFloat() < 0.45f) {
                prev
            } else {
                opts[rnd.nextInt(opts.size)]
            }
            d = nd
            val n = (cy + nd.dy) * w + (cx + nd.dx)
            cells.add(n)
            seen.add(n)
        }
        return cells
    }

    fun build(mask: BooleanArray, maxLen: Int): List<List<Int>> {
        val free = HashSet<Int>()
        for (k in 0 until w * h) if (mask[k]) free.add(k)

        val cx = (w - 1) / 2.0
        val cy = (h - 1) / 2.0
        val maxd = hypot(cx, cy)
        val order = free
            .map { k -> Pair(k, hypot(px(k) - cx, py(k) - cy) + rnd.nextDouble() * maxd * 0.5) }
            .sortedBy { it.second }
            .map { it.first }

        var arrows: MutableList<List<Int>> = mutableListOf()
        val occ = HashSet<Int>()

        for (c in order) {
            if (!free.contains(c)) continue
            var placed = false
            var attempt = 0
            while (attempt < 8 && !placed) {
                val target = if (attempt >= 5) 2 else 2 + rnd.nextInt(maxOf(1, maxLen - 1))
                val p = grow(c, free, target)
                if (p.size < 2) break
                val rev = p.reversed()
                val orients = if (rnd.nextBoolean()) listOf<List<Int>>(p, rev) else listOf<List<Int>>(rev, p)
                for (q in orients) {
                    if (rayFree(q, occ)) {
                        arrows.add(q)
                        occ.addAll(q)
                        free.removeAll(q.toSet())
                        placed = true
                        break
                    }
                }
                attempt++
            }
        }

        // Repair: merge leftover cells into neighbours so there are no big empty holes.
        val leftover = order.filter { free.contains(it) }
        for (c in leftover) {
            if (!free.contains(c)) continue
            var done = false

            var i = 0
            while (i < arrows.size && !done) {
                val a = arrows[i]
                for (endIdx in 0..1) {
                    val e = if (endIdx == 0) a[0] else a[a.size - 1]
                    if (abs(px(e) - px(c)) + abs(py(e) - py(c)) == 1) {
                        val na: List<Int> = if (endIdx == 0) listOf(c) + a else a + c
                        val trial = ArrayList<List<Int>>(arrows)
                        trial[i] = na
                        if (stuckCount(trial) == 0) {
                            arrows = trial
                            free.remove(c)
                            done = true
                            break
                        }
                    }
                }
                i++
            }
            if (done) continue

            for (dd in Dir.values()) {
                val nx = px(c) + dd.dx
                val ny = py(c) + dd.dy
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                val n = ny * w + nx
                if (!free.contains(n)) continue
                val options = listOf<List<Int>>(listOf(c, n), listOf(n, c))
                for (q in options) {
                    val trial = ArrayList<List<Int>>(arrows)
                    trial.add(q)
                    if (stuckCount(trial) == 0) {
                        arrows = trial
                        free.remove(c)
                        free.remove(n)
                        done = true
                        break
                    }
                }
                if (done) break
            }
        }
        return arrows
    }
}
