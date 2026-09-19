package app.xpod.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownNotesRepositoryTest {
  private lateinit var database: XpodDatabase
  private lateinit var repository: LocalMarkdownNotesRepository
  private lateinit var exportDirectory: File

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
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
}
