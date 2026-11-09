package com.jubahir.arrowroute

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
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

    var coins: Int
        get() = sp.getInt("coins", 0)
        set(v) {
            sp.edit().putInt("coins", v).apply()
        }

    var level: Int
        get() = sp.getInt("level", 1)
        set(v) {
            sp.edit().putInt("level", v).apply()
        }

    /** 0 = classic (black on beige), 1 = neon (colourful on dark) */
    var theme: Int
        get() = sp.getInt("theme", 0)
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

class GameState(private val prefs: Prefs) {
    var level by mutableIntStateOf(prefs.level)
    var coins by mutableIntStateOf(prefs.coins)
    var hearts by mutableIntStateOf(3)
    var w by mutableIntStateOf(1)
    var h by mutableIntStateOf(1)
    var arrows by mutableStateOf<List<Arrow>>(emptyList())
    val ghosts = mutableStateMapOf<Int, Ghost>()
    var blockedId by mutableIntStateOf(-1)
    var hintId by mutableIntStateOf(-1)
    var status by mutableStateOf(Status.PLAYING)
    var gen by mutableIntStateOf(0)
    var theme by mutableIntStateOf(prefs.theme)
    var bonus by mutableIntStateOf(0)
    var doubled by mutableStateOf(false)
    var reviveUsed by mutableStateOf(false)

    init {
        startLevel()
    }

    fun startLevel() {
        val lv = LevelGen.generate(level)
        w = lv.w
        h = lv.h
        arrows = lv.arrows
        hearts = 3
        status = Status.PLAYING
        ghosts.clear()
        blockedId = -1
        hintId = -1
        doubled = false
        reviveUsed = false
        gen++
    }

    /** Rewarded ad finished after a win: give the level bonus a second time (2x). */
    fun doubleBonus() {
        if (doubled || status != Status.WON) return
        doubled = true
        coins += bonus
        prefs.coins = coins
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
            coins += 2
            prefs.coins = coins
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
                bonus = 10 + level * 2
                coins += bonus
                prefs.coins = coins
                status = Status.WON
                fx.win()
            }
        } else {
            hearts -= 1
            fx.error()
            blockedId = a.id
            scope.launch {
                delay(600)
                if (blockedId == a.id) blockedId = -1
            }
            if (hearts <= 0) status = Status.LOST
        }
    }

    fun hint(scope: CoroutineScope) {
        if (status != Status.PLAYING) return
        val occ = LevelGen.occupancy(arrows, w)
        val a = arrows.firstOrNull { LevelGen.canExit(it, w, h, occ) } ?: return
        hintId = a.id
        scope.launch {
            delay(1500)
            if (hintId == a.id) hintId = -1
        }
    }
}
