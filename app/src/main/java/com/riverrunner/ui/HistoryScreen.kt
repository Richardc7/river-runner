package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.Stats
import com.riverrunner.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NetFilter { ALL, WON, LOST, EVEN }
private data class HistoryEntry(val hand: HandRecord, val date: String, val title: String, val position: String) {
    fun matches(query: String): Boolean = query.isEmpty() ||
        listOf(date, title, position, hand.hero?.let { Cards.format(it.hole) }.orEmpty())
            .any { it.contains(query, ignoreCase = true) }
}

@Composable
fun HistoryScreen(vm: AppViewModel, onClose: () -> Unit = {}) {
    val st = LocalStrings.current
    fun t(zh: String, en: String) = if (st.isZh) zh else en
    val back = {
        onClose()
        vm.screen = vm.historyReturnScreen
    }
    BackHandler(onBack = back)
    val store = vm.history
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    var records by remember(store) { mutableStateOf<List<HandRecord>?>(null) }
    var showResults by rememberSaveable(vm.blindReviewEnabled) { mutableStateOf(!vm.blindReviewEnabled) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var netFilter by rememberSaveable { mutableStateOf(NetFilter.ALL) }
    var deviationOnly by rememberSaveable { mutableStateOf(false) }
    var deviations by remember { mutableStateOf<Set<Long>?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var fileStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store) { records = withContext(Dispatchers.IO) { store.all().asReversed() } }
    LaunchedEffect(store, records, showResults) {
        val hands = records
        deviations = if (showResults && hands != null) vm.handsWithModelDeviations(hands) else null
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val saved = try {
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(uri) ?: throw IOException("No output stream")
                    output.bufferedWriter().use { writer ->
                        for (hand in store.all()) {
                            writer.write(hand.encode())
                            writer.newLine()
                        }
                    }
                }
                true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
            fileStatus = if (saved) t("牌谱已导出", "Hand histories exported")
                else t("无法导出牌谱", "Could not export hand histories")
        }
    }

    val entries = remember(records, st) {
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        records.orEmpty().map { hand ->
            val mode = if (hand.mode == GameMode.CASH) st.cash else st.tournament
            HistoryEntry(hand, formatter.format(Date(hand.time)), "$mode #${hand.handNumber}", Stats.positionOf(hand))
        }
    }
    val positions = remember(entries) { entries.map { it.position }.distinct().filter { it.isNotBlank() } }
    val shown = remember(entries, query, favOnly, position, showResults, netFilter, deviationOnly, deviations) {
        val term = query.trim()
        entries.filter { entry ->
            val hand = entry.hand
            (!favOnly || hand.favorite) && (position.isEmpty() || position == entry.position) && entry.matches(term) &&
                (!showResults || (when (netFilter) {
                    NetFilter.ALL -> true
                    NetFilter.WON -> hand.heroNet > 0
                    NetFilter.LOST -> hand.heroNet < 0
                    NetFilter.EVEN -> hand.heroNet == 0
                } && (!deviationOnly || deviations?.contains(hand.id) == true)))
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("← " + st.back, back)
            Text(st.history, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (vm.blindReviewEnabled) {
                Pill(if (showResults) t("隐藏结果", "Hide results") else t("显示结果", "Show results"),
                    showResults, {
                        showResults = !showResults
                        if (!showResults) {
                            netFilter = NetFilter.ALL
                            deviationOnly = false
                        }
                    })
            }
            Pill("★ " + st.favorites, favOnly, { favOnly = !favOnly })
            Box {
                SmallButton(t("更多", "More"), { showMore = true })
                DropdownMenu(showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(
                        text = { Text(t("导出完整牌谱（含结果）", "Export all hands (with results)")) },
                        enabled = entries.isNotEmpty(),
                        onClick = {
                            showMore = false
                            export.launch("river-runner-hands.txt")
                        },
                    )
                    DropdownMenuItem(text = { Text(t("清理未收藏牌局", "Clear unstarred hands")) },
                        enabled = entries.any { !it.hand.favorite },
                        onClick = { showMore = false; confirmClear = true })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it.take(100) },
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text(t("搜索牌号、位置、日期或底牌", "Search hand, position, date or cards"), fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(onClick = { query = ""; focus.clearFocus() }) {
                            Text(t("清除", "Clear"))
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodySmall)
            Row(Modifier.weight(1.3f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryFilter(listOf("" to t("所有位置", "All positions")) + positions.map { it to it }, position) { position = it }
                if (showResults) {
                    HistoryFilter(listOf(
                        NetFilter.ALL to t("所有结果", "All results"),
                        NetFilter.WON to t("盈利", "Won"),
                        NetFilter.LOST to t("亏损", "Lost"),
                        NetFilter.EVEN to t("持平", "Even"),
                    ), netFilter) { netFilter = it }
                    if (deviations?.isNotEmpty() == true) {
                        Pill(t("模型偏离", "Model deviations"), deviationOnly, { deviationOnly = !deviationOnly })
                    }
                }
            }
        }
        Text(fileStatus ?: when {
            entries.isEmpty() -> st.keepHandsNote
            else -> t("显示 ${shown.size} / ${entries.size} 手", "Showing ${shown.size} of ${entries.size} hands")
        }, color = Palette.TextDim, fontSize = 11.sp)
        if (records == null || shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val message = when {
                    records == null -> t("正在读取牌局…", "Loading hands…")
                    entries.isEmpty() -> st.noHandsHint
                    else -> t("没有符合条件的牌局", "No hands match the filters")
                }
                Text(message, color = Palette.TextDim, fontSize = 14.sp)
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.hand.id }) { entry ->
                    HistoryRow(entry, showResults, deviations?.contains(entry.hand.id) == true,
                        onReplay = { vm.openReplay(entry.hand) },
                        onBlind = if (vm.blindReviewEnabled) { { vm.openReplay(entry.hand, blind = true) } } else null,
                        onFavorite = {
                            val updated = vm.toggleFavorite(entry.hand.id)
                            if (updated != null) records = records?.map { if (it.id == updated.id) updated else it }
                        })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        try {
                            records = withContext(Dispatchers.IO) { store.clearNonFavorites(); store.all().asReversed() }
                            fileStatus = null
                        } catch (_: IOException) {
                            fileStatus = t("无法清理牌局", "Could not clear hands")
                        }
                    }
                }) { Text(st.clearAction, color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(st.keep) } },
            text = { Text(st.clearHistoryConfirm) },
        )
    }
}

