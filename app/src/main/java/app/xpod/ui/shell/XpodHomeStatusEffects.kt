package app.xpod.ui.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.xpod.ui.shared.UiStatus

@Composable
internal fun XpodHomeStatusEffects(
    snackbar: SnackbarHostState,
    mainStatus: UiStatus?,
    onDismissMainStatus: () -> Unit,
    memosStatus: UiStatus?,
    onDismissMemosStatus: () -> Unit,
    musicStatus: UiStatus?,
    onDismissMusicStatus: () -> Unit,
    videoStatus: UiStatus?,
    onDismissVideoStatus: () -> Unit,
    booksStatus: UiStatus?,
    onDismissBooksStatus: () -> Unit,
) {
  LaunchedEffect(mainStatus) {
    mainStatus?.let {
      snackbar.showXpodSnackbar(it)
      onDismissMainStatus()
    }
  }
  LaunchedEffect(memosStatus) {
    memosStatus?.let {
      snackbar.showXpodSnackbar(it)
      onDismissMemosStatus()
    }
  }
  LaunchedEffect(musicStatus) {
    musicStatus?.let {
      snackbar.showXpodSnackbar(it)
      onDismissMusicStatus()
    }
  }
  LaunchedEffect(videoStatus) {
    videoStatus?.let {
      snackbar.showXpodSnackbar(it)
      onDismissVideoStatus()
    }
  }
  LaunchedEffect(booksStatus) {
    booksStatus?.let {
      snackbar.showXpodSnackbar(it)
      onDismissBooksStatus()
    }
  }
}
