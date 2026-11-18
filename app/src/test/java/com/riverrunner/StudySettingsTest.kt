package com.riverrunner

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riverrunner.i18n.ZH
import com.riverrunner.ui.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w780dp-h360dp-land-xxhdpi")
class StudySettingsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allSixControlsAreInTheNormalSettingsPage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        compose.setContent { RiverTheme(ZH) { SettingsScreen(vm) } }
        for (label in listOf(
            "1. 盲测复盘", "2. 弱点分类报告", "3. 错题与间隔复习",
            "4. 翻前范围矩阵教练", "5. 河牌 What-if 情景比较",
            "实时 GTO/参考策略显示"
        )) compose.onNodeWithText(label).assertExists()
    }
    @Test fun featureTogglesPersistIndependentlyAndLiveDefaultsOff() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = app.getSharedPreferences("river_runner", 0)
        store.edit().remove("study_realtime_gto").apply()
        val vm = AppViewModel(app)
        assertFalse(vm.realtimeGtoEnabled)
        vm.setStudyToggle("live", true)
        assertTrue(vm.prefs.realtimeGto)
        assertTrue(vm.realtimeGtoEnabled)
        vm.setStudyToggle("blind", false)
        assertFalse(vm.prefs.blindReview)
        vm.setStudyToggle("live", false)
        assertFalse(vm.prefs.realtimeGto)
    }
}
