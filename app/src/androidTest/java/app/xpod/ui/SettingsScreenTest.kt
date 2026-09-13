package app.xpod.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.data.ReadingPreferences
import app.xpod.data.ThemeMode
import app.xpod.data.defaultTabOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun tabManagerShowsOnlyTheSixCurrentTabsAndForwardsToggle() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    var toggled: Pair<AppTab, Boolean>? = null

    compose.setContent {
      MaterialTheme {
        SettingsScreen(
            theme = ThemeMode.System,
            dynamicColor = true,
            wifiOnlyDownloads = true,
            readingPreferences = ReadingPreferences(),
            cloudMemos = CloudMemosUiState(),
            setTheme = {},
            setDynamicColor = {},
            setWifiOnlyDownloads = {},
            setReadingFontSize = {},
            setReadingLineHeight = {},
            setReadingTheme = {},
            showQueue = {},
            add = { _, _ -> },
            importOpml = {},
            exportOpml = {},
            configureCloudMemos = { _, _, _ -> },
            disconnectCloudMemos = {},
            openReleases = {},
            tabOrder = defaultTabOrder,
            enabledTabs = defaultTabOrder.toSet(),
            moveTab = { _, _ -> },
            setTabEnabled = { tab, enabled -> toggled = tab to enabled },
        )
      }
    }

    repeat(4) {
      compose.onNodeWithTag("settings_list").performTouchInput { swipeUp() }
    }
    compose
        .onNodeWithText(context.getString(R.string.manage_tab_order))
        .performScrollTo()
        .performClick()
    compose.onAllNodesWithText(context.getString(R.string.tab_order)).assertCountEquals(2)
    compose.onNodeWithContentDescription(context.getString(R.string.podcasts)).performClick()
    compose.runOnIdle { assertEquals(AppTab.Podcasts to false, toggled) }
    compose.onAllNodesWithText(context.getString(R.string.library)).assertCountEquals(0)
  }
}
