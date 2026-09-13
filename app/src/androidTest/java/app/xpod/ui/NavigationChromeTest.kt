package app.xpod.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.xpod.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationChromeTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun bottomNavigationRendersSixMappedRoutesWithoutLibrary() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val routes =
        listOf(
            AppRoute.Podcasts,
            AppRoute.Reader,
            AppRoute.Music,
            AppRoute.Memos,
            AppRoute.Books,
            AppRoute.Settings,
        )

    compose.setContent {
      MaterialTheme {
        HomeBottomBar(
            visible = true,
            summary = null,
            destination = AppRoute.Podcasts,
            routes = routes,
            onDestinationSelected = {},
            onToggle = {},
            onPrevious = {},
            onNext = {},
            onOpenPlayer = {},
            onShowSpeedPicker = {},
        )
      }
    }

    routes.forEach { route ->
      compose
          .onNodeWithText(context.getString(destinationLabelResource(route)))
          .assertIsDisplayed()
    }
    compose.onAllNodesWithText(context.getString(R.string.library)).assertCountEquals(0)
  }
}
