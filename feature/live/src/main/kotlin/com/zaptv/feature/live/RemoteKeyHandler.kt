package com.zaptv.feature.live

import android.os.SystemClock
import android.view.KeyEvent

/**
 * Turns raw key events into [RemoteAction]s.
 *
 * - OK fires on release; holding it (until the first key repeat, about half a second) fires OkLong instead.
 * - Held ▲/▼ repeats at most every 150 ms; the view model's zap debounce does the rest.
 * - BACK is not handled here: it goes through BackHandler so it also works with predictive back.
 */
class RemoteKeyHandler(private val onAction: (RemoteAction) -> Unit) {

    private var okDown = false
    private var okLongFired = false
    private var lastRepeatAt = 0L

    fun handle(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> when {
                    event.repeatCount == 0 -> { okDown = true; okLongFired = false }
                    okDown && !okLongFired -> { okLongFired = true; onAction(RemoteAction.OkLong) }
                }
                KeyEvent.ACTION_UP -> {
                    if (okDown && !okLongFired) onAction(RemoteAction.Ok)
                    okDown = false
                }
            }
            return true
        }

        val action = actionFor(code) ?: return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0) {
            if (action !in repeatable) return true
            val now = SystemClock.uptimeMillis()
            if (now - lastRepeatAt < REPEAT_INTERVAL_MS) return true
            lastRepeatAt = now
        }
        onAction(action)
        return true
    }

    private fun actionFor(code: Int): RemoteAction? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> RemoteAction.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> RemoteAction.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> RemoteAction.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteAction.Right
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> RemoteAction.ChannelUp
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> RemoteAction.ChannelDown
        KeyEvent.KEYCODE_INFO -> RemoteAction.Info
        KeyEvent.KEYCODE_LAST_CHANNEL -> RemoteAction.LastChannel
        KeyEvent.KEYCODE_PROG_RED -> RemoteAction.Favourites
        KeyEvent.KEYCODE_PROG_GREEN -> RemoteAction.Groups
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> RemoteAction.Menu
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> RemoteAction.Digit('0' + (code - KeyEvent.KEYCODE_0))
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> RemoteAction.Digit('0' + (code - KeyEvent.KEYCODE_NUMPAD_0))
        else -> null
    }

    private companion object {
        const val REPEAT_INTERVAL_MS = 150L
        val repeatable = setOf(RemoteAction.Up, RemoteAction.Down, RemoteAction.ChannelUp, RemoteAction.ChannelDown, RemoteAction.Left, RemoteAction.Right)
    }
}
