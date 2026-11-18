package com.riverrunner

import com.riverrunner.engine.*
import com.riverrunner.engine.strategy.*
import com.riverrunner.ui.StudyPresentation
import org.junit.Assert.*
import org.junit.Test

class StudyPresentationTest {
    @Test fun chineseCaptionsNeverExposeEnglishEnumNames() {
        assertEquals("弃牌", StudyPresentation.moveType(MoveType.FOLD, true))
        assertEquals("跟注", StudyPresentation.moveType(MoveType.CALL, true))
        assertEquals("翻牌前", StudyPresentation.street(Street.PREFLOP, true))
        assertEquals("加注", StudyPresentation.actionType(ActionType.RAISE, true))
        assertEquals("翻牌前面对加注", StudyPresentation.topic("Preflop facing raise", true))
        assertEquals("Raise", StudyPresentation.moveType(MoveType.RAISE, false))
    }
    @Test fun actualModelNotesAreLocalizedOrSafelyHidden() {
        assertTrue(StudyPresentation.modelNote("Top model range classes: AA: 25%", true).startsWith("模型估计"))
        assertTrue(StudyPresentation.modelNote("River EV is coaching-only in this version; it does not override the bounded live policy.", true).contains("训练"))
        assertFalse(StudyPresentation.modelNote("Untranslated engine diagnostics 2026", true).contains("Untranslated"))
        assertEquals("Untranslated engine diagnostics 2026", StudyPresentation.modelNote("Untranslated engine diagnostics 2026", false))
    }
    @Test fun atMostSixTrueAlternativesAndBestAlwaysIncluded() {
        val options = listOf(
            ActionValue(Move.FOLD, 0.0, 0.0, 0.0),
            ActionValue(Move.CALL, 10.0, 10.0, 10.0)
        ) + (100..900 step 100).map { size ->
            ActionValue(Move.raiseTo(size), size.toDouble(), size.toDouble(), size.toDouble())
        }
        val drill = TrainingDrill("test", "river-model", options)
        val picked = StudyPresentation.choices(drill)
        assertTrue(picked.size <= 6)
        assertTrue(drill.best.move in picked.map { it.move })
        assertTrue(picked.all { it in options })
        assertEquals(picked.map { it.move }.distinct().size, picked.size)
    }
    @Test fun opponentAdviceIsExplicitlyProbabilisticNotHiddenHoleFacts() {
        val action = Action(Street.TURN, 1, ActionType.RAISE, 800, 800, false)
        val text = StudyPresentation.opponentReason(action, BotStyle.LAG, true)
        assertTrue(text.contains("可能"))
        assertFalse(text.contains("確定"))
        assertTrue(StudyPresentation.opponentReason(action, BotStyle.LAG, false).contains("loose-aggressive"))
    }
}
