package com.lazylines.leanback

import android.graphics.Color

/**
 * Centralised colour constants and colour-temperature logic for the
 * LazyLines Projector browser.
 *
 * All colour values match the UI design specification.
 */
object ProjectorColorScheme {

    // ── Backgrounds ────────────────────────────────────────────────
    const val BG_PRIMARY    = 0xFF0D0D0DL
    const val BG_SECONDARY  = 0xFF1A1A2EL
    const val BG_ELEVATED   = 0xFF252540L
    const val SURFACE       = 0xFF16213EL

    // ── Text ───────────────────────────────────────────────────────
    const val TEXT_PRIMARY   = 0xFFFFFFFFL
    const val TEXT_SECONDARY = 0xFFB0BEC5L
    const val TEXT_HINT      = 0xFF607D8BL

    // ── Accent ─────────────────────────────────────────────────────
    const val ACCENT           = 0xFF00E5FFL
    const val ACCENT_SECONDARY = 0xFFFF6E40L
    const val SUCCESS          = 0xFF69F0AEL
    const val ERROR            = 0xFFFF5252L

    // ── Focus / dividers ───────────────────────────────────────────
    const val FOCUS_GLOW = 0x5A00E5FFL   // 35 % alpha cyan
    const val DIVIDER    = 0xFF2A2A45L

    // ── Convenience Int accessors (argb, as used by Android) ───────
    val bgPrimaryInt:    Int get() = BG_PRIMARY.toInt()
    val bgSecondaryInt:  Int get() = BG_SECONDARY.toInt()
    val bgElevatedInt:   Int get() = BG_ELEVATED.toInt()
    val surfaceInt:      Int get() = SURFACE.toInt()

    val textPrimaryInt:   Int get() = TEXT_PRIMARY.toInt()
    val textSecondaryInt: Int get() = TEXT_SECONDARY.toInt()
    val textHintInt:      Int get() = TEXT_HINT.toInt()

    val accentInt:          Int get() = ACCENT.toInt()
    val accentSecondaryInt: Int get() = ACCENT_SECONDARY.toInt()
    val successInt:         Int get() = SUCCESS.toInt()
    val errorInt:           Int get() = ERROR.toInt()

    val focusGlowInt: Int get() = FOCUS_GLOW.toInt()
    val dividerInt:   Int get() = DIVIDER.toInt()

    // ── Colour temperature ─────────────────────────────────────────

    /**
     * Colour temperature presets that apply a subtle tint shift to the
     * primary background colour, matching common projector white-point
     * calibrations.
     */
    enum class ColorTemperature(val label: String, val whitePoint: String) {
        /** D65 – 6500 K, neutral / standard. */
        STANDARD("Standard", "D65"),
        /** D75 – 7500 K, cooler / bluer. */
        COOL("Cool", "D75"),
        /** D55 – 5500 K, warmer / redder. */
        WARM("Warm", "D55")
    }

    /**
     * Return the background colour with a subtle tint shift applied for
     * the given [ColorTemperature].
     *
     * - STANDARD: no shift (pure #0D0D0D)
     * - COOL:     slight blue tint  → +8 on blue channel
     * - WARM:     slight red tint   → +8 on red channel
     */
    fun getBackgroundColor(temp: ColorTemperature): Int {
        val base = BG_PRIMARY.toInt()
        val r = Color.red(base)
        val g = Color.green(base)
        val b = Color.blue(base)

        return when (temp) {
            ColorTemperature.STANDARD -> base
            ColorTemperature.COOL     -> Color.rgb(r, g, (b + 8).coerceAtMost(255))
            ColorTemperature.WARM     -> Color.rgb((r + 8).coerceAtMost(255), g, b)
        }
    }
}
