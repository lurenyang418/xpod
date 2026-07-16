package app.xpod.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

enum class FeedRequestType(
    internal val userAgent: String,
    internal val accept: String,
) {
  Podcast(
      userAgent = "XPOD/1.0 (Android podcast client)",
      accept = "application/rss+xml, application/xml, text/xml, */*",
  ),
  Articles(
      userAgent = "XPOD/1.0 (Android reader)",
      accept = "application/rss+xml, application/atom+xml, application/xml, text/xml, */*",
  ),
  Subscription(
      userAgent = "XPOD/1.0 (Android subscription client)",
      accept = "application/rss+xml, application/atom+xml, application/xml, text/xml, */*",
  ),
}

@Singleton
class FeedFetcher @Inject constructor(private val client: OkHttpClient) {
  fun fetch(url: String, requestType: FeedRequestType): ByteArray {
    if (!url.startsWith("https://", ignoreCase = true)) throw UnsupportedFeedUrlException(url)
    return client
        .newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", requestType.userAgent)
                .header("Accept", requestType.accept)
                .build()
        )
        .apply { timeout().timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        .execute()
        .use { response ->
          if (!response.isSuccessful) throw FeedHttpException(response.code)
          val body = requireNotNull(response.body)
          require(body.contentLength() <= MAX_FEED_BYTES || body.contentLength() == -1L) {
            "Feed exceeds the 10 MiB limit"
          }
          body.byteStream().use { readBytesAtMost(it, MAX_FEED_BYTES) }
        }
  }

  private companion object {
    const val MAX_FEED_BYTES = 10 * 1024 * 1024
    const val REQUEST_TIMEOUT_SECONDS = 25L
  }
}

class FeedHttpException(val statusCode: Int) : IOException("Feed request failed: HTTP $statusCode")

class UnsupportedFeedUrlException(val feedUrl: String) :
    IllegalArgumentException("Only HTTPS feed URLs are supported: $feedUrl")

internal fun shouldRetryFeedRefresh(error: Throwable): Boolean =
    when (error) {
      is FeedHttpException -> error.statusCode >= 500
      is IOException -> true
      else -> false
    }

internal fun readBytesAtMost(input: InputStream, maxBytes: Int): ByteArray {
  val output = ByteArrayOutputStream()
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  var total = 0
  while (true) {
    val count = input.read(buffer)
    if (count < 0) break
    total += count
    require(total <= maxBytes) { "Input exceeds the configured size limit" }
    output.write(buffer, 0, count)
  }
  return output.toByteArray()
}
