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
        LocalPlaylistEntity::class,
        LocalPlaylistVideoEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun localPlaylistDao(): LocalPlaylistDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_playlists` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_playlist_videos` (
                        `playlistId` TEXT NOT NULL,
                        `videoId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `channelName` TEXT NOT NULL,
                        `channelId` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `durationSeconds` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `videoId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `local_playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_local_playlist_videos_playlistId_position` 
                    ON `local_playlist_videos` (`playlistId`, `position`)
                    """.trimIndent()
                )
            }
        }
    }
}
