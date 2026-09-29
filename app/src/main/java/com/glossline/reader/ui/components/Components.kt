package com.glossline.reader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glossline.reader.ui.theme.Gutter
import com.glossline.reader.ui.theme.LingoTheme
import com.glossline.reader.ui.theme.Space

/**
 * The pieces every screen is built from.
 *
 * The pre-redesign app had none of these: each of the three screens hand-rolled its own card,
 * its own empty text and its own error line, which is why they ended up looking like three
 * different apps rather than one product with three screens.
 */

/** A content card on [MaterialTheme.colorScheme.surfaceContainer]. */
@Composable
fun LingoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

/**
 * A section label, optionally with a trailing action.
 *
 * The count matters: "Recently read (12)" tells the user the size of the thing they are about to
 * scroll, and the pre-redesign sections had no count at all.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = LingoTheme.emphasized.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The count sits opposite the title rather than appended to it. Appended, it read as a
        // stray number floating after the label rather than as the size of what follows.
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            trailing?.invoke()
        }
    }
}

/**
 * The empty state.
 *
 * A screen with nothing in it is a moment, and the pre-redesign empty states were a single grey
 * line of body text ("Nothing here yet.") at the top of an otherwise empty scroll view. This one
 * has artwork, a headline that says what happened, a body that says what to do about it, and the
 * action itself.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = LingoTheme.colors.accent,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter.standard, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(LingoTheme.colors.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            text = title,
            style = LingoTheme.emphasized.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Space.sm))
            action()
        }
    }
}

/**
 * An inline error, as a surface rather than as red body text.
 *
 * The pre-redesign app rendered fetch failures as a bare `Text` in `colorScheme.error` wedged
 * between the input and the button, which read as a validation message and was easy to miss
 * entirely on a long screen.
 */
@Composable
fun ErrorSurface(
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
    }
}

/** A small tonal pill: a domain, a part of speech, a count, a state. */
@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A number in the library's stats row.
 *
 * The value animates from the previous one to the new one rather than snapping, because a stat
 * that changes while you are looking at it should acknowledge that it changed.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    LingoCard(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$value $label" },
        contentPadding = PaddingValues(Space.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                style = LingoTheme.emphasized.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A thin rule that respects the palette, replacing a bare `HorizontalDivider`. */
@Composable
fun HairLine(
    modifier: Modifier = Modifier,
    inset: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(LingoTheme.colors.hairline),
    )
}

/**
 * Collapses extra content away when [expanded] is false, instead of removing it.
 *
 * Used for "Show all 12 senses" in the lookup card: the reader has to be able to see that there
 * are more without the list jumping.
 */
@Composable
fun ExpandableSection(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        content()
    }
}

/** Fades its content in or out, for the reader's "tap any word" hint. */
@Composable
fun Hint(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "hint-alpha",
    )
    Box(modifier = modifier.alpha(alpha)) {
        if (alpha > 0.01f) content()
    }
}

/** Fills the available space, centred. Used by every "nothing to show yet but a spinner" state. */
@Composable
fun CenteredMessage(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

/** A horizontal spacer of [Space.sm], for call sites that read better with one. */
@Composable
fun HSpace(width: Dp = Space.sm) = Spacer(Modifier.width(width))
