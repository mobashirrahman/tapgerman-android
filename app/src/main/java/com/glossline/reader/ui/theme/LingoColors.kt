package com.glossline.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The electric accent, kept out of [ColorScheme] on purpose.
 *
 * Material's roles are load-bearing: primary is what a filled button is tinted with, and
 * `primaryContainer` is what a selected nav item sits on. Flooding them with a chartreuse
 * highlight would turn the whole app into a lime-and-violet toy and break the tonal contrast
 * guarantees the components assume. So the accent is a separate, deliberately small set of
 * roles with four jobs:
 *
 * 1. the highlight behind the word under your finger in the reader,
 * 2. the marker for a word you have already saved,
 * 3. the filled "Read" action, the one place the brand gets to shout,
 * 4. the highlight bar in the app icon, so the icon and the product's core gesture are
 *    visibly the same idea.
 *
 * The contrast pairs are chosen by hand rather than derived, because a highlight sits *behind*
 * text rather than replacing a surface, and Material's tonal math has nothing to say about that.
 */
@Immutable
data class LingoColors(
    /** The highlight itself. Reads well behind near-black and near-white text alike. */
    val accent: Color,
    /** Text or icon content placed on [accent]. */
    val onAccent: Color,
    /** A quieter accent for large fills such as empty-state artwork. */
    val accentContainer: Color,
    /** Content on [accentContainer]. */
    val onAccentContainer: Color,
    /**
     * The colour of a tappable word that has not been saved.
     *
     * This is a *text* colour, not an underline colour, and that is a deliberate consequence of a
     * Compose limitation: `SpanStyle` in 1.11 has no `textDecorationColor`, so an underline always
     * takes its span's text colour. Underlining every word in a paragraph would therefore have to
     * be drawn at full strength, which on 20sp serif is a wall of noise rather than a hint.
     *
     * So the tappable state is carried by colour instead, nudged about a tenth of the way from the
     * reading text toward the accent. It is quiet enough to read straight through and distinct
     * enough to notice once, which is all an affordance has to do.
     */
    val wordHint: Color,
    /**
     * The colour of a word that is already in the word list.
     *
     * One step further toward the accent, and underlined. Saved words are few, so a full-strength
     * underline on them costs nothing visually and is the one piece of reader decoration that is
     * information rather than hint: a second read of an article shows what you collected.
     */
    val wordSaved: Color,
    /** A hairline that is visible without being loud, for inset dividers. */
    val hairline: Color,
    /** The paper tone the reader lays text on, so long-form reading is not on pure white. */
    val readingSurface: Color,
    val onReadingSurface: Color,
    /** Gradient stops for the app icon, splash and hero artwork. */
    val gradientStart: Color,
    val gradientMid: Color,
    val gradientEnd: Color,
)

internal val LingoLightAccent = LingoColors(
    accent = Color(0xFFC6F24E),
    onAccent = Color(0xFF1F2A00),
    accentContainer = Color(0xFFE8FFC4),
    onAccentContainer = Color(0xFF2A3A00),
    // Reading text is #16141C. The first version of wordHint was #4B4576, and a screenshot of the
    // reader showed the consequence: the whole paragraph read as violet text rather than as prose
    // with a hint, which is styling, not an affordance. This is a much smaller step — noticeable
    // once you look for it, invisible while you read. Both stay above 4.5:1 on the reading surface.
    wordHint = Color(0xFF2E2A45),
    wordSaved = Color(0xFF5F8010),
    hairline = Color(0x14000000),
    readingSurface = Color(0xFFFDFCFF),
    onReadingSurface = Color(0xFF16141C),
    gradientStart = Color(0xFF2B1A8C),
    gradientMid = Color(0xFF5B4BCB),
    gradientEnd = Color(0xFF8A5CF0),
)

internal val LingoDarkAccent = LingoColors(
    accent = Color(0xFFD2FF6B),
    onAccent = Color(0xFF233100),
    accentContainer = Color(0xFF3B4E00),
    onAccentContainer = Color(0xFFE8FFC4),
    // Reading text is #E8E4EE; the same two steps, in the other direction, for a dark surface.
    wordHint = Color(0xFFB6AFCC),
    wordSaved = Color(0xFFB6D45C),
    hairline = Color(0x1FFFFFFF),
    readingSurface = Color(0xFF101016),
    onReadingSurface = Color(0xFFE8E4EE),
    gradientStart = Color(0xFF1B1060),
    gradientMid = Color(0xFF3A2C8C),
    gradientEnd = Color(0xFF5E3FB0),
)

val LocalLingoColors = staticCompositionLocalOf { LingoLightAccent }
