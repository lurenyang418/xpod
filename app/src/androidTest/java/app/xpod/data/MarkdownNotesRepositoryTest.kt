package app.xpod.data

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownNotesRepositoryTest {
  private lateinit var database: XpodDatabase
  private lateinit var repository: LocalMarkdownNotesRepository
  private lateinit var context: android.content.Context
  private lateinit var exportDirectory: File

  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(context, XpodDatabase::class.java).build()
    repository = LocalMarkdownNotesRepository(database, context)
    exportDirectory = File(context.cacheDir, "markdown-notes-test")
    exportDirectory.mkdirs()
  }

  @After
  fun tearDown() {
    database.close()
    exportDirectory.deleteRecursively()
  }

  @Test
  fun saveKeepsModifiedTimestampMonotonic() = runBlocking {
    val id = repository.create(nowEpochMs = 100L)

    repository.save(id, title = "Title", content = "First", nowEpochMs = 100L)
    assertEquals(101L, repository.find(id)?.modifiedEpochMs)

    repository.save(id, title = "Title", content = "Second", nowEpochMs = 99L)
    assertEquals(102L, repository.find(id)?.modifiedEpochMs)

    repository.save(id, title = "Title", content = "Third", nowEpochMs = 200L)
    assertEquals(200L, repository.find(id)?.modifiedEpochMs)

    repository.save(id, title = "Title", content = "Third", nowEpochMs = 300L)
    assertEquals(200L, repository.find(id)?.modifiedEpochMs)
  }

  @Test
  fun saveReturnsFalseWhenNoteWasDeleted() = runBlocking {
    assertEquals(false, repository.save(404L, title = "Title", content = "Draft", nowEpochMs = 1L))
  }

  @Test
  fun documentThemePersistsAndUnspecifiedSavesPreserveIt() = runBlocking {
    val id = repository.create(100L, MarkdownThemeMode.Newsprint)
    assertEquals("Newsprint", repository.find(id)?.theme)

    repository.save(id, "Title", "Body", 101L, MarkdownThemeMode.Night)
    assertEquals("Night", repository.find(id)?.theme)

    repository.save(id, "Updated", "Body", 102L)
    assertEquals("Night", repository.find(id)?.theme)
  }

  @Test
  fun createPersistsCustomThemeSelection() = runBlocking {
    val selection =
        MarkdownThemeSelection(
            MarkdownThemeMode.Custom,
            customThemeId = "01234567-89ab-4def-8123-456789abcdef",
        )

    val id = repository.create(nowEpochMs = 100L, themeSelection = selection)

    assertEquals(selection.toStorageValue(), repository.find(id)?.theme)
  }

  @Test
  fun savePersistsCustomThemeSelection() = runBlocking {
    val selection =
        MarkdownThemeSelection(
            MarkdownThemeMode.Custom,
            customThemeId = "01234567-89ab-4def-8123-456789abcdef",
        )
    val id = repository.create(nowEpochMs = 100L)

    assertTrue(
        repository.save(
            id,
            title = "Custom theme note",
            content = "Body",
            nowEpochMs = 101L,
            themeSelection = selection,
        )
    )

    assertEquals(selection.toStorageValue(), repository.find(id)?.theme)
  }

  @Test
  fun ftsSearchMatchesTitleAndBodyAndDeleteRemovesTheIndexRow() = runBlocking {
    val titleMatch = repository.create(1L)
    repository.save(titleMatch, "Kotlin notes", "A body", 2L)
    val bodyMatch = repository.create(3L)
    repository.save(bodyMatch, "Other", "Compose body", 4L)

    assertEquals(listOf(titleMatch), repository.observe("Kotlin").first().map { it.id })
    assertEquals(listOf(bodyMatch), repository.observe("Compose").first().map { it.id })

    repository.delete(titleMatch)

    assertEquals(emptyList<LocalMarkdownNoteEntity>(), repository.observe("Kotlin").first())
  }

  @Test
  fun writeMarkdownToFileNormalizesExportLineEndings() = runBlocking {
    val target = File(exportDirectory, "note.md")
    val note =
        LocalMarkdownNoteEntity(
            id = 1L,
            title = "Note",
            content = "first\r\nsecond\r\n\n",
            createdEpochMs = 1L,
            modifiedEpochMs = 2L,
        )

    repository.writeMarkdownToFile(note, target)

    assertEquals("first\nsecond\n", target.readText())
  }

  @Test
  fun importMarkdownCreatesNoteFromSafDocument() = runBlocking {
    val sharedNotesDirectory = File(context.cacheDir, "shared-notes")
    sharedNotesDirectory.mkdirs()
    val source = File.createTempFile("markdown-import-", ".md", sharedNotesDirectory)
    try {
      source.writeText("\uFEFFIntro\n# Imported title\nBody")
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)

      val id = repository.importMarkdown(uri, nowEpochMs = 123L, untitledLabel = "Untitled note")
      val imported = repository.find(id)

      assertEquals("Imported title", imported?.title)
      assertEquals("Intro\n# Imported title\nBody", imported?.content)
      assertEquals(123L, imported?.createdEpochMs)
      assertEquals(123L, imported?.modifiedEpochMs)
    } finally {
      source.delete()
    }
  }

  @Test
  fun pdfExportProducesMultiplePagesForLongNotes() {
    val target = File(exportDirectory, "long-note.pdf")
    val note =
        LocalMarkdownNoteEntity(
            id = 1L,
            title = "Long note",
            content =
                (1..180).joinToString("\n\n") { index ->
                  "Paragraph $index: ${"Markdown pagination keeps each page readable. ".repeat(3)}"
                },
            createdEpochMs = 1L,
            modifiedEpochMs = 2L,
        )

    FileOutputStream(target).use { output ->
      MarkdownPdfExporter(context).write(note, MarkdownThemeMode.Newsprint, "Untitled note", output)
    }

    ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
      PdfRenderer(descriptor).use { renderer ->
        val header =
            target.inputStream().use { input ->
              val bytes = ByteArray(4)
              input.read(bytes)
              bytes.toString(Charsets.US_ASCII)
            }
        assertEquals("%PDF", header)
        assertTrue("Long Markdown should paginate", renderer.pageCount > 1)
      }
    }
  }

  @Test
  fun localImageAttachmentStaysPrivateAndIsIncludedInZipExport() = runBlocking {
    val noteId = repository.create(10L, MarkdownThemeMode.Newsprint)
    val customThemeId = "01234567-89ab-4def-8123-456789abcdef"
    val customTheme =
        MarkdownCustomTheme(
            id = customThemeId,
            name = "Test paper",
            spec =
                markdownThemeSpec(MarkdownThemeMode.Custom).copy(mode = MarkdownThemeMode.Custom),
        )
    val sharedDirectory = File(context.cacheDir, "shared-notes")
    sharedDirectory.mkdirs()
    val source = File.createTempFile("markdown-image-", ".png", sharedDirectory)
    val zipTarget = File.createTempFile("markdown-backup-", ".zip", sharedDirectory)
    try {
      val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
      FileOutputStream(source).use { output ->
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
      }
      bitmap.recycle()
      val sourceUri =
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
      val attachment = repository.attachImage(noteId, sourceUri)
      val privateImage = File(requireNotNull(attachment.fileUri.path))
      assertTrue(privateImage.isFile)
      assertEquals(source.nameWithoutExtension, attachment.altText)

      val markdown = "![image](${attachment.markdownReference})"
      repository.save(
          noteId,
          "Image note",
          markdown,
          11L,
          themeSelection = MarkdownThemeSelection(MarkdownThemeMode.Custom, customThemeId),
      )
      val note = requireNotNull(repository.find(noteId))
      val zipUri =
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipTarget)
      repository.exportZip(
          listOf(note),
          zipUri,
          exportedAtEpochMs = 12L,
          appVersion = "test",
          customThemes = listOf(customTheme),
      )

      ZipFile(zipTarget).use { zip ->
        val markdownEntry = requireNotNull(zip.getEntry("Image note.md"))
        val exportedMarkdown =
            zip.getInputStream(markdownEntry).bufferedReader().use { it.readText() }
        val attachmentPath = "attachments/note-$noteId/${privateImage.name}"
        assertTrue(exportedMarkdown.contains("($attachmentPath)"))
        val imageEntry = requireNotNull(zip.getEntry(attachmentPath))
        assertArrayEquals(source.readBytes(), zip.getInputStream(imageEntry).use { it.readBytes() })
        val manifest =
            zip.getInputStream(requireNotNull(zip.getEntry("manifest.json"))).bufferedReader().use {
              it.readText()
            }
        assertTrue(manifest.contains("\"attachmentCount\": 1"))
        assertTrue(manifest.contains("\"fileName\": \"Image note.md\""))
        assertTrue(manifest.contains("\"theme\": \"Custom:$customThemeId\""))
        assertTrue(manifest.contains("\"name\": \"Test paper\""))
      }

      repository.delete(noteId)
      assertTrue(!privateImage.exists())
    } finally {
      source.delete()
      zipTarget.delete()
    }
  }
}
