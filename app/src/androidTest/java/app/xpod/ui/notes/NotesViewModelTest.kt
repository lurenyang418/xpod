package app.xpod.ui.notes

import android.app.Instrumentation
import android.os.SystemClock
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.xpod.data.LocalMarkdownNotesRepository
import app.xpod.data.SettingsRepository
import app.xpod.data.XpodDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesViewModelTest {
  @Test
  fun openingAnotherNoteAfterMissingNoteLoadsRequestedNote() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val database = Room.inMemoryDatabaseBuilder(context, XpodDatabase::class.java).build()
    try {
      val repository = LocalMarkdownNotesRepository(database, context)
      val validNoteId = runBlocking { repository.create(1L) }
      val viewModel =
          NotesViewModel(
              repository = repository,
              settings = SettingsRepository(context),
              clock = Clock.fixed(Instant.ofEpochMilli(100L), ZoneOffset.UTC),
              context = context,
          )
      instrumentation.runOnMainSync {
        viewModel.openNote(404L)
        viewModel.openNote(validNoteId)
      }

      waitForEditor(viewModel, instrumentation)

      assertEquals(validNoteId, viewModel.editorState.value?.id)
    } finally {
      database.close()
    }
  }

  @Test
  fun contentIsSavedAfterDebounceAndFlushesLatestDraft() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val database = Room.inMemoryDatabaseBuilder(context, XpodDatabase::class.java).build()
    try {
      val repository = LocalMarkdownNotesRepository(database, context)
      val noteId = runBlocking { repository.create(1L) }
      val viewModel =
          NotesViewModel(
              repository = repository,
              settings = SettingsRepository(context),
              clock = Clock.fixed(Instant.ofEpochMilli(100L), ZoneOffset.UTC),
              context = context,
          )

      instrumentation.runOnMainSync { viewModel.openNote(noteId) }
      waitForEditor(viewModel, instrumentation)
      instrumentation.runOnMainSync { viewModel.setContent("draft") }
      SystemClock.sleep(100L)
      instrumentation.waitForIdleSync()
      assertEquals("", runBlocking { repository.find(noteId)?.content })

      instrumentation.runOnMainSync { viewModel.flushEditor() }
      waitForContent(repository, noteId, "draft", instrumentation)

      instrumentation.runOnMainSync { viewModel.setContent("debounced") }
      waitForContent(repository, noteId, "debounced", instrumentation, timeoutMs = 2_000L)
    } finally {
      database.close()
    }
  }

  private fun waitForEditor(viewModel: NotesViewModel, instrumentation: Instrumentation) {
    repeat(40) {
      if (viewModel.editorState.value != null) return
      SystemClock.sleep(50L)
      instrumentation.waitForIdleSync()
    }
    error("Editor did not load")
  }

  private fun waitForContent(
      repository: LocalMarkdownNotesRepository,
      noteId: Long,
      expected: String,
      instrumentation: Instrumentation,
      timeoutMs: Long = 1_000L,
  ) {
    val attempts = (timeoutMs / 50L).toInt()
    repeat(attempts) {
      if (runBlocking { repository.find(noteId)?.content } == expected) return
      SystemClock.sleep(50L)
      instrumentation.waitForIdleSync()
    }
    assertEquals(expected, runBlocking { repository.find(noteId)?.content })
  }
}
