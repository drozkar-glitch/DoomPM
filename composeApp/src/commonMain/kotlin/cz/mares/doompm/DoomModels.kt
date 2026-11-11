package cz.mares.doompm

enum class Difficulty(
    val id: String,
    val titleCz: String,
    val subtitleOrig: String,
    val damageTakenMult: Float,
    val ammoPickupMult: Float,
    val monsterSpeedMult: Float,
    val respawnMonsters: Boolean
) {
    BABY("baby", "1. Příliš mladý na smrt", "I'm Too Young To Die", 0.5f, 2.0f, 0.85f, false),
    EASY("easy", "2. Hej, ne tak drsně", "Hey, Not Too Rough", 0.75f, 1.25f, 0.95f, false),
    MEDIUM("medium", "3. Dej mi pořádně zabrat", "Hurt Me Plenty", 1.0f, 1.0f, 1.0f, false),
    HARD("hard", "4. Ultra-násilí", "Ultra-Violence", 1.2f, 1.0f, 1.2f, false),
    NIGHTMARE("nightmare", "5. Noční můra!", "Nightmare!", 1.35f, 1.5f, 1.45f, true)
}

enum class AmmoType(val labelCz: String, val baseMax: Int) {
    NONE("-", 0),
    BULLETS("Náboje", 200),
    SHELLS("Broky", 50),
    ROCKETS("Rakety", 50),
    CELLS("Články", 300)
}

enum class WeaponType(
    val slot: Int,
    val titleCz: String,
    val ammoType: AmmoType,
    val ammoPerShot: Int,
    val cooldownSec: Float,
    val minDamage: Int,
    val maxDamage: Int,
    val pellets: Int,
    val range: Float,
    val isMelee: Boolean,
    val isProjectile: Boolean
) {
    FIST(1, "Pěst", AmmoType.NONE, 0, 0.34f, 10, 24, 1, 1.75f, true, false),
    CHAINSAW(1, "Motorová pila", AmmoType.NONE, 0, 0.12f, 11, 22, 1, 1.85f, true, false),
    PISTOL(2, "Pistole", AmmoType.BULLETS, 1, 0.36f, 12, 22, 1, 26f, false, false),
    SHOTGUN(3, "Brokovnice", AmmoType.SHELLS, 1, 0.78f, 11, 18, 7, 22f, false, false),
    CHAINGUN(4, "Rotační kulomet", AmmoType.BULLETS, 1, 0.11f, 11, 19, 1, 26f, false, false),
    ROCKET_LAUNCHER(5, "Raketomet", AmmoType.ROCKETS, 1, 0.68f, 95, 160, 1, 32f, false, true),
    PLASMA_RIFLE(6, "Plazmová puška", AmmoType.CELLS, 1, 0.13f, 22, 42, 1, 32f, false, true),
    BFG_9000(7, "BFG 9000", AmmoType.CELLS, 40, 1.25f, 350, 800, 1, 36f, false, true)
}

enum class KeyCard(val titleCz: String, val colorHex: Long) {
    BLUE("Modrá karta", 0xFF2979FF),
    YELLOW("Žlutá karta", 0xFFFFD600),
    RED("Červená karta", 0xFFFF1744)
}

enum class ItemType(
    val titleCz: String,
    val countsForItemStat: Boolean,
    val isSolidObstacle: Boolean = false,
    val isShootableBarrel: Boolean = false
) {
    HEALTH_BONUS("Láhvička zdraví (+1 %)", true),
    ARMOR_BONUS("Helma brnění (+1 %)", true),
    STIMPACK("Stimpack (+10 % zdraví)", true),
    MEDIKIT("Lékárnička (+25 % zdraví)", true),
    SOUL_SPHERE("Duševní sféra (+100 % zdraví!)", true),
    BERSERK("Berserk (100 % zdraví + 10x pěst!)", true),
    GREEN_ARMOR("Zelená zbroj (100 %)", true),
    BLUE_ARMOR("Modrá bojová zbroj (200 %!)", true),
    INVULNERABILITY("Sféra nesmrtelnosti (30 s)!", true),
    RAD_SUIT("Radiační oblek proti slizu (60 s)", true),
    COMPUTER_MAP("Počítačová mapa celé oblasti!", true),
    BACKPACK("Batoh s dvojitou kapacitou munice!", true),

    KEY_BLUE("Sebrána Modrá klíčová karta", false),
    KEY_YELLOW("Sebrána Žlutá klíčová karta", false),
    KEY_RED("Sebrána Červená klíčová karta", false),

    WEAPON_CHAINSAW("Motorová pila! Pojď si pro maso!", false),
    WEAPON_SHOTGUN("Sebrána Brokovnice!", false),
    WEAPON_CHAINGUN("Sebrán Rotační kulomet!", false),
    WEAPON_ROCKET("Sebrán Raketomet!", false),
    WEAPON_PLASMA("Sebrána Plazmová puška!", false),
    WEAPON_BFG("Sebráno BFG 9000! Ohromná síla!", false),

    AMMO_CLIP("Zásobník nábojů (+10)", false),
    AMMO_BOX("Krabice nábojů (+50)", false),
    SHELL_4("4 brokové patrony", false),
    SHELL_BOX("Krabice broků (+20)", false),
    ROCKET_ONE("Raketa (+1)", false),
    ROCKET_BOX("Bedna raket (+5)", false),
    CELL_PACK("Energetický článek (+20)", false),
    CELL_BULK("Velký energetický článek (+100)", false),

    BARREL("Výbušný sud", false, isSolidObstacle = true, isShootableBarrel = true),
    TECH_PILLAR("Technologický sloup", false, isSolidObstacle = true, isShootableBarrel = false),
    CANDELABRA("Pekelný svícen", false, isSolidObstacle = true, isShootableBarrel = false)
}

