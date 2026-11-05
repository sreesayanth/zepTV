package com.zaptv.core.parser

import java.util.Locale

/** What a video's file name (or caption) says about it. Every field except [title] is optional. */
data class MediaName(
    /** Cleaned title, e.g. "Monsoon Letters". Never blank: falls back to the raw name. */
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** Last episode of a double episode such as S01E05-E06. */
    val episodeEnd: Int? = null,
    /** Vertical resolution: 2160, 1080, 720, 480, 360. */
    val quality: Int? = null,
    val codec: Codec? = null,
    val hdr: Hdr? = null,
    /** For example "WEB-DL", "BluRay", "HDRip". */
    val source: String? = null,
    /** ISO 639-1 codes in the order they appear, e.g. ["ml", "en"]. */
    val languages: List<String> = emptyList(),
    /** 1-based part of a split upload (Part 2, CD2, .002). */
    val part: Int? = null,
) {
    val isEpisode: Boolean get() = episode != null

    /** Key that groups episodes of one show: lower-case letters and digits of the title. */
    val seriesKey: String get() = normalizeKey(title)

    enum class Codec { H264, HEVC, AV1 }
    enum class Hdr { HDR, HDR10_PLUS, DOLBY_VISION }

    companion object {
        fun normalizeKey(title: String): String =
            title.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }
}

/**
 * Reads titles, seasons, episodes and quality tags out of the names Telegram videos carry, for
 * example `Monsoon.Letters.2024.1080p.WEB-DL.Mal.x265.mkv` or `Backwater Season S01E04 720p.mp4`.
 * Pure Kotlin and unit-tested; it never fails, it only finds less.
 */
object MediaNameParser {

    private val videoExtensions = setOf("mkv", "mp4", "avi", "ts", "m4v", "webm", "mov", "wmv", "flv", "m2ts", "mpg", "mpeg")

    // Episode markers, most specific first.
    private val sxe = Regex("""(?i)\bS(\d{1,2})\s?[ ._-]?\s?E(\d{1,3})(?:\s?-\s?E?(\d{1,3}))?\b""")
    private val nxn = Regex("""(?i)\b(\d{1,2})x(\d{2,3})\b""")
    private val seasonEpisodeWords = Regex("""(?i)\bSeason\s*(\d{1,2})\s*[,:-]?\s*Episode\s*(\d{1,3})\b""")
    private val seasonOnly = Regex("""(?i)\b(?:Season\s*(\d{1,2})|S(\d{1,2}))\b""")
    private val episodeOnly = Regex("""(?i)(?:\bEp(?:isode)?\s*[.:#-]?\s*|\bE)(\d{1,3})\b|എപ്പിസോഡ്\s*(\d{1,3})""")

    private val year = Regex("""\b(19[3-9]\d|20[0-4]\d)\b""")
    private val quality = Regex("""(?i)\b(2160|1440|1080|720|576|480|360|240)[pi]\b|\b(4K|UHD)\b""")
    private val codec = Regex("""(?i)\b(x265|h\.?265|hevc|x264|h\.?264|avc|av1)\b""")
    private val hdr = Regex("""(?i)\b(HDR10\+|HDR10Plus|HDR10|HDR|DoVi|DV|Dolby\s?Vision)(?!\w)""")
    private val source = Regex("""(?i)\b(WEB-?DL|WEB-?Rip|WEB|Blu-?Ray|BDRip|BRRip|HDRip|DVDRip|DVDScr|HDTV|HDTC|HDCAM|PreDVD|CAMRip|CAM|TRUE\s?WEB-?DL)\b""")
    private val part = Regex("""(?i)\b(?:Part|Pt|CD|Disc)\s*[.-]?\s*(\d{1,2})\b""")
    private val splitExtension = Regex("""\.(\d{3})$""")

    private val languageWords = linkedMapOf(
        "ml" to listOf("malayalam", "mal"),
        "ta" to listOf("tamil", "tam"),
        "te" to listOf("telugu", "tel"),
        "hi" to listOf("hindi", "hin"),
        "kn" to listOf("kannada", "kan"),
        "en" to listOf("english", "eng"),
        "ko" to listOf("korean", "kor"),
        "ja" to listOf("japanese", "jap", "jpn"),
    )

