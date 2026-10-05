package com.tapgerman.reader.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.tapgerman.reader.dict.ConjugationTable
import com.tapgerman.reader.ui.components.CollapsibleGrammarSection
import com.tapgerman.reader.ui.theme.LingoTheme
import com.tapgerman.reader.ui.theme.Space

/**
 * A verb's conjugation, collapsed to a line until asked for.
 *
 * Collapsed by default because the card already opens on a meaning, a context and an example, and a
 * six-by-four grid in that position would bury all three. The collapsed line is not a summary row
 * though — it carries the three forms a reader actually reaches for (he/she/it, past, participle),
 * which is enough to be worth the space it takes.
 *
 * The German is set in the reading serif, like the article and the worked examples, and the German
 * grammar around it in the UI face. A paradigm is text to be read, not a form to be filled in.
 */
@Composable
internal fun ConjugationBlock(table: ConjugationTable) {
    if (table.isEmpty) return

    CollapsibleGrammarSection(
        title = stringResource(R.string.lookup_conjugation),
        // The three forms a reader reaches for: what it looks like now, what it looked like, and the
        // form the perfect tense is built from.
        preview = listOfNotNull(
            table.present.getOrNull(2)?.form?.takeIf { it.isNotEmpty() },
            table.preterite.getOrNull(2)?.form?.takeIf { it.isNotEmpty() },
            table.pastParticiple,
        ).joinToString(" · "),
    ) {
        ConjugationGrid(table)
    }
}

// Not private: the screenshot test renders the grid on its own, because reaching the expanded state
// would mean driving a disclosure the rest of the test suite has no reason to know about.
@Composable
internal fun ConjugationGrid(table: ConjugationTable) {
    val columns = listOf(
        R.string.lookup_present to table.present,
        R.string.lookup_preterite to table.preterite,
        R.string.lookup_subjunctive_i to table.subjunctiveI,
        R.string.lookup_subjunctive_ii to table.subjunctiveII,
    ).filter { (_, cells) -> cells.any { it.form.isNotEmpty() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.hair),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(LABEL_WIDTH))
            columns.forEach { (label, _) ->
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Space.xs))

        ConjugationTable.PERSON_ROWS.forEachIndexed { rowIndex, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(LABEL_WIDTH),
                )
                columns.forEach { (_, cells) ->
                    val form = cells.getOrNull(rowIndex)?.form.orEmpty()
                    Text(
                        // A cell Kaikki did not list stays blank rather than showing a dash. A dash
                        // reads as "this form does not exist"; a blank reads as "we do not know",
                        // which is the honest one, and German does have forms this payload omits.
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

        // Auxiliary and class: what Perfekt is built from, and where the verb sits in the system.
        val footer = listOfNotNull(
            table.infinitive,
            table.pastParticiple,
            table.auxiliary?.let { "mit $it" },
            table.verbClass,
        ).joinToString(" · ")
        if (footer.isNotEmpty()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xs),
            )
        }
    }
}

/**
 * Room for `er/sie/es`, the longest label, at the label size.
 *
 * Narrower than this and it wraps onto a second line, which does not look like a label any more —
 * it looks like a mistake, and it throws the row heights out so the forms stop lining up. 64dp
 * fits it and still leaves roughly 78dp per form column on a 411dp screen, which is far more than
 * `gingest` needs.
 */
private val LABEL_WIDTH = 64.dp
