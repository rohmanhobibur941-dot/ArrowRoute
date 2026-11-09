package com.jubahir.arrowroute

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

class Level(val w: Int, val h: Int, val arrows: List<Arrow>)

object LevelGen {

    fun sizeFor(level: Int): Pair<Int, Int> {
        val w = minOf(5 + level, 13)
        val h = minOf(7 + level + level / 2, 20)
        return w to h
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

    /** Greedy simulation: removing an arrow only frees space, so order does not matter. */
    private fun stuckArrows(w: Int, h: Int, arrows: List<Arrow>): List<Arrow> {
        val remaining = arrows.toMutableList()
        val occ = occupancy(remaining, w)
        var progress = true
        while (progress) {
            progress = false
            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val a = iter.next()
                if (canExit(a, w, h, occ)) {
                    for (c in a.cells) occ.remove(c.y * w + c.x)
                    iter.remove()
                    progress = true
                }
            }
        }
        return remaining
    }

    private fun buildPaths(w: Int, h: Int, maxLen: Int, rnd: Random): MutableList<Arrow> {
        val used = Array(h) { BooleanArray(w) }
        val order = (0 until w * h).shuffled(rnd)
        val out = mutableListOf<Arrow>()
        var id = 0
        for (k in order) {
            val sx = k % w
            val sy = k / w
            if (used[sy][sx]) continue
            if (rnd.nextFloat() < 0.06f) continue // keep a few empty spots like the original
            val cells = mutableListOf(Cell(sx, sy))
            used[sy][sx] = true
            val target = 2 + rnd.nextInt(maxOf(1, maxLen - 1))
            var dir: Dir? = null
            while (cells.size < target) {
                val cur = cells.last()
                val options = Dir.values().filter { d ->
                    val nx = cur.x + d.dx
                    val ny = cur.y + d.dy
                    nx in 0 until w && ny in 0 until h && !used[ny][nx]
                }
                if (options.isEmpty()) break
                val d = if (dir != null && options.contains(dir) && rnd.nextFloat() < 0.55f) {
                    dir
                } else {
                    options[rnd.nextInt(options.size)]
                }
                dir = d
                val n = Cell(cur.x + d.dx, cur.y + d.dy)
                used[n.y][n.x] = true
                cells.add(n)
            }
            if (cells.size >= 2) {
                val finalCells = if (rnd.nextBoolean()) cells.reversed() else cells.toList()
                out.add(Arrow(id++, finalCells))
            } else {
                used[sy][sx] = false
            }
        }
        return out
    }

    /** Flip the head of stuck arrows until the level is solvable; prune whatever is left. */
    private fun makeSolvable(w: Int, h: Int, input: List<Arrow>, rnd: Random): List<Arrow> {
        val arrows = input.toMutableList()
        for (i in 0 until 400) {
            val stuck = stuckArrows(w, h, arrows)
            if (stuck.isEmpty()) return arrows
            val pick = stuck[rnd.nextInt(stuck.size)]
            val idx = arrows.indexOfFirst { it.id == pick.id }
            arrows[idx] = Arrow(pick.id, pick.cells.reversed())
        }
        val stuckIds = stuckArrows(w, h, arrows).map { it.id }.toSet()
        return arrows.filter { it.id !in stuckIds }
    }

    fun generate(level: Int): Level {
        val (w, h) = sizeFor(level)
        val maxLen = minOf(3 + level, 12)
        val rnd = Random(System.nanoTime())
        var best: List<Arrow> = emptyList()
        for (attempt in 0 until 5) {
            val arrows = makeSolvable(w, h, buildPaths(w, h, maxLen, rnd), rnd)
            if (arrows.size > best.size) best = arrows
            if (arrows.size >= (w * h) / 8) break
        }
        return Level(w, h, best)
    }
}
