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
}
