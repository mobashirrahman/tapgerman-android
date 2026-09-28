package com.lingodeck.reader.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lingodeck.reader.ui.theme.LingoTheme

/**
 * The mark, drawn in Compose so it can carry the theme's colours.
 *
 * The same artwork ships as three vectors in `res/drawable/`: the adaptive icon's foreground, its
 * monochrome themed-icon layer, and the splash icon. Those have to be vectors because the system
 * draws them before any code of ours runs. This is the in-app copy, and the reason it exists rather
 * than reusing the vector is that the accent here is `LingoTheme.colors.accent`, which changes with
 * light and dark mode — the static vector is pinned to the light-mode lime and would glow wrongly
 * on a near-black surface.
 *
 * The geometry matches the vector exactly: a page plate, two ink lines, and one line drawn taller
 * in the accent so it reads as marked rather than merely differently coloured.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    contentDescription: String? = null,
) {
    val lingo = LingoTheme.colors
    val markColor = Color.White
    val inkColor = Material3Ink(lingo.gradientStart)

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            // The mark is authored on a 26..82 grid inside a 108 viewport, so it is drawn on the
            // same 0..1 fractions here rather than on raw units.
            val unit = this.size.minDimension / 108f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(lingo.gradientStart, lingo.gradientMid, lingo.gradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height),
                ),
                topLeft = Offset(0f, 0f),
                size = Size(this.size.width, this.size.height),
                cornerRadius = CornerRadius(26f * unit, 26f * unit),
            )

            drawRoundRect(
                color = markColor,
                topLeft = Offset(26f * unit, 24f * unit),
                size = Size(56f * unit, 60f * unit),
                cornerRadius = CornerRadius(14f * unit, 14f * unit),
            )

            drawLine(
                color = inkColor,
                start = Offset(36f * unit, 38f * unit),
                end = Offset(74f * unit, 38f * unit),
                strokeWidth = 8f * unit,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawLine(
                color = inkColor,
                start = Offset(36f * unit, 73f * unit),
                end = Offset(70f * unit, 73f * unit),
                strokeWidth = 8f * unit,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            // Taller than the lines around it, which is what makes it read as highlighted.
            drawLine(
                color = lingo.accent,
                start = Offset(36f * unit, 56.5f * unit),
                end = Offset(66f * unit, 56.5f * unit),
                strokeWidth = 13f * unit,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/** The ink the mark's text lines are cut in, derived from the gradient's dark end. */
private fun Material3Ink(gradientStart: Color): Color = gradientStart.copy(alpha = 0.92f)

@Suppress("unused")
private fun DrawScope.unusedReference() = Unit
