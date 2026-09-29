package com.glossline.reader.dict

import com.glossline.reader.data.InflectedForm

/**
 * A German noun's declension, rebuilt from the tags Kaikki puts on each form.
 *
 * The extraction ships the grid as separate rows — `Haus` tagged `nominative, singular`,
 * `Häusern` tagged `dative, definite, plural` — and this puts it back together.
 *
 * Two details in the data are worth knowing before changing anything here.
 *
 * The plural rows are all tagged `definite`, because a German plural dative is written with an
 * article and Wiktionary's table marks it that way. The *form* string is still just `Häuser` with
 * no article in it, so those rows are the only plural source there is and are used as they stand.
 * Bare rows are preferred wherever both exist.
 *
 * Several nouns have two correct forms in one cell — `Kind` is genitive `Kindes` or `Kinds`, `Haus`
 * is dative `Haus` or `Hause`. Both are listed, joined with "or", in the order the payload gives
 * them. Which one is more common is not something this data says, and guessing would be worse than
 * showing both.
 */
data class DeclensionTable(
    val nominative: List<Cell> = emptyList(),
    val genitive: List<Cell> = emptyList(),
    val dative: List<Cell> = emptyList(),
    val accusative: List<Cell> = emptyList(),
) {
    data class Cell(val form: String)

    val isEmpty: Boolean
        get() = listOf(nominative, genitive, dative, accusative).all { it.isEmpty() }

    companion object {
        /** The four cases, in the order a German learner is taught them. */
        val CASE_ROWS = listOf(
            CaseRow("Nominativ", "nominative"),
            CaseRow("Genitiv", "genitive"),
            CaseRow("Dativ", "dative"),
            CaseRow("Akkusativ", "accusative"),
        )

        /** Columns, singular first. */
        val NUMBERS = listOf("singular", "plural")
    }

    data class CaseRow(val label: String, val tag: String)
}

/**
 * Builds a [DeclensionTable] from parsed [InflectedForm]s, or null when there is nothing to show.
 */
object Declension {

    fun tableFor(forms: List<InflectedForm>): DeclensionTable? {
        // A table needs at least one case, and the nominative is the one every noun has: without it
        // this is a verb's conjugation rows or a list of derived words, not a declension.
        val hasCase = forms.any { form ->
            DeclensionTable.CASE_ROWS.any { row -> row.tag in form.tags }
        }
        if (!hasCase) return null

        fun column(tag: String) = DeclensionTable.NUMBERS.map { number ->
            DeclensionTable.Cell(cellFor(forms, tag, number))
        }

        return DeclensionTable(
            nominative = column("nominative"),
            genitive = column("genitive"),
            dative = column("dative"),
            accusative = column("accusative"),
        )
    }

    /**
     * One cell: the forms for a case and number, joined, bare ones preferred.
     *
     * Joining rather than picking is deliberate — see the note on [DeclensionTable]. Two forms in a
     * cell is correct German, and showing one of them would be a table that is quietly wrong.
     */
    private fun cellFor(forms: List<InflectedForm>, case: String, number: String): String {
        val inCell = forms.filter { form ->
            form.form.isNotEmpty() && case in form.tags && number in form.tags
        }
        if (inCell.isEmpty()) return ""
        val bare = inCell.filterNot { "definite" in it.tags }
        val chosen = bare.ifEmpty { inCell }
        return chosen.map { it.form }.distinct().joinToString(" or ")
    }
}
