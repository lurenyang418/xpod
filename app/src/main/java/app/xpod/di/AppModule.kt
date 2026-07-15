package app.xpod.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.xpod.data.XpodDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  @Provides @Singleton fun clock(): Clock = Clock.systemUTC()

  @Provides
  @Singleton
  fun database(@ApplicationContext context: Context): XpodDatabase =
      Room.databaseBuilder(context, XpodDatabase::class.java, "xpod.db")
          .addMigrations(MIGRATION_1_2)
          .build()

  @Provides
  @Singleton
  fun httpClient(): OkHttpClient =
      OkHttpClient.Builder()
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .followRedirects(true)
          .followSslRedirects(true)
          .build()

  private val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE EpisodeEntity ADD COLUMN isNew INTEGER NOT NULL DEFAULT 0")
          db.execSQL(
              "ALTER TABLE EpisodeEntity ADD COLUMN lastPlayedEpochMs INTEGER NOT NULL DEFAULT 0")
        }
      }
}
