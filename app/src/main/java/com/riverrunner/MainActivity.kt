package com.riverrunner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
            val pageState = rememberSaveableStateHolder()
            RiverTheme(vm.strings, vm.deckStyle) {
                Box(Modifier.fillMaxSize().background(Palette.Bg).windowInsetsPadding(WindowInsets.displayCutout)) {
                    when (val screen = vm.screen) {
                        Screen.Home -> HomeScreen(vm)
                        Screen.Table -> TableScreen(vm)
                        is Screen.Result -> ResultScreen(vm, screen.summary)
                        Screen.History -> {
                            val key = "hands"
                            pageState.SaveableStateProvider(key) {
                                HistoryScreen(vm, onClose = { pageState.removeState(key) })
                            }
                        }
                        is Screen.Replay -> EditionReplayScreen(vm, screen.record, screen.blind)
                        Screen.Stats -> StatsScreen(vm)
                        Screen.Settings -> SettingsScreen(vm)
                        Screen.EditionSettings -> EditionScreen(
                            vm.editionConfig, vm.prefs.chinese, vm::saveEdition, vm::closeEditionSettings
                        )
                        Screen.Training -> TrainingScreen(vm, vm::closeTraining)
                        Screen.Guide -> GuideScreen(vm)
                        Screen.Saves -> SavesScreen(vm)
                        Screen.Achievements -> AchievementsScreen(vm)
                        Screen.Setup -> SetupScreen(vm)
                        Screen.TrainingSetup -> SetupScreen(vm, forTraining = true)
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
