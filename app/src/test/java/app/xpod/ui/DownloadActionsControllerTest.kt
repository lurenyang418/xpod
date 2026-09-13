package app.xpod.ui

import app.xpod.data.DownloadPhase
import app.xpod.data.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadActionsControllerTest {
  @Test
  fun `missing state enqueues a download`() {
    assertEquals(DownloadAction.Enqueue, downloadAction(null))
  }

  @Test
  fun `completed state removes the download`() {
    assertEquals(
        DownloadAction.Remove,
        downloadAction(DownloadState(progress = 1f, isCompleted = true)),
    )
  }

  @Test
  fun `failed state retries the download`() {
    assertEquals(
        DownloadAction.Retry,
        downloadAction(DownloadState(progress = 0.5f, phase = DownloadPhase.Failed)),
    )
  }

  @Test
  fun `active state reports download in progress`() {
    assertEquals(
        DownloadAction.ShowInProgress,
        downloadAction(DownloadState(progress = 0.5f, phase = DownloadPhase.Downloading)),
    )
  }
}
