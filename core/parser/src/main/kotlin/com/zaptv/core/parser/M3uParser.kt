package com.zaptv.core.parser

import java.io.Reader

/** One channel line from an M3U playlist, with the options that came with it. */
data class M3uEntry(
    /** Display name with the "(576p)" and "[Not 24/7]" markers removed. */
    val title: String,
    val rawTitle: String,
    val url: String,
    /** `#EXTINF` attributes with lower-case keys, e.g. `tvg-id`, `tvg-logo`, `group-title`. */
    val attributes: Map<String, String>,
    val userAgent: String?,
    val referrer: String?,
    /** For example "1080p", taken from the title. */
    val quality: String?,
    /** Markers from the title such as "Not 24/7" or "Geo-blocked". */
    val labels: List<String>,
    /** From `#EXTGRP`, used when there is no `group-title`. */
    val extGroup: String? = null,
) {
    val tvgId: String? get() = attributes["tvg-id"]?.takeIf { it.isNotBlank() }
    val logo: String? get() = attributes["tvg-logo"]?.takeIf { it.isNotBlank() }
    val groupTitle: String? get() = attributes["group-title"]?.takeIf { it.isNotBlank() } ?: extGroup
    val channelNumber: Int? get() = attributes["tvg-chno"]?.trim()?.toIntOrNull()

    /**
     * The channel's country, from an iptv-org style tvg-id such as `NDTV24x7.in@SD` → `in`.
     * Null when the tvg-id doesn't follow that pattern.
     */
    val countryCode: String?
        get() {
            val id = tvgId?.substringBefore('@') ?: return null
            val code = id.substringAfterLast('.', missingDelimiterValue = "")
            return code.lowercase().takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }
        }
}

data class M3uPlaylist(
    val headerAttributes: Map<String, String>,
    val entries: List<M3uEntry>,
) {
    /** XMLTV guide address from the `#EXTM3U` header, if the playlist names one. */
    val epgUrl: String?
        get() = (headerAttributes["url-tvg"] ?: headerAttributes["x-tvg-url"])?.takeIf { it.isNotBlank() }
}

/**
 * Line-by-line M3U/M3U8 playlist parser. It never holds more than one entry's lines at a
 * time, so 50k-entry playlists are fine, and it skips broken entries instead of failing.
 */
object M3uParser {

    private val attributeRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")
    private val labelRegex = Regex("""\s*\[([^\]]+)]""")
    private val qualityRegex = Regex("""\s*\((\d{3,4}[pi])\)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): M3uPlaylist = parse(text.lineSequence())

    fun parse(reader: Reader): M3uPlaylist = reader.buffered().useLines { parse(it) }

    fun parse(lines: Sequence<String>): M3uPlaylist {
        var header = emptyMap<String, String>()
        val entries = ArrayList<M3uEntry>()

        var pendingAttributes: Map<String, String>? = null
        var pendingTitle = ""
        var userAgent: String? = null
        var referrer: String? = null
        var extGroup: String? = null

        fun reset() {
            pendingAttributes = null
            pendingTitle = ""
            userAgent = null
            referrer = null
            extGroup = null
        }

        for (raw in lines) {
            val line = raw.trim().removePrefix("﻿").trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) ->
                    header = parseAttributes(line.substring(7))

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    reset()
                    val body = line.substringAfter(':', "")
                    val split = indexOfTitleComma(body)
                    if (split >= 0) {
                        pendingAttributes = parseAttributes(body.substring(0, split))
                        pendingTitle = body.substring(split + 1).trim()
                    } else {
                        pendingAttributes = parseAttributes(body)
                        pendingTitle = ""
                    }
                }

                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val option = line.substringAfter(':', "")
                    val key = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim().takeIf { it.isNotEmpty() }
                    when (key) {
                        "http-user-agent" -> userAgent = value
                        "http-referrer", "http-referer" -> referrer = value
                    }
                }

                line.startsWith("#EXTGRP", ignoreCase = true) ->
                    extGroup = line.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }

                line.startsWith("#") -> Unit // #KODIPROP, #EXT-X-..., comments

                else -> {
                    val attributes = pendingAttributes
                    if (attributes != null) {
                        entries += buildEntry(line, attributes, pendingTitle, userAgent, referrer, extGroup)
                    }
                    reset()
                }
            }
        }
        return M3uPlaylist(header, entries)
    }

    private fun buildEntry(
        urlLine: String,
        attributes: Map<String, String>,
        rawTitle: String,
        userAgent: String?,
        referrer: String?,
        extGroup: String?,
    ): M3uEntry {
        // Kodi-style headers after a pipe: http://host/stream.m3u8|User-Agent=abc&Referer=xyz
        var url = urlLine
        var ua = userAgent
        var ref = referrer
        val pipe = urlLine.indexOf('|')
        if (pipe > 0) {
            url = urlLine.substring(0, pipe).trim()
            urlLine.substring(pipe + 1).split('&').forEach { pair ->
                val name = pair.substringBefore('=').trim().lowercase()
                val value = pair.substringAfter('=', "").trim().takeIf { it.isNotEmpty() }
                when (name) {
                    "user-agent" -> ua = ua ?: value
                    "referer", "referrer" -> ref = ref ?: value
                }
            }
        }

        val name = rawTitle.ifBlank { attributes["tvg-name"].orEmpty() }
        val labels = labelRegex.findAll(name).map { it.groupValues[1].trim() }.toList()
        val quality = qualityRegex.find(name)?.groupValues?.get(1)?.lowercase()
        val title = name.replace(labelRegex, "").replace(qualityRegex, "").trim()
            .ifEmpty { url.substringAfterLast('/').substringBefore('?') }

        return M3uEntry(
            title = title,
            rawTitle = rawTitle,
            url = url,
            attributes = attributes,
            userAgent = ua ?: attributes["user-agent"] ?: attributes["http-user-agent"],
            referrer = ref ?: attributes["referrer"] ?: attributes["http-referrer"],
            quality = quality,
            labels = labels,
            extGroup = extGroup,
        )
    }

    /** The title starts after the first comma that is not inside a quoted attribute value. */
    private fun indexOfTitleComma(body: String): Int {
        var inQuotes = false
        for (i in body.indices) {
            when (body[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return i
            }
        }
        return -1
    }

    private fun parseAttributes(text: String): Map<String, String> =
        attributeRegex.findAll(text).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
}
