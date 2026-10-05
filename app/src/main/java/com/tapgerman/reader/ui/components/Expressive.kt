package com.tapgerman.reader.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tapgerman.reader.ui.theme.Space

/**
 * The Material 3 Expressive components, rebuilt.
 *
 * `LoadingIndicator`, `ContainedLoadingIndicator`, `ButtonGroup` and `SplitButton` only exist in
 * material3 1.5.0-alpha, and every 1.5.0 alpha declares `minCompileSdk 37` / `minAGP 9.1`,
 * which drags the graph to Compose 1.12 and forces an Android Gradle Plugin 9 migration. That
 * is a toolchain migration, and folding one into a UI redesign makes the redesign unreviewable.
 * What stable 1.4.0 does carry is `MaterialExpressiveTheme` and `MotionScheme`, which is the
 * part that actually decides how the app feels, so these are written on top of that token set.
 *
 * When material3 1.5.0 goes stable and a BOM can carry it, each of these should collapse to a
 * re-export of the library version.
 */

// --- Morphing loading indicator ---------------------------------------------------

/**
 * A shape that continuously morphs between a circle, a rounded square and a squircle while the
 * whole thing rotates.
 *
 * This is the most recognisable piece of Material 3 Expressive, and it replaces the pre-redesign
 * `CircularProgressIndicator` for a reason past looks: both places this app waits on the network,
 * fetching an article and looking up a word, can take several seconds, and a spinner that keeps
 * changing shape is visibly *alive* while a circle just sits there.
 *
 * The morph interpolates four corner radii on a loop rather than morphing between two paths,
 * which keeps it short and renders identically on every API level from 26 up.
 */
@Composable
fun MorphingLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    thickness: Dp = 3.dp,
) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "lingo-loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    Canvas(modifier = modifier.size(size)) {
        val stroke = thickness.toPx()
        val side = minOf(this.size.width, this.size.height) - stroke
        val radius = (side / 2f) * morphRadiusFraction(phase)
        rotate(degrees = spin, pivot = Offset(this.size.width / 2f, this.size.height / 2f)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(side, side),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = stroke),
            )
        }
    }
}

/** Fully round at the ends of the loop, near-square in the middle, with eased shoulders. */
private fun morphRadiusFraction(phase: Float): Float {
    val triangle = 1f - kotlin.math.abs(2f * phase - 1f) // 0 at both ends, 1 in the middle
    val eased = triangle * triangle * (3f - 2f * triangle)
    return 0.5f - (0.5f - 0.08f) * eased
}

/** The contained variant: a large accent disc with the morph running inside it. */
@Composable
fun ContainedMorphingLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        contentColor = color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MorphingLoadingIndicator(size = size * 0.42f, color = color)
        }
    }
}

// --- Button group -----------------------------------------------------------------

/**
 * A row of related buttons that push each other aside on press while holding a constant total
 * width.
 *
 * This is the expressive answer to "two or three actions that belong together", and it is the
 * right control for the reader's Save / Send to Anki pair. The pre-redesign app rendered that
 * pair as two equal buttons in a plain `Row`, so the only press feedback was a ripple that
 * stopped at the button edge and no sense that the two were related at all.
 *
 * The squeeze is a weighted `Row`: the pressed child gains weight and its neighbours give up an
 * equal share, so the group's total width never changes and nothing on screen shifts. Corners are
 * interpolated, so a pressed button squares off its inner corners against its neighbour while its
 * outer corners stay round.
 *
 * The caller supplies each child's index and the group size rather than the composable
 * discovering them. That is less clever than a `SubcomposeLayout` and considerably more
 * predictable: the weights are plain values, so a press recomposes one button and remeasures the
 * row, instead of a layout pass re-measuring every child.
 */
@Composable
fun LingoButtonGroup(
    modifier: Modifier = Modifier,
    expandedIndex: Int? = null,
    spacing: Dp = 2.dp,
    content: @Composable LingoButtonGroupScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scope = LingoButtonGroupScopeImpl(this, expandedIndex)
        scope.content()
    }
}

/**
 * Exposes the two things a child in the group needs: a width that responds to a press, and the
 * matching shape. The [RowScope] is held rather than inherited — `RowScope` has five abstract
 * members, three of them `alignBy` overloads this group has no use for, and delegating to it
 * beats reimplementing the layout contract.
 */
interface LingoButtonGroupScope {
    /**
     * Width for the child at [index] of [count]. Apply to the button's own modifier, together with
     * [groupShape] for the same index. An extension on Modifier rather than a factory returning
     * one, so it chains.
     */
    fun Modifier.groupButton(index: Int, count: Int): Modifier

    /** The shape for a button that is currently pressed. */
    fun groupShape(index: Int, count: Int, pressed: Boolean): Shape
}

private class LingoButtonGroupScopeImpl(
    private val rowScope: RowScope,
    private val expandedIndex: Int?,
) : LingoButtonGroupScope {

    override fun Modifier.groupButton(index: Int, count: Int): Modifier =
        with(rowScope) { this@groupButton.weight(groupWeight(index, count)) }

    override fun groupShape(index: Int, count: Int, pressed: Boolean): Shape {
        val outer = 20.dp
        val inner = if (pressed) 6.dp else 12.dp
        val isFirst = index == 0
        val isLast = index == count - 1
        return RoundedCornerShape(
            topStart = if (isFirst) outer else inner,
            bottomStart = if (isFirst) outer else inner,
            topEnd = if (isLast) outer else inner,
            bottomEnd = if (isLast) outer else inner,
        )
    }

    /**
     * The pressed child gains [EXPANSION] and every other child gives up an equal share, so the
     * weights still sum to the group size and the row's total width is unchanged. A lone child
     * has nobody to squeeze, so it does not move.
     */
    private fun groupWeight(index: Int, count: Int): Float {
        if (count <= 1) return 1f
        if (index == expandedIndex) return 1f + EXPANSION
        return (1f - EXPANSION / (count - 1)).coerceAtLeast(0.55f)
    }

    private companion object {
        /** How much wider a pressed button gets, as a fraction of a normal child's weight. */
        const val EXPANSION = 0.45f
    }
}

// --- Split button -----------------------------------------------------------------

/**
 * A primary action with a related secondary action behind a divider.
 *
 * Built for "Send to Anki" with a trailing menu: the common case stays one large target, and the
 * occasional case is one tap away instead of buried in an overflow menu at the far end of a bar.
 */
@Composable
fun LingoSplitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    leadingIcon: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(
            topStart = 20.dp,
            bottomStart = 20.dp,
            topEnd = 4.dp,
            bottomEnd = 4.dp,
        ),
        colors = colors,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(Space.sm))
        }
        text()
    }
}

/** The trailing half of a [LingoSplitButton]: square inner corners, a divider on its right. */
@Composable
fun LingoSplitButtonMenu(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = ButtonDefaults.buttonColors().containerColor,
    contentColor: Color = ButtonDefaults.buttonColors().contentColor,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

// --- Brushes ----------------------------------------------------------------------

/** The three-stop brand gradient, used by the hero card, the icon and the splash. */
fun brandGradient(start: Color, mid: Color, end: Color): Brush = Brush.linearGradient(
    colors = listOf(start, mid, end),
)
