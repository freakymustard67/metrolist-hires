/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * One place for the app's motion: durations in milliseconds and the standard curves.
 *
 * Keep these small. Navigation and list work must stay under ~200 ms so a screen that is
 * still mounting its lists never animates for longer than it takes to feel instant.
 */
object Motion {
    /** Small state changes: chip selection, icon morphs, ripples. */
    const val FAST = 120

    /** Default for fades and content swaps. */
    const val STANDARD = 200

    /** Deliberate, larger surfaces: sheets, page changes. */
    const val SLOW = 320

    /** Emphasized curve for entrances; decelerates into place. */
    val emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Standard curve for exits; accelerates out of view. */
    val accelerated: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}
