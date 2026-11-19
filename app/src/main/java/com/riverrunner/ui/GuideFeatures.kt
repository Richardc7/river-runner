package com.riverrunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class GuideText(val zh: String, val en: String) {
    fun display(chinese: Boolean): String = if (chinese) zh else en
}

private data class FeatureLesson(val title: GuideText, val body: GuideText)

private enum class FeatureTopic(val title: GuideText, val lessons: List<FeatureLesson>) {
    PLAY(GuideText("开局与牌桌", "Play"), listOf(
        FeatureLesson(
            GuideText("开始游戏与快速开始", "Start a game and quick start"),
            GuideText(
                "「开始游戏」可选择人数、起始筹码、桌型和规则，最多九人。「快速开始」直接沿用上一次开局设置。筹码局用大盲计净输赢，锦标赛按设定手数涨盲，后期会加前注。提前离桌只显示筹码暂列名次。",
                "Start a game to choose table size, stacks and rules for up to nine seats. Quick start reuses your last setup. Cash games track net results in big blinds. Tournament blinds rise on a hand schedule, and antes start at later levels. Leaving early shows provisional chip standings.",
            ),
        ),
        FeatureLesson(
            GuideText("基准桌、混合桌与自定义座位", "Baseline, mixed tables and seat settings"),
            GuideText(
                "基准桌对手默认用基准策略，混合桌会分配多种模拟打法。开局时点「自定义对手及规则」，或在设置打开自定义 AI。点座位卡可指定对手风格，筹码局还可单独设起始筹码。保存后从新牌局生效。姓名可以随语言显示，也可在设置中选择是否随打法风格取名。",
                "The baseline table starts with a common policy, while mixed tables assign varied bot styles. Open custom opponents and rules in setup, or custom AI in Settings. Seat cards let you choose opponent styles and, in cash games, individual starting stacks. Saved changes apply to new games. Names follow the selected language and can also reflect play style.",
            ),
        ),
        FeatureLesson(
            GuideText("原版基准、动态剥削与决策记录", "Legacy, adaptive AI and decision recording"),
            GuideText(
                "原版基准保留既有打法。动态剥削会按本局公开行动积累证据，样本少时保留基准。开启「保存决策时的证据与双轨建议」后，盲测、双轨分析和训练会读取当时保存的决策。",
                "Legacy keeps the established opponent policy. Adaptive mode uses evidence from public actions in the session and retains the baseline when samples are thin. Enable recording of decision-time evidence and both recommendations to supply blind replay, policy comparison and training.",
            ),
        ),
        FeatureLesson(
            GuideText("筹码局规则", "Cash rules"),
            GuideText(
                "可设枪口位强制加盲、抽水比例、每手抽水上限、未见翻牌不抽水及每座位自动重买次数。重买在筹码用完后补回设定起始量，手动补码在两手之间进行。抽水局的牌桌赔率按抽水前底池显示。",
                "Configure a live UTG straddle, rake rate and cap per hand, no flop no drop, and automatic rebuys per seat. A rebuy restores the configured stack after busting. Manual top-ups are available between hands. The live pot-odds readout uses the pot before rake.",
            ),
        ),
        FeatureLesson(
            GuideText("行动区、练习模式与实时参考", "Action controls, practice and live reference"),
            GuideText(
                "桌面下方固定显示最近行动，右侧是完整行动记录和操作。加注快捷项和滑杆设定金额，点行动按钮确认。练习模式显示需跟注、底池赔率、所需胜率、筹码底池比和牌型。实时「策略参考」可在开局时开启，也可在设置指定新牌局默认值。右上方可开关每手复盘、收藏与音效。",
                "The latest action stays beneath the table, with the full log and controls on the right. Bet presets and the slider set a size. Tap the action to confirm. Practice shows the call cost, pot odds, break-even equity, stack-to-pot ratio and your hand. Enable live strategy reference in setup or set its default in Settings. The toolbar also has review-each-hand, favourite and sound controls.",
            ),
        ),
    )),
    REPLAY(GuideText("牌局回放与盲测", "Replays"), listOf(
        FeatureLesson(
            GuideText("牌局记录、筛选与收藏", "Hand history, filters and favourites"),
            GuideText(
                "可按牌号、位置、日期或底牌搜索，也可筛位置与收藏。打开「显示结果」后可筛输赢、未行动或自动弃牌，并在有记录时筛模型偏离。点记录进回放，点星标收藏。「更多」可导出牌谱或清理未收藏记录。",
                "Search by hand number, position, date or hole cards. Filter by position or favourites. Show results to filter by net result, no action or exit auto-fold, and model deviations when available. Open a row to replay it or use the star to keep it. More contains hand export and clearing of unstarred hands.",
            ),
        ),
        FeatureLesson(
            GuideText("牌局回放、摊牌权益与本步分析", "Hand replay, showdown equity and step analysis"),
            GuideText(
                "牌桌下方可跳到开头、结尾或逐步前后移动，也可点行动记录跳转。蓝色百分比是当时牌面下的摊牌权益，平分也按份额计入。切到「本步分析」可查看行动前的位置、剩余筹码、有效后手、跟注价格和实际下注尺度。自己的决策还会显示可用的策略频率。",
                "Use the playback controls under the table or tap an action in the log to jump to it. Blue percentages show showdown equity at that step, including shares of split pots. Step analysis shows the position, chips behind, effective stacks, call price and recorded bet sizing before each move. Your decisions also show available strategy frequencies.",
            ),
        ),
        FeatureLesson(
            GuideText("双轨分析与模型偏离", "Policy comparison and model deviations"),
            GuideText(
                "双轨把「基准参考」与「针对性调整」并排，频率条表示模型给各行动分配的比例。模型偏离标记用于筛选值得复习的决策。有可比较的行动 EV 时会显示相对模型首选少多少 BB。如显示「证据不足」或「场景未支持」，模型会保留基准。策略来源详见设置。",
                "Policy comparison shows baseline and opponent adjustments side by side. The bars show the model's action frequencies. Deviation flags help you find spots to revisit. Where model EV is available, the gap is shown in BB versus the model's best action. Thin evidence or an unsupported spot retains the baseline. See Strategy sources in Settings.",
            ),
        ),
        FeatureLesson(
            GuideText("盲测复盘", "Blind replay"),
            GuideText(
                "牌局记录里出现「盲测」才表示本手有可作答的决策。先凭当时可见的底牌、公共牌和行动作答，随后看历史行动和模型偏好。自动弃牌本身不会成为题目。该入口可在设置中独立开关。",
                "If a history row offers Blind quiz, that hand has playable decisions. Answer using the hole cards, board and actions available at the time, then reveal the recorded play and model preference. A synthetic exit fold isn't a quiz decision. Blind replay has its own Settings toggle.",
            ),
        ),
    )),
    TRAINING(GuideText("专项训练", "Training"), listOf(
        FeatureLesson(
            GuideText("生成河牌决策题", "Generate river decision drills"),
            GuideText(
                "从首页进「专项训练 → 决策答题」。没有题时，按提示开启决策记录，把抽水设为 0%，开一局单挑筹码局并打到河牌。有利位置或面对下注时更容易形成可估算的决策。结束该手后回到训练。",
                "Open Training → Decision quiz from the lobby. If there are no questions, follow the prompt to enable decision recording, set rake to zero and play a heads-up cash hand through the river. Acting in position or facing a river bet is more likely to produce a supported decision. Return after the hand ends.",
            ),
        ),
        FeatureLesson(
            GuideText("选题、解析与进度", "Question selection, answers and progress"),
            GuideText(
                "训练优先选择没练过或与模型首选一致率较低的类别。作答后可看条件模型 EV 差距，点「保存练习记录 · 下一题」才记入训练次数。进度环表示与模型首选的一致率，进度按存档保存。",
                "The trainer prioritises untried categories and categories with lower model agreement. After answering, review the conditional model EV gap. Tap Save answer · Next question to record the attempt. The ring shows agreement with the model's preferred action. Progress is per save.",
            ),
        ),
        FeatureLesson(
            GuideText("弱点分类报告与错题复习", "Weakness report and spaced review"),
            GuideText(
                "复盘的「训练工具」里可按位置、有效筹码、赔率或下注尺度查看模型偏离。专项训练的「错题复习」会从已保存的决策里挑题，按到期时间安排重做。作答后记下结果，系统更新下次复习日期。",
                "Replay → Study tools groups flags by position, effective stack, odds or bet size. Training → Spaced review selects from saved decisions and schedules them for later practice. Save your answer to update the due date.",
            ),
        ),
        FeatureLesson(
            GuideText("翻前范围矩阵与河牌情景对比", "Preflop range grid and river what-if"),
            GuideText(
                "两项都在复盘的「训练工具」。13×13 矩阵按 169 种起手牌类别显示翻前行动倾向。「情景对比」可改下注尺寸、对手弃牌率、范围强弱和有效筹码，比较同一历史决策在不同假设下的 EV。适用于模型支持的无抽水单挑筹码局河牌。",
                "Both are under Study tools in replay. The 13×13 grid shows preflop action tendencies for 169 starting-hand classes. River what-if compares model EV under different bet sizes, fold rates, range strength and effective stacks. It covers supported river spots from unraked heads-up cash games.",
            ),
        ),
        FeatureLesson(
            GuideText("离线求解器参考", "Offline solver references"),
            GuideText(
                "在双轨分析或河牌题解析中可导出支持的河牌场景。用外部工具求解后，在「专项训练 → 求解器参考」导入或粘贴 RR-REFERENCE-1 结果。文件要带来源、作者、许可和场景说明。导入内容按原决策条件匹配，已有参考可以复制或替换。",
                "Export a supported river spot from policy comparison or a river drill explanation. Use an external solver and import or paste the RR-REFERENCE-1 result in Training → Solver reference. Include the source, author, licence and context. The app matches the original decision conditions. You can copy or replace the saved reference.",
            ),
        ),
    )),
    SETTINGS(GuideText("统计、存档与设置", "Stats and settings"), listOf(
        FeatureLesson(
            GuideText("近期统计与生涯成绩", "Recent stats and career totals"),
            GuideText(
                "统计可分筹码局和锦标赛，显示累计输赢趋势及位置输赢。左侧比率按指标计算有效样本，少于 50 时显示「—」，BB/100 要有 100 手。首页的锦标赛冠军数和成就按存档累计。点指标可看分母和解释。生涯成绩按当前存档累计。",
                "Filter recent stats by cash or tournament and see cumulative results and results by position. Recent rates show a dash below 50 eligible observations. BB/100 requires 100 hands. Lobby wins and achievements are tracked per save. Tap a metric for its definition and denominator. Career totals belong to the current save.",
            ),
        ),
        FeatureLesson(
            GuideText("外观、可读性和反馈", "Appearance, readability and feedback"),
            GuideText(
                "牌桌、牌面和牌背可分别选择。经典、赛博朋克、美式西部和塔罗也可作为整套切换。设置可调字号和高对比度，也可选择音效、行动震动、对手速度及是否用 BB 显示筹码。牌桌的 SPR 标签可点开解释。",
                "Mix table, face and back styles, or choose a full Classic, Cyberpunk, Western or Tarot preset. Settings include text size, high contrast, sound, haptics, opponent speed and chip units. Tap the live SPR label for its definition.",
            ),
        ),
        FeatureLesson(
            GuideText("存档备份与恢复", "Save backup and restore"),
            GuideText(
                "在设置里导出当前存档的 ZIP，包含牌谱、复盘分析、成就、训练进度和导入的参考策略。恢复需有空槽位，会创建并切换到新存档。牌局记录里的文本导出用于查看牌谱。重置练习进度会清除当前存档的答题统计和复习计划。",
                "Export the current save as a ZIP from Settings, including hands, analysis, achievements, training progress and imported references. Restore needs a free slot and switches to the restored copy. History's text export is for reading the hands. Reset practice clears the current save's quiz totals and review schedule.",
            ),
        ),
        FeatureLesson(
            GuideText("训练开关、更新与隐私", "Training toggles, updates and privacy"),
            GuideText(
                "设置里的各项复盘和训练开关可独立调整。「关于与隐私」包含本地数据说明、版本、GitHub 和 MIT 协议。「更新日志」记录版本变化，术语表可从设置或牌桌的问号进入。",
                "The replay and training switches in Settings operate independently. About & privacy includes local storage details, version, GitHub and the MIT licence. What's new lists changes. Open the glossary from Settings or the question mark at the table.",
            ),
        ),
    )),
}

@Composable
internal fun GuideFeatures(zh: Boolean) {
    var topic by rememberSaveable { mutableStateOf(FeatureTopic.PLAY) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        for (choice in FeatureTopic.entries) {
            SettingsChip(choice.title.display(zh), selected = choice == topic) { topic = choice }
        }
    }
    for (lesson in topic.lessons) {
        GuideCard(lesson.title.display(zh)) { GuideParagraph(lesson.body.display(zh)) }
    }
}

@Composable
internal fun GuideCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.PanelHi, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Palette.Gold, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun GuideParagraph(text: String) {
    Text(text, color = Palette.Text, fontSize = 13.sp, lineHeight = 19.sp)
}
