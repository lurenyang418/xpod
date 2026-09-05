package app.xpod.data

import kotlinx.coroutines.flow.Flow

/** Storage/network gateway for the Cloud Memos list domain, so ViewModels can be unit-tested. */
interface CloudMemosGateway {
  val connection: Flow<CloudMemosConnection>

  suspend fun listMemos(
      query: String? = null,
      tag: String? = null,
      cursor: String? = null,
      limit: Int = 20,
  ): Result<CloudMemoPage>

  suspend fun createMemo(
      content: String,
      visibility: CloudMemoVisibility = CloudMemoVisibility.Private,
  ): Result<String>

  suspend fun updateMemoState(
      memoId: String,
      version: Long,
      state: CloudMemoState,
  ): Result<CloudMemo>

  suspend fun deleteMemo(memoId: String): Result<Unit>
}
