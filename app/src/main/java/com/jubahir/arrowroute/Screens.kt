package com.jubahir.arrowroute

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.min

enum class Screen { SPLASH, HOME, GAME }

@Composable
fun AppRoot(ads: AdManager) {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val prefs = remember { Prefs(ctx) }
    val fx = remember { Fx(ctx, prefs) }
    val gs = remember {
        // play area = screen minus header and bottom bar; used to shape the grid so it fills the screen
        val boardH = (cfg.screenHeightDp - 170f).coerceAtLeast(300f)
        GameState(prefs, (cfg.screenWidthDp / boardH).coerceIn(0.45f, 1.0f))
    }
    var screen by remember { mutableStateOf(Screen.SPLASH) }

    BackHandler(enabled = screen == Screen.GAME) { screen = Screen.HOME }

    when (screen) {
        Screen.SPLASH -> SplashScreen { screen = Screen.HOME }
        Screen.HOME -> HomeScreen(gs, prefs) { screen = Screen.GAME }
        Screen.GAME -> GameScreen(gs, prefs, fx, ads) { screen = Screen.HOME }
    }
}

// ------------------------------------------------------------------ splash + home

/** An image used as a button: press-shrink animation, optional heartbeat, overlay for live content. */
@Composable
fun HomeImageButton(
    res: Int,
    widthFraction: Float,
    pulse: Boolean = false,
    onClick: () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")
    val inf = rememberInfiniteTransition(label = "beat")
    val beat by inf.animateFloat(
        initialValue = 1f,
        targetValue = if (pulse) 1.035f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "beat"
    )
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .graphicsLayer {
                scaleX = press * beat
                scaleY = press * beat
            }
            .clickable(interactionSource = src, indication = null) { onClick() }
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
        overlay()
    }
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onDone()
    }
    // the splash PNG is padded to 9:20, so Crop only trims a sliver on most phones
    Image(
        painter = painterResource(R.drawable.splash_bg),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun HomeScreen(gs: GameState, prefs: Prefs, onPlay: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showHow by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        // positions come from the reference design (1449 x 2576); k shrinks things on shorter screens
        val k = min(1f, (h / w) / (2576f / 1449f))

        Image(
            painter = painterResource(R.drawable.bg_home),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // coins (top right)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = w * 0.06f)
                .width(w * 0.2f)
        ) {
            Image(
                painter = painterResource(R.drawable.home_coin),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            val txt = "${gs.coins}"
            val digitH: Dp = when {
                txt.length >= 6 -> 14.dp
                txt.length == 5 -> 17.dp
                txt.length == 4 -> 20.dp
                else -> 22.dp
            }
            NumberImage(txt, DigitStyle.W, digitH, Modifier.align(BiasAlignment(0.3f, 0f)))
        }

        // logo
        Image(
            painter = painterResource(R.drawable.home_logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * 0.138f)
                .width(w * 0.62f * k),
            contentScale = ContentScale.FillWidth
        )

        // big play button
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * 0.378f)
                .width(w * 0.34f * k)
        ) {
            HomeImageButton(R.drawable.home_play, 1f, pulse = true, onClick = onPlay)
        }

        // current level, small, under the play button
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * 0.566f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabelImage(R.drawable.lbl_level, 16.dp)
            Spacer(Modifier.width(6.dp))
            NumberImage("${gs.level}", DigitStyle.W, 18.dp)
        }

        // theme + how to play
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * 0.592f)
                .width(w * 0.53f * k)
        ) {
            HomeImageButton(R.drawable.home_theme, 1f, onClick = { showTheme = true })
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * 0.706f)
                .width(w * 0.61f * k)
        ) {
            HomeImageButton(R.drawable.home_how, 1f, onClick = { showHow = true })
        }

        // settings (bottom right)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = h * 0.90f)
                .padding(end = w * 0.083f)
                .width(w * 0.125f)
        ) {
            HomeImageButton(R.drawable.home_gear, 1f, onClick = { showSettings = true })
        }
    }

    if (showSettings) SettingsDialog(prefs, { showSettings = false }, BtnStyle.GREEN, null)
    if (showTheme) ThemeDialog(gs, BtnStyle.GREEN) { showTheme = false }
    if (showHow) HowToDialog(BtnStyle.GREEN) { showHow = false }
}

