package com.zaptv.core.parser

import com.zaptv.core.model.PlaylistPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPresetsTest {

    @Test
    fun `ids are unique`() {
        val ids = PlaylistPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `number blocks never overlap`() {
        val ranges = PlaylistPresets.all.map { (it.numberBase + 1) until (it.numberBase + it.capacity) }
        for (i in ranges.indices) for (j in i + 1 until ranges.size) {
            val a = ranges[i]
            val b = ranges[j]
            assertTrue("${PlaylistPresets.all[i].name} overlaps ${PlaylistPresets.all[j].name}", a.last < b.first || b.last < a.first)
        }
    }

    @Test
    fun `existing Malayalam numbers and id are unchanged`() {
        assertEquals("iptv-org-mal", PlaylistPresets.Malayalam.id)
        assertEquals(0, PlaylistPresets.Malayalam.numberBase)
        assertEquals(PlaylistPresets.Malayalam, PlaylistPresets.all.first())
    }

    @Test
    fun `defaults are Malayalam, English (India), Tamil and Hindi`() {
        assertEquals(
            setOf("Malayalam", "English (India)", "Tamil", "Hindi"),
            PlaylistPresets.defaultIds.map { PlaylistPresets.byId(it)!!.name }.toSet(),
        )
    }

    @Test
    fun `country code comes from the tvg-id`() {
        fun entry(tvgId: String?) = M3uParser.parse(
            "#EXTM3U\n#EXTINF:-1" + (tvgId?.let { " tvg-id=\"$it\"" } ?: "") + ",X\nhttp://x",
        ).entries.single()
        assertEquals("in", entry("NDTV24x7.in@SD").countryCode)
        assertEquals("uk", entry("BBCNews.uk").countryCode)
        assertNull(entry("NoCountry").countryCode)
        assertNull(entry(null).countryCode)
    }
}
