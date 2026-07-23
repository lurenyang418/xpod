package app.xpod.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class ConflatedSerialExecutor<T>(
    scope: CoroutineScope,
    block: suspend (T) -> Unit,
) {
  private val requests = Channel<T>(Channel.CONFLATED)
  private val worker: Job = scope.launch {
    for (request in requests) {
      block(request)
    }
  }

  fun submit(value: T): Boolean = requests.trySend(value).isSuccess

  fun close() {
    requests.close()
  }

  fun invokeOnCompletion(block: (Throwable?) -> Unit) {
    worker.invokeOnCompletion(block)
  }
}
