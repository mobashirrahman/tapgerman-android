package com.lingodeck.reader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A deliberately round shape scale.
 *
 * Material 3 Expressive leans on larger corner radii than the baseline M3 scale: containers
 * are meant to read as soft, and the expressive component set morphs between shapes on
 * interaction, which only reads as intentional when the endpoints are clearly curved. The
 * pre-redesign app used the stock scale, so cards were 12dp and the vocabulary shelf had the
 * same silhouette as a Material demo.
 *
 * The scale still steps from square-ish to round, so hierarchy survives: a chip is tighter
 * than a card, a card tighter than a sheet, a sheet tighter than a dialog.
 */
val LingoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Fully round, for badges, avatars, the active navigation indicator and toggle pills. */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * Spacing, as named steps rather than literals.
 *
 * The pre-redesign screens used raw `.dp` values at every call site and the set that ended up
 * in use was `2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 40, 44, 320` — thirteen values with no
 * rhythm, which is why the three screens look like three different apps. This is a 4dp grid
 * with a 2dp half-step for the optical nudges, and nothing outside it.
 */
object Space {
    /** Optical correction only: 1px of a border, the inside of a tight pill. */
    val hair = 2.dp

    /** 4dp. Between an icon and its label. */
    val xs = 4.dp

    /** 8dp. Between related elements inside a card. */
    val sm = 8.dp

    /** 12dp. Between a card and its own sections. */
    val md = 12.dp

    /** 16dp. Screen gutter. */
    val lg = 16.dp

    /** 20dp. Between sibling cards. */
    val xl = 20.dp

    /** 24dp. Between a section and its header. */
    val xxl = 24.dp

    /** 32dp. Between major sections. */
    val xxxl = 32.dp

    /** 48dp. The Material minimum touch target; also the empty-state illustration. */
    val huge = 48.dp
}

/** Shared edge paddings, so every screen's gutter agrees. */
object Gutter {
    /** Standard screen inset. */
    val standard = 16.dp

    /** The reader runs wider because prose wants a measure, not a margin. */
    val reading = 20.dp
}
