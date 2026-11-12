package cz.mares.doompm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.mares.doompm.DoomRenderer.render3DView
import cz.mares.doompm.DoomRenderer.renderAutomap
import cz.mares.doompm.DoomRenderer.renderDoomguyFace
import cz.mares.doompm.DoomRenderer.renderTitleBanner
import kotlinx.coroutines.delay

enum class AppScreen {
    MENU,
    PLAYING,
    LEADERBOARD,
    GUIDE
}

enum class AimSensitivity(val id: String, val labelCz: String, val mult: Float) {
    LOW("low", "Nízká", 0.68f),
    MEDIUM("med", "Střední", 1.0f),
    HIGH("high", "Vysoká", 1.48f);

    companion object {
        fun fromId(id: String): AimSensitivity = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

@Composable
fun App() {
    val baseDensity = LocalDensity.current
    val fixedDensity = remember(baseDensity.density) {
        Density(density = baseDensity.density, fontScale = 1f)
    }

    MaterialTheme {
        CompositionLocalProvider(LocalDensity provides fixedDensity) {
            val engine = remember { DoomEngine() }
            var currentScreen by remember { mutableStateOf(AppScreen.MENU) }
            var selectedDifficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
            var selectedStartMap by remember { mutableIntStateOf(0) }

            var savedNick by remember { mutableStateOf(FirebaseLeaderboard.getSavedNick()) }
            var sensitivity by remember {
                mutableStateOf(AimSensitivity.fromId(PlatformServices.loadString("doom_turn_sens", "med")))
            }

            var frameTick by remember { mutableIntStateOf(0) }
            var menuFireNowMs by remember { mutableLongStateOf(PlatformServices.currentTimeMillis()) }
            var showAutomap by remember { mutableStateOf(false) }
            var showCheatDialog by remember { mutableStateOf(false) }

            var holdFwd by remember { mutableStateOf(false) }
            var holdBack by remember { mutableStateOf(false) }
            var holdStrafeL by remember { mutableStateOf(false) }
            var holdStrafeR by remember { mutableStateOf(false) }
            var holdTurnL by remember { mutableStateOf(false) }
            var holdTurnR by remember { mutableStateOf(false) }
            var dragTurnAccum by remember { mutableFloatStateOf(0f) }
            var isFiringHeld by remember { mutableStateOf(false) }

            var soundOn by remember { mutableStateOf(true) }
            var musicOn by remember { mutableStateOf(true) }

            fun clearHoldInputs() {
                holdFwd = false
                holdBack = false
                holdStrafeL = false
                holdStrafeR = false
                holdTurnL = false
                holdTurnR = false
                dragTurnAccum = 0f
                isFiringHeld = false
            }

            // Title screen DOOM fire animation ticker
            LaunchedEffect(currentScreen) {
                while (currentScreen == AppScreen.MENU) {
                    menuFireNowMs = PlatformServices.currentTimeMillis()
                    delay(38L)
                }
            }

            // 60 FPS Game Loop
            LaunchedEffect(currentScreen) {
                var lastNanos = 0L
                while (currentScreen == AppScreen.PLAYING) {
                    withFrameNanos { nowNanos ->
                        val dt = if (lastNanos == 0L) 0.016f else ((nowNanos - lastNanos) / 1_000_000_000f)
                        lastNanos = nowNanos

                        val moveFwd = (if (holdFwd) 1f else 0f) - (if (holdBack) 1f else 0f)
                        val moveStrafe = (if (holdStrafeR) 1f else 0f) - (if (holdStrafeL) 1f else 0f)
                        val turnDir = (if (holdTurnR) 1f else 0f) - (if (holdTurnL) 1f else 0f)
                        val turnDelta = turnDir * dt * 2.45f * sensitivity.mult + dragTurnAccum
                        dragTurnAccum = 0f

                        engine.update(
                            dt = dt,
                            moveForward = moveFwd,
                            moveStrafe = moveStrafe,
                            turnDelta = turnDelta,
                            isFiring = isFiringHeld
                        )
                        DoomAudioManager.updateMusic(
                            nowMs = PlatformServices.currentTimeMillis(),
                            isPlayingGame = (engine.completedLevelStats == null && !engine.isPlayerDead)
                        )
                        frameTick++
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0C0E))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                color = Color(0xFF0B0C0E)
            ) {
                when (currentScreen) {
                    AppScreen.MENU -> {
                        MainMenuScreen(
                            fireNowMs = menuFireNowMs,
                            savedNick = savedNick,
                            onNickSaved = { savedNick = it },
                            selectedDifficulty = selectedDifficulty,
                            onSelectDifficulty = { selectedDifficulty = it },
                            selectedStartMap = selectedStartMap,
                            onSelectStartMap = { selectedStartMap = it },
                            sensitivity = sensitivity,
                            onCycleSensitivity = {
                                val next = AimSensitivity.entries[(sensitivity.ordinal + 1) % AimSensitivity.entries.size]
                                sensitivity = next
                                PlatformServices.saveString("doom_turn_sens", next.id)
                            },
                            soundOn = soundOn,
                            musicOn = musicOn,
                            onToggleSound = {
                                soundOn = !soundOn
                                DoomAudioManager.soundEnabled = soundOn
                            },
                            onToggleMusic = {
                                musicOn = !musicOn
                                DoomAudioManager.musicEnabled = musicOn
                            },
                            onStartGame = {
                                clearHoldInputs()
                                engine.startNewCampaign(selectedDifficulty, selectedStartMap)
                                showAutomap = false
                                currentScreen = AppScreen.PLAYING
                            },
                            onOpenLeaderboard = { currentScreen = AppScreen.LEADERBOARD },
                            onOpenGuide = { currentScreen = AppScreen.GUIDE }
                        )
                    }

                    AppScreen.PLAYING -> {
                        val summary = engine.completedLevelStats
                        if (summary != null) {
                            LaunchedEffect(summary) {
                                clearHoldInputs()
                                FirebaseLeaderboard.syncSavedPlayerRecordIfAny(
                                    difficultyId = engine.difficulty.id,
                                    score = summary.totalScore,
                                    timeSeconds = summary.timeSeconds
                                )
                            }
                            IntermissionScreen(
                                stats = summary,
                                difficulty = engine.difficulty,
                                savedNick = savedNick,
                                onNickSaved = { savedNick = it },
                                onNextLevel = {
                                    if (summary.isEpisodeCompleted) {
                                        currentScreen = AppScreen.LEADERBOARD
                                    } else {
                                        engine.loadMap(summary.nextMapIndex, resetInventory = false)
                                    }
                                },
                                onBackToMenu = {
                                    clearHoldInputs()
                                    currentScreen = AppScreen.MENU
                                }
                            )
                        } else {
                            // Auto-save score (not incomplete time) if player dies with points
                            LaunchedEffect(engine.isPlayerDead) {
                                if (engine.isPlayerDead && engine.totalScore > 0) {
                                    FirebaseLeaderboard.syncSavedPlayerRecordIfAny(
                                        difficultyId = engine.difficulty.id,
                                        score = engine.totalScore,
                                        timeSeconds = 0
                                    )
                                }
                            }

                            GamePlayScreen(
                                engine = engine,
                                frameTick = frameTick,
                                sensitivityMult = sensitivity.mult,
                                savedNick = savedNick,
                                onNickSaved = { savedNick = it },
                                showAutomap = showAutomap,
                                onToggleAutomap = { showAutomap = !showAutomap },
                                onOpenCheats = {
                                    clearHoldInputs()
                                    showCheatDialog = true
                                },
                                onBackToMenu = {
                                    if (engine.totalScore > 0) {
                                        FirebaseLeaderboard.syncSavedPlayerRecordIfAny(
                                            difficultyId = engine.difficulty.id,
                                            score = engine.totalScore,
                                            timeSeconds = 0
                                        )
                                    }
                                    clearHoldInputs()
                                    currentScreen = AppScreen.MENU
                                },
                                onHoldFwd = { holdFwd = it },
                                onHoldBack = { holdBack = it },
                                onHoldStrafeL = { holdStrafeL = it },
                                onHoldStrafeR = { holdStrafeR = it },
                                onHoldTurnL = { holdTurnL = it },
                                onHoldTurnR = { holdTurnR = it },
                                onDragTurn = { delta -> dragTurnAccum += delta },
                                onFireHold = { held ->
                                    isFiringHeld = held
                                    if (held) engine.fireCurrentWeapon()
                                },
                                onUseAction = { engine.triggerUseAction() }
                            )
                        }

                        if (showCheatDialog) {
                            CheatTerminalDialog(
                                engine = engine,
                                onDismiss = { showCheatDialog = false }
                            )
                        }
                    }

                    AppScreen.LEADERBOARD -> {
                        LeaderboardScreen(
                            initialDifficulty = selectedDifficulty,
                            savedNick = savedNick,
                            onNickSaved = { savedNick = it },
                            lastScore = engine.totalScore,
                            lastCompletedTimeSec = engine.completedLevelStats?.timeSeconds ?: 0,
                            onBack = { currentScreen = AppScreen.MENU }
                        )
                    }

                    AppScreen.GUIDE -> {
                        GuideAndSecretsScreen(onBack = { currentScreen = AppScreen.MENU })
                    }
                }
            }
        }
    }
}

@Composable
private fun MainMenuScreen(
    fireNowMs: Long,
    savedNick: String,
    onNickSaved: (String) -> Unit,
    selectedDifficulty: Difficulty,
    onSelectDifficulty: (Difficulty) -> Unit,
    selectedStartMap: Int,
    onSelectStartMap: (Int) -> Unit,
    sensitivity: AimSensitivity,
    onCycleSensitivity: () -> Unit,
    soundOn: Boolean,
    musicOn: Boolean,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit,
    onStartGame: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenGuide: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF140808), Color(0xFF0B0C0E), Color(0xFF160B09))
                )
            )
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column: Animated DOOM Fire Logo + Marine Identity + Map + Launch
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DoomTitleFireHeader(fireNowMs = fireNowMs, heightDp = 92)
                    Spacer(modifier = Modifier.height(8.dp))
                    MarineIdentityPanel(
                        savedNick = savedNick,
                        selectedDifficulty = selectedDifficulty,
                        initialScore = 0,
                        initialTimeSec = 0,
                        onNickSaved = onNickSaved
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DoomMapSelectorPanel(
                        selectedStartMap = selectedStartMap,
                        onSelectStartMap = onSelectStartMap
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DoomLaunchAndToolsPanel(
                        curMapCode = DoomMaps.allMaps[selectedStartMap].code,
                        sensitivity = sensitivity,
                        soundOn = soundOn,
                        musicOn = musicOn,
                        onStartGame = onStartGame,
                        onOpenLeaderboard = onOpenLeaderboard,
                        onOpenGuide = onOpenGuide,
                        onCycleSensitivity = onCycleSensitivity,
                        onToggleSound = onToggleSound,
                        onToggleMusic = onToggleMusic
                    )
                }

                // Right Column: Classic Skull Skill Selector + Copyright
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    DoomSkillSelectorPanel(
                        selectedDifficulty = selectedDifficulty,
                        onSelectDifficulty = onSelectDifficulty
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "© 2026 Created by Pavel Mareš",
                        color = Color(0xFF8D6E63),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DoomTitleFireHeader(fireNowMs = fireNowMs, heightDp = 112)
                Spacer(modifier = Modifier.height(10.dp))

                MarineIdentityPanel(
                    savedNick = savedNick,
                    selectedDifficulty = selectedDifficulty,
                    initialScore = 0,
                    initialTimeSec = 0,
                    onNickSaved = onNickSaved
                )
                Spacer(modifier = Modifier.height(10.dp))

                DoomMapSelectorPanel(
                    selectedStartMap = selectedStartMap,
                    onSelectStartMap = onSelectStartMap
                )
                Spacer(modifier = Modifier.height(10.dp))

                DoomSkillSelectorPanel(
                    selectedDifficulty = selectedDifficulty,
                    onSelectDifficulty = onSelectDifficulty
                )
                Spacer(modifier = Modifier.height(12.dp))

                DoomLaunchAndToolsPanel(
                    curMapCode = DoomMaps.allMaps[selectedStartMap].code,
                    sensitivity = sensitivity,
                    soundOn = soundOn,
                    musicOn = musicOn,
                    onStartGame = onStartGame,
                    onOpenLeaderboard = onOpenLeaderboard,
                    onOpenGuide = onOpenGuide,
                    onCycleSensitivity = onCycleSensitivity,
                    onToggleSound = onToggleSound,
                    onToggleMusic = onToggleMusic
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "© 2026 Created by Pavel Mareš",
                    color = Color(0xFF8D6E63),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DoomTitleFireHeader(fireNowMs: Long, heightDp: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF120707), RoundedCornerShape(8.dp))
            .border(2.dp, Color(0xFF8D4E2A), RoundedCornerShape(8.dp))
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .border(1.dp, Color(0xFF3E2723), RoundedCornerShape(5.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                renderTitleBanner(fireNowMs)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA0D0707))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EPIZODA 1 • KNEE-DEEP IN THE DEAD",
                    color = Color(0xFFFFD54F),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Permanent Nickname Lock Component:
 * Shows registration input ONLY when savedNick is empty. Once saved, the input field permanently disappears!
 */
@Composable
private fun MarineIdentityPanel(
    savedNick: String,
    selectedDifficulty: Difficulty,
    initialScore: Int,
    initialTimeSec: Int,
    onNickSaved: (String) -> Unit
) {
    var nickDraft by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val localBestScore = FirebaseLeaderboard.getLocalBestScore(selectedDifficulty.id)
    val localBestTime = FirebaseLeaderboard.getLocalBestTime(selectedDifficulty.id)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF171A1F), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF5D4037), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (savedNick.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💀 MARIŇÁK: $savedNick",
                    color = Color(0xFFFFD54F),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val bestStr = buildString {
                    append("Rekord: $localBestScore b.")
                    if (localBestTime > 0) {
                        append(" • ${FirebaseLeaderboard.formatTimeMmSs(localBestTime)}")
                    }
                }
                Text(
                    text = bestStr,
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = "IDENTIFIKACE MARIŇÁKA PRO ONLINE REKORDY:",
                color = Color(0xFFFFCC80),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nickDraft,
                    onValueChange = { nickDraft = it.take(18) },
                    placeholder = { Text("Zadej unikátní přezdívku...", fontSize = 12.sp, color = Color(0xFF78909C)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFFAB00),
                        unfocusedBorderColor = Color(0xFF546E7A)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        statusText = "Ověřuji..."
                        FirebaseLeaderboard.registerNickname(
                            rawNick = nickDraft,
                            difficultyId = selectedDifficulty.id,
                            initialScore = initialScore,
                            initialTimeSec = initialTimeSec
                        ) { ok, msg ->
                            busy = false
                            isError = !ok
                            statusText = msg
                            if (ok) {
                                onNickSaved(FirebaseLeaderboard.getSavedNick())
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) {
                    Text("ULOŽIT", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFFFFD54F))
                }
            }
            if (statusText.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = statusText,
                    color = if (isError) Color(0xFFFF5252) else Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DoomMapSelectorPanel(
    selectedStartMap: Int,
    onSelectStartMap: (Int) -> Unit
) {
    val curMap = DoomMaps.allMaps[selectedStartMap]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF16181D), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF4E342E), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = "SEKTOR NASAZENÍ NA PHOBOSU:",
            color = Color(0xFFB0BEC5),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            DoomBevelMiniButton("◀") {
                val prev = (selectedStartMap - 1 + DoomMaps.allMaps.size) % DoomMaps.allMaps.size
                onSelectStartMap(prev)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Text(
                    text = "${curMap.code} — ${curMap.title}",
                    color = Color(0xFFFFF8E1),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Par Time: ${FirebaseLeaderboard.formatTimeMmSs(curMap.parSeconds)} • Monstra: ${curMap.monsters.size}",
                    color = Color(0xFFFFAB40),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            DoomBevelMiniButton("▶") {
                val next = (selectedStartMap + 1) % DoomMaps.allMaps.size
                onSelectStartMap(next)
            }
        }
    }
}

@Composable
private fun DoomBevelMiniButton(symbol: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp, 34.dp)
            .background(Color(0xFF2B1D1D), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF8D6E63), RoundedCornerShape(6.dp))
            .clickable {
                DoomAudioManager.playSound(DoomSoundEffect.SWITCH_CLICK)
                onClick()
            }
    ) {
        Text(text = symbol, color = Color(0xFFFFD54F), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun DoomSkillSelectorPanel(
    selectedDifficulty: Difficulty,
    onSelectDifficulty: (Difficulty) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141112), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF5D4037), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(
            text = "ZVOL OBTÍŽNOST (CHOOSE SKILL LEVEL):",
            color = Color(0xFFFFCC80),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        Difficulty.entries.forEach { diff ->
            val selected = (diff == selectedDifficulty)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .background(
                        if (selected) Color(0xFF56100C) else Color(0xFF1C1E22),
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) Color(0xFFFFB300) else Color(0xFF333840),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                        onSelectDifficulty(diff)
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selected) "💀" else "  ",
                    fontSize = 12.sp,
                    modifier = Modifier.width(22.dp)
                )
                Text(
                    text = diff.titleCz,
                    color = if (selected) Color(0xFFFFF3E0) else Color(0xFFCFD8DC),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = diff.subtitleOrig,
                    color = if (selected) Color(0xFFFFD54F) else Color(0xFF78909C),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DoomLaunchAndToolsPanel(
    curMapCode: String,
    sensitivity: AimSensitivity,
    soundOn: Boolean,
    musicOn: Boolean,
    onStartGame: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenGuide: () -> Unit,
    onCycleSensitivity: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Big Chiseled DOOM Launch Slab
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFB71C1C), Color(0xFF7F0000))
                    ),
                    RoundedCornerShape(8.dp)
                )
                .border(2.dp, Color(0xFFFFB300), RoundedCornerShape(8.dp))
                .clickable {
                    DoomAudioManager.playSound(DoomSoundEffect.SHOTGUN)
                    onStartGame()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "▶ VSTOUPIT DO ZÁKLADNY ($curMapCode)",
                color = Color(0xFFFFF8E1),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                maxLines = 1
            )
        }

        // Secondary DOOM Iron Plates: Leaderboard & Secrets Guide
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            DoomIronMenuButton(
                label = "🏆 ONLINE REKORDY",
                accentBorder = Color(0xFFD4AF37),
                modifier = Modifier.weight(1f),
                onClick = onOpenLeaderboard
            )
            DoomIronMenuButton(
                label = "🕵 SKRÝŠE & CHEATY",
                accentBorder = Color(0xFF78909C),
                modifier = Modifier.weight(1f),
                onClick = onOpenGuide
            )
        }

        // Settings Row: Aim Sensitivity, Sound & Riff E1M1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            DoomIronMenuButton(
                label = "🎯 Citl: ${sensitivity.labelCz}",
                accentBorder = Color(0xFF546E7A),
                fontSizeSp = 10,
                modifier = Modifier.weight(1f),
                onClick = onCycleSensitivity
            )
            DoomIronMenuButton(
                label = if (soundOn) "🔊 Zvuk: ZAP" else "🔇 Zvuk: VYP",
                accentBorder = if (soundOn) Color(0xFF2E7D32) else Color(0xFF455A64),
                fontSizeSp = 10,
                modifier = Modifier.weight(1f),
                onClick = onToggleSound
            )
            DoomIronMenuButton(
                label = if (musicOn) "🎸 E1M1: ZAP" else "🎸 E1M1: VYP",
                accentBorder = if (musicOn) Color(0xFF2E7D32) else Color(0xFF455A64),
                fontSizeSp = 10,
                modifier = Modifier.weight(1f),
                onClick = onToggleMusic
            )
        }
    }
}

