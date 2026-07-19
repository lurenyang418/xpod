package app.xpod.data

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudMemosApiTest {
  @Test
  fun endpointRequiresHttpsWithoutCredentialsOrQuery() {
    assertThrows(InvalidCloudMemosUrlException::class.java) {
      normalizeCloudMemosUrl("http://memos.example.com")
    }
    assertThrows(InvalidCloudMemosUrlException::class.java) {
      normalizeCloudMemosUrl("https://user:secret@memos.example.com")
    }
    assertThrows(InvalidCloudMemosUrlException::class.java) {
      normalizeCloudMemosUrl("https://memos.example.com?token=secret")
    }

    assertEquals(
        "https://memos.example.com/base/",
        normalizeCloudMemosUrl(" https://memos.example.com/base/ ").toString(),
    )
  }

  @Test
  fun memoWebUrlPreservesInstancePathAndEncodesMemoId() {
    assertEquals(
        "https://memos.example.com/base/m/memo%2Fid",
        cloudMemoWebUrl("https://memos.example.com/base", "memo/id"),
    )
  }

  @Test
  fun memoWebUrlSupportsRootInstanceUrl() {
    assertEquals(
        "https://memos.example.com/m/memo-id",
        cloudMemoWebUrl("https://memos.example.com", "memo-id"),
    )
  }

  @Test
  fun verifyChecksReadAndWriteAccessWithoutCreatingAMemo() = runTest {
    val requests = mutableListOf<Request>()
    val api =
        CloudMemosApi(
            client { incoming ->
              requests += incoming
              if (incoming.method == "GET") {
                response(incoming, 200, """{"items":[],"nextCursor":null}""")
              } else {
                response(
                    incoming,
                    404,
                    """{"error":{"code":"MEMO_NOT_FOUND","message":"missing"}}""",
                )
              }
            }
        )

    api.verify(normalizeCloudMemosUrl("https://memos.example.com/base"), TOKEN)

    assertEquals(2, requests.size)
    assertEquals(
        "https://memos.example.com/base/api/v1/memos?state=ACTIVE&limit=1",
        requests[0].url.toString(),
    )
    assertEquals("GET", requests[0].method)
    assertEquals(
        "https://memos.example.com/base/api/v1/memos/00000000-0000-0000-0000-000000000000",
        requests[1].url.toString(),
    )
    assertEquals("PATCH", requests[1].method)
    requests.forEach { request ->
      assertEquals("Bearer $TOKEN", request.header("Authorization"))
      assertEquals("application/json", request.header("Accept"))
    }
  }

  @Test
  fun verifyRejectsAReadOnlyToken() = runTest {
    val api =
        CloudMemosApi(
            client { incoming ->
              if (incoming.method == "GET") {
                response(incoming, 200, """{"items":[],"nextCursor":null}""")
              } else {
                response(
                    incoming,
                    403,
                    """{"error":{"code":"INSUFFICIENT_SCOPE","message":"denied"}}""",
                )
              }
            }
        )

    val error =
        try {
          api.verify(normalizeCloudMemosUrl("https://memos.example.com"), TOKEN)
          throw AssertionError("Expected CloudMemosHttpException")
        } catch (error: CloudMemosHttpException) {
          error
        }

    assertEquals(403, error.statusCode)
    assertEquals("INSUFFICIENT_SCOPE", error.errorCode)
  }

  @Test
  fun savedTokenIsOnlyReusedForTheSameInstanceUrl() {
    val credentials = "https://memos.example.com/base" to TOKEN

    assertEquals(
        TOKEN,
        storedTokenForCloudMemosUrl(
            normalizeCloudMemosUrl("https://memos.example.com/base/"),
            credentials,
        ),
    )
    assertEquals(
        null,
        storedTokenForCloudMemosUrl(
            normalizeCloudMemosUrl("https://other.example.com/base"),
            credentials,
        ),
    )
  }

  @Test
  fun createMemoPostsPrivateMarkdownWithoutAttachments() = runTest {
    val request = AtomicReference<Request>()
    val api =
        CloudMemosApi(
            client { incoming ->
              request.set(incoming)
              response(incoming, 201, """{"id":"memo-id"}""")
            }
        )

    val id =
        api.createMemo(
            normalizeCloudMemosUrl("https://memos.example.com"),
            TOKEN,
            "A **memo**",
            CloudMemoVisibility.Private,
        )

    assertEquals("memo-id", id)
    assertEquals("POST", request.get().method)
    val buffer = Buffer()
    requireNotNull(request.get().body).writeTo(buffer)
    val json = buffer.readUtf8()
    assertTrue(json.contains("\"content\":\"A **memo**\""))
    assertTrue(json.contains("\"visibility\":\"PRIVATE\""))
    assertTrue(json.contains("\"attachmentIds\":[]"))
  }

  @Test
  fun listMemosParsesItemsAndForwardsSearchCursor() = runTest {
    val request = AtomicReference<Request>()
    val api =
        CloudMemosApi(
            client { incoming ->
              request.set(incoming)
              response(
                  incoming,
                  200,
                  """{
                    "items":[{
                      "id":"memo-id",
                      "content":"Hello #xpod",
                      "visibility":"MEMBERS",
                      "state":"ACTIVE",
                      "pinned":true,
                      "version":3,
                      "createdAt":100,
                      "updatedAt":200,
                      "tags":["xpod"]
                    }],
                    "nextCursor":"next-page"
                  }""",
              )
            }
        )

    val page =
        api.listMemos(
            normalizeCloudMemosUrl("https://memos.example.com"),
            TOKEN,
            query = "hello world",
            tag = "xpod",
            cursor = "current-page",
            limit = 12,
        )

    assertEquals(
        "https://memos.example.com/api/v1/memos?state=ACTIVE&limit=12&q=hello%20world&tag=xpod&cursor=current-page",
        request.get().url.toString(),
    )
    assertEquals("next-page", page.nextCursor)
    assertEquals(1, page.items.size)
    assertEquals("memo-id", page.items.single().id)
    assertEquals(CloudMemoVisibility.Members, page.items.single().visibility)
    assertEquals(CloudMemoState.Active, page.items.single().state)
    assertEquals(3L, page.items.single().version)
    assertTrue(page.items.single().pinned)
    assertEquals(listOf("xpod"), page.items.single().tags)
  }

  @Test
  fun archiveMemoPatchesStateWithCurrentVersion() = runTest {
    val request = AtomicReference<Request>()
    val api =
        CloudMemosApi(
            client { incoming ->
              request.set(incoming)
              response(
                  incoming,
                  200,
                  """{
                    "id":"memo/id",
                    "content":"Archived memo",
                    "visibility":"PRIVATE",
                    "state":"ARCHIVED",
                    "pinned":false,
                    "version":8,
                    "createdAt":100,
                    "updatedAt":300,
                    "tags":[]
                  }""",
              )
            }
        )

    val memo =
        api.updateMemoState(
            normalizeCloudMemosUrl("https://memos.example.com/base"),
            TOKEN,
            memoId = "memo/id",
            version = 7,
            state = CloudMemoState.Archived,
        )

    assertEquals("PATCH", request.get().method)
    assertEquals(
        "https://memos.example.com/base/api/v1/memos/memo%2Fid",
        request.get().url.toString(),
    )
    val buffer = Buffer()
    requireNotNull(request.get().body).writeTo(buffer)
    assertEquals("{\"state\":\"ARCHIVED\",\"version\":7}", buffer.readUtf8())
    assertEquals(CloudMemoState.Archived, memo.state)
    assertEquals(8L, memo.version)
  }

  @Test
  fun deleteMemoMovesMemoToTrash() = runTest {
    val requests = mutableListOf<Request>()
    val api =
        CloudMemosApi(
            client { incoming ->
              requests += incoming
              if (incoming.url.encodedPath.endsWith("/openapi.json")) {
                response(
                    incoming,
                    200,
                    """{
                      "paths": {
                        "/api/v1/memos/{id}/restore": {"post": {}},
                        "/api/v1/memos/{id}/permanent": {"delete": {}}
                      }
                    }""",
                )
              } else {
                response(incoming, 204, "")
              }
            }
        )

    api.deleteMemo(
        normalizeCloudMemosUrl("https://memos.example.com/base"),
        TOKEN,
        memoId = "memo-id",
    )

    assertEquals(2, requests.size)
    assertEquals("GET", requests[0].method)
    assertEquals(
        "https://memos.example.com/base/api/v1/openapi.json",
        requests[0].url.toString(),
    )
    assertEquals("DELETE", requests[1].method)
    assertEquals(
        "https://memos.example.com/base/api/v1/memos/memo-id",
        requests[1].url.toString(),
    )
  }

  @Test
  fun deleteMemoRefusesServerWithoutRecycleBinSupport() = runTest {
    val requests = mutableListOf<Request>()
    val api =
        CloudMemosApi(
            client { incoming ->
              requests += incoming
              response(
                  incoming,
                  200,
                  """{
                    "paths": {
                      "/api/v1/memos/{id}": {"delete": {}}
                    }
                  }""",
              )
            }
        )

    try {
      api.deleteMemo(
          normalizeCloudMemosUrl("https://memos.example.com"),
          TOKEN,
          memoId = "memo-id",
      )
      throw AssertionError("Expected CloudMemosRecycleBinUnsupportedException")
    } catch (_: CloudMemosRecycleBinUnsupportedException) {
      // Expected: the destructive request must not be sent without a proven recycle bin.
    }

    assertEquals(1, requests.size)
    assertEquals("GET", requests.single().method)
  }

  @Test
  fun exposesStableServerErrorCode() = runTest {
    val api =
        CloudMemosApi(
            client { incoming ->
              response(
                  incoming,
                  403,
                  """{"error":{"code":"INSUFFICIENT_SCOPE","message":"denied"}}""",
              )
            }
        )

    val error =
        try {
          api.verify(normalizeCloudMemosUrl("https://memos.example.com"), TOKEN)
          throw AssertionError("Expected CloudMemosHttpException")
        } catch (error: CloudMemosHttpException) {
          error
        }

    assertEquals(403, error.statusCode)
    assertEquals("INSUFFICIENT_SCOPE", error.errorCode)
  }

  private fun client(handler: (Request) -> Response): OkHttpClient =
      OkHttpClient.Builder().addInterceptor { chain -> handler(chain.request()) }.build()

  private fun response(request: Request, code: Int, body: String): Response =
      Response.Builder()
          .request(request)
          .protocol(Protocol.HTTP_1_1)
          .code(code)
          .message("test")
          .body(body.toResponseBody())
          .build()

  private companion object {
    const val TOKEN = "cm_pat_prefix_secret"
  }
}
