package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.i18n.LocalStrings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SettingsSection { AI_COACH, GENERAL, APPEARANCE, DATA_ABOUT }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var practice by remember { mutableStateOf(vm.prefs.practiceDefault) }
    var speed by remember { mutableIntStateOf(vm.prefs.speed) }
    var haptics by remember { mutableStateOf(vm.prefs.actionHaptics) }
    var namesFollowStyle by remember { mutableStateOf(vm.prefs.opponentNamesFollowStyle) }
    var showAbout by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var resetStatus by remember { mutableStateOf<String?>(null) }
    var activeSection by remember { mutableStateOf(SettingsSection.AI_COACH) }
    val scrollState = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<SettingsSection, Int>() }
    val scope = rememberCoroutineScope()

    fun sectionLabel(s: SettingsSection): String = when (s) {
        SettingsSection.GENERAL -> if (st.isZh) "常规与显示" else "General & Display"
        SettingsSection.APPEARANCE -> if (st.isZh) "外观与反馈" else "Appearance & Audio"
        SettingsSection.AI_COACH -> if (st.isZh) "AI 与复盘训练" else "AI & Coaching"
        SettingsSection.DATA_ABOUT -> if (st.isZh) "备份与关于" else "Backup & About"
    }

    Row(
        Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Left modern sidebar navigation rail
        Column(
            Modifier.width(176.dp).fillMaxHeight()
                .studioCard(radius = 16.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("←", { vm.screen = Screen.Home })
                Text(st.settings, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            for (section in SettingsSection.entries) {
                val selected = activeSection == section
                val shape = RoundedCornerShape(10.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(
                            if (selected) Brush.horizontalGradient(listOf(Palette.Gold.copy(alpha = 0.22f), Palette.PanelHi))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .then(if (selected) Modifier.border(1.dp, Palette.Gold.copy(alpha = 0.45f), shape) else Modifier)
                        .clickable(role = Role.Tab) {
                            activeSection = section
                            sectionOffsets[section]?.let { y ->
                                scope.launch { scrollState.animateScrollTo(y) }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (selected) Palette.Gold else Color.Transparent)
                    )
                    Text(
                        sectionLabel(section),
                        color = if (selected) Palette.Text else Palette.TextDim,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            SmallButton(st.whatsNew, { vm.showChangelog() }, modifier = Modifier.fillMaxWidth())
        }

        // Right grouped frosted-glass cards canvas (all sections stay mounted for smooth jump & test compatibility)
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section 1: AI & Coaching (placed first so custom AI entry is immediately reachable above the fold)
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { sectionOffsets[SettingsSection.AI_COACH] = it.positionInParent().y.roundToInt() }
                    .studioCard(accent = Palette.Gold, radius = 16.dp, highlighted = true)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(if (st.isZh) "AI 与复盘训练" else "AI & COACHING")
                Text(editionSummary(vm.editionConfig, st.isZh), color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                ActionButton(
                    if (st.isZh) "⚙ 自定义 AI、对手与私人局" else "⚙ Customize AI, opponents & cash",
                    Palette.Gold,
                    textColor = Palette.Ink,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) { vm.openEditionSettings() }
                Text(
                    if (st.isZh) "说明：默认是原版 AI；动态剥削需主动开启，且证据不足时会保留基准策略。"
                    else "Default AI is Legacy. Enable Adaptive explicitly; sparse evidence retains the baseline.",
                    color = Palette.TextDim, fontSize = 11.sp,
                )
                HorizontalDivider(color = Palette.Line.copy(alpha = 0.45f))
                ToggleRow(if (st.isZh) "1. 盲测复盘" else "1. Blind replay", vm.blindReviewEnabled) {
                    vm.setStudyToggle("blind", it)
                }
                ToggleRow(if (st.isZh) "2. 弱点分类报告" else "2. Weakness report", vm.weaknessReportEnabled) {
                    vm.setStudyToggle("weak", it)
                }
                ToggleRow(if (st.isZh) "3. 错题与间隔复习" else "3. Spaced error drills", vm.spacedDrillsEnabled) {
                    vm.setStudyToggle("spaced", it)
                }
                ToggleRow(if (st.isZh) "4. 翻前范围矩阵教练" else "4. Preflop range coach", vm.preflopCoachEnabled) {
                    vm.setStudyToggle("preflop", it)
                }
                ToggleRow(if (st.isZh) "5. 河牌 What-if 情景比较" else "5. River what-if scenarios", vm.riverWhatIfEnabled) {
                    vm.setStudyToggle("whatif", it)
                }
                ToggleRow(if (st.isZh) "新牌局默认显示策略参考" else "Default live strategy reference in new games", vm.realtimeGtoEnabled) {
                    vm.setStudyToggle("live", it)
                }
                Text(
                    if (st.isZh) "启发式模型和剥削建议是训练参考，并非 GTO 求解结果。导入的求解器策略仅匹配对应场景。"
                    else "Heuristic and exploit suggestions are study references, not GTO solutions. Imported solver strategies match only their own scenarios.",
                    color = Palette.TextDim, fontSize = 11.sp, lineHeight = 16.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        if (st.isZh) "专项训练中心" else "Training centre",
                        Palette.PanelHi, modifier = Modifier.weight(1f).height(40.dp),
                    ) { vm.openTraining() }
                    ActionButton(
                        if (st.isZh) "打开错题间隔训练" else "Open spaced training",
                        Palette.PanelHi, modifier = Modifier.weight(1f).height(40.dp),
                    ) { vm.openTraining() }
                }
            }

            // Section 2: General & Display
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { sectionOffsets[SettingsSection.GENERAL] = it.positionInParent().y.roundToInt() }
                    .studioCard(radius = 16.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(if (st.isZh) "常规与显示" else "GENERAL & DISPLAY")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(st.language, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        StudioSegmentedBar(
                            options = listOf(true to "中文", false to "English"),
                            selected = st.isZh,
                            onSelect = { vm.setChinese(it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (st.isZh) "字号" else "Text size", color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        StudioSegmentedBar(
                            options = listOf(100 to "100%", 110 to "110%", 120 to "120%"),
                            selected = vm.textSizePercent,
                            onSelect = { vm.setTextSize(it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(st.speed, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    StudioSegmentedBar(
                        options = listOf(0 to st.slow, 1 to st.normal, 2 to st.fast),
                        selected = speed,
                        onSelect = { speed = it; vm.prefs.speed = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(color = Palette.Line.copy(alpha = 0.45f))
                ToggleRow(if (st.isZh) "高对比度" else "High contrast", vm.highContrast, vm::updateHighContrast)
                ToggleRow(st.practiceDefault, practice) { practice = it; vm.prefs.practiceDefault = it }
                ToggleRow(st.showInBB, vm.showInBB) { vm.setShowBB(it) }
            }

            // Section 2: Appearance & Audio
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { sectionOffsets[SettingsSection.APPEARANCE] = it.positionInParent().y.roundToInt() }
                    .studioCard(radius = 16.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle(if (st.isZh) "外观与反馈" else "APPEARANCE & AUDIO")
                ConfigurationChoices(
                    if (st.isZh) "全局主题（同步切换牌桌与牌面牌背）" else "Global theme (syncs table & cards)",
                    AppTheme.entries,
                    vm.appTheme,
                    { it.title(st.isZh) },
                ) { vm.updateAppTheme(it, syncAppearance = true) }
                ActionButton(
                    if (st.isZh) "牌桌与扑克牌外观  ›" else "Table and card appearance  ›",
                    Palette.PanelHi,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) { vm.screen = Screen.Appearance }
                SettingsSwitch(
                    if (st.isZh) "对手姓名随打法风格" else "Match opponent names to play style",
                    if (st.isZh) "关闭时随机取名。更改从下一局生效。" else "Off uses random names. Changes apply to the next game.",
                    namesFollowStyle,
                ) {
                    namesFollowStyle = it
                    vm.prefs.opponentNamesFollowStyle = it
                }
                ToggleRow(st.sound, vm.soundOn) { vm.setSound(it) }
                ToggleRow(if (st.isZh) "行动时震动反馈" else "Haptic feedback on actions", haptics) {
                    haptics = it
                    vm.prefs.actionHaptics = it
                }
            }



            // Section 4: Backup & About
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { sectionOffsets[SettingsSection.DATA_ABOUT] = it.positionInParent().y.roundToInt() }
                    .studioCard(radius = 16.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle(if (st.isZh) "备份、牌谱与关于" else "BACKUP, DATA & ABOUT")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        if (st.isZh) "存档备份与恢复" else "Save backup and restore",
                        Palette.PanelHi, modifier = Modifier.weight(1f).height(42.dp),
                    ) { showBackup = true }
                    ActionButton(
                        if (st.isZh) "管理牌谱与导出" else "Manage and export hands",
                        Palette.PanelHi, modifier = Modifier.weight(1f).height(42.dp),
                    ) { vm.openHistory() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallButton(if (st.isZh) "扑克术语表" else "Poker glossary", { vm.openGuide(GuideTab.GLOSSARY) })
                    SmallButton(if (st.isZh) "关于与隐私" else "About & privacy", { showAbout = true })
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = !resetting, onClick = { confirmReset = true }) {
                        Text(if (st.isZh) "重置练习进度" else "Reset practice progress", color = Palette.Red)
                    }
                }
                resetStatus?.let { Text(it, color = Palette.TextDim, fontSize = 12.sp) }
            }
        }
    }

    if (showBackup) SaveBackupDialog(vm) { showBackup = false }
    if (showAbout) AboutDialog { showAbout = false }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(if (st.isZh) "重置练习进度？" else "Reset practice progress?") },
            text = {
                Text(
                    if (st.isZh) "将清除当前存档的答题统计和错题复习计划。"
                    else "This clears quiz statistics and the spaced-review schedule for the active save."
                )
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(st.keep) } },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    resetting = true
                    scope.launch {
                        resetStatus = try {
                            vm.resetPracticeProgress()
                            if (st.isZh) "练习进度已重置" else "Practice progress reset"
                        } catch (_: java.io.IOException) {
                            if (st.isZh) "重置未完成" else "Could not finish resetting progress"
                        } catch (_: IllegalStateException) {
                            if (st.isZh) "重置未完成" else "Could not finish resetting progress"
                        } finally {
                            resetting = false
                        }
                    }
                }) { Text(if (st.isZh) "重置" else "Reset", color = Palette.Red) }
            },
        )
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    val st = LocalStrings.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val repository = "https://github.com/Richardc7/river-runner"
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var status by remember { mutableStateOf<String?>(null) }
    fun open(link: String) {
        status = runCatching { uriHandler.openUri(link) }.exceptionOrNull()?.let {
            if (st.isZh) "设备无法打开链接" else "This device could not open the link"
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (st.isZh) "关于与隐私" else "About & privacy") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("River Runner EE · " + st.currentVersion(version))
                Text(
                    if (st.isZh) "牌局、练习进度和导入的策略保存在设备上。应用本身离线运行。Android 可能按照设备备份设置备份应用数据。导出时，由你在系统文件选择器中指定保存位置。"
                    else "Hands, practice progress, and imported strategies are stored on the device. The app runs offline. Android may back up app data according to your device settings. Exports go to the location you select in the system file picker."
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextButton(onClick = { open(repository) }) { Text("GitHub") }
                    TextButton(onClick = { open("$repository/blob/exploit-edition/LICENSE") }) {
                        Text(if (st.isZh) "MIT 协议" else "MIT license")
                    }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(repository))
                        status = if (st.isZh) "已复制 GitHub 链接" else "GitHub link copied"
                    }) { Text(if (st.isZh) "复制地址" else "Copy URL") }
                }
                status?.let { Text(it, color = Palette.TextDim, fontSize = 11.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(st.back) } },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.FeltCenter,
                checkedThumbColor = Palette.Gold,
                uncheckedTrackColor = Palette.PanelHi2,
            ),
        )
    }
}
