package cz.mares.doompm

data class MonsterSpawnDef(val type: MonsterType, val x: Float, val y: Float, val hardOnly: Boolean = false)
data class ItemSpawnDef(val type: ItemType, val x: Float, val y: Float)
data class TeleportPairDef(val fromX: Int, val fromY: Int, val toX: Float, val toY: Float)

data class DoomMapDefinition(
    val index: Int,
    val code: String,
    val title: String,
    val parSeconds: Int,
    val normalNextIndex: Int,
    val secretNextIndex: Int,
    val skyColorTop: Long,
    val skyColorBottom: Long,
    val floorColor: Long,
    val ceilingColor: Long,
    val startX: Float,
    val startY: Float,
    val startAngleRad: Float,
    val rawAsciiRows: List<String>,
    val monsters: List<MonsterSpawnDef>,
    val items: List<ItemSpawnDef>,
    val teleporters: List<TeleportPairDef> = emptyList()
)

object DoomMaps {
    /**
     * Legend for 20x20 ASCII grids:
     * '#' -> 1 (Tech Wall)
     * 'W' -> 2 (Stone/Concrete Wall)
     * 'H' -> 3 (Hell/Marble Brick Wall)
     * 'P' -> 4 (Toxic Pipe Wall)
     * 'C' -> 5 (Computer Panel Wall)
     * 'D' -> 6 (Normal Door)
     * 'B' -> 7 (Blue Key Door)
     * 'Y' -> 8 (Yellow Key Door)
     * 'R' -> 9 (Red Key Door)
     * 'S' -> 10 (Secret Push-Wall - counts as a secret when opened!)
     * 'E' -> 11 (Normal Exit Switch)
     * 'X' -> 12 (Secret Exit Switch -> goes to E1M9!)
     * '~' -> 13 (Radioactive Acid/Slime Floor)
     * '?' -> 14 (Secret Floor Sector - counts as a secret when stepped on!)
     * 'T' -> 15 (Teleporter Floor Pad)
     * '.' -> 0 (Walkable Floor)
     */
    fun charToTile(ch: Char): Int = when (ch) {
        '#' -> 1
        'W' -> 2
        'H' -> 3
        'P' -> 4
        'C' -> 5
        'D' -> 6
        'B' -> 7
        'Y' -> 8
        'R' -> 9
        'S' -> 10
        'E' -> 11
        'X' -> 12
        '~' -> 13
        '?' -> 14
        'T' -> 15
        else -> 0
    }

