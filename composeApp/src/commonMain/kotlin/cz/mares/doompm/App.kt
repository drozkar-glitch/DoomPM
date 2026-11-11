package cz.mares.doompm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class AppScreen {
    MENU,
    PLAYING,
    LEADERBOARD,
    GUIDE
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

            var frameTick by remember { mutableIntStateOf(0) }
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

            LaunchedEffect(currentScreen) {
                var lastNanos = 0L
                while (currentScreen == AppScreen.PLAYING) {
                    withFrameNanos { nowNanos ->
                        val dt = if (lastNanos == 0L) 0.016f else ((nowNanos - lastNanos) / 1_000_000_000f)
                        lastNanos = nowNanos

                        val moveFwd = (if (holdFwd) 1f else 0f) - (if (holdBack) 1f else 0f)
                        val moveStrafe = (if (holdStrafeR) 1f else 0f) - (if (holdStrafeL) 1f else 0f)
                        val turnDir = (if (holdTurnR) 1f else 0f) - (if (holdTurnL) 1f else 0f)
                        val turnDelta = turnDir * dt * 2.45f + dragTurnAccum
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
                    .background(Color(0xFF0E1013))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                color = Color(0xFF0E1013)
            ) {
                when (currentScreen) {
                    AppScreen.MENU -> {
                        MainMenuScreen(
                            selectedDifficulty = selectedDifficulty,
                            onSelectDifficulty = { selectedDifficulty = it },
                            selectedStartMap = selectedStartMap,
                            onSelectStartMap = { selectedStartMap = it },
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
                            }
                            IntermissionScreen(
                                stats = summary,
                                difficulty = engine.difficulty,
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
                            GamePlayScreen(
                                engine = engine,
                                frameTick = frameTick,
                                showAutomap = showAutomap,
                                onToggleAutomap = { showAutomap = !showAutomap },
                                onOpenCheats = {
                                    clearHoldInputs()
                                    showCheatDialog = true
                                },
                                onBackToMenu = {
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
                            lastScore = engine.totalScore,
                            lastTimeSec = engine.campaignTimeSec.toInt(),
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
    selectedDifficulty: Difficulty,
    onSelectDifficulty: (Difficulty) -> Unit,
    selectedStartMap: Int,
    onSelectStartMap: (Int) -> Unit,
    soundOn: Boolean,
    musicOn: Boolean,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit,
    onStartGame: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val compactBtnPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DOOM I",
            color = Color(0xFFFF2A00),
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Knee-Deep in the Dead (VGA 3D Pixel Edition)",
            color = Color(0xFFFFCC00),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "VYBER POČÁTEČNÍ MAPU EPIZODY 1:",
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        val curMap = DoomMaps.allMaps[selectedStartMap]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1E24), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF455A64), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            Button(
                onClick = {
                    val prev = (selectedStartMap - 1 + DoomMaps.allMaps.size) % DoomMaps.allMaps.size
                    onSelectStartMap(prev)
                },
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                modifier = Modifier.size(42.dp, 38.dp)
            ) {
                Text("◀", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "${curMap.code}: ${curMap.title}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Button(
                onClick = {
                    val next = (selectedStartMap + 1) % DoomMaps.allMaps.size
                    onSelectStartMap(next)
                },
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                modifier = Modifier.size(42.dp, 38.dp)
            ) {
                Text("▶", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "ZVOL OBTÍŽNOST (SKILL LEVEL):",
            color = Color(0xFFB0BEC5),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Difficulty.entries.forEach { diff ->
            val selected = (diff == selectedDifficulty)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(
                        if (selected) Color(0xFF7A1812) else Color(0xFF1B1F26),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color(0xFFFFAB00) else Color(0xFF37474F),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectDifficulty(diff) }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = diff.titleCz,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = diff.subtitleOrig,
                        color = Color(0xFFFFCC80),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onStartGame,
            contentPadding = compactBtnPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
        ) {
            Text(
                text = "🔥 SPUSTIT HRU DOOM I 🔥",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenLeaderboard,
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                modifier = Modifier.weight(1f)
            ) {
                Text("🏆 Rekordy", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Button(
                onClick = onOpenGuide,
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                modifier = Modifier.weight(1f)
            ) {
                Text("🕵 Skrýše & Cheaty", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggleSound,
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (soundOn) Color(0xFF37474F) else Color(0xFF263238)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (soundOn) "🔊 Zvuky: ZAP" else "🔇 Zvuky: VYP", fontSize = 12.sp, maxLines = 1)
            }
            Button(
                onClick = onToggleMusic,
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (musicOn) Color(0xFF37474F) else Color(0xFF263238)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (musicOn) "🎸 Riff E1M1: ZAP" else "🎸 Riff E1M1: VYP", fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "© 2026 Created by Pavel Mareš",
            color = Color(0xFF90A4AE),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun GamePlayScreen(
    engine: DoomEngine,
    frameTick: Int,
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

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Flexible Non-Clipping Top Tactical Bar
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

        // 2. Main VGA 3D Pixel-Art Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragTurn(dragAmount.x * 0.0065f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { engine.fireCurrentWeapon() },
                        onDoubleTap = { onUseAction() }
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
                        .padding(16.dp)
                        .background(Color(0xEB280606), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFFF1744), RoundedCornerShape(12.dp))
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "💀 ZEMŘEL JSI! 💀",
                        color = Color(0xFFFF5252),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { engine.loadMap(engine.currentMapIndex, resetInventory = true) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000))
                    ) {
                        Text("🔄 Zkusit mapu ${engine.currentMapDef.code} znovu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Classic DOOM Stone Status Bar (Intrinsic height so labels never clip!)
        ClassicDoomStatusBar(engine = engine, frameTick = tick)

        // 4. Safe-Padded Multi-Touch Controls Bar
        TouchControlsBar(
            onHoldFwd = onHoldFwd,
            onHoldBack = onHoldBack,
            onHoldStrafeL = onHoldStrafeL,
            onHoldStrafeR = onHoldStrafeR,
            onHoldTurnL = onHoldTurnL,
            onHoldTurnR = onHoldTurnR,
            onFireHold = onFireHold,
            onUseAction = onUseAction,
            onCycleWeapon = { engine.cycleWeapon(next = true) }
        )
    }
}

@Composable
private fun TopCompactActionChip(
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
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
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF1E2227), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = label,
            color = Color(0xFFB0BEC5),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TouchControlsBar(
    onHoldFwd: (Boolean) -> Unit,
    onHoldBack: (Boolean) -> Unit,
    onHoldStrafeL: (Boolean) -> Unit,
    onHoldStrafeR: (Boolean) -> Unit,
    onHoldTurnL: (Boolean) -> Unit,
    onHoldTurnR: (Boolean) -> Unit,
    onFireHold: (Boolean) -> Unit,
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
        // Left 3x2 Movement & Turn Pad
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

        // Right Action & Fire Cluster (Equal 1:1 split on top so neither DVEŘE nor ZBRAŇ ever clips!)
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

            HoldControlButton(
                label = "🔥 PALBA",
                bgColor = Color(0xFFD50000),
                fontSizeSp = 13,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 42.dp),
                onPressChanged = onFireHold
            )
        }
    }
}

@Composable
private fun HoldControlButton(
    label: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 10,
    onPressChanged: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(if (isPressed) Color(0xFFFF6D00) else bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF78909C), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressChanged(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                            onPressChanged(false)
                        }
                    }
                )
            }
            .padding(horizontal = 3.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 3).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun IntermissionScreen(
    stats: LevelSummaryStats,
    difficulty: Difficulty,
    onNextLevel: () -> Unit,
    onBackToMenu: () -> Unit
) {
    val compactBtnPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    val killPct = if (stats.totalMonsters > 0) (stats.killsCount * 100) / stats.totalMonsters else 100
    val itemPct = if (stats.totalItems > 0) (stats.itemsCount * 100) / stats.totalItems else 100
    val secretPct = if (stats.totalSecrets > 0) (stats.secretsCount * 100) / stats.totalSecrets else 100

    var savedNick by remember { mutableStateOf(FirebaseLeaderboard.getSavedNick()) }
    var nickInput by remember { mutableStateOf(savedNick) }
    var saveStatus by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (stats.isEpisodeCompleted) "🏆 EPIZODA DOKONČENA! 🏆" else "${stats.mapCode} DOKONČENO!",
            color = Color(0xFFFF2A00),
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stats.mapTitle,
            color = Color(0xFFFFD600),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (stats.isSecretNext) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "★ ODHALEN TAJNÝ VÝCHOD DO VOJENSKÉ ZÁKLADNY E1M9! ★",
                color = Color(0xFFE040FB),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1E24), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF455A64), RoundedCornerShape(10.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatRow("ZABITÍ MONSTER (KILLS):", "$killPct % (${stats.killsCount}/${stats.totalMonsters})")
            StatRow("SEBRANÉ PŘEDMĚTY (ITEMS):", "$itemPct % (${stats.itemsCount}/${stats.totalItems})")
            StatRow("TAJNÉ SKRÝŠE (SECRETS):", "$secretPct % (${stats.secretsCount}/${stats.totalSecrets})")
            StatRow("ČAS MAPY / PAR TIME:", "${FirebaseLeaderboard.formatTimeMmSs(stats.timeSeconds)} / ${FirebaseLeaderboard.formatTimeMmSs(stats.parTimeSeconds)}")
            StatRow("CELKOVÉ SKÓRE:", "${stats.totalScore} bodů")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (savedNick.isEmpty()) {
            OutlinedTextField(
                value = nickInput,
                onValueChange = { nickInput = it },
                label = { Text("Tvoje přezdívka pro online žebříček") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val useNick = if (savedNick.isNotEmpty()) savedNick else nickInput
                FirebaseLeaderboard.submitScoreAndTime(
                    difficultyId = difficulty.id,
                    nick = useNick,
                    score = stats.totalScore,
                    timeSeconds = stats.timeSeconds
                ) { ok, msg ->
                    saveStatus = msg
                    if (ok) savedNick = FirebaseLeaderboard.getSavedNick()
                }
            },
            contentPadding = compactBtnPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("☁ Uložit výsledek do online žebříčku", fontWeight = FontWeight.Bold)
        }

        if (saveStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = saveStatus, color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onNextLevel,
            contentPadding = compactBtnPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
        ) {
            val nextLabel = if (stats.isEpisodeCompleted) {
                "🏆 ZOBRAZIT ONLINE ŽEBŘÍČEK VÍTĚZŮ"
            } else {
                val nextMap = DoomMaps.allMaps[stats.nextMapIndex]
                "▶ POKRAČOVAT DO ${nextMap.code}"
            }
            Text(nextLabel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onBackToMenu,
            contentPadding = compactBtnPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
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
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1E24), RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFFFFAB00), RoundedCornerShape(12.dp))
                .clickable(enabled = false) { }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⌨ TERMINÁL CHEATŮ DOOM I",
                color = Color(0xFFFFD600),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = feedback, color = Color(0xFF00E676), fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))

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
                label = { Text("Vlastní cheat (např. IDCLEV11 až IDCLEV19)", fontSize = 12.sp) },
                singleLine = true,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
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
    lastScore: Int,
    lastTimeSec: Int,
    onBack: () -> Unit
) {
    val compactBtnPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    var selectedDiff by remember { mutableStateOf(initialDifficulty) }
    var showBestTime by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(emptyList<LeaderboardEntry>()) }
    var loading by remember { mutableStateOf(true) }

    var savedNick by remember { mutableStateOf(FirebaseLeaderboard.getSavedNick()) }
    var nickInput by remember { mutableStateOf(savedNick) }
    var statusMsg by remember { mutableStateOf("") }

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

    LaunchedEffect(selectedDiff, showBestTime) {
        reload()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🏆 ONLINE ŽEBŘÍČEK REKORDŮ",
            color = Color(0xFFFFD600),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(8.dp))

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
                            if (active) Color(0xFFD50000) else Color(0xFF263238),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedDiff = d }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "${d.ordinal + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = selectedDiff.titleCz, color = Color(0xFFFFCC80), fontSize = 12.sp)

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showBestTime = false },
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!showBestTime) Color(0xFF1E88E5) else Color(0xFF37474F)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("🎯 Nejvyšší skóre", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
            Button(
                onClick = { showBestTime = true },
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showBestTime) Color(0xFF1E88E5) else Color(0xFF37474F)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("⏱ Nejrychlejší čas", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }

        if (lastScore > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            if (savedNick.isEmpty()) {
                OutlinedTextField(
                    value = nickInput,
                    onValueChange = { nickInput = it },
                    label = { Text("Zadej přezdívku") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = {
                    val useNick = if (savedNick.isNotEmpty()) savedNick else nickInput
                    FirebaseLeaderboard.submitScoreAndTime(
                        difficultyId = selectedDiff.id,
                        nick = useNick,
                        score = lastScore,
                        timeSeconds = lastTimeSec
                    ) { ok, msg ->
                        statusMsg = msg
                        if (ok) {
                            savedNick = FirebaseLeaderboard.getSavedNick()
                            reload()
                        }
                    }
                },
                contentPadding = compactBtnPadding,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("☁ Zapsat poslední skóre ($lastScore b.)", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (statusMsg.isNotBlank()) {
                Text(text = statusMsg, color = Color(0xFF00E676), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF16191F), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF37474F), RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (loading) {
                Text("Načítám online výsledky...", color = Color(0xFFB0BEC5))
            } else if (entries.isEmpty()) {
                Text("Zatím zde nejsou žádné záznamy. Buď první!", color = Color(0xFFB0BEC5))
            } else {
                entries.forEachIndexed { idx, item ->
                    val valFormatted = if (showBestTime) {
                        FirebaseLeaderboard.formatTimeMmSs(item.value)
                    } else {
                        "${item.value} b."
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222730), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${idx + 1}. ${item.nick}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = valFormatted,
                            color = Color(0xFFFFD600),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onBack,
            contentPadding = compactBtnPadding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zpět do menu")
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
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        val sections = listOf(
            "Ovládání a boj" to "Levým křížem chodíš a děláš úkroky (strafe), tažením prstu po 3D obrazovce se plynule rozhlížíš. Tlačítkem DVEŘE (nebo dvojitým ťuknutím na 3D obrazovku) otevíráš dveře, přepínáš spínače EXIT a odhaluješ tajné posuvné stěny.",
            "Jak najít tajné skrýše (Secrets)" to "V každé z 9 map jsou tajné posuvné zdi (označené jemnou prasklinou a svislou spárou uprostřed panelu) a skryté sektory. Postav se ke stěně a stiskni DVEŘE. Po otevření uslyšíš signál a započítá se ti nalezené tajemství!",
            "Tajný východ do E1M9 (Military Base)" to "V mapě E1M3 (Toxin Refinery) jsou dva východy: klasický červeno-zelený spínač EXIT vede do E1M4, zatímco tajný fialovo-zlatý spínač EXIT vpravo nahoře za tajnou stěnou tě přenese do bonusové mapy E1M9!",
            "Monster Infighting & Sudy" to "Pokud výstřel jednoho monstra zasáhne jiný druh démona, pustí se do boje mezi sebou. Výbušné sudy s radioaktivním slizem po rozstřelení způsobí řetězovou tlakovou vlnu.",
            "Klasické cheaty z klávesnice" to "IDDQD (Nesmrtelnost), IDKFA (Všechny zbraně, 200 % zbroj a klíče), IDFA (Zbraně a munice bez klíčů), IDSPISPOPD (Procházení zdmi), IDDT (Odhalení celé automapy a skrýší), IDCLEV11 až IDCLEV19 (Okamžitý skok do mapy)."
        )

        for (s in sections) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .background(Color(0xFF1A1E24), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF37474F), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(text = s.first, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = s.second, color = Color(0xFFECEFF1), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zpět do hlavního menu", fontWeight = FontWeight.Bold)
        }
    }
}
