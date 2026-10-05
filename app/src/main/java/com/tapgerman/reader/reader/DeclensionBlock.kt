package com.tapgerman.reader.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapgerman.reader.R
import com.tapgerman.reader.dict.DeclensionTable
import com.tapgerman.reader.ui.components.CollapsibleGrammarSection
import com.tapgerman.reader.ui.theme.LingoTheme
import com.tapgerman.reader.ui.theme.Space

/**
 * A noun's cases, folded away to the two forms a learner meets in running text.
 *
 * Gender and declension class are not repeated: the card already shows `neuter · strong` from the
 * sense tags. What is new here is the genitive and the plural, which are the forms that actually
 * appear in articles and the two a learner is most likely to meet without recognising.
 *
 * The same disclosure as the conjugation section, for the same reason, so both go through
 * [CollapsibleGrammarSection] rather than each having its own arrow.
 */
@Composable
internal fun DeclensionBlock(table: DeclensionTable) {
    if (table.isEmpty) return

    CollapsibleGrammarSection(
        title = stringResource(R.string.lookup_declension),
        // The two that matter for reading: the genitive, and the plural.
        preview = listOf(table.genitive.firstOrNull()?.form, table.nominative.getOrNull(1)?.form)
            .filterNot { it.isNullOrBlank() }
            .joinToString(" · "),
    ) {
        DeclensionGrid(table)
    }
}

// Not private: the screenshot test renders the grid on its own, for the same reason the conjugation
// one does — reaching the expanded state would mean driving a disclosure nothing else knows about.
@Composable
internal fun DeclensionGrid(table: DeclensionTable) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(LABEL_WIDTH))
            DeclensionTable.NUMBERS.forEach { number ->
                Text(
                    // The abbreviations are the ones German grammar uses, so a learner meeting "Sg."
                    // on this card meets it again in a textbook.
                    text = stringResource(
                        if (number == "singular") R.string.lookup_singular else R.string.lookup_plural,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Space.xs))

        DeclensionTable.CASE_ROWS.forEach { caseRow ->
            val cells = when (caseRow.tag) {
                "nominative" -> table.nominative
                "genitive" -> table.genitive
                "dative" -> table.dative
                else -> table.accusative
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = caseRow.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(LABEL_WIDTH),
                )
                DeclensionTable.NUMBERS.indices.forEach { index ->
                    val form = cells.getOrNull(index)?.form.orEmpty()
                    Text(
                        // Blank where the payload had nothing, which is different from a dash
                        // claiming the form does not exist.
                        text = form,
                        style = LingoTheme.reading.quote,
                        color = if (form.isEmpty()) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Room for `Akkusativ`, the longest case name. */
private val LABEL_WIDTH = 64.dp
