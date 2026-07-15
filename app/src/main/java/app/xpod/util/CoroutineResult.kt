package app.xpod.util

import kotlinx.coroutines.CancellationException

suspend inline fun <T> runCatchingCancellable(crossinline block: suspend () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (error: Throwable) {
      if (error is CancellationException) throw error
      Result.failure(error)
    }
