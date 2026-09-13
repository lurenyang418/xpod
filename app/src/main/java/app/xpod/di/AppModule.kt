package app.xpod.di

import android.content.Context
import androidx.room.Room
import app.xpod.data.XpodDatabase
import app.xpod.data.XpodDatabaseMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  @Provides @Singleton fun clock(): Clock = Clock.systemUTC()

  @Provides
  @Singleton
  fun database(@ApplicationContext context: Context): XpodDatabase =
      Room.databaseBuilder(context, XpodDatabase::class.java, "xpod.db")
          .addMigrations(
              XpodDatabaseMigrations.MIGRATION_1_2,
              XpodDatabaseMigrations.MIGRATION_2_3,
              XpodDatabaseMigrations.MIGRATION_3_4,
              XpodDatabaseMigrations.MIGRATION_4_5,
              XpodDatabaseMigrations.MIGRATION_5_6,
          )
          .build()

  @Provides
  @Singleton
  fun httpClient(@ApplicationContext context: Context): OkHttpClient =
      buildHttpClient(File(context.cacheDir, "http_cache"))
}

internal fun buildHttpClient(cacheDir: File): OkHttpClient =
    OkHttpClient.Builder()
        .cache(Cache(cacheDir, 50L * 1024 * 1024))
        .connectionPool(ConnectionPool(8, 30, TimeUnit.SECONDS))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
