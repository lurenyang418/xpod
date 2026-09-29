@file:Suppress("DEPRECATION")

package app.xpod.ui.video

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun systemBackFromNestedFolderOpensParentFolder() {
    val uiState =
        mutableStateOf(
            VideoUiState(
                currentFolderPath = "Movies/Action",
                selectedTreeUri = "content://provider/tree/videos",
            )
        )
    val openedPaths = mutableListOf<String>()

    compose.setContent {
      MaterialTheme {
        VideoScreen(
            state = uiState.value,
            chooseFolder = {},
            startAutomaticScan = {},
            refresh = {},
            cancelScan = {},
            setQuery = {},
            openFolder = { path ->
              openedPaths += path
              uiState.value = uiState.value.copy(currentFolderPath = path, query = "")
            },
            play = {},
            renameVideo = { _, _ -> },
            deleteVideo = {},
            deleteFolder = {},
        )
      }
    }

    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    compose.runOnIdle {
      assertEquals(listOf("Movies"), openedPaths)
      assertEquals("Movies", uiState.value.currentFolderPath)
    }
  }
}
