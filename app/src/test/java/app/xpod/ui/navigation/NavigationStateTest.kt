package app.xpod.ui.navigation

import androidx.lifecycle.SavedStateHandle
import app.xpod.data.AppTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationStateTest {
  @Test
  fun selectingPodcastSubViewClearsPodcastAndEpisodeDetails() {
    val state =
        MainNavigationState(
            podcast = PodcastNavigationState(PodcastSubView.Subscriptions, "podcast"),
            selectedEpisodeId = "episode",
        )

    val updated =
        reduceNavigation(state, NavigationAction.SelectPodcastSubView(PodcastSubView.Library))

    assertEquals(PodcastSubView.Library, updated.podcast.subView)
    assertNull(updated.podcast.selectedPodcastId)
    assertNull(updated.selectedEpisodeId)
  }

  @Test
  fun openingPodcastReturnsToSubscriptionsAndClosesPlayer() {
    val state = MainNavigationState(destination = AppRoute.Music, fullPlayer = true)

    val updated = reduceNavigation(state, NavigationAction.OpenPodcast("podcast"))

    assertEquals(AppRoute.Podcasts, updated.destination)
    assertEquals(PodcastSubView.Subscriptions, updated.podcast.subView)
    assertEquals("podcast", updated.podcast.selectedPodcastId)
    assertFalse(updated.fullPlayer)
  }

  @Test
  fun backNavigationUnwindsDetailsBeforePodcastSelection() {
    val state =
        MainNavigationState(
            podcast = PodcastNavigationState(selectedPodcastId = "podcast"),
            selectedEpisodeId = "episode",
        )

    val afterEpisode = reduceNavigation(state, NavigationAction.NavigateBack)
    val afterPodcast = reduceNavigation(afterEpisode, NavigationAction.NavigateBack)

    assertNull(afterEpisode.selectedEpisodeId)
    assertEquals("podcast", afterEpisode.podcast.selectedPodcastId)
    assertNull(afterPodcast.podcast.selectedPodcastId)
  }

  @Test
  fun allTabsMapToTheirRuntimeRoutes() {
    assertEquals(
        listOf(
            AppRoute.Podcasts,
            AppRoute.Reader,
            AppRoute.Music,
            AppRoute.Video,
            AppRoute.Memos,
            AppRoute.Notes,
            AppRoute.Books,
            AppRoute.Settings,
        ),
        listOf(
                AppTab.Podcasts,
                AppTab.Reader,
                AppTab.Music,
                AppTab.Video,
                AppTab.Memos,
                AppTab.Notes,
                AppTab.Books,
                AppTab.Settings,
            )
            .toAppRoutes(),
    )
    assertEquals(AppRoute.Settings, resolveRoute(AppRoute.Podcasts, emptyList()))
    assertEquals(AppRoute.Podcasts, appRouteFromKey(" PODCASTS "))
    assertEquals(AppRoute.Podcasts, appRouteFromKey("library"))
    assertEquals(PodcastSubView.Library, podcastSubViewFromKey("LIBRARY"))
    assertEquals(PodcastSubView.Subscriptions, podcastSubViewFromKey(null))
  }

  @Test
  fun unavailableRoutePrefersPodcastsWhenItIsVisible() {
    assertEquals(
        AppRoute.Podcasts,
        resolveRoute(
            AppRoute.Reader,
            listOf(AppRoute.Memos, AppRoute.Podcasts, AppRoute.Settings),
        ),
    )
    assertEquals(
        AppRoute.Memos,
        resolveRoute(AppRoute.Reader, listOf(AppRoute.Memos, AppRoute.Settings)),
    )
  }

  @Test
  fun selectingDestinationClearsDetailsAndPlayer() {
    val state =
        MainNavigationState(
            destination = AppRoute.Podcasts,
            podcast = PodcastNavigationState(selectedPodcastId = "podcast"),
            selectedEpisodeId = "episode",
            selectedArticleId = "article",
            selectedBookId = "book",
            fullPlayer = true,
        )

    val updated = reduceNavigation(state, NavigationAction.SelectDestination(AppRoute.Settings))

    assertEquals(AppRoute.Settings, updated.destination)
    assertNull(updated.podcast.selectedPodcastId)
    assertNull(updated.selectedEpisodeId)
    assertNull(updated.selectedArticleId)
    assertNull(updated.selectedBookId)
    assertFalse(updated.fullPlayer)
  }

  @Test
  fun clearingPodcastSelectionKeepsTheCurrentDestinationAndSubView() {
    val state =
        MainNavigationState(
            destination = AppRoute.Podcasts,
            podcast = PodcastNavigationState(PodcastSubView.Library, "podcast"),
        )

    val updated = reduceNavigation(state, NavigationAction.ClearPodcastSelection)

    assertEquals(AppRoute.Podcasts, updated.destination)
    assertEquals(PodcastSubView.Library, updated.podcast.subView)
    assertNull(updated.podcast.selectedPodcastId)
  }

  @Test
  fun backIsNoOpWhenThereIsNoDeeperNavigation() {
    val state = MainNavigationState(destination = AppRoute.Settings)

    assertEquals(state, reduceNavigation(state, NavigationAction.NavigateBack))
  }

  @Test
  fun clearingEpisodeSelectionRemovesADeletedEpisodeFromNavigation() {
    val state = MainNavigationState(selectedEpisodeId = "episode")

    val updated = reduceNavigation(state, NavigationAction.ClearEpisodeSelection)

    assertNull(updated.selectedEpisodeId)
  }

  @Test
  fun openingNoteUsesNotesRouteAndBackReturnsToList() {
    val opened = reduceNavigation(MainNavigationState(), NavigationAction.OpenNote(42L))

    assertEquals(AppRoute.Notes, opened.destination)
    assertEquals(42L, opened.selectedNoteId)

    val returned = reduceNavigation(opened, NavigationAction.NavigateBack)
    assertNull(returned.selectedNoteId)
    assertEquals(AppRoute.Notes, returned.destination)
  }

  @Test
  fun clearingMissingNoteSelectionKeepsNotesListRoute() {
    val opened = reduceNavigation(MainNavigationState(), NavigationAction.OpenNote(42L))

    val cleared = reduceNavigation(opened, NavigationAction.ClearNoteSelection)

    assertNull(cleared.selectedNoteId)
    assertEquals(AppRoute.Notes, cleared.destination)
  }

  @Test
  fun savedStateRoundTripRestoresOnlyRouteAndPodcastSubView() {
    val savedStateHandle = SavedStateHandle()
    persistNavigationState(
        savedStateHandle,
        MainNavigationState(
            destination = AppRoute.Reader,
            podcast = PodcastNavigationState(PodcastSubView.Library),
            selectedEpisodeId = "episode",
            fullPlayer = true,
        ),
    )

    val restored = restoreNavigationState(savedStateHandle)

    assertEquals(AppRoute.Reader, restored.destination)
    assertEquals(PodcastSubView.Library, restored.podcast.subView)
    assertNull(restored.podcast.selectedPodcastId)
    assertNull(restored.selectedEpisodeId)
    assertFalse(restored.fullPlayer)
  }

  @Test
  fun invalidSavedRouteFallsBackToPodcasts() {
    val savedStateHandle =
        SavedStateHandle(
            mapOf(
                NAVIGATION_ROUTE_KEY to "library",
                NAVIGATION_SUBVIEW_KEY to "unknown",
            )
        )

    val restored = restoreNavigationState(savedStateHandle)

    assertEquals(AppRoute.Podcasts, restored.destination)
    assertEquals(PodcastSubView.Subscriptions, restored.podcast.subView)
  }
}
