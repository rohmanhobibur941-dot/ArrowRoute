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

/** Difficulty knobs. longMin..longMax = length of the long arrows, keepFree = how many arrows are
 *  allowed to be free at the start (the rest are blocked), straight = chance to keep going straight. */
private class Tier(val longMin: Int, val longMax: Int, val keepFree: Int, val straight: Float)

/** Only this share of arrows may be short (2-3 cells); everything else is long. */
private const val SHORT_FRAC = 0.30f

object LevelGen {

    /**
     * Arrow count per level: level 1 = 50, level 2 = 65, level 3 = 80 ... (+15 per level).
     * From level 4 a small deterministic +-8% wobble is added. Capped so late levels stay playable.
     */
    fun arrowTarget(level: Int): Int {
        val base = 50 + 15 * (level - 1)
        val wobble = if (level <= 3) 0.0 else ((level * 7919) % 17 - 8) / 100.0
        return (base * (1.0 + wobble)).toInt().coerceIn(50, 250)
    }

    /** Easy -> Medium -> Hard -> Extreme: longer arrows and fewer free arrows at the start. */
    private fun tierFor(level: Int): Tier = when {
        level <= 4 -> Tier(6, 9, 4, 0.55f)
        level <= 9 -> Tier(7, 11, 4, 0.45f)
        level <= 19 -> Tier(8, 14, 3, 0.40f)
        else -> Tier(10, 18, 3, 0.35f)
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

    /** aspect = board width / board height of the play area, so the grid fills the screen.
     *  Runs for up to ~2.5s on big levels, so call it from a background thread when possible. */
    fun generate(level: Int, aspect: Float): Level {
        val shape = shapeFor(level)
        val tier = tierFor(level)
        val target = arrowTarget(level)
        val avg = SHORT_FRAC * 2.5f + (1f - SHORT_FRAC) * (tier.longMin + tier.longMax) / 2f * 0.8f
        val size = sizeFor(shape, aspect, (target * avg).toInt())
        val w = size.first
        val h = size.second
        val mask = makeMask(shape, w, h)
        val rnd = Random(System.nanoTime())
        val deadline = System.nanoTime() + 2_500_000_000L

        var scale = 1f
        var best: Level? = null
        var bestDiff = Int.MAX_VALUE
        // Every attempt is solvable (checked while building); we retry only to land near the arrow target.
        for (attempt in 0 until 5) {
            val g = Gen(w, h, rnd, tier.straight)
            g.build(mask, tier.longMin, tier.longMax, tier.keepFree, scale)
            g.optimize(tier.keepFree, deadline)
            val list = g.arrows.mapIndexed { i, p -> Arrow(i, p.map { k -> Cell(k % w, k / w) }) }
            val diff = abs(list.size - target)
            if (diff < bestDiff) {
                bestDiff = diff
                best = Level(w, h, mask, shape, list)
            }
            if (diff <= target * 0.06) break
            if (System.nanoTime() > deadline) break
            scale = (scale * list.size / target).coerceIn(0.5f, 2f)
        }
        return checkNotNull(best)
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
        if (q.size < 2) return null
        if (!rayFree(q, occ)) return null
        if (freeAhead(q, free) < 1) return null
        return q
    }

    private fun pickLen(longMin: Int, longMax: Int, scale: Float): Int {
        return if (rnd.nextFloat() < SHORT_FRAC) {
            2 + rnd.nextInt(2)
        } else {
            val t = ((longMin + rnd.nextInt(longMax - longMin + 1)) * scale).roundToInt()
            max(4, t)
        }
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
                        if (p.size >= 2) {
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