enum class MonsterType(
    val titleCz: String,
    val maxHp: Int,
    val speed: Float,
    val radius: Float,
    val scoreValue: Int,
    val meleeDamage: Int,
    val rangedDamage: Int,
    val isHitscan: Boolean,
    val hasProjectile: Boolean,
    val isPartialInvisible: Boolean = false,
    val isBoss: Boolean = false
) {
    ZOMBIEMAN("Zombieman", 25, 1.55f, 0.32f, 100, 8, 10, true, false),
    SHOTGUN_GUY("Seržant s brokovnicí", 35, 1.70f, 0.33f, 200, 12, 22, true, false),
    IMP("Imp", 65, 1.95f, 0.34f, 250, 16, 20, false, true),
    PINKY_DEMON("Pinky Démon", 150, 2.85f, 0.38f, 350, 28, 0, false, false),
    SPECTRE("Spectre (Neviditelný démon)", 150, 2.90f, 0.38f, 400, 28, 0, false, false, isPartialInvisible = true),
    LOST_SOUL("Ztracená duše", 95, 3.30f, 0.30f, 300, 22, 0, false, false),
    CACODEMON("Cacodemon", 400, 1.85f, 0.42f, 700, 32, 30, false, true),
    BARON_OF_HELL("Baron pekla", 1000, 2.05f, 0.44f, 1500, 50, 42, false, true, isBoss = true),
    CYBERDEMON("Cyberdemon", 4000, 2.35f, 0.50f, 5000, 90, 95, false, true, isBoss = true)
}

enum class MonsterState {
    IDLE,
    CHASE,
    ATTACK,
    PAIN,
    DYING,
    DEAD
}

class MonsterEntity(
    val id: Int,
    val type: MonsterType,
    val spawnX: Float,
    val spawnY: Float,
    var x: Float,
    var y: Float,
    var hp: Int = type.maxHp,
    var state: MonsterState = MonsterState.IDLE,
    var stateTimer: Float = 0f,
    var attackCooldown: Float = 0.8f,
    var animPhase: Float = 0f,
    var infightTargetMonsterId: Int? = null,
    var respawnTimer: Float = 0f
)

class MapItemEntity(
    val id: Int,
    val type: ItemType,
    var x: Float,
    var y: Float,
    var collected: Boolean = false,
    var barrelHp: Int = 25,
    var explodeAnimTimer: Float = 0f
)

enum class ProjectileKind {
    ROCKET,
    PLASMA,
    BFG_ORB,
    IMP_FIREBALL,
    CACO_LIGHTNING,
    BARON_PLASMA
}

class ProjectileEntity(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val kind: ProjectileKind,
    val fromPlayer: Boolean,
    val ownerMonsterId: Int? = null,
    val directDamage: Int,
    val splashRadius: Float,
    var active: Boolean = true,
    var explodeTimer: Float = 0f
)

class DoorState(
    val gridX: Int,
    val gridY: Int,
    val tileType: Int, // 6=normal, 7=blue, 8=yellow, 9=red, 10=secret pushwall
    val isSecretPushWall: Boolean,
    var openAmount: Float = 0f, // 0f = closed, 1f = fully open
    var opening: Boolean = false,
    var closing: Boolean = false,
    var holdOpenTimer: Float = 0f,
    var secretDiscovered: Boolean = false
)

data class LevelSummaryStats(
    val mapCode: String,
    val mapTitle: String,
    val nextMapIndex: Int,
    val isSecretNext: Boolean,
    val isEpisodeCompleted: Boolean,
    val killsCount: Int,
    val totalMonsters: Int,
    val itemsCount: Int,
    val totalItems: Int,
    val secretsCount: Int,
    val totalSecrets: Int,
    val timeSeconds: Int,
    val parTimeSeconds: Int,
    val totalScore: Int
)
