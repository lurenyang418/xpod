package app.xpod.data

import app.xpod.di.buildHttpClient
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFetcherTest {
  @Test
  fun productionClientDoesNotFollowCrossProtocolRedirects() {
    val client = buildHttpClient(File(System.getProperty("java.io.tmpdir")))
    assertFalse(client.followSslRedirects)
  }

  @Test
  fun rejectsNonHttpsUrlsBeforeMakingARequest() = runTest {
    val url = "http://unsafe.example/feed.xml"

    val error =
        try {
          FeedFetcher(OkHttpClient()).fetch(url, FeedRequestType.Subscription)
          throw AssertionError("Expected UnsupportedFeedUrlException")
        } catch (error: UnsupportedFeedUrlException) {
          error
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
