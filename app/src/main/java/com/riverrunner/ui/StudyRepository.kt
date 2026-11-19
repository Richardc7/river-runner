package com.riverrunner.ui

import android.content.ContentResolver
import android.net.Uri
import com.riverrunner.data.Profile
import com.riverrunner.data.ProfileStore
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.Replayer
import com.riverrunner.engine.strategy.DecisionAssessment
import com.riverrunner.engine.strategy.DecisionFingerprint
import com.riverrunner.engine.strategy.HandAnalysis
import com.riverrunner.engine.strategy.ObservationFactory
import com.riverrunner.engine.strategy.ReferenceBook
import com.riverrunner.engine.strategy.ReviewProgress
import com.riverrunner.engine.strategy.SolverSpot
import com.riverrunner.engine.strategy.StrategyTraceStore
import com.riverrunner.engine.strategy.StudyCoach
import com.riverrunner.engine.strategy.TrainingCurriculum
import com.riverrunner.engine.strategy.TrainingProgress
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Encapsulates disk-backed study analysis, lightweight metadata indexing,
 * spaced review, training curriculum, external solver reference storage,
 * and ZIP save backup/restore for [AppViewModel].
 */
internal class StudyRepository(private val analysisRoot: File) {

    val practiceStorage = Dispatchers.IO.limitedParallelism(1)

    fun analysisStore(profileId: String): StrategyTraceStore = StrategyTraceStore(analysisRoot, profileId)

    private fun indexStore(profileId: String): StudyIndexStore = StudyIndexStore(analysisRoot, profileId)

    private fun computeIndexEntry(record: HandRecord, analysis: HandAnalysis?): StudyIndexEntry {
        if (analysis == null) return StudyIndexEntry(record.id, hasBlind = false, hasDeviation = false, hasDrill = false)
        val replayer = Replayer(record)
        val decisions = historicalDecisions(record, analysis, replayer)
        val hasBlind = blindDecisions(record, decisions).isNotEmpty()
        val hasDeviation = decisions.any { (view, trace, action) ->
            runCatching { StudyCoach.assess(record.id, view, trace, action) }.getOrNull()?.needsStudy == true
        }
        val hasDrill = analysis.decisions.any { it.actionValues.size >= 2 && it.seat == record.heroSeat }
        return StudyIndexEntry(record.id, hasBlind, hasDeviation, hasDrill)
    }

    /** Indexes a newly saved hand and its analysis so later History/Training loads skip full scans. */
    suspend fun indexHand(profileId: String, record: HandRecord, analysis: HandAnalysis?, retainedIds: Set<Long>) =
        withContext(practiceStorage) {
            runCatching {
                indexStore(profileId).upsert(computeIndexEntry(record, analysis), retainedIds)
            }
        }

    suspend fun analysisFor(profileId: String, retainedIds: Set<Long>, recordId: Long): HandAnalysis? =
        withContext(Dispatchers.IO) {
            analysisStore(profileId).also { it.retain(retainedIds) }.load(recordId)
        }

    /** V0.4 drills: derive only from saved, contemporaneously modelled river/turn/flop choices. */
    suspend fun trainingItems(profileId: String, records: List<HandRecord>): List<TrainingItem> {
        val idxStore = indexStore(profileId)
        val indexSnapshot = withContext(practiceStorage) { idxStore.load() }
        val newEntries = ArrayList<StudyIndexEntry>()
        val out = withContext(Dispatchers.IO) {
            val store = analysisStore(profileId)
            val items = ArrayList<TrainingItem>()
            for (record in records.takeLast(300)) {
                currentCoroutineContext().ensureActive()
                val cached = indexSnapshot[record.id]
                if (cached != null && !cached.hasDrill) continue
                val analysis = runCatching { store.load(record.id) }.getOrNull()
                if (cached == null) {
                    newEntries += computeIndexEntry(record, analysis)
                }
                if (analysis == null) continue
                val replayer = Replayer(record)
                for (trace in analysis.decisions) {
                    if (trace.actionValues.size < 2 || trace.seat != record.heroSeat) continue
                    val view = runCatching {
                        ObservationFactory.forActor(replayer.engineBeforeAction(trace.actionIndex), record.mode)
                    }.getOrNull() ?: continue
                    if (DecisionFingerprint.of(view) != trace.contextHash) continue
                    val exercise = TrainingCurriculum.fromHistory(listOf(analysis)).firstOrNull {
                        it.id == "h" + record.id + "a" + trace.actionIndex
                    } ?: continue
                    items += TrainingItem(
                        exercise,
                        record,
                        view,
                        trace.baseline,
                        trace.notes.toList(),
                        "${analysis.algorithmVersion} / ${analysis.randomVersion}",
                    )
                }
            }
            items
        }
        if (newEntries.isNotEmpty()) {
            withContext(practiceStorage) { runCatching { idxStore.upsertAll(newEntries) } }
        }
        return out
    }