    val allMaps: List<DoomMapDefinition> = listOf(
        // =========================================================================
        // Index 0: E1M1 - HANGAR (3 secrets: courtyard pushwall, shotgun alcove, computer stash)
        // =========================================================================
        DoomMapDefinition(
            index = 0,
            code = "E1M1",
            title = "Hangar (Hangár na Phobosu)",
            parSeconds = 30,
            normalNextIndex = 1,
            secretNextIndex = 1,
            skyColorTop = 0xFF425B76,
            skyColorBottom = 0xFF90A4AE,
            floorColor = 0xFF373B42,
            ceilingColor = 0xFF23272D,
            startX = 2.5f,
            startY = 17.5f,
            startAngleRad = -1.57f,
            rawAsciiRows = listOf(
                "####################",
                "#..C...#...#....E..#",
                "#..C...D...B.......#",
                "#......#...#####D###",
                "####D#######.......#",
                "#..~~..S.?.....P...#",
                "#..~~..#####...P...#",
                "#......#...#.......#",
                "####D###...?...#####",
                "#......#...S...#??.#",
                "#..W...#####...S??.#",
                "#..W...D.......#####",
                "#......#...~~......#",
                "####D###...~~..W...#",
                "#..........~~..W...#",
                "#..C.......#########",
                "#..C.......D...#??.#",
                "#..........#...S??.#",
                "#..........#...#####",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.ZOMBIEMAN, 5.5f, 15.5f),
                MonsterSpawnDef(MonsterType.ZOMBIEMAN, 8.5f, 14.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 3.5f, 11.5f),
                MonsterSpawnDef(MonsterType.IMP, 9.5f, 11.5f),
                MonsterSpawnDef(MonsterType.ZOMBIEMAN, 2.5f, 6.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 4.5f, 2.5f),
                MonsterSpawnDef(MonsterType.IMP, 14.5f, 6.5f),
                MonsterSpawnDef(MonsterType.IMP, 16.5f, 4.5f, hardOnly = true),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 14.5f, 2.0f)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.GREEN_ARMOR, 8.5f, 17.5f),
                ItemSpawnDef(ItemType.HEALTH_BONUS, 4.5f, 14.5f),
                ItemSpawnDef(ItemType.HEALTH_BONUS, 5.5f, 14.5f),
                ItemSpawnDef(ItemType.ARMOR_BONUS, 6.5f, 14.5f),
                ItemSpawnDef(ItemType.WEAPON_SHOTGUN, 17.0f, 17.0f), // Secret #1
                ItemSpawnDef(ItemType.SHELL_BOX, 17.5f, 16.5f),
                ItemSpawnDef(ItemType.BARREL, 6.5f, 11.5f),
                ItemSpawnDef(ItemType.BARREL, 12.5f, 13.5f),
                ItemSpawnDef(ItemType.STIMPACK, 2.5f, 10.5f),
                ItemSpawnDef(ItemType.KEY_BLUE, 3.5f, 2.0f),
                ItemSpawnDef(ItemType.SOUL_SPHERE, 17.0f, 9.8f), // Secret #2
                ItemSpawnDef(ItemType.BLUE_ARMOR, 9.5f, 5.5f), // Secret #3
                ItemSpawnDef(ItemType.MEDIKIT, 12.5f, 5.5f),
                ItemSpawnDef(ItemType.AMMO_BOX, 15.5f, 7.5f),
                ItemSpawnDef(ItemType.BARREL, 13.5f, 2.0f)
            )
        ),

        // =========================================================================
        // Index 1: E1M2 - NUCLEAR PLANT (Red key, Chainsaw secret, Soul Sphere courtyard)
        // =========================================================================
        DoomMapDefinition(
            index = 1,
            code = "E1M2",
            title = "Nuclear Plant (Jaderná elektrárna)",
            parSeconds = 75,
            normalNextIndex = 2,
            secretNextIndex = 2,
            skyColorTop = 0xFF3E536B,
            skyColorBottom = 0xFF8397A3,
            floorColor = 0xFF3B3E39,
            ceilingColor = 0xFF262A2C,
            startX = 9.5f,
            startY = 17.5f,
            startAngleRad = -1.57f,
            rawAsciiRows = listOf(
                "####################",
                "#..E...#...~~..#??.#",
                "#......R...~~..S??.#",
                "########...~~..#####",
                "#......D.......#...#",
                "#..C...#####D###...#",
                "#..C...#.......D...#",
                "####D###...P...#####",
                "#......#...P.......#",
                "#..~~..D.......W...#",
                "#..~~..#####...W...#",
                "#......#??.#.......#",
                "####D###??.S...#####",
                "#..#...#####...D...#",
                "#..D.......#...#...#",
                "#..#####...D...#??.#",
                "#..#??.S...#...S??.#",
                "#..#??.#.......#####",
                "#..#####...........#",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.ZOMBIEMAN, 7.5f, 15.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 12.5f, 15.5f),
                MonsterSpawnDef(MonsterType.IMP, 5.5f, 11.5f),
                MonsterSpawnDef(MonsterType.IMP, 13.5f, 10.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 16.5f, 14.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 3.5f, 5.5f),
                MonsterSpawnDef(MonsterType.IMP, 11.5f, 4.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 17.0f, 5.5f, hardOnly = true),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 4.5f, 2.0f)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.WEAPON_SHOTGUN, 9.5f, 15.5f),
                ItemSpawnDef(ItemType.WEAPON_CHAINSAW, 5.0f, 16.5f), // Secret #1
                ItemSpawnDef(ItemType.GREEN_ARMOR, 17.0f, 16.0f), // Secret #2
                ItemSpawnDef(ItemType.BACKPACK, 9.0f, 11.8f), // Secret #3
                ItemSpawnDef(ItemType.KEY_RED, 17.0f, 5.0f),
                ItemSpawnDef(ItemType.WEAPON_CHAINGUN, 17.0f, 2.0f), // Secret #4
                ItemSpawnDef(ItemType.SOUL_SPHERE, 16.5f, 1.5f),
                ItemSpawnDef(ItemType.MEDIKIT, 2.5f, 8.5f),
                ItemSpawnDef(ItemType.SHELL_BOX, 13.5f, 8.5f),
                ItemSpawnDef(ItemType.BARREL, 8.5f, 13.5f),
                ItemSpawnDef(ItemType.BARREL, 10.5f, 9.5f),
                ItemSpawnDef(ItemType.RAD_SUIT, 9.5f, 3.5f)
            )
        ),

        // =========================================================================
        // Index 2: E1M3 - TOXIN REFINERY (Includes NORMAL exit 'E' -> E1M4 and SECRET exit 'X' -> E1M9!)
        // =========================================================================
        DoomMapDefinition(
            index = 2,
            code = "E1M3",
            title = "Toxin Refinery (Rafinerie toxinů)",
            parSeconds = 120,
            normalNextIndex = 3, // E1M4
            secretNextIndex = 8, // E1M9 (Secret Military Base!)
            skyColorTop = 0xFF334E4B,
            skyColorBottom = 0xFF729B8E,
            floorColor = 0xFF343C38,
            ceilingColor = 0xFF202724,
            startX = 2.5f,
            startY = 17.5f,
            startAngleRad = 0.0f,
            rawAsciiRows = listOf(
                "####################",
                "#..E...#...~~..#.X.#",
                "#......Y...~~..S??.#",
                "########...~~..#####",
                "#......B.......D...#",
                "#..P...#####D###...#",
                "#..P...#.......#...#",
                "####D###...~~..D...#",
                "#......#...~~..#####",
                "#..~~..D...~~......#",
                "#..~~..#########D###",
                "#......#??.#.......#",
                "####D###??.S...C...#",
                "#......#####...C...#",
                "#..W.......D.......#",
                "#..W.......#########",
                "#..........D...#??.#",
                "#..........#...S??.#",
                "#..........#...#####",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 6.5f, 16.5f),
                MonsterSpawnDef(MonsterType.IMP, 9.5f, 14.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 4.5f, 10.5f),
                MonsterSpawnDef(MonsterType.SPECTRE, 11.5f, 9.5f),
                MonsterSpawnDef(MonsterType.IMP, 15.5f, 12.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 14.5f, 6.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 4.5f, 5.5f),
                MonsterSpawnDef(MonsterType.IMP, 11.5f, 2.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 5.0f, 2.0f, hardOnly = true)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.SHELL_4, 5.5f, 17.5f),
                ItemSpawnDef(ItemType.GREEN_ARMOR, 8.5f, 15.5f),
                ItemSpawnDef(ItemType.WEAPON_ROCKET, 9.0f, 12.0f), // Secret #1
                ItemSpawnDef(ItemType.ROCKET_BOX, 9.5f, 11.5f),
                ItemSpawnDef(ItemType.SOUL_SPHERE, 17.0f, 17.0f), // Secret #2
                ItemSpawnDef(ItemType.KEY_BLUE, 16.5f, 12.5f),
                ItemSpawnDef(ItemType.KEY_YELLOW, 17.0f, 5.5f),
                ItemSpawnDef(ItemType.RAD_SUIT, 10.5f, 6.5f),
                ItemSpawnDef(ItemType.BLUE_ARMOR, 16.5f, 2.2f), // Secret #3 right before Secret Exit 'X'!
                ItemSpawnDef(ItemType.MEDIKIT, 3.5f, 8.5f),
                ItemSpawnDef(ItemType.BARREL, 7.5f, 14.5f),
                ItemSpawnDef(ItemType.BARREL, 12.5f, 7.5f)
            )
        ),

        // =========================================================================
        // Index 3: E1M4 - COMMAND CONTROL
        // =========================================================================
        DoomMapDefinition(
            index = 3,
            code = "E1M4",
            title = "Command Control (Velitelské centrum)",
            parSeconds = 90,
            normalNextIndex = 4,
            secretNextIndex = 4,
            skyColorTop = 0xFF39496B,
            skyColorBottom = 0xFF7986CB,
            floorColor = 0xFF393B44,
            ceilingColor = 0xFF24262E,
            startX = 2.5f,
            startY = 9.5f,
            startAngleRad = 0.0f,
            rawAsciiRows = listOf(
                "####################",
                "#..#??.S.......#..E#",
                "#..#??.#...C...Y...#",
                "#..#####...C...#####",
                "#......D.......D...#",
                "####D#######B#####.#",
                "#......#.......#...#",
                "#..W...D...P...D...#",
                "#..W...#...P...#...#",
                "#......D.......D...#",
                "####D#######D#####.#",
                "#......#.......#...#",
                "#..~~..D...C...D...#",
                "#..~~..#...C...#...#",
                "####D#######D#####.#",
                "#..........#...#??.#",
                "#..........D...S??.#",
                "#..........#...#####",
                "#..................#",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 5.5f, 8.5f),
                MonsterSpawnDef(MonsterType.IMP, 9.5f, 7.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 9.5f, 12.5f),
                MonsterSpawnDef(MonsterType.SPECTRE, 4.5f, 13.5f),
                MonsterSpawnDef(MonsterType.IMP, 15.5f, 8.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 11.5f, 3.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 6.5f, 16.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 16.5f, 3.5f, hardOnly = true)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.WEAPON_CHAINGUN, 5.5f, 9.5f),
                ItemSpawnDef(ItemType.KEY_BLUE, 13.5f, 16.5f),
                ItemSpawnDef(ItemType.KEY_YELLOW, 9.5f, 2.5f),
                ItemSpawnDef(ItemType.SOUL_SPHERE, 5.0f, 1.8f), // Secret #1
                ItemSpawnDef(ItemType.WEAPON_ROCKET, 17.0f, 16.5f), // Secret #2
                ItemSpawnDef(ItemType.ROCKET_BOX, 17.5f, 16.0f),
                ItemSpawnDef(ItemType.MEDIKIT, 10.5f, 9.5f),
                ItemSpawnDef(ItemType.GREEN_ARMOR, 3.5f, 3.5f),
                ItemSpawnDef(ItemType.AMMO_BOX, 15.5f, 12.5f),
                ItemSpawnDef(ItemType.BARREL, 8.5f, 9.5f)
            )
        ),

        // =========================================================================
        // Index 4: E1M5 - PHOBOS LAB (Teleporters, Toxic tunnels, Plasma Rifle secret)
        // =========================================================================
        DoomMapDefinition(
            index = 4,
            code = "E1M5",
            title = "Phobos Lab (Laboratoř Phobos)",
            parSeconds = 165,
            normalNextIndex = 5,
            secretNextIndex = 5,
            skyColorTop = 0xFF2E473B,
            skyColorBottom = 0xFF668F7A,
            floorColor = 0xFF333935,
            ceilingColor = 0xFF1F2421,
            startX = 9.5f,
            startY = 17.5f,
            startAngleRad = -1.57f,
            rawAsciiRows = listOf(
                "####################",
                "#..E...#...T...#??.#",
                "#......B.......S??.#",
                "########...H...#####",
                "#..~~..D...H...D...#",
                "#..~~..#####D###...#",
                "#..~~..#.......#...#",
                "####Y###...P...D...#",
                "#......#...P...#####",
                "#..C...D.......#??.#",
                "#..C...#####D###??.#",
                "#......#.......S...#",
                "####D###...~~..#####",
                "#......D...~~..D...#",
                "#..W...#########...#",
                "#..W.......D.......#",
                "#..........#####...#",
                "#..T.......#.......#",
                "#..........#.......#",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.IMP, 5.5f, 15.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 14.5f, 15.5f),
                MonsterSpawnDef(MonsterType.SPECTRE, 9.5f, 12.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 4.5f, 10.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 10.5f, 7.5f),
                MonsterSpawnDef(MonsterType.IMP, 16.5f, 5.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 3.5f, 5.5f),
                MonsterSpawnDef(MonsterType.BARON_OF_HELL, 11.5f, 2.5f, hardOnly = true),
                MonsterSpawnDef(MonsterType.LOST_SOUL, 4.5f, 2.0f)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.KEY_YELLOW, 16.5f, 13.5f),
                ItemSpawnDef(ItemType.KEY_BLUE, 3.5f, 5.0f),
                ItemSpawnDef(ItemType.RAD_SUIT, 9.5f, 13.5f),
                ItemSpawnDef(ItemType.WEAPON_PLASMA, 17.0f, 10.0f), // Secret #1
                ItemSpawnDef(ItemType.CELL_BULK, 17.0f, 2.0f), // Secret #2
                ItemSpawnDef(ItemType.BERSERK, 5.5f, 9.5f),
                ItemSpawnDef(ItemType.BLUE_ARMOR, 14.5f, 17.5f),
                ItemSpawnDef(ItemType.MEDIKIT, 9.5f, 6.5f),
                ItemSpawnDef(ItemType.SHELL_BOX, 15.5f, 7.5f),
                ItemSpawnDef(ItemType.BARREL, 7.5f, 11.5f)
            ),
            teleporters = listOf(
                TeleportPairDef(3, 17, 11.5f, 2.5f),
                TeleportPairDef(11, 1, 9.5f, 16.5f)
            )
        ),

        // =========================================================================
        // Index 5: E1M6 - CENTRAL PROCESSING (All 3 keys: Blue, Yellow, Red)
        // =========================================================================
        DoomMapDefinition(
            index = 5,
            code = "E1M6",
            title = "Central Processing (Centrální zpracování)",
            parSeconds = 180,
            normalNextIndex = 6,
            secretNextIndex = 6,
            skyColorTop = 0xFF4A3535,
            skyColorBottom = 0xFF966B6B,
            floorColor = 0xFF3E3636,
            ceilingColor = 0xFF262020,
            startX = 9.5f,
            startY = 9.5f,
            startAngleRad = -1.57f,
            rawAsciiRows = listOf(
                "####################",
                "#..E...R.......#??.#",
                "#......#########??.#",
                "####Y###.......S...#",
                "#......#...C...#####",
                "#..~~..B...C...D...#",
                "#..~~..#####D###...#",
                "####D###.......#...#",
                "#......D.......D...#",
                "#..P.......#.......#",
                "#..P.......D.......#",
                "####D#######D#######",
                "#......#.......#...#",
                "#..W...D...~~..D...#",
                "#..W...#...~~..#...#",
                "####D#######D#####.#",
                "#..#??.S.......D...#",
                "#..#??.#.......#...#",
                "#..#####...........#",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 5.5f, 8.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 13.5f, 8.5f),
                MonsterSpawnDef(MonsterType.IMP, 4.5f, 13.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 15.5f, 13.5f),
                MonsterSpawnDef(MonsterType.SPECTRE, 9.5f, 16.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 3.5f, 5.5f),
                MonsterSpawnDef(MonsterType.LOST_SOUL, 14.5f, 4.5f),
                MonsterSpawnDef(MonsterType.IMP, 9.5f, 2.0f),
                MonsterSpawnDef(MonsterType.BARON_OF_HELL, 5.5f, 1.5f)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.KEY_BLUE, 16.5f, 13.5f),
                ItemSpawnDef(ItemType.KEY_YELLOW, 3.5f, 5.5f),
                ItemSpawnDef(ItemType.KEY_RED, 16.5f, 5.5f),
                ItemSpawnDef(ItemType.INVULNERABILITY, 5.0f, 16.8f), // Secret #1
                ItemSpawnDef(ItemType.SOUL_SPHERE, 17.0f, 2.0f), // Secret #2
                ItemSpawnDef(ItemType.COMPUTER_MAP, 11.5f, 8.5f),
                ItemSpawnDef(ItemType.ROCKET_BOX, 4.5f, 9.5f),
                ItemSpawnDef(ItemType.CELL_PACK, 14.5f, 9.5f),
                ItemSpawnDef(ItemType.MEDIKIT, 9.5f, 12.5f),
                ItemSpawnDef(ItemType.BARREL, 7.5f, 8.5f)
            )
        ),

        // =========================================================================
        // Index 6: E1M7 - COMPUTER STATION (Heavy combat + Secret BFG 9000 prototype!)
        // =========================================================================
        DoomMapDefinition(
            index = 6,
            code = "E1M7",
            title = "Computer Station (Počítačová stanice)",
            parSeconds = 180,
            normalNextIndex = 7,
            secretNextIndex = 7,
            skyColorTop = 0xFF283552,
            skyColorBottom = 0xFF607D8B,
            floorColor = 0xFF333842,
            ceilingColor = 0xFF1E2229,
            startX = 2.5f,
            startY = 17.5f,
            startAngleRad = -1.57f,
            rawAsciiRows = listOf(
                "####################",
                "#..E...#.......#??.#",
                "#......R...C...S??.#",
                "########...C...#####",
                "#......D.......D...#",
                "#..C...#####Y#####.#",
                "#..C...#.......#...#",
                "####D###...~~..D...#",
                "#......B...~~..#...#",
                "#..W...#####D#####.#",
                "#..W...#.......#...#",
                "####D###...C...D...#",
                "#......D...C...#...#",
                "#..~~..#########...#",
                "#..~~......D.......#",
                "####D#######...#####",
                "#..........D...#??.#",
                "#..........#...S??.#",
                "#..........#...#####",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 5.5f, 16.5f),
                MonsterSpawnDef(MonsterType.IMP, 9.5f, 14.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 4.5f, 11.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 11.5f, 11.5f),
                MonsterSpawnDef(MonsterType.SPECTRE, 16.5f, 10.5f),
                MonsterSpawnDef(MonsterType.LOST_SOUL, 4.5f, 6.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 11.5f, 7.5f),
                MonsterSpawnDef(MonsterType.BARON_OF_HELL, 11.5f, 3.5f),
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 4.5f, 2.0f)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.KEY_BLUE, 16.5f, 14.5f),
                ItemSpawnDef(ItemType.KEY_YELLOW, 3.5f, 6.5f),
                ItemSpawnDef(ItemType.KEY_RED, 16.5f, 6.5f),
                ItemSpawnDef(ItemType.WEAPON_BFG, 17.0f, 2.0f), // Secret #1: BFG 9000!
                ItemSpawnDef(ItemType.CELL_BULK, 16.5f, 2.5f),
                ItemSpawnDef(ItemType.SOUL_SPHERE, 17.0f, 17.0f), // Secret #2
                ItemSpawnDef(ItemType.BLUE_ARMOR, 8.5f, 16.5f),
                ItemSpawnDef(ItemType.ROCKET_BOX, 9.5f, 10.5f),
                ItemSpawnDef(ItemType.MEDIKIT, 9.5f, 4.5f),
                ItemSpawnDef(ItemType.BARREL, 6.5f, 12.5f)
            )
        ),

        // =========================================================================
        // Index 7: E1M8 - PHOBOS ANOMALY (Final Boss Arena: Twin Barons + Cyberdemon!)
        // =========================================================================
        DoomMapDefinition(
            index = 7,
            code = "E1M8",
            title = "Phobos Anomaly (Finální anomálie Phobos)",
            parSeconds = 90,
            normalNextIndex = -1, // Episode Victory!
            secretNextIndex = -1,
            skyColorTop = 0xFF6B1414,
            skyColorBottom = 0xFFC62828,
            floorColor = 0xFF442828,
            ceilingColor = 0xFF2B1616,
            startX = 9.5f,
            startY = 17.5f,
            startAngleRad = -1.57f,
            rawAsciiRows = listOf(
                "####################",
                "#........E.........#",
                "#......#####.......#",
                "#..H...D...D...H...#",
                "#..H...........H...#",
                "#......H...H.......#",
                "#..................#",
                "#..H...........H...#",
                "#..H...~~.~~...H...#",
                "#......~~.~~.......#",
                "#..H...........H...#",
                "#..H...........H...#",
                "#......#####D#####.#",
                "#..#??.S...#...S??.#",
                "#..#??.#...D...#??.#",
                "#..#########...#####",
                "#........#.........#",
                "#........D.........#",
                "#........#.........#",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 6.5f, 14.5f),
                MonsterSpawnDef(MonsterType.SPECTRE, 12.5f, 14.5f),
                MonsterSpawnDef(MonsterType.BARON_OF_HELL, 6.5f, 7.5f),
                MonsterSpawnDef(MonsterType.BARON_OF_HELL, 13.5f, 7.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 4.5f, 4.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 15.5f, 4.5f),
                MonsterSpawnDef(MonsterType.CYBERDEMON, 9.5f, 5.5f)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.SOUL_SPHERE, 5.0f, 13.8f), // Secret #1
                ItemSpawnDef(ItemType.INVULNERABILITY, 17.0f, 13.8f), // Secret #2
                ItemSpawnDef(ItemType.BLUE_ARMOR, 9.5f, 15.5f),
                ItemSpawnDef(ItemType.BACKPACK, 9.5f, 13.5f),
                ItemSpawnDef(ItemType.CELL_BULK, 3.5f, 9.5f),
                ItemSpawnDef(ItemType.CELL_BULK, 16.5f, 9.5f),
                ItemSpawnDef(ItemType.ROCKET_BOX, 3.5f, 6.5f),
                ItemSpawnDef(ItemType.ROCKET_BOX, 16.5f, 6.5f),
                ItemSpawnDef(ItemType.MEDIKIT, 9.5f, 11.5f),
                ItemSpawnDef(ItemType.BARREL, 8.5f, 11.5f),
                ItemSpawnDef(ItemType.BARREL, 10.5f, 11.5f)
            )
        ),

        // =========================================================================
        // Index 8: E1M9 - MILITARY BASE (Secret Level accessed from E1M3 -> exits to E1M4)
        // =========================================================================
        DoomMapDefinition(
            index = 8,
            code = "E1M9",
            title = "Military Base (Tajná vojenská základna!)",
            parSeconds = 135,
            normalNextIndex = 3, // Returns to E1M4 Command Control fully loaded!
            secretNextIndex = 3,
            skyColorTop = 0xFF4A148C,
            skyColorBottom = 0xFF8E24AA,
            floorColor = 0xFF3A3144,
            ceilingColor = 0xFF231C2B,
            startX = 9.5f,
            startY = 17.5f,
            startAngleRad = -1.57f,
            rawAsciiRows = listOf(
                "####################",
                "#..E...Y.......#??.#",
                "#......#########??.#",
                "####B###.......S...#",
                "#......#...H...#####",
                "#..P...D...H...D...#",
                "#..P...#####D###...#",
                "####D###.......#...#",
                "#......D...T...D...#",
                "#..~~..........~~..#",
                "#..~~..........~~..#",
                "#......D.......D...#",
                "####D###.......#####",
                "#......#####D###??.#",
                "#..W...#.......S??.#",
                "#..W...D...C...#####",
                "########...C.......#",
                "#..........D.......#",
                "#..........#.......#",
                "####################"
            ),
            monsters = listOf(
                MonsterSpawnDef(MonsterType.SHOTGUN_GUY, 8.5f, 14.5f),
                MonsterSpawnDef(MonsterType.IMP, 5.5f, 10.5f),
                MonsterSpawnDef(MonsterType.IMP, 14.5f, 10.5f),
                MonsterSpawnDef(MonsterType.PINKY_DEMON, 9.5f, 9.5f),
                MonsterSpawnDef(MonsterType.SPECTRE, 4.5f, 6.5f),
                MonsterSpawnDef(MonsterType.LOST_SOUL, 15.5f, 6.5f),
                MonsterSpawnDef(MonsterType.CACODEMON, 10.5f, 4.5f),
                MonsterSpawnDef(MonsterType.BARON_OF_HELL, 9.5f, 2.0f)
            ),
            items = listOf(
                ItemSpawnDef(ItemType.WEAPON_PLASMA, 9.5f, 15.5f),
                ItemSpawnDef(ItemType.WEAPON_ROCKET, 9.5f, 10.5f),
                ItemSpawnDef(ItemType.KEY_BLUE, 3.5f, 14.5f),
                ItemSpawnDef(ItemType.KEY_YELLOW, 16.5f, 5.5f),
                ItemSpawnDef(ItemType.WEAPON_BFG, 17.0f, 14.0f), // Secret #1
                ItemSpawnDef(ItemType.INVULNERABILITY, 17.0f, 2.0f), // Secret #2
                ItemSpawnDef(ItemType.SOUL_SPHERE, 3.5f, 5.5f),
                ItemSpawnDef(ItemType.CELL_BULK, 11.5f, 10.5f),
                ItemSpawnDef(ItemType.ROCKET_BOX, 7.5f, 10.5f)
            ),
            teleporters = listOf(
                TeleportPairDef(11, 8, 3.5f, 5.5f)
            )
        )
    )
}
