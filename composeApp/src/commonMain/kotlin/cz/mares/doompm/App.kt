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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    MaterialTheme {
        val engine = remember { DoomEngine() }
        var currentScreen by remember { mutableStateOf(AppScreen.MENU) }
        var selectedDifficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
        var selectedStartMap by remember { mutableIntStateOf(0) }

        var frameTick by remember { mutableIntStateOf(0) }
        var showAutomap by remember { mutableStateOf(false) }
        var showCheatDialog by remember { mutableStateOf(false) }

        // Continuous touch inputs
        var moveForwardInput by remember { mutableFloatStateOf(0f) }
        var moveStrafeInput by remember { mutableFloatStateOf(0f) }
        var turnRateInput by remember { mutableFloatStateOf(0f) }
        var dragTurnAccum by remember { mutableFloatStateOf(0f) }
        var isFiringHeld by remember { mutableStateOf(false) }

        var soundOn by remember { mutableStateOf(true) }
        var musicOn by remember { mutableStateOf(true) }

        // 60 FPS Game Loop
        LaunchedEffect(currentScreen) {
            var lastNanos = 0L
            while (currentScreen == AppScreen.PLAYING) {
                withFrameNanos { nowNanos ->
                    val dt = if (lastNanos == 0L) 0.016f else ((nowNanos - lastNanos) / 1_000_000_000f)
                    lastNanos = nowNanos
                    val turnDelta = turnRateInput * dt * 2.4f + dragTurnAccum
                    dragTurnAccum = 0f

                    engine.update(
                        dt = dt,
                        moveForward = moveForwardInput,
                        moveStrafe = moveStrafeInput,
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
                            onBackToMenu = { currentScreen = AppScreen.MENU }
                        )
                    } else {
                        GamePlayScreen(
                            engine = engine,
                            frameTick = frameTick,
                            showAutomap = showAutomap,
                            onToggleAutomap = { showAutomap = !showAutomap },
                            onOpenCheats = { showCheatDialog = true },
                            onBackToMenu = {
                                moveForwardInput = 0f
                                moveStrafeInput = 0f
                                turnRateInput = 0f
                                isFiringHeld = false
                                currentScreen = AppScreen.MENU
                            },
                            onMoveInput = { fwd, strafe, turn ->
                                moveForwardInput = fwd
                                moveStrafeInput = strafe
                                turnRateInput = turn
                            },
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DOOM I",
            color = Color(0xFFFF2A00),
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Knee-Deep in the Dead (3D / 2.5D Edition)",
            color = Color(0xFFFFCC00),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Start Map selector (E1M1 - E1M9)
        Text(
            text = "VYBER POČÁTEČNÍ MAPU EPIZODY 1:",
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp,
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
            ) {
                Text("◀")
            }
            Text(
                text = "${curMap.code}: ${curMap.title}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
            ) {
                Text("▶")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "ZVOL OBTÍŽNOST (SKILL LEVEL):",
            color = Color(0xFFB0BEC5),
            fontSize = 13.sp,
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
                        fontSize = 14.sp
                    )
                    Text(
                        text = diff.subtitleOrig,
                        color = Color(0xFFFFCC80),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onStartGame,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "🔥 SPUSTIT HRU DOOM I 🔥",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenLeaderboard,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                modifier = Modifier.weight(1f)
            ) {
                Text("🏆 Online rekordy", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onOpenGuide,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                modifier = Modifier.weight(1f)
            ) {
                Text("🕵️ Skrýše & Cheaty", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggleSound,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (soundOn) Color(0xFF37474F) else Color(0xFF263238)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (soundOn) "🔊 Zvuky: ZAP" else "🔇 Zvuky: VYP", fontSize = 13.sp)
            }
            Button(
                onClick = onToggleMusic,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (musicOn) Color(0xFF37474F) else Color(0xFF263238)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (musicOn) "🎸 Riff E1M1: ZAP" else "🎸 Riff E1M1: VYP", fontSize = 13.sp)
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
    onMoveInput: (Float, Float, Float) -> Unit,
    onDragTurn: (Float) -> Unit,
    onFireHold: (Boolean) -> Unit,
    onUseAction: () -> Unit
) {
    // Reference frameTick so Compose redraws every frame
    val tick = frameTick

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Top Tactical Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16191E))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${engine.currentMapDef.code} • Skóre: ${engine.totalScore}",
                    color = Color(0xFFFFD600),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Zabití ${engine.killsCount}/${engine.totalMonsters} • Tajemství ${engine.secretsCount}/${engine.totalSecrets} • Čas ${FirebaseLeaderboard.formatTimeMmSs(engine.levelTimeSec.toInt())}",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .background(
                            if (showAutomap) Color(0xFF00C853) else Color(0xFF37474F),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onToggleAutomap() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("🗺️ Mapa", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF6A1B9A), RoundedCornerShape(6.dp))
                        .clickable { onOpenCheats() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("⌨️ IDDQD", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF455A64), RoundedCornerShape(6.dp))
                        .clickable { onBackToMenu() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("⏸️ Menu", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Main 3D / 2.5D Viewport (with drag-to-turn and tap-to-shoot/use)
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

            // Status Banner message at top of 3D view
            if (engine.statusMessageTimer > 0f && engine.statusMessage.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color(0xCC121417), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFFFFAB00), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = engine.statusMessage,
                        color = Color(0xFFFFEA00),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Death Overlay
            if (engine.isPlayerDead) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xE6280606), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFFF1744), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "💀 ZEMŘEL JSI! 💀",
                        color = Color(0xFFFF5252),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { engine.loadMap(engine.currentMapIndex, resetInventory = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000))
                    ) {
                        Text("🔄 Zkusit mapu ${engine.currentMapDef.code} znovu", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Classic DOOM Grey Stone Status Bar (Ammo, Health, Marine Face, Armor, Keys, Weapons 1-7)
        ClassicDoomStatusBar(engine = engine, frameTick = tick)

        // 4. Touch Controls Bar (Movement D-Pad + Fire / Open Door / Switch Weapon)
        TouchControlsBar(
            onMoveInput = onMoveInput,
            onFireHold = onFireHold,
            onUseAction = onUseAction,
            onCycleWeapon = { engine.cycleWeapon(next = true) }
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
            .background(Color(0xFF33373D))
            .border(1.dp, Color(0xFF546E7A))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // AMMO
            StatusBox(label = "MUNICE", value = curAmmoText, valueColor = Color(0xFFFF5252))
            // HEALTH
            StatusBox(label = "ZDRAVÍ", value = "${engine.playerHealth}%", valueColor = Color(0xFFFF5252))

            // DOOMGUY FACE CANVAS
            Canvas(modifier = Modifier.size(46.dp, 46.dp)) {
                if (frameTick >= 0) {
                    renderDoomguyFace(engine)
                }
            }

            // ARMOR
            StatusBox(label = "BRNĚNÍ", value = "${engine.playerArmor}%", valueColor = Color(0xFF00E676))

            // KEYS (Blue, Yellow, Red)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color(0xFF1E2227), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text("KLÍČE", color = Color(0xFFB0BEC5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyCard.entries.forEach { k ->
                        val has = engine.keys.contains(k)
                        Box(
                            modifier = Modifier
                                .size(11.dp, 15.dp)
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

        // Weapon Slots 1..7 Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (slot in 1..7) {
                val owned = WeaponType.entries.any { it.slot == slot && engine.ownedWeapons.contains(it) }
                val active = (engine.currentWeapon.slot == slot)
                val label = when (slot) {
                    1 -> if (engine.ownedWeapons.contains(WeaponType.CHAINSAW)) "1:Pila" else "1:Pěst"
                    2 -> "2:Pist"
                    3 -> "3:Brok"
                    4 -> "4:Kulom"
                    5 -> "5:Raket"
                    6 -> "6:Plaz"
                    else -> "7:BFG"
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
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
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = if (owned) Color.White else Color(0xFF546E7A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBox(label: String, value: String, valueColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF1E2227), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = label, color = Color(0xFFB0BEC5), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TouchControlsBar(
    onMoveInput: (Float, Float, Float) -> Unit,
    onFireHold: (Boolean) -> Unit,
    onUseAction: () -> Unit,
    onCycleWeapon: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121519))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Movement & Turn Pad
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HoldControlButton("↶ Otoč", Color(0xFF263238)) { pressed ->
                    onMoveInput(0f, 0f, if (pressed) -1f else 0f)
                }
                HoldControlButton("▲ Vpřed", Color(0xFF37474F)) { pressed ->
                    onMoveInput(if (pressed) 1f else 0f, 0f, 0f)
                }
                HoldControlButton("↷ Otoč", Color(0xFF263238)) { pressed ->
                    onMoveInput(0f, 0f, if (pressed) 1f else 0f)
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HoldControlButton("◀ Úkrok", Color(0xFF263238)) { pressed ->
                    onMoveInput(0f, if (pressed) -1f else 0f, 0f)
                }
                HoldControlButton("▼ Vzad", Color(0xFF37474F)) { pressed ->
                    onMoveInput(if (pressed) -1f else 0f, 0f, 0f)
                }
                HoldControlButton("Úkrok ▶", Color(0xFF263238)) { pressed ->
                    onMoveInput(0f, if (pressed) 1f else 0f, 0f)
                }
            }
        }

        // Right Combat & Action Cluster
        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(Color(0xFF1E88E5), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF90CAF9), RoundedCornerShape(8.dp))
                        .clickable { onUseAction() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text("🖐️ DVEŘE / AKCE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(Color(0xFF455A64), RoundedCornerShape(8.dp))
                        .clickable { onCycleWeapon() }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text("🔄 ZBRAŇ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            HoldControlButton(
                label = "🔥 PALBA (DRŽET / ŤUKNOUT) 🔥",
                bgColor = Color(0xFFD50000),
                minWidthDp = 175
            ) { pressed ->
                onFireHold(pressed)
            }
        }
    }
}

@Composable
private fun HoldControlButton(
    label: String,
    bgColor: Color,
    minWidthDp: Int = 64,
    onPressChanged: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(minWidthDp.dp)
            .height(38.dp)
            .background(if (isPressed) Color(0xFFFF6D00) else bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF78909C), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressChanged(true)
                        tryAwaitRelease()
                        isPressed = false
                        onPressChanged(false)
                    }
                )
            }
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
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
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = stats.mapTitle,
            color = Color(0xFFFFD600),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
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

        // Save to Online Leaderboard
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("☁️ Uložit výsledek do online žebříčku", fontWeight = FontWeight.Bold)
        }

        if (saveStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = saveStatus, color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onNextLevel,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            val nextLabel = if (stats.isEpisodeCompleted) {
                "🏆 ZOBRAZIT ONLINE ŽEBŘÍČEK VÍTĚZŮ"
            } else {
                val nextMap = DoomMaps.allMaps[stats.nextMapIndex]
                "▶ POKRAČOVAT DO ${nextMap.code}: ${nextMap.title}"
            }
            Text(nextLabel, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onBackToMenu,
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
        Text(text = label, color = Color(0xFFB0BEC5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = value, color = Color(0xFFFFEA00), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
private fun CheatTerminalDialog(
    engine: DoomEngine,
    onDismiss: () -> Unit
) {
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
                text = "⌨️ TERMINÁL CHEATŮ DOOM I",
                color = Color(0xFFFFD600),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = feedback, color = Color(0xFF00E676), fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))

            val quickCheats = listOf(
                "IDDQD" to "IDDQD (Nesmrtelnost)",
                "IDKFA" to "IDKFA (Zbraně + Klíče)",
                "IDFA" to "IDFA (Plný arzenál)",
                "IDSPISPOPD" to "IDSPISPOPD (Zdi)",
                "IDDT" to "IDDT (Celá mapa)",
                "IDCLEV19" to "IDCLEV19 (Tajná E1M9)"
            )

            for (row in quickCheats.chunked(2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (pair in row) {
                        Button(
                            onClick = { feedback = engine.applyCheatCode(pair.first) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(pair.second, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                label = { Text("Vlastní cheat (např. IDCLEV11 až IDCLEV19)") },
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("AKTIVOVAT", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDismiss,
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
    var selectedDiff by remember { mutableStateOf(initialDifficulty) }
    var showBestTime by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
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
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Difficulty tabs
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!showBestTime) Color(0xFF1E88E5) else Color(0xFF37474F)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("🎯 Nejvyšší skóre", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = { showBestTime = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showBestTime) Color(0xFF1E88E5) else Color(0xFF37474F)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("⏱️ Nejrychlejší čas", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("☁️ Zapsat poslední skóre ($lastScore b.)", fontWeight = FontWeight.Bold)
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
            text = "🕵️ PRŮVODCE SKRÝŠEMI A HERNÍ LOGIKOU DOOM I",
            color = Color(0xFFFFD600),
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(10.dp))

        val sections = listOf(
            "Ovládání a boj" to "Levým křížem chodíš a děláš úkroky (strafe), tažením prstu po 3D obrazovce se plynule rozhlížíš. Tlačítkem DVEŘE / AKCE (nebo dvojitým ťuknutím na obrazovku) otevíráš dveře, přepínáš spínače EXIT a odhaluješ tajné posuvné stěny.",
            "Jak najít tajné skrýše (Secrets)" to "V každé z 9 map jsou tajné posuvné zdi (označené jemným svislým švem na technické stěně) a skryté sektory. Postav se ke stěně a stiskni DVEŘE / AKCE. Po otevření uslyšíš signál a započítá se ti % Tajemství!",
            "Tajný východ do E1M9 (Military Base)" to "V mapě E1M3 (Toxin Refinery) jsou dva východy: klasický červeno-zelený spínač vede do E1M4, zatímco tajný fialovo-zlatý spínač vpravo nahoře za tajnou stěnou tě přenese do bonusové mapy E1M9!",
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zpět do hlavního menu", fontWeight = FontWeight.Bold)
        }
    }
}
