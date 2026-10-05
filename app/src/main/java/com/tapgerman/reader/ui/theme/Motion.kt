package com.tapgerman.reader.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset

/**
 * The motion tokens.
 *
 * The pre-redesign app had no animation code at all: screen changes were an instant cut, the
 * lookup card popped in with no transition, the loading state was a bare spinner. That reads as a
 * web form rather than an app, and after the toolchain spike established that Material 3
 * Expressive's own motion tokens are internal to material3 1.4.0, these are where the expressive
 * feel comes from. See the note in `gradle/libs.versions.toml`.
 *
 * Every value is a spring rather than a fixed duration, so an overlay opening over a long article
 * settles in the same time as one near the top of the page and the app does not feel slower on
 * big articles.
 *
 * The specs are `FiniteAnimationSpec` rather than `AnimationSpec` because Compose's transition
 * APIs demand finite specs, and a spring always is one.
 */
@Immutable
data class LingoMotion(
    /**
     * Screen to screen: content moves, it does not merely cross-fade. Typed for `Float` because
     * it drives a scale.
     */
    val screen: FiniteAnimationSpec<Float>,

    /**
     * The same spring typed for [IntOffset].
     *
     * `slideInHorizontally` and `slideOutHorizontally` animate an `IntOffset`, and Kotlin will not
     * hand a `FiniteAnimationSpec<Float>` to them. A second value is needed rather than a cast.
     */
    val screenOffset: FiniteAnimationSpec<IntOffset>,

    /** An overlay arriving: a little overshoot, so it reads as arriving rather than appearing. */
    val enter: FiniteAnimationSpec<Float>,

    /** An overlay leaving: faster than it came in, and without the overshoot. */
    val exit: FiniteAnimationSpec<Float>,

    /** A press response. Fast enough to stay connected to the finger. */
    val press: FiniteAnimationSpec<Float>,

    /** A value settling into place after a selection changes. */
    val settle: FiniteAnimationSpec<Float>,

    /** A slow-tracking value such as the reader's scroll progress. */
    val continuous: FiniteAnimationSpec<Float>,

    /** The loading indicator's own rotation. */
    val loading: FiniteAnimationSpec<Float>,
)

/**
 * The expressive set, plus the five springs Material's tokens do not name.
 *
 * The dampings follow the expressive spatial family: `enter` and `screen` overshoot slightly,
 * `exit` and `loading` do not, and `press` is stiff enough to stay under a finger.
 */
val LingoMotionTokens = LingoMotion(
    screen = spring<Float>(dampingRatio = 0.9f, stiffness = 380f),
    screenOffset = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 380f),
    enter = spring<Float>(dampingRatio = 0.72f, stiffness = 420f),
    exit = spring<Float>(dampingRatio = 1f, stiffness = 700f),
    press = spring<Float>(dampingRatio = 0.85f, stiffness = 900f),
    settle = spring<Float>(dampingRatio = 0.85f, stiffness = 500f),
    continuous = spring<Float>(dampingRatio = 1f, stiffness = 200f),
    loading = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
)

val LocalLingoMotion = staticCompositionLocalOf { LingoMotionTokens }
