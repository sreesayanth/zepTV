package com.zaptv.core.model

/** One way to play a channel. A channel can have several, tried in order. */
data class StreamSource(
    val url: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    val quality: String? = null,
)

/**
 * A channel in the lineup. [key] is stable across playlist refreshes, so favourites and
 * channel numbers survive when the playlist is downloaded again.
 */
data class Channel(
    val key: String,
    val number: Int,
    val name: String,
    val category: Category,
    val groupTitle: String,
    val logoUrl: String?,
    val tvgId: String?,
    val quality: String?,
    val labels: List<String>,
    val sources: List<StreamSource>,
    /** The [PlaylistPreset] (or other playlist) this channel came from. */
    val playlistId: String = "",
) {
    /** 720p and above. */
    val isHd: Boolean
        get() = (quality?.removeSuffix("p")?.toIntOrNull() ?: 0) >= 720

    /** Two characters for the placeholder logo, e.g. "Asianet News" -> "AN", "24 News" -> "24". */
    val monogram: String
        get() {
            val words = name.split(' ', '-', '.').filter { it.isNotBlank() }
            val first = words.firstOrNull() ?: return "?"
            if (first.all { it.isDigit() }) return first.take(3)
            val initials = words.take(2).map { it.first().uppercaseChar() }.joinToString("")
            return initials.ifEmpty { "?" }
        }

    /** A stable colour for the placeholder logo, in degrees. */
    val hue: Float
        get() = ((key.hashCode().toLong() and 0x7fffffff) % 360).toFloat()
}

/**
 * Cable-style channel blocks (see IMPLEMENTATION_PLAN.md, section 5.4).
 * Channels get numbers from [numberBase] + 1 upwards.
 */
enum class Category(val label: String, val numberBase: Int) {
    ENTERTAINMENT("Entertainment", 0),
    NEWS("News", 100),
    MOVIES("Movies", 200),
    MUSIC("Music", 300),
    KIDS("Kids", 400),
    DEVOTIONAL("Devotional", 500),
    EDUCATION("Education", 600),
    SPORTS("Sports", 700),
    OTHER("Other", 800);

    companion object {
        /**
         * Maps an M3U `group-title` such as "News", "Animation;Kids" or "Religious" to a category.
         * When a channel has several groups, the more specific one wins (Kids over Entertainment).
         */
        fun fromGroupTitle(groupTitle: String?): Category {
            val tokens = groupTitle.orEmpty().split(';', ',', '|').map { it.trim().lowercase() }
            fun has(vararg names: String) = tokens.any { it in names }
            return when {
                has("news") -> NEWS
                has("kids", "animation") -> KIDS
                has("religious", "devotional", "spiritual") -> DEVOTIONAL
                has("education", "legislative", "science") -> EDUCATION
                has("movies", "movie") -> MOVIES
                has("music") -> MUSIC
                has("sports", "sport") -> SPORTS
                has("entertainment", "general", "lifestyle", "culture", "documentary",
                    "comedy", "family", "series", "travel", "cooking") -> ENTERTAINMENT
                else -> OTHER
            }
        }
    }
}

/** Settings the user can change. Stored on the device only. */
data class UserSettings(
    val lastChannelKey: String? = null,
    val upIsNextChannel: Boolean = true,
    val coachSeen: Boolean = false,
    val statsVisible: Boolean = false,
    /** Ids of the [PlaylistPreset]s to show, e.g. Malayalam and Tamil. */
    val enabledPlaylists: Set<String> = PlaylistPresets.defaultIds,
)
