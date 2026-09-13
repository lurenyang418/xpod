package app.xpod.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedInputTest {
  @Test
  fun readsInputAtOrBelowTheLimit() = runTest {
    val input = byteArrayOf(1, 2, 3, 4)

    assertArrayEquals(input, readBytesAtMost(ByteArrayInputStream(input), input.size))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsInputAboveTheLimit(): Unit = runTest {
    readBytesAtMost(ByteArrayInputStream(ByteArray(5)), 4)
  }

  @Test
  fun stopsReadingOnceTheCallingCoroutineIsCancelled() = runTest {
    lateinit var job: Job
    var reads = 0
    val endlessInput =
        object : InputStream() {
          override fun read(): Int = error("unused")

          override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            reads++
            if (reads > 1) return -1 // Keep the test finite even if cancellation regresses.
            job.cancel()
            return length
          }
        }

    job = launch { readBytesAtMost(endlessInput, Int.MAX_VALUE) }
    job.join()

    assertTrue(job.isCancelled)
    assertEquals(1, reads)
  }
}
