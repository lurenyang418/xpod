package app.xpod.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.data.ReadingPreferences
import app.xpod.data.ThemeMode
import app.xpod.data.defaultTabOrder
import app.xpod.ui.coordination.CloudMemosUiState
import app.xpod.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun tabManagerShowsCurrentTabsAndForwardsToggle() {
    var toggled: Pair<AppTab, Boolean>? = null
    var manageTabsLabel = ""
    var tabOrderLabel = ""
    var podcastsLabel = ""
    var libraryLabel = ""

    compose.setContent {
      manageTabsLabel = stringResource(R.string.manage_tab_order)
      tabOrderLabel = stringResource(R.string.tab_order)
      podcastsLabel = stringResource(R.string.podcasts)
      libraryLabel = stringResource(R.string.library)
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
            exportNotesZip = {},
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

    compose.onNodeWithTag("settings_list").performScrollToNode(hasText(manageTabsLabel))
    compose.onNodeWithText(manageTabsLabel).performClick()
    compose.onAllNodesWithText(tabOrderLabel).assertCountEquals(2)
    compose.onNodeWithContentDescription(podcastsLabel).performClick()
    compose.runOnIdle { assertEquals(AppTab.Podcasts to false, toggled) }
    compose.onAllNodesWithText(libraryLabel).assertCountEquals(0)
  }
}
