package app.xpod.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test

class CoroutineResultTest {
    @Test fun rethrowsCancellation() = runTest {
        try {
            runCatchingCancellable { throw CancellationException("cancelled") }
            fail("CancellationException should be rethrown")
        } catch (_: CancellationException) {
            // Expected: cancellation must retain structured-concurrency semantics.
        }
    }
}
