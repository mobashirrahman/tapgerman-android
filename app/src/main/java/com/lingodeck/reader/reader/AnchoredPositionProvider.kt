package com.lingodeck.reader.reader

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Places the lookup card next to the word that was tapped, clamped to the window.
 *
 * Using a [PopupPositionProvider] rather than a plain `alignment` + `offset` is what lets the card
 * sit *at* the word and then nudge itself back on screen, instead of the reader having to chase a
 * panel that opened off-screen. The gap keeps the card from covering the word being defined.
 */
class AnchoredPositionProvider(
    private val anchor: IntOffset,
    private val gapPx: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)

        // Prefer just below the word, reading-order natural. If that runs off the bottom, flip
        // above it; if it runs off the top too (a very short window), pin to the top edge.
        var x = anchor.x
        var y = anchor.y + gapPx
        if (y > maxY) y = anchor.y - gapPx - popupContentSize.height
        if (y < 0 && popupContentSize.height <= windowSize.height) y = 0

        return IntOffset(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }
}
