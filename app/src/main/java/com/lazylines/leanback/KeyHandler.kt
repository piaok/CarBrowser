package com.lazylines.leanback

import android.view.KeyEvent

/**
 * Maps Android D-pad key codes to semantic [Action] values used by the
 * Leanback UI layer.
 *
 * This is a pure static mapping — no Android Context is required.
 */
object KeyHandler {

    /**
     * Semantic actions that a D-pad / remote key press can produce.
     */
    enum class Action {
        FOCUS_UP,
        FOCUS_DOWN,
        FOCUS_LEFT,
        FOCUS_RIGHT,
        SELECT,
        BACK,
        HOME
    }

    /** Immutable mapping table: key-code → Action. */
    private val MAPPING = mapOf(
        KeyEvent.KEYCODE_DPAD_UP    to Action.FOCUS_UP,
        KeyEvent.KEYCODE_DPAD_DOWN  to Action.FOCUS_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT  to Action.FOCUS_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to Action.FOCUS_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER to Action.SELECT,
        KeyEvent.KEYCODE_ENTER      to Action.SELECT,
        KeyEvent.KEYCODE_BACK       to Action.BACK,
        KeyEvent.KEYCODE_HOME       to Action.HOME
    )

    /**
     * Translate an Android [keyCode] into a semantic [Action].
     *
     * @param keyCode The key code from [KeyEvent.getKeyCode].
     * @return The corresponding [Action], or null if the key is not mapped.
     */
    fun handleKey(keyCode: Int): Action? = MAPPING[keyCode]
}