@Composable
private fun DoomIronMenuButton(
    label: String,
    accentBorder: Color,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 11,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .background(Color(0xFF1E2126), RoundedCornerShape(6.dp))
            .border(1.dp, accentBorder, RoundedCornerShape(6.dp))
            .clickable {
                DoomAudioManager.playSound(DoomSoundEffect.SWITCH_CLICK)
                onClick()
            }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFECEFF1),
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun GamePlayScreen(
    engine: DoomEngine,
    frameTick: Int,
    sensitivityMult: Float,
    savedNick: String,
    onNickSaved: (String) -> Unit,
    showAutomap: Boolean,
    onToggleAutomap: () -> Unit,
    onOpenCheats: () -> Unit,
    onBackToMenu: () -> Unit,
    onHoldFwd: (Boolean) -> Unit,
    onHoldBack: (Boolean) -> Unit,
    onHoldStrafeL: (Boolean) -> Unit,
    onHoldStrafeR: (Boolean) -> Unit,
    onHoldTurnL: (Boolean) -> Unit,
    onHoldTurnR: (Boolean) -> Unit,
    onDragTurn: (Float) -> Unit,
    onFireHold: (Boolean) -> Unit,
    onUseAction: () -> Unit
) {
    val tick = frameTick

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // =========================================================================
            // CONSOLE / HANDHELD LANDSCAPE GAMEPAD LAYOUT (Widescreen 3D + Side Pads)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B0D10))
            ) {
                // LEFT THUMB GAMEPAD COLUMN
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF13161B))
                        .border(1.dp, Color(0xFF2B313A))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Map & Stats readout + Mapa / Menu buttons
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1B2027), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${engine.currentMapDef.code} • ${FirebaseLeaderboard.formatTimeMmSs(engine.levelTimeSec.toInt())}",
                                color = Color(0xFFFFD54F),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Skóre: ${engine.totalScore}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Zabití ${engine.killsCount}/${engine.totalMonsters} • Skr. ${engine.secretsCount}/${engine.totalSecrets}",
                                color = Color(0xFFB0BEC5),
                                fontSize = 9.5.sp,
                                maxLines = 1
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TopCompactActionChip(
                                label = "Mapa",
                                bgColor = if (showAutomap) Color(0xFF00C853) else Color(0xFF37474F),
                                modifier = Modifier.weight(1f),
                                onClick = onToggleAutomap
                            )
                            TopCompactActionChip(
                                label = "Menu",
                                bgColor = Color(0xFF455A64),
                                modifier = Modifier.weight(1f),
                                onClick = onBackToMenu
                            )
                        }
                    }

                    // Ergonomic Left Thumb Movement & Strafe Pad
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HoldControlButton(
                                label = "↶",
                                bgColor = Color(0xFF263238),
                                fontSizeSp = 13,
                                modifier = Modifier.weight(1f).height(46.dp),
                                onPressChanged = onHoldTurnL
                            )
                            HoldControlButton(
                                label = "▲",
                                bgColor = Color(0xFF37474F),
                                fontSizeSp = 14,
                                modifier = Modifier.weight(1.15f).height(46.dp),
                                onPressChanged = onHoldFwd
                            )
                            HoldControlButton(
                                label = "↷",
                                bgColor = Color(0xFF263238),
                                fontSizeSp = 13,
                                modifier = Modifier.weight(1f).height(46.dp),
                                onPressChanged = onHoldTurnR
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HoldControlButton(
                                label = "◀ Úkr",
                                bgColor = Color(0xFF263238),
                                fontSizeSp = 10,
                                modifier = Modifier.weight(1f).height(46.dp),
                                onPressChanged = onHoldStrafeL
                            )
                            HoldControlButton(
                                label = "▼",
                                bgColor = Color(0xFF37474F),
                                fontSizeSp = 14,
                                modifier = Modifier.weight(1.15f).height(46.dp),
                                onPressChanged = onHoldBack
                            )
                            HoldControlButton(
                                label = "Úkr ▶",
                                bgColor = Color(0xFF263238),
                                fontSizeSp = 10,
                                modifier = Modifier.weight(1f).height(46.dp),
                                onPressChanged = onHoldStrafeR
                            )
                        }
                    }
                }

                // CENTER FULL-HEIGHT WIDESCREEN 3D VIEWPORT + SLIM STATUS BAR
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    DoomViewportBox(
                        engine = engine,
                        tick = tick,
                        showAutomap = showAutomap,
                        sensitivityMult = sensitivityMult,
                        savedNick = savedNick,
                        onNickSaved = onNickSaved,
                        onDragTurn = onDragTurn,
                        onUseAction = onUseAction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    // Slim Classic DOOM Status Strip (only 42dp tall so 3D view stays huge!)
                    LandscapeSlimStatusBar(engine = engine, frameTick = tick)
                }

                // RIGHT THUMB GAMEPAD COLUMN (Weapons + Doors + Fire & Aim)
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF13161B))
                        .border(1.dp, Color(0xFF2B313A))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Cheat button + Direct Weapon Slots 1..7
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TopCompactActionChip(
                            label = "⌨ Cheat IDDQD",
                            bgColor = Color(0xFF5E1688),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenCheats
                        )

                        // 2 rows of weapon slot buttons (1..4 and 5..7)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            for (slot in 1..4) {
                                CompactWeaponPill(engine = engine, slot = slot, modifier = Modifier.weight(1f))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            for (slot in 5..7) {
                                CompactWeaponPill(engine = engine, slot = slot, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // Bottom Right Combat Cluster: Doors, Cycle Weapon & Drag-Aim Fire Button!
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .background(Color(0xFF1565C0), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF90CAF9), RoundedCornerShape(8.dp))
                                    .clickable { onUseAction() }
                            ) {
                                Text(
                                    text = "🖐 DVEŘE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .background(Color(0xFF37474F), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF78909C), RoundedCornerShape(8.dp))
                                    .clickable { engine.cycleWeapon(next = true) }
                            ) {
                                Text(
                                    text = "🔄 ZBRAŇ",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }

                        FireAndAimButton(
                            label = "🔥 PALBA",
                            sensitivityMult = sensitivityMult,
                            onFireHold = onFireHold,
                            onDragTurn = onDragTurn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        )
                    }
                }
            }
        } else {
            // =========================================================================
            // PORTRAIT LAYOUT
            // =========================================================================
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(Color(0xFF16191E))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${engine.currentMapDef.code} • Skóre ${engine.totalScore} • ${FirebaseLeaderboard.formatTimeMmSs(engine.levelTimeSec.toInt())}",
                            color = Color(0xFFFFD600),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Zabití ${engine.killsCount}/${engine.totalMonsters} • Skrýše ${engine.secretsCount}/${engine.totalSecrets}",
                            color = Color(0xFFB0BEC5),
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TopCompactActionChip(
                            label = "Mapa",
                            bgColor = if (showAutomap) Color(0xFF00C853) else Color(0xFF37474F),
                            onClick = onToggleAutomap
                        )
                        TopCompactActionChip(
                            label = "IDDQD",
                            bgColor = Color(0xFF6A1B9A),
                            onClick = onOpenCheats
                        )
                        TopCompactActionChip(
                            label = "Menu",
                            bgColor = Color(0xFF455A64),
                            onClick = onBackToMenu
                        )
                    }
                }

                DoomViewportBox(
                    engine = engine,
                    tick = tick,
                    showAutomap = showAutomap,
                    sensitivityMult = sensitivityMult,
                    savedNick = savedNick,
                    onNickSaved = onNickSaved,
                    onDragTurn = onDragTurn,
                    onUseAction = onUseAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                ClassicDoomStatusBar(engine = engine, frameTick = tick)

                TouchControlsBar(
                    sensitivityMult = sensitivityMult,
                    onHoldFwd = onHoldFwd,
                    onHoldBack = onHoldBack,
                    onHoldStrafeL = onHoldStrafeL,
                    onHoldStrafeR = onHoldStrafeR,
                    onHoldTurnL = onHoldTurnL,
                    onHoldTurnR = onHoldTurnR,
                    onFireHold = onFireHold,
                    onDragTurn = onDragTurn,
                    onUseAction = onUseAction,
                    onCycleWeapon = { engine.cycleWeapon(next = true) }
                )
            }
        }
    }
}

