package com.riverrunner

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riverrunner.engine.*
import com.riverrunner.i18n.ZH
import com.riverrunner.ui.RiverTheme
import com.riverrunner.ui.SetupContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w780dp-h360dp-land-xxhdpi")
class NinePlayerSetupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nineSeatOptionIsClickableAndPassedIntoNewSession() {
        var started: SessionConfig? = null
        compose.setContent {
            RiverTheme(ZH) {
                SetupContent(SessionConfig(GameMode.CASH, 8, 100, TableType.MIXED), {}, { started = it })
            }
        }
        compose.onNodeWithText("9", useUnmergedTree = true).performClick()
        compose.onNodeWithText(ZH.start, useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(9, started?.tableSize)
            assertEquals(9, GameSession(checkNotNull(started)).players.size)
        }
    }
}
