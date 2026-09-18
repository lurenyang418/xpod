package app.xpod.ui

import app.xpod.R
import app.xpod.data.CloudMemo
import app.xpod.data.CloudMemoPage
import app.xpod.data.CloudMemoState
import app.xpod.data.CloudMemoVisibility
import app.xpod.data.CloudMemosConnection
import app.xpod.data.CloudMemosCredentialsException
import app.xpod.data.CloudMemosGateway
import app.xpod.data.CloudMemosNotConfiguredException
import app.xpod.ui.memos.MemosStrings
import app.xpod.ui.memos.MemosViewModel
import app.xpod.ui.memos.cloudMemosFailureReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemosViewModelTest {
  private val dispatcher: TestDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `loadMemos populates items and marks loaded`() {
    val gateway = FakeGateway(configured = true)
    gateway.pages += CloudMemoPage(listOf(memo("a"), memo("b")), nextCursor = "c2")
    val vm = MemosViewModel(gateway, fakeStrings())

    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("a", "b"), vm.memosState.value.items.map { it.id })
    assertTrue(vm.memosState.value.hasLoaded)
    assertFalse(vm.memosState.value.isRefreshing)
    assertEquals(1, gateway.listCalls.size)
    assertNull(gateway.listCalls.first().cursor)
  }

  @Test
  fun `refresh replaces items and drops cursor`() {
    val gateway = FakeGateway(configured = true)
    gateway.pages += CloudMemoPage(listOf(memo("a")), nextCursor = "c2")
    gateway.pages += CloudMemoPage(listOf(memo("b")), nextCursor = null)
    val vm = MemosViewModel(gateway, fakeStrings())

    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(listOf("a"), vm.memosState.value.items.map { it.id })

    vm.refreshMemos()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(listOf("b"), vm.memosState.value.items.map { it.id })
    assertNull(vm.memosState.value.nextCursor)
  }

  @Test
  fun `loadMore appends distinct items`() {
    val gateway = FakeGateway(configured = true)
    gateway.pages += CloudMemoPage(listOf(memo("a")), nextCursor = "c2")
    gateway.pages += CloudMemoPage(listOf(memo("b"), memo("a")), nextCursor = null)
    val vm = MemosViewModel(gateway, fakeStrings())

    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()
    vm.loadMoreMemos()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("a", "b"), vm.memosState.value.items.map { it.id })
    assertEquals("c2", gateway.listCalls[1].cursor)
  }

  @Test
  fun `createMemo sends trimmed content and emits saved status`() {
    val gateway = FakeGateway(configured = true)
    val vm = MemosViewModel(gateway, fakeStrings())

    vm.setMemoDraft("  hello world  ")
    vm.createMemo()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("hello world", gateway.createCalls.single().first)
    assertEquals("", vm.memosState.value.draft)
    assertFalse(vm.memosState.value.isCreating)
    assertNotNull(vm.status.value)
  }

  @Test
  fun `createMemo ignores blank draft`() {
    val gateway = FakeGateway(configured = true)
    val vm = MemosViewModel(gateway, fakeStrings())

    vm.setMemoDraft("   ")
    vm.createMemo()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(gateway.createCalls.isEmpty())
    assertFalse(vm.memosState.value.isCreating)
  }

  @Test
  fun `archive removes item and arms the undo window`() {
    val gateway = FakeGateway(configured = true)
    gateway.pages += CloudMemoPage(listOf(memo("a")), nextCursor = null)
    val vm = MemosViewModel(gateway, fakeStrings())
    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()

    vm.archiveMemo("a")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(CloudMemoState.Archived, gateway.updateCalls.single().third)
    assertTrue(vm.memosState.value.items.isEmpty())
    assertEquals("a", vm.memosState.value.archivedMemoForUndo?.id)
    assertEquals(1L, vm.memosState.value.archivedMemoUndoSequence)
  }

  @Test
  fun `archive of a busy or non-active memo is ignored`() {
    val gateway = FakeGateway(configured = true)
    val archived = memo("x", state = CloudMemoState.Archived)
    gateway.pages += CloudMemoPage(listOf(memo("a"), archived), nextCursor = null)
    val vm = MemosViewModel(gateway, fakeStrings())
    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()

    vm.archiveMemo("a")
    dispatcher.scheduler.advanceUntilIdle()
    vm.archiveMemo("a")
    dispatcher.scheduler.advanceUntilIdle()
    vm.archiveMemo("x")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, gateway.updateCalls.size)
  }

  @Test
  fun `restoring from undo clears undo and emits status`() {
    val gateway = FakeGateway(configured = true)
    gateway.pages += CloudMemoPage(listOf(memo("a")), nextCursor = null)
    val vm = MemosViewModel(gateway, fakeStrings())
    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()
    vm.archiveMemo("a")
    dispatcher.scheduler.advanceUntilIdle()

    vm.restoreArchivedMemo("a")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(CloudMemoState.Active, gateway.updateCalls.last().third)
    assertNull(vm.memosState.value.archivedMemoForUndo)
    assertNotNull(vm.status.value)
  }

  @Test
  fun `moving a memo to trash deletes it and shows status`() {
    val gateway = FakeGateway(configured = true)
    gateway.pages += CloudMemoPage(listOf(memo("a")), nextCursor = null)
    val vm = MemosViewModel(gateway, fakeStrings())
    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()

    vm.requestMemoDelete("a")
    vm.moveMemoToTrash("a")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("a"), gateway.deleteCalls)
    assertTrue(vm.memosState.value.items.isEmpty())
    assertNotNull(vm.status.value)
  }

  @Test
  fun `a failed delete surfaces an inline error and clears busy`() {
    val gateway = FakeGateway(configured = true)
    gateway.pages += CloudMemoPage(listOf(memo("a")), nextCursor = null)
    gateway.deleteResults += Result.failure(IllegalStateException("boom"))
    val vm = MemosViewModel(gateway, fakeStrings())
    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()

    vm.requestMemoDelete("a")
    vm.moveMemoToTrash("a")
    dispatcher.scheduler.advanceUntilIdle()

    assertNotNull(vm.memosState.value.error)
    assertTrue(vm.memosState.value.busyMemoIds.isEmpty())
    assertFalse(vm.memosState.value.items.isEmpty())
  }

  @Test
  fun `account change resets state and bumps reload token`() {
    val gateway = FakeGateway(configured = false)
    val vm = MemosViewModel(gateway, fakeStrings())
    dispatcher.scheduler.advanceUntilIdle()

    gateway.pages += CloudMemoPage(listOf(memo("a")), nextCursor = null)
    vm.loadMemos()
    dispatcher.scheduler.advanceUntilIdle()
    vm.setMemoDraft("draft")
    assertFalse(vm.memosState.value.items.isEmpty())
    assertEquals(0, vm.reloadToken.value)

    gateway.connection.value = CloudMemosConnection("https://other.example", isConfigured = true)
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(vm.memosState.value.items.isEmpty())
    assertEquals("", vm.memosState.value.draft)
    assertFalse(vm.memosState.value.hasLoaded)
    assertEquals(1, vm.reloadToken.value)
    assertNull(vm.status.value)
  }

  @Test
  fun `a newer refresh cancels and supersedes an in-flight load`() {
    val gateway = FakeGateway(configured = true)
    gateway.holdList = true
    val vm = MemosViewModel(gateway, fakeStrings())

    vm.refreshMemos()
    dispatcher.scheduler.advanceUntilIdle() // first load suspends inside the gateway

    gateway.holdList = false
    gateway.pages += CloudMemoPage(listOf(memo("fresh")), nextCursor = null)

    vm.refreshMemos() // cancels the first load and starts a second
    dispatcher.scheduler.advanceUntilIdle()
    assertFalse(vm.memosState.value.isRefreshing)

    gateway.release.complete(Unit) // let the cancelled request finish; it must not apply
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("fresh"), vm.memosState.value.items.map { it.id })
  }

  @Test
  fun `credential decryption failures are not reported as not configured`() {
    assertEquals(
        "R:${R.string.cloud_memos_error_credentials}",
        cloudMemosFailureReason(
            fakeStrings(),
            CloudMemosCredentialsException(RuntimeException("keystore")),
        ),
    )
    assertEquals(
        "R:${R.string.cloud_memos_error_not_configured}",
        cloudMemosFailureReason(fakeStrings(), CloudMemosNotConfiguredException()),
    )
  }
}

