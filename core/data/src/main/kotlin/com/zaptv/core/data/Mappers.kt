package com.zaptv.core.data

import com.zaptv.core.database.ChannelEntity
import com.zaptv.core.database.ChannelWithSources
import com.zaptv.core.database.StreamSourceEntity
import com.zaptv.core.model.Category
import com.zaptv.core.model.Channel
import com.zaptv.core.model.StreamSource

internal fun Channel.toEntity(playlistId: String) = ChannelEntity(
    key = key,
    playlistId = playlistId,
    number = number,
    name = name,
    category = category.name,
    groupTitle = groupTitle,
    logoUrl = logoUrl,
    tvgId = tvgId,
    quality = quality,
    labels = labels.joinToString("|"),
)

internal fun Channel.sourceEntities() = sources.mapIndexed { index, source ->
    StreamSourceEntity(
        channelKey = key,
        url = source.url,
        userAgent = source.userAgent,
        referrer = source.referrer,
        quality = source.quality,
        priority = index,
    )
}

internal fun ChannelWithSources.toModel() = Channel(
    key = channel.key,
    number = channel.number,
    name = channel.name,
    category = runCatching { Category.valueOf(channel.category) }.getOrDefault(Category.OTHER),
    groupTitle = channel.groupTitle,
    logoUrl = channel.logoUrl,
    tvgId = channel.tvgId,
    quality = channel.quality,
    labels = channel.labels.split('|').filter { it.isNotBlank() },
    sources = sources.sortedBy { it.priority }.map { StreamSource(it.url, it.userAgent, it.referrer, it.quality) },
    playlistId = channel.playlistId,
)
