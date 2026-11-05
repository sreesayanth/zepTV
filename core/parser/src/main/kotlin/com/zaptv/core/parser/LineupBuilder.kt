package com.zaptv.core.parser

import com.zaptv.core.model.Category
import com.zaptv.core.model.Channel
import com.zaptv.core.model.StreamSource

/**
 * Turns playlist entries into a numbered lineup:
 * - entries for the same channel are merged, and their URLs become failover sources;
 * - channels keep the number they had last time (from [previousNumbers]);
 * - new channels get the next free number in their category block (News 101+, Devotional 501+...).
 *
 * Each playlist numbers inside its own range, starting at [numberBase] (Malayalam 0, Tamil 2000...),
 * so several playlists can be shown together without clashing numbers.
 */
object LineupBuilder {

    private const val BLOCK_SIZE = 99
    private const val OVERFLOW_START = 900

    /**
     * @param numberBase first number of this playlist's range; Tamil News starts at `2000 + 101`.
     * @param capacity size of the range. Channels that don't fit in their category block go to
     *   `numberBase + 900` and up, until the range is full; any further channels are left out.
     */
    fun build(
        playlistId: String,
        entries: List<M3uEntry>,
        previousNumbers: Map<String, Int> = emptyMap(),
        numberBase: Int = 0,
        capacity: Int = 1000,
    ): List<Channel> {
        val range = (numberBase + 1) until (numberBase + capacity)
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
            if (previous in range && used.add(previous)) numbers[key] = previous
        }

        val channels = ArrayList<Channel>(merged.size)
        for ((key, group) in merged) {
            val first = group.first()
            val category = Category.fromGroupTitle(first.groupTitle)
            val number = numbers[key] ?: nextFreeNumber(category, used, numberBase, range)?.also { used += it } ?: continue
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
                playlistId = playlistId,
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

    private fun nextFreeNumber(category: Category, used: Set<Int>, numberBase: Int, range: IntRange): Int? {
        val start = numberBase + category.numberBase + 1
        for (n in start..start + BLOCK_SIZE - 1) if (n in range && n !in used) return n
        var n = numberBase + OVERFLOW_START
        while (n in used) n++
        return n.takeIf { it in range }
    }

    /**
     * Joins the lineups of several playlists into one, ordered by number. A channel that is in more
     * than one playlist (same tvg-id) is kept only once, in the playlist with the lowest numbers.
     */
    fun combine(lineups: List<Channel>): List<Channel> {
        val seen = HashSet<String>()
        return lineups.sortedBy { it.number }.filter { channel ->
            val id = channel.tvgId?.lowercase() ?: return@filter true
            seen.add(id)
        }
    }

    private fun qualityRank(quality: String?): Int = quality?.dropLast(1)?.toIntOrNull() ?: 0
}
