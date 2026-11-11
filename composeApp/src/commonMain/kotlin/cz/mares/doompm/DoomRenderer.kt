package cz.mares.doompm

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object DoomRenderer {
    private const val NUM_RAYS = 160
    private val zBuffer = FloatArray(NUM_RAYS)

    fun DrawScope.render3DView(engine: DoomEngine) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return

        val halfH = h * 0.5f
        val mapDef = engine.currentMapDef
        val isInvuln = engine.invulnerableTimer > 0f

        // 1. Sky / Ceiling and Shaded Floor
        if (isInvuln) {
            drawRect(color = Color(0xFFDCDCDC), topLeft = Offset.Zero, size = Size(w, halfH))
            drawRect(color = Color(0xFF9E9E9E), topLeft = Offset(0f, halfH), size = Size(w, halfH))
        } else {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(mapDef.skyColorTop), Color(mapDef.skyColorBottom), Color(0xFF14171C)),
                    startY = 0f,
                    endY = halfH
                ),
                topLeft = Offset.Zero,
                size = Size(w, halfH)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF101215), Color(mapDef.floorColor)),
                    startY = halfH,
                    endY = h
                ),
                topLeft = Offset(0f, halfH),
                size = Size(w, halfH)
            )
        }

        val dirX = cos(engine.playerAngle)
        val dirY = sin(engine.playerAngle)
        val planeScale = 0.66f
        val planeX = -dirY * planeScale
        val planeY = dirX * planeScale

        val colWidth = w / NUM_RAYS.toFloat()

        // 2. DDA Raycasting
        for (col in 0 until NUM_RAYS) {
            val cameraX = 2f * col.toFloat() / NUM_RAYS.toFloat() - 1f
            val rayDirX = dirX + planeX * cameraX
            val rayDirY = dirY + planeY * cameraX

            var mapX = floor(engine.playerX).toInt()
            var mapY = floor(engine.playerY).toInt()

            val deltaDistX = if (abs(rayDirX) < 1e-6f) 1e6f else abs(1f / rayDirX)
            val deltaDistY = if (abs(rayDirY) < 1e-6f) 1e6f else abs(1f / rayDirY)

            val stepX: Int
            var sideDistX: Float
            if (rayDirX < 0f) {
                stepX = -1
                sideDistX = (engine.playerX - mapX) * deltaDistX
            } else {
                stepX = 1
                sideDistX = (mapX + 1f - engine.playerX) * deltaDistX
            }

            val stepY: Int
            var sideDistY: Float
            if (rayDirY < 0f) {
                stepY = -1
                sideDistY = (engine.playerY - mapY) * deltaDistY
            } else {
                stepY = 1
                sideDistY = (mapY + 1f - engine.playerY) * deltaDistY
            }

            var hitTile = 0
            var side = 0
            var sawAcidFloor = false
            var acidDist = 0f
            var sawTeleporter = false
            var teleDist = 0f

            var steps = 0
            while (steps < 42) {
                steps++
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX
                    mapX += stepX
                    side = 0
                } else {
                    sideDistY += deltaDistY
                    mapY += stepY
                    side = 1
                }

                if (mapX !in 0 until engine.gridSize || mapY !in 0 until engine.gridSize) {
                    hitTile = 1
                    break
                }

                val t = engine.grid[mapY][mapX]
                if (t == 13 && !sawAcidFloor) {
                    sawAcidFloor = true
                    acidDist = if (side == 0) (sideDistX - deltaDistX) else (sideDistY - deltaDistY)
                } else if (t == 15 && !sawTeleporter) {
                    sawTeleporter = true
                    teleDist = if (side == 0) (sideDistX - deltaDistX) else (sideDistY - deltaDistY)
                }

                if (t in 1..5 || t == 11 || t == 12) {
                    hitTile = t
                    break
                } else if (t in 6..10) {
                    val door = engine.doors[mapY * engine.gridSize + mapX]
                    val openAmt = door?.openAmount ?: 0f
                    if (openAmt < 0.92f) {
                        val perpTmp = if (side == 0) (sideDistX - deltaDistX) else (sideDistY - deltaDistY)
                        val wxTmp = if (side == 0) {
                            engine.playerY + perpTmp * rayDirY
                        } else {
                            engine.playerX + perpTmp * rayDirX
                        }
                        val fract = wxTmp - floor(wxTmp)
                        if (fract >= openAmt) {
                            hitTile = t
                            break
                        }
                    }
                }
            }

            val perpWallDist = (if (side == 0) (sideDistX - deltaDistX) else (sideDistY - deltaDistY))
                .coerceAtLeast(0.12f)
            zBuffer[col] = perpWallDist

            val lineHeight = (h / perpWallDist)
            val drawStart = (halfH - lineHeight * 0.5f).coerceAtLeast(0f)
            val drawEnd = (halfH + lineHeight * 0.5f).coerceAtMost(h)
            val xLeft = col * colWidth
            val sliceW = colWidth + 1.2f

            if (sawAcidFloor && acidDist > 0.35f && !isInvuln) {
                val acidLineH = h / acidDist
                val acidY = (halfH + acidLineH * 0.48f).coerceIn(drawEnd, h)
                val pulse = (sin(engine.levelTimeSec * 4.5f + col * 0.2f) * 0.15f + 0.85f)
                drawRect(
                    color = Color(0f, (0.68f * pulse).coerceIn(0f, 1f), 0.18f, 0.82f),
                    topLeft = Offset(xLeft, drawEnd),
                    size = Size(sliceW, (acidY - drawEnd).coerceAtLeast(4f))
                )
            }
            if (sawTeleporter && teleDist > 0.35f && !isInvuln) {
                val teleLineH = h / teleDist
                val teleY = (halfH + teleLineH * 0.48f).coerceIn(drawEnd, h)
                drawRect(
                    color = Color(0.95f, 0.15f, 0.2f, 0.85f),
                    topLeft = Offset(xLeft, drawEnd),
                    size = Size(sliceW, (teleY - drawEnd).coerceAtLeast(4f))
                )
            }

            val rawWallX = if (side == 0) {
                engine.playerY + perpWallDist * rayDirY
            } else {
                engine.playerX + perpWallDist * rayDirX
            }
            val wallU = rawWallX - floor(rawWallX)

            val distShade = (1f / (1f + perpWallDist * 0.10f)).coerceIn(0.18f, 1f)
            val sideShade = if (side == 1) 0.76f else 1.0f
            val totalShade = distShade * sideShade

            drawWallColumn(
                tile = hitTile,
                xLeft = xLeft,
                sliceW = sliceW,
                drawStart = drawStart,
                drawEnd = drawEnd,
                wallU = wallU,
                shade = totalShade,
                isInvuln = isInvuln,
                timeSec = engine.levelTimeSec
            )
        }

        // 3. Render Sprites
        renderSprites(
            engine = engine,
            w = w,
            h = h,
            halfH = halfH,
            dirX = dirX,
            dirY = dirY,
            planeX = planeX,
            planeY = planeY,
            colWidth = colWidth,
            isInvuln = isInvuln
        )

        // 4. First-Person Weapon with Marine Gloves
        if (!engine.isPlayerDead) {
            renderFirstPersonWeapon(engine, w, h)
        }

        // 5. Screen Flash Effects
        if (engine.damageFlashTimer > 0f) {
            val alpha = (engine.damageFlashTimer * 0.85f).coerceIn(0f, 0.55f)
            drawRect(color = Color(1f, 0.08f, 0.05f, alpha), topLeft = Offset.Zero, size = Size(w, h))
        }
        if (engine.pickupFlashTimer > 0f) {
            val alpha = (engine.pickupFlashTimer * 0.9f).coerceIn(0f, 0.35f)
            drawRect(color = Color(1f, 0.85f, 0.2f, alpha), topLeft = Offset.Zero, size = Size(w, h))
        }
        if (engine.teleportFlashTimer > 0f) {
            val alpha = (engine.teleportFlashTimer * 0.95f).coerceIn(0f, 0.6f)
            drawRect(color = Color(0.2f, 1f, 0.35f, alpha), topLeft = Offset.Zero, size = Size(w, h))
        }
        if (engine.radSuitTimer > 0f) {
            drawRect(color = Color(0.1f, 0.85f, 0.2f, 0.14f), topLeft = Offset.Zero, size = Size(w, h))
        }

        // Crosshair
        val cx = w * 0.5f
        val cy = halfH
        drawLine(Color(0xBB00FF66), Offset(cx - 8f, cy), Offset(cx + 8f, cy), strokeWidth = 2f)
        drawLine(Color(0xBB00FF66), Offset(cx, cy - 8f), Offset(cx, cy + 8f), strokeWidth = 2f)
    }

    private fun DrawScope.drawWallColumn(
        tile: Int,
        xLeft: Float,
        sliceW: Float,
        drawStart: Float,
        drawEnd: Float,
        wallU: Float,
        shade: Float,
        isInvuln: Boolean,
        timeSec: Float
    ) {
        val totalH = (drawEnd - drawStart).coerceAtLeast(1f)
        val texX = (wallU * 16f).toInt().coerceIn(0, 15)

        fun shadedColor(r: Int, g: Int, b: Int): Color {
            if (isInvuln) {
                val lum = ((r + g + b) / 3 * shade).toInt().coerceIn(30, 245)
                return Color(lum, lum, lum)
            }
            return Color(
                (r * shade).toInt().coerceIn(0, 255),
                (g * shade).toInt().coerceIn(0, 255),
                (b * shade).toInt().coerceIn(0, 255)
            )
        }

        val baseCol = when (tile) {
            1 -> if (texX == 0 || texX == 8 || texX == 15) shadedColor(58, 68, 78) else shadedColor(118, 130, 142)
            2 -> if ((texX % 4) == 0) shadedColor(76, 72, 65) else shadedColor(124, 116, 104)
            3 -> if ((texX % 4) == 0) shadedColor(88, 24, 24) else shadedColor(164, 44, 38)
            4 -> shadedColor(78, 104, 80)
            5 -> shadedColor(62, 78, 98)
            6 -> if (texX in 7..8) shadedColor(44, 48, 54) else shadedColor(142, 148, 156)
            7 -> if (texX in 1..2 || texX in 13..14) shadedColor(35, 135, 255) else shadedColor(95, 115, 145)
            8 -> if (texX in 1..2 || texX in 13..14) shadedColor(255, 215, 20) else shadedColor(135, 122, 92)
            9 -> if (texX in 1..2 || texX in 13..14) shadedColor(255, 40, 50) else shadedColor(145, 85, 85)
            10 -> if (texX == 7 || texX == 8) shadedColor(86, 98, 110) else shadedColor(116, 128, 140)
            11 -> shadedColor(135, 52, 52)
            12 -> shadedColor(120, 46, 155)
            else -> shadedColor(100, 100, 100)
        }

        drawRect(
            color = baseCol,
            topLeft = Offset(xLeft, drawStart),
            size = Size(sliceW, totalH)
        )

        val trimH = (totalH * 0.11f).coerceAtLeast(2f)
        drawRect(
            color = shadedColor(42, 46, 52),
            topLeft = Offset(xLeft, drawStart),
            size = Size(sliceW, trimH)
        )
        drawRect(
            color = shadedColor(36, 40, 46),
            topLeft = Offset(xLeft, drawEnd - trimH),
            size = Size(sliceW, trimH)
        )

        when (tile) {
            1 -> {
                drawRect(
                    color = shadedColor(48, 55, 64),
                    topLeft = Offset(xLeft, drawStart + totalH * 0.48f),
                    size = Size(sliceW, (totalH * 0.05f).coerceAtLeast(1.5f))
                )
            }
            3 -> {
                drawRect(
                    color = shadedColor(58, 16, 16),
                    topLeft = Offset(xLeft, drawStart + totalH * 0.33f),
                    size = Size(sliceW, (totalH * 0.04f).coerceAtLeast(1.5f))
                )
                drawRect(
                    color = shadedColor(58, 16, 16),
                    topLeft = Offset(xLeft, drawStart + totalH * 0.66f),
                    size = Size(sliceW, (totalH * 0.04f).coerceAtLeast(1.5f))
                )
            }
            4 -> {
                if (texX in 4..11) {
                    drawRect(
                        color = shadedColor(42, 230, 80),
                        topLeft = Offset(xLeft, drawStart + totalH * 0.42f),
                        size = Size(sliceW, totalH * 0.16f)
                    )
                }
            }
            5 -> {
                if (texX in 3..12 && texX % 2 == 0) {
                    val blink = ((timeSec * 4f + texX).toInt() % 2 == 0)
                    val ledCol = if (blink) shadedColor(40, 245, 120) else shadedColor(255, 180, 30)
                    drawRect(
                        color = ledCol,
                        topLeft = Offset(xLeft, drawStart + totalH * 0.32f),
                        size = Size(sliceW, totalH * 0.28f)
                    )
                }
            }
            6, 7, 8, 9 -> {
                val barCol = when (tile) {
                    7 -> shadedColor(30, 135, 255)
                    8 -> shadedColor(255, 215, 20)
                    9 -> shadedColor(255, 45, 55)
                    else -> shadedColor(205, 160, 45)
                }
                drawRect(
                    color = barCol,
                    topLeft = Offset(xLeft, drawStart + totalH * 0.44f),
                    size = Size(sliceW, totalH * 0.12f)
                )
            }
            11, 12 -> {
                if (texX in 5..10) {
                    val swCol = if (tile == 12) shadedColor(255, 215, 0) else shadedColor(50, 250, 90)
                    drawRect(
                        color = shadedColor(32, 32, 38),
                        topLeft = Offset(xLeft, drawStart + totalH * 0.32f),
                        size = Size(sliceW, totalH * 0.36f)
                    )
                    if (texX in 6..9) {
                        drawRect(
                            color = swCol,
                            topLeft = Offset(xLeft, drawStart + totalH * 0.40f),
                            size = Size(sliceW, totalH * 0.20f)
                        )
                    }
                }
            }
        }
    }

    private class SpriteRenderEntry(
        val x: Float,
        val y: Float,
        val distSq: Float,
        val kind: Int,
        val monster: MonsterEntity? = null,
        val item: MapItemEntity? = null,
        val projectile: ProjectileEntity? = null
    )

    private fun DrawScope.renderSprites(
        engine: DoomEngine,
        w: Float,
        h: Float,
        halfH: Float,
        dirX: Float,
        dirY: Float,
        planeX: Float,
        planeY: Float,
        colWidth: Float,
        isInvuln: Boolean
    ) {
        val entries = ArrayList<SpriteRenderEntry>(engine.monsters.size + engine.items.size + engine.projectiles.size)

        for (m in engine.monsters) {
            val dx = m.x - engine.playerX
            val dy = m.y - engine.playerY
            entries.add(SpriteRenderEntry(m.x, m.y, dx * dx + dy * dy, 1, monster = m))
        }
        for (it in engine.items) {
            if (it.collected) continue
            val dx = it.x - engine.playerX
            val dy = it.y - engine.playerY
            entries.add(SpriteRenderEntry(it.x, it.y, dx * dx + dy * dy, 2, item = it))
        }
        for (p in engine.projectiles) {
            val dx = p.x - engine.playerX
            val dy = p.y - engine.playerY
            entries.add(SpriteRenderEntry(p.x, p.y, dx * dx + dy * dy, 3, projectile = p))
        }

        entries.sortByDescending { it.distSq }

        val invDet = 1.0f / (planeX * dirY - dirX * planeY)

        for (entry in entries) {
            val spriteX = entry.x - engine.playerX
            val spriteY = entry.y - engine.playerY

            val transformX = invDet * (dirY * spriteX - dirX * spriteY)
            val transformY = invDet * (-planeY * spriteX + planeX * spriteY)

            if (transformY <= 0.18f) continue

            val spriteScreenX = (w * 0.5f) * (1f + transformX / transformY)
            val baseSpriteH = abs(h / transformY)

            val centerCol = (spriteScreenX / colWidth).toInt()
            if (centerCol !in -25 until (NUM_RAYS + 25)) continue

            val halfCols = ((baseSpriteH * 0.45f) / colWidth).toInt().coerceAtLeast(1)
            val minCol = (centerCol - halfCols).coerceIn(0, NUM_RAYS - 1)
            val maxCol = (centerCol + halfCols).coerceIn(0, NUM_RAYS - 1)
            var visibleCols = 0
            for (c in minCol..maxCol) {
                if (transformY < zBuffer[c]) visibleCols++
            }
            if (visibleCols == 0) continue

            val shade = (1f / (1f + transformY * 0.09f)).coerceIn(0.25f, 1f)

            when (entry.kind) {
                1 -> entry.monster?.let { m ->
                    drawMonsterSprite(m, spriteScreenX, halfH, baseSpriteH, shade, isInvuln)
                }
                2 -> entry.item?.let { item ->
                    drawItemSprite(item, spriteScreenX, halfH, baseSpriteH, shade)
                }
                3 -> entry.projectile?.let { proj ->
                    drawProjectileSprite(proj, spriteScreenX, halfH, baseSpriteH)
                }
            }
        }
    }

    private fun DrawScope.drawMonsterSprite(
        m: MonsterEntity,
        screenX: Float,
        halfH: Float,
        baseH: Float,
        shade: Float,
        isInvuln: Boolean
    ) {
        val scale = when (m.type) {
            MonsterType.CYBERDEMON -> 1.18f
            MonsterType.BARON_OF_HELL -> 1.00f
            MonsterType.CACODEMON -> 0.86f
            MonsterType.LOST_SOUL -> 0.56f
            else -> 0.84f
        }
        val mh = baseH * scale
        val mw = mh * 0.70f
        val bottomY = halfH + baseH * 0.48f

        if (m.state == MonsterState.DEAD) {
            val corpseH = mh * 0.22f
            drawRoundRect(
                color = Color(0.58f * shade, 0.08f * shade, 0.08f * shade),
                topLeft = Offset(screenX - mw * 0.52f, bottomY - corpseH),
                size = Size(mw * 1.04f, corpseH),
                cornerRadius = CornerRadius(corpseH * 0.4f, corpseH * 0.4f)
            )
            return
        }

        if (m.state == MonsterState.DYING) {
            val collapse = (m.stateTimer / 0.55f).coerceIn(0.2f, 1f)
            val curH = mh * collapse
            drawRoundRect(
                color = Color(0.88f, 0.2f, 0.1f, 0.92f),
                topLeft = Offset(screenX - mw * 0.45f, bottomY - curH),
                size = Size(mw * 0.9f, curH),
                cornerRadius = CornerRadius(8f, 8f)
            )
            return
        }

        val walkOffset = sin(m.animPhase) * (mh * 0.03f)
        val topY = (if (m.type == MonsterType.CACODEMON || m.type == MonsterType.LOST_SOUL) {
            bottomY - mh - baseH * 0.18f
        } else {
            bottomY - mh
        }) + walkOffset

        val alpha = if (m.type.isPartialInvisible) 0.36f else 1.0f
        val bodyColor = when {
            m.state == MonsterState.PAIN -> Color(1f, 0.9f, 0.9f, alpha)
            isInvuln -> Color(0.3f, 0.3f, 0.3f, alpha)
            m.type == MonsterType.ZOMBIEMAN -> Color(0.46f * shade, 0.58f * shade, 0.38f * shade, alpha)
            m.type == MonsterType.SHOTGUN_GUY -> Color(0.26f * shade, 0.28f * shade, 0.32f * shade, alpha)
            m.type == MonsterType.IMP -> Color(0.60f * shade, 0.36f * shade, 0.20f * shade, alpha)
            m.type == MonsterType.PINKY_DEMON -> Color(0.90f * shade, 0.42f * shade, 0.52f * shade, alpha)
            m.type == MonsterType.SPECTRE -> Color(0.25f, 0.28f, 0.32f, 0.36f)
            m.type == MonsterType.LOST_SOUL -> Color(0.96f, 0.65f, 0.18f, 0.92f)
            m.type == MonsterType.CACODEMON -> Color(0.90f * shade, 0.16f * shade, 0.18f * shade, alpha)
            m.type == MonsterType.BARON_OF_HELL -> Color(0.80f * shade, 0.28f * shade, 0.32f * shade, alpha)
            m.type == MonsterType.CYBERDEMON -> Color(0.68f * shade, 0.38f * shade, 0.28f * shade, alpha)
            else -> Color.Red
        }

        if (m.type == MonsterType.CACODEMON) {
            drawOval(
                color = bodyColor,
                topLeft = Offset(screenX - mw * 0.55f, topY),
                size = Size(mw * 1.1f, mh * 0.95f)
            )
            // Horns
            val hornPath = Path().apply {
                moveTo(screenX - mw * 0.35f, topY + mh * 0.15f)
                lineTo(screenX - mw * 0.48f, topY - mh * 0.10f)
                lineTo(screenX - mw * 0.18f, topY + mh * 0.08f)
                close()
                moveTo(screenX + mw * 0.35f, topY + mh * 0.15f)
                lineTo(screenX + mw * 0.48f, topY - mh * 0.10f)
                lineTo(screenX + mw * 0.18f, topY + mh * 0.08f)
                close()
            }
            drawPath(hornPath, Color(0xFFECEFF1))
            drawCircle(
                color = Color(0xFF00FF66),
                radius = mw * 0.16f,
                center = Offset(screenX, topY + mh * 0.34f)
            )
            drawRoundRect(
                color = Color(0xFF2979FF),
                topLeft = Offset(screenX - mw * 0.32f, topY + mh * 0.60f),
                size = Size(mw * 0.64f, mh * 0.20f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        } else {
            val legW = mw * 0.28f
            val legH = mh * 0.35f
            val legSwing = sin(m.animPhase) * (legH * 0.2f)
            val rightLegCol = if (m.type == MonsterType.CYBERDEMON) Color(0xFF78909C) else bodyColor

            drawRect(
                color = bodyColor,
                topLeft = Offset(screenX - mw * 0.34f, bottomY - legH + legSwing),
                size = Size(legW, legH)
            )
            drawRect(
                color = rightLegCol,
                topLeft = Offset(screenX + mw * 0.06f, bottomY - legH - legSwing),
                size = Size(legW, legH)
            )
            drawRoundRect(
                color = bodyColor,
                topLeft = Offset(screenX - mw * 0.45f, topY + mh * 0.22f),
                size = Size(mw * 0.90f, mh * 0.48f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = bodyColor,
                topLeft = Offset(screenX - mw * 0.26f, topY),
                size = Size(mw * 0.52f, mh * 0.26f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Horns for Baron, Cyberdemon, and Imp spikes
            if (m.type == MonsterType.BARON_OF_HELL || m.type == MonsterType.CYBERDEMON || m.type == MonsterType.IMP) {
                val hornCol = Color(0xFFD7CCC8)
                drawLine(
                    color = hornCol,
                    start = Offset(screenX - mw * 0.20f, topY + mh * 0.04f),
                    end = Offset(screenX - mw * 0.34f, topY - mh * 0.12f),
                    strokeWidth = (mw * 0.08f).coerceAtLeast(2f)
                )
                drawLine(
                    color = hornCol,
                    start = Offset(screenX + mw * 0.20f, topY + mh * 0.04f),
                    end = Offset(screenX + mw * 0.34f, topY - mh * 0.12f),
                    strokeWidth = (mw * 0.08f).coerceAtLeast(2f)
                )
            }

            // Weapon held by zombies / Cyberdemon rocket arm
            if (m.type == MonsterType.ZOMBIEMAN || m.type == MonsterType.SHOTGUN_GUY || m.type == MonsterType.CYBERDEMON) {
                drawRect(
                    color = Color(0xFF455A64),
                    topLeft = Offset(screenX - mw * 0.35f, topY + mh * 0.40f),
                    size = Size(mw * 0.75f, mh * 0.12f)
                )
            }

            val eyeCol = if (m.type == MonsterType.BARON_OF_HELL) Color(0xFF00FF66) else Color(0xFFFF1744)
            drawCircle(eyeCol, radius = (mw * 0.06f).coerceAtLeast(1.5f), center = Offset(screenX - mw * 0.1f, topY + mh * 0.11f))
            drawCircle(eyeCol, radius = (mw * 0.06f).coerceAtLeast(1.5f), center = Offset(screenX + mw * 0.1f, topY + mh * 0.11f))

            if (m.state == MonsterState.ATTACK) {
                val flashCol = if (m.type == MonsterType.BARON_OF_HELL) Color(0xFF00FF66) else Color(0xFFFFAB00)
                drawCircle(
                    color = flashCol,
                    radius = mw * 0.22f,
                    center = Offset(screenX + mw * 0.35f, topY + mh * 0.42f)
                )
            }
        }

        if (m.hp < m.type.maxHp && m.hp > 0) {
            val barW = mw * 0.85f
            val barH = 4f
            val ratio = (m.hp.toFloat() / m.type.maxHp.toFloat()).coerceIn(0f, 1f)
            drawRect(
                color = Color(0xAA000000),
                topLeft = Offset(screenX - barW * 0.5f, topY - 8f),
                size = Size(barW, barH)
            )
            drawRect(
                color = Color(0xFFFF1744),
                topLeft = Offset(screenX - barW * 0.5f, topY - 8f),
                size = Size(barW * ratio, barH)
            )
        }
    }

    private fun DrawScope.drawItemSprite(
        item: MapItemEntity,
        screenX: Float,
        halfH: Float,
        baseH: Float,
        shade: Float
    ) {
        val bottomY = halfH + baseH * 0.48f
        if (item.type == ItemType.BARREL) {
            if (item.barrelHp <= 0) {
                drawCircle(
                    color = Color(0xFFFF6D00),
                    radius = baseH * 0.35f,
                    center = Offset(screenX, bottomY - baseH * 0.28f)
                )
                drawCircle(
                    color = Color(0xFFFFEA00),
                    radius = baseH * 0.20f,
                    center = Offset(screenX, bottomY - baseH * 0.28f)
                )
                return
            }
            val bw = baseH * 0.34f
            val bh = baseH * 0.46f
            drawRoundRect(
                color = Color(0.32f * shade, 0.38f * shade, 0.30f * shade),
                topLeft = Offset(screenX - bw * 0.5f, bottomY - bh),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawOval(
                color = Color(0xFF00E676),
                topLeft = Offset(screenX - bw * 0.42f, bottomY - bh),
                size = Size(bw * 0.84f, bh * 0.22f)
            )
            return
        }

        val iw = baseH * 0.28f
        val ih = baseH * 0.28f
        val color = when (item.type) {
            ItemType.KEY_BLUE, ItemType.SOUL_SPHERE, ItemType.BLUE_ARMOR, ItemType.HEALTH_BONUS -> Color(0xFF2979FF)
            ItemType.KEY_YELLOW, ItemType.AMMO_CLIP, ItemType.AMMO_BOX -> Color(0xFFFFD600)
            ItemType.KEY_RED, ItemType.BERSERK, ItemType.SHELL_4, ItemType.SHELL_BOX -> Color(0xFFFF1744)
            ItemType.GREEN_ARMOR, ItemType.ARMOR_BONUS, ItemType.INVULNERABILITY, ItemType.WEAPON_BFG -> Color(0xFF00E676)
            ItemType.STIMPACK, ItemType.MEDIKIT -> Color(0xFFF5F5F5)
            else -> Color(0xFFFF9100)
        }

        drawRoundRect(
            color = color,
            topLeft = Offset(screenX - iw * 0.5f, bottomY - ih),
            size = Size(iw, ih),
            cornerRadius = CornerRadius(4f, 4f)
        )

        if (item.type == ItemType.MEDIKIT || item.type == ItemType.STIMPACK) {
            drawRect(
                color = Color(0xFFD50000),
                topLeft = Offset(screenX - iw * 0.12f, bottomY - ih * 0.8f),
                size = Size(iw * 0.24f, ih * 0.6f)
            )
            drawRect(
                color = Color(0xFFD50000),
                topLeft = Offset(screenX - iw * 0.3f, bottomY - ih * 0.62f),
                size = Size(iw * 0.6f, ih * 0.24f)
            )
        }
    }

    private fun DrawScope.drawProjectileSprite(
        proj: ProjectileEntity,
        screenX: Float,
        halfH: Float,
        baseH: Float
    ) {
        val radius = when (proj.kind) {
            ProjectileKind.BFG_ORB -> baseH * 0.22f
            ProjectileKind.ROCKET -> baseH * 0.14f
            else -> baseH * 0.12f
        }.coerceAtLeast(3f)

        val col = when (proj.kind) {
            ProjectileKind.BFG_ORB, ProjectileKind.BARON_PLASMA -> Color(0xFF00FF66)
            ProjectileKind.PLASMA -> Color(0xFF40C4FF)
            ProjectileKind.CACO_LIGHTNING -> Color(0xFFFF1744)
            else -> Color(0xFFFF9100)
        }
        drawCircle(color = col, radius = radius, center = Offset(screenX, halfH))
        drawCircle(color = Color.White, radius = radius * 0.45f, center = Offset(screenX, halfH))
    }

    private fun DrawScope.renderFirstPersonWeapon(engine: DoomEngine, w: Float, h: Float) {
        val bobX = sin(engine.walkBobPhase) * 12f * engine.walkBobAmplitude
        val bobY = abs(cos(engine.walkBobPhase)) * 9f * engine.walkBobAmplitude
        val recoilY = if (engine.weaponFlashTimer > 0f) 14f else 0f

        val cx = w * 0.5f + bobX
        val baseBottom = h + bobY + recoilY
        val gunW = (minOf(w, h) * 0.30f).coerceAtLeast(68f)
        val gunH = (h * 0.32f).coerceAtLeast(64f)

        if (engine.weaponFlashTimer > 0f && !engine.currentWeapon.isMelee) {
            val flashColor = when (engine.currentWeapon) {
                WeaponType.PLASMA_RIFLE -> Color(0xFF40C4FF)
                WeaponType.BFG_9000 -> Color(0xFF00FF66)
                else -> Color(0xFFFFEA00)
            }
            drawCircle(
                color = flashColor,
                radius = gunW * 0.42f,
                center = Offset(cx, baseBottom - gunH * 0.94f)
            )
            drawCircle(
                color = Color.White,
                radius = gunW * 0.20f,
                center = Offset(cx, baseBottom - gunH * 0.94f)
            )
        }

        val gloveColor = Color(0xFF6D4C41)

        when (engine.currentWeapon) {
            WeaponType.FIST -> {
                val fistCol = if (engine.hasBerserk) Color(0xFFFF5252) else Color(0xFFD7A98C)
                val punchOffset = if (engine.weaponFlashTimer > 0f) -gunH * 0.35f else 0f
                drawRoundRect(
                    color = fistCol,
                    topLeft = Offset(cx - gunW * 0.32f, baseBottom - gunH * 0.65f + punchOffset),
                    size = Size(gunW * 0.64f, gunH * 0.65f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRect(
                    color = gloveColor,
                    topLeft = Offset(cx - gunW * 0.34f, baseBottom - gunH * 0.42f + punchOffset),
                    size = Size(gunW * 0.68f, gunH * 0.22f)
                )
            }
            WeaponType.CHAINSAW -> {
                drawRoundRect(
                    color = Color(0xFF90A4AE),
                    topLeft = Offset(cx - gunW * 0.16f, baseBottom - gunH * 0.94f),
                    size = Size(gunW * 0.32f, gunH * 0.62f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = Color(0xFFFF6D00),
                    topLeft = Offset(cx - gunW * 0.38f, baseBottom - gunH * 0.42f),
                    size = Size(gunW * 0.76f, gunH * 0.42f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
            WeaponType.BFG_9000 -> {
                drawRoundRect(
                    color = Color(0xFF546E7A),
                    topLeft = Offset(cx - gunW * 0.55f, baseBottom - gunH * 0.88f),
                    size = Size(gunW * 1.1f, gunH * 0.88f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                drawCircle(
                    color = Color(0xFF00E676),
                    radius = gunW * 0.24f,
                    center = Offset(cx, baseBottom - gunH * 0.55f)
                )
            }
            else -> {
                val barrelCol = when (engine.currentWeapon) {
                    WeaponType.SHOTGUN -> Color(0xFF37474F)
                    WeaponType.CHAINGUN -> Color(0xFF607D8B)
                    WeaponType.ROCKET_LAUNCHER -> Color(0xFF4E342E)
                    WeaponType.PLASMA_RIFLE -> Color(0xFF37474F)
                    else -> Color(0xFF546E7A)
                }
                drawRoundRect(
                    color = barrelCol,
                    topLeft = Offset(cx - gunW * 0.22f, baseBottom - gunH * 0.90f),
                    size = Size(gunW * 0.44f, gunH * 0.90f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawRect(
                    color = Color(0xFFB0BEC5),
                    topLeft = Offset(cx - gunW * 0.05f, baseBottom - gunH * 0.88f),
                    size = Size(gunW * 0.10f, gunH * 0.55f)
                )
                // Brown marine glove at bottom of gun
                drawRoundRect(
                    color = gloveColor,
                    topLeft = Offset(cx - gunW * 0.28f, baseBottom - gunH * 0.26f),
                    size = Size(gunW * 0.56f, gunH * 0.26f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }
    }

    fun DrawScope.renderAutomap(engine: DoomEngine) {
        val w = size.width
        val h = size.height
        drawRect(color = Color(0xEB080A0C), topLeft = Offset.Zero, size = Size(w, h))

        val mapDim = minOf(w, h) * 0.90f
        val cell = mapDim / engine.gridSize.toFloat()
        val startX = (w - mapDim) * 0.5f
        val startY = (h - mapDim) * 0.5f

        val showAll = engine.hasComputerMap || engine.fullMapCheat

        for (y in 0 until engine.gridSize) {
            for (x in 0 until engine.gridSize) {
                if (!showAll && !engine.explored[y][x]) continue
                val t = engine.grid[y][x]
                val col = when (t) {
                    1, 2, 3, 4, 5 -> Color(0xFFD32F2F)
                    6 -> Color(0xFFB0BEC5)
                    7 -> Color(0xFF2979FF)
                    8 -> Color(0xFFFFD600)
                    9 -> Color(0xFFFF1744)
                    10 -> if (engine.isSecretCellRevealedOnMap(x, y)) Color(0xFFE040FB) else Color(0xFFD32F2F)
                    14 -> if (engine.isSecretCellRevealedOnMap(x, y)) Color(0xFFAA00FF) else Color(0xFF1A2026)
                    11, 12 -> Color(0xFF00E676)
                    13 -> Color(0xFF1B5E20)
                    15 -> Color(0xFFFF6D00)
                    else -> Color(0xFF1A2026)
                }
                drawRect(
                    color = col,
                    topLeft = Offset(startX + x * cell, startY + y * cell),
                    size = Size((cell - 1f).coerceAtLeast(1f), (cell - 1f).coerceAtLeast(1f))
                )
            }
        }

        if (engine.fullMapCheat) {
            for (m in engine.monsters) {
                if (m.state != MonsterState.DEAD) {
                    drawCircle(
                        color = Color(0xFFFF5252),
                        radius = cell * 0.3f,
                        center = Offset(startX + m.x * cell, startY + m.y * cell)
                    )
                }
            }
        }

        val px = startX + engine.playerX * cell
        val py = startY + engine.playerY * cell
        val dirX = cos(engine.playerAngle)
        val dirY = sin(engine.playerAngle)
        drawCircle(color = Color.White, radius = cell * 0.35f, center = Offset(px, py))
        drawLine(
            color = Color(0xFF00E676),
            start = Offset(px, py),
            end = Offset(px + dirX * cell * 1.1f, py + dirY * cell * 1.1f),
            strokeWidth = 3f
        )
    }

    fun DrawScope.renderDoomguyFace(engine: DoomEngine) {
        val w = size.width
        val h = size.height
        drawRect(color = Color(0xFF22252A), topLeft = Offset.Zero, size = Size(w, h))

        val skinColor = if (engine.playerHealth <= 0) Color(0xFF8D6E63) else Color(0xFFE0AC8B)
        drawRoundRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(w * 0.18f, h * 0.08f),
            size = Size(w * 0.64f, h * 0.25f),
            cornerRadius = CornerRadius(5f, 5f)
        )
        drawRoundRect(
            color = skinColor,
            topLeft = Offset(w * 0.20f, h * 0.20f),
            size = Size(w * 0.60f, h * 0.68f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        val isGod = engine.godModeCheat || engine.invulnerableTimer > 0f
        val eyeOffset = engine.faceLookDir * (w * 0.05f)
        val eyeColor = when {
            engine.playerHealth <= 0 -> Color(0xFF3E2723)
            isGod -> Color(0xFFFFEA00)
            else -> Color(0xFF1A237E)
        }

        drawCircle(eyeColor, radius = w * 0.07f, center = Offset(w * 0.36f + eyeOffset, h * 0.44f))
        drawCircle(eyeColor, radius = w * 0.07f, center = Offset(w * 0.64f + eyeOffset, h * 0.44f))

        if (engine.playerHealth < 75 && !isGod) {
            drawCircle(Color(0xFFC62828), radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.58f))
        }
        if (engine.playerHealth < 40 && !isGod) {
            drawCircle(Color(0xFFB71C1C), radius = w * 0.09f, center = Offset(w * 0.32f, h * 0.66f))
            drawCircle(Color(0xFFB71C1C), radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.64f))
        }

        val mouthColor = if (engine.faceGrinTimer > 0f) Color.White else Color(0xFF4E342E)
        val mouthH = if (engine.facePainTimer > 0f) h * 0.16f else h * 0.08f
        drawRoundRect(
            color = mouthColor,
            topLeft = Offset(w * 0.34f, h * 0.68f),
            size = Size(w * 0.32f, mouthH),
            cornerRadius = CornerRadius(3f, 3f)
        )

        drawRect(
            color = Color(0xFF546E7A),
            topLeft = Offset.Zero,
            size = Size(w, h),
            style = Stroke(width = 2f)
        )
    }
}