    // Uploader tags at the start or end: "@ChannelName", "www.site.com -", "[Group]".
    private val leadingJunk = Regex("""(?i)^(?:\s*(?:@\w+|www\.\S+|\[[^\]]*]|\{[^}]*}|[-–_|:]))+""")
    private val trailingJunk = Regex("""(?i)(?:\s*(?:@\w+|www\.\S+))+$""")
    // Phone and Telegram default names: "video_2025-01-03_21-14-08", "VID_20250103_211408".
    private val dateStamp = Regex("""\d{4}-\d{2}-\d{2}(?:[ _]\d{2}-\d{2}-\d{2})?|\d{8}_\d{6}""")
    private val genericNames = setOf("video", "vid", "movie", "file", "telegram", "document")
    private val bracketed = Regex("""\[[^\]]*]|\{[^}]*}""")
    private val spaces = Regex("""\s+""")

    /** Parses a file name. Pass the caption too: channels often write "Title (2024)" there. */
    fun parse(fileName: String, caption: String? = null): MediaName {
        val fromFile = parseOne(stripExtension(fileName))
        // The title is on the caption's first line; tags such as "1080p" often follow below it.
        val captionHead = caption?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.take(3)?.joinToString(" | ")?.takeIf { it.isNotEmpty() }
        val fromCaption = captionHead?.let { parseOne(it) }

        val splitPart = splitExtension.find(fileName.trim())?.groupValues?.get(1)?.toIntOrNull()

        // The caption title wins when it carries a year or an episode marker and the file name
        // does not: "Monsoon Letters (2024)" beats "MonsoonLetters_final_v2".
        val fileIsGeneric = fromFile.title.lowercase(Locale.ROOT) in genericNames
        val captionIsBetter = fromCaption != null && fromCaption.title.length >= 2 &&
            (fileIsGeneric || (fromCaption.year != null && fromFile.year == null) ||
                (fromCaption.isEpisode && !fromFile.isEpisode))
        val base = if (captionIsBetter) fromCaption!! else fromFile
        val other = if (captionIsBetter) fromFile else fromCaption

        return base.copy(
            year = base.year ?: other?.year,
            season = base.season ?: other?.season,
            episode = base.episode ?: other?.episode,
            episodeEnd = base.episodeEnd ?: other?.episodeEnd,
            quality = base.quality ?: other?.quality,
            codec = base.codec ?: other?.codec,
            hdr = base.hdr ?: other?.hdr,
            source = base.source ?: other?.source,
            languages = (base.languages + (other?.languages ?: emptyList())).distinct(),
            part = base.part ?: splitPart ?: other?.part,
        )
    }

    fun isVideoFileName(fileName: String): Boolean {
        val name = fileName.lowercase(Locale.ROOT).replace(splitExtension, "")
        return name.substringAfterLast('.', "") in videoExtensions
    }

    private fun stripExtension(name: String): String {
        var n = name.trim().replace(splitExtension, "")
        val ext = n.substringAfterLast('.', "")
        if (ext.lowercase(Locale.ROOT) in videoExtensions) n = n.substringBeforeLast('.')
        return n
    }

