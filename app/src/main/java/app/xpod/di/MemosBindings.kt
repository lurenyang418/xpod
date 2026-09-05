package app.xpod.di

import android.content.Context
import app.xpod.data.CloudMemosGateway
import app.xpod.data.CloudMemosRepository
import app.xpod.ui.MemosStrings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudMemosGatewayModule {
  @Binds
  @Singleton
  abstract fun bindCloudMemosGateway(impl: CloudMemosRepository): CloudMemosGateway
}

@Module
@InstallIn(SingletonComponent::class)
object MemosStringsModule {
  @Provides
  @Singleton
  fun memosStrings(@ApplicationContext context: Context): MemosStrings =
      MemosStrings { resId, formatArgs ->
        context.getString(resId, *formatArgs)
      }
}
