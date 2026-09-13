package app.xpod.data

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.xpod.data.reader.EpubBookParser
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBooksRepositoryIntegrationTest {
  private lateinit var database: XpodDatabase
  private lateinit var settings: SettingsRepository

  @Before
  fun setUp() {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    database = Room.inMemoryDatabaseBuilder(targetContext, XpodDatabase::class.java).build()
    settings = SettingsRepository(targetContext)
    runBlocking { settings.setLocalBooksTreeUri(TREE_URI.toString()) }
  }

  @After
  fun tearDown() {
    if (this::settings.isInitialized) runBlocking { settings.setLocalBooksTreeUri(null) }
    if (this::database.isInitialized) database.close()
  }

  @Test
  fun refreshIndexesSafDocumentsAndPersistsProgressInRoom() = runBlocking {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val repository =
        LocalBooksRepository(
            context = targetContext,
            database = database,
            settings = settings,
            clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC),
            epubParser = EpubBookParser(File(targetContext.cacheDir, "local-books-epub-test")),
        )

    val result = repository.refresh()

    assertEquals(BookScanResult(bookCount = 1, failureCount = 0), result)
    val indexed = database.localBooks().all().single()
    assertEquals("Book", indexed.title)
    assertEquals("Shelf", indexed.relativePath)
    assertEquals(BookFormat.PDF.name, indexed.format)
    assertEquals(
        "test pdf bytes",
        repository.openInputStream(indexed.id).use { it.readBytes().decodeToString() },
    )
    repository.openFileDescriptor(indexed.id).use { descriptor ->
      assertTrue(descriptor.fileDescriptor.valid())
    }

    repository.recordProgress(
        bookId = indexed.id,
        positionVersion = 1,
        positionJson = "{\"kind\":\"pdf\",\"pageIndex\":1}",
        sourceModifiedEpochMs = indexed.modifiedEpochMs,
        updatedEpochMs = 2_000L,
    )
    val withProgress = database.localBooks().observeAllWithProgress().first().single()
    assertEquals("{\"kind\":\"pdf\",\"pageIndex\":1}", withProgress.positionJson)
    assertEquals(2_000L, withProgress.lastOpenedEpochMs)

    database
        .localBooks()
        .upsertAll(
            indexed
                .copy(addedEpochMs = 77L, lastOpenedEpochMs = 2_000L, isFavorite = true)
                .let(::listOf)
        )
    repository.refresh()
    val rescanned = database.localBooks().find(indexed.id)!!
    assertEquals(77L, rescanned.addedEpochMs)
    assertEquals(2_000L, rescanned.lastOpenedEpochMs)
    assertTrue(rescanned.isFavorite)
  }

  private companion object {
    // Served by the debug-only app.xpod.debug.BooksProvider fixture.
    const val AUTHORITY = "books.integration.test"
    const val ROOT_ID = "root"
    val TREE_URI: Uri = Uri.parse("content://$AUTHORITY/tree/$ROOT_ID")
  }
}
