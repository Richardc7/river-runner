package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.test.*

class ActionValueComparisonTest {
    private val fold = ActionValue(Move.FOLD, 0.0, 0.0, 0.0)
    private val borderlineCall = ActionValue(Move.CALL, 10.0, -20.0, 40.0)
    private val clearCall = ActionValue(Move.CALL, 170.0, 130.0, 210.0)
    private val facing = DecisionView(
        0, Cards.parseMany("AsKh").toList(), Street.RIVER, Cards.parseMany("Ac7d2h9s3c").toList(),
        2400, 100, 0, listOf(1, 2, 0), listOf(
            PublicSeat(0, 10000, 9400, 0, 600, false, false),
            PublicSeat(1, 10000, 9100, 300, 900, false, false),
            PublicSeat(2, 10000, 9100, 300, 900, false, false),
        ), emptyList(), LegalActions(0, 300, false, true, true, false, 600, 9400, 300, 0, 2400),
        GameMode.CASH,
    )
    private val actualFold = Action(Street.RIVER, 0, ActionType.FOLD, 0, 0, false)
    private val actualCall = Action(Street.RIVER, 0, ActionType.CALL, 300, 300, false)
    private fun trace(call: ActionValue, version: String? = MultiwayRiverEv.VERSION): DecisionTrace {
        val policy = PolicySnapshot(
            listOf(PolicyChoice(Move.FOLD, .9), PolicyChoice(Move.CALL, .1)), null, .11, 2400,
        )
        return DecisionTrace(0, 0, Street.RIVER, DecisionFingerprint.of(facing), policy, policy,
            AdviceStatus.BASELINE, emptyList(), emptyList(), listOf(fold, call),
            listOfNotNull(version?.let { "$it: terminal cash river" }))
    }
    private fun history(d: DecisionTrace) = HandAnalysis(1, "test", "test", 1, AiMode.ADAPTIVE, listOf(d))

    @Test fun overlappingOrTouchingBandsDoNotFlagOrGradeEitherAction() {
        val calls = listOf(borderlineCall, ActionValue(Move.CALL, 10.0, 0.0, 20.0))
        for (call in calls) {
            val d = trace(call)
            for (a in listOf(actualFold, actualCall)) {
                val model = StudyCoach.assess(1, facing, d, a)
                assertNull(model.modelGapBB)
                assertNull(model.sampledEvBest)
                assertFalse(model.needsStudy, "Policy frequency must not override the unresolved EV evidence")
            }
            assertNull(d.gradableEvGapChips(Move.CALL))
            assertTrue(TrainingCurriculum.fromHistory(listOf(history(d))).isEmpty())
        }
    }

    @Test fun separatedBandsGradeBothChoicesByTheSameConservativeGap() {
        val d = trace(clearCall)
        val folded = StudyCoach.assess(1, facing, d, actualFold)
        assertEquals(1.3, folded.modelGapBB)
        assertEquals(MoveType.CALL, folded.sampledEvBest)
        assertTrue(folded.needsStudy)
        val called = StudyCoach.assess(1, facing, d, actualCall)
        assertEquals(0.0, called.modelGapBB)
        assertFalse(called.needsStudy, "The heuristic favours FOLD but should not override EV")
        assertNull(d.gradableEvGapChips(Move.raiseTo(600)))

        val drill = TrainingCurriculum.fromHistory(listOf(history(d))).single()
        assertTrue(drill.usesSamplingBands)
        assertEquals(Move.CALL, drill.best.move)
        assertEquals(130.0, drill.grade(Move.FOLD).modelLossChips)
        assertTrue(drill.grade(Move.CALL).exact)
    }

    @Test fun clearlyNegativeCallMakesFoldTheGradableAnswer() {
        val d = trace(ActionValue(Move.CALL, -170.0, -210.0, -130.0))
        assertEquals(Move.FOLD, d.gradableBestEv()?.move)
        assertEquals(130.0, d.gradableEvGapChips(Move.CALL))
        assertEquals(0.0, d.gradableEvGapChips(Move.FOLD))
    }

    @Test fun oldMultiwayTracesStayReadableButCannotBeScored() {
        val old = history(trace(clearCall, "multiway-river-closure-v1"))
        val decoded = TraceCodec.decode(TraceCodec.encode(old))
        assertEquals(old, decoded)
        assertTrue(decoded.decisions.single().isMultiwayEv)
        assertNull(decoded.decisions.single().gradableBestEv())
        assertTrue(TrainingCurriculum.fromHistory(listOf(decoded)).isEmpty())
    }

    @Test fun unsupportedNewAndOldMultiwayNodesAreNotFlaggedFromHeuristics() {
        for (note in listOf(
            "${MultiwayRiverEv.VERSION}: no comparison",
            "Multiway river continuation is not yet evaluated: a later opponent can still act.",
            "Joint range sampling produced insufficient effective samples",
        )) {
            val trace = trace(clearCall).copy(actionValues = emptyList(), notes = listOf(note))
            assertTrue(trace.isMultiwayEv)
            assertNull(trace.gradableBestEv())
            assertFalse(StudyCoach.assess(1, facing, trace, actualCall).needsStudy)
        }
    }

    @Test fun legacyPointEstimateGradingIsUnchanged() {
        val d = trace(borderlineCall, version = null)
        assertFalse(d.isMultiwayEv)
        val drill = TrainingCurriculum.fromHistory(listOf(history(d))).single()
        assertFalse(drill.usesSamplingBands)
        assertEquals(10.0, drill.grade(Move.FOLD).modelLossChips)
        assertTrue(StudyCoach.assess(1, facing, d, actualCall).needsStudy)
    }
}
