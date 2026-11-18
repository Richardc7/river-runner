package com.riverrunner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riverrunner.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val vm: AppViewModel = viewModel()
            var editionOpen by remember { mutableStateOf(false) }
            var trainingOpen by remember { mutableStateOf(false) }
            BackHandler(enabled = trainingOpen) { trainingOpen = false }
            BackHandler(enabled = editionOpen) { editionOpen = false }
            RiverTheme(vm.strings, vm.deckStyle) {
                Box(Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.displayCutout)) {
                    if (trainingOpen) {
                        TrainingScreen(vm) { trainingOpen = false }
                    } else if (editionOpen) {
                        EditionScreen(vm.editionConfig, vm.prefs.chinese, vm::saveEdition) { editionOpen = false }
                    } else Column(Modifier.fillMaxSize()) {
                        if (vm.screen == Screen.Home || vm.screen == Screen.Setup || vm.screen == Screen.Settings) {
                            TextButton(onClick = { editionOpen = true }) {
                                Text(if (vm.prefs.chinese) "Exploit Edition \u7b56\u7565\u8bbe\u7f6e" else "Exploit Edition strategy settings", color = Palette.Gold)
                            }
                        }
                        if (vm.screen == Screen.Home || vm.screen == Screen.Settings || vm.screen == Screen.History) {
                            TextButton(onClick = { trainingOpen = true }) {
                                Text(if (vm.prefs.chinese) "V0.4 专项训练" else "V0.4 Training", color = Palette.Gold)
                            }
                        }
                        if (vm.topUpAvailable && (vm.screen == Screen.Table || vm.reviewing)) {
                            TextButton(onClick = vm::topUpHero) { Text(if (vm.prefs.chinese) "\u624b\u95f4\u8865\u7801\u81f3\u8d77\u59cb\u7b79\u7801" else "Top up between hands", color = Palette.Gold) }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (val s = vm.screen) {
                                Screen.Home -> HomeScreen(vm)
                                Screen.Table -> TableScreen(vm)
                                is Screen.Result -> ResultScreen(vm, s.summary)
                                Screen.History -> HistoryScreen(vm)
                                is Screen.Replay -> EditionReplayScreen(vm, s.record)
                                Screen.Stats -> StatsScreen(vm)
                                Screen.Settings -> SettingsScreen(vm)
                                Screen.Guide -> GuideScreen(vm)
                                Screen.Saves -> SavesScreen(vm)
                                Screen.Achievements -> AchievementsScreen(vm)
                                Screen.Setup -> SetupScreen(vm)
                            }
                        }
                    }
                    UnlockToast(vm.unlockToast, Modifier.align(Alignment.TopCenter))
                    vm.whatsNew?.let { shown ->
                        WhatsNewDialog(shown, onDismiss = vm::dismissWhatsNew,
                            onShowAll = if (shown.size < vm.changelog.size) vm::showChangelog else null)
                    }
                }
            }
        }
    }
}
