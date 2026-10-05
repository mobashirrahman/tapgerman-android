package com.tapgerman.reader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapgerman.reader.ui.theme.LingoTheme
import com.tapgerman.reader.ui.theme.Space

/**
 * A reference table that folds away to a line of its most useful forms.
 *
 * Shared by the conjugation and declension sections because they want exactly the same thing: a
 * table too tall to put at the top of a card that already answers the question the reader arrived
 * with, but too useful to hide behind a menu.
 *
 * The collapsed line is a preview rather than a label, so the section is worth opening even before
 * it is opened. Collapsed by default because a grid placed above the worked example would bury the
 * reason the reader tapped the word at all.
 */
@Composable
fun CollapsibleGrammarSection(
    title: String,
    /** The forms worth seeing without expanding. Already localised and joined. */
    preview: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (preview.isBlank()) return
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = Space.xs, vertical = Space.hair),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Space.xs))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    // Rotated rather than swapped for a different icon, so the arrow is one shape
                    // that visibly turns instead of two glyphs that appear unrelated.
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = preview,
            style = LingoTheme.reading.quote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.xs),
        )

        AnimatedVisibility(visible = expanded) { content() }
    }
}