    suspend fun hasBlindDecisions(profileId: String, record: HandRecord): Boolean {
        val idxStore = indexStore(profileId)
        withContext(practiceStorage) { idxStore.load()[record.id] }?.let { return it.hasBlind }
        val entry = withContext(Dispatchers.IO) {
            val analysis = analysisStore(profileId).load(record.id)
            computeIndexEntry(record, analysis)
        }
        withContext(practiceStorage) { runCatching { idxStore.upsert(entry) } }
        return entry.hasBlind
    }

    /** Aggregates only fingerprint-matched historical hero decision-time records. */
    suspend fun studyAssessments(profileId: String, records: List<HandRecord>): List<DecisionAssessment> =
        withContext(Dispatchers.IO) {
            val store = analysisStore(profileId)
            records.takeLast(300).flatMap { record ->
                currentCoroutineContext().ensureActive()
                assessmentsFor(record, store).toList()
            }
        }

    suspend fun handsWithModelDeviations(profileId: String, records: List<HandRecord>): Set<Long> {
        val idxStore = indexStore(profileId)
        val indexSnapshot = withContext(practiceStorage) { idxStore.load() }
        val newEntries = ArrayList<StudyIndexEntry>()
        val result = withContext(Dispatchers.IO) {
            val store = analysisStore(profileId)
            buildSet {
                for (record in records) {
                    currentCoroutineContext().ensureActive()
                    val cached = indexSnapshot[record.id]
                    if (cached != null) {
                        if (cached.hasDeviation) add(record.id)
                    } else {
                        val analysis = runCatching { store.load(record.id) }.getOrNull()
                        val entry = computeIndexEntry(record, analysis)
                        newEntries += entry
                        if (entry.hasDeviation) add(record.id)
                    }
                }
            }
        }
        if (newEntries.isNotEmpty()) {
            withContext(practiceStorage) { runCatching { idxStore.upsertAll(newEntries) } }
        }
        return result
    }

    /**
     * Collects recent eligible heads-up river spots for batch TexasSolver export,
     * prioritizing hands with model deviations first and deduplicating by context hash.
     */
    suspend fun exportableSolverSpots(
        profileId: String,
        records: List<HandRecord>,
        limit: Int = 50,
    ): List<Pair<HandRecord, SolverSpot>> {
        val index = withContext(practiceStorage) { indexStore(profileId).load() }
        return withContext(Dispatchers.IO) {
            val store = analysisStore(profileId)
            val recent = records.takeLast(300).asReversed()
            val ordered = recent.sortedByDescending { index[it.id]?.hasDeviation == true }
            val seenHashes = hashSetOf<String>()
            val spots = ArrayList<Pair<HandRecord, SolverSpot>>()
            for (record in ordered) {
                currentCoroutineContext().ensureActive()
                if (spots.size >= limit) break
                val analysis = runCatching { store.load(record.id) }.getOrNull() ?: continue
                val replayer = Replayer(record)
                for ((view, trace, _) in historicalDecisions(record, analysis, replayer)) {
                    if (spots.size >= limit) break
                    val spot = SolverSpot.from(view) ?: continue
                    if (seenHashes.add(trace.contextHash)) {
                        spots += record to spot
                    }
                }
            }
            spots
        }
    }

