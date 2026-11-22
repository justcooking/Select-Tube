package com.selecttube.app.di

import android.content.Context
import androidx.room.Room
import com.selecttube.app.data.db.AppDatabase
import com.selecttube.app.data.db.ChannelDao
import com.selecttube.app.data.db.LocalPlaylistDao
import com.selecttube.app.data.db.PlaylistDao
import com.selecttube.app.data.db.WatchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "selecttube.db",
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            )
            .build()

    @Provides
    fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideWatchHistoryDao(db: AppDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    fun provideLocalPlaylistDao(db: AppDatabase): LocalPlaylistDao = db.localPlaylistDao()
}