private fun memo(id: String, state: CloudMemoState = CloudMemoState.Active): CloudMemo =
    CloudMemo(
        id = id,
        content = "content-$id",
        visibility = CloudMemoVisibility.Private,
        state = state,
        pinned = false,
        version = 1,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 2,
        tags = emptyList(),
    )

private fun fakeStrings(): MemosStrings = MemosStrings { resId, _ -> "R:$resId" }

private class FakeGateway(configured: Boolean) : CloudMemosGateway {
  override val connection =
      MutableStateFlow<CloudMemosConnection>(
          CloudMemosConnection(if (configured) "https://memos.example" else "", configured)
      )
  val pages = ArrayDeque<CloudMemoPage>()
  val createResults = ArrayDeque<Result<String>>()
  val updateResults = ArrayDeque<Result<CloudMemo>>()
  val deleteResults = ArrayDeque<Result<Unit>>()
  val createCalls = mutableListOf<Pair<String, CloudMemoVisibility>>()
  val updateCalls = mutableListOf<Triple<String, Long, CloudMemoState>>()
  val deleteCalls = mutableListOf<String>()

  data class ListCall(
      val query: String?,
      val tag: String?,
      val cursor: String?,
      val limit: Int,
  )

  val listCalls = mutableListOf<ListCall>()
  var holdList = false
  val release = CompletableDeferred<Unit>()

  override suspend fun listMemos(
      query: String?,
      tag: String?,
      cursor: String?,
      limit: Int,
  ): Result<CloudMemoPage> {
    listCalls += ListCall(query, tag, cursor, limit)
    if (holdList) release.await()
    return pages.removeFirstOrNull()?.let { Result.success(it) }
        ?: Result.success(CloudMemoPage(emptyList(), nextCursor = null))
  }

  override suspend fun createMemo(
      content: String,
      visibility: CloudMemoVisibility,
  ): Result<String> {
    createCalls += content to visibility
    return createResults.removeFirstOrNull() ?: Result.success("memo-created")
  }

  override suspend fun updateMemoState(
      memoId: String,
      version: Long,
      state: CloudMemoState,
  ): Result<CloudMemo> {
    updateCalls += Triple(memoId, version, state)
    return updateResults.removeFirstOrNull() ?: Result.success(memo(memoId, state))
  }

  override suspend fun deleteMemo(memoId: String): Result<Unit> {
    deleteCalls += memoId
    return deleteResults.removeFirstOrNull() ?: Result.success(Unit)
  }
}
