package com.glossline.reader.brand

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glossline.reader.ui.theme.LingoTheme

/**
 * The mark, drawn in Compose so it can carry the theme.
 *
 * The same artwork ships as three vectors in `res/drawable` — the adaptive icon's foreground, its
 * monochrome themed-icon layer, and the splash icon — because the system draws those before any
 * code of ours runs. This is the in-app copy, and the reason it is not a re-use of the vector is
 * one colour: the marked bar here is `LingoTheme.colors.accent`, which differs between light and
 * dark mode, while the static vector is pinned to the light-mode lime and would glow wrongly on a
 * near-black surface.
 *
 * Geometry is in the 108-unit grid the vectors are authored on, and is kept in step with
 * `ic_launcher_foreground.xml`. It was scaled about the centre until its furthest point sat at
 * radius 32.5, which is inside both Android's 72dp mask and its 33dp key line; see the comment on
 * that vector, and `tools/render_icon.py` for how that was measured.
 */
private data class Bar(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float)

private val LineOne = Bar(x = 31.5f, y = 31.0f, w = 29.0f, h = 9.5f, r = 4.75f)
private val MarkedWord = Bar(x = 31.5f, y = 45.5f, w = 42.5f, h = 15.5f, r = 7.75f)
private val LineTwo = Bar(x = 31.5f, y = 65.5f, w = 33.0f, h = 9.5f, r = 4.75f)

/** The plate behind the bars, matching the icon's squircle rather than a circle. */
private const val PlateRadius = 26f

/** The bars sit on the plate at 92% opacity, so the gradient reads through them slightly. */
private val BarTint = Color(0xFFF2F0FF)

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    contentDescription: String? = null,
) {
    val lingo = LingoTheme.colors

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val unit = this.size.minDimension / 108f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(lingo.gradientStart, lingo.gradientMid, lingo.gradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height),
                ),
                topLeft = Offset(0f, 0f),
                size = Size(this.size.width, this.size.height),
                cornerRadius = CornerRadius(PlateRadius * unit, PlateRadius * unit),
            )

            listOf(LineOne, LineTwo).forEach { bar ->
                drawRoundRect(
                    color = BarTint.copy(alpha = 0.95f),
                    topLeft = Offset(bar.x * unit, bar.y * unit),
                    size = Size(bar.w * unit, bar.h * unit),
                    cornerRadius = CornerRadius(bar.r * unit, bar.r * unit),
                )
            }
            // The marked word, drawn last and taller, so height carries the idea as well as hue.
            drawRoundRect(
                color = lingo.accent,
                topLeft = Offset(MarkedWord.x * unit, MarkedWord.y * unit),
                size = Size(MarkedWord.w * unit, MarkedWord.h * unit),
                cornerRadius = CornerRadius(MarkedWord.r * unit, MarkedWord.r * unit),
            )
        }
    }
}
