package com.selecttube.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChannelEntity::class,
        PlaylistEntity::class,
        WatchHistoryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `playlistId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `uploaderName` TEXT,
                        `videoCount` INTEGER NOT NULL,
                        `activationTimestamp` INTEGER,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watch_history` (
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `durationSeconds` INTEGER NOT NULL,
                        `lastPositionMs` INTEGER NOT NULL,
                        `watchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
