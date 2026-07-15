package app.xpod.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BoundedInputTest {
    @Test fun readsInputAtOrBelowTheLimit() {
        val input = byteArrayOf(1, 2, 3, 4)

        assertArrayEquals(input, readBytesAtMost(ByteArrayInputStream(input), input.size))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInputAboveTheLimit() {
        readBytesAtMost(ByteArrayInputStream(ByteArray(5)), 4)
    }
}
