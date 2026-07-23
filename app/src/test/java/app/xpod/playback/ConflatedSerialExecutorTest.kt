package app.xpod.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConflatedSerialExecutorTest {
  @Test
  fun writesSeriallyAndKeepsTheLatestPendingValue() = runTest {
    val firstWriteCanFinish = CompletableDeferred<Unit>()
    val written = mutableListOf<Int>()
    var activeWrites = 0
    var maximumActiveWrites = 0
    val executor =
        ConflatedSerialExecutor<Int>(this) { value ->
          activeWrites += 1
          maximumActiveWrites = maxOf(maximumActiveWrites, activeWrites)
          if (value == 1) firstWriteCanFinish.await()
          written += value
          activeWrites -= 1
        }

    assertTrue(executor.submit(1))
    runCurrent()
    assertTrue(executor.submit(2))
    assertTrue(executor.submit(3))
    executor.close()
    firstWriteCanFinish.complete(Unit)
    advanceUntilIdle()

    assertEquals(listOf(1, 3), written)
    assertEquals(1, maximumActiveWrites)
  }
}
