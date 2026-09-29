package com.glossline.reader.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.glossline.reader.R
import com.glossline.reader.data.Etymology
import com.glossline.reader.data.RelatedWords
import com.glossline.reader.ui.theme.LingoTheme
import com.glossline.reader.ui.theme.Space

/**
 * Where a word came from, and what it sits next to.
 *
 * The etymology is one line of descent rather than a section: the intermediate forms are what make
 * a word memorable — *hūs* becoming Haus across five languages is why the word feels like a house
 * rather than a label — and a column of stage names would bury that. When a word has a second root,
 * the extra chains are counted rather than printed, because two lines of descent is already a wall
 * on a phone.
 *
 * The related words are chips because they are not facts, they are doors: the whole point of an
 * antonym is that the reader wants to go and look at it.
 */
@Composable
internal fun WordRelationsBlock(
    etymology: Etymology,
    related: RelatedWords,
    onLookupWord: (String) -> Unit,
) {
    val descent = etymology.primary
    val extraRoots = (etymology.chains.size - 1).coerceAtLeast(0)

    if (descent.isNotEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.lookup_etymology),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // A wrapping paragraph rather than one line per stage: a six-stage descent is a
                // long sentence in a narrow column, and a list of six one-word rows would be a wall.
                // The card scrolls, so the tail being below the fold is fine.
                text = descent.joinToString(" → ") { stage ->
                    // The last stage is the word itself, which the header already says. Repeating it
                    // in the line of descent wastes the most interesting position in the sentence.
                    if (stage.language == "German") stage.form else "${stage.language} ${stage.form}"
                },
                style = LingoTheme.reading.quote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
            if (extraRoots > 0) {
                // Its own line, in the label style: it is a note about the data, not part of the
                // descent, and appended to the sentence it reads as a form the word took.
                // Stated rather than hidden, because silently showing one of two roots would be a
                // claim the data does not support.
                Text(
                    text = pluralStringResource(R.plurals.lookup_more_roots, extraRoots, extraRoots),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (!related.isEmpty) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            RelatedRow(
                label = stringResource(R.string.lookup_antonyms),
                words = related.antonyms,
                onLookupWord = onLookupWord,
            )
            RelatedRow(
                label = stringResource(R.string.lookup_related),
                words = related.related,
                onLookupWord = onLookupWord,
            )
            RelatedRow(
                label = stringResource(R.string.lookup_derived),
                words = related.derived,
                onLookupWord = onLookupWord,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedRow(
    label: String,
    words: List<String>,
    onLookupWord: (String) -> Unit,
) {
    if (words.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            words.forEach { word ->
                Surface(
                    onClick = { onLookupWord(word) },
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = word,
                        style = LingoTheme.reading.quote,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(horizontal = Space.sm, vertical = 5.dp)
                            // The chip is a link, not a label, and a screen reader announcing only
                            // "kommen" gives no idea that tapping it goes somewhere.
                            .semantics { contentDescription = "$label: $word" },
                    )
                }
            }
        }
    }
}
