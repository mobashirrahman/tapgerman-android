package com.lingodeck.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lingodeck.reader.R

/**
 * Two variable fonts, one binary each.
 *
 * The pre-redesign app had no `Typography` at all: every `Text` reached for a default Material
 * slot, and the reader body was Roboto at a hardcoded 18sp/28sp. That is a reasonable way to
 * render a form and a poor way to render German prose, which is the one thing this app does.
 *
 * **Literata** carries everything the reader has to *read*: article body, source sentences,
 * glosses, article headlines. It is a reading-optimised serif with a real `opsz` axis, so the
 * letterforms at 20sp are the small-optical-size designs rather than a display cut shrunk down.
 * It also has full coverage of the German diacritics and eszett, which is not something to
 * discover after shipping.
 *
 * **Inter** carries everything the reader has to *operate*: labels, buttons, chips, counters,
 * navigation. It stays a sans on purpose, so controls never compete with the prose.
 *
 * Both ship as variable fonts with `wght` running 200..900 and 100..900 respectively, so a
 * single ~900 KB binary per family covers the whole weight range. That is what makes the
 * emphasised scale below free: Material 3 Expressive's fifteen emphasised roles differ from
 * their baseline partners by weight, and with a variable font that is one `FontVariation`
 * setting rather than a second asset.
 */
@OptIn(ExperimentalTextApi::class)
private fun literata(weight: Int, opsz: Int, style: FontStyle = FontStyle.Normal) = Font(
    resId = R.font.literata_variable,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz.toFloat()),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Literata = FontFamily(
    literata(300, 16),
    literata(400, 16),
    literata(500, 18),
    literata(600, 20),
    literata(700, 24),
    literata(400, 16, FontStyle.Italic),
    literata(500, 18, FontStyle.Italic),
    literata(600, 20, FontStyle.Italic),
    literata(700, 24, FontStyle.Italic),
)

val Inter = FontFamily(
    inter(300),
    inter(400),
    inter(500),
    inter(600),
    inter(700),
)

/**
 * Even leading, with no trimming.
 *
 * This started as `Trim.Both` on the reasoning that a capital Ä would otherwise collide with the
 * line above. The screenshots showed the trimming is worse than the problem it solves: it trims
 * each line box to the tallest glyph *on that line*, so a line opening with a capital and an
 * ascender gets visibly tighter leading than the line before it, and a German paragraph ends up
 * with random gaps in it. `Trim.None` keeps an even baseline grid, and at the reader's 32sp
 * leading for 20sp text there is ample room for a diacritic anyway.
 */
private val EvenLeading = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Builds a style from the default scale.
 *
 * `TextStyle`'s public constructor in this Compose version only takes a `SpanStyle` and a
 * `ParagraphStyle`; the named `fontSize` / `lineHeight` parameters are properties, not
 * constructor arguments. Copying the Material default and overriding the fields keeps the scale
 * structurally familiar while still being explicit about every value that matters.
 */
private fun Typography.style(
    from: TextStyle,
    family: FontFamily,
    weight: FontWeight,
    size: TextUnit,
    leading: TextUnit,
    tracking: TextUnit = 0.sp,
    italic: Boolean = false,
) = from.copy(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    lineHeight = leading,
    letterSpacing = tracking,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    lineHeightStyle = EvenLeading,
)

/**
 * The UI type scale: Inter for controls, Literata for headlines.
 *
 * Headlines are set in the serif on purpose. The app has three titles competing for attention —
 * the library's own name, an article's headline, a word being looked up — and setting all three
 * in the same face is what made the pre-redesign app read as one undifferentiated stack of text.
 */
