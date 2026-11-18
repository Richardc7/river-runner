package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32

/** Bounded, checksummed, versioned sidecar. H1 remains entirely unchanged. */
object TraceCodec {
    const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAGIC = 0x52525458
    private const val SCHEMA = 1

    fun encode(analysis: HandAnalysis): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeLong(analysis.handId); out.writeUTF(analysis.algorithmVersion); out.writeUTF(analysis.randomVersion)
            out.writeLong(analysis.sessionSeed); out.writeUTF(analysis.mode.name); out.writeInt(analysis.decisions.size)
            analysis.decisions.forEach { d ->
                out.writeInt(d.actionIndex); out.writeInt(d.seat); out.writeUTF(d.street.name); out.writeUTF(d.contextHash)
                policy(out, d.baseline); policy(out, d.exploit); out.writeUTF(d.status.name)
                require(d.evidence.size <= 32); out.writeInt(d.evidence.size)
                d.evidence.forEach { v ->
                    out.writeUTF(v.code.name); out.writeInt(v.seat); key(out, v.stat)
                    out.writeInt(v.successes); out.writeInt(v.opportunities); out.writeDouble(v.posterior); out.writeDouble(v.evidenceWeight)
                }
                require(d.opponents.size <= 9); out.writeInt(d.opponents.size)
                d.opponents.forEach { p ->
                    out.writeInt(p.seat); out.writeInt(p.handsObserved); require(p.stats.size <= 512); out.writeInt(p.stats.size)
                    p.stats.entries.sortedBy { it.key.toString() }.forEach { (k, v) ->
                        key(out, k); out.writeInt(v.successes); out.writeInt(v.opportunities)
                    }
                }
                require(d.actionValues.size <= 64); out.writeInt(d.actionValues.size)
                d.actionValues.forEach { v -> move(out, v.move); out.writeDouble(v.chips); out.writeDouble(v.low); out.writeDouble(v.high) }
                require(d.notes.size <= 32); out.writeInt(d.notes.size)
                d.notes.forEach { require(it.length <= 4096); out.writeUTF(it) }
            }
        }
        val bytes = body.toByteArray(); require(bytes.size <= MAX_BYTES)
        return ByteArrayOutputStream().also { stream -> DataOutputStream(stream).use { out ->
            out.writeInt(MAGIC); out.writeInt(SCHEMA); out.writeInt(bytes.size)
            out.writeLong(CRC32().apply { update(bytes) }.value); out.write(bytes)
        } }.toByteArray()
    }
    fun decode(bytes: ByteArray): HandAnalysis {
        require(bytes.size in 20..MAX_BYTES + 20)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == SCHEMA) { "Unknown trace format" }
            val size = input.readInt(); require(size in 0..MAX_BYTES && size == bytes.size - 20)
            val checksum = input.readLong(); val body = ByteArray(size); input.readFully(body)
            require(CRC32().apply { update(body) }.value == checksum) { "Corrupt trace" }
            DataInputStream(ByteArrayInputStream(body)).use { data ->
                val id = data.readLong(); val version = data.readUTF(); val rng = data.readUTF(); val seed = data.readLong()
                val mode = AiMode.valueOf(data.readUTF())
                val decisions = List(count(data, 256)) {
                    val index = data.readInt(); val seat = data.readInt(); val street = Street.valueOf(data.readUTF()); val hash = data.readUTF()
                    val baseline = policy(data); val exploit = policy(data); val status = AdviceStatus.valueOf(data.readUTF())
                    val evidence = List(count(data, 32)) {
                        val code = ReasonCode.valueOf(data.readUTF()); val opponent = data.readInt(); val key = key(data)
                        val successes = data.readInt(); val opportunities = data.readInt(); BayesianStat(successes, opportunities)
                        val posterior = data.readDouble(); val weight = data.readDouble()
                        require(opponent in 0..8 && posterior.isFinite() && posterior in 0.0..1.0 && weight.isFinite() && weight in 0.0..1.0)
                        ReasonEvidence(code, opponent, key, successes, opportunities, posterior, weight)
                    }
                    val opponents = List(count(data, 9)) {
                        val opponent = data.readInt(); val hands = data.readInt(); require(opponent in 0..8 && hands >= 0)
                        val stats = linkedMapOf<StatKey, BayesianStat>()
                        repeat(count(data, 512)) { val k = key(data); require(k !in stats); stats[k] = BayesianStat(data.readInt(), data.readInt()) }
                        OpponentSnapshot(opponent, hands, stats.toMap())
                    }
                    require(opponents.map { it.seat }.distinct().size == opponents.size)
                    val values = List(count(data, 64)) { ActionValue(move(data), data.readDouble(), data.readDouble(), data.readDouble()) }
                    val notes = List(count(data, 32)) { data.readUTF().also { require(it.length <= 4096) } }
                    DecisionTrace(index, seat, street, hash, baseline, exploit, status, evidence, opponents, values, notes)
                }
                require(data.available() == 0)
                HandAnalysis(id, version, rng, seed, mode, decisions)
            }
        }
    }
    private fun count(input: DataInputStream, max: Int): Int = input.readInt().also { require(it in 0..max) }
    private fun move(out: DataOutputStream, move: Move) { out.writeUTF(move.type.name); out.writeInt(move.raiseTo) }
    private fun move(input: DataInputStream): Move {
        val type = MoveType.valueOf(input.readUTF()); val amount = input.readInt()
        require(amount >= 0 && (type == MoveType.RAISE || amount == 0))
        return Move(type, amount)
    }
    private fun key(out: DataOutputStream, key: StatKey) {
        out.writeUTF(key.kind.name); out.writeUTF(key.street?.name ?: ""); out.writeUTF(key.size?.name ?: ""); out.writeBoolean(key.multiway)
    }
    private fun key(input: DataInputStream): StatKey {
        val kind = StatKind.valueOf(input.readUTF()); val street = input.readUTF(); val size = input.readUTF()
        return StatKey(kind, street.takeIf { it.isNotEmpty() }?.let(Street::valueOf), size.takeIf { it.isNotEmpty() }?.let(SizeClass::valueOf), input.readBoolean())
    }
    private fun policy(out: DataOutputStream, p: PolicySnapshot) {
        out.writeInt(p.choices.size); p.choices.forEach { move(out, it.move); out.writeDouble(it.probability) }
        out.writeBoolean(p.equity != null); p.equity?.let { out.writeDouble(it) }; out.writeDouble(p.potOdds); out.writeInt(p.pot)
    }
    private fun policy(input: DataInputStream): PolicySnapshot {
        val choices = List(count(input, 64)) { PolicyChoice(move(input), input.readDouble()) }
        val equity = if (input.readBoolean()) input.readDouble() else null
        return PolicySnapshot(choices, equity, input.readDouble(), input.readInt())
    }
}