    private fun parseOne(raw: String): MediaName {
        // Dots and underscores separate words in release names; keep "H.265" and "5.1" intact.
        val text = leadingJunk.replace(raw, "")
            .replace(dateStamp, " ")
            .replace(Regex("""(?i)h\.(26[45])"""), "h$1")
            .replace(Regex("""(?<!\d)(\d)\.(\d)(?!\d)"""), "$1·$2")
            .replace('_', ' ')
            .replace('.', ' ')
            .replace('·', '.')
            .let { spaces.replace(it, " ").trim() }

        // Every tag's start position; the title is whatever comes before the first one.
        val cuts = ArrayList<Int>()

        var season: Int? = null
        var episode: Int? = null
        var episodeEnd: Int? = null
        sxe.find(text)?.let {
            season = it.groupValues[1].toInt()
            episode = it.groupValues[2].toInt()
            episodeEnd = it.groupValues[3].toIntOrNull()
            cuts += it.range.first
        } ?: seasonEpisodeWords.find(text)?.let {
            season = it.groupValues[1].toInt()
            episode = it.groupValues[2].toInt()
            cuts += it.range.first
        } ?: nxn.find(text)?.takeIf { year.find(it.value) == null }?.let {
            season = it.groupValues[1].toInt()
            episode = it.groupValues[2].toInt()
            cuts += it.range.first
        } ?: run {
            seasonOnly.find(text)?.let {
                season = (it.groupValues[1].ifEmpty { it.groupValues[2] }).toInt()
                cuts += it.range.first
            }
            episodeOnly.find(text)?.let {
                episode = (it.groupValues[1].ifEmpty { it.groupValues[2] }).toInt()
                cuts += it.range.first
            }
        }

        val qualityMatch = quality.find(text)
        val q = qualityMatch?.let { m ->
            m.groupValues[1].toIntOrNull() ?: 2160
        }
        qualityMatch?.let { cuts += it.range.first }

        val codecMatch = codec.find(text)
        val c = codecMatch?.value?.lowercase(Locale.ROOT)?.let {
            when {
                "265" in it || it == "hevc" -> MediaName.Codec.HEVC
                it == "av1" -> MediaName.Codec.AV1
                else -> MediaName.Codec.H264
            }
        }
        codecMatch?.let { cuts += it.range.first }

        val hdrMatch = hdr.find(text)
        val h = hdrMatch?.value?.lowercase(Locale.ROOT)?.let {
            when {
                "+" in it || "plus" in it -> MediaName.Hdr.HDR10_PLUS
                it == "dv" || it == "dovi" || "dolby" in it -> MediaName.Hdr.DOLBY_VISION
                else -> MediaName.Hdr.HDR
            }
        }
        hdrMatch?.let { cuts += it.range.first }

        val sourceMatch = source.find(text)
        sourceMatch?.let { cuts += it.range.first }

        val partMatch = part.find(text)
        partMatch?.let { cuts += it.range.first }

        val languages = findLanguages(text)
        languages.firstOrNull()?.let { cuts += it.second }

        // A year is a tag only when something comes before it, so "2018" the film keeps its title.
        val yearMatches = year.findAll(text).filter { it.range.first > 0 }.toList()
        val firstCut = cuts.minOrNull() ?: text.length
        val yearMatch = yearMatches.lastOrNull { it.range.first <= firstCut } ?: yearMatches.firstOrNull()
        yearMatch?.let { cuts += it.range.first }

        val titleEnd = cuts.minOrNull() ?: text.length
        val title = cleanTitle(text.substring(0, titleEnd)).ifEmpty { cleanTitle(text) }.ifEmpty { raw.trim() }

        return MediaName(
            title = title,
            year = yearMatch?.value?.toInt(),
            season = season,
            episode = episode,
            episodeEnd = episodeEnd,
            quality = q,
            codec = c,
            hdr = h,
            source = sourceMatch?.value?.let(::normalizeSource),
            languages = languages.map { it.first },
            part = partMatch?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    /** Language codes with the position of their first mention. */
    private fun findLanguages(text: String): List<Pair<String, Int>> {
        val found = ArrayList<Pair<String, Int>>()
        for ((code, words) in languageWords) {
            val position = words.asSequence()
                .mapNotNull { Regex("""(?i)\b$it\b""").find(text)?.range?.first }
                .minOrNull()
            if (position != null && position > 0) found += code to position
        }
        return found.sortedBy { it.second }
    }

    private fun normalizeSource(value: String): String {
        val v = value.lowercase(Locale.ROOT).replace(" ", "")
        return when {
            v.startsWith("truewebdl") || v == "web-dl" || v == "webdl" -> "WEB-DL"
            v == "webrip" || v == "web-rip" -> "WEBRip"
            v == "web" -> "WEB"
            v.startsWith("blu") -> "BluRay"
            else -> value.uppercase(Locale.ROOT).replace("RIP", "Rip")
        }
    }

    private fun cleanTitle(segment: String): String {
        var t = leadingJunk.replace(segment, "")
        t = bracketed.replace(t, " ")
        t = t.replace("(", " ").replace(")", " ")
        t = trailingJunk.replace(t.trim(), "")
        t = spaces.replace(t, " ").trim().trim('-', '–', '|', ':', ',', ' ')
        return t
    }
}
