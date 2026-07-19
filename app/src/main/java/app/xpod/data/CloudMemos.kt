package app.xpod.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.xpod.util.runCatchingCancellable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

private val Context.cloudMemosStore by preferencesDataStore("cloud_memos")

enum class CloudMemoVisibility {
  Private,
  Members,
  Public,
}

enum class CloudMemoState {
  Active,
  Archived,
}

data class CloudMemosConnection(
    val baseUrl: String = "",
    val isConfigured: Boolean = false,
)

data class CloudMemo(
    val id: String,
    val content: String,
    val visibility: CloudMemoVisibility,
    val state: CloudMemoState,
    val pinned: Boolean,
    val version: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val tags: List<String>,
)

data class CloudMemoPage(
    val items: List<CloudMemo>,
    val nextCursor: String?,
)

class InvalidCloudMemosUrlException :
    IllegalArgumentException("Cloud Memos requires an HTTPS instance URL")

class InvalidCloudMemosTokenException :
    IllegalArgumentException("Cloud Memos API token has an invalid format")

class CloudMemosNotConfiguredException :
    IllegalStateException("Cloud Memos has not been configured")

class CloudMemosRecycleBinUnsupportedException :
    IllegalStateException("Cloud Memos recycle bin support could not be verified")

class CloudMemosHttpException(
    val statusCode: Int,
    val errorCode: String?,
) : IOException("Cloud Memos request failed: HTTP $statusCode${errorCode?.let { " ($it)" } ?: ""}")

class CloudMemosProtocolException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

internal fun normalizeCloudMemosUrl(value: String): HttpUrl {
  val parsed = value.trim().toHttpUrlOrNull() ?: throw InvalidCloudMemosUrlException()
  if (
      parsed.scheme != "https" ||
          parsed.username.isNotEmpty() ||
          parsed.password.isNotEmpty() ||
          parsed.query != null ||
          parsed.fragment != null
  ) {
    throw InvalidCloudMemosUrlException()
  }
  val path = parsed.encodedPath.trimEnd('/')
  return parsed.newBuilder().encodedPath(if (path.isEmpty()) "/" else "$path/").build()
}

internal fun cloudMemoWebUrl(baseUrl: String, memoId: String): String =
    normalizeCloudMemosUrl(baseUrl)
        .newBuilder()
        .addPathSegment("m")
        .addPathSegment(memoId)
        .build()
        .toString()

@Singleton
class CloudMemosApi @Inject constructor(private val client: OkHttpClient) {
  suspend fun verify(baseUrl: HttpUrl, token: String) {
    listMemos(baseUrl, token, limit = 1)
    verifyWriteAccess(baseUrl, token)
  }

  suspend fun listMemos(
      baseUrl: HttpUrl,
      token: String,
      query: String? = null,
      tag: String? = null,
      cursor: String? = null,
      limit: Int = DEFAULT_PAGE_SIZE,
  ): CloudMemoPage {
    require(limit in 1..MAX_PAGE_SIZE)
    val url =
        apiUrl(baseUrl)
            .newBuilder()
            .addQueryParameter("state", "ACTIVE")
            .addQueryParameter("limit", limit.toString())
            .apply {
              query?.trim()?.takeIf(String::isNotEmpty)?.let { addQueryParameter("q", it) }
              tag?.trim()?.takeIf(String::isNotEmpty)?.let { addQueryParameter("tag", it) }
              cursor?.takeIf(String::isNotEmpty)?.let { addQueryParameter("cursor", it) }
            }
            .build()
    val response =
        executeJson(
            requestBuilder(token).url(url).get().build(),
            expectedCode = 200,
        )
    return parsePage(response)
  }

  suspend fun createMemo(
      baseUrl: HttpUrl,
      token: String,
      content: String,
      visibility: CloudMemoVisibility,
  ): String {
    val body =
        buildJsonObject {
              put("content", content)
              put("visibility", visibility.apiValue)
              put("attachmentIds", buildJsonArray {})
            }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
    val response =
        executeJson(
            requestBuilder(token).url(apiUrl(baseUrl)).post(body).build(),
            expectedCode = 201,
        )
    return response["id"]?.jsonPrimitive?.content
        ?: throw CloudMemosProtocolException("Cloud Memos returned an invalid response")
  }

  suspend fun updateMemoState(
      baseUrl: HttpUrl,
      token: String,
      memoId: String,
      version: Long,
      state: CloudMemoState,
  ): CloudMemo {
    val body =
        buildJsonObject {
              put("state", state.apiValue)
              put("version", version)
            }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
    val response =
        executeJson(
            requestBuilder(token)
                .url(apiUrl(baseUrl).newBuilder().addPathSegment(memoId).build())
                .patch(body)
                .build(),
            expectedCode = 200,
        )
    return parseMemo(response)
  }

