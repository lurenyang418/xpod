package app.xpod.ui.notes

import android.net.Uri as AndroidUri
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xpod.R
import app.xpod.data.MarkdownFontFamily
import app.xpod.data.MarkdownThemeSpec
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.NoOpImageTransformerImpl

@Composable
internal fun MarkdownPreview(
    content: String,
    themeSpec: MarkdownThemeSpec,
    attachments: Map<String, Uri>,
    modifier: Modifier = Modifier,
) {
  val spec = themeSpec
  val isSerif = spec.fontFamily == MarkdownFontFamily.Serif
  val documentFont = if (isSerif) FontFamily.Serif else FontFamily.Default
  val bodyTextStyle =
      MaterialTheme.typography.bodyLarge.copy(
          fontFamily = documentFont,
          fontSize = spec.bodyFontSizeSp.sp,
          lineHeight = (spec.bodyFontSizeSp * spec.lineHeightMultiplier).sp,
      )
  Surface(modifier = modifier, color = Color(spec.backgroundArgb)) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .widthIn(max = spec.contentWidthDp.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
    ) {
      Markdown(
          content = content.ifBlank { stringResource(R.string.notes_empty_content) },
          modifier = Modifier.fillMaxWidth(),
          colors =
              markdownColor(
                  text = Color(spec.textArgb),
                  codeBackground = Color(spec.codeBackgroundArgb),
                  inlineCodeBackground = Color(spec.codeBackgroundArgb),
                  dividerColor = Color(spec.tableBorderArgb),
                  tableBackground = Color(spec.quoteBackgroundArgb),
              ),
          typography =
              markdownTypography(
                  h1 =
                      MaterialTheme.typography.headlineMedium.copy(
                          fontFamily = documentFont,
                          fontSize = spec.headingFontSizeSp.sp,
                      ),
                  h2 =
                      MaterialTheme.typography.headlineSmall.copy(
                          fontFamily = documentFont,
                          fontSize = (spec.headingFontSizeSp * 0.8f).sp,
                      ),
                  h3 =
                      MaterialTheme.typography.titleLarge.copy(
                          fontFamily = documentFont,
                          fontSize = (spec.headingFontSizeSp * 0.67f).sp,
                      ),
                  text = bodyTextStyle,
                  code =
                      bodyTextStyle.copy(
                          color = Color(spec.codeTextArgb),
                          background = Color(spec.codeBackgroundArgb),
                          fontFamily = FontFamily.Monospace,
                      ),
                  inlineCode =
                      bodyTextStyle.copy(
                          color = Color(spec.codeTextArgb),
                          background = Color(spec.codeBackgroundArgb),
                          fontFamily = FontFamily.Monospace,
                      ),
                  quote = bodyTextStyle.copy(background = Color(spec.quoteBackgroundArgb)),
                  paragraph = bodyTextStyle,
                  ordered = bodyTextStyle,
                  bullet = bodyTextStyle,
                  list = bodyTextStyle,
                  textLink = TextLinkStyles(style = SpanStyle(color = Color(spec.linkArgb))),
              ),
          imageTransformer = remember(attachments) { NoteImageTransformer(attachments) },
      )
    }
  }
}

private class NoteImageTransformer(private val attachments: Map<String, Uri>) : ImageTransformer {
  private val fallback = NoOpImageTransformerImpl()

  @Composable
  override fun transform(link: String): ImageData? =
      when {
        AndroidUri.parse(link).scheme.equals("https", ignoreCase = true) -> {
          val painter =
              rememberAsyncImagePainter(
                  model =
                      ImageRequest.Builder(LocalPlatformContext.current)
                          .data(link)
                          .size(1280, 1280)
                          .build()
              )
          ImageData(painter = painter, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp))
        }
        attachments[link] != null -> {
          val painter =
              rememberAsyncImagePainter(
                  model =
                      ImageRequest.Builder(LocalPlatformContext.current)
                          .data(attachments.getValue(link))
                          .size(1280, 1280)
                          .build()
              )
          ImageData(painter = painter, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp))
        }
        else -> fallback.transform(link)
      }

  @Composable
  override fun intrinsicSize(painter: Painter): Size =
      if (painter is AsyncImagePainter) painter.intrinsicSize else fallback.intrinsicSize(painter)
}
