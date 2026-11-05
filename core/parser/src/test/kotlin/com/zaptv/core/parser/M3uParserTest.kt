package com.zaptv.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parses an iptv-org style entry`() {
        val playlist = M3uParser.parse(
            """
            #EXTM3U x-tvg-url="https://example.com/guide.xml"
            #EXTINF:-1 tvg-id="24News.in@SD" tvg-logo="https://example.com/24.png" group-title="News",24 News (576p)
            https://example.com/24news/index.m3u8
            """.trimIndent()
        )
        assertEquals("https://example.com/guide.xml", playlist.epgUrl)
        val entry = playlist.entries.single()
        assertEquals("24 News", entry.title)
        assertEquals("576p", entry.quality)
        assertEquals("24News.in@SD", entry.tvgId)
        assertEquals("News", entry.groupTitle)
        assertEquals("https://example.com/24.png", entry.logo)
        assertEquals("https://example.com/24news/index.m3u8", entry.url)
    }

    @Test
    fun `extracts labels and keeps the region in the name`() {
        val entry = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="AnandTV.uk@SD" group-title="General",Anand TV (United Kingdom) (720p) [Not 24/7]
            https://example.com/anand.m3u8
            """.trimIndent()
        ).entries.single()
        assertEquals("Anand TV (United Kingdom)", entry.title)
        assertEquals("720p", entry.quality)
        assertEquals(listOf("Not 24/7"), entry.labels)
    }

    @Test
    fun `reads VLC options for user agent and referrer`() {
        val entry = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="x",X TV
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 (Android)
            #EXTVLCOPT:http-referrer=https://example.com/
            https://example.com/x.m3u8
            """.trimIndent()
        ).entries.single()
        assertEquals("Mozilla/5.0 (Android)", entry.userAgent)
        assertEquals("https://example.com/", entry.referrer)
    }

    @Test
    fun `reads Kodi pipe headers`() {
        val entry = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,Y TV
            https://example.com/y.m3u8|User-Agent=ZapTV&Referer=https://y.example/
            """.trimIndent()
        ).entries.single()
        assertEquals("https://example.com/y.m3u8", entry.url)
        assertEquals("ZapTV", entry.userAgent)
        assertEquals("https://y.example/", entry.referrer)
    }

    @Test
    fun `handles commas inside quoted attributes, BOM and CRLF`() {
        val text = "﻿#EXTM3U\r\n#EXTINF:-1 group-title=\"News, Kerala\" tvg-name=\"A, B\",Name, with comma\r\nhttp://a/b.m3u8\r\n"
        val entry = M3uParser.parse(text).entries.single()
        assertEquals("News, Kerala", entry.groupTitle)
        assertEquals("Name, with comma", entry.title)
        assertEquals("http://a/b.m3u8", entry.url)
    }

    @Test
    fun `skips URLs without EXTINF and comment lines`() {
        val playlist = M3uParser.parse(
            """
            #EXTM3U
            http://orphan/stream.m3u8
            # a comment
            #KODIPROP:inputstream=inputstream.adaptive
            #EXTINF:-1,Good
            http://good/stream.m3u8
            """.trimIndent()
        )
        assertEquals(1, playlist.entries.size)
        assertEquals("Good", playlist.entries.single().title)
    }

    @Test
    fun `uses EXTGRP when group-title is missing`() {
        val entry = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,Z TV
            #EXTGRP:Music
            http://z/stream.m3u8
            """.trimIndent()
        ).entries.single()
        assertEquals("Music", entry.groupTitle)
        assertNull(entry.tvgId)
    }

    @Test
    fun `options do not leak into the next entry`() {
        val playlist = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,One
            #EXTVLCOPT:http-user-agent=UA1
            http://one/stream.m3u8
            #EXTINF:-1,Two
            http://two/stream.m3u8
            """.trimIndent()
        )
        assertEquals("UA1", playlist.entries[0].userAgent)
        assertNull(playlist.entries[1].userAgent)
        assertTrue(playlist.entries.all { it.url.startsWith("http://") })
    }
}
