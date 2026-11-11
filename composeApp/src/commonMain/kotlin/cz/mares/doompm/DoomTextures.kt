package cz.mares.doompm

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

object DoomTextures {
    const val TEX_SIZE = 64
    const val WEAPON_W = 128
    const val WEAPON_H = 96
    const val FACE_SIZE = 48

    private var initialized = false

    private val wallTexturesFrame0 = Array(13) { IntArray(TEX_SIZE * TEX_SIZE) }
    private val wallTexturesFrame1 = Array(13) { IntArray(TEX_SIZE * TEX_SIZE) }

    val floorTex = IntArray(TEX_SIZE * TEX_SIZE)
    val ceilingTex = IntArray(TEX_SIZE * TEX_SIZE)
    private val acidTex0 = IntArray(TEX_SIZE * TEX_SIZE)
    private val acidTex1 = IntArray(TEX_SIZE * TEX_SIZE)
    val teleporterTex = IntArray(TEX_SIZE * TEX_SIZE)

    // Monster sprites: [MonsterType.ordinal][frameType 0=walkA, 1=walkB, 2=attack, 3=pain, 4=dying, 5=dead]
    private val monsterSprites = Array(MonsterType.entries.size) { Array(6) { IntArray(TEX_SIZE * TEX_SIZE) } }
    private val itemSprites = Array(ItemType.entries.size) { IntArray(TEX_SIZE * TEX_SIZE) }
    private val barrelAltSprite = IntArray(TEX_SIZE * TEX_SIZE)
    private val barrelExplodeSprite = IntArray(TEX_SIZE * TEX_SIZE)

    // Weapon sprites: [WeaponType.ordinal][0=idle, 1=fire]
    private val weaponSprites = Array(WeaponType.entries.size) { Array(2) { IntArray(WEAPON_W * WEAPON_H) } }

    fun rgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or
            (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or
            b.coerceIn(0, 255)
    }