  suspend fun deleteMemo(baseUrl: HttpUrl, token: String, memoId: String) {
    requireRecycleBinSupport(baseUrl, token)
    val response =
        execute(
            requestBuilder(token)
                .url(apiUrl(baseUrl).newBuilder().addPathSegment(memoId).build())
                .delete()
                .build()
        )
    if (response.statusCode != 204) {
      throw httpException(response.statusCode, response.body)
    }
  }

  private suspend fun requireRecycleBinSupport(baseUrl: HttpUrl, token: String) {
    val response =
        execute(
            requestBuilder(token)
                .url(baseUrl.newBuilder().addPathSegments("api/v1/openapi.json").build())
                .get()
                .build()
        )
    if (response.statusCode == 404) throw CloudMemosRecycleBinUnsupportedException()
    if (response.statusCode != 200) {
      throw httpException(response.statusCode, response.body)
    }
    val document =
        runCatching { JSON.parseToJsonElement(response.body).jsonObject }
            .getOrElse { throw CloudMemosRecycleBinUnsupportedException() }
    val supportsRecycleBin =
        runCatching {
              val paths = requireNotNull(document["paths"]).jsonObject
              paths["/api/v1/memos/{id}/restore"]?.jsonObject?.containsKey("post") == true &&
                  paths["/api/v1/memos/{id}/permanent"]?.jsonObject?.containsKey("delete") == true
            }
            .getOrDefault(false)
    if (!supportsRecycleBin) {
      throw CloudMemosRecycleBinUnsupportedException()
    }
  }

  private fun requestBuilder(token: String): Request.Builder =
      Request.Builder()
          .header("Authorization", "Bearer $token")
          .header("Accept", "application/json")
          .header("User-Agent", "XPOD/1.0 (Android Cloud Memos client)")

  private fun apiUrl(baseUrl: HttpUrl): HttpUrl =
      baseUrl.newBuilder().addPathSegments("api/v1/memos").build()

  private suspend fun verifyWriteAccess(baseUrl: HttpUrl, token: String) {
    // The server checks memos:write before looking up the memo. This all-zero ID cannot be
    // produced by its UUID v4 generator, so MEMO_NOT_FOUND proves access without mutating data.
    val body =
        buildJsonObject {
              put("version", 1)
              put("pinned", false)
            }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
    val response =
        execute(
            requestBuilder(token)
                .url(apiUrl(baseUrl).newBuilder().addPathSegment(WRITE_PROBE_MEMO_ID).build())
                .patch(body)
                .build()
        )
    val error = httpException(response.statusCode, response.body)
    if (response.statusCode == 404 && error.errorCode == "MEMO_NOT_FOUND") return
    throw error
  }

  private suspend fun executeJson(request: Request, expectedCode: Int): JsonObject {
    val response = execute(request)
    if (response.statusCode != expectedCode) {
      throw httpException(response.statusCode, response.body)
    }
    return runCatching { JSON.parseToJsonElement(response.body).jsonObject }
        .getOrElse { error ->
          throw CloudMemosProtocolException("Cloud Memos returned invalid JSON", error)
        }
  }

  private suspend fun execute(request: Request): CloudMemosResponse {
    val response = client.newCall(request).await()
    return response.use {
      val body = requireNotNull(it.body)
      if (body.contentLength() > MAX_RESPONSE_BYTES) {
        throw CloudMemosProtocolException("Cloud Memos response exceeds the size limit")
      }
      val text =
          try {
            body
                .byteStream()
                .use { input -> readBytesAtMost(input, MAX_RESPONSE_BYTES) }
                .toString(StandardCharsets.UTF_8)
          } catch (error: IllegalArgumentException) {
            throw CloudMemosProtocolException("Cloud Memos response exceeds the size limit", error)
          }
      CloudMemosResponse(it.code, text)
    }
  }

  private fun httpException(statusCode: Int, body: String): CloudMemosHttpException {
    val code =
        runCatching {
              JSON.parseToJsonElement(body)
                  .jsonObject["error"]
                  ?.jsonObject
                  ?.get("code")
                  ?.jsonPrimitive
                  ?.content
            }
            .getOrNull()
    return CloudMemosHttpException(statusCode, code)
  }

  private fun parsePage(response: JsonObject): CloudMemoPage {
    val items =
        response["items"]?.jsonArray?.map { element -> parseMemo(element.jsonObject) }
            ?: throw CloudMemosProtocolException("Cloud Memos returned an invalid memo page")
    val nextCursor = response["nextCursor"]?.jsonPrimitive?.contentOrNull
    return CloudMemoPage(items, nextCursor)
  }

