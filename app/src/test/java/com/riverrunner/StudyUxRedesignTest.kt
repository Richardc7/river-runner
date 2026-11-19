package com.riverrunner

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riverrunner.engine.*
import com.riverrunner.engine.strategy.*
import com.riverrunner.i18n.ZH
import com.riverrunner.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w780dp-h360dp-land-xxhdpi")
class StudyUxRedesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeOffersTrainingBesideStartGame() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { RiverTheme(ZH) { HomeScreen(vm) } }
        compose.onNodeWithText("专项训练").assertExists().performClick()
        compose.runOnIdle { assertEquals(Screen.Training, vm.screen) }
    }

    @Test fun newGameLetsPlayerChooseLiveReferenceBeforeStarting() {
        var enabled by mutableStateOf(false)
        compose.setContent {
            RiverTheme(ZH) {
                SetupContent(
                    SessionConfig(GameMode.CASH, 9, 100, TableType.GTO),
                    {}, {}, liveStrategyEnabled = enabled,
                    onLiveStrategyChange = { enabled = it }
                )
            }
        }
        compose.onNodeWithText("本局显示实时策略参考").assertExists()
        compose.onNodeWithTag("live-strategy-switch").performClick()
        compose.runOnIdle { assertTrue(enabled) }
        compose.onNodeWithText("实时参考：开启（非已验证 GTO）").assertExists()
    }

    @Test fun historyOpensBlindReviewBeforeShowingResultsOrReplay() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.setStudyToggle("blind", true)
        vm.screen = Screen.History
        val session = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO, seed = 7))
        val hand = session.startHand()
        session.applyMove(Move.FOLD)
        val record = session.completeHand(hand)
        vm.history.add(record)
        compose.setContent {
            RiverTheme(ZH) {
                when (val screen = vm.screen) {
                    Screen.History -> HistoryScreen(vm)
                    is Screen.Replay -> EditionReplayScreen(vm, screen.record, screen.blind)
                    else -> error("Unexpected screen")
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("盲测").fetchSemanticsNodes().isNotEmpty() }
        val net = record.heroNet.toDouble() / record.bigBlind
        val result = (if (net > 0) "+" else "") + "%.1f BB".format(net)
        compose.onNodeWithText(result).assertDoesNotExist()
        compose.onNodeWithText("盲测").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("盲测复盘 · 先选后看").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("盲测复盘 · 先选后看").assertExists()
        compose.onNodeWithText("复盘 · 第 1 手").assertDoesNotExist()
    }

    @Test fun emptyTrainingRoutesToRecordingSettingsOrHeadsUpCashSetup() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.openTraining()
        vm.saveEdition(EditionConfig(recordAdvice = false))
        compose.setContent { RiverTheme(ZH) { TrainingScreen(vm, vm::closeTraining) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("去开启记录").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("去开启记录").performClick()
        compose.runOnIdle {
            assertEquals(Screen.EditionSettings, vm.screen)
            vm.saveEdition(EditionConfig(recordAdvice = true))
            vm.closeEditionSettings()
            assertEquals(Screen.Training, vm.screen)
        }
        compose.onNodeWithText("配置单挑筹码局").performClick()
        compose.runOnIdle { assertEquals(Screen.TrainingSetup, vm.screen) }
    }

    @Test fun liveTablePinsTotalIncludingPostedBetsAndLatestAction() {
        val session = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO, seed = 7))
        val hand = session.startHand()
        val seats = session.players.map { SeatInfo(it.seat, it.name, it.isHuman, it.stack, false) }
        val fmt: (Int) -> String = { Format.amount(it, hand.bigBlind, true) }
        val table = TableMapper.build(ZH, hand, seats, 1, 0, TableMapper.Stage.LIVE, fmt)
        val latest = "对手 (BB) 大盲 1 BB"
        val ui = TableScreenUi(
            table, 1, "50/100", null, false, hand.legalActions(), null,
            listOf(LogLine(LogKind.ACTION, latest), LogLine(LogKind.STREET, "翻牌")),
            null, null, true, false, false,
        )
        compose.setContent {
            RiverTheme(ZH) { TableContent(ui, fmt, {}, {}, {}, {}, {}, {}, animate = false) }
        }
        assertEquals(0, table.pot)
        assertEquals(150, table.totalPot)
        compose.onNodeWithText("底池 1.5 BB").assertExists()
        compose.onNodeWithText("最近行动").assertExists()
        assertTrue(compose.onAllNodesWithText(latest).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun trainingShowsQuestionThenExplanationAndNext() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        val engine = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO, seed = 7)).startHand()
        val view = ObservationFactory.forActor(engine, GameMode.CASH)
        val record = HandRecord(
            1L, 1L, GameMode.CASH, 1, 50, 100, 0, 0, 0, 2,
            emptyList(), IntArray(0), emptyList(), emptyMap()
        )
        val values = listOf(
            ActionValue(Move.FOLD, 0.0, 0.0, 0.0),
            ActionValue(Move.CALL, 10.0, 10.0, 10.0),
            ActionValue(Move.raiseTo(250), 25.0, 25.0, 25.0),
        )
        val item = TrainingItem(
            TrainingDrill("quiz1", "river-model", values),
            record, view, PolicySnapshot(
                listOf(PolicyChoice(Move.FOLD, .3), PolicyChoice(Move.CALL, .7)),
                null, 0.0, view.pot
            ), emptyList()
        )
        var picked by mutableStateOf<Move?>(null)
        var advanced = 0
        compose.setContent {
            RiverTheme(ZH) {
                DecisionQuestion(vm, item, null, picked,
                    onSelect = { picked = it },
                    onNext = { advanced++; picked = null })
            }
        }
        compose.onNodeWithText("你会选择哪个行动？").assertExists()
        compose.onNodeWithText("A   弃牌").performClick()
        compose.onNodeWithText("答案解析").assertExists()
        compose.onNodeWithText("保存练习记录 · 下一题 →").performClick()
        compose.runOnIdle { assertEquals(1, advanced) }
        compose.onNodeWithText("你会选择哪个行动？").assertExists()
    }
}
