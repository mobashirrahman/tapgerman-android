package com.glossline.reader.dict

import com.glossline.reader.data.InflectedForm

/**
 * A German verb's conjugation, rebuilt from the tags Kaikki puts on each form.
 *
 * Wiktionary renders these as a table server-side; the extraction ships it as 111 separate rows
 * for `gehen`, each labelled with the grammar that produced it. Turning those rows back into a grid
 * is mostly bookkeeping, and the bookkeeping is the whole feature — a reader who taps a verb in an
 * article is almost always looking at an inflected form and needs to see the rest of the paradigm.
 *
 * The table is deliberately not exhaustive. A German verb also has Perfekt, Plusquamperfekt, Futur I
 * and Futur II, which are compounds of an auxiliary and a participle rather than rows of their own,
 * and the payload does not carry them as separate cells. Inventing them would mean conjugating in
 * the app, which is a different and much larger thing to be right about; the participles and the
 * auxiliary are carried instead, which is enough to build them mentally.
 */
data class ConjugationTable(
    /** The verb class, e.g. `7 strong` for `gehen`. Absent for verbs with no class. */
    val verbClass: String? = null,
    val auxiliary: String? = null,
    val infinitive: String? = null,
    val presentParticiple: String? = null,
    val pastParticiple: String? = null,
    /** Rows of [Cell], ordered by [PERSON_ROWS]. Empty slots mean Kaikki did not list that form. */
    val present: List<Cell> = emptyList(),
    val preterite: List<Cell> = emptyList(),
    val subjunctiveI: List<Cell> = emptyList(),
    val subjunctiveII: List<Cell> = emptyList(),
    /** Only `du` and `ihr` have a distinct imperative; the rest is the present form. */
    val imperative: List<Cell> = emptyList(),
) {
    /** One cell of the table. */
    data class Cell(val form: String)

    /** Whether there is anything to show. A noun produces nothing. */
    val isEmpty: Boolean
        get() = present.isEmpty() && preterite.isEmpty() && subjunctiveI.isEmpty() &&
            subjunctiveII.isEmpty() && imperative.isEmpty()

    /**
     * The person rows, in the order German teaches them, with the pronoun each one is built on.
     *
     * The third-person plural row is the formal *Sie*, which is why the labels are not simply the
     * six personal pronouns. German has one word for "she", "they" and formal "you", told apart only
     * by case and politeness, and a table that printed `sie` three times would teach nothing.
     */
    companion object {
        val PERSON_ROWS = listOf(
            PersonRow("ich", "first-person", "singular"),
            PersonRow("du", "second-person", "singular"),
            PersonRow("er/sie/es", "third-person", "singular"),
            PersonRow("wir", "first-person", "plural"),
            PersonRow("ihr", "second-person", "plural"),
            PersonRow("sie", "third-person", "plural"),
        )
    }

    data class PersonRow(val label: String, val person: String, val number: String)
}

/**
 * Builds a [ConjugationTable] from parsed [InflectedForm]s, or null when the word is not a verb.
 *
 * Null rather than an empty table so the card can simply not draw a conjugation section, and so the
 * reason a table is missing is legible: a noun has no conjugation, and a verb with no `forms` is
 * missing data rather than having none.
 */
object Conjugation {

    fun tableFor(forms: List<InflectedForm>): ConjugationTable? {
        // A table needs at least a present-tense row; anything less is not a conjugation.
        val indicative = forms.filter { FORM.hasMood(it, "indicative") }
        if (indicative.isEmpty()) return null

        return ConjugationTable(
            verbClass = forms.firstOrNull { "class" in it.tags }?.form,
            auxiliary = forms.firstOrNull { "auxiliary" in it.tags }?.form,
            infinitive = forms.firstOrNull { "infinitive" in it.tags }?.form,
            presentParticiple = participle(forms, "present"),
            pastParticiple = participle(forms, "past"),
            present = column(forms, "indicative", "present"),
            preterite = column(forms, "indicative", "preterite"),
            subjunctiveI = column(forms, "subjunctive-i"),
            subjunctiveII = column(forms, "subjunctive-ii"),
            imperative = column(forms, "imperative"),
        )
    }

    /** The six person rows of one mood/tense, with blanks where nothing was listed. */
    private fun column(
        forms: List<InflectedForm>,
        mood: String,
        tense: String? = null,
    ): List<ConjugationTable.Cell> = ConjugationTable.PERSON_ROWS.map { row ->
        val match = forms.firstOrNull { form ->
            FORM.hasMood(form, mood) &&
                (tense == null || tense in form.tags) &&
                row.person in form.tags &&
                row.number in form.tags
        }
        ConjugationTable.Cell(match?.form.orEmpty())
    }

    private fun participle(forms: List<InflectedForm>, tense: String): String? =
        forms.firstOrNull { it.tags.containsAll(listOf("participle", tense)) }?.form

    /**
     * Mood matching, tolerating the spelling Kaikki uses for the two subjunctives.
     *
     * The payload says `subjunctive-i` and `subjunctive-ii`, but it also reaches for `subjunctive`
     * on rows that name only the tense — Konjunktiv I is Konjunktiv, present, and the `-i` is
     * implied by the absence of `-ii`. Treating those rows as belonging to no mood would drop half
     * of both subjunctive columns.
     */
    private object FORM {
        fun hasMood(form: InflectedForm, mood: String): Boolean = when (mood) {
            "subjunctive-i" -> "subjunctive-i" in form.tags ||
                ("subjunctive" in form.tags && "subjunctive-ii" !in form.tags)
            "subjunctive-ii" -> "subjunctive-ii" in form.tags
            else -> mood in form.tags
        }
    }
}