val LingoTypography = Typography().run {
    Typography(
        displayLarge = style(displayLarge, Inter, FontWeight.SemiBold, 52.sp, 60.sp, (-1).sp),
        displayMedium = style(displayMedium, Inter, FontWeight.SemiBold, 42.sp, 50.sp, (-0.6).sp),
        displaySmall = style(displaySmall, Inter, FontWeight.SemiBold, 34.sp, 42.sp, (-0.4).sp),

        headlineLarge = style(headlineLarge, Literata, FontWeight.SemiBold, 32.sp, 40.sp, (-0.4).sp),
        headlineMedium = style(headlineMedium, Literata, FontWeight.SemiBold, 28.sp, 36.sp, (-0.2).sp),
        headlineSmall = style(headlineSmall, Literata, FontWeight.SemiBold, 24.sp, 32.sp),

        titleLarge = style(titleLarge, Inter, FontWeight.SemiBold, 21.sp, 28.sp, (-0.2).sp),
        titleMedium = style(titleMedium, Inter, FontWeight.SemiBold, 16.sp, 24.sp),
        titleSmall = style(titleSmall, Inter, FontWeight.Medium, 14.sp, 20.sp, 0.1.sp),

        bodyLarge = style(bodyLarge, Inter, FontWeight.Normal, 16.sp, 24.sp, 0.15.sp),
        bodyMedium = style(bodyMedium, Inter, FontWeight.Normal, 14.sp, 21.sp, 0.15.sp),
        bodySmall = style(bodySmall, Inter, FontWeight.Normal, 12.sp, 18.sp, 0.2.sp),

        labelLarge = style(labelLarge, Inter, FontWeight.SemiBold, 14.sp, 20.sp, 0.1.sp),
        labelMedium = style(labelMedium, Inter, FontWeight.Medium, 12.sp, 16.sp, 0.4.sp),
        labelSmall = style(labelSmall, Inter, FontWeight.Medium, 11.sp, 16.sp, 0.4.sp),
    )
}

/**
 * The fifteen Material 3 Expressive emphasised roles.
 *
 * These are not in `Typography` because Material's own scale has no slot for them, and because
 * components do not apply them by default. They are exposed separately so that using one is an
 * explicit choice at the call site. Material's rule is followed here: emphasised type marks
 * selection, action and editorial headlines, never running body text.
 *
 * Each is its baseline partner one weight step up, which is how Material describes the family.
 */
@Immutable
data class EmphasizedType(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

private fun TextStyle.emphasize() = copy(fontWeight = FontWeight.Bold)

val LingoEmphasizedType = EmphasizedType(
    displayLarge = LingoTypography.displayLarge.emphasize(),
    displayMedium = LingoTypography.displayMedium.emphasize(),
    displaySmall = LingoTypography.displaySmall.emphasize(),
    headlineLarge = LingoTypography.headlineLarge.emphasize(),
    headlineMedium = LingoTypography.headlineMedium.emphasize(),
    headlineSmall = LingoTypography.headlineSmall.emphasize(),
    titleLarge = LingoTypography.titleLarge.emphasize(),
    titleMedium = LingoTypography.titleMedium.emphasize(),
    titleSmall = LingoTypography.titleSmall.emphasize(),
    bodyLarge = LingoTypography.bodyLarge.emphasize(),
    bodyMedium = LingoTypography.bodyMedium.emphasize(),
    bodySmall = LingoTypography.bodySmall.emphasize(),
    labelLarge = LingoTypography.labelLarge.emphasize(),
    labelMedium = LingoTypography.labelMedium.emphasize(),
    labelSmall = LingoTypography.labelSmall.emphasize(),
)

/**
 * The reading scale, which is the app's real typography.
 *
 * It is separate from [LingoTypography] because it answers a different question. The UI scale
 * optimises for scanning controls; this one optimises for twenty minutes of German prose, and the
 * two want different typefaces, different measures and different leading.
 *
 * Body size is a user setting, so [at] takes a scale factor rather than the size being baked in.
 * Leading is derived from it: leading that does not grow with the glyphs is exactly what makes
 * enlarged text look crowded.
 */
@Immutable
data class ReadingType(
    val headline: TextStyle,
    val byline: TextStyle,
    val body: TextStyle,
    val quote: TextStyle,
    val gloss: TextStyle,
    val word: TextStyle,
) {
    fun at(scale: Float): ReadingType = copy(
        body = body.copy(
            fontSize = (body.fontSize.value * scale).sp,
            lineHeight = (body.lineHeight.value * scale).sp,
        ),
    )
}

private val readingBase = Typography()

val LingoReadingType = ReadingType(
    headline = readingBase.headlineMedium.copy(
        fontFamily = Literata,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = EvenLeading,
    ),
    byline = LingoTypography.labelMedium.copy(fontSize = 12.sp),
    body = readingBase.bodyLarge.copy(
        fontFamily = Literata,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = EvenLeading,
    ),
    quote = readingBase.bodyLarge.copy(
        fontFamily = Literata,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        lineHeightStyle = EvenLeading,
    ),
    gloss = readingBase.bodyLarge.copy(
        fontFamily = Literata,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = EvenLeading,
    ),
    word = readingBase.headlineSmall.copy(
        fontFamily = Literata,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
)

val LocalReadingType = staticCompositionLocalOf { LingoReadingType }
val LocalEmphasizedType = staticCompositionLocalOf { LingoEmphasizedType }
