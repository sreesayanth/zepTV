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
}
