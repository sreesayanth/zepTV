package com.zaptv.core.parser

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parses a real downloaded playlist and prints the resulting lineup. Skipped unless a file is given:
 *   ./gradlew :core:parser:test -Dzaptv.playlist=/path/to/mal.m3u
 * (Playlists are not committed to the repo.)
 */
class RealPlaylistSmokeTest {

    @Test
    fun `real playlist builds a sensible lineup`() {
        val path = System.getProperty("zaptv.playlist")
        assumeTrue("Set -Dzaptv.playlist to run", !path.isNullOrBlank() && File(path).isFile)

        val playlist = File(path).reader().use { M3uParser.parse(it) }
        val lineup = LineupBuilder.build("smoke", playlist.entries)

        println("entries=${playlist.entries.size} channels=${lineup.size}")
        lineup.forEach { c ->
            println("%4d  %-28s %-13s %-6s sources=%d".format(c.number, c.name.take(28), c.category.label, c.quality ?: "-", c.sources.size))
        }
        assertTrue(lineup.isNotEmpty())
        assertTrue("numbers must be unique", lineup.map { it.number }.toSet().size == lineup.size)
        assertTrue("every channel needs a source", lineup.all { it.sources.isNotEmpty() })
    }
}