class StrategyTraceStore(private val root: File, private val profileId: String) {
    init { require(profileId.matches(Regex("[A-Za-z0-9_-]{1,80}"))) }
    private val dir get() = File(root, "analysis_$profileId")
    @Synchronized fun save(analysis: HandAnalysis) {
        check(dir.exists() || dir.mkdirs()) { "Cannot create analysis directory" }
        val destination = File(dir, "${analysis.handId}.rrt")
        val temporary = File.createTempFile("trace-", ".tmp", dir)
        try {
            FileOutputStream(temporary).use { it.write(TraceCodec.encode(analysis)); it.fd.sync() }
            try { Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (e: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { temporary.delete() }
    }
    @Synchronized fun load(handId: Long): HandAnalysis? {
        if (handId <= 0) return null
        val f = File(dir, "$handId.rrt")
        if (!f.isFile || f.length() !in 20L..(TraceCodec.MAX_BYTES + 20L)) return null
        return try { TraceCodec.decode(f.readBytes()).takeIf { it.handId == handId } } catch (e: Exception) { null }
    }
    /** Call after history pruning and before review. Favorites survive because their ids are retained. */
    @Synchronized fun retain(handIds: Set<Long>) {
        dir.listFiles()?.forEach { f ->
            val id = f.name.removeSuffix(".rrt").toLongOrNull()
            if (f.isFile && f.extension == "rrt" && id != null && id !in handIds) f.delete()
        }
    }
    @Synchronized fun deleteAll() {
        dir.listFiles()?.filter { it.isFile && (it.extension == "rrt" || it.extension == "tmp") }?.forEach { it.delete() }
        dir.delete()
    }
}
