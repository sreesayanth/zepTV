package com.zaptv.core.parser

import com.zaptv.core.parser.MediaName.Codec
import com.zaptv.core.parser.MediaName.Hdr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNameParserTest {

    @Test
    fun `parses a dotted movie release name`() {
        val n = MediaNameParser.parse("Monsoon.Letters.2024.1080p.WEB-DL.Mal.DDP5.1.x265.mkv")
        assertEquals("Monsoon Letters", n.title)
        assertEquals(2024, n.year)
        assertEquals(1080, n.quality)
        assertEquals("WEB-DL", n.source)
        assertEquals(Codec.HEVC, n.codec)
        assertEquals(listOf("ml"), n.languages)
        assertFalse(n.isEpisode)
    }

    @Test
    fun `parses SxxEyy episodes`() {
        val n = MediaNameParser.parse("Backwater Season S01E04 720p.mp4")
        assertEquals("Backwater Season", n.title)
        assertEquals(1, n.season)
        assertEquals(4, n.episode)
        assertEquals(720, n.quality)
    }

    @Test
    fun `parses double episodes and spaced markers`() {
        val n = MediaNameParser.parse("Night_Bus_47_S02_E05-E06_480p.mkv")
        assertEquals("Night Bus 47", n.title)
        assertEquals(2, n.season)
        assertEquals(5, n.episode)
        assertEquals(6, n.episodeEnd)
    }

    @Test
    fun `parses 2x04 and Season n Episode n`() {
        assertEquals(2 to 4, MediaNameParser.parse("Tea Estate 2x04.mkv").let { it.season to it.episode })
        val words = MediaNameParser.parse("Harbour Lights Season 3 Episode 12.mp4")
        assertEquals("Harbour Lights", words.title)
        assertEquals(3 to 12, words.season to words.episode)
    }

    @Test
    fun `parses Ep markers without a season`() {
        val n = MediaNameParser.parse("Little Explorers Ep 07 [Kids].mp4")
        assertEquals("Little Explorers", n.title)
        assertNull(n.season)
        assertEquals(7, n.episode)
    }

    @Test
    fun `parses Malayalam episode word`() {
        val n = MediaNameParser.parse("Kudumbavilakku എപ്പിസോഡ് 12.mp4")
        assertEquals("Kudumbavilakku", n.title)
        assertEquals(12, n.episode)
    }

    @Test
    fun `strips uploader tags`() {
        val n = MediaNameParser.parse("@MovieChannel - Kayal (2022) 720p HDRip Malayalam.mkv")
        assertEquals("Kayal", n.title)
        assertEquals(2022, n.year)
        assertEquals("HDRip", n.source)
        assertEquals(listOf("ml"), n.languages)

        val www = MediaNameParser.parse("www.example.site - Red Earth 2025 TRUE WEB-DL 4K HEVC HDR10+.mkv")
        assertEquals("Red Earth", www.title)
        assertEquals(2160, www.quality)
        assertEquals(Hdr.HDR10_PLUS, www.hdr)
    }

    @Test
    fun `keeps a year that is the title`() {
        val n = MediaNameParser.parse("2018.2023.1080p.BluRay.mkv")
        assertEquals("2018", n.title)
        assertEquals(2023, n.year)
        assertEquals("BluRay", n.source)
    }

    @Test
    fun `keeps hyphenated titles`() {
        assertEquals("Spider-Man", MediaNameParser.parse("Spider-Man.2002.720p.mkv").title)
    }

    @Test
    fun `reads dual audio languages in order`() {
        val n = MediaNameParser.parse("The Ferry Clerk 2023 Tamil + Malayalam + Eng 720p.mkv")
        assertEquals("The Ferry Clerk", n.title)
        assertEquals(listOf("ta", "ml", "en"), n.languages)
    }

    @Test
    fun `finds split parts`() {
        assertEquals(2, MediaNameParser.parse("Spice Route 2021 Part 2.mkv").part)
        assertEquals(2, MediaNameParser.parse("Spice Route 2021 CD2.avi").part)
        val split = MediaNameParser.parse("Spice.Route.2021.1080p.mkv.002")
        assertEquals(2, split.part)
        assertEquals("Spice Route", split.title)
    }

    @Test
    fun `prefers a caption with a year over a vague file name`() {
        val n = MediaNameParser.parse("video_2025-01-03_21-14-08.mp4", caption = "Monsoon Letters (2024)\nMalayalam · 1080p")
        assertEquals("Monsoon Letters", n.title)
        assertEquals(2024, n.year)
        assertEquals(1080, n.quality)
    }

    @Test
    fun `series key ignores case and punctuation`() {
        val a = MediaNameParser.parse("Backwater.Season.S01E01.mkv")
        val b = MediaNameParser.parse("backwater season - S01E02.mp4")
        assertEquals(a.seriesKey, b.seriesKey)
    }

    @Test
    fun `recognises video file names`() {
        assertTrue(MediaNameParser.isVideoFileName("a.MKV"))
        assertTrue(MediaNameParser.isVideoFileName("a.mkv.001"))
        assertFalse(MediaNameParser.isVideoFileName("subs.srt"))
    }

    @Test
    fun `never returns a blank title`() {
        assertEquals("S01E01", MediaNameParser.parse("S01E01.mkv").title)
    }
}
