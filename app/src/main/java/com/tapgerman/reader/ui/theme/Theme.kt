package com.tapgerman.reader.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.tapgerman.reader.data.ThemeMode

/**
 * The design system, assembled.
 *
 * **On the absence of `MaterialExpressiveTheme`.** It would be the natural way to write this, but
 * in material3 1.4.0 — the only stable version — both `MaterialExpressiveTheme` and the
 * `MotionScheme` it takes are declared `internal`, so neither is callable from application code.
 * They are only public in 1.5.0-alpha, which requires `compileSdk 37` and AGP 9, which is a
 * toolchain migration rather than a UI change. See the note in `gradle/libs.versions.toml`.
 *
 * What that costs is provenance, not appearance. The expressive layer is a bigger round shape
 * scale, spring-based motion, shape morphing on press and more vivid colour — all of which this
 * package supplies directly in [LingoShapes], [LingoMotionTokens] and the palette, and all of
 * which every screen in this app opts into explicitly. When 1.5.0 goes stable this function
 * becomes a one-line change.
 *
 * Dynamic color is a user choice, not an app default. Material You is lovely and is the right
 * answer for most apps, but in a reader the palette is doing real work — the accent marks which
 * words are tappable and which are already saved — and handing that to whatever colour the
 * user's wallpaper happens to be makes the app's one essential affordance arbitrary. So the brand
 * leads, and wallpaper colours are opt-in from Settings.
 */
@Composable
fun TapGermanTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = when {
        dynamicColor && supportsDynamic ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> LingoDarkColors
        else -> LingoLightColors
    }

    // The accent is tuned per brightness against the brand ramps, so it does not follow dynamic
    // color. A chartreuse chosen to sit behind near-black text is wrong on a near-black surface.
    val lingoColors = if (dark) LingoDarkAccent else LingoLightAccent

    CompositionLocalProvider(
        LocalLingoColors provides lingoColors,
        LocalLingoMotion provides LingoMotionTokens,
        LocalReadingType provides LingoReadingType,
        LocalEmphasizedType provides LingoEmphasizedType,
        LocalMotionEnabled provides animatorsAreEnabled(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LingoTypography,
            shapes = LingoShapes,
            content = content,
        )
    }
}

/**
 * Whether the user has turned animations off, at the system level.
 *
 * `ValueAnimator.areAnimatorsEnabled()` is the right question rather than
 * `ANIMATOR_DURATION_SCALE`, because it accounts for the Accessibility setting that removes
 * animations outright, not just the developer slider. It is API 26, which is this app's floor,
 * so there is no version check to make.
 *
 * Compose honours this for its own transitions. It does not for hand-written ones, and after the
 * redesign most of the motion here *is* hand-written: the word highlight, the lookup card's
 * entrance, the count on the stats row. Those consult this and stand down, rather than
 * animating at a user who asked for stillness.
 */
private fun animatorsAreEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

val LocalMotionEnabled = staticCompositionLocalOf { true }

/** Accessors, so call sites read as `LingoTheme.accent` rather than as plumbing. */
object LingoTheme {

    val colors: LingoColors
        @Composable @ReadOnlyComposable get() = LocalLingoColors.current

    val motion: LingoMotion
        @Composable @ReadOnlyComposable get() = LocalLingoMotion.current

    val emphasized: EmphasizedType
        @Composable @ReadOnlyComposable get() = LocalEmphasizedType.current

    val reading: ReadingType
        @Composable @ReadOnlyComposable get() = LocalReadingType.current

    /** False when the user has reduced or disabled system motion. */
    val motionEnabled: Boolean
        @Composable @ReadOnlyComposable get() = LocalMotionEnabled.current
}
