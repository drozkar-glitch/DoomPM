package cz.mares.doompm

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class DoomEngine {
    val gridSize = 20
    val grid = Array(gridSize) { IntArray(gridSize) }
    val explored = Array(gridSize) { BooleanArray(gridSize) }

    var currentMapIndex: Int = 0
    var currentMapDef: DoomMapDefinition = DoomMaps.allMaps[0]
    var difficulty: Difficulty = Difficulty.MEDIUM

    // Player state
    var playerX: Float = 2.5f
    var playerY: Float = 17.5f
    var playerAngle: Float = -1.57f
    var playerHealth: Int = 100
    var playerArmor: Int = 0
    var armorGrade: Int = 1 // 1 = Green (33%), 2 = Blue (50%)
    var hasBackpack: Boolean = false
    var hasBerserk: Boolean = false
    var hasComputerMap: Boolean = false

    // Power-up & status timers
    var invulnerableTimer: Float = 0f
    var radSuitTimer: Float = 0f
    var damageFlashTimer: Float = 0f
    var pickupFlashTimer: Float = 0f
    var teleportFlashTimer: Float = 0f
    var acidTickTimer: Float = 0f

    // Cheats
    var godModeCheat: Boolean = false
    var noClipCheat: Boolean = false
    var fullMapCheat: Boolean = false

    // Weapons & Ammo
    val ownedWeapons = mutableSetOf(WeaponType.FIST, WeaponType.PISTOL)
    var currentWeapon: WeaponType = WeaponType.PISTOL
    val ammo = mutableMapOf(
        AmmoType.NONE to 0,
        AmmoType.BULLETS to 50,
        AmmoType.SHELLS to 0,
        AmmoType.ROCKETS to 0,
        AmmoType.CELLS to 0
    )
    var weaponCooldown: Float = 0f
    var weaponFlashTimer: Float = 0f
    var walkBobPhase: Float = 0f
    var walkBobAmplitude: Float = 0f

    // Doomguy HUD Face
    var faceLookDir: Int = 0 // -1 left, 0 center, 1 right
    var faceChangeTimer: Float = 1.2f
    var faceGrinTimer: Float = 0f
    var facePainTimer: Float = 0f

    // Keys
    val keys = mutableSetOf<KeyCard>()

    // Entities, Doors & Connected Secret Groups
    val doors = mutableMapOf<Int, DoorState>() // key = y * gridSize + x
    val cellToSecretGroup = mutableMapOf<Int, Int>() // key = y * gridSize + x -> groupId
    val discoveredSecretGroups = mutableSetOf<Int>()
    val monsters = mutableListOf<MonsterEntity>()
    val items = mutableListOf<MapItemEntity>()
    val projectiles = mutableListOf<ProjectileEntity>()

    // Level & Campaign statistics
    var killsCount: Int = 0
    var totalMonsters: Int = 0
    var itemsCount: Int = 0
    var totalItems: Int = 0
    var secretsCount: Int = 0
    var totalSecrets: Int = 0
    var levelTimeSec: Float = 0f
    var campaignTimeSec: Float = 0f
    var totalScore: Int = 0

    // HUD Banner message
    var statusMessage: String = ""
    var statusMessageTimer: Float = 0f

    // Outcomes
    var isPlayerDead: Boolean = false
    var completedLevelStats: LevelSummaryStats? = null

    fun startNewCampaign(diff: Difficulty, startMapIdx: Int = 0) {
        difficulty = diff
        playerHealth = 100
        playerArmor = 0
        armorGrade = 1
        hasBackpack = false
        ownedWeapons.clear()
        ownedWeapons.add(WeaponType.FIST)
        ownedWeapons.add(WeaponType.PISTOL)
        currentWeapon = WeaponType.PISTOL
        ammo[AmmoType.BULLETS] = 50
        ammo[AmmoType.SHELLS] = 0
        ammo[AmmoType.ROCKETS] = 0
        ammo[AmmoType.CELLS] = 0
        campaignTimeSec = 0f
        totalScore = 0
        godModeCheat = false
        noClipCheat = false
        fullMapCheat = false
        loadMap(startMapIdx, resetInventory = false)
    }

    fun loadMap(mapIndex: Int, resetInventory: Boolean = false) {
        val safeIdx = mapIndex.coerceIn(0, DoomMaps.allMaps.lastIndex)
        currentMapIndex = safeIdx
        val def = DoomMaps.allMaps[safeIdx]
        currentMapDef = def

        if (resetInventory) {
            playerHealth = 100
            playerArmor = 0
            ownedWeapons.clear()
            ownedWeapons.add(WeaponType.FIST)
            ownedWeapons.add(WeaponType.PISTOL)
            currentWeapon = WeaponType.PISTOL
            ammo[AmmoType.BULLETS] = 50
            ammo[AmmoType.SHELLS] = 0
            ammo[AmmoType.ROCKETS] = 0
            ammo[AmmoType.CELLS] = 0
        } else if (playerHealth <= 0) {
            playerHealth = 100
        }

        playerX = def.startX
        playerY = def.startY
        playerAngle = def.startAngleRad

        keys.clear()
        hasBerserk = false
        hasComputerMap = false
        invulnerableTimer = 0f
        radSuitTimer = 0f
        damageFlashTimer = 0f
        pickupFlashTimer = 0f
        teleportFlashTimer = 0f
        isPlayerDead = false
        completedLevelStats = null

        doors.clear()
        cellToSecretGroup.clear()
        discoveredSecretGroups.clear()
        monsters.clear()
        items.clear()
        projectiles.clear()

        killsCount = 0
        itemsCount = 0
        secretsCount = 0
        totalSecrets = 0
        levelTimeSec = 0f

        val rawSecretCells = mutableListOf<Pair<Int, Int>>()

        for (y in 0 until gridSize) {
            val rowStr = def.rawAsciiRows.getOrNull(y) ?: "####################"
            for (x in 0 until gridSize) {
                explored[y][x] = false
                val ch = if (x < rowStr.length) rowStr[x] else '#'
                val t = DoomMaps.charToTile(ch)
                grid[y][x] = t
                val cellKey = y * gridSize + x
                if (t in 6..10) {
                    val isSec = (t == 10)
                    doors[cellKey] = DoorState(x, y, t, isSec)
                    if (isSec) rawSecretCells.add(x to y)
                } else if (t == 14) {
                    rawSecretCells.add(x to y)
                }
            }
        }

        // Group connected secret push-walls ('S' = 10) and secret floor alcoves ('?' = 14) into single secrets
        val visited = BooleanArray(rawSecretCells.size)
        var groupCounter = 0
        for (i in rawSecretCells.indices) {
            if (visited[i]) continue
            val queue = ArrayDeque<Int>()
            queue.add(i)
            visited[i] = true
            while (queue.isNotEmpty()) {
                val curIdx = queue.removeFirst()
                val (cx, cy) = rawSecretCells[curIdx]
                cellToSecretGroup[cy * gridSize + cx] = groupCounter
                for (j in rawSecretCells.indices) {
                    if (!visited[j]) {
                        val (nx, ny) = rawSecretCells[j]
                        if (abs(cx - nx) + abs(cy - ny) <= 2) {
                            visited[j] = true
                            queue.add(j)
                        }
                    }
                }
            }
            groupCounter++
        }
        totalSecrets = groupCounter

        var mId = 1
        for (mDef in def.monsters) {
            if (mDef.hardOnly && difficulty.ordinal < Difficulty.HARD.ordinal) continue
            monsters.add(
                MonsterEntity(
                    id = mId++,
                    type = mDef.type,
                    spawnX = mDef.x,
                    spawnY = mDef.y,
                    x = mDef.x,
                    y = mDef.y,
                    hp = mDef.type.maxHp
                )
            )
        }
        totalMonsters = monsters.size

        var iId = 1
        var countStatItems = 0
        for (iDef in def.items) {
            items.add(MapItemEntity(id = iId++, type = iDef.type, x = iDef.x, y = iDef.y))
            if (iDef.type.countsForItemStat) countStatItems++
        }
        totalItems = countStatItems

        markExploredAroundPlayer()
        showBanner("${def.code}: ${def.title}")
    }

    private fun markSecretGroupDiscovered(groupId: Int) {
        if (!discoveredSecretGroups.add(groupId)) return
        secretsCount++
        totalScore += 500
        pickupFlashTimer = 0.35f
        for ((cellKey, gId) in cellToSecretGroup) {
            if (gId == groupId) {
                val gx = cellKey % gridSize
                val gy = cellKey / gridSize
                if (grid[gy][gx] == 14) {
                    grid[gy][gx] = 0
                }
                doors[cellKey]?.secretDiscovered = true
            }
        }
        showBanner("Byla objevena tajná skrýš! ($secretsCount/$totalSecrets)")
        DoomAudioManager.playSound(DoomSoundEffect.SECRET_FOUND)
        PlatformServices.triggerHaptic(heavy = true)
    }

    fun isSecretCellRevealedOnMap(gx: Int, gy: Int): Boolean {
        if (hasComputerMap || fullMapCheat) return true
        val gId = cellToSecretGroup[gy * gridSize + gx] ?: return false
        return discoveredSecretGroups.contains(gId)
    }

    fun showBanner(msg: String) {
        statusMessage = msg
        statusMessageTimer = 3.2f
    }

    fun maxAmmoFor(type: AmmoType): Int {
        return if (hasBackpack) type.baseMax * 2 else type.baseMax
    }

    fun addAmmo(type: AmmoType, baseAmount: Int): Boolean {
        if (type == AmmoType.NONE) return false
        val max = maxAmmoFor(type)
        val cur = ammo[type] ?: 0
        if (cur >= max) return false
        val scaled = (baseAmount * difficulty.ammoPickupMult).toInt().coerceAtLeast(1)
        ammo[type] = (cur + scaled).coerceAtMost(max)
        return true
    }

    fun applyCheatCode(rawCode: String): String {
        val code = rawCode.trim().uppercase()
        return when {
            code == "IDDQD" -> {
                godModeCheat = !godModeCheat
                if (godModeCheat) playerHealth = 100
                val msg = if (godModeCheat) "IDDQD: Režim Boha ZAPNUT (100 % zdraví)!" else "IDDQD: Režim Boha VYPNUT."
                showBanner(msg)
                DoomAudioManager.playSound(DoomSoundEffect.SECRET_FOUND)
                msg
            }
            code == "IDKFA" || code == "IDFA" -> {
                WeaponType.entries.forEach { ownedWeapons.add(it) }
                hasBackpack = true
                AmmoType.entries.forEach { if (it != AmmoType.NONE) ammo[it] = maxAmmoFor(it) }
                playerArmor = 200
                armorGrade = 2
                if (code == "IDKFA") {
                    keys.addAll(KeyCard.entries)
                }
                val msg = if (code == "IDKFA") {
                    "IDKFA: Všechny zbraně, plná munice, 200 % brnění a všechny klíče!"
                } else {
                    "IDFA: Všechny zbraně, plná munice a 200 % brnění!"
                }
                showBanner(msg)
                DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                msg
            }
            code == "IDSPISPOPD" || code == "IDCLIP" -> {
                noClipCheat = !noClipCheat
                val msg = if (noClipCheat) "IDSPISPOPD: Procházení zdmi ZAPNUTO!" else "IDSPISPOPD: Procházení zdmi VYPNUTO."
                showBanner(msg)
                DoomAudioManager.playSound(DoomSoundEffect.TELEPORT)
                msg
            }
            code == "IDDT" -> {
                fullMapCheat = !fullMapCheat
                hasComputerMap = true
                val msg = "IDDT: Kompletní automapa a skrýše odhaleny!"
                showBanner(msg)
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                msg
            }
            code == "IDBERSERK" -> {
                hasBerserk = true
                if (playerHealth < 100) playerHealth = 100
                currentWeapon = WeaponType.FIST
                val msg = "IDBERSERK: Berserk aktivován!"
                showBanner(msg)
                msg
            }
            code.startsWith("IDCLEV1") && code.length == 8 -> {
                val m = code[7].digitToIntOrNull()
                if (m != null && m in 1..9) {
                    loadMap(m - 1, resetInventory = false)
                    DoomAudioManager.playSound(DoomSoundEffect.TELEPORT)
                    "IDCLEV1$m: Přesun do mapy ${currentMapDef.code}!"
                } else {
                    "Neplatné číslo mapy (použij IDCLEV11 až IDCLEV19)."
                }
            }
            else -> "Neznámý cheat. Zkus IDDQD, IDKFA, IDFA, IDSPISPOPD, IDDT nebo IDCLEV11-19."
        }
    }

    fun selectWeaponSlot(slot: Int) {
        if (isPlayerDead) return
        val candidates = WeaponType.entries.filter { it.slot == slot && ownedWeapons.contains(it) }
        if (candidates.isEmpty()) return
        currentWeapon = if (candidates.size > 1 && currentWeapon == candidates.last()) {
            candidates.first()
        } else {
            candidates.last()
        }
        DoomAudioManager.playSound(DoomSoundEffect.SWITCH_CLICK)
    }

    fun cycleWeapon(next: Boolean) {
        if (isPlayerDead) return
        val ordered = WeaponType.entries.filter { ownedWeapons.contains(it) }
        if (ordered.size <= 1) return
        val idx = ordered.indexOf(currentWeapon).coerceAtLeast(0)
        val nextIdx = if (next) (idx + 1) % ordered.size else (idx - 1 + ordered.size) % ordered.size
        currentWeapon = ordered[nextIdx]
        DoomAudioManager.playSound(DoomSoundEffect.SWITCH_CLICK)
    }

    fun update(
        dt: Float,
        moveForward: Float,
        moveStrafe: Float,
        turnDelta: Float,
        isFiring: Boolean
    ) {
        if (completedLevelStats != null) return

        val clampedDt = dt.coerceIn(0.001f, 0.05f)
        if (!isPlayerDead) {
            levelTimeSec += clampedDt
            campaignTimeSec += clampedDt
        }

        if (statusMessageTimer > 0f) statusMessageTimer = (statusMessageTimer - clampedDt).coerceAtLeast(0f)
        if (invulnerableTimer > 0f) invulnerableTimer = (invulnerableTimer - clampedDt).coerceAtLeast(0f)
        if (radSuitTimer > 0f) radSuitTimer = (radSuitTimer - clampedDt).coerceAtLeast(0f)
        if (damageFlashTimer > 0f) damageFlashTimer = (damageFlashTimer - clampedDt).coerceAtLeast(0f)
        if (pickupFlashTimer > 0f) pickupFlashTimer = (pickupFlashTimer - clampedDt).coerceAtLeast(0f)
        if (teleportFlashTimer > 0f) teleportFlashTimer = (teleportFlashTimer - clampedDt).coerceAtLeast(0f)
        if (weaponCooldown > 0f) weaponCooldown = (weaponCooldown - clampedDt).coerceAtLeast(0f)
        if (weaponFlashTimer > 0f) weaponFlashTimer = (weaponFlashTimer - clampedDt).coerceAtLeast(0f)
        if (faceGrinTimer > 0f) faceGrinTimer = (faceGrinTimer - clampedDt).coerceAtLeast(0f)
        if (facePainTimer > 0f) facePainTimer = (facePainTimer - clampedDt).coerceAtLeast(0f)

        faceChangeTimer -= clampedDt
        if (faceChangeTimer <= 0f) {
            faceChangeTimer = 0.85f + Random.nextFloat() * 1.1f
            faceLookDir = Random.nextInt(-1, 2)
        }

        if (!isPlayerDead) {
            playerAngle += turnDelta
            while (playerAngle > PI.toFloat()) playerAngle -= (2.0 * PI).toFloat()
            while (playerAngle < -PI.toFloat()) playerAngle += (2.0 * PI).toFloat()

            val moveSpeed = 4.2f
            val dirX = cos(playerAngle)
            val dirY = sin(playerAngle)
            val strafeX = -dirY
            val strafeY = dirX

            val vx = (dirX * moveForward + strafeX * moveStrafe) * moveSpeed
            val vy = (dirY * moveForward + strafeY * moveStrafe) * moveSpeed

            val speedMag = hypot(vx, vy)
            if (speedMag > 0.15f) {
                walkBobPhase += clampedDt * 10.5f
                walkBobAmplitude = (walkBobAmplitude + clampedDt * 4f).coerceAtMost(1f)
                tryMovePlayer(vx * clampedDt, vy * clampedDt)
                markExploredAroundPlayer()
                checkFloorSpecials(clampedDt)
                checkItemPickups()
            } else {
                walkBobAmplitude = (walkBobAmplitude - clampedDt * 6f).coerceAtLeast(0f)
                checkFloorSpecials(clampedDt)
            }

            if (isFiring && weaponCooldown <= 0f) {
                fireCurrentWeapon()
            }
        }

        updateDoors(clampedDt)
        updateProjectiles(clampedDt)
        updateBarrels(clampedDt)
        updateMonsters(clampedDt)
    }

    private fun tryMovePlayer(dx: Float, dy: Float) {
        val radius = 0.28f
        val nextX = playerX + dx
        val nextY = playerY + dy

        if (noClipCheat) {
            playerX = nextX.coerceIn(1.2f, gridSize - 1.2f)
            playerY = nextY.coerceIn(1.2f, gridSize - 1.2f)
            return
        }

        if (canWalkAt(nextX, playerY, radius, isPlayer = true)) {
            playerX = nextX
        }
        if (canWalkAt(playerX, nextY, radius, isPlayer = true)) {
            playerY = nextY
        }
    }

    fun isCellSolidForRay(gx: Int, gy: Int): Boolean {
        if (gx !in 0 until gridSize || gy !in 0 until gridSize) return true
        val t = grid[gy][gx]
        if (t in 1..5 || t == 11 || t == 12) return true
        if (t in 6..10) {
            val door = doors[gy * gridSize + gx]
            return door == null || door.openAmount < 0.85f
        }
        return false
    }

    fun canWalkAt(x: Float, y: Float, radius: Float, isPlayer: Boolean): Boolean {
        val minGx = (x - radius).toInt()
        val maxGx = (x + radius).toInt()
        val minGy = (y - radius).toInt()
        val maxGy = (y + radius).toInt()

        for (gy in minGy..maxGy) {
            for (gx in minGx..maxGx) {
                if (gx !in 0 until gridSize || gy !in 0 until gridSize) return false
                val t = grid[gy][gx]
                if (t in 1..5 || t == 11 || t == 12) return false
                if (t in 6..10) {
                    val door = doors[gy * gridSize + gx]
                    if (door == null || door.openAmount < 0.78f) return false
                }
            }
        }

        for (item in items) {
            if (!item.collected && item.type.isSolidObstacle && item.barrelHp > 0) {
                if (hypot(x - item.x, y - item.y) < radius + 0.26f) return false
            }
        }

        if (isPlayer) {
            for (m in monsters) {
                if (m.state != MonsterState.DYING && m.state != MonsterState.DEAD) {
                    if (hypot(x - m.x, y - m.y) < radius + m.type.radius * 0.85f) return false
                }
            }
        }
        return true
    }

    private fun markExploredAroundPlayer() {
        val px = playerX.toInt()
        val py = playerY.toInt()
        for (dy in -3..3) {
            for (dx in -3..3) {
                val gx = px + dx
                val gy = py + dy
                if (gx in 0 until gridSize && gy in 0 until gridSize) {
                    explored[gy][gx] = true
                }
            }
        }
    }

    private fun checkFloorSpecials(dt: Float) {
        val gx = playerX.toInt().coerceIn(0, gridSize - 1)
        val gy = playerY.toInt().coerceIn(0, gridSize - 1)
        val tile = grid[gy][gx]
        val cellKey = gy * gridSize + gx

        val secretGroup = cellToSecretGroup[cellKey]
        if (secretGroup != null && !discoveredSecretGroups.contains(secretGroup)) {
            markSecretGroupDiscovered(secretGroup)
        }

        if (tile == 15 && teleportFlashTimer <= 0f) {
            val pair = currentMapDef.teleporters.firstOrNull { it.fromX == gx && it.fromY == gy }
            if (pair != null) {
                playerX = pair.toX
                playerY = pair.toY
                teleportFlashTimer = 0.55f
                showBanner("Teleportace!")
                DoomAudioManager.playSound(DoomSoundEffect.TELEPORT)
                PlatformServices.triggerHaptic(heavy = true)
            }
        }

        if (tile == 13) {
            acidTickTimer += dt
            if (acidTickTimer >= 0.95f) {
                acidTickTimer = 0f
                if (radSuitTimer <= 0f && invulnerableTimer <= 0f && !godModeCheat) {
                    damagePlayer(5, "Kyselinový sliz ubírá zdraví!")
                }
            }
        } else {
            acidTickTimer = 0f
        }
    }

    private fun checkItemPickups() {
        for (item in items) {
            if (item.collected || item.type.isSolidObstacle) continue
            if (hypot(playerX - item.x, playerY - item.y) <= 0.62f) {
                val picked = applyItemEffect(item.type)
                if (picked) {
                    item.collected = true
                    if (item.type.countsForItemStat) itemsCount++
                    pickupFlashTimer = 0.22f
                    showBanner(item.type.titleCz)
                }
            }
        }
    }

    private fun applyItemEffect(type: ItemType): Boolean {
        return when (type) {
            ItemType.HEALTH_BONUS -> {
                playerHealth = (playerHealth + 2).coerceAtMost(200)
                totalScore += 25
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.ARMOR_BONUS -> {
                playerArmor = (playerArmor + 2).coerceAtMost(200)
                totalScore += 25
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.STIMPACK -> {
                if (playerHealth >= 100) return false
                playerHealth = (playerHealth + 10).coerceAtMost(100)
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.MEDIKIT -> {
                if (playerHealth >= 100) return false
                playerHealth = (playerHealth + 25).coerceAtMost(100)
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.SOUL_SPHERE -> {
                playerHealth = (playerHealth + 100).coerceAtMost(200)
                totalScore += 500
                DoomAudioManager.playSound(DoomSoundEffect.SECRET_FOUND)
                true
            }
            ItemType.BERSERK -> {
                if (playerHealth < 100) playerHealth = 100
                hasBerserk = true
                currentWeapon = WeaponType.FIST
                damageFlashTimer = 0.45f
                totalScore += 400
                DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                true
            }
            ItemType.GREEN_ARMOR -> {
                if (playerArmor >= 100) return false
                playerArmor = 100
                armorGrade = 1
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.BLUE_ARMOR -> {
                if (playerArmor >= 200) return false
                playerArmor = 200
                armorGrade = 2
                totalScore += 400
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.INVULNERABILITY -> {
                invulnerableTimer = 30f
                totalScore += 600
                DoomAudioManager.playSound(DoomSoundEffect.SECRET_FOUND)
                true
            }
            ItemType.RAD_SUIT -> {
                radSuitTimer = 60f
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.COMPUTER_MAP -> {
                hasComputerMap = true
                totalScore += 300
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.BACKPACK -> {
                hasBackpack = true
                addAmmo(AmmoType.BULLETS, 10)
                addAmmo(AmmoType.SHELLS, 4)
                addAmmo(AmmoType.ROCKETS, 1)
                addAmmo(AmmoType.CELLS, 20)
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.KEY_BLUE -> {
                keys.add(KeyCard.BLUE)
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.KEY_YELLOW -> {
                keys.add(KeyCard.YELLOW)
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.KEY_RED -> {
                keys.add(KeyCard.RED)
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.WEAPON_CHAINSAW -> {
                ownedWeapons.add(WeaponType.CHAINSAW)
                currentWeapon = WeaponType.CHAINSAW
                faceGrinTimer = 1.8f
                DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                true
            }
            ItemType.WEAPON_SHOTGUN -> {
                ownedWeapons.add(WeaponType.SHOTGUN)
                addAmmo(AmmoType.SHELLS, 8)
                currentWeapon = WeaponType.SHOTGUN
                faceGrinTimer = 1.8f
                DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                true
            }
            ItemType.WEAPON_CHAINGUN -> {
                ownedWeapons.add(WeaponType.CHAINGUN)
                addAmmo(AmmoType.BULLETS, 20)
                currentWeapon = WeaponType.CHAINGUN
                faceGrinTimer = 1.8f
                DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                true
            }
            ItemType.WEAPON_ROCKET -> {
                ownedWeapons.add(WeaponType.ROCKET_LAUNCHER)
                addAmmo(AmmoType.ROCKETS, 2)
                currentWeapon = WeaponType.ROCKET_LAUNCHER
                faceGrinTimer = 1.8f
                DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                true
            }
            ItemType.WEAPON_PLASMA -> {
                ownedWeapons.add(WeaponType.PLASMA_RIFLE)
                addAmmo(AmmoType.CELLS, 40)
                currentWeapon = WeaponType.PLASMA_RIFLE
                faceGrinTimer = 1.8f
                DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                true
            }
            ItemType.WEAPON_BFG -> {
                ownedWeapons.add(WeaponType.BFG_9000)
                addAmmo(AmmoType.CELLS, 40)
                currentWeapon = WeaponType.BFG_9000
                faceGrinTimer = 2.2f
                DoomAudioManager.playSound(DoomSoundEffect.WEAPON_PICKUP)
                true
            }
            ItemType.AMMO_CLIP -> {
                if (!addAmmo(AmmoType.BULLETS, 10)) return false
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.AMMO_BOX -> {
                if (!addAmmo(AmmoType.BULLETS, 50)) return false
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.SHELL_4 -> {
                if (!addAmmo(AmmoType.SHELLS, 4)) return false
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.SHELL_BOX -> {
                if (!addAmmo(AmmoType.SHELLS, 20)) return false
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.ROCKET_ONE -> {
                if (!addAmmo(AmmoType.ROCKETS, 1)) return false
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.ROCKET_BOX -> {
                if (!addAmmo(AmmoType.ROCKETS, 5)) return false
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.CELL_PACK -> {
                if (!addAmmo(AmmoType.CELLS, 20)) return false
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            ItemType.CELL_BULK -> {
                if (!addAmmo(AmmoType.CELLS, 100)) return false
                DoomAudioManager.playSound(DoomSoundEffect.ITEM_PICKUP)
                true
            }
            else -> false
        }
    }

    fun triggerUseAction() {
        if (isPlayerDead || completedLevelStats != null) return
        val dirX = cos(playerAngle)
        val dirY = sin(playerAngle)

        for (step in 1..3) {
            val dist = step * 0.6f
            val tx = (playerX + dirX * dist).toInt()
            val ty = (playerY + dirY * dist).toInt()
            if (tx !in 0 until gridSize || ty !in 0 until gridSize) continue

            val tile = grid[ty][tx]
            val cellKey = ty * gridSize + tx

            if (tile in 6..10) {
                val door = doors[cellKey] ?: continue
                when (tile) {
                    7 -> if (!keys.contains(KeyCard.BLUE)) {
                        showBanner("Potřebuješ Modrou klíčovou kartu!")
                        DoomAudioManager.playSound(DoomSoundEffect.PLAYER_PAIN)
                        return
                    }
                    8 -> if (!keys.contains(KeyCard.YELLOW)) {
                        showBanner("Potřebuješ Žlutou klíčovou kartu!")
                        DoomAudioManager.playSound(DoomSoundEffect.PLAYER_PAIN)
                        return
                    }
                    9 -> if (!keys.contains(KeyCard.RED)) {
                        showBanner("Potřebuješ Červenou klíčovou kartu!")
                        DoomAudioManager.playSound(DoomSoundEffect.PLAYER_PAIN)
                        return
                    }
                }

                val secretGroup = cellToSecretGroup[cellKey]
                if (door.isSecretPushWall && secretGroup != null && !discoveredSecretGroups.contains(secretGroup)) {
                    markSecretGroupDiscovered(secretGroup)
                } else {
                    DoomAudioManager.playSound(DoomSoundEffect.DOOR_OPEN)
                }

                door.opening = true
                door.closing = false
                door.holdOpenTimer = if (door.isSecretPushWall) 9999f else 4.5f
                return
            }

            if (tile == 11 || tile == 12) {
                DoomAudioManager.playSound(DoomSoundEffect.SWITCH_CLICK)
                PlatformServices.triggerHaptic(heavy = true)
                completeCurrentLevel(isSecretExit = (tile == 12))
                return
            }
        }
    }

    private fun completeCurrentLevel(isSecretExit: Boolean) {
        val nextIdx = if (isSecretExit) currentMapDef.secretNextIndex else currentMapDef.normalNextIndex
        val timeBonus = ((currentMapDef.parSeconds - levelTimeSec.toInt()).coerceAtLeast(0)) * 15
        val secretBonus = secretsCount * 250
        totalScore += 1000 + timeBonus + secretBonus

        completedLevelStats = LevelSummaryStats(
            mapCode = currentMapDef.code,
            mapTitle = currentMapDef.title,
            nextMapIndex = nextIdx,
            isSecretNext = isSecretExit,
            isEpisodeCompleted = (nextIdx < 0),
            killsCount = killsCount,
            totalMonsters = totalMonsters,
            itemsCount = itemsCount,
            totalItems = totalItems,
            secretsCount = secretsCount,
            totalSecrets = totalSecrets,
            timeSeconds = levelTimeSec.toInt().coerceAtLeast(1),
            parTimeSeconds = currentMapDef.parSeconds,
            totalScore = totalScore
        )
    }

    fun fireCurrentWeapon() {
        if (isPlayerDead || weaponCooldown > 0f) return
        val w = currentWeapon
        if (w.ammoType != AmmoType.NONE) {
            val curAmmo = ammo[w.ammoType] ?: 0
            if (curAmmo < w.ammoPerShot) {
                val fallback = WeaponType.entries.reversed().firstOrNull {
                    ownedWeapons.contains(it) && (it.ammoType == AmmoType.NONE || (ammo[it.ammoType] ?: 0) >= it.ammoPerShot)
                } ?: WeaponType.FIST
                currentWeapon = fallback
                showBanner("Došla munice! Přepnuto na: ${fallback.titleCz}")
                return
            }
            ammo[w.ammoType] = curAmmo - w.ammoPerShot
        }

        weaponCooldown = w.cooldownSec
        weaponFlashTimer = 0.14f
        PlatformServices.triggerHaptic(heavy = (w.slot >= 3))

        when (w) {
            WeaponType.FIST -> DoomAudioManager.playSound(DoomSoundEffect.PUNCH)
            WeaponType.CHAINSAW -> DoomAudioManager.playSound(DoomSoundEffect.CHAINSAW)
            WeaponType.PISTOL -> DoomAudioManager.playSound(DoomSoundEffect.PISTOL)
            WeaponType.SHOTGUN -> DoomAudioManager.playSound(DoomSoundEffect.SHOTGUN)
            WeaponType.CHAINGUN -> DoomAudioManager.playSound(DoomSoundEffect.CHAINGUN)
            WeaponType.ROCKET_LAUNCHER -> DoomAudioManager.playSound(DoomSoundEffect.ROCKET_LAUNCH)
            WeaponType.PLASMA_RIFLE -> DoomAudioManager.playSound(DoomSoundEffect.PLASMA)
            WeaponType.BFG_9000 -> DoomAudioManager.playSound(DoomSoundEffect.BFG_BLAST)
        }

        for (m in monsters) {
            if (m.state == MonsterState.IDLE && hypot(m.x - playerX, m.y - playerY) < 12f) {
                m.state = MonsterState.CHASE
            }
        }

        if (w.isProjectile) {
            val speed = when (w) {
                WeaponType.ROCKET_LAUNCHER -> 11.5f
                WeaponType.PLASMA_RIFLE -> 15.0f
                WeaponType.BFG_9000 -> 9.5f
                else -> 12.0f
            }
            val kind = when (w) {
                WeaponType.ROCKET_LAUNCHER -> ProjectileKind.ROCKET
                WeaponType.PLASMA_RIFLE -> ProjectileKind.PLASMA
                else -> ProjectileKind.BFG_ORB
            }
            val dmg = Random.nextInt(w.minDamage, w.maxDamage + 1)
            val splash = when (w) {
                WeaponType.ROCKET_LAUNCHER -> 2.4f
                WeaponType.BFG_9000 -> 4.5f
                else -> 0f
            }
            projectiles.add(
                ProjectileEntity(
                    x = playerX + cos(playerAngle) * 0.4f,
                    y = playerY + sin(playerAngle) * 0.4f,
                    vx = cos(playerAngle) * speed,
                    vy = sin(playerAngle) * speed,
                    kind = kind,
                    fromPlayer = true,
                    ownerMonsterId = null,
                    directDamage = dmg,
                    splashRadius = splash
                )
            )
            return
        }

        for (p in 0 until w.pellets) {
            val spread = if (w.pellets > 1) (Random.nextFloat() - 0.5f) * 0.16f else (Random.nextFloat() - 0.5f) * 0.03f
            val rayAngle = playerAngle + spread
            var dmg = Random.nextInt(w.minDamage, w.maxDamage + 1)
            if (w == WeaponType.FIST && hasBerserk) {
                dmg *= 10
            }
            performPlayerHitscan(rayAngle, w.range, dmg, pullPlayer = (w == WeaponType.CHAINSAW))
        }
    }

    private fun performPlayerHitscan(angle: Float, maxRange: Float, damage: Int, pullPlayer: Boolean) {
        val dirX = cos(angle)
        val dirY = sin(angle)
        val stepSize = 0.08f
        var dist = 0.2f

        while (dist <= maxRange) {
            val rx = playerX + dirX * dist
            val ry = playerY + dirY * dist
            val gx = rx.toInt()
            val gy = ry.toInt()

            if (isCellSolidForRay(gx, gy)) return

            for (item in items) {
                if (!item.collected && item.type.isShootableBarrel && item.barrelHp > 0) {
                    if (hypot(rx - item.x, ry - item.y) <= 0.35f) {
                        damageBarrel(item, damage)
                        return
                    }
                }
            }

            for (m in monsters) {
                if (m.state != MonsterState.DYING && m.state != MonsterState.DEAD) {
                    if (hypot(rx - m.x, ry - m.y) <= m.type.radius + 0.08f) {
                        damageMonster(m, damage, attackerMonsterId = null)
                        if (pullPlayer) {
                            tryMovePlayer(dirX * 0.09f, dirY * 0.09f)
                        }
                        return
                    }
                }
            }

            dist += stepSize
        }
    }

    private fun damageBarrel(barrel: MapItemEntity, dmg: Int) {
        if (barrel.barrelHp <= 0) return
        barrel.barrelHp -= dmg
        if (barrel.barrelHp <= 0) {
            barrel.explodeAnimTimer = 0.45f
            DoomAudioManager.playSound(DoomSoundEffect.EXPLOSION)
            PlatformServices.triggerHaptic(heavy = true)
            triggerSplashDamage(barrel.x, barrel.y, radius = 2.6f, maxDamage = 135, attackerMonsterId = null)
        }
    }

    private fun triggerSplashDamage(
        cx: Float,
        cy: Float,
        radius: Float,
        maxDamage: Int,
        attackerMonsterId: Int?
    ) {
        for (m in monsters) {
            if (m.state != MonsterState.DYING && m.state != MonsterState.DEAD) {
                if (m.type == MonsterType.CYBERDEMON) continue
                val d = hypot(m.x - cx, m.y - cy)
                if (d <= radius) {
                    val dmg = (maxDamage * (1f - d / radius)).toInt().coerceAtLeast(10)
                    damageMonster(m, dmg, attackerMonsterId)
                }
            }
        }
        for (item in items) {
            if (!item.collected && item.type.isShootableBarrel && item.barrelHp > 0) {
                val d = hypot(item.x - cx, item.y - cy)
                if (d in 0.05f..radius) {
                    val dmg = (maxDamage * (1f - d / radius)).toInt().coerceAtLeast(15)
                    damageBarrel(item, dmg)
                }
            }
        }
        val playerDist = hypot(playerX - cx, playerY - cy)
        if (playerDist <= radius) {
            val pDmg = (maxDamage * 0.6f * (1f - playerDist / radius)).toInt()
            if (pDmg > 0) {
                damagePlayer(pDmg, "Zásah tlakovou vlnou výbuchu!")
            }
        }
    }

    private fun triggerBfgConeRays() {
        for (m in monsters) {
            if (m.state == MonsterState.DYING || m.state == MonsterState.DEAD) continue
            val dx = m.x - playerX
            val dy = m.y - playerY
            val dist = hypot(dx, dy)
            if (dist <= 16f && hasLineOfSight(playerX, playerY, m.x, m.y)) {
                val angleToM = atan2(dy, dx)
                var diff = abs(angleToM - playerAngle)
                while (diff > PI.toFloat()) diff = abs(diff - (2.0 * PI).toFloat())
                if (diff <= 0.85f) {
                    damageMonster(m, Random.nextInt(180, 380), attackerMonsterId = null)
                }
            }
        }
    }

    private fun damageMonster(m: MonsterEntity, dmg: Int, attackerMonsterId: Int?) {
        if (m.state == MonsterState.DYING || m.state == MonsterState.DEAD) return
        m.hp -= dmg
        m.state = MonsterState.CHASE

        if (attackerMonsterId != null && attackerMonsterId != m.id) {
            val attacker = monsters.firstOrNull { it.id == attackerMonsterId }
            if (attacker != null && attacker.type != m.type && attacker.state != MonsterState.DEAD) {
                m.infightTargetMonsterId = attackerMonsterId
            }
        }

        if (m.hp <= 0) {
            m.hp = 0
            m.state = MonsterState.DYING
            m.stateTimer = 0.55f
            m.respawnTimer = if (difficulty.respawnMonsters) 18f else 0f
            killsCount++
            totalScore += m.type.scoreValue
            DoomAudioManager.playSound(DoomSoundEffect.MONSTER_DEATH)

            if (m.type == MonsterType.SHOTGUN_GUY) {
                items.add(MapItemEntity(id = 9000 + m.id, type = ItemType.SHELL_4, x = m.x, y = m.y))
            } else if (m.type == MonsterType.ZOMBIEMAN) {
                items.add(MapItemEntity(id = 9000 + m.id, type = ItemType.AMMO_CLIP, x = m.x, y = m.y))
            }

            if (currentMapDef.code == "E1M8") {
                val bossesAlive = monsters.any { it.type.isBoss && it.state != MonsterState.DYING && it.state != MonsterState.DEAD }
                if (!bossesAlive) {
                    doors.values.forEach {
                        it.opening = true
                        it.holdOpenTimer = 9999f
                    }
                    showBanner("Bossové padli! Brána k anomálii je otevřena!")
                }
            }
        } else {
            if (Random.nextFloat() < 0.45f) {
                m.state = MonsterState.PAIN
                m.stateTimer = 0.22f
                DoomAudioManager.playSound(DoomSoundEffect.MONSTER_PAIN)
            }
        }
    }

    fun damagePlayer(rawAmount: Int, reason: String) {
        if (isPlayerDead || godModeCheat || invulnerableTimer > 0f) return
        val scaled = (rawAmount * difficulty.damageTakenMult).toInt().coerceAtLeast(1)

        var healthLoss = scaled
        if (playerArmor > 0) {
            val absorbRatio = if (armorGrade == 2) 0.5f else 0.33f
            val absorbed = (scaled * absorbRatio).toInt().coerceAtMost(playerArmor)
            playerArmor -= absorbed
            healthLoss -= absorbed
        }

        playerHealth = (playerHealth - healthLoss).coerceAtLeast(0)
        damageFlashTimer = 0.38f
        facePainTimer = 0.65f
        PlatformServices.triggerHaptic(heavy = true)

        if (playerHealth <= 0) {
            isPlayerDead = true
            showBanner("Zemřel jsi! ($reason)")
            DoomAudioManager.playSound(DoomSoundEffect.PLAYER_DEATH)
        } else {
            DoomAudioManager.playSound(DoomSoundEffect.PLAYER_PAIN)
        }
    }

    private fun updateDoors(dt: Float) {
        for (door in doors.values) {
            if (door.opening) {
                door.openAmount = (door.openAmount + dt * 1.6f).coerceAtMost(1f)
                if (door.openAmount >= 1f) {
                    door.opening = false
                }
            } else if (door.openAmount >= 1f && !door.isSecretPushWall) {
                door.holdOpenTimer -= dt
                if (door.holdOpenTimer <= 0f) {
                    val px = playerX.toInt()
                    val py = playerY.toInt()
                    val blockedByPlayer = (px == door.gridX && py == door.gridY)
                    val blockedByMonster = monsters.any {
                        it.state != MonsterState.DEAD && it.x.toInt() == door.gridX && it.y.toInt() == door.gridY
                    }
                    if (!blockedByPlayer && !blockedByMonster) {
                        door.closing = true
                    }
                }
            } else if (door.closing) {
                door.openAmount = (door.openAmount - dt * 1.4f).coerceAtLeast(0f)
                if (door.openAmount <= 0f) {
                    door.closing = false
                }
            }
        }
    }

    private fun updateBarrels(dt: Float) {
        for (item in items) {
            if (item.type.isShootableBarrel && item.barrelHp <= 0 && !item.collected) {
                item.explodeAnimTimer -= dt
                if (item.explodeAnimTimer <= 0f) {
                    item.collected = true
                }
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (!p.active) {
                p.explodeTimer -= dt
                if (p.explodeTimer <= 0f) iter.remove()
                continue
            }

            p.x += p.vx * dt
            p.y += p.vy * dt

            val gx = p.x.toInt()
            val gy = p.y.toInt()

            if (isCellSolidForRay(gx, gy)) {
                detonateProjectile(p)
                continue
            }

            var hitBarrel = false
            for (item in items) {
                if (!item.collected && item.type.isShootableBarrel && item.barrelHp > 0) {
                    if (hypot(p.x - item.x, p.y - item.y) <= 0.35f) {
                        damageBarrel(item, p.directDamage)
                        detonateProjectile(p)
                        hitBarrel = true
                        break
                    }
                }
            }
            if (hitBarrel) continue

            var hitMonster = false
            for (m in monsters) {
                if (m.state == MonsterState.DYING || m.state == MonsterState.DEAD) continue
                if (!p.fromPlayer && p.ownerMonsterId == m.id) continue
                if (hypot(p.x - m.x, p.y - m.y) <= m.type.radius + 0.12f) {
                    damageMonster(m, p.directDamage, attackerMonsterId = p.ownerMonsterId)
                    detonateProjectile(p)
                    hitMonster = true
                    break
                }
            }
            if (hitMonster) continue

            if (!p.fromPlayer && hypot(p.x - playerX, p.y - playerY) <= 0.36f) {
                damagePlayer(p.directDamage, "Zásah projektilem démona!")
                detonateProjectile(p)
            }
        }
    }

    private fun detonateProjectile(p: ProjectileEntity) {
        p.active = false
        p.explodeTimer = 0.25f
        if (p.splashRadius > 0f) {
            DoomAudioManager.playSound(DoomSoundEffect.EXPLOSION)
            triggerSplashDamage(p.x, p.y, p.splashRadius, p.directDamage, p.ownerMonsterId)
        }
        if (p.kind == ProjectileKind.BFG_ORB && p.fromPlayer) {
            triggerBfgConeRays()
        }
    }

    private fun updateMonsters(dt: Float) {
        for (m in monsters) {
            m.animPhase += dt * 5f

            if (m.state == MonsterState.DEAD) {
                if (difficulty.respawnMonsters && m.respawnTimer > 0f) {
                    m.respawnTimer -= dt
                    if (m.respawnTimer <= 0f) {
                        m.x = m.spawnX
                        m.y = m.spawnY
                        m.hp = m.type.maxHp
                        m.state = MonsterState.CHASE
                        DoomAudioManager.playSound(DoomSoundEffect.TELEPORT)
                    }
                }
                continue
            }

            if (m.state == MonsterState.DYING) {
                m.stateTimer -= dt
                if (m.stateTimer <= 0f) {
                    m.state = MonsterState.DEAD
                }
                continue
            }

            if (m.state == MonsterState.PAIN) {
                m.stateTimer -= dt
                if (m.stateTimer <= 0f) {
                    m.state = MonsterState.CHASE
                }
                continue
            }

            val infightTarget = m.infightTargetMonsterId?.let { tid ->
                monsters.firstOrNull { it.id == tid && it.state != MonsterState.DYING && it.state != MonsterState.DEAD }
            }
            if (infightTarget == null) {
                m.infightTargetMonsterId = null
            }

            val targetX = infightTarget?.x ?: playerX
            val targetY = infightTarget?.y ?: playerY
            val distToTarget = hypot(targetX - m.x, targetY - m.y)
            val canSeeTarget = hasLineOfSight(m.x, m.y, targetX, targetY)

            if (m.state == MonsterState.IDLE) {
                if (canSeeTarget && distToTarget < 14f && !isPlayerDead) {
                    m.state = MonsterState.CHASE
                    DoomAudioManager.playSound(DoomSoundEffect.MONSTER_SIGHT)
                }
                continue
            }

            if (isPlayerDead && infightTarget == null) continue

            m.attackCooldown = (m.attackCooldown - dt).coerceAtLeast(0f)

            if (distToTarget <= 1.25f && m.type.meleeDamage > 0 && m.attackCooldown <= 0f) {
                m.state = MonsterState.ATTACK
                m.attackCooldown = 0.95f
                if (infightTarget != null) {
                    damageMonster(infightTarget, m.type.meleeDamage, attackerMonsterId = m.id)
                } else {
                    damagePlayer(m.type.meleeDamage, m.type.titleCz)
                }
                continue
            }

            if (canSeeTarget && distToTarget in 1.3f..13.5f && m.attackCooldown <= 0f &&
                (m.type.isHitscan || m.type.hasProjectile)
            ) {
                if (Random.nextFloat() < 0.45f) {
                    m.state = MonsterState.ATTACK
                    m.attackCooldown = if (m.type == MonsterType.CYBERDEMON) 0.75f else 1.35f
                    performMonsterRangedAttack(m, targetX, targetY, infightTarget)
                    continue
                }
            }

            m.state = MonsterState.CHASE
            val angle = atan2(targetY - m.y, targetX - m.x)
            val speed = m.type.speed * difficulty.monsterSpeedMult
            val stepX = cos(angle) * speed * dt
            val stepY = sin(angle) * speed * dt

            if (canWalkAt(m.x + stepX, m.y, m.type.radius, isPlayer = false)) {
                m.x += stepX
            }
            if (canWalkAt(m.x, m.y + stepY, m.type.radius, isPlayer = false)) {
                m.y += stepY
            }
        }
    }

    private fun performMonsterRangedAttack(
        m: MonsterEntity,
        targetX: Float,
        targetY: Float,
        infightTarget: MonsterEntity?
    ) {
        val baseAngle = atan2(targetY - m.y, targetX - m.x)
        if (m.type.isHitscan) {
            DoomAudioManager.playSound(
                if (m.type == MonsterType.SHOTGUN_GUY) DoomSoundEffect.SHOTGUN else DoomSoundEffect.PISTOL
            )
            val hitChance = 0.72f
            if (Random.nextFloat() <= hitChance) {
                val dmg = Random.nextInt(m.type.rangedDamage / 2, m.type.rangedDamage + 4)
                if (infightTarget != null) {
                    damageMonster(infightTarget, dmg, attackerMonsterId = m.id)
                } else {
                    damagePlayer(dmg, m.type.titleCz)
                }
            }
        } else if (m.type.hasProjectile) {
            val projKind = when (m.type) {
                MonsterType.IMP -> ProjectileKind.IMP_FIREBALL
                MonsterType.CACODEMON -> ProjectileKind.CACO_LIGHTNING
                MonsterType.BARON_OF_HELL -> ProjectileKind.BARON_PLASMA
                else -> ProjectileKind.ROCKET
            }
            val speed = if (projKind == ProjectileKind.ROCKET) 9.5f else 7.2f
            val splash = if (projKind == ProjectileKind.ROCKET) 2.0f else 0f
            projectiles.add(
                ProjectileEntity(
                    x = m.x + cos(baseAngle) * 0.4f,
                    y = m.y + sin(baseAngle) * 0.4f,
                    vx = cos(baseAngle) * speed,
                    vy = sin(baseAngle) * speed,
                    kind = projKind,
                    fromPlayer = false,
                    ownerMonsterId = m.id,
                    directDamage = m.type.rangedDamage,
                    splashRadius = splash
                )
            )
        }
    }

    fun hasLineOfSight(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = hypot(dx, dy)
        if (dist <= 0.2f) return true
        val steps = (dist / 0.18f).toInt().coerceAtLeast(1)
        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val cx = (x0 + dx * t).toInt()
            val cy = (y0 + dy * t).toInt()
            if (isCellSolidForRay(cx, cy)) return false
        }
        return true
    }
}
