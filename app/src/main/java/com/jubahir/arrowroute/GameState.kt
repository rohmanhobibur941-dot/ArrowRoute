package com.jubahir.arrowroute

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("arrow_route", Context.MODE_PRIVATE)

    /** Last crash / generator error text, shown once on the next launch so it can be reported. */
    var lastCrash: String
        get() = sp.getString("last_crash", "") ?: ""
        set(v) {
            sp.edit().putString("last_crash", v).commit()
        }

    fun saveCrash(text: String) {
        lastCrash = text.take(3500)
    }

    /** Hints left. Every player starts with 3 free hints. */
    var hints: Int
        get() = sp.getInt("hints", 3)
        set(v) {
            sp.edit().putInt("hints", v).apply()
        }

    var level: Int
        get() = sp.getInt("level", 1)
        set(v) {
            sp.edit().putInt("level", v).apply()
        }

    /** 0 = classic (black on beige), 1 = neon (colourful on dark), 2 = candy (glossy 3D) */
    var theme: Int
        get() = sp.getInt("theme", 2)
        set(v) {
            sp.edit().putInt("theme", v).apply()
        }

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) {
            sp.edit().putBoolean("sound", v).apply()
        }

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) {
            sp.edit().putBoolean("vibrate", v).apply()
        }
}

