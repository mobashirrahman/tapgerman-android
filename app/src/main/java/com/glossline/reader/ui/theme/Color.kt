package com.glossline.reader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The GlossLine palette: deep indigo into violet, with one electric accent.
 *
 * The pre-redesign theme set nine of Material 3's forty-eight-plus color roles, so almost
 * everything on screen actually rendered in stock baseline-M3 grey. Every role is filled in
 * here, in both brightnesses, which is what lets the neutral greys, the dividers and the
 * disabled states stop being accidental.
 *
 * The ramps are Material tonal ramps built around a violet-indigo hue of roughly 262 degrees.
 * They are hand-authored rather than generated at runtime so the scheme is inspectable,
 * diffable and independent of any color library; the ramps follow the Material rule that
 * tone steps monotonically within a hue so contrast behaves the way components assume.
 *
 * The electric accent deliberately is *not* a Material role. See [LingoColors].
 */

// --- Primary: violet-indigo -------------------------------------------------------
private val Violet10 = Color(0xFF100663)
private val Violet20 = Color(0xFF210C77)
private val Violet30 = Color(0xFF33119B)
private val Violet40 = Color(0xFF452EB4)
private val Violet50 = Color(0xFF5B4BCB)
private val Violet60 = Color(0xFF6F5DDD)
private val Violet70 = Color(0xFF8A79EA)
private val Violet80 = Color(0xFFA797F2)
private val Violet90 = Color(0xFFC6BCFA)
private val Violet95 = Color(0xFFE4DEFF)
private val Violet98 = Color(0xFFF2EFFF)
private val Violet99 = Color(0xFFFAF8FF)
private val Violet100 = Color(0xFFFFFFFF)

// --- Secondary: the same hue, drained of chroma ------------------------------------
private val Slate20 = Color(0xFF2B2A42)
private val Slate30 = Color(0xFF42415A)
private val Slate40 = Color(0xFF5A5972)
private val Slate50 = Color(0xFF73728B)
private val Slate60 = Color(0xFF8D8CA5)
private val Slate80 = Color(0xFFC5C3DC)
private val Slate90 = Color(0xFFE2E0F9)
private val Slate95 = Color(0xFFF0EEFC)

// --- Tertiary: rose, the Material-correct third accent ------------------------------
private val Rose20 = Color(0xFF3B0D26)
private val Rose30 = Color(0xFF571F3B)
private val Rose40 = Color(0xFF743152)
private val Rose50 = Color(0xFF8F4568)
private val Rose60 = Color(0xFFA95A7E)
private val Rose80 = Color(0xFFEB9CB9)
private val Rose90 = Color(0xFFFFD9E2)
private val Rose95 = Color(0xFFFFE9EE)

// --- Neutral: a cool ink -----------------------------------------------------------
private val Ink0 = Color(0xFF000000)
private val Ink4 = Color(0xFF0B0A12)
private val Ink6 = Color(0xFF121019)
private val Ink10 = Color(0xFF1A1822)
private val Ink12 = Color(0xFF1F1D28)
private val Ink17 = Color(0xFF2A2833)
private val Ink20 = Color(0xFF2F2D3A)
private val Ink22 = Color(0xFF37353F)
private val Ink24 = Color(0xFF403D4A)
private val Ink30 = Color(0xFF4C4956)
private val Ink40 = Color(0xFF625F6E)
private val Ink50 = Color(0xFF7A7787)
private val Ink60 = Color(0xFF9491A1)
private val Ink80 = Color(0xFFC9C6D2)
private val Ink87 = Color(0xFFDCD9E3)
private val Ink90 = Color(0xFFE5E2EC)
private val Ink92 = Color(0xFFEDEAF3)
private val Ink94 = Color(0xFFF4F2F8)
private val Ink96 = Color(0xFFF9F7FC)
private val Ink98 = Color(0xFFFDFBFF)
private val Ink100 = Color(0xFFFFFFFF)

// --- Error ------------------------------------------------------------------------
private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

/** Fixed brand roles, shared by both brightnesses. */
internal val BrandInk = Violet40
internal val BrandInkContainer = Violet30
internal val BrandOnInkContainer = Violet90

internal val LingoLightColors = lightColorScheme(
    primary = Violet40,
    onPrimary = Violet100,
    primaryContainer = Violet90,
    onPrimaryContainer = Violet10,
    inversePrimary = Violet80,

    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate20,

    tertiary = Rose40,
    onTertiary = Color.White,
    tertiaryContainer = Rose90,
    onTertiaryContainer = Rose20,

    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,

    background = Ink98,
    onBackground = Ink10,
    surface = Ink98,
    onSurface = Ink10,
    surfaceVariant = Slate90,
    onSurfaceVariant = Ink30,
    surfaceTint = Violet40,
    inverseSurface = Ink20,
    inverseOnSurface = Ink96,

    surfaceDim = Slate95,
    surfaceBright = Ink98,
    surfaceContainerLowest = Ink100,
    surfaceContainerLow = Ink96,
    surfaceContainer = Ink94,
    surfaceContainerHigh = Ink92,
    surfaceContainerHighest = Slate90,

    outline = Ink50,
    outlineVariant = Ink80,
    scrim = Ink0,
)

internal val LingoDarkColors = darkColorScheme(
    primary = Violet80,
    onPrimary = Violet20,
    primaryContainer = Violet30,
    onPrimaryContainer = Violet90,
    inversePrimary = Violet40,

    secondary = Slate80,
    onSecondary = Slate20,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,

    tertiary = Rose80,
    onTertiary = Rose20,
    tertiaryContainer = Rose30,
    onTertiaryContainer = Rose90,

    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,

    background = Ink6,
    onBackground = Ink90,
    surface = Ink6,
    onSurface = Ink90,
    surfaceVariant = Ink24,
    onSurfaceVariant = Slate80,
    surfaceTint = Violet80,
    inverseSurface = Ink90,
    inverseOnSurface = Ink20,

    surfaceDim = Ink4,
    surfaceBright = Ink30,
    surfaceContainerLowest = Ink0,
    surfaceContainerLow = Ink10,
    surfaceContainer = Ink12,
    surfaceContainerHigh = Ink17,
    surfaceContainerHighest = Ink22,

    outline = Ink60,
    outlineVariant = Ink30,
    scrim = Ink0,
)