@Composable
private fun <T> HistoryFilter(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected } ?: options.first()
    Box {
        Pill(current.second + " ▾", current != options.first(), { expanded = true })
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            for ((value, label) in options) DropdownMenuItem(
                text = { Text(label) }, onClick = { expanded = false; onSelect(value) },
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, showResults: Boolean, deviated: Boolean,
    onReplay: () -> Unit, onBlind: (() -> Unit)?, onFavorite: () -> Unit) {
    val st = LocalStrings.current
    fun t(zh: String, en: String) = if (st.isZh) zh else en
    val hand = entry.hand
    val status = listOfNotNull(
        if (showResults && hand.autoFoldedOnExit) t("离桌自动弃牌", "Auto-folded on exit")
        else if (showResults && hand.actions.none { it.seat == hand.heroSeat && !it.type.isPost }) t("未行动", "Did not act")
        else null,
        t("已复盘", "Reviewed").takeIf { hand.reviewed },
        t("有模型偏离", "Model deviation").takeIf { showResults && deviated },
    )
    val net = hand.heroNet.toDouble() / hand.bigBlind
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Panel)
            .clickable(role = Role.Button, onClickLabel = t("回放", "Replay"), onClick = onReplay)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.width(90.dp)) {
            Text(entry.date + " · " + entry.position, color = Palette.TextDim, fontSize = 10.sp)
            Text(entry.title, color = Palette.Text, fontSize = 13.sp)
        }
        hand.hero?.let { hero ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                hero.hole.forEach { PlayingCard(it, 24.dp) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (showResults && hand.board.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    hand.board.forEach { PlayingCard(it, 19.dp) }
                }
            }
            if (status.isNotEmpty()) {
                Text(status.joinToString(" · "), color = Palette.TextDim, fontSize = 11.sp)
            }
        }
        if (showResults) {
            Text((if (net > 0) "+" else "") + "%.1f BB".format(net),
                color = if (net > 0) Palette.Green else if (net < 0) Palette.Red else Palette.TextDim,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (onBlind != null) SmallButton(t("盲测", "Blind quiz"), onBlind)
        Text(if (hand.favorite) "★" else "☆", color = Palette.Gold, fontSize = 22.sp,
            modifier = Modifier.clickable(role = Role.Button,
                onClickLabel = if (hand.favorite) t("取消收藏", "Remove favourite") else t("收藏", "Favourite"),
                onClick = onFavorite).padding(6.dp))
        Text("›", color = Palette.TextDim, fontSize = 24.sp, lineHeight = 24.sp)
    }
}