class Fx(ctx: Context, private val prefs: Prefs) {
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 50)
    } catch (e: Exception) {
        null
    }
    private val vib: Vibrator? = ctx.getSystemService(Vibrator::class.java)

    fun pop() {
        if (prefs.sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
        buzz(15)
    }

    fun error() {
        if (prefs.sound) tone?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
        buzz(70)
    }

    fun win() {
        if (prefs.sound) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        buzz(120)
    }

    private fun buzz(ms: Long) {
        if (prefs.vibrate) {
            try {
                vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}

enum class Status { PLAYING, WON, LOST }

class Ghost(val arrow: Arrow, val progress: Float)

/** A blocked arrow that is rushing into the arrow in front of it (shift = how many cells it moved). */
class Bump(val arrowId: Int, val shift: Float)

class GameState(private val prefs: Prefs, private val aspect: Float) {
    var level by mutableIntStateOf(prefs.level)
    var hints by mutableIntStateOf(prefs.hints)

    /** 0 easy, 1 medium, 2 hard, 3 extreme (from the level number). */
    val difficulty: Int get() = LevelGen.difficultyOf(level)
    var hearts by mutableIntStateOf(3)
    var w by mutableIntStateOf(1)
    var h by mutableIntStateOf(1)
    var arrows by mutableStateOf<List<Arrow>>(emptyList())
    var mask by mutableStateOf(BooleanArray(1))
    var shape by mutableStateOf(ShapeType.SQUARE)
    var totalArrows by mutableIntStateOf(1)
    val ghosts = mutableStateMapOf<Int, Ghost>()
    var blockedId by mutableIntStateOf(-1)
    var bump by mutableStateOf<Bump?>(null)
    var hintId by mutableIntStateOf(-1)
    var status by mutableStateOf(Status.PLAYING)
    var gen by mutableIntStateOf(0)
    var theme by mutableIntStateOf(prefs.theme)
    var hintClaimed by mutableStateOf(false)
    var reviveUsed by mutableStateOf(false)
    var stars by mutableIntStateOf(0)
    // arrows that were already shown by a hint in this level (showing one again costs nothing)
    private val hintedIds = HashSet<Int>()

    // arrows that already cost a heart in this level: tapping the same blocked arrow again is free
    private val penalized = HashSet<Int>()

    // The level currently being played (kept so restart / try again replays the same puzzle).
    private var current: Level? = null
    private var currentNo = -1

    // Next level is generated on a background thread while the player is still busy.
    @Volatile private var prefetched: Level? = null
    @Volatile private var prefetchedFor = -1

    init {
        // big levels take a moment to build, so do the first one off the UI thread
        val startNo = level
        Thread {
            val lv = LevelGen.generateSafe(startNo, aspect) { prefs.saveCrash("LEVEL GENERATOR: " + Log.getStackTraceString(it)) }
            Handler(Looper.getMainLooper()).post {
                if (current == null) {
                    current = lv
                    currentNo = level
                    applyLevel(lv)
                    prefetchNext()
                }
            }
        }.start()
    }

    private fun levelFor(no: Int): Level {
        val p = prefetched
        if (p != null && prefetchedFor == no) {
            prefetched = null
            return p
        }
        return LevelGen.generateSafe(no, aspect) { prefs.saveCrash("LEVEL GENERATOR: " + Log.getStackTraceString(it)) }
    }

    private fun prefetchNext() {
        val no = level + 1
        if (prefetchedFor == no) return
        prefetchedFor = no
        prefetched = null
        Thread {
            val lv = LevelGen.generateSafe(no, aspect) { prefs.saveCrash("LEVEL GENERATOR: " + Log.getStackTraceString(it)) }
            if (prefetchedFor == no) prefetched = lv
        }.start()
    }

    private fun applyLevel(lv: Level) {
        w = lv.w
        h = lv.h
        arrows = lv.arrows
        mask = lv.mask
        shape = lv.shape
        totalArrows = maxOf(1, lv.arrows.size)
        hearts = 3
        status = Status.PLAYING
        ghosts.clear()
        blockedId = -1
        bump = null
        hintId = -1
        hintClaimed = false
        reviveUsed = false
        stars = 0
        penalized.clear()
        hintedIds.clear()
        gen++
    }

    fun startLevel() {
        val cur = current
        val lv = if (cur != null && currentNo == level) cur else levelFor(level)
        current = lv
        currentNo = level
        applyLevel(lv)
        prefetchNext()
    }

    /** One more hint (rewarded ad, or the win reward). */
    fun addHint() {
        hints += 1
        prefs.hints = hints
    }

    /** Rewarded ad finished after a win: the player gets 1 hint. Only once per level. */
    fun claimWinHint() {
        if (hintClaimed || status != Status.WON) return
        hintClaimed = true
        addHint()
    }

    /** Rewarded ad finished after a loss: continue the level with one extra life. */
    fun revive() {
        if (reviveUsed || status != Status.LOST) return
        reviveUsed = true
        hearts = 1
        status = Status.PLAYING
    }

    fun nextLevel() {
        level += 1
        prefs.level = level
        startLevel()
    }

    fun setThemeIndex(t: Int) {
        theme = t
        prefs.theme = t
    }

    fun onTap(a: Arrow, scope: CoroutineScope, fx: Fx) {
        if (status != Status.PLAYING) return
        val occ = LevelGen.occupancy(arrows, w)
        if (LevelGen.canExit(a, w, h, occ)) {
            arrows = arrows.filter { it.id != a.id }
            fx.pop()
            val g = gen
            val len = a.cells.size
            val dist = LevelGen.exitDistance(a, w, h)
            val duration = (200 + (dist + len) * 25).coerceIn(250, 900)
            scope.launch {
                animate(0f, 1f, animationSpec = tween(duration)) { v, _ ->
                    if (g == gen) ghosts[a.id] = Ghost(a, v)
                }
                ghosts.remove(a.id)
            }
            if (arrows.isEmpty()) {
                // 3 hearts left = 3 stars, 2 hearts = 2 stars, 1 heart = 1 star
                stars = hearts.coerceIn(1, 3)
                status = Status.WON
                fx.win()
            }
        } else {
            // one heart per arrow: tapping the same blocked arrow again does not cost another one
            if (penalized.add(a.id)) hearts -= 1
            fx.error()
            blockedId = a.id
            if (bump == null) {
                // the arrow dashes (fast) into the arrow in front of it, then goes back to its place
                val k = LevelGen.blockerDistance(a, w, h, occ)
                val travel = maxOf(0.15f, k - 0.7f)
                val g = gen
                scope.launch {
                    animate(0f, travel, animationSpec = tween(110, easing = FastOutSlowInEasing)) { v, _ ->
                        if (g == gen) bump = Bump(a.id, v)
                    }
                    animate(travel, 0f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { v, _ ->
                        if (g == gen) bump = Bump(a.id, v)
                    }
                    if (g == gen) bump = null
                }
            }
            scope.launch {
                delay(750)
                if (blockedId == a.id) blockedId = -1
            }
            if (hearts <= 0) status = Status.LOST
        }
    }

/** True if pressing hint would cost nothing now: every arrow that can leave was already hinted. */
    fun hintIsFree(): Boolean {
        if (status != Status.PLAYING) return false
        val occ = LevelGen.occupancy(arrows, w)
        val free = arrows.filter { LevelGen.canExit(it, w, h, occ) }
        return free.isNotEmpty() && free.all { it.id in hintedIds }
    }

    /**
     * Shows an arrow that can leave right now. An arrow that was not hinted before costs 1 hint and is
     * remembered; if you press hint again (without taking that arrow) another arrow is shown. When
     * every free arrow was already hinted, they are shown again for free.
     */
    fun hint(scope: CoroutineScope) {
        if (status != Status.PLAYING) return
        val occ = LevelGen.occupancy(arrows, w)
        val free = arrows.filter { LevelGen.canExit(it, w, h, occ) }
        if (free.isEmpty()) return
        val fresh = free.filter { it.id !in hintedIds }
        val a: Arrow
        if (fresh.isNotEmpty()) {
            if (hints <= 0) return
            a = fresh.first()
            hints -= 1
            prefs.hints = hints
            hintedIds.add(a.id)
        } else {
            val others = free.filter { it.id != hintId }
            a = if (others.isNotEmpty()) others.first() else free.first()
        }
        hintId = a.id
        scope.launch {
            delay(2600)
            if (hintId == a.id) hintId = -1
        }
    }
}
