package app.xpod.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.xpod.R
import app.xpod.data.ArticleEntity
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticleReaderScreen(
    article: ArticleEntity,
    feedTitle: String?,
    setRead: (String, Boolean) -> Unit,
    toggleFavorite: (String) -> Unit,
    onBack: () -> Unit,
) {
  val originalUrl = remember(article.url) { preferHttps(article.url) }
  var showOriginal by rememberSaveable(article.id) { mutableStateOf(false) }
  if (showOriginal && originalUrl != null) {
    OriginalArticleScreen(
        url = originalUrl,
        title = article.title,
        onBack = { showOriginal = false },
    )
    return
  }
  val parser = remember { ArticleContentParser() }
  val document by
      produceState<ArticleDocument?>(null, article.content, originalUrl) {
        value = withContext(Dispatchers.Default) { parser.parse(article.content, originalUrl) }
      }
  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.article)) },
            navigationIcon = {
              IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
              }
            },
            actions = {
              IconButton(onClick = { setRead(article.id, !article.isRead) }) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription =
                        stringResource(
                            if (article.isRead) R.string.mark_unread else R.string.mark_read
                        ),
                    tint =
                        if (article.isRead) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(onClick = { toggleFavorite(article.id) }) {
                Icon(
                    imageVector =
                        if (article.isFavorite) Icons.Filled.Favorite
                        else Icons.Filled.FavoriteBorder,
                    contentDescription =
                        stringResource(
                            if (article.isFavorite) R.string.remove_favorite else R.string.favorite
                        ),
                    tint =
                        if (article.isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              originalUrl?.let {
                IconButton(onClick = { showOriginal = true }) {
                  Icon(
                      Icons.Filled.Language,
                      stringResource(R.string.open_original),
                  )
                }
              }
            },
        )
      },
  ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      item(key = "article-header") {
        ArticlePageWidth {
          ArticleHeader(
              article = article,
              feedTitle = feedTitle,
              showArtwork = document?.hasImage == false,
          )
        }
      }
      when {
        document == null -> {
          item(key = "loading-content") {
            ArticlePageWidth { LinearProgressIndicator(Modifier.fillMaxWidth()) }
          }
        }
        document?.blocks?.isEmpty() == true -> {
          item(key = "empty-content") {
            ArticlePageWidth {
              EmptyArticleContent(
                  canOpenOriginal = originalUrl != null,
                  openOriginal = { showOriginal = true },
              )
            }
          }
        }
        else -> {
          items(document?.blocks.orEmpty()) { block ->
            ArticlePageWidth { ArticleBlockContent(block) }
          }
        }
      }
    }
  }
}

private fun preferHttps(url: String?): String? = url?.let {
  if (it.startsWith("http://", ignoreCase = true)) "https://${it.drop(7)}" else it
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OriginalArticleScreen(url: String, title: String, onBack: () -> Unit) {
  val uriHandler = LocalUriHandler.current
  var webView by remember { mutableStateOf<WebView?>(null) }
  var progress by remember { mutableIntStateOf(0) }
  BackHandler {
    val view = webView
    if (view?.canGoBack() == true) view.goBack() else onBack()
  }
  DisposableEffect(Unit) {
    onDispose {
      webView?.apply {
        stopLoading()
        clearHistory()
        removeAllViews()
        destroy()
      }
    }
  }
  Scaffold(
      topBar = {
        Column {
          TopAppBar(
              title = {
                Text(
                    text = title,
                    maxLines = 1,
                )
              },
              navigationIcon = {
                IconButton(onClick = onBack) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_to_reader))
                }
              },
              actions = {
                IconButton(onClick = { uriHandler.openUri(url) }) {
                  Icon(
                      Icons.AutoMirrored.Filled.OpenInNew,
                      stringResource(R.string.open_in_browser),
                  )
                }
              },
          )
          if (progress in 0..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      },
  ) { padding ->
    AndroidView(
        factory = { context ->
          WebView(context).apply {
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              allowFileAccess = false
              allowContentAccess = false
              javaScriptCanOpenWindowsAutomatically = false
              setSupportMultipleWindows(false)
              mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webChromeClient =
                object : WebChromeClient() {
                  override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                  }
                }
            webViewClient =
                object : WebViewClient() {
                  override fun shouldOverrideUrlLoading(
                      view: WebView?,
                      request: WebResourceRequest,
                  ): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return false
                    runCatching { uriHandler.openUri(request.url.toString()) }
                    return true
                  }
                }
            webView = this
            loadUrl(url)
          }
        },
        update = { view -> if (view.url == null) view.loadUrl(url) },
        modifier = Modifier.fillMaxSize().padding(padding),
    )
  }
}

@Composable
private fun ArticleHeader(article: ArticleEntity, feedTitle: String?, showArtwork: Boolean) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
        text = article.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    val metadata =
        listOfNotNull(
                feedTitle?.takeIf { it.isNotBlank() },
                article.author.takeIf { it.isNotBlank() },
                article.publishedEpochMs.takeIf { it > 0 }?.let(::formatArticleDate),
            )
            .joinToString(" · ")
    if (metadata.isNotEmpty()) {
      Text(
          text = metadata,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (showArtwork) {
      article.artworkUrl?.let { artwork ->
        AsyncImage(
            model = artwork,
            contentDescription = article.title,
            modifier =
                Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Fit,
        )
      }
    }
    HorizontalDivider()
  }
}

