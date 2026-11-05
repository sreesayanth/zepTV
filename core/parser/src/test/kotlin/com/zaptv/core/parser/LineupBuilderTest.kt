package com.zaptv.core.parser

import com.zaptv.core.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class LineupBuilderTest {

    private fun playlist(vararg lines: String) = M3uParser.parse("#EXTM3U\n" + lines.joinToString("\n")).entries

    @Test
    fun `numbers channels in cable-style category blocks`() {
        val lineup = LineupBuilder.build(
            "p",
            playlist(
                "#EXTINF:-1 tvg-id=\"a\" group-title=\"News\",A News", "http://a",
                "#EXTINF:-1 tvg-id=\"b\" group-title=\"Entertainment\",B TV", "http://b",
                "#EXTINF:-1 tvg-id=\"c\" group-title=\"News\",C News", "http://c",
                "#EXTINF:-1 tvg-id=\"d\" group-title=\"Religious\",D TV", "http://d",
                "#EXTINF:-1 tvg-id=\"e\" group-title=\"Animation;Kids\",E Kids", "http://e",
            ),
        )
        assertEquals(listOf(1, 101, 102, 401, 501), lineup.map { it.number })
        assertEquals(Category.KIDS, lineup.first { it.name == "E Kids" }.category)
    }

    @Test
    fun `keeps previous numbers and fills gaps for new channels`() {
        val entries = playlist(
            "#EXTINF:-1 tvg-id=\"a\" group-title=\"News\",A News", "http://a",
            "#EXTINF:-1 tvg-id=\"new\" group-title=\"News\",New News", "http://new",
        )
        val lineup = LineupBuilder.build("p", entries, previousNumbers = mapOf("p:a" to 105))
        assertEquals(105, lineup.first { it.name == "A News" }.number)
        assertEquals(101, lineup.first { it.name == "New News" }.number)
    }

    @Test
    fun `merges duplicate entries into failover sources, best quality first`() {
        val lineup = LineupBuilder.build(
            "p",
            playlist(
                "#EXTINF:-1 tvg-id=\"x\" group-title=\"News\",X News (576p)", "http://x-sd",
                "#EXTINF:-1 tvg-id=\"x\" group-title=\"News\",X News (1080p)", "http://x-hd",
                "#EXTINF:-1 tvg-id=\"x\" group-title=\"News\",X News (1080p)", "http://x-hd",
            ),
        )
        val channel = lineup.single()
        assertEquals(listOf("http://x-hd", "http://x-sd"), channel.sources.map { it.url })
        assertEquals("1080p", channel.quality)
        assertEquals(true, channel.isHd)
    }

    @Test
    fun `channels without tvg-id are matched by name`() {
        val lineup = LineupBuilder.build(
            "p",
            playlist("#EXTINF:-1,Raj Musix", "http://1", "#EXTINF:-1,RAJ  musix", "http://2"),
        )
        assertEquals(1, lineup.size)
        assertEquals(2, lineup.single().sources.size)
    }

    @Test
    fun `monogram uses initials or a leading number`() {
        val lineup = LineupBuilder.build(
            "p",
            playlist("#EXTINF:-1 tvg-id=\"a\",Asianet News", "http://a", "#EXTINF:-1 tvg-id=\"b\",24 News", "http://b"),
        )
        assertEquals(setOf("AN", "24"), lineup.map { it.monogram }.toSet())
    }

    @Test
    fun `numbers a playlist inside its own block`() {
        val lineup = LineupBuilder.build(
            "tam",
            playlist(
                "#EXTINF:-1 tvg-id=\"a\" group-title=\"News\",A News", "http://a",
                "#EXTINF:-1 tvg-id=\"b\" group-title=\"Entertainment\",B TV", "http://b",
            ),
            numberBase = 2000,
        )
        assertEquals(listOf(2001, 2101), lineup.map { it.number })
        assertEquals(listOf("tam", "tam"), lineup.map { it.playlistId })
    }

    @Test
    fun `ignores a previous number from outside the block`() {
        val entries = playlist("#EXTINF:-1 tvg-id=\"a\" group-title=\"News\",A News", "http://a")
        val lineup = LineupBuilder.build("hin", entries, previousNumbers = mapOf("hin:a" to 105), numberBase = 3000)
        assertEquals(3101, lineup.single().number)
    }

    @Test
    fun `a full category block overflows to 900 and up, and stops at the end of the range`() {
        val news = (1..120).flatMap { listOf("#EXTINF:-1 tvg-id=\"n$it\" group-title=\"News\",News $it", "http://n$it") }
        val lineup = LineupBuilder.build("hin", playlist(*news.toTypedArray()), numberBase = 3000)
        assertEquals(120, lineup.size)
        assertEquals(3101, lineup.first().number)
        assertEquals(3199, lineup[98].number)
        assertEquals(3900, lineup[99].number)

        val tiny = LineupBuilder.build("x", playlist(*news.toTypedArray()), numberBase = 0, capacity = 905)
        assertEquals(99 + 5, tiny.size)
        assertEquals(904, tiny.last().number)
    }

    @Test
    fun `combine keeps a shared channel only in the lowest-numbered playlist`() {
        val malayalam = LineupBuilder.build(
            "mal",
            playlist(
                "#EXTINF:-1 tvg-id=\"DDNational.in@SD\" group-title=\"General\",DD National", "http://mal-dd",
                "#EXTINF:-1 tvg-id=\"AsianetNews.in@SD\" group-title=\"News\",Asianet News", "http://an",
            ),
        )
        val hindi = LineupBuilder.build(
            "hin",
            playlist(
                "#EXTINF:-1 tvg-id=\"ddnational.in@SD\" group-title=\"General\",DD National", "http://hin-dd",
                "#EXTINF:-1 tvg-id=\"AajTak.in@SD\" group-title=\"News\",Aaj Tak", "http://aaj",
            ),
            numberBase = 3000,
        )
        val combined = LineupBuilder.combine(hindi + malayalam)
        assertEquals(listOf("DD National", "Asianet News", "Aaj Tak"), combined.map { it.name })
        assertEquals(listOf("mal", "mal", "hin"), combined.map { it.playlistId })
    }

    @Test
    fun `combine keeps channels without a tvg-id`() {
        val a = LineupBuilder.build("a", playlist("#EXTINF:-1,Same Name", "http://1"))
        val b = LineupBuilder.build("b", playlist("#EXTINF:-1,Same Name", "http://2"), numberBase = 1000)
        assertEquals(2, LineupBuilder.combine(a + b).size)
    }
}
