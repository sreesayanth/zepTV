package com.zaptv.core.model

/**
 * A channel list the app knows about out of the box. Only playlist URLs are stored, never stream links.
 *
 * Each preset owns a block of channel numbers, `numberBase + 1` to `numberBase + capacity - 1`, so
 * turning languages on and off never renumbers other channels. Inside its block a preset uses the
 * usual category pattern: News from `numberBase + 101`, Devotional from `numberBase + 501` and so on.
 */
data class PlaylistPreset(
    val id: String,
    /** English name, shown in lists. */
    val name: String,
    /** Name in the language's own script, shown next to [name]. Empty for English. */
    val nativeName: String,
    val url: String,
    val numberBase: Int,
    val capacity: Int = BLOCK,
    /** Keep only channels from this country (lower-case ISO code, from the tvg-id). Null keeps everything. */
    val country: String? = null,
) {
    companion object {
        const val BLOCK = 1000
    }
}

/**
 * The built-in channel lists, from the community-maintained iptv-org project.
 *
 * Order matters: the position decides the number block, and a channel that appears in several lists
 * is shown only once, in the list with the lowest numbers. **Never reorder or reuse ids**: that would
 * renumber channels and lose favourites. Add new presets with a new, unused block.
 */
object PlaylistPresets {
    private const val BASE = "https://iptv-org.github.io/iptv/languages"

    val Malayalam = PlaylistPreset("iptv-org-mal", "Malayalam", "മലയാളം", "$BASE/mal.m3u", numberBase = 0)
    val EnglishIndia = PlaylistPreset("iptv-org-eng-in", "English (India)", "", "$BASE/eng.m3u", numberBase = 1000, country = "in")
    val Tamil = PlaylistPreset("iptv-org-tam", "Tamil", "தமிழ்", "$BASE/tam.m3u", numberBase = 2000)
    val Hindi = PlaylistPreset("iptv-org-hin", "Hindi", "हिन्दी", "$BASE/hin.m3u", numberBase = 3000)
    val Telugu = PlaylistPreset("iptv-org-tel", "Telugu", "తెలుగు", "$BASE/tel.m3u", numberBase = 4000)
    val Kannada = PlaylistPreset("iptv-org-kan", "Kannada", "ಕನ್ನಡ", "$BASE/kan.m3u", numberBase = 5000)
    val Bengali = PlaylistPreset("iptv-org-ben", "Bengali", "বাংলা", "$BASE/ben.m3u", numberBase = 6000)
    val Marathi = PlaylistPreset("iptv-org-mar", "Marathi", "मराठी", "$BASE/mar.m3u", numberBase = 7000)
    val Gujarati = PlaylistPreset("iptv-org-guj", "Gujarati", "ગુજરાતી", "$BASE/guj.m3u", numberBase = 8000)
    val Punjabi = PlaylistPreset("iptv-org-pan", "Punjabi", "ਪੰਜਾਬੀ", "$BASE/pan.m3u", numberBase = 9000)

    /** Every English-language channel in the world (about 3,000). Off by default. */
    val EnglishWorld = PlaylistPreset(
        "iptv-org-eng", "English (World)", "", "$BASE/eng.m3u", numberBase = 10_000, capacity = 10_000,
    )

    val all: List<PlaylistPreset> = listOf(
        Malayalam, EnglishIndia, Tamil, Hindi, Telugu, Kannada, Bengali, Marathi, Gujarati, Punjabi, EnglishWorld,
    )

    /** Turned on for a fresh install. Malayalam stays first so it loads (and plays) first. */
    val defaultIds: Set<String> = setOf(Malayalam.id, EnglishIndia.id, Tamil.id, Hindi.id)

    fun byId(id: String): PlaylistPreset? = all.firstOrNull { it.id == id }
}