  private fun parseMemo(value: JsonObject): CloudMemo =
      CloudMemo(
          id = value.requiredString("id"),
          content = value.requiredString("content"),
          visibility =
              when (value.requiredString("visibility")) {
                "PRIVATE" -> CloudMemoVisibility.Private
                "MEMBERS" -> CloudMemoVisibility.Members
                "PUBLIC" -> CloudMemoVisibility.Public
                else ->
                    throw CloudMemosProtocolException("Cloud Memos returned an invalid visibility")
              },
          state =
              when (value.requiredString("state")) {
                "ACTIVE" -> CloudMemoState.Active
                "ARCHIVED" -> CloudMemoState.Archived
                else -> throw CloudMemosProtocolException("Cloud Memos returned an invalid state")
              },
          pinned =
              value["pinned"]?.jsonPrimitive?.booleanOrNull
                  ?: throw CloudMemosProtocolException(
                      "Cloud Memos returned an invalid pinned state"
                  ),
          version = value.requiredLong("version"),
          createdAtEpochMs = value.requiredLong("createdAt"),
          updatedAtEpochMs = value.requiredLong("updatedAt"),
          tags =
              value["tags"]?.jsonArray?.map { it.jsonPrimitive.content }
                  ?: throw CloudMemosProtocolException("Cloud Memos returned invalid tags"),
      )

  private companion object {
    val JSON = Json { ignoreUnknownKeys = true }
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 50
    const val WRITE_PROBE_MEMO_ID = "00000000-0000-0000-0000-000000000000"
  }
}

private data class CloudMemosResponse(val statusCode: Int, val body: String)

@Singleton
class CloudMemosRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val api: CloudMemosApi,
) {
  private val cipher = CloudMemosCredentialCipher()
  private val baseUrlKey = stringPreferencesKey("base_url")
  private val encryptedTokenKey = stringPreferencesKey("encrypted_api_token")

  val connection: Flow<CloudMemosConnection> =
      context.cloudMemosStore.data.map { preferences ->
        CloudMemosConnection(
            baseUrl = preferences[baseUrlKey].orEmpty(),
            isConfigured =
                !preferences[baseUrlKey].isNullOrBlank() &&
                    !preferences[encryptedTokenKey].isNullOrBlank(),
        )
      }

  suspend fun configure(baseUrl: String, token: String?) {
    val url = normalizeCloudMemosUrl(baseUrl)
    val resolvedToken =
        token?.trim()?.takeIf(String::isNotEmpty)
            ?: storedTokenForCloudMemosUrl(url, readCredentialsOrNull())
            ?: throw InvalidCloudMemosTokenException()
    if (!resolvedToken.startsWith("cm_pat_") || resolvedToken.any(Char::isWhitespace)) {
      throw InvalidCloudMemosTokenException()
    }

    api.verify(url, resolvedToken)
    val encrypted = withContext(Dispatchers.IO) { cipher.encrypt(resolvedToken) }
    context.cloudMemosStore.edit { preferences ->
      preferences[baseUrlKey] = url.toString().trimEnd('/')
      preferences[encryptedTokenKey] = encrypted
    }
  }

  suspend fun disconnect() {
    context.cloudMemosStore.edit { preferences ->
      preferences.remove(baseUrlKey)
      preferences.remove(encryptedTokenKey)
    }
    withContext(Dispatchers.IO) { cipher.deleteKey() }
  }

  suspend fun createMemo(
      content: String,
      visibility: CloudMemoVisibility = CloudMemoVisibility.Private,
  ): Result<String> = runCatchingCancellable {
    val (baseUrl, token) = readCredentialsOrNull() ?: throw CloudMemosNotConfiguredException()
    api.createMemo(normalizeCloudMemosUrl(baseUrl), token, content, visibility)
  }

  suspend fun listMemos(
      query: String? = null,
      tag: String? = null,
      cursor: String? = null,
      limit: Int = 20,
  ): Result<CloudMemoPage> = runCatchingCancellable {
    val (baseUrl, token) = readCredentialsOrNull() ?: throw CloudMemosNotConfiguredException()
    api.listMemos(
        normalizeCloudMemosUrl(baseUrl),
        token,
        query = query,
        tag = tag,
        cursor = cursor,
        limit = limit,
    )
  }

  suspend fun updateMemoState(
      memoId: String,
      version: Long,
      state: CloudMemoState,
  ): Result<CloudMemo> = runCatchingCancellable {
    val (baseUrl, token) = readCredentialsOrNull() ?: throw CloudMemosNotConfiguredException()
    api.updateMemoState(
        normalizeCloudMemosUrl(baseUrl),
        token,
        memoId = memoId,
        version = version,
        state = state,
    )
  }

  suspend fun deleteMemo(memoId: String): Result<Unit> = runCatchingCancellable {
    val (baseUrl, token) = readCredentialsOrNull() ?: throw CloudMemosNotConfiguredException()
    api.deleteMemo(normalizeCloudMemosUrl(baseUrl), token, memoId)
  }

  private suspend fun readCredentialsOrNull(): Pair<String, String>? {
    val preferences = context.cloudMemosStore.data.first()
    val baseUrl = preferences[baseUrlKey]?.takeIf(String::isNotBlank) ?: return null
    val encrypted = preferences[encryptedTokenKey]?.takeIf(String::isNotBlank) ?: return null
    val token =
        runCatching { withContext(Dispatchers.IO) { cipher.decrypt(encrypted) } }.getOrNull()
            ?: return null
    return baseUrl to token
  }
}

