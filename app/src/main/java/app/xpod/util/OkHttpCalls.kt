package app.xpod.util

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Executes the call without blocking a thread and cancels the in-flight request when the calling
 * coroutine is cancelled.
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
  continuation.invokeOnCancellation { cancel() }
  enqueue(
      object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
          if (continuation.isActive) continuation.resumeWith(Result.success(response))
          else response.close()
        }
      }
  )
}
