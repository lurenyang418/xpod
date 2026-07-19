package app.xpod.ui

import app.xpod.data.CloudMemo
import app.xpod.data.CloudMemoState
import app.xpod.data.CloudMemoVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MemosUiStateTest {
  @Test
  fun retryableArchiveRestoreFailureKeepsUndoAndRequestsAnotherSnackbar() {
    val memo = memo("memo-a")
    val state = MemosUiState(archivedMemoForUndo = memo, archivedMemoUndoSequence = 4)

    val updated = state.afterArchiveRestoreFailure(memo.id, isVersionConflict = false)

    assertSame(memo, updated.archivedMemoForUndo)
    assertEquals(5L, updated.archivedMemoUndoSequence)
  }

  @Test
  fun versionConflictClearsTheStaleUndoWithoutRetrying() {
    val memo = memo("memo-a")
    val state = MemosUiState(archivedMemoForUndo = memo, archivedMemoUndoSequence = 4)

    val updated = state.afterArchiveRestoreFailure(memo.id, isVersionConflict = true)

    assertNull(updated.archivedMemoForUndo)
    assertEquals(4L, updated.archivedMemoUndoSequence)
  }

  @Test
  fun failureForAnOlderMemoDoesNotRestartTheCurrentUndo() {
    val currentMemo = memo("memo-b")
    val state = MemosUiState(archivedMemoForUndo = currentMemo, archivedMemoUndoSequence = 7)

    val updated = state.afterArchiveRestoreFailure("memo-a", isVersionConflict = false)

    assertSame(currentMemo, updated.archivedMemoForUndo)
    assertEquals(7L, updated.archivedMemoUndoSequence)
  }

  private fun memo(id: String) =
      CloudMemo(
          id = id,
          content = "memo",
          visibility = CloudMemoVisibility.Private,
          state = CloudMemoState.Archived,
          pinned = false,
          version = 2,
          createdAtEpochMs = 1,
          updatedAtEpochMs = 2,
          tags = emptyList(),
      )
}