// ------------------------------------------------------------------ game screen

private val BAR_DARK = Color(0xE61E1004)
private val EDGE_LINE = Color(0xFF8A5A2B)

@Composable
fun GameScreen(gs: GameState, prefs: Prefs, fx: Fx, ads: AdManager, onHome: () -> Unit) {
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var adMsg by remember { mutableStateOf(false) }

    LaunchedEffect(gs.gen) { zoom = 1f }
    LaunchedEffect(adMsg) {
        if (adMsg) {
            delay(1800)
            adMsg = false
        }
    }

    fun watchAd(onReward: () -> Unit) {
        ads.showRewarded(onReward) { adMsg = true }
    }

    val cleared = gs.totalArrows - gs.arrows.size
    val fraction = cleared.toFloat() / gs.totalArrows.toFloat()

    Box(Modifier.fillMaxSize()) {
        // wood background for the whole game screen
        Image(
            painter = painterResource(R.drawable.bg_wood),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(Modifier.fillMaxSize()) {
            // header (square corners)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xF01E1004), Color(0xB32B1808))))
                    .statusBarsPadding()
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
                    LabelImage(R.drawable.lbl_level, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    NumberImage("${gs.level}", DigitStyle.W, 24.dp)
                    Spacer(Modifier.weight(1f))
                    for (i in 1..3) {
                        GameIcon(if (i <= gs.hearts) Ico.HEART else Ico.HEART_OFF, 26.dp)
                        if (i < 3) Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(fraction, Modifier.fillMaxWidth())
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(EDGE_LINE)
            )

            GameBoard(
                gs = gs,
                zoom = zoom,
                onZoom = { zoom = it },
                fx = fx,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(EDGE_LINE)
            )
            // bottom bar (square corners, no banner ad under it)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(BAR_DARK)
                    .navigationBarsPadding()
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
        }

        if (adMsg) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 90.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC1E1004))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                LabelImage(R.drawable.lbl_ad_not_ready, 18.dp)
            }
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
        ResultDialog(true, gs.stars) {
            GameIcon(Ico.COIN, 58.dp)
            Spacer(Modifier.height(8.dp))
            val shown = if (gs.doubled) gs.bonus * 2 else gs.bonus
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelImage(R.drawable.lbl_bonus, 26.dp)
                Spacer(Modifier.width(6.dp))
                NumberImage("+$shown", DigitStyle.G, 28.dp)
                Spacer(Modifier.width(6.dp))
                LabelImage(R.drawable.lbl_coins, 26.dp)
            }
            if (!gs.doubled) {
                Spacer(Modifier.height(14.dp))
                GlossyButton(R.drawable.lbl_2x_coins, BtnStyle.ORANGE, Modifier.fillMaxWidth(), 40.dp) {
                    watchAd { gs.doubleBonus() }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlossyButton(R.drawable.lbl_next_level, BtnStyle.GREEN, Modifier.fillMaxWidth(), 28.dp) {
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
        ResultDialog(false, 0) {
            GameIcon(Ico.HEART_OFF, 58.dp)
            if (!gs.reviveUsed) {
                Spacer(Modifier.height(14.dp))
                GlossyButton(R.drawable.lbl_extra_life, BtnStyle.ORANGE, Modifier.fillMaxWidth(), 40.dp) {
                    watchAd { gs.revive() }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlossyButton(R.drawable.lbl_try_again, BtnStyle.GREEN, Modifier.fillMaxWidth(), 28.dp) { gs.startLevel() }
        }
    }
}

@Composable
private fun ZoomSign(plus: Boolean, onClick: () -> Unit) {
    Canvas(
        Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .size(20.dp)
    ) {
        val w = this.size.width
        val sw = w * 0.2f
        drawLine(Color.White, Offset(w * 0.1f, w / 2f), Offset(w * 0.9f, w / 2f), strokeWidth = sw, cap = StrokeCap.Round)
        if (plus) {
            drawLine(Color.White, Offset(w / 2f, w * 0.1f), Offset(w / 2f, w * 0.9f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun ZoomBar(zoom: Float, onZoom: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF3A230F))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ZoomSign(false) { onZoom((zoom - 0.25f).coerceAtLeast(1f)) }
        Slider(
            value = zoom,
            onValueChange = onZoom,
            valueRange = 1f..4f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFA03A),
                activeTrackColor = Color(0xFFFFD27A),
                inactiveTrackColor = Color(0xFF7A5630)
            )
        )
        ZoomSign(true) { onZoom((zoom + 0.25f).coerceAtMost(4f)) }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
fun SettingRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LabelImage(label, 26.dp)
        Spacer(Modifier.weight(1f))
        OnOffToggle(checked, onChange)
    }
}

@Composable
fun SettingsDialog(prefs: Prefs, onClose: () -> Unit, ribbon: BtnStyle = BtnStyle.BLUE, onRestart: (() -> Unit)?) {
    var sound by remember { mutableStateOf(prefs.sound) }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
    GamePanel(R.drawable.lbl_settings, onClose = onClose, ribbon = ribbon) {
        SettingRow(R.drawable.lbl_sound, sound) {
            sound = it
            prefs.sound = it
        }
        Spacer(Modifier.height(14.dp))
        SettingRow(R.drawable.lbl_vibration, vibrate) {
            vibrate = it
            prefs.vibrate = it
        }
        if (onRestart != null) {
            Spacer(Modifier.height(20.dp))
            GlossyButton(R.drawable.lbl_restart, BtnStyle.ORANGE, Modifier.fillMaxWidth(), 26.dp) { onRestart() }
        }
    }
}

@Composable
fun ThemePreview(theme: Int, nameRes: Int, selected: Boolean, onClick: () -> Unit) {
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
        LabelImage(nameRes, 16.dp)
    }
}

@Composable
fun ThemeDialog(gs: GameState, ribbon: BtnStyle = BtnStyle.BLUE, onClose: () -> Unit) {
    GamePanel(R.drawable.lbl_theme, onClose = onClose, ribbon = ribbon) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ThemePreview(Themes.CLASSIC, R.drawable.lbl_classic, gs.theme == Themes.CLASSIC) { gs.setThemeIndex(Themes.CLASSIC) }
            ThemePreview(Themes.NEON, R.drawable.lbl_neon, gs.theme == Themes.NEON) { gs.setThemeIndex(Themes.NEON) }
            ThemePreview(Themes.CANDY, R.drawable.lbl_candy, gs.theme == Themes.CANDY) { gs.setThemeIndex(Themes.CANDY) }
        }
    }
}

@Composable
fun HowToDialog(ribbon: BtnStyle = BtnStyle.ORANGE, onClose: () -> Unit) {
    GamePanel(R.drawable.lbl_howto, onClose = onClose, ribbon = ribbon) {
        // (image, height): one-line images are 20dp tall, two-line images 40dp, so the text size matches
        val lines = listOf(
            Pair(R.drawable.lbl_how_1, 20.dp),
            Pair(R.drawable.lbl_how_2, 40.dp),
            Pair(R.drawable.lbl_how_3, 40.dp),
            Pair(R.drawable.lbl_how_4, 20.dp),
            Pair(R.drawable.lbl_how_5, 40.dp)
        )
        for (l in lines) {
            LabelImage(l.first, l.second, Modifier.padding(vertical = 5.dp))
        }
    }
}

@Composable
fun ResultDialog(win: Boolean, stars: Int, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ResultBanner(win, stars)
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