    fun shadeColor(argb: Int, shade: Float, invulnerable: Boolean = false): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        if (invulnerable) {
            val lum = (235 - ((r * 77 + g * 150 + b * 29) ushr 8) * shade).toInt().coerceIn(24, 248)
            return (0xFF shl 24) or (lum shl 16) or (lum shl 8) or lum
        }
        val s = (shade * 256f).toInt().coerceIn(28, 256)
        val sr = (r * s) ushr 8
        val sg = (g * s) ushr 8
        val sb = (b * s) ushr 8
        return (0xFF shl 24) or (sr shl 16) or (sg shl 8) or sb
    }

    private fun hashNoise(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFF) - 128
    }

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        buildFlats()
        buildWalls()
        buildMonsters()
        buildItems()
        buildWeapons()
    }

    private class PixCanvas(val w: Int, val h: Int, val pixels: IntArray) {
        fun clear(col: Int = 0) {
            pixels.fill(col)
        }

        fun set(x: Int, y: Int, col: Int) {
            if (x in 0 until w && y in 0 until h) {
                pixels[y * w + x] = col
            }
        }

        fun get(x: Int, y: Int): Int {
            if (x in 0 until w && y in 0 until h) return pixels[y * w + x]
            return 0
        }

        fun rect(x: Int, y: Int, rw: Int, rh: Int, col: Int) {
            val x0 = x.coerceAtLeast(0)
            val y0 = y.coerceAtLeast(0)
            val x1 = (x + rw).coerceAtMost(w)
            val y1 = (y + rh).coerceAtMost(h)
            for (py in y0 until y1) {
                val row = py * w
                for (px in x0 until x1) {
                    pixels[row + px] = col
                }
            }
        }

        fun bevelPanel(
            x: Int,
            y: Int,
            rw: Int,
            rh: Int,
            r: Int,
            g: Int,
            b: Int,
            grit: Int = 14,
            seed: Int = 7
        ) {
            for (py in y until (y + rh)) {
                if (py !in 0 until h) continue
                for (px in x until (x + rw)) {
                    if (px !in 0 until w) continue
                    val n = (hashNoise(px, py, seed) * grit) / 128
                    var delta = n
                    if (px == x || py == y) delta += 28
                    else if (px == x + 1 || py == y + 1) delta += 14
                    if (px == x + rw - 1 || py == y + rh - 1) delta -= 34
                    else if (px == x + rw - 2 || py == y + rh - 2) delta -= 16
                    set(px, py, rgb(r + delta, g + delta, b + delta))
                }
            }
        }

        fun rivet(x: Int, y: Int) {
            set(x, y, rgb(185, 192, 200))
            set(x + 1, y, rgb(70, 75, 82))
            set(x, y + 1, rgb(65, 70, 78))
            set(x + 1, y + 1, rgb(42, 46, 52))
        }

        fun shadedOval(
            cx: Float,
            cy: Float,
            rx: Float,
            ry: Float,
            r: Int,
            g: Int,
            b: Int,
            grit: Int = 16,
            specular: Boolean = false
        ) {
            val minX = (cx - rx - 1f).toInt().coerceAtLeast(0)
            val maxX = (cx + rx + 1f).toInt().coerceAtMost(w - 1)
            val minY = (cy - ry - 1f).toInt().coerceAtLeast(0)
            val maxY = (cy + ry + 1f).toInt().coerceAtMost(h - 1)
            for (py in minY..maxY) {
                val ny = (py - cy) / ry
                for (px in minX..maxX) {
                    val nx = (px - cx) / rx
                    val d2 = nx * nx + ny * ny
                    if (d2 <= 1.0f) {
                        val nz = sqrt((1.0f - d2).coerceAtLeast(0f))
                        // Light from upper-left-front
                        var light = (-nx * 0.35f - ny * 0.40f + nz * 0.85f).coerceIn(0.22f, 1.25f)
                        if (specular && light > 0.96f) light += 0.30f
                        val n = (hashNoise(px, py, 31) * grit) / 128
                        val outline = if (d2 > 0.84f) 0.55f else 1.0f
                        val f = light * outline
                        set(px, py, rgb((r * f).toInt() + n, (g * f).toInt() + n, (b * f).toInt() + n))
                    }
                }
            }
        }

        fun shadedLimb(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            radiusTop: Float,
            radiusBot: Float,
            r: Int,
            g: Int,
            b: Int
        ) {
            val steps = (hypot(x1 - x0, y1 - y0) * 2.0f).toInt().coerceAtLeast(2)
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val cx = x0 + (x1 - x0) * t
                val cy = y0 + (y1 - y0) * t
                val rad = radiusTop + (radiusBot - radiusTop) * t
                shadedOval(cx, cy, rad, rad * 0.9f, r, g, b, grit = 12)
            }
        }

        fun line(x0: Int, y0: Int, x1: Int, y1: Int, thickness: Int, col: Int) {
            val dx = x1 - x0
            val dy = y1 - y0
            val steps = maxOf(abs(dx), abs(dy)).coerceAtLeast(1)
            for (i in 0..steps) {
                val px = x0 + (dx * i) / steps
                val py = y0 + (dy * i) / steps
                for (oy in -thickness / 2..thickness / 2) {
                    for (ox in -thickness / 2..thickness / 2) {
                        set(px + ox, py + oy, col)
                    }
                }
            }
        }
    }

    private fun buildFlats() {
        // 1. Industrial Octagonal / Riveted Metal Floor (Doom FLOOR4_8 style)
        val fc = PixCanvas(TEX_SIZE, TEX_SIZE, floorTex)
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val tx = x and 31
                val ty = y and 31
                val n = (hashNoise(x, y, 101) * 10) / 128
                val edge = (tx == 0 || ty == 0 || tx + ty < 5 || (31 - tx) + ty < 5 ||
                    tx + (31 - ty) < 5 || (31 - tx) + (31 - ty) < 5)
                if (edge) {
                    fc.set(x, y, rgb(34 + n, 37 + n, 42 + n))
                } else {
                    val bev = if (tx == 1 || ty == 1) 12 else if (tx == 30 || ty == 30) -12 else 0
                    fc.set(x, y, rgb(66 + n + bev, 70 + n + bev, 76 + n + bev))
                }
            }
        }
        fc.rivet(4, 4); fc.rivet(26, 4); fc.rivet(36, 36); fc.rivet(58, 36)

        // 2. Industrial Ceiling with Glowing Recessed Light Panel (TLITE6_1)
        val cc = PixCanvas(TEX_SIZE, TEX_SIZE, ceilingTex)
        cc.bevelPanel(0, 0, 64, 64, 46, 50, 56, grit = 8, seed = 202)
        cc.rect(31, 0, 2, 64, rgb(28, 30, 35))
        cc.rect(0, 31, 64, 2, rgb(28, 30, 35))
        cc.bevelPanel(20, 20, 24, 24, 70, 74, 80, grit = 4)
        cc.bevelPanel(23, 23, 18, 18, 225, 220, 195, grit = 6)
        cc.rect(31, 23, 2, 18, rgb(140, 138, 125))
        cc.rect(23, 31, 18, 2, rgb(140, 138, 125))

        // 3. Animated Radioactive Nukage Slime (Frames 0 & 1)
        for (frame in 0..1) {
            val out = if (frame == 0) acidTex0 else acidTex1
            val phase = frame * 1.6f
            for (y in 0 until TEX_SIZE) {
                for (x in 0 until TEX_SIZE) {
                    val swirl = sin(x * 0.22f + phase) + cos(y * 0.22f - phase) +
                        sin((x + y) * 0.15f + phase)
                    val n = (hashNoise(x, y, 303 + frame) * 14) / 128
                    val gVal = (105 + swirl * 38f + n).toInt().coerceIn(38, 235)
                    val rVal = (gVal * 0.16f).toInt()
                    val bVal = (gVal * 0.22f).toInt()
                    out[y * TEX_SIZE + x] = rgb(rVal, gVal, bVal)
                }
            }
        }

        // 4. Evil Red Pentagram Teleporter Pad (GATE1)
        val tc = PixCanvas(TEX_SIZE, TEX_SIZE, teleporterTex)
        tc.bevelPanel(0, 0, 64, 64, 88, 22, 22, grit = 14, seed = 404)
        tc.shadedOval(32f, 32f, 27f, 27f, 52, 12, 12, grit = 10)
        // Outer glowing ring
        for (a in 0 until 360 step 2) {
            val rad = a * 0.0174533f
            val rx = (32f + cos(rad) * 25f).toInt()
            val ry = (32f + sin(rad) * 25f).toInt()
            tc.rect(rx - 1, ry - 1, 2, 2, rgb(255, 65, 30))
        }
        // 5-pointed star
        val pts = Array(5) { i ->
            val ang = -1.5708f + i * 1.256637f
            ((32f + cos(ang) * 23f).toInt()) to ((32f + sin(ang) * 23f).toInt())
        }
        val starOrder = intArrayOf(0, 2, 4, 1, 3, 0)
        for (i in 0 until 5) {
            val p0 = pts[starOrder[i]]
            val p1 = pts[starOrder[i + 1]]
            tc.line(p0.first, p0.second, p1.first, p1.second, 2, rgb(255, 180, 40))
        }
    }

    private fun buildWalls() {
        for (frame in 0..1) {
            val bank = if (frame == 0) wallTexturesFrame0 else wallTexturesFrame1

            // Tile 1: UAC Techbase Wall (STARTAN / METAL1 with hazard trim & rivets)
            PixCanvas(TEX_SIZE, TEX_SIZE, bank[1]).apply {
                bevelPanel(0, 0, 32, 48, 126, 120, 110, grit = 16, seed = 11)
                bevelPanel(32, 0, 32, 48, 114, 120, 128, grit = 16, seed = 12)
                bevelPanel(4, 6, 24, 20, 96, 92, 86, grit = 12, seed = 13)
                bevelPanel(36, 6, 24, 34, 88, 96, 106, grit = 12, seed = 14)
                rivet(2, 2); rivet(28, 2); rivet(34, 2); rivet(60, 2)
                rivet(2, 44); rivet(28, 44); rivet(34, 44); rivet(60, 44)
                // Dark recessed cable groove + Bottom yellow/black hazard baseboard
                rect(0, 48, 64, 6, rgb(38, 42, 48))
                for (x in 0 until 64) {
                    val stripe = ((x + yOffsetHazard(x)) / 6) % 2 == 0
                    for (y in 54 until 60) {
                        val isYellow = (((x + y) / 5) % 2 == 0)
                        if (stripe || isYellow) {
                            set(x, y, if (isYellow) rgb(205, 162, 28) else rgb(36, 36, 38))
                        }
                    }
                }
                bevelPanel(0, 60, 64, 4, 72, 76, 82, grit = 8)
            }

            // Tile 2: Weathered Stone / Ashlar Concrete Wall
            PixCanvas(TEX_SIZE, TEX_SIZE, bank[2]).apply {
                clear(rgb(42, 40, 36))
                val rowHeights = intArrayOf(0, 16, 32, 48, 64)
                for (r in 0 until 4) {
                    val y0 = rowHeights[r]
                    val bh = rowHeights[r + 1] - y0 - 2
                    val offset = if (r % 2 == 0) 0 else 16
                    for (bIdx in -1..2) {
                        val bx = bIdx * 32 + offset
                        bevelPanel(bx + 1, y0 + 1, 30, bh, 112, 108, 96, grit = 22, seed = 20 + r * 7 + bIdx)
                    }
                }
            }

            // Tile 3: Hell Crimson Brick Wall with Demonic Emblem
            PixCanvas(TEX_SIZE, TEX_SIZE, bank[3]).apply {
                clear(rgb(48, 12, 12))
                for (r in 0 until 8) {
                    val y0 = r * 8
                    val off = if (r % 2 == 0) 0 else 8
                    for (c in -1..4) {
                        bevelPanel(c * 16 + off + 1, y0 + 1, 14, 6, 152, 36, 32, grit = 20, seed = 50 + r * 5 + c)
                    }
                }
                // Carved Stone Skull Relief in center
                bevelPanel(20, 18, 24, 28, 92, 86, 80, grit = 14)
                shadedOval(32f, 29f, 7f, 7f, 168, 158, 142, grit = 12)
                rect(28, 28, 3, 3, if (frame == 0) rgb(255, 50, 20) else rgb(200, 20, 10))
                rect(34, 28, 3, 3, if (frame == 0) rgb(255, 50, 20) else rgb(200, 20, 10))
                rect(29, 35, 6, 4, rgb(145, 138, 124))
            }

            // Tile 4: Toxic Nukage Pipe Wall
            PixCanvas(TEX_SIZE, TEX_SIZE, bank[4]).apply {
                bevelPanel(0, 0, 64, 64, 78, 94, 76, grit = 16, seed = 41)
                // Vertical glowing nukage glass vat in center
                rect(22, 6, 20, 52, rgb(24, 38, 26))
                val slimeSrc = if (frame == 0) acidTex0 else acidTex1
                for (py in 8 until 56) {
                    for (px in 24 until 40) {
                        set(px, py, slimeSrc[py * TEX_SIZE + px])
                    }
                }
                // Horizontal metallic pipes top & bottom
                for (py in 10..16) {
                    val sh = 1.0f - abs(py - 13) * 0.14f
                    rect(0, py, 64, 1, rgb((135 * sh).toInt(), (142 * sh).toInt(), (135 * sh).toInt()))
                }
                for (py in 48..54) {
                    val sh = 1.0f - abs(py - 51) * 0.14f
                    rect(0, py, 64, 1, rgb((135 * sh).toInt(), (142 * sh).toInt(), (135 * sh).toInt()))
                }
            }

            // Tile 5: Animated UAC Computer Station Wall
            PixCanvas(TEX_SIZE, TEX_SIZE, bank[5]).apply {
                bevelPanel(0, 0, 64, 64, 66, 74, 86, grit = 12, seed = 51)
                // Left CRT monitor
                rect(5, 8, 25, 22, rgb(14, 26, 20))
                for (x in 7 until 28) {
                    val wy = 19 + (sin((x + frame * 5) * 0.45f) * 5f).toInt()
                    set(x, wy, rgb(40, 250, 110))
                    set(x, wy + 1, rgb(20, 140, 60))
                }
                // Right status screen
                rect(34, 8, 25, 22, rgb(18, 20, 28))
                for (row in 0..3) {
                    val active = ((row + frame) % 2 == 0)
                    rect(37, 11 + row * 4, if (active) 18 else 11, 2, if (active) rgb(255, 185, 30) else rgb(45, 150, 255))
                }
                // Bottom server blinkenlights
                bevelPanel(5, 35, 54, 22, 44, 50, 58, grit = 8)
                for (ry in 0..2) {
                    for (rx in 0..7) {
                        val on = ((rx + ry + frame) % 3 != 0)
                        val col = when {
                            !on -> rgb(28, 32, 36)
                            (rx + ry) % 2 == 0 -> rgb(255, 55, 45)
                            else -> rgb(50, 240, 100)
                        }
                        rect(9 + rx * 6, 39 + ry * 5, 4, 3, col)
                    }
                }
            }

            // Tiles 6, 7, 8, 9: Heavy UAC Steel Doors (6=Normal, 7=Blue, 8=Yellow, 9=Red)
            for (doorTile in 6..9) {
                PixCanvas(TEX_SIZE, TEX_SIZE, bank[doorTile]).apply {
                    bevelPanel(0, 0, 64, 64, 116, 122, 130, grit = 15, seed = 60 + doorTile)
                    bevelPanel(6, 6, 23, 46, 88, 94, 102, grit = 14, seed = 65)
                    bevelPanel(35, 6, 23, 46, 88, 94, 102, grit = 14, seed = 66)
                    // Central heavy door seam
                    rect(31, 0, 2, 64, rgb(28, 30, 35))
                    // Keycard side light strips & center lock badge
                    val accent = when (doorTile) {
                        7 -> rgb(35, 140, 255)
                        8 -> rgb(255, 215, 25)
                        9 -> rgb(255, 35, 45)
                        else -> rgb(200, 155, 40)
                    }
                    rect(2, 8, 3, 44, accent)
                    rect(59, 8, 3, 44, accent)
                    bevelPanel(25, 24, 14, 14, 54, 58, 66, grit = 6)
                    rect(28, 28, 8, 6, accent)
                    // Bottom warning threshold
                    for (x in 0 until 64) {
                        val isYellow = ((x / 4) % 2 == 0)
                        rect(x, 56, 1, 5, if (isYellow) accent else rgb(32, 34, 38))
                    }
                }
            }

            // Tile 10: Secret Push-Wall (UAC Tech Wall with subtle weathered crack & misaligned seam)
            bank[1].copyInto(bank[10])
            PixCanvas(TEX_SIZE, TEX_SIZE, bank[10]).apply {
                rect(31, 0, 2, 64, rgb(42, 38, 34))
                line(18, 12, 22, 28, 1, rgb(48, 44, 40))
                line(22, 28, 19, 44, 1, rgb(48, 44, 40))
            }

            // Tile 11 (Normal Exit Switch) & Tile 12 (Secret Exit Switch -> E1M9)
            for (swTile in 11..12) {
                val isSecret = (swTile == 12)
                PixCanvas(TEX_SIZE, TEX_SIZE, bank[swTile]).apply {
                    bevelPanel(0, 0, 64, 64, if (isSecret) 98 else 128, 46, if (isSecret) 128 else 46, grit = 18, seed = 90 + swTile)
                    // "EXIT" glowing sign box at top
                    bevelPanel(16, 8, 32, 11, 28, 28, 32, grit = 4)
                    val signCol = if (isSecret) rgb(255, 215, 30) else rgb(255, 60, 50)
                    // Pixel letters E X I T
                    rect(19, 10, 5, 1, signCol); rect(19, 13, 4, 1, signCol); rect(19, 16, 5, 1, signCol); rect(19, 10, 1, 7, signCol)
                    line(26, 10, 31, 16, 1, signCol); line(31, 10, 26, 16, 1, signCol)
                    rect(35, 10, 2, 7, signCol)
                    rect(40, 10, 6, 1, signCol); rect(42, 10, 2, 7, signCol)

                    // Switch housing box
                    bevelPanel(18, 24, 28, 28, 62, 66, 72, grit = 10)
                    bevelPanel(23, 29, 18, 18, 26, 28, 32, grit = 4)
                    val glowCol = if (isSecret) {
                        if (frame == 0) rgb(235, 64, 255) else rgb(255, 215, 0)
                    } else {
                        if (frame == 0) rgb(45, 250, 95) else rgb(30, 195, 70)
                    }
                    rect(28, 32, 8, 7, glowCol)
                    bevelPanel(29, 38, 6, 7, 175, 180, 188, grit = 4)
                }
            }
        }
    }

    private fun yOffsetHazard(x: Int): Int = x * 0

    private fun buildMonsters() {
        for (mType in MonsterType.entries) {
            for (frameIdx in 0..5) {
                val c = PixCanvas(TEX_SIZE, TEX_SIZE, monsterSprites[mType.ordinal][frameIdx])
                c.clear(0)
                drawMonsterFrame(c, mType, frameIdx)
            }
        }
    }

    private fun drawMonsterFrame(c: PixCanvas, type: MonsterType, frame: Int) {
        // frame 5 = DEAD corpse on ground, frame 4 = DYING splatter
        if (frame == 5) {
            val r = if (type == MonsterType.CACODEMON) 45 else 155
            val g = if (type == MonsterType.BARON_OF_HELL) 95 else 24
            val b = if (type == MonsterType.CACODEMON) 185 else 24
            c.shadedOval(32f, 56f, 22f, 6f, r, g, b, grit = 22)
            c.shadedOval(28f, 53f, 14f, 5f, (r * 0.8f).toInt(), g, b, grit = 18)
            c.shadedOval(38f, 57f, 9f, 3f, 180, 18, 18, grit = 14)
            return
        }
        if (frame == 4) {
            c.shadedOval(32f, 44f, 18f, 16f, 185, 32, 24, grit = 26)
            c.shadedOval(24f, 36f, 8f, 8f, 235, 85, 25, grit = 20)
            c.shadedOval(40f, 40f, 9f, 9f, 165, 20, 20, grit = 20)
            return
        }

        val stride = when (frame) {
            0 -> -3f
            1 -> 3f
            else -> 0f
        }
        val painTint = (frame == 3)
        val isAttack = (frame == 2)

        when (type) {
            MonsterType.ZOMBIEMAN, MonsterType.SHOTGUN_GUY -> {
                val isShotgun = (type == MonsterType.SHOTGUN_GUY)
                val armorR = if (painTint) 220 else if (isShotgun) 64 else 112
                val armorG = if (painTint) 120 else if (isShotgun) 68 else 128
                val armorB = if (painTint) 120 else if (isShotgun) 76 else 88
                val pantsR = if (isShotgun) 52 else 118
                val pantsG = if (isShotgun) 54 else 98
                val pantsB = if (isShotgun) 58 else 72

                // Legs & combat boots
                c.shadedLimb(26f, 39f, 24f + stride, 56f, 5.2f, 4.2f, pantsR, pantsG, pantsB)
                c.shadedLimb(38f, 39f, 40f - stride, 56f, 5.2f, 4.2f, pantsR, pantsG, pantsB)
                c.shadedOval(24f + stride, 59f, 5.5f, 3.2f, 42, 36, 32)
                c.shadedOval(40f - stride, 59f, 5.5f, 3.2f, 42, 36, 32)

                // Armored torso & shoulders
                c.shadedOval(32f, 30f, 12.5f, 11.5f, armorR, armorG, armorB, grit = 18)
                c.shadedOval(20f, 25f, 5.5f, 5.0f, if (isShotgun) 150 else armorR, 45, 45)
                c.shadedOval(44f, 25f, 5.5f, 5.0f, if (isShotgun) 150 else armorR, 45, 45)

                // Arms
                c.shadedLimb(19f, 27f, 23f, 35f, 4.2f, 3.6f, 178, 134, 106)
                c.shadedLimb(45f, 27f, 41f, 35f, 4.2f, 3.6f, 178, 134, 106)

                // Head (Zombieman has green hair, Shotgun Guy is bald with scars)
                c.shadedOval(32f, 14f, 6.8f, 7.5f, 185, 142, 116, grit = 14)
                if (!isShotgun) {
                    c.shadedOval(32f, 8.5f, 6.8f, 3.2f, 65, 135, 58, grit = 18)
                }
                // Glowing red zombie eyes & bloody mouth
                c.rect(28, 13, 2, 2, rgb(255, 25, 25))
                c.rect(34, 13, 2, 2, rgb(255, 25, 25))
                c.rect(29, 18, 6, 2, rgb(140, 20, 20))

                // Rifle / Shotgun across chest + Muzzle Flash
                c.line(20, 34, 45, 31, 3, rgb(48, 54, 62))
                c.line(21, 33, 44, 30, 1, rgb(135, 145, 155))
                if (isAttack) {
                    c.shadedOval(46f, 30f, 6.5f, 6.5f, 255, 220, 45, grit = 10, specular = true)
                    c.shadedOval(46f, 30f, 3.2f, 3.2f, 255, 255, 245, grit = 0)
                }
            }

            MonsterType.IMP -> {
                val r = if (painTint) 230 else 148
                val g = if (painTint) 130 else 88
                val b = if (painTint) 110 else 52

                c.shadedLimb(25f, 39f, 23f + stride, 58f, 5.2f, 3.8f, r, g, b)
                c.shadedLimb(39f, 39f, 41f - stride, 58f, 5.2f, 3.8f, r, g, b)
                c.shadedOval(32f, 29f, 12.5f, 12.5f, r, g, b, grit = 20)
                c.shadedLimb(19f, 25f, if (isAttack) 13f else 17f, if (isAttack) 18f else 36f, 4.4f, 3.5f, r, g, b)
                c.shadedLimb(45f, 25f, if (isAttack) 51f else 47f, if (isAttack) 18f else 36f, 4.4f, 3.5f, r, g, b)
                c.shadedOval(32f, 13f, 7.2f, 7.5f, r, g, b, grit = 16)

                // White bone spikes on shoulders, chest & crown
                c.line(18, 22, 13, 16, 2, rgb(235, 230, 220))
                c.line(46, 22, 51, 16, 2, rgb(235, 230, 220))
                c.line(27, 8, 24, 3, 2, rgb(235, 230, 220))
                c.line(37, 8, 40, 3, 2, rgb(235, 230, 220))
                c.shadedOval(27f, 27f, 2.0f, 2.0f, 235, 230, 220)
                c.shadedOval(37f, 27f, 2.0f, 2.0f, 235, 230, 220)

                // Glowing red eyes & fanged mouth
                c.rect(28, 12, 3, 2, rgb(255, 35, 20))
                c.rect(34, 12, 3, 2, rgb(255, 35, 20))
                c.rect(29, 16, 6, 3, rgb(65, 10, 10))

                if (isAttack) {
                    c.shadedOval(52f, 17f, 6.0f, 6.0f, 255, 140, 20, specular = true)
                }
            }

            MonsterType.PINKY_DEMON, MonsterType.SPECTRE -> {
                val r = if (painTint) 250 else 218
                val g = if (painTint) 170 else 108
                val b = if (painTint) 180 else 128

                // Massive muscular haunches & hooves
                c.shadedLimb(21f, 38f, 19f + stride, 57f, 7.5f, 5.0f, r, g, b)
                c.shadedLimb(43f, 38f, 45f - stride, 57f, 7.5f, 5.0f, r, g, b)
                c.shadedOval(19f + stride, 59f, 5.5f, 3.0f, 75, 58, 52)
                c.shadedOval(45f - stride, 59f, 5.5f, 3.0f, 75, 58, 52)

                // Hulking hunched body + huge head in front
                c.shadedOval(32f, 31f, 18f, 14f, r, g, b, grit = 20)
                c.shadedOval(32f, 24f, 13f, 12f, r + 12, g + 8, b + 8, grit = 16)

                // Bull horns
                c.line(19, 18, 11, 11, 3, rgb(195, 175, 145))
                c.line(45, 18, 53, 11, 3, rgb(195, 175, 145))

                // Yellow eyes & cavernous toothed jaws
                c.rect(25, 19, 3, 3, rgb(255, 215, 20))
                c.rect(36, 19, 3, 3, rgb(255, 215, 20))
                c.shadedOval(32f, 30f, 8.5f, 6.0f, 75, 12, 18, grit = 6)
                for (tx in 25..38 step 3) {
                    c.rect(tx, 26, 2, 3, rgb(245, 242, 235))
                    c.rect(tx, 33, 2, 3, rgb(245, 242, 235))
                }
            }

            MonsterType.LOST_SOUL -> {
                // Blazing fire aura behind flying skull
                val flameOff = if (frame % 2 == 0) -2f else 2f
                c.shadedOval(32f, 24f + flameOff, 17f, 20f, 255, 115, 15, grit = 28)
                c.shadedOval(32f, 21f - flameOff, 13f, 16f, 255, 205, 35, grit = 20)
                // Bone skull & curved horns
                c.shadedOval(32f, 32f, 11f, 11f, 225, 212, 185, grit = 14)
                c.line(21, 26, 12, 16, 3, rgb(195, 180, 150))
                c.line(43, 26, 52, 16, 3, rgb(195, 180, 150))
                // Hollow eye sockets & teeth
                c.shadedOval(27f, 31f, 3.5f, 3.5f, 28, 12, 12, grit = 0)
                c.shadedOval(37f, 31f, 3.5f, 3.5f, 28, 12, 12, grit = 0)
                c.set(27, 31, rgb(255, 40, 20))
                c.set(37, 31, rgb(255, 40, 20))
                for (tx in 26..38 step 3) {
                    c.rect(tx, 39, 2, 4, rgb(240, 235, 220))
                }
            }

            MonsterType.CACODEMON -> {
                val r = if (painTint) 250 else 212
                val g = if (painTint) 140 else 36
                val b = if (painTint) 140 else 42

                // Spherical crimson scaled body
                c.shadedOval(32f, 32f, 23f, 22f, r, g, b, grit = 24, specular = true)
                // Crown of white horns
                c.line(20, 14, 14, 5, 3, rgb(230, 225, 215))
                c.line(32, 11, 32, 2, 3, rgb(230, 225, 215))
                c.line(44, 14, 50, 5, 3, rgb(230, 225, 215))
                c.line(12, 26, 5, 22, 2, rgb(215, 210, 200))
                c.line(52, 26, 59, 22, 2, rgb(215, 210, 200))

                // Iconic single emerald-green cyclops eye
                c.shadedOval(32f, 22f, 6.2f, 5.2f, 35, 245, 95, grit = 6, specular = true)
                c.rect(31, 20, 2, 4, rgb(10, 55, 20))

                // Huge electric-blue mouth & jagged fangs
                val mouthColorR = if (isAttack) 255 else 35
                val mouthColorG = if (isAttack) 190 else 115
                val mouthColorB = 255
                c.shadedOval(32f, 39f, 15f, 8.5f, mouthColorR, mouthColorG, mouthColorB, grit = 10)
                for (tx in 20..44 step 4) {
                    c.rect(tx, 32, 2, 4, rgb(248, 248, 245))
                    c.rect(tx, 43, 2, 4, rgb(248, 248, 245))
                }
            }

            MonsterType.BARON_OF_HELL -> {
                val r = if (painTint) 245 else 198
                val g = if (painTint) 150 else 72
                val b = if (painTint) 150 else 82

                // Goat-like brown furry legs
                c.shadedLimb(24f, 38f, 22f + stride, 59f, 6.2f, 4.5f, 112, 78, 52)
                c.shadedLimb(40f, 38f, 42f - stride, 59f, 6.2f, 4.5f, 112, 78, 52)
                // Muscular crimson minotaur torso & arms
                c.shadedOval(32f, 27f, 15f, 13f, r, g, b, grit = 18)
                c.shadedLimb(16f, 22f, 12f, if (isAttack) 14f else 35f, 5.2f, 4.2f, r, g, b)
                c.shadedLimb(48f, 22f, 52f, if (isAttack) 14f else 35f, 5.2f, 4.2f, r, g, b)
                // Head & horns
                c.shadedOval(32f, 11f, 7.5f, 8.0f, r, g, b, grit = 14)
                c.line(24, 8, 16, 1, 3, rgb(215, 205, 185))
                c.line(40, 8, 48, 1, 3, rgb(215, 205, 185))
                c.rect(28, 10, 3, 2, rgb(45, 255, 95))
                c.rect(34, 10, 3, 2, rgb(45, 255, 95))
                // Green hell-plasma glowing hands!
                val glowY = if (isAttack) 14f else 36f
                c.shadedOval(12f, glowY, 4.5f, 4.5f, 40, 255, 100, specular = true)
                c.shadedOval(52f, glowY, 4.5f, 4.5f, 40, 255, 100, specular = true)
            }

            MonsterType.CYBERDEMON -> {
                val r = if (painTint) 245 else 168
                val g = if (painTint) 160 else 104
                val b = if (painTint) 150 else 84

                // Flesh left leg + Silver cybernetic right leg!
                c.shadedLimb(23f, 37f, 21f + stride, 59f, 6.8f, 5.0f, r, g, b)
                c.shadedLimb(41f, 37f, 43f - stride, 59f, 6.8f, 5.0f, 145, 158, 172)

                // Towering torso with red cybernetic belly wires
                c.shadedOval(32f, 26f, 17f, 14f, r, g, b, grit = 20)
                c.shadedOval(32f, 33f, 9f, 5f, 195, 28, 28, grit = 16)

                // Left flesh arm + Right arm Rocket Launcher cannon!
                c.shadedLimb(14f, 22f, 11f, 36f, 5.5f, 4.5f, r, g, b)
                c.shadedLimb(50f, 22f, 55f, 33f, 6.0f, 5.5f, 110, 120, 132)
                c.rect(51, 29, 9, 8, rgb(58, 64, 72))
                if (isAttack) {
                    c.shadedOval(56f, 33f, 7.5f, 7.5f, 255, 195, 35, specular = true)
                }

                // Horned skull head
                c.shadedOval(32f, 10f, 8.5f, 8.5f, r, g, b, grit = 14)
                c.line(23, 8, 12, 1, 4, rgb(210, 200, 180))
                c.line(41, 8, 52, 1, 4, rgb(210, 200, 180))
                c.rect(28, 9, 3, 3, rgb(255, 25, 25))
                c.rect(34, 9, 3, 3, rgb(255, 25, 25))
            }
        }
    }

    private fun buildItems() {
        for (iType in ItemType.entries) {
            val c = PixCanvas(TEX_SIZE, TEX_SIZE, itemSprites[iType.ordinal])
            c.clear(0)
            drawItem(c, iType, altFrame = false)
        }
        PixCanvas(TEX_SIZE, TEX_SIZE, barrelAltSprite).apply {
            clear(0)
            drawItem(this, ItemType.BARREL, altFrame = true)
        }
        PixCanvas(TEX_SIZE, TEX_SIZE, barrelExplodeSprite).apply {
            clear(0)
            shadedOval(32f, 40f, 24f, 22f, 255, 95, 15, grit = 28)
            shadedOval(32f, 40f, 16f, 15f, 255, 215, 40, grit = 18, specular = true)
            shadedOval(32f, 40f, 8f, 8f, 255, 255, 235, grit = 4)
        }
    }

    private fun drawItem(c: PixCanvas, type: ItemType, altFrame: Boolean) {
        when (type) {
            ItemType.BARREL -> {
                c.shadedLimb(32f, 28f, 32f, 57f, 13f, 13f, 96, 104, 88)
                c.rect(19, 35, 26, 3, rgb(55, 60, 52))
                c.rect(19, 48, 26, 3, rgb(55, 60, 52))
                // Glowing green radioactive slime rim at top
                c.shadedOval(32f, 26f, 12f, 4.5f, if (altFrame) 55 else 35, if (altFrame) 250 else 210, 65, specular = true)
            }
            ItemType.TECH_PILLAR -> {
                c.shadedLimb(32f, 6f, 32f, 60f, 11f, 12f, 112, 122, 134)
                c.shadedOval(32f, 32f, 7f, 14f, 45, 195, 255, specular = true)
            }
            ItemType.CANDELABRA -> {
                c.shadedLimb(32f, 22f, 32f, 60f, 3.5f, 6f, 205, 165, 45)
                c.line(20, 26, 44, 26, 3, rgb(205, 165, 45))
                for (cx in intArrayOf(20, 32, 44)) {
                    c.rect(cx - 2, 18, 4, 8, rgb(240, 235, 220))
                    c.shadedOval(cx.toFloat(), 14f, 3.2f, 4.8f, 255, 200, 35, specular = true)
                }
            }
            ItemType.HEALTH_BONUS -> {
                c.shadedOval(32f, 52f, 8f, 8f, 35, 115, 255, specular = true)
                c.rect(29, 41, 6, 5, rgb(175, 185, 195))
            }
            ItemType.ARMOR_BONUS -> {
                c.shadedOval(32f, 51f, 10f, 9f, 55, 165, 65, specular = true)
                c.rect(26, 50, 12, 4, rgb(25, 35, 25))
            }
            ItemType.STIMPACK, ItemType.MEDIKIT, ItemType.BERSERK -> {
                val isBig = (type != ItemType.STIMPACK)
                val isBerserk = (type == ItemType.BERSERK)
                val bw = if (isBig) 26 else 18
                val bh = if (isBig) 18 else 13
                val bx = 32 - bw / 2
                val by = 60 - bh
                c.bevelPanel(bx, by, bw, bh, if (isBerserk) 42 else 215, if (isBerserk) 44 else 215, if (isBerserk) 48 else 210, grit = 8)
                c.rect(30, by + 3, 4, bh - 6, rgb(220, 25, 25))
                c.rect(32 - (bh - 6) / 2, by + bh / 2 - 2, bh - 6, 4, rgb(220, 25, 25))
            }
            ItemType.GREEN_ARMOR, ItemType.BLUE_ARMOR -> {
                val isBlue = (type == ItemType.BLUE_ARMOR)
                val r = if (isBlue) 35 else 52
                val g = if (isBlue) 115 else 175
                val b = if (isBlue) 245 else 62
                c.shadedOval(32f, 47f, 15f, 12f, r, g, b, specular = true)
                c.shadedOval(18f, 41f, 6f, 5f, r, g, b, specular = true)
                c.shadedOval(46f, 41f, 6f, 5f, r, g, b, specular = true)
            }
            ItemType.SOUL_SPHERE, ItemType.INVULNERABILITY -> {
                val isInv = (type == ItemType.INVULNERABILITY)
                c.shadedOval(32f, 44f, 14f, 14f, if (isInv) 45 else 35, if (isInv) 235 else 125, if (isInv) 85 else 255, specular = true)
                c.rect(27, 41, 3, 3, rgb(255, 245, 180))
                c.rect(34, 41, 3, 3, rgb(255, 245, 180))
                c.rect(28, 47, 8, 3, rgb(20, 35, 80))
            }
            ItemType.KEY_BLUE, ItemType.KEY_YELLOW, ItemType.KEY_RED -> {
                val col = when (type) {
                    ItemType.KEY_BLUE -> rgb(35, 135, 255)
                    ItemType.KEY_YELLOW -> rgb(255, 215, 20)
                    else -> rgb(255, 35, 45)
                }
                c.bevelPanel(24, 42, 16, 18, 210, 215, 222, grit = 6)
                c.rect(26, 44, 12, 8, col)
                c.rect(27, 55, 3, 3, rgb(45, 245, 95))
            }
            ItemType.RAD_SUIT -> {
                c.shadedLimb(32f, 28f, 32f, 58f, 10f, 11f, 220, 228, 220)
                c.shadedOval(32f, 26f, 6.5f, 6.5f, 45, 195, 75, specular = true)
            }
            ItemType.COMPUTER_MAP -> {
                c.bevelPanel(20, 42, 24, 18, 140, 148, 156, grit = 8)
                c.rect(23, 45, 18, 11, rgb(15, 45, 25))
                c.line(25, 52, 38, 47, 1, rgb(50, 250, 100))
            }
            ItemType.BACKPACK -> {
                c.shadedOval(32f, 49f, 12f, 11f, 138, 96, 58, grit = 16)
                c.rect(24, 46, 16, 3, rgb(75, 52, 32))
            }
            ItemType.WEAPON_SHOTGUN, ItemType.WEAPON_CHAINGUN, ItemType.WEAPON_ROCKET,
            ItemType.WEAPON_PLASMA, ItemType.WEAPON_BFG, ItemType.WEAPON_CHAINSAW -> {
                val colR = if (type == ItemType.WEAPON_CHAINSAW) 235 else if (type == ItemType.WEAPON_BFG) 65 else 95
                val colG = if (type == ItemType.WEAPON_CHAINSAW) 125 else if (type == ItemType.WEAPON_BFG) 185 else 104
                val colB = if (type == ItemType.WEAPON_CHAINSAW) 25 else if (type == ItemType.WEAPON_BFG) 85 else 114
                c.bevelPanel(12, 48, 40, 9, colR, colG, colB, grit = 14)
                c.bevelPanel(18, 54, 10, 6, 115, 75, 45, grit = 8)
            }
            else -> {
                // Ammo pickups (clips, shells, rockets, cells)
                val isShell = (type == ItemType.SHELL_4 || type == ItemType.SHELL_BOX)
                val isCell = (type == ItemType.CELL_PACK || type == ItemType.CELL_BULK)
                val r = if (isShell) 215 else if (isCell) 55 else 118
                val g = if (isShell) 45 else if (isCell) 195 else 112
                val b = if (isShell) 45 else if (isCell) 95 else 78
                c.bevelPanel(22, 46, 20, 14, r, g, b, grit = 12)
            }
        }
    }

    private fun buildWeapons() {
        for (wType in WeaponType.entries) {
            for (fireIdx in 0..1) {
                val c = PixCanvas(WEAPON_W, WEAPON_H, weaponSprites[wType.ordinal][fireIdx])
                c.clear(0)
                drawFirstPersonWeapon(c, wType, isFiring = (fireIdx == 1))
            }
        }
    }

    private fun drawFirstPersonWeapon(c: PixCanvas, weapon: WeaponType, isFiring: Boolean) {
        val cx = WEAPON_W / 2
        val recoilY = if (isFiring && !weapon.isMelee) 7 else 0

        // Muzzle flash star behind barrel tip!
        if (isFiring && !weapon.isMelee) {
            val (fr, fg, fb) = when (weapon) {
                WeaponType.PLASMA_RIFLE -> Triple(55, 205, 255)
                WeaponType.BFG_9000 -> Triple(55, 255, 110)
                else -> Triple(255, 195, 35)
            }
            c.shadedOval(cx.toFloat(), 28f, 24f, 18f, fr, fg, fb, grit = 24, specular = true)
            c.line(cx - 28, 28, cx + 28, 28, 3, rgb(255, 240, 150))
            c.line(cx, 8, cx, 48, 3, rgb(255, 240, 150))
            c.shadedOval(cx.toFloat(), 28f, 10f, 8f, 255, 255, 245, grit = 0)
        }

        when (weapon) {
            WeaponType.FIST -> {
                val fy = if (isFiring) 34f else 62f
                val fx = if (isFiring) cx.toFloat() else (cx + 16).toFloat()
                // Muscular forearm + brown Doomguy glove + brass knuckles!
                c.shadedLimb(fx + 10f, 95f, fx, fy + 8f, 16f, 13f, 212, 158, 124)
                c.shadedOval(fx, fy, 16f, 13f, 118, 78, 48, grit = 14)
                for (kx in -10..10 step 5) {
                    c.shadedOval(fx + kx, fy - 10f, 3.2f, 3.2f, 215, 180, 55, specular = true)
                }
            }

            WeaponType.CHAINSAW -> {
                val buzz = if (isFiring) 2 else 0
                // Saw steel blade with jagged teeth
                c.shadedLimb((cx + buzz).toFloat(), 22f, cx.toFloat(), 66f, 9f, 13f, 148, 158, 168)
                for (ty in 24..62 step 5) {
                    c.rect(cx - 12 + buzz, ty, 3, 3, rgb(220, 225, 230))
                    c.rect(cx + 9 + buzz, ty, 3, 3, rgb(220, 225, 230))
                }
                // Yellow/Orange motor housing
                c.bevelPanel(cx - 26, 62, 52, 34, 225, 135, 22, grit = 16)
                c.rect(cx - 22, 56, 44, 4, rgb(45, 50, 55))
            }

            WeaponType.PISTOL -> {
                // Foreshortened Beretta-style Doom pistol barrel + front sight + gloved hand
                c.shadedLimb(cx.toFloat(), (34 + recoilY).toFloat(), cx.toFloat(), 76f, 8f, 13f, 96, 106, 118)
                c.rect(cx - 3, 32 + recoilY, 6, 36, rgb(158, 168, 178))
                c.rect(cx - 2, 29 + recoilY, 4, 5, rgb(42, 46, 52)) // Front iron sight
                c.shadedOval(cx.toFloat(), 84f, 20f, 14f, 118, 76, 46, grit = 14)
            }

            WeaponType.SHOTGUN -> {
                // Iconic Doom Pump-Action Shotgun with wooden pump grip & steel barrel
                c.shadedLimb(cx.toFloat(), (26 + recoilY).toFloat(), cx.toFloat(), 82f, 7f, 15f, 74, 82, 92)
                c.rect(cx - 2, 24 + recoilY, 4, 48, rgb(148, 158, 168))
                c.rect(cx - 1, 21 + recoilY, 2, 4, rgb(210, 180, 60)) // Brass bead sight
                // Ribbed brown wooden fore-end grip
                c.bevelPanel(cx - 14, 56 + recoilY, 28, 22, 128, 78, 42, grit = 16)
                for (ry in 59..74 step 4) {
                    c.rect(cx - 13, ry + recoilY, 26, 2, rgb(78, 46, 24))
                }
                c.shadedOval(cx.toFloat(), 88f, 22f, 12f, 112, 72, 44, grit = 12)
            }

            WeaponType.CHAINGUN -> {
                // Multi-barrel rotary Vulcan Chaingun!
                c.bevelPanel(cx - 24, 56 + recoilY, 48, 40, 78, 86, 96, grit = 16)
                for (bx in intArrayOf(-13, -4, 5, 14)) {
                    c.shadedLimb((cx + bx).toFloat(), (28 + recoilY).toFloat(), (cx + bx).toFloat(), (58 + recoilY).toFloat(), 3.8f, 4.2f, 132, 142, 154)
                }
                c.rect(cx - 20, 38 + recoilY, 40, 5, rgb(45, 52, 60))
                c.rect(cx - 20, 50 + recoilY, 40, 5, rgb(45, 52, 60))
            }

            WeaponType.ROCKET_LAUNCHER -> {
                // Heavy centered Doom Rocket Launcher tube
                c.shadedLimb(cx.toFloat(), (22 + recoilY).toFloat(), cx.toFloat(), 95f, 15f, 23f, 98, 86, 74)
                c.shadedOval(cx.toFloat(), (24 + recoilY).toFloat(), 12f, 7f, 28, 28, 32, grit = 6)
                c.rect(cx - 18, 52 + recoilY, 36, 5, rgb(185, 145, 35))
            }

            WeaponType.PLASMA_RIFLE -> {
                // Ribbed futuristic UAC Plasma Rifle with glowing blue coils
                c.shadedLimb(cx.toFloat(), (28 + recoilY).toFloat(), cx.toFloat(), 95f, 14f, 24f, 126, 118, 106)
                for (ry in 34..66 step 6) {
                    c.rect(cx - 14, ry + recoilY, 28, 3, rgb(45, 195, 255))
                }
            }

            WeaponType.BFG_9000 -> {
                // Massive BFG 9000 cannon with glowing green plasma core!
                c.bevelPanel(cx - 38, 42 + recoilY, 76, 54, 96, 106, 114, grit = 18)
                c.bevelPanel(cx - 26, 26 + recoilY, 52, 22, 74, 82, 90, grit = 14)
                c.shadedOval(cx.toFloat(), (52 + recoilY).toFloat(), 16f, 12f, 45, 245, 95, specular = true)
            }
        }
    }

    fun getWallTexture(tile: Int, animFrame: Int): IntArray {
        ensureInitialized()
        val safeTile = tile.coerceIn(1, 12)
        return if (animFrame % 2 == 0) wallTexturesFrame0[safeTile] else wallTexturesFrame1[safeTile]
    }

    fun getAcidTexture(animFrame: Int): IntArray {
        ensureInitialized()
        return if (animFrame % 2 == 0) acidTex0 else acidTex1
    }

    fun getMonsterSprite(type: MonsterType, state: MonsterState, animFrame: Int): IntArray {
        ensureInitialized()
        val f = when (state) {
            MonsterState.DEAD -> 5
            MonsterState.DYING -> 4
            MonsterState.PAIN -> 3
            MonsterState.ATTACK -> 2
            MonsterState.CHASE -> animFrame and 1
            MonsterState.IDLE -> 0
        }
        return monsterSprites[type.ordinal][f]
    }

    fun getItemSprite(type: ItemType, isExplodingBarrel: Boolean, animFrame: Int): IntArray {
        ensureInitialized()
        if (type == ItemType.BARREL) {
            if (isExplodingBarrel) return barrelExplodeSprite
            return if (animFrame % 2 == 0) itemSprites[type.ordinal] else barrelAltSprite
        }
        return itemSprites[type.ordinal]
    }

    fun getWeaponSprite(weapon: WeaponType, isFiring: Boolean, hasBerserk: Boolean): IntArray {
        ensureInitialized()
        return weaponSprites[weapon.ordinal][if (isFiring) 1 else 0]
    }

    private val faceScratch = IntArray(FACE_SIZE * FACE_SIZE)

    fun renderDoomguyFacePixels(
        health: Int,
        isGod: Boolean,
        lookDir: Int,
        isPain: Boolean,
        isGrin: Boolean
    ): IntArray {
        ensureInitialized()
        val c = PixCanvas(FACE_SIZE, FACE_SIZE, faceScratch)
        c.bevelPanel(0, 0, FACE_SIZE, FACE_SIZE, 42, 46, 52, grit = 10, seed = 777)

        val dead = (health <= 0)
        val skinR = if (dead) 142 else 218
        val skinG = if (dead) 128 else 166
        val skinB = if (dead) 118 else 132

        // Neck & Shoulders
        c.shadedOval(24f, 44f, 16f, 7f, 65, 82, 62, grit = 10)
        // Chiseled Head & Jawline
        c.shadedOval(24f, 25f, 13.5f, 16.5f, skinR, skinG, skinB, grit = 12)
        c.bevelPanel(14, 24, 20, 14, skinR - 12, skinG - 14, skinB - 12, grit = 10)
        // Brown military buzzcut hair
        c.shadedOval(24f, 10f, 13.5f, 5.5f, 92, 62, 40, grit = 18)
        c.rect(11, 10, 3, 9, rgb(82, 54, 34))
        c.rect(34, 10, 3, 9, rgb(82, 54, 34))

        // Furrowed eyebrows
        c.line(14, 18, 22, 20, 2, rgb(74, 48, 30))
        c.line(34, 18, 26, 20, 2, rgb(74, 48, 30))

        // Eyes
        val ex = lookDir.coerceIn(-1, 1) * 2
        if (dead) {
            c.rect(15, 22, 6, 2, rgb(65, 30, 30))
            c.rect(27, 22, 6, 2, rgb(65, 30, 30))
        } else if (isGod) {
            c.rect(15, 21, 6, 4, rgb(255, 235, 45))
            c.rect(27, 21, 6, 4, rgb(255, 235, 45))
            c.rect(17, 22, 2, 2, rgb(255, 255, 255))
            c.rect(29, 22, 2, 2, rgb(255, 255, 255))
        } else {
            c.rect(15, 21, 6, 4, rgb(238, 238, 242))
            c.rect(27, 21, 6, 4, rgb(238, 238, 242))
            c.rect(17 + ex, 21, 3, 4, rgb(32, 55, 95))
            c.rect(29 + ex, 21, 3, 4, rgb(32, 55, 95))
        }

        // Nose bridge & shadow
        c.shadedLimb(24f, 21f, 24f, 29f, 2.2f, 3.0f, skinR - 8, skinG - 12, skinB - 12)

        // Progressive Blood Splatters as health drops!
        if (!isGod) {
            if (health < 80) {
                c.rect(23, 30, 3, 4, rgb(185, 20, 20))
            }
            if (health < 60) {
                c.shadedOval(16f, 16f, 3.5f, 2.5f, 175, 18, 18)
                c.rect(15, 18, 2, 6, rgb(165, 15, 15))
            }
            if (health < 40) {
                c.shadedOval(32f, 29f, 4.0f, 3.5f, 185, 18, 18)
                c.shadedOval(16f, 33f, 3.5f, 3.0f, 170, 15, 15)
            }
            if (health < 20) {
                c.shadedOval(24f, 39f, 7.0f, 3.0f, 165, 14, 14)
            }
        }

        // Mouth (Evil weapon grin, Pain scream, or Grit teeth)
        if (isPain || dead) {
            c.rect(18, 33, 12, 6, rgb(75, 18, 18))
            c.rect(19, 33, 10, 2, rgb(235, 230, 220))
        } else if (isGrin) {
            c.rect(16, 33, 16, 5, rgb(55, 25, 25))
            for (tx in 17..30 step 3) {
                c.rect(tx, 34, 2, 3, rgb(248, 248, 242))
            }
        } else {
            c.rect(18, 34, 12, 3, rgb(95, 48, 42))
            c.rect(19, 35, 10, 1, rgb(230, 225, 215))
        }

        return faceScratch
    }
}
