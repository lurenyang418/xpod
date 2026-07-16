package app.xpod.data

import java.io.IOException
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFetcherTest {
  @Test
  fun rejectsNonHttpsUrlsBeforeMakingARequest() {
    val url = "http://unsafe.example/feed.xml"

    val error =
        assertThrows(UnsupportedFeedUrlException::class.java) {
          FeedFetcher(OkHttpClient()).fetch(url, FeedRequestType.Subscription)
        }

    assertEquals(url, error.feedUrl)
  }

  @Test
  fun retriesNetworkAndServerFailuresButNotClientFailures() {
    assertTrue(shouldRetryFeedRefresh(IOException("offline")))
    assertTrue(shouldRetryFeedRefresh(FeedHttpException(503)))
    assertFalse(shouldRetryFeedRefresh(FeedHttpException(404)))
    assertFalse(shouldRetryFeedRefresh(IllegalArgumentException("invalid feed")))
  }
}