@Composable
private fun EmptyArticleContent(canOpenOriginal: Boolean, openOriginal: () -> Unit) {
  Card {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
          text = stringResource(R.string.no_article_content),
          style = MaterialTheme.typography.bodyLarge,
      )
      if (canOpenOriginal) {
        TextButton(onClick = openOriginal) {
          Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
          Text(stringResource(R.string.open_original), Modifier.padding(start = 8.dp))
        }
      }
    }
  }
}

@Composable
private fun ArticlePageWidth(content: @Composable () -> Unit) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Box(modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()) { content() }
  }
}

@Composable
private fun ArticleBlockContent(block: ArticleBlock) {
  when (block) {
    is ArticleBlock.Paragraph ->
        SelectableArticleText(
            text = block.content,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 29.sp),
        )
    is ArticleBlock.Heading ->
        SelectableArticleText(
            text = block.content,
            style =
                when (block.level) {
                  1 -> MaterialTheme.typography.headlineSmall
                  2 -> MaterialTheme.typography.titleLarge
                  3 -> MaterialTheme.typography.titleMedium
                  else -> MaterialTheme.typography.titleSmall
                },
            fontWeight = FontWeight.SemiBold,
        )
    is ArticleBlock.Image -> ArticleImage(block)
    is ArticleBlock.Quote -> ArticleQuote(block)
    is ArticleBlock.Code -> ArticleCode(block)
    is ArticleBlock.ListItems -> ArticleList(block)
    is ArticleBlock.Table -> ArticleTable(block)
    is ArticleBlock.Embed -> ArticleEmbed(block)
    ArticleBlock.Divider -> HorizontalDivider()
  }
}

@Composable
private fun ArticleImage(image: ArticleBlock.Image) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    AsyncImage(
        model = image.url,
        contentDescription = image.description,
        modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit,
    )
    image.caption?.let { caption ->
      SelectableArticleText(
          text = caption,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ArticleQuote(quote: ArticleBlock.Quote) {
  Row(
      modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Box(
        Modifier.fillMaxHeight()
            .width(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary),
    )
    SelectableArticleText(
        text = quote.content,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontStyle = FontStyle.Italic,
    )
  }
}

@Composable
private fun ArticleCode(code: ArticleBlock.Code) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(12.dp),
  ) {
    SelectionContainer {
      Text(
          text = code.content,
          modifier = Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun ArticleList(list: ArticleBlock.ListItems) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    list.items.forEachIndexed { index, item ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (list.ordered) "${index + 1}." else "•",
            modifier = Modifier.widthIn(min = 20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        SelectableArticleText(
            text = item,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
        )
      }
    }
  }
}

@Composable
private fun ArticleTable(table: ArticleBlock.Table) {
  Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      tonalElevation = 1.dp,
  ) {
    Column(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
      table.rows.forEachIndexed { rowIndex, row ->
        Row {
          row.forEach { cell ->
            SelectableArticleText(
                text = cell,
                modifier = Modifier.widthIn(min = 140.dp, max = 260.dp).padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (rowIndex == 0) FontWeight.SemiBold else null,
            )
          }
        }
        if (rowIndex != table.rows.lastIndex) HorizontalDivider()
      }
    }
  }
}

@Composable
private fun ArticleEmbed(embed: ArticleBlock.Embed) {
  val uriHandler = LocalUriHandler.current
  Card(onClick = { uriHandler.openUri(embed.url) }) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
      Text(
          text = embed.title ?: stringResource(R.string.open_embedded_content),
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleSmall,
      )
    }
  }
}

@Composable
private fun SelectableArticleText(
    text: ArticleText,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
) {
  val annotated = articleAnnotatedString(text)
  SelectionContainer(modifier = modifier) {
    Text(
        text = annotated,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
    )
  }
}

@Composable
private fun articleAnnotatedString(text: ArticleText): AnnotatedString {
  val linkColor = MaterialTheme.colorScheme.primary
  val codeBackground = MaterialTheme.colorScheme.surfaceVariant
  return remember(text, linkColor, codeBackground) {
    buildAnnotatedString {
      text.runs.forEach { run ->
        val span =
            SpanStyle(
                fontWeight = if (ArticleTextStyle.Bold in run.styles) FontWeight.Bold else null,
                fontStyle = if (ArticleTextStyle.Italic in run.styles) FontStyle.Italic else null,
                fontFamily =
                    if (ArticleTextStyle.Code in run.styles) FontFamily.Monospace else null,
                background =
                    if (ArticleTextStyle.Code in run.styles) codeBackground else Color.Unspecified,
                textDecoration =
                    if (ArticleTextStyle.StrikeThrough in run.styles) TextDecoration.LineThrough
                    else null,
            )
        val appendRun = { withStyle(span) { append(run.text) } }
        run.url?.let { url ->
          withLink(
              LinkAnnotation.Url(
                  url = url,
                  styles =
                      TextLinkStyles(
                          style =
                              SpanStyle(
                                  color = linkColor,
                                  textDecoration = TextDecoration.Underline,
                              ),
                      ),
              ),
          ) {
            appendRun()
          }
        } ?: appendRun()
      }
    }
  }
}
