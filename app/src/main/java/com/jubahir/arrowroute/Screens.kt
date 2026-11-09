package com.jubahir.arrowroute

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

enum class Screen { SPLASH, HOME, GAME }

private val SKY = Brush.verticalGradient(listOf(Color(0xFF9B4DE8), Color(0xFF5E7BF0), Color(0xFFFF9EC4)))

@Composable
fun AppRoot(ads: AdManager) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val fx = remember { Fx(ctx, prefs) }
    val gs = remember { GameState(prefs) }
    var screen by remember { mutableStateOf(Screen.SPLASH) }

    BackHandler(enabled = screen == Screen.GAME) { screen = Screen.HOME }

    when (screen) {
        Screen.SPLASH -> SplashScreen { screen = Screen.HOME }
        Screen.HOME -> HomeScreen(gs, prefs) { screen = Screen.GAME }
        Screen.GAME -> GameScreen(gs, prefs, fx, ads) { screen = Screen.HOME }
    }
}

// ------------------------------------------------------------------ splash + home

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1600)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(SKY),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(150.dp)
            )
            Spacer(Modifier.height(16.dp))
            TitleText("ARROW ROUTE", 38.sp, Color(0xFF9BE7FF), Color(0xFF2D8CFF), Color(0xFF1B3C8C))
            Spacer(Modifier.height(44.dp))
            Text("LOADING...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun HomeScreen(gs: GameState, prefs: Prefs, onPlay: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showHow by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(SKY)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            drawCircle(Color.White.copy(alpha = 0.08f), radius = w * 0.55f, center = Offset(w * 0.95f, h * 0.12f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = w * 0.45f, center = Offset(w * 0.05f, h * 0.62f))
            drawCircle(Color.White.copy(alpha = 0.07f), radius = w * 0.35f, center = Offset(w * 0.85f, h * 0.9f))
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CoinPill(gs.coins)
            }
            Spacer(Modifier.height(8.dp))
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(118.dp)
            )
            Spacer(Modifier.height(4.dp))
            TitleText("ARROW", 56.sp, Color(0xFF9BE7FF), Color(0xFF2D8CFF), Color(0xFF1B3C8C))
            TitleText("ROUTE", 56.sp, Color(0xFFFFE27A), Color(0xFFFF8A1E), Color(0xFF7A3B0A))
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BtnStyle.BLUE.base)
                    .border(2.dp, Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 4.dp)
            ) {
                Text("GRID RUSH", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.weight(1f))

            GlossyButton("PLAY  \u2022  LEVEL ${gs.level}", BtnStyle.GREEN, Modifier.width(290.dp), 22.sp) { onPlay() }
            Spacer(Modifier.height(14.dp))
            GlossyButton("THEME", BtnStyle.BLUE, Modifier.width(290.dp), 20.sp) { showTheme = true }
            Spacer(Modifier.height(14.dp))
            GlossyButton("HOW TO PLAY", BtnStyle.ORANGE, Modifier.width(290.dp), 20.sp) { showHow = true }

            Spacer(Modifier.weight(0.6f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RoundIconButton(Ico.GEAR, BtnStyle.BLUE, 54.dp) { showSettings = true }
            }
        }
    }

    if (showSettings) SettingsDialog(prefs, { showSettings = false }, null)
    if (showTheme) ThemeDialog(gs) { showTheme = false }
    if (showHow) HowToDialog { showHow = false }
}

// ------------------------------------------------------------------ game screen

