package app.xpod.ui.memos

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.xpod.R

@Composable
internal fun MemosDialogs(
    state: MemosUiState,
    shareActions: MemosShareActions,
    manageActions: MemosManageActions,
) {
  state.pendingPrivateShareMemoId
      ?.let { memoId -> state.items.firstOrNull { memo -> memo.id == memoId } }
      ?.let { memo ->
        AlertDialog(
            onDismissRequest = shareActions.dismissPrivateMemoShare,
            title = { Text(stringResource(R.string.share_private_memo_title)) },
            text = { Text(stringResource(R.string.share_private_memo_message)) },
            confirmButton = {
              TextButton(
                  onClick = {
                    shareActions.sharePrivateMemoContent(memo)
                    shareActions.dismissPrivateMemoShare()
                  }
              ) {
                Text(stringResource(R.string.share_markdown))
              }
            },
            dismissButton = {
              TextButton(onClick = shareActions.dismissPrivateMemoShare) {
                Text(stringResource(R.string.cancel))
              }
            },
        )
      }

  state.pendingDeleteMemoId
      ?.let { memoId -> state.items.firstOrNull { memo -> memo.id == memoId } }
      ?.let { memo ->
        AlertDialog(
            onDismissRequest = manageActions.dismissDelete,
            title = { Text(stringResource(R.string.move_memo_to_trash_title)) },
            text = { Text(stringResource(R.string.move_memo_to_trash_message)) },
            confirmButton = {
              TextButton(onClick = { manageActions.moveToTrash(memo.id) }) {
                Text(
                    stringResource(R.string.move_memo_to_trash),
                    color = MaterialTheme.colorScheme.error,
                )
              }
            },
            dismissButton = {
              TextButton(onClick = manageActions.dismissDelete) {
                Text(stringResource(R.string.cancel))
              }
            },
        )
      }
}
