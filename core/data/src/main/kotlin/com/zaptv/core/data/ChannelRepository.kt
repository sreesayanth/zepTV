package com.zaptv.core.data

import com.zaptv.core.database.ChannelDao
import com.zaptv.core.database.FavouriteDao
import com.zaptv.core.database.FavouriteEntity
import com.zaptv.core.model.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val favouriteDao: FavouriteDao,
) {
    /** All channels, ordered by channel number. */
    val channels: Flow<List<Channel>> =
        channelDao.observeLineup().map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()

    val favourites: Flow<Set<String>> =
        favouriteDao.observeKeys().map { it.toSet() }.distinctUntilChanged()

    suspend fun setFavourite(channelKey: String, favourite: Boolean) {
        if (favourite) favouriteDao.add(FavouriteEntity(channelKey, System.currentTimeMillis()))
        else favouriteDao.remove(channelKey)
    }
}