internal fun storedTokenForCloudMemosUrl(
    requestedUrl: HttpUrl,
    storedCredentials: Pair<String, String>?,
): String? {
  val (storedUrl, storedToken) = storedCredentials ?: return null
  val normalizedStoredUrl = runCatching { normalizeCloudMemosUrl(storedUrl) }.getOrNull()
  return storedToken.takeIf { normalizedStoredUrl == requestedUrl }
}

private fun JsonObject.requiredString(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull
        ?: throw CloudMemosProtocolException("Cloud Memos response is missing $name")

private fun JsonObject.requiredLong(name: String): Long =
    get(name)?.jsonPrimitive?.longOrNull
        ?: throw CloudMemosProtocolException("Cloud Memos response is missing $name")

internal object CloudMemoDrafts {
  fun episode(episode: EpisodeEntity, podcastTitle: String?): String = buildString {
    append("## ")
    append(markdownLink(episode.title, episode.audioUrl))
    podcastTitle?.takeIf(String::isNotBlank)?.let {
      append("\n\nPodcast: ").append(markdownLiteral(it))
    }
    append("\n\n#xpod #podcast")
  }

  fun article(article: ArticleEntity, feedTitle: String?): String = buildString {
    append("## ")
    append(
        article.url?.takeIf(String::isNotBlank)?.let { markdownLink(article.title, it) }
            ?: markdownLiteral(article.title)
    )
    feedTitle?.takeIf(String::isNotBlank)?.let {
      append("\n\nSource: ").append(markdownLiteral(it))
    }
    article.author.takeIf(String::isNotBlank)?.let {
      append("\n\nAuthor: ").append(markdownLiteral(it))
    }
    append("\n\n#xpod #article")
  }

  private fun markdownLink(label: String, url: String): String =
      "[${markdownLiteral(label)}](<${url.replace(">", "%3E")}>)"

  private fun markdownLiteral(value: String): String = buildString {
    value.trim().forEach { character ->
      when {
        character == '\r' || character == '\n' -> append(' ')
        character in MARKDOWN_SPECIAL_CHARACTERS -> append('\\').append(character)
        else -> append(character)
      }
    }
  }

  private val MARKDOWN_SPECIAL_CHARACTERS =
      setOf(
          '\\',
          '`',
          '*',
          '_',
          '{',
          '}',
          '[',
          ']',
          '<',
          '>',
          '(',
          ')',
          '#',
          '+',
          '-',
          '.',
          '!',
          '|',
      )
}

private val CloudMemoVisibility.apiValue: String
  get() =
      when (this) {
        CloudMemoVisibility.Private -> "PRIVATE"
        CloudMemoVisibility.Members -> "MEMBERS"
        CloudMemoVisibility.Public -> "PUBLIC"
      }

private val CloudMemoState.apiValue: String
  get() =
      when (this) {
        CloudMemoState.Active -> "ACTIVE"
        CloudMemoState.Archived -> "ARCHIVED"
      }

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
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

private class CloudMemosCredentialCipher {
  fun encrypt(value: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return listOf(
            FORMAT_VERSION,
            encoder.encodeToString(cipher.iv),
            encoder.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))),
        )
        .joinToString(":")
  }

  fun decrypt(value: String): String {
    val parts = value.split(':')
    require(parts.size == 3 && parts[0] == FORMAT_VERSION) { "Unsupported credential format" }
    val decoder = Base64.getUrlDecoder()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
        Cipher.DECRYPT_MODE,
        key(),
        GCMParameterSpec(GCM_TAG_LENGTH_BITS, decoder.decode(parts[1])),
    )
    return String(cipher.doFinal(decoder.decode(parts[2])), StandardCharsets.UTF_8)
  }

  @Synchronized
  private fun key(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
      return it
    }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
      init(
          KeyGenParameterSpec.Builder(
                  KEY_ALIAS,
                  KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
              )
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build()
      )
      generateKey()
    }
  }

  @Synchronized
  fun deleteKey() {
    KeyStore.getInstance(ANDROID_KEY_STORE).apply {
      load(null)
      if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
    }
  }

  private companion object {
    const val ANDROID_KEY_STORE = "AndroidKeyStore"
    const val KEY_ALIAS = "app.xpod.cloud_memos.api_token.v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_LENGTH_BITS = 128
    const val FORMAT_VERSION = "v1"
  }
}
