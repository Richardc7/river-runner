package com.riverrunner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riverrunner.i18n.ZH
import com.riverrunner.ui.GuideContent
import com.riverrunner.ui.RiverTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w914dp-h411dp-land")
class BeginnerGuideTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quickOddsSeparatesOneCardFromTwoAndGlossaryAcceptsAliases() {
        compose.setContent { RiverTheme(ZH) { GuideContent(onClose = {}) } }

        compose.onNodeWithText("扑克速算").performClick()
        compose.onNodeWithText("只看下一张：出张 × 2%").assertIsDisplayed()
        compose.onNodeWithText("翻牌看到河牌：出张 × 4%").assertIsDisplayed()

        compose.onNodeWithText("术语表").performClick()
        compose.onNodeWithText("搜索中英文术语").performTextInput("4.2")
        compose.onNodeWithText("4/2 法则").assertIsDisplayed()
        compose.onNodeWithText("BB / 大盲").assertDoesNotExist()
        compose.onNodeWithText("搜索中英文术语").performImeAction()

        compose.onNodeWithText("功能指南").performClick()
        compose.onNodeWithText("专项训练").performClick()
        compose.onNodeWithText("生成河牌决策题").assertIsDisplayed()
    }
}
