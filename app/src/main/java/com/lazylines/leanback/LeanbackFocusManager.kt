package com.lazylines.leanback

import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup

/**
 * Manages focus navigation for the Leanback (Android TV) UI.
 *
 * Supports explicit focus chain registration and two focus indicator styles
 * that can be applied to any view that receives D-pad focus.
 */
class LeanbackFocusManager {

    /**
     * Style of visual indicator shown when a view has focus.
     */
    enum class FocusIndicatorStyle {
        /** Cyan glow border around the focused view. */
        GLOW_BORDER,
        /** Subtle background lift (elevation / color shift) on the focused view. */
        BACKGROUND_LIFT
    }

    /** Registered focus chains: direction → ordered list of views. */
    private val focusChains = mutableMapOf<Int, MutableList<View>>()

    /** Current focus indicator style. */
    var focusIndicatorStyle: FocusIndicatorStyle = FocusIndicatorStyle.GLOW_BORDER
        private set

    /** Reference to the currently focused view, if any. */
    var currentFocus: View? = null
        private set

    /**
     * Register an ordered list of views that form a focus chain for the given direction.
     *
     * When navigating in [direction], focus will move through the list in order,
     * wrapping from the last element back to the first.
     *
     * @param views     Ordered list of views in the chain.
     * @param direction One of [View.FOCUS_UP], [View.FOCUS_DOWN],
     *                  [View.FOCUS_LEFT], [View.FOCUS_RIGHT].
     */
    fun registerFocusChain(views: List<View>, direction: Int) {
        require(direction in VALID_DIRECTIONS) {
            "Direction must be one of FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT"
        }
        focusChains[direction] = views.toMutableList()

        // Also wire up the native nextFocus* attributes so the Android
        // focus system follows the chain when possible.
        for (i in views.indices) {
            val current = views[i]
            val nextIndex = (i + 1) % views.size
            val next = views[nextIndex]

            when (direction) {
                View.FOCUS_UP    -> current.nextFocusUpId = next.id
                View.FOCUS_DOWN  -> current.nextFocusDownId = next.id
                View.FOCUS_LEFT  -> current.nextFocusLeftId = next.id
                View.FOCUS_RIGHT -> current.nextFocusRightId = next.id
            }
        }
    }

    /**
     * Find the next view that should receive focus when navigating from [current]
     * in the given [direction].
     *
     * Priority:
     * 1. Check registered focus chains for an explicit mapping.
     * 2. Fall back to the Android [FocusFinder] system.
     *
     * @param current   The view that currently has focus.
     * @param direction One of [View.FOCUS_UP], [View.FOCUS_DOWN],
     *                  [View.FOCUS_LEFT], [View.FOCUS_RIGHT].
     * @return The next focusable view, or null if none found.
     */
    fun findNextFocus(current: View, direction: Int): View? {
        // 1. Try explicit chain first.
        focusChains[direction]?.let { chain ->
            val index = chain.indexOf(current)
            if (index >= 0) {
                val nextIndex = (index + 1) % chain.size
                val next = chain[nextIndex]
                if (next.isFocusable && next.isEnabled && next.visibility == View.VISIBLE) {
                    return next
                }
            }
        }

        // 2. Fall back to Android focus finder.
        val root = current.rootView as? ViewGroup ?: return null
        return FocusFinder.getInstance().findNextFocus(root, current, direction)
    }

    /**
     * Set the visual style used to indicate focus.
     */
    fun setFocusIndicator(style: FocusIndicatorStyle) {
        focusIndicatorStyle = style
    }

    /**
     * Returns the currently focused view tracked by this manager.
     */
    fun getCurrentFocus(): View? = currentFocus

    /**
     * Notify the manager that focus changed. Call this from your
     * [View.OnFocusChangeListener] or Activity's onWindowFocusChanged.
     */
    fun notifyFocusChanged(newFocus: View?) {
        currentFocus = newFocus
    }

    companion object {
        private val VALID_DIRECTIONS = intArrayOf(
            View.FOCUS_UP,
            View.FOCUS_DOWN,
            View.FOCUS_LEFT,
            View.FOCUS_RIGHT
        )
    }
}
