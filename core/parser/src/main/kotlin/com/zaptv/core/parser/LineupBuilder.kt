package com.zaptv.core.parser

import com.zaptv.core.model.Category
import com.zaptv.core.model.Channel
import com.zaptv.core.model.StreamSource

/**
 * Turns playlist entries into a numbered lineup:
 * - entries for the same channel are merged, and their URLs become failover sources;
 * - channels keep the number they had last time (from [previousNumbers]);
 * - new channels get the next free number in their category block (News 101+, Devotional 501+...).
 */
object LineupBuilder {

    private const val BLOCK_SIZE = 99
    private const val OVERFLOW_START = 900

    fun build(
        playlistId: String,
        entries: List<M3uEntry>,
        previousNumbers: Map<String, Int> = emptyMap(),
    ): List<Channel> {
        val merged = LinkedHashMap<String, MutableList<M3uEntry>>()
        for (entry in entries) {
            if (entry.url.isBlank()) continue
            merged.getOrPut(channelKey(playlistId, entry)) { ArrayList() } += entry
        }

        val used = HashSet<Int>()
        val numbers = HashMap<String, Int>()
        // Keep previous numbers first, so a new channel can never take an existing one's number.
        for (key in merged.keys) {
            val previous = previousNumbers[key] ?: continue
            if (previous > 0 && used.add(previous)) numbers[key] = previous
        }

        val channels = ArrayList<Channel>(merged.size)
        for ((key, group) in merged) {
            val first = group.first()
            val category = Category.fromGroupTitle(first.groupTitle)
            val number = numbers[key] ?: nextFreeNumber(category, used).also { used += it }
            val sources = group
                .distinctBy { it.url }
                .sortedByDescending { qualityRank(it.quality) }
                .map { StreamSource(url = it.url, userAgent = it.userAgent, referrer = it.referrer, quality = it.quality) }
            channels += Channel(
                key = key,
                number = number,
                name = first.title,
                category = category,
                groupTitle = first.groupTitle.orEmpty(),
                logoUrl = group.firstNotNullOfOrNull { it.logo },
                tvgId = first.tvgId,
                quality = sources.first().quality,
                labels = group.flatMap { it.labels }.distinct(),
                sources = sources,
            )
        }
        return channels.sortedBy { it.number }
    }

    /** Same channel = same tvg-id, or the same name when there is no tvg-id. */
    fun channelKey(playlistId: String, entry: M3uEntry): String {
        val identity = entry.tvgId?.lowercase() ?: normalise(entry.title)
        return "$playlistId:$identity"
    }

    private fun normalise(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { name.lowercase() }

    private fun nextFreeNumber(category: Category, used: Set<Int>): Int {
        val start = category.numberBase + 1
        for (n in start..category.numberBase + BLOCK_SIZE) if (n !in used) return n
        var n = OVERFLOW_START
        while (n in used) n++
        return n
    }

    private fun qualityRank(quality: String?): Int = quality?.dropLast(1)?.toIntOrNull() ?: 0
}
