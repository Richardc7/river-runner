package com.riverrunner.engine
import kotlin.random.Random
import com.riverrunner.engine.strategy.*

enum class GameMode(val code: String) { CASH("C"), TOURNAMENT("T"); companion object { fun fromCode(c: String) = entries.first { it.code == c } } }
enum class TableType { GTO, MIXED }
data class SessionConfig(val mode: GameMode, val tableSize: Int, val startingBB: Int, val tableType: TableType,
    val handsPerLevel: Int = 10, val cashAnteFraction: Double = 0.0, val seed: Long = System.nanoTime(), val edition: EditionConfig = EditionConfig())
data class BlindLevel(val level: Int, val smallBlind: Int, val bigBlind: Int, val ante: Int)
class SessionPlayer(val seat: Int, val name: String, val style: BotStyle, val isHuman: Boolean, var stack: Int) {
    val startStack = stack
    var place: Int = 0
    val alive: Boolean get() = place == 0 && stack > 0
}
data class Standing(val seat: Int, val name: String, val place: Int, val stack: Int, val isHuman: Boolean)
data class SessionSummary(val mode: GameMode, val standings: List<Standing>, val heroPlace: Int,
    val heroProfit: Double, val handsPlayed: Int, val endedEarly: Boolean)
class GameSession(val config: SessionConfig) {
    val random = Random(config.seed)
    val strategy = StrategySession(config.seed, config.mode, config.edition)
    val players: List<SessionPlayer>
    var handNumber = 0; private set
    var buttonSeat: Int; private set
    var current: HandEngine? = null; private set
    private var finished: SessionSummary? = null
    companion object {
        const val HERO_SEAT = 0
        const val BASE_BB = 100
        private val BOT_NAMES = listOf("Ace", "Blaze", "Cobra", "Dune", "Echo", "Flint", "Ghost", "Hawk", "Iris", "Jade", "Koda", "Luna", "Maverick", "Nova", "Onyx", "Pike")
        private val MIXED_POOL = listOf(BotStyle.GTO, BotStyle.GTO, BotStyle.TAG, BotStyle.LAG, BotStyle.STATION, BotStyle.NIT, BotStyle.MANIAC)
        private val BB_SCHEDULE = intArrayOf(100, 150, 200, 300, 400, 500, 600, 800, 1000, 1200, 1500, 2000, 2500, 3000, 4000, 5000, 6000, 8000, 10000, 12000, 15000, 20000)
        fun levelFor(index: Int): BlindLevel {
            val bb = if (index < BB_SCHEDULE.size) BB_SCHEDULE[index] else (BB_SCHEDULE.last() * Math.pow(1.25, (index - BB_SCHEDULE.size + 1).toDouble())).toInt() / 100 * 100
            val ante = if (index >= 3) (bb / 8) / 5 * 5 else 0
            return BlindLevel(index + 1, bb / 2, bb, ante)
        }
    }
    init {
        require(config.tableSize in 2..9)
        val names = BOT_NAMES.shuffled(random); val start = config.startingBB * BASE_BB
        players = List(config.tableSize) { i ->
            if (i == HERO_SEAT) SessionPlayer(i, "You", BotStyle.GTO, true, start)
            else {
                val style = if (config.tableType == TableType.GTO) BotStyle.GTO else MIXED_POOL[random.nextInt(MIXED_POOL.size)]
                SessionPlayer(i, names[i - 1], config.edition.opponents[i]?.legacyStyle ?: style, false, start)
            }
        }
        buttonSeat = random.nextInt(config.tableSize)
    }
    val hero: SessionPlayer get() = players[HERO_SEAT]
    val alivePlayers: List<SessionPlayer> get() = players.filter { it.alive }
    val payouts: DoubleArray get() = Icm.payoutsFor(config.tableSize)
    fun currentLevel(): BlindLevel = when (config.mode) {
        GameMode.TOURNAMENT -> levelFor(handNumber / config.handsPerLevel.coerceAtLeast(1))
        GameMode.CASH -> BlindLevel(1, BASE_BB / 2, BASE_BB, (BASE_BB * config.cashAnteFraction).toInt())
    }
    val isOver: Boolean get() = finished != null || alivePlayers.size <= 1 || !hero.alive
    fun tournamentContext(): TournamentContext? = if (config.mode == GameMode.TOURNAMENT) TournamentContext(alivePlayers.associate { it.seat to it.stack }, payouts) else null
    fun startHand(): HandEngine {
        check(!isOver)
        val alive = alivePlayers
        if (handNumber > 0) {
            var b = buttonSeat
            do { b = (b + 1) % players.size } while (!players[b].alive)
            buttonSeat = b
        } else if (!players[buttonSeat].alive) buttonSeat = alive.first().seat
        val lvl = currentLevel()
        val setup = HandSetup(lvl.smallBlind, lvl.bigBlind, lvl.ante, buttonSeat, alive.map { SeatSetup(it.seat, it.name, it.stack) })
        val deckRandom = if (config.edition.aiMode == AiMode.ADAPTIVE)
            StrategyRandom.forDecision(config.seed, handNumber, -1, -1, 0) else random
        val engine = HandEngine(setup, RandomCardSource(deckRandom))
        strategy.beginHand(engine, handNumber); current = engine; return engine
    }
    /** All live actions go through this gate so opportunities cannot be skipped. */
    fun applyMove(move: Move): Action {
        val engine = checkNotNull(current)
        val before = strategy.opportunity(engine)
        val action = engine.apply(move)
        strategy.observe(before, action)
        return action
    }

    fun botMove(seat: Int): Move {
        val engine = checkNotNull(current)
        require(engine.toAct == seat)
        return strategy.choose(engine, players.first { it.seat == seat }.style, random, tournamentContext())
    }

    fun completeHand(engine: HandEngine): HandRecord {
        val result = checkNotNull(engine.result) { "Hand not finished" }; val aliveBefore = alivePlayers.size
        for (ps in engine.players) players[ps.seat].stack = ps.stack
        val busted = engine.players.filter { it.stack == 0 }.sortedBy { it.startStack }; var place = aliveBefore
        for (b in busted) { players[b.seat].place = place; place-- }
        handNumber++
        val record = HandRecord.from(engine, config.mode, handNumber, HERO_SEAT, config.tableSize, styles = players.associate { it.seat to it.style })
        current = null
        return record.copy(won = result.won)
    }
    fun finish(early: Boolean = false): SessionSummary {
        finished?.let { return it }
        val alive = alivePlayers.sortedByDescending { it.stack }; alive.forEachIndexed { i, p -> p.place = i + 1 }
        val standings = players.map { Standing(it.seat, it.name, it.place, it.stack, it.isHuman) }.sortedBy { it.place }
        val heroPlace = hero.place
        val profit = when (config.mode) {
            GameMode.CASH -> (hero.stack - hero.startStack).toDouble() / BASE_BB
            GameMode.TOURNAMENT -> { val pool = 100.0 * config.tableSize; val pay = payouts.getOrNull(heroPlace - 1) ?: 0.0; pool * pay - 100.0 }
        }
        val s = SessionSummary(config.mode, standings, heroPlace, profit, handNumber, early); finished = s; return s
    }
}
