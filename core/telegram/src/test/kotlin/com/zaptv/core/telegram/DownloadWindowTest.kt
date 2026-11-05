package com.zaptv.core.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWindowTest {

    private val gb = 1_000_000_000L
    private val mb = 1_000_000L

    private fun decide(
        position: Long,
        offset: Long = 0,
        prefix: Long = 0,
        completed: Boolean = false,
        active: Boolean = true,
        requested: Long = -1,
        size: Long = 2 * gb,
    ) = DownloadWindow.decide(position, size, offset, prefix, completed, active, requested)

    @Test
    fun `reads what is inside the window`() {
        val d = decide(position = 10 * mb, offset = 0, prefix = 30 * mb)
        assertEquals(20 * mb, d.readable)
        assertFalse(d.moveDownload)
    }

    @Test
    fun `waits just past the window while it is growing`() {
        val d = decide(position = 30 * mb + 1000, offset = 0, prefix = 30 * mb)
        assertEquals(0, d.readable)
        assertFalse(d.moveDownload)
    }

    @Test
    fun `seek forward into a hole moves the download`() {
        val d = decide(position = gb, offset = 0, prefix = 30 * mb)
        assertEquals(0, d.readable)
        assertTrue(d.moveDownload)
    }

    @Test
    fun `does not ask twice for the same offset`() {
        val d = decide(position = gb, offset = 0, prefix = 30 * mb, requested = gb)
        assertFalse(d.moveDownload)
    }

    @Test
    fun `asks again when the download stopped`() {
        val d = decide(position = gb, offset = 0, prefix = 30 * mb, requested = gb, active = false)
        assertTrue(d.moveDownload)
    }

    @Test
    fun `seek back before the window moves the download`() {
        // Bytes already on disk there are reported straight away once TDLib moves.
        val d = decide(position = 5 * mb, offset = gb, prefix = 10 * mb)
        assertTrue(d.moveDownload)
    }

    @Test
    fun `mp4 index at the end is a normal seek`() {
        val d = decide(position = 2 * gb - 200_000, offset = 0, prefix = 2 * mb)
        assertTrue(d.moveDownload)
    }

    @Test
    fun `completed file is readable to the end`() {
        val d = decide(position = gb, completed = true, active = false)
        assertEquals(gb, d.readable)
        assertFalse(d.moveDownload)
    }

    @Test
    fun `end of file is never a hole`() {
        val d = decide(position = 2 * gb, offset = 0, prefix = 30 * mb)
        assertEquals(0, d.readable)
        assertFalse(d.moveDownload)
    }
}
