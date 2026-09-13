package app.xpod.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XpodDatabaseMigrationTest {
  @get:Rule
  val helper =
      MigrationTestHelper(
          InstrumentationRegistry.getInstrumentation(),
          XpodDatabase::class.java,
      )

  @Test
  fun migrate2To3PreservesPlaybackDataAndCreatesCascadingReaderTables() {
    helper.createDatabase(TEST_DATABASE, 2).apply {
      execSQL(
          "INSERT INTO PodcastEntity (id, feedUrl, title, author, description, artworkUrl, lastRefreshEpochMs, lastError) VALUES ('podcast', 'https://example.com/feed.xml', 'Podcast', '', '', NULL, 0, NULL)"
      )
      execSQL(
          "INSERT INTO EpisodeEntity (id, podcastId, stableKey, title, description, audioUrl, publishedEpochMs, durationMs, artworkUrl, isPlayed, isFavorite, isNew, lastPlayedEpochMs) VALUES ('episode', 'podcast', 'stable-episode', 'Episode', '', 'https://example.com/episode.mp3', 123, 456, NULL, 1, 1, 1, 789)"
      )
      execSQL(
          "INSERT INTO PlaybackStateEntity (`key`, episodeId, positionMs, speed, updatedAtEpochMs) VALUES ('active', 'episode', 321, 1.25, 999)"
      )
      execSQL("INSERT INTO QueueItemEntity (episodeId, position) VALUES ('episode', 0)")
      close()
    }

    helper
        .runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            XpodDatabaseMigrations.MIGRATION_2_3,
        )
        .use { database ->
          database.query("SELECT COUNT(*) FROM PodcastEntity").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
          }
          database
              .query(
                  "SELECT isPlayed, isFavorite, isNew, lastPlayedEpochMs FROM EpisodeEntity WHERE id = 'episode'"
              )
              .use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(789L, cursor.getLong(3))
              }
          database
              .query(
                  "SELECT episodeId, positionMs, speed FROM PlaybackStateEntity WHERE `key` = 'active'"
              )
              .use { cursor ->
                cursor.moveToFirst()
                assertEquals("episode", cursor.getString(0))
                assertEquals(321L, cursor.getLong(1))
                assertEquals(1.25f, cursor.getFloat(2), 0f)
              }
          database.query("SELECT episodeId, position FROM QueueItemEntity").use { cursor ->
            cursor.moveToFirst()
            assertEquals("episode", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
          }
          database.query("SELECT COUNT(*) FROM ArticleFeedEntity").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
          }
          database.query("SELECT COUNT(*) FROM ArticleEntity").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
          }

          database.execSQL("PRAGMA foreign_keys=ON")
          database.execSQL(
              "INSERT INTO ArticleFeedEntity (id, feedUrl, title, author, description, artworkUrl, lastRefreshEpochMs, lastError) VALUES ('articles', 'https://example.com/articles.xml', 'Articles', '', '', NULL, 0, NULL)"
          )
          database.execSQL(
              "INSERT INTO ArticleEntity (id, feedId, stableKey, title, author, content, url, publishedEpochMs, artworkUrl, isRead, isFavorite) VALUES ('article', 'articles', 'stable-article', 'Article', '', 'Body', 'https://example.com/article', 123, NULL, 1, 1)"
          )
          database.execSQL("DELETE FROM ArticleFeedEntity WHERE id = 'articles'")
          database.query("SELECT COUNT(*) FROM ArticleEntity").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
          }
        }
  }

  @Test
  fun migrate3To4PreservesPodcastPlaybackAndCreatesLocalMusicTables() {
    helper.createDatabase(TEST_DATABASE, 3).apply {
      execSQL(
          "INSERT INTO PlaybackStateEntity (`key`, episodeId, positionMs, speed, updatedAtEpochMs) VALUES ('active', 'episode', 321, 1.25, 999)"
      )
      execSQL("INSERT INTO QueueItemEntity (episodeId, position) VALUES ('episode', 0)")
      close()
    }

    helper
        .runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            XpodDatabaseMigrations.MIGRATION_3_4,
        )
        .use { database ->
          database
              .query(
                  "SELECT `key`, episodeId, mediaType, positionMs, speed FROM PlaybackStateEntity"
              )
              .use { cursor ->
                cursor.moveToFirst()
                assertEquals(PlaybackMediaType.Podcast.name, cursor.getString(0))
                assertEquals("episode", cursor.getString(1))
                assertEquals(PlaybackMediaType.Podcast.name, cursor.getString(2))
                assertEquals(321L, cursor.getLong(3))
                assertEquals(1.25f, cursor.getFloat(4), 0f)
              }
          database.query("SELECT episodeId, mediaType, position FROM QueueItemEntity").use { cursor
            ->
            cursor.moveToFirst()
            assertEquals("episode", cursor.getString(0))
            assertEquals(PlaybackMediaType.Podcast.name, cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
          }
          database.query("SELECT COUNT(*) FROM LocalTrackEntity").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
          }
        }
  }

  @Test
  fun migrate4To5AddsRelativePathToLocalMusic() {
    helper.createDatabase(TEST_DATABASE, 4).apply {
      execSQL(
          "INSERT INTO LocalTrackEntity (id, documentUri, treeUri, title, artist, album, durationMs, modifiedEpochMs) VALUES ('local:track', 'content://provider/document/track', 'content://provider/tree/music', 'Track', 'Artist', 'Album', 1000, 2)"
      )
      close()
    }

    helper
        .runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            XpodDatabaseMigrations.MIGRATION_4_5,
        )
        .use { database ->
          database
              .query("SELECT relativePath FROM LocalTrackEntity WHERE id = 'local:track'")
              .use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
              }
        }
  }

  @Test
  fun migrate5To6CreatesLocalBookAndProgressTables() {
    helper.createDatabase(TEST_DATABASE, 5).close()

    helper
        .runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            XpodDatabaseMigrations.MIGRATION_5_6,
        )
        .use { database ->
          database.execSQL(
              "INSERT INTO LocalBookEntity (id, documentUri, treeUri, title, author, language, format, fileSizeBytes, modifiedEpochMs, relativePath, coverCachePath, addedEpochMs, lastOpenedEpochMs, isFavorite) VALUES ('book:id', 'content://provider/document/book', 'content://provider/tree/books', 'Book', 'Author', 'en', 'EPUB', 12, 34, '', NULL, 56, 0, 0)"
          )
          database.execSQL(
              "INSERT INTO BookProgressEntity (bookId, positionVersion, positionJson, sourceModifiedEpochMs, updatedEpochMs, readingSeconds) VALUES ('book:id', 1, '{\"kind\":\"epub\"}', 34, 78, 42)"
          )
          database.query("SELECT title, format FROM LocalBookEntity WHERE id = 'book:id'").use {
              cursor ->
            cursor.moveToFirst()
            assertEquals("Book", cursor.getString(0))
            assertEquals("EPUB", cursor.getString(1))
          }
          database
              .query(
                  "SELECT positionJson, readingSeconds FROM BookProgressEntity WHERE bookId = 'book:id'"
              )
              .use { cursor ->
                cursor.moveToFirst()
                assertEquals("{\"kind\":\"epub\"}", cursor.getString(0))
                assertEquals(42L, cursor.getLong(1))
              }
        }
  }

  private companion object {
    const val TEST_DATABASE = "xpod-migration-test"
  }
}
