package cz.mares.doompm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

object DoomRenderer {
    private const val FB_W = 280
    private const val FB_H = 260
    private val frameBuffer = IntArray(FB_W * FB_H)
    private val zBuffer = FloatArray(FB_W)

    // Classic Doom Spectre fuzz vertical offset table
    private val fuzzOffsets = intArrayOf(
        1, -1, 1, -1, 1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1,
        1, -1, -1, -1, -1, 1, -1, -1, 1, 1, 1, 1, -1, 1, -1, 1
    )
    private var fuzzPos = 0

    private class SpriteEntry(
        val x: Float,
        val y: Float,
        val distSq: Float,
        val kind: Int, // 1 = Monster, 2 = Item, 3 = Projectile
        val monster: MonsterEntity? = null,
        val item: MapItemEntity? = null,
        val projectile: ProjectileEntity? = null
    )

    fun DrawScope.render3DView(engine: DoomEngine) {
        val dstW = size.width.toInt()
        val dstH = size.height.toInt()
        if (dstW <= 2 || dstH <= 2) return

        DoomTextures.ensureInitialized()

        val aspect = (size.width / size.height).coerceIn(0.62f, 1.55f)
        val dirX = cos(engine.playerAngle)
        val dirY = sin(engine.playerAngle)
        val planeScale = 0.68f * aspect
        val planeX = -dirY * planeScale
        val planeY = dirX * planeScale

        val halfH = FB_H / 2
        val projH = (FB_H.toFloat() / aspect.coerceAtMost(1.1f))
        val isInvuln = engine.invulnerableTimer > 0f
        val animFrame = (engine.levelTimeSec * 3.5f).toInt()

        // 1. Perspective-Correct Textured Floor, Ceiling & Phobos Mountain Sky
        renderFloorsAndCeilings(
            engine = engine,
            dirX = dirX,
            dirY = dirY,
            planeX = planeX,
            planeY = planeY,
            halfH = halfH,
            projH = projH,
            animFrame = animFrame,
            isInvuln = isInvuln
        )

        // 2. Per-Column Textured Wall & Sliding Door Raycasting
        renderWalls(
            engine = engine,
            dirX = dirX,
            dirY = dirY,
            planeX = planeX,
            planeY = planeY,
            halfH = halfH,
            projH = projH,
            animFrame = animFrame,
            isInvuln = isInvuln
        )

        // 3. Z-Buffered Pixel-Art Sprites (Monsters, Spectre Fuzz, Items, Barrels, Projectiles)
        renderSpritesToBuffer(
            engine = engine,
            dirX = dirX,
            dirY = dirY,
            planeX = planeX,
            planeY = planeY,
            halfH = halfH,
            projH = projH,
            aspect = aspect,
            animFrame = animFrame,
            isInvuln = isInvuln
        )

        // 4. Pixel-Art First-Person Weapon with Walk Bobbing & Muzzle Light
        if (!engine.isPlayerDead) {
            renderWeaponToBuffer(engine, isInvuln)
        }

        // 5. Screen Tints (Damage Red, Pickup Gold, Teleport Green, RadSuit Visor) & Crosshair
        applyScreenPostEffects(engine, halfH)

        // Blit crisp pixel-art VGA framebuffer to screen
        val bmp = PlatformServices.pixelsToImageBitmap(frameBuffer, FB_W, FB_H)
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(FB_W, FB_H),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(dstW, dstH),
            filterQuality = FilterQuality.None
        )
    }

    private fun renderFloorsAndCeilings(
        engine: DoomEngine,
        dirX: Float,
        dirY: Float,
        planeX: Float,
        planeY: Float,
        halfH: Int,
        projH: Float,
        animFrame: Int,
        isInvuln: Boolean
    ) {
        val rayDirX0 = dirX - planeX
        val rayDirY0 = dirY - planeY
        val rayDirX1 = dirX + planeX
        val rayDirY1 = dirY + planeY
        val posZ = 0.5f * projH

        val floorTex = DoomTextures.floorTex
        val ceilTex = DoomTextures.ceilingTex
        val acidTex = DoomTextures.getAcidTexture(animFrame)
        val teleTex = DoomTextures.teleporterTex
        val gs = engine.gridSize
        val grid = engine.grid

        val skyTopR = ((engine.currentMapDef.skyColorTop ushr 16) and 0xFF).toInt()
        val skyTopG = ((engine.currentMapDef.skyColorTop ushr 8) and 0xFF).toInt()
        val skyTopB = (engine.currentMapDef.skyColorTop and 0xFF).toInt()
        val skyBotR = ((engine.currentMapDef.skyColorBottom ushr 16) and 0xFF).toInt()
        val skyBotG = ((engine.currentMapDef.skyColorBottom ushr 8) and 0xFF).toInt()
        val skyBotB = (engine.currentMapDef.skyColorBottom and 0xFF).toInt()

        for (y in halfH until FB_H) {
            val p = (y - halfH).coerceAtLeast(1)
            val rowDistance = posZ / p.toFloat()
            val shade = (1.0f / (1.0f + rowDistance * 0.14f)).coerceIn(0.14f, 1.0f)

            val stepX = rowDistance * (rayDirX1 - rayDirX0) / FB_W.toFloat()
            val stepY = rowDistance * (rayDirY1 - rayDirY0) / FB_W.toFloat()
            var floorX = engine.playerX + rowDistance * rayDirX0
            var floorY = engine.playerY + rowDistance * rayDirY0

            val floorRowOffset = y * FB_W
            val ceilY = (FB_H - 1 - y)
            val ceilRowOffset = ceilY * FB_W
            val skyGrad = (ceilY.toFloat() / halfH.toFloat()).coerceIn(0f, 1f)

            for (x in 0 until FB_W) {
                val cellX = floor(floorX).toInt()
                val cellY = floor(floorY).toInt()
                val tx = ((floorX * 64f).toInt()) and 63
                val ty = ((floorY * 64f).toInt()) and 63
                val texIdx = (ty shl 6) + tx

                val tile = if (cellX in 0 until gs && cellY in 0 until gs) grid[cellY][cellX] else 0

                // Floor pixel
                val rawFloor = when (tile) {
                    13 -> acidTex[texIdx]
                    15 -> teleTex[texIdx]
                    else -> floorTex[texIdx]
                }
                val floorShade = if (tile == 13 || tile == 15) (shade * 1.35f).coerceAtMost(1.0f) else shade
                frameBuffer[floorRowOffset + x] = DoomTextures.shadeColor(rawFloor, floorShade, isInvuln)

                // Symmetrical Ceiling pixel (Outdoor mountain sky above acid pools, industrial lights indoors)
                if (ceilY >= 0) {
                    if (tile == 13) {
                        val skyAng = engine.playerAngle + (x.toFloat() / FB_W.toFloat() - 0.5f) * 1.1f
                        val mtnHeight = 0.68f + sin(skyAng * 5f) * 0.12f + cos(skyAng * 13f) * 0.06f
                        val skyCol = if (skyGrad > mtnHeight) {
                            DoomTextures.rgb(58, 52, 48)
                        } else {
                            val r = (skyTopR + (skyBotR - skyTopR) * skyGrad).toInt()
                            val g = (skyTopG + (skyBotG - skyTopG) * skyGrad).toInt()
                            val b = (skyTopB + (skyBotB - skyTopB) * skyGrad).toInt()
                            DoomTextures.rgb(r, g, b)
                        }
                        frameBuffer[ceilRowOffset + x] = if (isInvuln) {
                            DoomTextures.shadeColor(skyCol, 0.9f, true)
                        } else {
                            skyCol
                        }
                    } else {
                        val rawCeil = ceilTex[texIdx]
                        // Keep ceiling light panels bright even at medium distance
                        val isLightPixel = ((rawCeil and 0xFF) > 175)
                        val cShade = if (isLightPixel) (shade * 1.45f).coerceAtMost(1.0f) else shade * 0.88f
                        frameBuffer[ceilRowOffset + x] = DoomTextures.shadeColor(rawCeil, cShade, isInvuln)
                    }
                }

                floorX += stepX
                floorY += stepY
            }
        }
    }

    private fun renderWalls(
        engine: DoomEngine,
        dirX: Float,
        dirY: Float,
        planeX: Float,
        planeY: Float,
        halfH: Int,
        projH: Float,
        animFrame: Int,
        isInvuln: Boolean
    ) {
        val gs = engine.gridSize
        val muzzleLightBoost = if (engine.weaponFlashTimer > 0f) 0.22f else 0f

        for (x in 0 until FB_W) {
            val cameraX = 2f * x.toFloat() / FB_W.toFloat() - 1f
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

            var hitTile = 1
            var side = 0
            var doorSlideOffset = 0f

            var steps = 0
            while (steps < 44) {
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

                if (mapX !in 0 until gs || mapY !in 0 until gs) {
                    hitTile = 1
                    break
                }

                val t = engine.grid[mapY][mapX]
                if (t in 1..5 || t == 11 || t == 12) {
                    hitTile = t
                    break
                } else if (t in 6..10) {
                    val door = engine.doors[mapY * gs + mapX]
                    val openAmt = door?.openAmount ?: 0f
                    if (openAmt < 0.96f) {
                        val perpTmp = if (side == 0) (sideDistX - deltaDistX) else (sideDistY - deltaDistY)
                        val wxTmp = if (side == 0) {
                            engine.playerY + perpTmp * rayDirY
                        } else {
                            engine.playerX + perpTmp * rayDirX
                        }
                        val fract = wxTmp - floor(wxTmp)
                        if (fract >= openAmt) {
                            hitTile = t
                            doorSlideOffset = openAmt
                            break
                        }
                    }
                }
            }

            val perpWallDist = (if (side == 0) (sideDistX - deltaDistX) else (sideDistY - deltaDistY))
                .coerceAtLeast(0.10f)
            zBuffer[x] = perpWallDist

            val lineHeight = (projH / perpWallDist).toInt().coerceAtLeast(1)
            val rawStart = halfH - lineHeight / 2
            val drawStart = rawStart.coerceAtLeast(0)
            val drawEnd = (halfH + lineHeight / 2).coerceAtMost(FB_H - 1)

            var wallX = if (side == 0) {
                engine.playerY + perpWallDist * rayDirY
            } else {
                engine.playerX + perpWallDist * rayDirX
            }
            wallX -= floor(wallX)
            if (doorSlideOffset > 0f) {
                wallX = (wallX - doorSlideOffset).coerceIn(0f, 0.999f)
            }

            var texX = (wallX * 64f).toInt().coerceIn(0, 63)
            if (doorSlideOffset == 0f) {
                if (side == 0 && rayDirX > 0f) texX = 63 - texX
                if (side == 1 && rayDirY < 0f) texX = 63 - texX
            }

            val wallTex = DoomTextures.getWallTexture(hitTile, animFrame)
            val distShade = (1.0f / (1.0f + perpWallDist * 0.12f) + muzzleLightBoost).coerceIn(0.15f, 1.0f)
            val sideShade = if (side == 1) 0.76f else 1.0f
            val totalShade = (distShade * sideShade).coerceIn(0.14f, 1.0f)

            val texStep = 64.0f / lineHeight.toFloat()
            var texPos = (drawStart - rawStart) * texStep

            for (y in drawStart..drawEnd) {
                val texY = (texPos.toInt()) and 63
                texPos += texStep
                val rawCol = wallTex[(texY shl 6) + texX]
                frameBuffer[y * FB_W + x] = DoomTextures.shadeColor(rawCol, totalShade, isInvuln)
            }
        }
    }

    private fun renderSpritesToBuffer(
        engine: DoomEngine,
        dirX: Float,
        dirY: Float,
        planeX: Float,
        planeY: Float,
        halfH: Int,
        projH: Float,
        aspect: Float,
        animFrame: Int,
        isInvuln: Boolean
    ) {
        val entries = ArrayList<SpriteEntry>(engine.monsters.size + engine.items.size + engine.projectiles.size)

        for (m in engine.monsters) {
            val dx = m.x - engine.playerX
            val dy = m.y - engine.playerY
            entries.add(SpriteEntry(m.x, m.y, dx * dx + dy * dy, 1, monster = m))
        }
        for (it in engine.items) {
            if (it.collected) continue
            val dx = it.x - engine.playerX
            val dy = it.y - engine.playerY
            entries.add(SpriteEntry(it.x, it.y, dx * dx + dy * dy, 2, item = it))
        }
        for (p in engine.projectiles) {
            val dx = p.x - engine.playerX
            val dy = p.y - engine.playerY
            entries.add(SpriteEntry(p.x, p.y, dx * dx + dy * dy, 3, projectile = p))
        }

        entries.sortByDescending { it.distSq }
        val invDet = 1.0f / (planeX * dirY - dirX * planeY)
        val widthAspect = (1.0f / aspect.coerceIn(0.65f, 1.35f))

        for (entry in entries) {
            val relX = entry.x - engine.playerX
            val relY = entry.y - engine.playerY

            val transformX = invDet * (dirY * relX - dirX * relY)
            val transformY = invDet * (-planeY * relX + planeX * relY)
            if (transformY <= 0.16f) continue

            val screenX = ((FB_W * 0.5f) * (1f + transformX / transformY)).toInt()
            val baseH = (projH / transformY).toInt().coerceAtLeast(2)

            if (entry.kind == 3) {
                val proj = entry.projectile ?: continue
                drawProjectileToBuffer(proj, screenX, halfH, baseH, transformY)
                continue
            }

            val scale = if (entry.kind == 1) {
                when (entry.monster?.type) {
                    MonsterType.CYBERDEMON -> 1.16f
                    MonsterType.BARON_OF_HELL -> 1.02f
                    MonsterType.CACODEMON -> 0.92f
                    MonsterType.LOST_SOUL -> 0.65f
                    else -> 0.88f
                }
            } else {
                0.78f
            }

            val spriteH = (baseH * scale).toInt().coerceAtLeast(2)
            val spriteW = (spriteH * widthAspect * 0.92f).toInt().coerceAtLeast(2)

            val floorScreenY = halfH + baseH / 2
            val floatOffset = if (entry.kind == 1 &&
                (entry.monster?.type == MonsterType.CACODEMON || entry.monster?.type == MonsterType.LOST_SOUL) &&
                entry.monster.state != MonsterState.DEAD
            ) {
                (baseH * 0.16f).toInt()
            } else 0

            val rawStartY = floorScreenY - spriteH - floatOffset
            val rawEndY = rawStartY + spriteH
            val drawStartY = rawStartY.coerceAtLeast(0)
            val drawEndY = (rawEndY - 1).coerceAtMost(FB_H - 1)

            val rawStartX = screenX - spriteW / 2
            val rawEndX = rawStartX + spriteW
            val drawStartX = rawStartX.coerceAtLeast(0)
            val drawEndX = (rawEndX - 1).coerceAtMost(FB_W - 1)

            if (drawStartX > drawEndX || drawStartY > drawEndY) continue

            val texPixels = if (entry.kind == 1) {
                val m = entry.monster!!
                DoomTextures.getMonsterSprite(m.type, m.state, (m.animPhase.toInt()) and 1)
            } else {
                val it = entry.item!!
                DoomTextures.getItemSprite(it.type, it.type.isShootableBarrel && it.barrelHp <= 0, animFrame)
            }

            val isSpectreFuzz = (entry.kind == 1 && entry.monster?.type?.isPartialInvisible == true &&
                entry.monster.state != MonsterState.DEAD)
            val shade = if (entry.kind == 2 && !entry.item!!.type.isSolidObstacle) {
                // Keep pickups slightly brighter so keys, medikits and weapons are clearly readable
                (1.0f / (1.0f + transformY * 0.07f)).coerceIn(0.42f, 1.0f)
            } else {
                (1.0f / (1.0f + transformY * 0.11f)).coerceIn(0.20f, 1.0f)
            }

            var anyColumnVisible = false
            for (stripe in drawStartX..drawEndX) {
                if (transformY >= zBuffer[stripe]) continue
                anyColumnVisible = true
                val texX = (((stripe - rawStartX) * 64) / spriteW).coerceIn(0, 63)

                for (y in drawStartY..drawEndY) {
                    val texY = (((y - rawStartY) * 64) / spriteH).coerceIn(0, 63)
                    val srcCol = texPixels[(texY shl 6) + texX]
                    if ((srcCol ushr 24) != 0) {
                        if (isSpectreFuzz) {
                            // Classic 1993 DOOM Spectre Partial Invisibility refraction/fuzz!
                            val fOff = fuzzOffsets[fuzzPos and 31]
                            fuzzPos++
                            val sampleY = (y + fOff).coerceIn(0, FB_H - 1)
                            val bg = frameBuffer[sampleY * FB_W + stripe]
                            frameBuffer[y * FB_W + stripe] = DoomTextures.shadeColor(bg, 0.58f, false)
                        } else {
                            frameBuffer[y * FB_W + stripe] = DoomTextures.shadeColor(srcCol, shade, isInvuln)
                        }
                    }
                }
            }

            // Crisp retro HP bar above damaged monsters
            val m = entry.monster
            if (anyColumnVisible && m != null && m.hp > 0 && m.hp < m.type.maxHp && rawStartY in 4 until FB_H) {
                val barW = (spriteW * 0.72f).toInt().coerceIn(8, 48)
                val bx0 = (screenX - barW / 2).coerceIn(0, FB_W - 1)
                val bx1 = (screenX + barW / 2).coerceIn(0, FB_W - 1)
                val by = (rawStartY - 3).coerceIn(1, FB_H - 3)
                val fillEnd = bx0 + ((bx1 - bx0) * m.hp) / m.type.maxHp
                for (bx in bx0..bx1) {
                    if (transformY < zBuffer[bx]) {
                        val c = if (bx <= fillEnd) DoomTextures.rgb(255, 35, 35) else DoomTextures.rgb(25, 25, 28)
                        frameBuffer[by * FB_W + bx] = c
                        frameBuffer[(by + 1) * FB_W + bx] = c
                    }
                }
            }
        }
    }

    private fun drawProjectileToBuffer(
        proj: ProjectileEntity,
        screenX: Int,
        halfH: Int,
        baseH: Int,
        transformY: Float
    ) {
        val rad = when (proj.kind) {
            ProjectileKind.BFG_ORB -> (baseH * 0.24f).toInt()
            ProjectileKind.ROCKET -> (baseH * 0.15f).toInt()
            else -> (baseH * 0.13f).toInt()
        }.coerceIn(2, 42)

        val (outerR, outerG, outerB) = when (proj.kind) {
            ProjectileKind.BFG_ORB, ProjectileKind.BARON_PLASMA -> Triple(35, 250, 95)
            ProjectileKind.PLASMA -> Triple(45, 195, 255)
            ProjectileKind.CACO_LIGHTNING -> Triple(255, 40, 75)
            else -> Triple(255, 135, 20)
        }

        val x0 = (screenX - rad).coerceAtLeast(0)
        val x1 = (screenX + rad).coerceAtMost(FB_W - 1)
        val y0 = (halfH - rad).coerceAtLeast(0)
        val y1 = (halfH + rad).coerceAtMost(FB_H - 1)

        for (px in x0..x1) {
            if (transformY >= zBuffer[px]) continue
            val nx = (px - screenX).toFloat() / rad.toFloat()
            for (py in y0..y1) {
                val ny = (py - halfH).toFloat() / rad.toFloat()
                val d2 = nx * nx + ny * ny
                if (d2 <= 1.0f) {
                    val core = (1.0f - sqrt(d2)).coerceIn(0f, 1f)
                    val r = (outerR + (255 - outerR) * core * 0.85f).toInt()
                    val g = (outerG + (255 - outerG) * core * 0.85f).toInt()
                    val b = (outerB + (255 - outerB) * core * 0.85f).toInt()
                    frameBuffer[py * FB_W + px] = DoomTextures.rgb(r, g, b)
                }
            }
        }
    }

    private fun renderWeaponToBuffer(engine: DoomEngine, isInvuln: Boolean) {
        val isFiring = engine.weaponFlashTimer > 0f
        val weaponPixels = DoomTextures.getWeaponSprite(
            weapon = engine.currentWeapon,
            isFiring = isFiring,
            hasBerserk = engine.hasBerserk
        )

        val bobX = (sin(engine.walkBobPhase) * 9f * engine.walkBobAmplitude).toInt()
        val bobY = (abs(cos(engine.walkBobPhase)) * 7f * engine.walkBobAmplitude).toInt()

        val dstW = (FB_W * 0.62f).toInt()
        val dstH = (FB_H * 0.44f).toInt()
        val startX = (FB_W - dstW) / 2 + bobX
        val startY = FB_H - dstH + bobY + 4

        val wShade = if (isFiring) 1.0f else 0.92f
        val berserkTint = (engine.currentWeapon == WeaponType.FIST && engine.hasBerserk)

        for (dy in 0 until dstH) {
            val py = startY + dy
            if (py !in 0 until FB_H) continue
            val srcY = (dy * DoomTextures.WEAPON_H) / dstH
            val srcRow = srcY * DoomTextures.WEAPON_W
            val dstRow = py * FB_W

            for (dx in 0 until dstW) {
                val px = startX + dx
                if (px !in 0 until FB_W) continue
                val srcX = (dx * DoomTextures.WEAPON_W) / dstW
                var col = weaponPixels[srcRow + srcX]
                if ((col ushr 24) != 0) {
                    if (berserkTint) {
                        val r = (((col ushr 16) and 0xFF) + 45).coerceAtMost(255)
                        val g = ((col ushr 8) and 0xFF)
                        val b = (col and 0xFF)
                        col = DoomTextures.rgb(r, g, b)
                    }
                    frameBuffer[dstRow + px] = DoomTextures.shadeColor(col, wShade, isInvuln)
                }
            }
        }
    }

    private fun applyScreenPostEffects(engine: DoomEngine, halfH: Int) {
        var tintR = 0
        var tintG = 0
        var tintB = 0
        var alpha = 0 // 0..256

        if (engine.damageFlashTimer > 0f) {
            tintR = 240; tintG = 15; tintB = 10
            alpha = (engine.damageFlashTimer * 170f).toInt().coerceIn(0, 135)
        } else if (engine.teleportFlashTimer > 0f) {
            tintR = 45; tintG = 250; tintB = 85
            alpha = (engine.teleportFlashTimer * 190f).toInt().coerceIn(0, 145)
        } else if (engine.pickupFlashTimer > 0f) {
            tintR = 250; tintG = 210; tintB = 55
            alpha = (engine.pickupFlashTimer * 165f).toInt().coerceIn(0, 90)
        } else if (engine.radSuitTimer > 0f) {
            tintR = 20; tintG = 200; tintB = 45
            alpha = 34
        }

        if (alpha > 0) {
            val invA = 256 - alpha
            val preR = tintR * alpha
            val preG = tintG * alpha
            val preB = tintB * alpha
            for (i in frameBuffer.indices) {
                val c = frameBuffer[i]
                val r = (((c ushr 16) and 0xFF) * invA + preR) ushr 8
                val g = (((c ushr 8) and 0xFF) * invA + preG) ushr 8
                val b = ((c and 0xFF) * invA + preB) ushr 8
                frameBuffer[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Crisp tactical pixel crosshair
        val cx = FB_W / 2
        val cy = halfH
        val crossCol = DoomTextures.rgb(50, 250, 100)
        for (d in -4..4) {
            if (d != 0) {
                if (cx + d in 0 until FB_W) frameBuffer[cy * FB_W + (cx + d)] = crossCol
                if (cy + d in 0 until FB_H) frameBuffer[(cy + d) * FB_W + cx] = crossCol
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
        val dstW = size.width.toInt().coerceAtLeast(1)
        val dstH = size.height.toInt().coerceAtLeast(1)
        val isGod = engine.godModeCheat || engine.invulnerableTimer > 0f
        val facePixels = DoomTextures.renderDoomguyFacePixels(
            health = engine.playerHealth,
            isGod = isGod,
            lookDir = engine.faceLookDir,
            isPain = engine.facePainTimer > 0f,
            isGrin = engine.faceGrinTimer > 0f
        )
        val bmp = PlatformServices.pixelsToImageBitmap(
            pixels = facePixels,
            width = DoomTextures.FACE_SIZE,
            height = DoomTextures.FACE_SIZE
        )
        drawImage(
            image = bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(DoomTextures.FACE_SIZE, DoomTextures.FACE_SIZE),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(dstW, dstH),
            filterQuality = FilterQuality.None
        )
    }
}
