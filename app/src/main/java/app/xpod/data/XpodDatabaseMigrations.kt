package app.xpod.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object XpodDatabaseMigrations {
  val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE EpisodeEntity ADD COLUMN isNew INTEGER NOT NULL DEFAULT 0")
          db.execSQL(
              "ALTER TABLE EpisodeEntity ADD COLUMN lastPlayedEpochMs INTEGER NOT NULL DEFAULT 0"
          )
        }
      }

  val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS `ArticleFeedEntity` (`id` TEXT NOT NULL, `feedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `description` TEXT NOT NULL, `artworkUrl` TEXT, `lastRefreshEpochMs` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`id`))"
          )
          db.execSQL(
              "CREATE UNIQUE INDEX IF NOT EXISTS `index_ArticleFeedEntity_feedUrl` ON `ArticleFeedEntity` (`feedUrl`)"
          )
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS `ArticleEntity` (`id` TEXT NOT NULL, `feedId` TEXT NOT NULL, `stableKey` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `content` TEXT NOT NULL, `url` TEXT, `publishedEpochMs` INTEGER NOT NULL, `artworkUrl` TEXT, `isRead` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`feedId`) REFERENCES `ArticleFeedEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_ArticleEntity_feedId` ON `ArticleEntity` (`feedId`)"
          )
          db.execSQL(
              "CREATE UNIQUE INDEX IF NOT EXISTS `index_ArticleEntity_feedId_stableKey` ON `ArticleEntity` (`feedId`, `stableKey`)"
          )
        }
      }

  val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS `LocalTrackEntity` (`id` TEXT NOT NULL, `documentUri` TEXT NOT NULL, `treeUri` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `modifiedEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_LocalTrackEntity_treeUri` ON `LocalTrackEntity` (`treeUri`)"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_LocalTrackEntity_title` ON `LocalTrackEntity` (`title`)"
          )
          db.execSQL(
              "ALTER TABLE `PlaybackStateEntity` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'Podcast'"
          )
          db.execSQL("UPDATE `PlaybackStateEntity` SET `key` = 'Podcast' WHERE `key` = 'active'")
          db.execSQL(
              "ALTER TABLE `QueueItemEntity` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'Podcast'"
          )
          db.execSQL("DROP INDEX IF EXISTS `index_QueueItemEntity_position`")
          db.execSQL(
              "CREATE UNIQUE INDEX IF NOT EXISTS `index_QueueItemEntity_mediaType_position` ON `QueueItemEntity` (`mediaType`, `position`)"
          )
        }
      }

  val MIGRATION_4_5 =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
              "ALTER TABLE `LocalTrackEntity` ADD COLUMN `relativePath` TEXT NOT NULL DEFAULT ''"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_LocalTrackEntity_relativePath` ON `LocalTrackEntity` (`relativePath`)"
          )
        }
      }

  val MIGRATION_5_6 =
      object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS `LocalBookEntity` (`id` TEXT NOT NULL, `documentUri` TEXT NOT NULL, `treeUri` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `language` TEXT NOT NULL, `format` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `modifiedEpochMs` INTEGER NOT NULL, `relativePath` TEXT NOT NULL, `coverCachePath` TEXT, `addedEpochMs` INTEGER NOT NULL, `lastOpenedEpochMs` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, PRIMARY KEY(`id`))"
          )
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS `BookProgressEntity` (`bookId` TEXT NOT NULL, `positionVersion` INTEGER NOT NULL, `positionJson` TEXT NOT NULL, `sourceModifiedEpochMs` INTEGER NOT NULL, `updatedEpochMs` INTEGER NOT NULL, `readingSeconds` INTEGER NOT NULL, PRIMARY KEY(`bookId`))"
          )
          // The episode and article tables predate v6, so the ORDER BY-serving indices added
          // to their entities in this version must be created here as well.
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_EpisodeEntity_publishedEpochMs` ON `EpisodeEntity` (`publishedEpochMs`)"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_EpisodeEntity_podcastId_publishedEpochMs` ON `EpisodeEntity` (`podcastId`, `publishedEpochMs`)"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_ArticleEntity_publishedEpochMs` ON `ArticleEntity` (`publishedEpochMs`)"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_ArticleEntity_feedId_publishedEpochMs` ON `ArticleEntity` (`feedId`, `publishedEpochMs`)"
          )
        }
      }

  val MIGRATION_6_7 =
      object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS `LocalVideoEntity` (`id` TEXT NOT NULL, `documentUri` TEXT NOT NULL, `treeUri` TEXT NOT NULL, `title` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `modifiedEpochMs` INTEGER NOT NULL, `relativePath` TEXT NOT NULL, `lastPositionMs` INTEGER NOT NULL, `lastOpenedEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_LocalVideoEntity_treeUri` ON `LocalVideoEntity` (`treeUri`)"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_LocalVideoEntity_title` ON `LocalVideoEntity` (`title`)"
          )
          db.execSQL(
              "CREATE INDEX IF NOT EXISTS `index_LocalVideoEntity_relativePath` ON `LocalVideoEntity` (`relativePath`)"
          )
        }
      }
}
