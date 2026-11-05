package com.zaptv.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [PlaylistEntity::class, ChannelEntity::class, StreamSourceEntity::class, FavouriteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ZapDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favouriteDao(): FavouriteDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ZapDatabase =
        Room.databaseBuilder(context, ZapDatabase::class.java, "zaptv.db")
            // Channel data can always be downloaded again; favourites need a real migration once released.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun channelDao(db: ZapDatabase): ChannelDao = db.channelDao()
    @Provides fun playlistDao(db: ZapDatabase): PlaylistDao = db.playlistDao()
    @Provides fun favouriteDao(db: ZapDatabase): FavouriteDao = db.favouriteDao()
}
