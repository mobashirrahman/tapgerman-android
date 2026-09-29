package com.glossline.reader.dict

import com.glossline.reader.data.InflectedForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning `gehen`'s 101 tagged rows back into a table.
 *
 * The fixture is the real payload, so these assertions are about what Kaikki actually sends rather
 * than about a table the builder and its author agree on. Every form was checked against the live
 * entry.
 */
class ConjugationTest {

    private val forms: List<InflectedForm> =
        KaikkiFixtures.GEHEN_FORMS.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val obj = org.json.JSONObject(line)
                InflectedForm(
                    form = obj.getString("form"),
                    tags = obj.getJSONArray("tags").let { tags ->
                        (0 until tags.length()).map { tags.getString(it) }
                    },
                    source = obj.optString("source").takeIf { it.isNotEmpty() },
                )
            }
            .toList()

    private val table = requireNotNull(Conjugation.tableFor(forms))

    @Test
    fun `the fixture is the real payload`() {
        assertTrue("expected a full table, got ${forms.size} rows", forms.size > 80)
    }

    @Test
    fun `the verb class and auxiliary are carried`() {
        assertEquals("7 strong", table.verbClass)
        assertEquals("sein", table.auxiliary)
    }

    @Test
    fun `the infinitive and both participles are carried`() {
        assertEquals("gehen", table.infinitive)
        assertEquals("gehend", table.presentParticiple)
        assertEquals("gegangen", table.pastParticiple)
    }

    @Test
    fun `every present-tense person is filled`() {
        assertEquals(
            listOf("gehe", "gehst", "geht", "gehen", "geht", "gehen"),
            table.present.map { it.form },
        )
    }

    @Test
    fun `every preterite person is filled`() {
        assertEquals(
            listOf("ging", "gingst", "ging", "gingen", "gingt", "gingen"),
            table.preterite.map { it.form },
        )
    }

    @Test
    fun `the first subjunctive is present and preterite`() {
        // Konjunktiv I of gehen is present-shaped: gehe, gehest, gehe, gehen, gehet, gehen. The
        // payload tags it only as `subjunctive` plus a tense, so this is the row that motivated
        // tolerating a bare `subjunctive` tag.
        assertEquals(
            listOf("gehe", "gehest", "gehe", "gehen", "gehet", "gehen"),
            table.subjunctiveI.map { it.form },
        )
    }

    @Test
    fun `the second subjunctive is past-shaped`() {
        assertEquals(
            listOf("ginge", "gingest", "ginge", "gingen", "ginget", "gingen"),
            table.subjunctiveII.map { it.form },
        )
    }

    @Test
    fun `the imperative covers du and ihr`() {
        assertTrue(table.imperative.any { it.form == "geh" })
        assertTrue(table.imperative.any { it.form == "geht" })
    }

    @Test
    fun `the person rows are the ones German teaches`() {
        assertEquals(
            listOf("ich", "du", "er/sie/es", "wir", "ihr", "sie"),
            ConjugationTable.PERSON_ROWS.map { it.label },
        )
    }

    @Test
    fun `every column has a cell for every person row`() {
        listOf(table.present, table.preterite, table.subjunctiveI, table.subjunctiveII, table.imperative)
            .forEach { column ->
                assertEquals(ConjugationTable.PERSON_ROWS.size, column.size)
            }
    }

    // ---- when there is no table ----

    @Test
    fun `a word with no forms has no table`() {
        assertNull(Conjugation.tableFor(emptyList()))
    }

    @Test
    fun `a noun has no table`() {
        // A noun's `forms` are derived words, and the parser keeps only table rows, so this is the
        // shape the card actually receives for one.
        val nounForms = listOf(
            InflectedForm("Hauses", listOf("genitive")),
            InflectedForm("Häuser", listOf("plural")),
        )
        assertNull(Conjugation.tableFor(nounForms))
    }

    @Test
    fun `a verb with only a loose form has no table`() {
        // Better no table than one cell and four blanks: the card should not draw a conjugation
        // section for a word whose conjugation was not in the payload.
        val loose = listOf(InflectedForm("ging", listOf("past")))
        assertNull(Conjugation.tableFor(loose))
    }

    @Test
    fun `a sparse payload leaves blanks rather than shifting forms`() {
        // A missing cell must not slide the next person's form into its place, which is what a
        // builder that collected the first match per person without checking would do.
        val sparse = listOf(
            InflectedForm("gehe", listOf("indicative", "present", "first-person", "singular")),
            InflectedForm("geht", listOf("indicative", "present", "third-person", "singular")),
        )
        val built = requireNotNull(Conjugation.tableFor(sparse))
        assertEquals("gehe", built.present[0].form)
        assertEquals("", built.present[1].form)
        assertEquals("geht", built.present[2].form)
        assertEquals("", built.present[3].form)
    }

    @Test
    fun `an unmarked subjunctive goes to the first, not to neither`() {
        val bare = listOf(
            InflectedForm("gehe", listOf("indicative", "present", "first-person", "singular")),
            InflectedForm("ginge", listOf("subjunctive", "preterite", "first-person", "singular")),
        )
        val built = requireNotNull(Conjugation.tableFor(bare))
        // A bare `subjunctive` is Konjunktiv I by implication: Konjunktiv II always says so.
        assertEquals("ginge", built.subjunctiveI[0].form)
        assertEquals("", built.subjunctiveII[0].form)
    }

    @Test
    fun `the table knows when it is empty`() {
        assertTrue(table.isEmpty.not())
        assertTrue(ConjugationTable().isEmpty)
    }
}