@Composable
private fun DoomViewportBox(
    engine: DoomEngine,
    tick: Int,
    showAutomap: Boolean,
    sensitivityMult: Float,
    savedNick: String,
    onNickSaved: (String) -> Unit,
    onDragTurn: (Float) -> Unit,
    onUseAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val curSens by rememberUpdatedState(sensitivityMult)
    val curDragTurn by rememberUpdatedState(onDragTurn)
    val curUse by rememberUpdatedState(onUseAction)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    curDragTurn(dragAmount.x * 0.0065f * curSens)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { engine.fireCurrentWeapon() },
                    onDoubleTap = { curUse() }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (tick >= 0) {
                render3DView(engine)
                if (showAutomap) {
                    renderAutomap(engine)
                }
            }
        }

        if (engine.statusMessageTimer > 0f && engine.statusMessage.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .background(Color(0xD8121417), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFFFFAB00), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = engine.statusMessage,
                    color = Color(0xFFFFEA00),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (engine.isPlayerDead) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp)
                    .width(290.dp)
                    .background(Color(0xF0220606), RoundedCornerShape(10.dp))
                    .border(2.dp, Color(0xFFFF1744), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💀 ZEMŘEL JSI! 💀",
                    color = Color(0xFFFF5252),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Dosažené skóre: ${engine.totalScore} b.",
                    color = Color(0xFFFFD54F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (savedNick.isEmpty() && engine.totalScore > 0) {
                    MarineIdentityPanel(
                        savedNick = savedNick,
                        selectedDifficulty = engine.difficulty,
                        initialScore = engine.totalScore,
                        initialTimeSec = 0,
                        onNickSaved = onNickSaved
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { engine.loadMap(engine.currentMapIndex, resetInventory = true) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Zkusit ${engine.currentMapDef.code} znovu", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CompactWeaponPill(
    engine: DoomEngine,
    slot: Int,
    modifier: Modifier = Modifier
) {
    val owned = WeaponType.entries.any { it.slot == slot && engine.ownedWeapons.contains(it) }
    val active = (engine.currentWeapon.slot == slot)
    val shortName = when (slot) {
        1 -> if (engine.ownedWeapons.contains(WeaponType.CHAINSAW)) "1Pil" else "1Pěs"
        2 -> "2Pis"
        3 -> "3Bro"
        4 -> "4Kul"
        5 -> "5Rak"
        6 -> "6Pla"
        else -> "7BFG"
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(25.dp)
            .background(
                when {
                    active -> Color(0xFFC62828)
                    owned -> Color(0xFF263238)
                    else -> Color(0xFF171A1F)
                },
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (active) Color(0xFFFFD54F) else Color(0xFF455A64),
                RoundedCornerShape(4.dp)
            )
            .clickable(enabled = owned) { engine.selectWeaponSlot(slot) }
    ) {
        Text(
            text = shortName,
            color = if (owned) Color.White else Color(0xFF546E7A),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun LandscapeSlimStatusBar(engine: DoomEngine, frameTick: Int) {
    val curAmmoType = engine.currentWeapon.ammoType
    val curAmmoText = if (curAmmoType == AmmoType.NONE) "∞" else "${engine.ammo[curAmmoType] ?: 0}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color(0xFF33373D))
            .border(1.dp, Color(0xFF546E7A))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        StatusBox(
            label = "MUNICE",
            value = curAmmoText,
            valueColor = Color(0xFFFF5252),
            compact = true,
            modifier = Modifier.weight(1f)
        )
        StatusBox(
            label = "ZDRAVÍ",
            value = "${engine.playerHealth}%",
            valueColor = Color(0xFFFF5252),
            compact = true,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(1.dp, Color(0xFF607D8B), RoundedCornerShape(3.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (frameTick >= 0) {
                    renderDoomguyFace(engine)
                }
            }
        }
        StatusBox(
            label = "BRNĚNÍ",
            value = "${engine.playerArmor}%",
            valueColor = Color(0xFF00E676),
            compact = true,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxHeight()
                .background(Color(0xFF1E2227), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp)
        ) {
            KeyCard.entries.forEach { k ->
                val has = engine.keys.contains(k)
                Box(
                    modifier = Modifier
                        .size(10.dp, 14.dp)
                        .background(
                            if (has) Color(k.colorHex) else Color(0xFF2C3138),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun TopCompactActionChip(
    label: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF607D8B), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ClassicDoomStatusBar(engine: DoomEngine, frameTick: Int) {
    val curAmmoType = engine.currentWeapon.ammoType
    val curAmmoText = if (curAmmoType == AmmoType.NONE) "∞" else "${engine.ammo[curAmmoType] ?: 0}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF363A40))
            .border(1.dp, Color(0xFF546E7A))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .defaultMinSize(minHeight = 50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            StatusBox(
                label = "MUNICE",
                value = curAmmoText,
                valueColor = Color(0xFFFF5252),
                modifier = Modifier.weight(1f)
            )
            StatusBox(
                label = "ZDRAVÍ",
                value = "${engine.playerHealth}%",
                valueColor = Color(0xFFFF5252),
                modifier = Modifier.weight(1.06f)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .border(1.dp, Color(0xFF607D8B), RoundedCornerShape(4.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (frameTick >= 0) {
                        renderDoomguyFace(engine)
                    }
                }
            }

            StatusBox(
                label = "BRNĚNÍ",
                value = "${engine.playerArmor}%",
                valueColor = Color(0xFF00E676),
                modifier = Modifier.weight(1.06f)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .background(Color(0xFF1E2227), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "KLÍČE",
                    color = Color(0xFFB0BEC5),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KeyCard.entries.forEach { k ->
                        val has = engine.keys.contains(k)
                        Box(
                            modifier = Modifier
                                .size(10.dp, 13.dp)
                                .background(
                                    if (has) Color(k.colorHex) else Color(0xFF2C3138),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (slot in 1..7) {
                val owned = WeaponType.entries.any { it.slot == slot && engine.ownedWeapons.contains(it) }
                val active = (engine.currentWeapon.slot == slot)
                val label = when (slot) {
                    1 -> if (engine.ownedWeapons.contains(WeaponType.CHAINSAW)) "1 Pila" else "1 Pěst"
                    2 -> "2 Pist"
                    3 -> "3 Brok"
                    4 -> "4 Kulo"
                    5 -> "5 Rake"
                    6 -> "6 Plaz"
                    else -> "7 BFG"
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 28.dp)
                        .background(
                            when {
                                active -> Color(0xFFD50000)
                                owned -> Color(0xFF263238)
                                else -> Color(0xFF181B1F)
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (active) Color(0xFFFFD600) else Color(0xFF455A64),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(enabled = owned) { engine.selectWeaponSlot(slot) }
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = if (owned) Color.White else Color(0xFF546E7A),
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBox(
    label: String,
    value: String,
    valueColor: Color,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF1E2227), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = if (compact) 1.dp else 4.dp)
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = if (compact) 12.sp else 14.sp,
            lineHeight = if (compact) 13.sp else 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = label,
            color = Color(0xFFB0BEC5),
            fontSize = if (compact) 7.5.sp else 9.sp,
            lineHeight = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TouchControlsBar(
    sensitivityMult: Float,
    onHoldFwd: (Boolean) -> Unit,
    onHoldBack: (Boolean) -> Unit,
    onHoldStrafeL: (Boolean) -> Unit,
    onHoldStrafeR: (Boolean) -> Unit,
    onHoldTurnL: (Boolean) -> Unit,
    onHoldTurnR: (Boolean) -> Unit,
    onFireHold: (Boolean) -> Unit,
    onDragTurn: (Float) -> Unit,
    onUseAction: () -> Unit,
    onCycleWeapon: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121519))
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1.18f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HoldControlButton(
                    label = "↶ Otoč",
                    bgColor = Color(0xFF263238),
                    fontSizeSp = 10,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                    onPressChanged = onHoldTurnL
                )
                HoldControlButton(
                    label = "▲ Vpřed",
                    bgColor = Color(0xFF37474F),
                    fontSizeSp = 10,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                    onPressChanged = onHoldFwd
                )
                HoldControlButton(
                    label = "↷ Otoč",
                    bgColor = Color(0xFF263238),
                    fontSizeSp = 10,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                    onPressChanged = onHoldTurnR
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HoldControlButton(
                    label = "◀ Úkrok",
                    bgColor = Color(0xFF263238),
                    fontSizeSp = 10,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                    onPressChanged = onHoldStrafeL
                )
                HoldControlButton(
                    label = "▼ Vzad",
                    bgColor = Color(0xFF37474F),
                    fontSizeSp = 10,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                    onPressChanged = onHoldBack
                )
                HoldControlButton(
                    label = "Úkrok ▶",
                    bgColor = Color(0xFF263238),
                    fontSizeSp = 10,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 42.dp),
                    onPressChanged = onHoldStrafeR
                )
            }
        }

        Column(
            modifier = Modifier.weight(1.0f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 42.dp)
                        .background(Color(0xFF1E88E5), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF90CAF9), RoundedCornerShape(8.dp))
                        .clickable { onUseAction() }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🖐 DVEŘE",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 42.dp)
                        .background(Color(0xFF455A64), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF78909C), RoundedCornerShape(8.dp))
                        .clickable { onCycleWeapon() }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🔄 ZBRAŇ",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            FireAndAimButton(
                label = "🔥 PALBA",
                sensitivityMult = sensitivityMult,
                onFireHold = onFireHold,
                onDragTurn = onDragTurn,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 42.dp)
            )
        }
    }
}

/**
 * Rock-solid hold button using awaitEachGesture so slight thumb wiggle never cancels walking/strafing!
 */
@Composable
private fun HoldControlButton(
    label: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 10,
    onPressChanged: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val curCallback by rememberUpdatedState(onPressChanged)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (isPressed) Color(0xFFFF6D00) else bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, if (isPressed) Color(0xFFFFD54F) else Color(0xFF78909C), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    curCallback(true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (tracked == null || !tracked.pressed) break
                            tracked.consume()
                        }
                    } finally {
                        isPressed = false
                        curCallback(false)
                    }
                }
            }
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 2).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Dedicated FPS Fire + Drag-Aim button: holds trigger AND smoothly aims left/right when sliding thumb!
 */
@Composable
private fun FireAndAimButton(
    label: String,
    sensitivityMult: Float,
    onFireHold: (Boolean) -> Unit,
    onDragTurn: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val curFire by rememberUpdatedState(onFireHold)
    val curDrag by rememberUpdatedState(onDragTurn)
    val curSens by rememberUpdatedState(sensitivityMult)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                if (isPressed) Color(0xFFFF6D00) else Color(0xFFC62828),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.5.dp,
                if (isPressed) Color(0xFFFFEA00) else Color(0xFFFF8A80),
                RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    curFire(true)
                    var lastX = down.position.x
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (tracked == null || !tracked.pressed) break
                            val dx = tracked.position.x - lastX
                            lastX = tracked.position.x
                            if (dx != 0f) {
                                curDrag(dx * 0.0075f * curSens)
                            }
                            tracked.consume()
                        }
                    } finally {
                        isPressed = false
                        curFire(false)
                    }
                }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun IntermissionScreen(
    stats: LevelSummaryStats,
    difficulty: Difficulty,
    savedNick: String,
    onNickSaved: (String) -> Unit,
    onNextLevel: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val compactBtnPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val killPct = if (stats.totalMonsters > 0) (stats.killsCount * 100) / stats.totalMonsters else 100
    val itemPct = if (stats.totalItems > 0) (stats.itemsCount * 100) / stats.totalItems else 100
    val secretPct = if (stats.totalSecrets > 0) (stats.secretsCount * 100) / stats.totalSecrets else 100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (stats.isEpisodeCompleted) "🏆 EPIZODA DOKONČENA! 🏆" else "${stats.mapCode} DOKONČENO!",
            color = Color(0xFFFF3D00),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stats.mapTitle,
            color = Color(0xFFFFD54F),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (stats.isSecretNext) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "★ ODHALEN TAJNÝ VÝCHOD DO VOJENSKÉ ZÁKLADNY E1M9! ★",
                color = Color(0xFFE040FB),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF171A20), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF5D4037), RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            StatRow("ZABITÍ MONSTER (KILLS):", "$killPct % (${stats.killsCount}/${stats.totalMonsters})")
            StatRow("SEBRANÉ PŘEDMĚTY (ITEMS):", "$itemPct % (${stats.itemsCount}/${stats.totalItems})")
            StatRow("TAJNÉ SKRÝŠE (SECRETS):", "$secretPct % (${stats.secretsCount}/${stats.totalSecrets})")
            StatRow("ČAS MAPY / PAR TIME:", "${FirebaseLeaderboard.formatTimeMmSs(stats.timeSeconds)} / ${FirebaseLeaderboard.formatTimeMmSs(stats.parTimeSeconds)}")
            StatRow("CELKOVÉ SKÓRE:", "${stats.totalScore} bodů")
        }

        Spacer(modifier = Modifier.height(12.dp))

        MarineIdentityPanel(
            savedNick = savedNick,
            selectedDifficulty = difficulty,
            initialScore = stats.totalScore,
            initialTimeSec = stats.timeSeconds,
            onNickSaved = onNickSaved
        )

        if (savedNick.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "☁ Výsledek byl automaticky odeslán do online žebříčku (${difficulty.titleCz}).",
                color = Color(0xFF00E676),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onNextLevel,
            contentPadding = compactBtnPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
        ) {
            val nextLabel = if (stats.isEpisodeCompleted) {
                "🏆 ZOBRAZIT ONLINE ŽEBŘÍČEK VÍTĚZŮ"
            } else {
                val nextMap = DoomMaps.allMaps[stats.nextMapIndex]
                "▶ POKRAČOVAT DO ${nextMap.code}"
            }
            Text(nextLabel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFFFFF8E1), maxLines = 1)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onBackToMenu,
            contentPadding = compactBtnPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zpět do hlavního menu")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFFB0BEC5), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(text = value, color = Color(0xFFFFEA00), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

@Composable
private fun CheatTerminalDialog(
    engine: DoomEngine,
    onDismiss: () -> Unit
) {
    val compactBtnPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    var codeInput by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("Klikni na cheat nebo ho napiš ručně:") }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { onDismiss() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(Color(0xFF1A1E24), RoundedCornerShape(10.dp))
                .border(2.dp, Color(0xFFFFAB00), RoundedCornerShape(10.dp))
                .clickable(enabled = false) { }
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⌨ TERMINÁL CHEATŮ DOOM I",
                color = Color(0xFFFFD600),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = feedback, color = Color(0xFF00E676), fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))

            val quickCheats = listOf(
                "IDDQD" to "IDDQD (Bůh)",
                "IDKFA" to "IDKFA (Vše + Klíče)",
                "IDFA" to "IDFA (Zbraně)",
                "IDSPISPOPD" to "IDSPISPOPD (Zdi)",
                "IDDT" to "IDDT (Celá mapa)",
                "IDCLEV19" to "IDCLEV19 (E1M9)"
            )

            for (row in quickCheats.chunked(2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (pair in row) {
                        Button(
                            onClick = { feedback = engine.applyCheatCode(pair.first) },
                            contentPadding = compactBtnPadding,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(pair.second, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                label = { Text("Vlastní cheat (IDCLEV11 až IDCLEV19)", fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        feedback = engine.applyCheatCode(codeInput)
                        codeInput = ""
                    },
                    contentPadding = compactBtnPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("AKTIVOVAT", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDismiss,
                    contentPadding = compactBtnPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ZAVŘÍT")
                }
            }
        }
    }
}

@Composable
private fun LeaderboardScreen(
    initialDifficulty: Difficulty,
    savedNick: String,
    onNickSaved: (String) -> Unit,
    lastScore: Int,
    lastCompletedTimeSec: Int,
    onBack: () -> Unit
) {
    val compactBtnPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
    var selectedDiff by remember { mutableStateOf(initialDifficulty) }
    var showBestTime by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(emptyList<LeaderboardEntry>()) }
    var loading by remember { mutableStateOf(true) }
    val myDevId = remember { FirebaseLeaderboard.getDeviceId() }

    fun reload() {
        loading = true
        FirebaseLeaderboard.fetchLeaderboard(
            difficultyId = selectedDiff.id,
            isBestTime = showBestTime,
            includeZeroScores = false
        ) { list ->
            entries = list
            loading = false
        }
    }

    LaunchedEffect(selectedDiff, showBestTime, savedNick) {
        reload()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🏆 ONLINE REKORDY DOOM I",
                color = Color(0xFFFFD54F),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            DoomBevelMiniButton("↻") { reload() }
        }
        Spacer(modifier = Modifier.height(6.dp))

        MarineIdentityPanel(
            savedNick = savedNick,
            selectedDifficulty = selectedDiff,
            initialScore = lastScore,
            initialTimeSec = lastCompletedTimeSec,
            onNickSaved = {
                onNickSaved(it)
                reload()
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Difficulty tabs 1..5
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Difficulty.entries.forEach { d ->
                val active = (d == selectedDiff)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (active) Color(0xFF8E1410) else Color(0xFF1F242B),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (active) Color(0xFFFFB300) else Color(0xFF37474F),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedDiff = d }
                        .padding(vertical = 5.dp)
                ) {
                    Text(
                        text = "💀 ${d.ordinal + 1}",
                        color = if (active) Color(0xFFFFF8E1) else Color(0xFFB0BEC5),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = selectedDiff.titleCz, color = Color(0xFFFFCC80), fontSize = 11.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showBestTime = false },
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!showBestTime) Color(0xFFB71C1C) else Color(0xFF263238)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("🎯 Nejvyšší skóre", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
            Button(
                onClick = { showBestTime = true },
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showBestTime) Color(0xFFB71C1C) else Color(0xFF263238)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("⏱ Nejrychlejší čas", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF14171C), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF4E342E), RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (loading) {
                Text("Načítám online databázi...", color = Color(0xFFB0BEC5), fontSize = 13.sp)
            } else if (entries.isEmpty()) {
                Text(
                    text = if (showBestTime) {
                        "V této obtížnosti zatím nikdo nedokončil mapu na čas. Buď první!"
                    } else {
                        "V této obtížnosti zatím nejsou žádné nenulové rekordy. Buď první!"
                    },
                    color = Color(0xFFB0BEC5),
                    fontSize = 13.sp
                )
            } else {
                entries.forEachIndexed { idx, item ->
                    val isMine = (item.deviceId.isNotEmpty() && item.deviceId == myDevId) ||
                        (savedNick.isNotEmpty() && item.nick == savedNick)
                    val valFormatted = if (showBestTime) {
                        FirebaseLeaderboard.formatTimeMmSs(item.value)
                    } else {
                        "${item.value} b."
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isMine) Color(0xFF4A1814) else Color(0xFF1E232B),
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (isMine) Color(0xFFFFB300) else Color(0xFF2C333F),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val medal = when (idx) {
                            0 -> "🥇 "
                            1 -> "🥈 "
                            2 -> "🥉 "
                            else -> "${idx + 1}. "
                        }
                        Text(
                            text = "$medal${item.nick}",
                            color = if (isMine) Color(0xFFFFF8E1) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = valFormatted,
                            color = if (isMine) Color(0xFF00E676) else Color(0xFFFFD54F),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onBack,
            contentPadding = compactBtnPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zpět do hlavního menu", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GuideAndSecretsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "🕵 PRŮVODCE SKRÝŠEMI A HERNÍ LOGIKOU DOOM I",
            color = Color(0xFFFFD600),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        val sections = listOf(
            "Ovládání na výšku i naležato (Gamepad)" to "Otoč telefon naležato pro plnohodnotný širokoúhlý režim! Levým palcem chodíš a děláš úkroky, pravým palcem můžeš plynule mířit přímo tažením po tlačítku PALBA nebo po 3D obrazovce.",
            "Jak najít tajné skrýše (Secrets)" to "V každé z 9 map jsou tajné posuvné zdi (označené jemnou prasklinou a svislou spárou uprostřed panelu) a skryté sektory. Postav se ke stěně a stiskni DVEŘE. Po otevření uslyšíš signál a započítá se ti nalezené tajemství!",
            "Tajný východ do E1M9 (Military Base)" to "V mapě E1M3 (Toxin Refinery) jsou dva východy: klasický červeno-zelený spínač EXIT vede do E1M4, zatímco tajný fialovo-zlatý spínač EXIT vpravo nahoře za tajnou stěnou tě přenese do bonusové mapy E1M9!",
            "Monster Infighting & Sudy" to "Pokud výstřel jednoho monstra zasáhne jiný druh démona, pustí se do boje mezi sebou. Výbušné sudy s radioaktivním slizem po rozstřelení způsobí řetězovou tlakovou vlnu.",
            "Klasické cheaty z klávesnice" to "IDDQD (Nesmrtelnost), IDKFA (Všechny zbraně, 200 % zbroj a klíče), IDFA (Zbraně a munice bez klíčů), IDSPISPOPD (Procházení zdmi), IDDT (Odhalení celé automapy a skrýší), IDCLEV11 až IDCLEV19 (Okamžitý skok do mapy)."
        )

        for (s in sections) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0xFF171A20), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF4E342E), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(text = s.first, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = s.second, color = Color(0xFFECEFF1), fontSize = 12.5.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zpět do hlavního menu", fontWeight = FontWeight.Bold)
        }
    }
}
