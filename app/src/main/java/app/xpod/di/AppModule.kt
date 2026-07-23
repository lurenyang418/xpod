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
          .addMigrations(
              XpodDatabaseMigrations.MIGRATION_1_2,
              XpodDatabaseMigrations.MIGRATION_2_3,
          )
          .build()

  @Provides
  @Singleton
  fun httpClient(): OkHttpClient =
      OkHttpClient.Builder()
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .followRedirects(true)
          .followSslRedirects(false)
          .build()
}
