package com.lingodeck.reader.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Material 3 Expressive motion, plus the springs this app needs that the library has no
 * name for.
 *
 * The pre-redesign app had no animation code at all: screen changes were an instant cut, the
 * lookup card popped in with no transition, the loading state was a bare spinner. That reads
 * as a web form rather than an app, and it is the single cheapest thing to fix, because
 * `MotionScheme.expressive()` in material3 1.4.0 is a real token set rather than something
 * hand-rolled per call site.
 *
 * Every value here is a spring rather than a fixed duration, so a card opening over a long
 * article and one opening near the top of the screen settle in the same time and the app does
 * not feel slower on a big page.
 */
@Immutable
data class LingoMotion(
    /** Screen to screen: content moves, it does not merely cross-fade. */
    val screen: AnimationSpec<Float>,
    /** An overlay arriving: a little overshoot, so it reads as arriving rather than appearing. */
    val enter: AnimationSpec<Float>,
    /** An overlay leaving: faster than it came in, and without the overshoot. */
    val exit: AnimationSpec<Float>,
    /** A press response. Fast enough to stay connected to the finger. */
    val press: AnimationSpec<Float>,
    /** A value settling into place after a selection changes. */
    val settle: AnimationSpec<Float>,
    /** A slow-tracking value such as the reader's scroll progress. */
    val continuous: AnimationSpec<Float>,
    /** The morphing loading indicator's own rotation. */
    val loading: AnimationSpec<Float>,
)

/**
 * The expressive token set, the springs the library does not name, and nothing else.
 *
 * `MotionScheme` itself is `internal` in material3 1.4.0, so the expressive spring set is
 * reached through `MaterialExpressiveTheme` rather than held as a value. The five springs below
 * are the ones this app needs that the library's set does not cover: an overlay entrance with a
 * slight overshoot, a press response fast enough to stay under the finger, and a slow-tracking
 * value for the reader's scroll progress.
 */
val LingoMotionTokens = LingoMotion(
    // The expressive spatial slow spring: enough overshoot to feel physical, not enough to wobble.
    screen = spring(dampingRatio = 0.9f, stiffness = 380f),
    enter = spring(dampingRatio = 0.72f, stiffness = 420f),
    exit = spring(dampingRatio = 1f, stiffness = 700f),
    press = spring(dampingRatio = 0.85f, stiffness = 900f),
    settle = spring(dampingRatio = 0.85f, stiffness = 500f),
    continuous = spring(dampingRatio = 1f, stiffness = 200f),
    loading = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
)

val LocalLingoMotion = staticCompositionLocalOf { LingoMotionTokens }
