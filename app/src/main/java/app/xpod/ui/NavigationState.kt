package app.xpod.ui

import androidx.lifecycle.SavedStateHandle
import app.xpod.data.AppTab
import java.util.Locale

internal const val NAVIGATION_ROUTE_KEY = "navigation_route_v2"
internal const val NAVIGATION_SUBVIEW_KEY = "navigation_subview_v2"

internal enum class AppRoute(val key: String) {
  Podcasts("podcasts"),
  Reader("reader"),
  Music("music"),
  Memos("memos"),
  Books("books"),
  Settings("settings"),
}

internal enum class PodcastSubView {
  Subscriptions,
  Library,
}

internal data class PodcastNavigationState(
    val subView: PodcastSubView = PodcastSubView.Subscriptions,
    val selectedPodcastId: String? = null,
)

internal data class MainNavigationState(
    val destination: AppRoute = AppRoute.Podcasts,
    val podcast: PodcastNavigationState = PodcastNavigationState(),
    val selectedEpisodeId: String? = null,
    val selectedArticleId: String? = null,
    val selectedBookId: String? = null,
    val fullPlayer: Boolean = false,
)

internal sealed interface NavigationAction {
  data class SelectDestination(val route: AppRoute) : NavigationAction

  data class SelectPodcastSubView(val value: PodcastSubView) : NavigationAction

  data class OpenPodcast(val id: String) : NavigationAction

  data class OpenEpisode(val id: String) : NavigationAction

  data class OpenArticle(val id: String) : NavigationAction

  data class OpenBook(val id: String) : NavigationAction

  data object OpenFullPlayer : NavigationAction

  data object CloseFullPlayer : NavigationAction

  data object ClearEpisodeSelection : NavigationAction

  data object ClearPodcastSelection : NavigationAction

  data object NavigateBack : NavigationAction
}

internal fun reduceNavigation(
    state: MainNavigationState,
    action: NavigationAction,
): MainNavigationState =
    when (action) {
      is NavigationAction.SelectDestination ->
          state.copy(
              destination = action.route,
              podcast = state.podcast.copy(selectedPodcastId = null),
              selectedEpisodeId = null,
              selectedArticleId = null,
              selectedBookId = null,
              fullPlayer = false,
          )
      is NavigationAction.SelectPodcastSubView ->
          state.copy(
              podcast =
                  PodcastNavigationState(
                      subView = action.value,
                      selectedPodcastId = null,
                  ),
              selectedEpisodeId = null,
          )
      is NavigationAction.OpenPodcast -> openPodcast(state, action.id)
      is NavigationAction.OpenEpisode ->
          state.copy(
              selectedEpisodeId = action.id,
              selectedArticleId = null,
              selectedBookId = null,
          )
      is NavigationAction.OpenArticle ->
          state.copy(
              selectedEpisodeId = null,
              selectedArticleId = action.id,
              selectedBookId = null,
          )
      is NavigationAction.OpenBook ->
          state.copy(
              selectedEpisodeId = null,
              selectedArticleId = null,
              selectedBookId = action.id,
          )
      NavigationAction.OpenFullPlayer -> state.copy(fullPlayer = true)
      NavigationAction.CloseFullPlayer -> state.copy(fullPlayer = false)
      NavigationAction.ClearEpisodeSelection -> state.copy(selectedEpisodeId = null)
      NavigationAction.ClearPodcastSelection ->
          state.copy(podcast = state.podcast.copy(selectedPodcastId = null))
      NavigationAction.NavigateBack ->
          when {
            state.fullPlayer -> state.copy(fullPlayer = false)
            state.selectedEpisodeId != null -> state.copy(selectedEpisodeId = null)
            state.selectedArticleId != null -> state.copy(selectedArticleId = null)
            state.selectedBookId != null -> state.copy(selectedBookId = null)
            state.destination == AppRoute.Podcasts && state.podcast.selectedPodcastId != null ->
                state.copy(podcast = state.podcast.copy(selectedPodcastId = null))
            else -> state
          }
    }

private fun openPodcast(state: MainNavigationState, id: String): MainNavigationState =
    state.copy(
        destination = AppRoute.Podcasts,
        podcast = PodcastNavigationState(PodcastSubView.Subscriptions, id),
        selectedEpisodeId = null,
        selectedArticleId = null,
        selectedBookId = null,
        fullPlayer = false,
    )

internal fun AppTab.toAppRoute(): AppRoute =
    when (this) {
      AppTab.Podcasts -> AppRoute.Podcasts
      AppTab.Reader -> AppRoute.Reader
      AppTab.Music -> AppRoute.Music
      AppTab.Memos -> AppRoute.Memos
      AppTab.Books -> AppRoute.Books
      AppTab.Settings -> AppRoute.Settings
    }

internal fun List<AppTab>.toAppRoutes(): List<AppRoute> = map(AppTab::toAppRoute)

internal fun restoreNavigationState(savedStateHandle: SavedStateHandle): MainNavigationState {
  val subView = podcastSubViewFromKey(savedStateHandle.get<String>(NAVIGATION_SUBVIEW_KEY))
  return MainNavigationState(
      destination = appRouteFromKey(savedStateHandle.get<String>(NAVIGATION_ROUTE_KEY)),
      podcast = PodcastNavigationState(subView = subView),
  )
}

internal fun persistNavigationState(
    savedStateHandle: SavedStateHandle,
    state: MainNavigationState,
) {
  savedStateHandle[NAVIGATION_ROUTE_KEY] = state.destination.key
  savedStateHandle[NAVIGATION_SUBVIEW_KEY] = state.podcast.subView.name.lowercase(Locale.ROOT)
}

internal fun resolveRoute(
    requested: AppRoute,
    visibleRoutes: List<AppRoute>,
): AppRoute =
    requested.takeIf(visibleRoutes::contains)
        ?: AppRoute.Podcasts.takeIf(visibleRoutes::contains)
        ?: visibleRoutes.firstOrNull()
        ?: AppRoute.Settings

internal fun appRouteFromKey(value: String?): AppRoute {
  val normalized = value?.trim()?.lowercase(Locale.ROOT)
  return AppRoute.entries.firstOrNull { it.key == normalized } ?: AppRoute.Podcasts
}

internal fun podcastSubViewFromKey(value: String?): PodcastSubView {
  val normalized = value?.trim()?.lowercase(Locale.ROOT)
  return when (normalized) {
    "library" -> PodcastSubView.Library
    else -> PodcastSubView.Subscriptions
  }
}