    private fun assessmentsFor(record: HandRecord, store: StrategyTraceStore): Sequence<DecisionAssessment> = sequence {
        val analysis = runCatching { store.load(record.id) }.getOrNull() ?: return@sequence
        for ((view, trace, action) in historicalDecisions(record, analysis, Replayer(record))) {
            val assessment = runCatching { StudyCoach.assess(record.id, view, trace, action) }.getOrNull() ?: continue
            yield(assessment)
        }
    }

    suspend fun loadAnalysis(profileId: String, id: Long): HandAnalysis? =
        withContext(Dispatchers.IO) { runCatching { analysisStore(profileId).load(id) }.getOrNull() }

    suspend fun loadSpacedProgress(profileId: String): ReviewProgress =
        withContext(practiceStorage) { SpacedReviewStore(analysisRoot, profileId).load() }

    suspend fun saveSpacedProgress(profileId: String, value: ReviewProgress) =
        withContext(practiceStorage) { SpacedReviewStore(analysisRoot, profileId).save(value) }

    suspend fun loadTrainingProgress(profileId: String): TrainingProgress =
        withContext(practiceStorage) { TrainingProgressStore(analysisRoot, profileId).load() }

    suspend fun saveTrainingProgress(profileId: String, progress: TrainingProgress) =
        withContext(practiceStorage) { TrainingProgressStore(analysisRoot, profileId).save(progress) }

    suspend fun resetPracticeProgress(profileId: String) = withContext(practiceStorage) {
        TrainingProgressStore(analysisRoot, profileId).save(TrainingProgress())
        SpacedReviewStore(analysisRoot, profileId).save(ReviewProgress())
    }

    suspend fun loadReference(profileId: String): ReferenceBook? =
        withContext(Dispatchers.IO) { TrainingReferenceStore(analysisRoot, profileId).load() }

    suspend fun saveReference(profileId: String, book: ReferenceBook) =
        withContext(Dispatchers.IO) { TrainingReferenceStore(analysisRoot, profileId).save(book) }

    fun deleteProfileArtifacts(profileId: String) {
        analysisStore(profileId).deleteAll()
        indexStore(profileId).delete()
        TrainingProgressStore(analysisRoot, profileId).delete()
        SpacedReviewStore(analysisRoot, profileId).delete()
        TrainingReferenceStore(analysisRoot, profileId).delete()
    }

    suspend fun exportSave(
        selected: Profile,
        hands: List<HandRecord>,
        resolver: ContentResolver,
        uri: Uri,
    ) = withContext(practiceStorage) {
        val id = selected.id
        val traces = analysisStore(id)
        val backup = SaveBackup(
            selected,
            hands,
            hands.mapNotNull { traces.load(it.id) },
            TrainingProgressStore(analysisRoot, id).load(),
            SpacedReviewStore(analysisRoot, id).load(),
            TrainingReferenceStore(analysisRoot, id).load(),
        )
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open backup")
        SaveBackupCodec.write(backup, output)
    }

    suspend fun importSave(
        profiles: ProfileStore,
        resolver: ContentResolver,
        uri: Uri,
    ): Profile = withContext(practiceStorage) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open backup")
        val backup = SaveBackupCodec.read(input)
        var importedId: String? = null
        try {
            profiles.restore(backup.profile) { id ->
                importedId = id
                profiles.historyFile(id).bufferedWriter().use { out ->
                    for (hand in backup.hands) {
                        out.write(hand.encode())
                        out.newLine()
                    }
                }
                val traces = analysisStore(id)
                val byHandId = backup.analyses.associateBy { it.handId }
                backup.analyses.forEach(traces::save)
                val indexMap = linkedMapOf<Long, StudyIndexEntry>()
                for (hand in backup.hands) {
                    indexMap[hand.id] = computeIndexEntry(hand, byHandId[hand.id])
                }
                indexStore(id).save(indexMap)
                TrainingProgressStore(analysisRoot, id).save(backup.training)
                SpacedReviewStore(analysisRoot, id).save(backup.review)
                backup.reference?.let { TrainingReferenceStore(analysisRoot, id).save(it) }
            }
        } catch (failure: Exception) {
            importedId?.let { deleteProfileArtifacts(it) }
            throw failure
        }
    }
}