@Composable
fun GameScreen(gs: GameState, prefs: Prefs, fx: Fx, ads: AdManager, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }

    LaunchedEffect(gs.gen) { zoom = 1f }

    fun watchAd(onReward: () -> Unit) {
        ads.showRewarded(onReward) {
            Toast.makeText(ctx, "Ad not ready, try again in a moment", Toast.LENGTH_SHORT).show()
        }
    }

    val cleared = gs.totalArrows - gs.arrows.size
    val fraction = cleared.toFloat() / gs.totalArrows.toFloat()

    Column(
        Modifier
            .fillMaxSize()
            .background(Pal.ScreenBg)
    ) {
        // header
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .background(Brush.verticalGradient(listOf(Pal.HeaderTop, Pal.HeaderBottom)))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Ico.HOME, BtnStyle.GREEN, 44.dp) { onHome() }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Ico.RESTART, BtnStyle.GREEN, 44.dp) { gs.startLevel() }
                Spacer(Modifier.weight(1f))
                CoinPill(gs.coins)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("LEVEL ${gs.level}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                for (i in 1..3) {
                    GameIcon(if (i <= gs.hearts) Ico.HEART else Ico.HEART_OFF, 26.dp)
                    if (i < 3) Spacer(Modifier.width(4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(fraction, Modifier.fillMaxWidth())
        }

        GameBoard(
            gs = gs,
            zoom = zoom,
            onZoom = { zoom = it },
            fx = fx,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // bottom bar
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Pal.BarBg)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Ico.PALETTE, BtnStyle.BLUE, 46.dp) { showTheme = true }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Ico.GEAR, BtnStyle.ORANGE, 46.dp) { showSettings = true }
            Spacer(Modifier.width(8.dp))
            ZoomBar(zoom, { zoom = it }, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Ico.BULB, BtnStyle.GREEN, 46.dp) { gs.hint(scope) }
        }

        // banner ad (Google test ad)
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(Pal.BarBg),
            contentAlignment = Alignment.Center
        ) {
            BannerAd()
        }
    }

    if (showSettings) {
        SettingsDialog(prefs, { showSettings = false }) {
            showSettings = false
            gs.startLevel()
        }
    }
    if (showTheme) ThemeDialog(gs) { showTheme = false }

    if (gs.status == Status.WON) {
        ResultDialog(true) {
            GameIcon(Ico.COIN, 58.dp)
            Spacer(Modifier.height(8.dp))
            val shown = if (gs.doubled) gs.bonus * 2 else gs.bonus
            Text(
                "Bonus +$shown coins",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF3F6B1F)
            )
            if (!gs.doubled) {
                Spacer(Modifier.height(14.dp))
                GlossyButton("2X COINS  (WATCH AD)", BtnStyle.ORANGE, Modifier.fillMaxWidth(), 16.sp) {
                    watchAd { gs.doubleBonus() }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlossyButton("NEXT LEVEL", BtnStyle.GREEN, Modifier.fillMaxWidth(), 18.sp) {
                val lv = gs.level
                if (lv % 3 == 0) {
                    ads.showInterstitial { gs.nextLevel() }
                } else {
                    gs.nextLevel()
                }
            }
        }
    }

    if (gs.status == Status.LOST) {
        ResultDialog(false) {
            GameIcon(Ico.HEART_OFF, 58.dp)
            if (!gs.reviveUsed) {
                Spacer(Modifier.height(14.dp))
                GlossyButton("EXTRA LIFE  (WATCH AD)", BtnStyle.ORANGE, Modifier.fillMaxWidth(), 16.sp) {
                    watchAd { gs.revive() }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlossyButton("TRY AGAIN", BtnStyle.GREEN, Modifier.fillMaxWidth(), 18.sp) { gs.startLevel() }
        }
    }
}

@Composable
fun ZoomBar(zoom: Float, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF2A2170))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "\u2212",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { onZoom((zoom - 0.25f).coerceAtLeast(1f)) }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        Slider(
            value = zoom,
            onValueChange = onZoom,
            valueRange = 1f..3f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFA03A),
                activeTrackColor = Color(0xFF4FC3F7),
                inactiveTrackColor = Color(0xFF5A4A9A)
            )
        )
        Text(
            "+",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { onZoom((zoom + 0.25f).coerceAtMost(3f)) }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Color(0xFFB8741A),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f)
        )
        OnOffToggle(checked, onChange)
    }
}

@Composable
fun SettingsDialog(prefs: Prefs, onClose: () -> Unit, onRestart: (() -> Unit)?) {
    var sound by remember { mutableStateOf(prefs.sound) }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
    GamePanel("Settings", onClose = onClose) {
        SettingRow("Sound", sound) {
            sound = it
            prefs.sound = it
        }
        Spacer(Modifier.height(14.dp))
        SettingRow("Vibration", vibrate) {
            vibrate = it
            prefs.vibrate = it
        }
        if (onRestart != null) {
            Spacer(Modifier.height(20.dp))
            GlossyButton("RESTART LEVEL", BtnStyle.ORANGE, Modifier.fillMaxWidth(), 16.sp) { onRestart() }
        }
    }
}

@Composable
fun ThemePreview(theme: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Canvas(
            Modifier
                .size(76.dp)
                .clip(shape)
                .background(Themes.boardBg(theme))
                .border(
                    if (selected) 4.dp else 1.dp,
                    if (selected) Color(0xFF3FBF2C) else Color(0xFFB98B4A),
                    shape
                )
        ) {
            val w = this.size.width
            val sw = Themes.arrowWidth(theme, w / 4f)
            drawArrow(
                listOf(Offset(w * 0.2f, w * 0.3f), Offset(w * 0.68f, w * 0.3f)),
                Themes.arrowColor(theme, 0), sw, 1f, theme
            )
            drawArrow(
                listOf(Offset(w * 0.2f, w * 0.52f), Offset(w * 0.2f, w * 0.75f), Offset(w * 0.62f, w * 0.75f)),
                Themes.arrowColor(theme, 1), sw, 1f, theme
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pal.Ink)
    }
}

@Composable
fun ThemeDialog(gs: GameState, onClose: () -> Unit) {
    GamePanel("Theme", onClose = onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ThemePreview(Themes.CLASSIC, "Classic", gs.theme == Themes.CLASSIC) { gs.setThemeIndex(Themes.CLASSIC) }
            ThemePreview(Themes.NEON, "Neon", gs.theme == Themes.NEON) { gs.setThemeIndex(Themes.NEON) }
            ThemePreview(Themes.CANDY, "Candy", gs.theme == Themes.CANDY) { gs.setThemeIndex(Themes.CANDY) }
        }
    }
}

@Composable
fun HowToDialog(onClose: () -> Unit) {
    GamePanel("How to play", onClose = onClose, ribbon = BtnStyle.ORANGE) {
        val lines = listOf(
            "Tap an arrow to slide it out.",
            "The path in front of its head must be clear.",
            "Blocked? You lose a heart. You get 3 hearts per level.",
            "Clear every arrow to win the level.",
            "Pinch with two fingers to zoom, drag to move the board."
        )
        for (l in lines) {
            Text(
                l,
                color = Pal.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ResultDialog(win: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ResultBanner(win)
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Pal.Cream)
                    .border(4.dp, BtnStyle.GREEN.base, RoundedCornerShape(26.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
        }
    }
}
