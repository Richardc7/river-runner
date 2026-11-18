package com.riverrunner.engine

data class RecordPlayer(val seat: Int, val name: String, val style: BotStyle, val startStack: Int, val hole: IntArray)
data class HandRecord(val id: Long, val time: Long, val mode: GameMode, val handNumber: Int,
    val smallBlind: Int, val bigBlind: Int, val ante: Int, val buttonSeat: Int, val heroSeat: Int,
    val tableSize: Int, val players: List<RecordPlayer>, val board: IntArray, val actions: List<Action>,
    val won: Map<Int, Int>, val favorite: Boolean = false, val cashRules: CashRules = CashRules(), val rake: Int = 0) {
    val hero: RecordPlayer? get() = players.firstOrNull { it.seat == heroSeat }
    val heroNet: Int get() { val committed = actions.filter { it.seat == heroSeat }.sumOf { it.amount }; return (won[heroSeat] ?: 0) - committed }
    fun encode(): String {
        val sb = StringBuilder()
        sb.append(if (cashRules.isDefault && rake == 0) "H1|" else "H2|").append(id).append('|').append(time).append('|').append(mode.code).append('|')
            .append(handNumber).append('|').append(smallBlind).append('|').append(bigBlind).append('|')
            .append(ante).append('|').append(buttonSeat).append('|').append(heroSeat).append('|')
            .append(tableSize).append('|').append(if (favorite) 1 else 0).append('|')
        sb.append(players.joinToString(";") { "${it.seat},${clean(it.name)},${it.style.code},${it.startStack},${Cards.format(it.hole)}" })
        sb.append('|').append(Cards.format(board)).append('|')
        sb.append(actions.joinToString(";") { "${it.street.ordinal},${it.seat},${it.type.ordinal},${it.amount},${it.streetTotal},${if (it.allIn) 1 else 0}" })
        sb.append('|'); sb.append(won.entries.joinToString(";") { "${it.key},${it.value}" })
        if (!cashRules.isDefault || rake != 0) {
            sb.append('|').append(if (cashRules.utgStraddle) 1 else 0).append(',')
                .append(cashRules.rakeBasisPoints).append(',').append(cashRules.rakeCapBB).append(',')
                .append(if (cashRules.noFlopNoDrop) 1 else 0).append(',').append(rake)
        }
        return sb.toString()
    }
    companion object {
        private fun clean(s: String) = s.replace(Regex("[|;,\\n]"), " ")
        fun decode(line: String): HandRecord? = try {
            require(line.length <= 100000)
            val f = line.split('|'); require(f[0] == "H1" || f[0] == "H2")
            val extra = if (f[0] == "H2") f[16].split(',') else emptyList()
            require(extra.isEmpty() || extra.size == 5)
            if (extra.isNotEmpty()) require(extra[0] in listOf("0", "1") && extra[3] in listOf("0", "1"))
            val rules = if (extra.isEmpty()) CashRules() else CashRules(extra[0] == "1", extra[1].toInt(), extra[2].toInt(), extra[3] == "1")
            val rake = if (extra.isEmpty()) 0 else extra[4].toInt().also { require(it >= 0) }
            val players = f[12].split(';').filter { it.isNotEmpty() }.map {
                val p = it.split(','); RecordPlayer(p[0].toInt(), p[1], BotStyle.fromCode(p[2]), p[3].toInt(), Cards.parseMany(p[4]))
            }
            val actions = if (f[14].isEmpty()) emptyList() else f[14].split(';').map {
                val a = it.split(','); Action(Street.entries[a[0].toInt()], a[1].toInt(), ActionType.entries[a[2].toInt()], a[3].toInt(), a[4].toInt(), a[5] == "1")
            }
            val won = if (f.size <= 15 || f[15].isEmpty()) emptyMap() else f[15].split(';').associate { val w = it.split(','); w[0].toInt() to w[1].toInt() }
            HandRecord(f[1].toLong(), f[2].toLong(), GameMode.fromCode(f[3]), f[4].toInt(),
                f[5].toInt(), f[6].toInt(), f[7].toInt(), f[8].toInt(), f[9].toInt(), f[10].toInt(),
                players, if (f[13].isEmpty()) IntArray(0) else Cards.parseMany(f[13]), actions, won, f[11] == "1", rules, rake).also { record ->
                require(record.players.size in 2..9 && record.tableSize in record.players.size..9)
                require(record.players.map { it.seat }.distinct().size == record.players.size)
                require(record.players.all { it.seat in 0..8 && it.startStack in 1..100000000 && it.hole.size == 2 })
                require(record.bigBlind in 1..1000000 && record.smallBlind in 1..record.bigBlind && record.ante >= 0)
                require(record.board.size in listOf(0, 3, 4, 5) && record.actions.size <= 600)
                val cards = record.players.flatMap { it.hole.toList() } + record.board.toList()
                require(cards.all { it in 0..51 } && cards.distinct().size == cards.size)
                val seats = record.players.map { it.seat }.toSet()
                require(record.heroSeat in seats && record.buttonSeat in seats)
                require(record.actions.all { it.seat in seats && it.amount >= 0 && it.streetTotal >= 0 })
                require(record.won.all { it.key in seats && it.value >= 0 })
                require(f[0] != "H1" || record.actions.none { it.type == ActionType.STRADDLE })
                if (f[0] == "H2") {
                    require(record.mode == GameMode.CASH)
                    require(record.actions.sumOf { it.amount.toLong() } - record.won.values.sumOf { it.toLong() } == record.rake.toLong())
                }
            }
        } catch (e: Exception) { null }
        fun from(engine: HandEngine, mode: GameMode, handNumber: Int, heroSeat: Int, tableSize: Int, styles: Map<Int, BotStyle>): HandRecord {
            val now = System.currentTimeMillis()
            return HandRecord(now * 1000 + (handNumber % 1000), now, mode, handNumber,
                engine.setup.smallBlind, engine.setup.bigBlind, engine.setup.ante, engine.buttonSeat, heroSeat, tableSize,
                engine.players.map { RecordPlayer(it.seat, it.name, styles[it.seat] ?: BotStyle.GTO, it.startStack, it.hole) },
                engine.board.toIntArray(), engine.actions.toList(), engine.result?.won ?: emptyMap(),
                cashRules = engine.setup.cashRules, rake = engine.result?.rake ?: 0)
        }
    }
    override fun equals(other: Any?): Boolean = other is HandRecord && other.id == id && other.favorite == favorite
    override fun hashCode(): Int = id.hashCode()
}
data class ReplayFrame(val index: Int, val action: Action?, val street: Street, val players: List<PlayerState>, val board: List<Int>, val pot: Int, val toAct: Int, val isEnd: Boolean)
class Replayer(val record: HandRecord) {
    private val voluntary = record.actions.filter { !it.type.isPost }
    private fun newEngine(): HandEngine {
        val setup = HandSetup(record.smallBlind, record.bigBlind, record.ante, record.buttonSeat, record.players.map { SeatSetup(it.seat, it.name, it.startStack) }, record.cashRules)
        val board = IntArray(5) { i -> record.board.getOrElse(i) { -1 } }
        return HandEngine(setup, PresetCardSource(record.players.associate { it.seat to it.hole }, board))
    }
    private fun frame(e: HandEngine, idx: Int, a: Action?, end: Boolean) = ReplayFrame(idx, a, e.street, e.players, e.board.toList(), e.pot, if (e.phase == Phase.BETTING) e.toAct else -1, end)
    val frames: List<ReplayFrame> by lazy {
        val out = ArrayList<ReplayFrame>(); val e = newEngine(); out += frame(e, 0, null, false)
        for (a in voluntary) {
            while (e.phase == Phase.STREET_DONE && e.street.ordinal < a.street.ordinal) { e.dealNextStreet(); out += frame(e, out.size, null, false) }
            if (e.phase != Phase.BETTING) break
            e.apply(toMove(a)); out += frame(e, out.size, a, e.phase == Phase.HAND_DONE)
        }
        while (e.phase == Phase.STREET_DONE && e.board.size < record.board.size) { e.dealNextStreet(); out += frame(e, out.size, null, e.phase == Phase.HAND_DONE) }
        if (out.isNotEmpty() && !out.last().isEnd) out[out.size - 1] = out.last().copy(isEnd = true)
        out
    }
    fun engineBeforeAction(actionIndex: Int): HandEngine {
        val e = newEngine()
        for (i in 0 until actionIndex) { val a = voluntary[i]; while (e.phase == Phase.STREET_DONE) e.dealNextStreet(); e.apply(toMove(a)) }
        while (e.phase == Phase.STREET_DONE) e.dealNextStreet()
        return e
    }
    fun actionIndexOf(frame: ReplayFrame): Int {
        var count = 0
        for (f in frames) if (f.action != null) { if (f.index == frame.index) return count; count++ }
        return -1
    }
    private fun toMove(a: Action): Move = when (a.type) {
        ActionType.FOLD -> Move.FOLD; ActionType.CHECK -> Move.CHECK; ActionType.CALL -> Move.CALL
        ActionType.BET, ActionType.RAISE -> Move.raiseTo(a.streetTotal); else -> error("post")
    }
}
