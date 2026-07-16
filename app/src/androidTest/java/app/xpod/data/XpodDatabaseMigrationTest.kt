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

  private companion object {
    const val TEST_DATABASE = "xpod-migration-test"
  }
}
