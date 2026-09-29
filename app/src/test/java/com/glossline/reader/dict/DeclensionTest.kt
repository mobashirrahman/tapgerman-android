package com.glossline.reader.dict

import com.glossline.reader.data.InflectedForm
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Haus` and `Kind` turned back into case tables, from the real payloads.
 *
 * Both nouns are here because they fail differently, which is the whole of what this builder has to
 * get right: `Haus` has one form per cell but a dative that alternates, and `Kind` has a genitive
 * with two correct spellings. Between them they cover both the joining and the preference rules.
 */
class DeclensionTest {

    private fun forms(json: String): List<InflectedForm> =
        json.lineSequence().filter { it.isNotBlank() }.map { line ->
            val o = JSONObject(line)
            InflectedForm(
                form = o.getString("form"),
                tags = o.getJSONArray("tags").let { t -> (0 until t.length()).map { t.getString(it) } },
                source = o.optString("source").takeIf { it.isNotEmpty() },
            )
        }.toList()

    private val haus = forms(KaikkiFixtures.HAUS_FORMS)
    private val kind = forms(KaikkiFixtures.KIND_FORMS)
    private val hausTable = requireNotNull(Declension.tableFor(haus))
    private val kindTable = requireNotNull(Declension.tableFor(kind))

    @Test
    fun `the singular of every case is filled for Haus`() {
        assertEquals("Haus", hausTable.nominative[0].form)
        assertEquals("Hauses", hausTable.genitive[0].form)
        assertEquals("Haus or Hause", hausTable.dative[0].form)
        assertEquals("Haus", hausTable.accusative[0].form)
    }

    @Test
    fun `the plural comes from the definite rows, which are the only plural rows`() {
        // Kaikki marks a plural table that needs an article as `definite`, and the form string has
        // no article in it, so these rows are usable as they stand.
        assertEquals("Häuser", hausTable.nominative[1].form)
        assertEquals("Häuser", hausTable.genitive[1].form)
        assertEquals("Häusern", hausTable.dative[1].form)
        assertEquals("Häuser", hausTable.accusative[1].form)
    }

    @Test
    fun `two correct genitives are both listed`() {
        // Kind is genitive Kindes or Kinds. Showing one would be a table that is quietly wrong.
        assertEquals("Kindes or Kinds", kindTable.genitive[0].form)
    }

    @Test
    fun `Kind's plural and singular read correctly`() {
        assertEquals("Kind", kindTable.nominative[0].form)
        assertEquals("Kinder", kindTable.nominative[1].form)
        // Payload order, which happens to put the commoner form first for this one. Not sorted,
        // not curated: the builder has no business preferring a form it cannot justify.
        assertEquals("Kind or Kinde", kindTable.dative[0].form)
        assertEquals("Kindern", kindTable.dative[1].form)
    }

    @Test
    fun `the case rows are the four German cases in teaching order`() {
        assertEquals(
            listOf("Nominativ", "Genitiv", "Dativ", "Akkusativ"),
            DeclensionTable.CASE_ROWS.map { it.label },
        )
    }

    @Test
    fun `every case has a cell for both numbers`() {
        listOf(
            hausTable.nominative, hausTable.genitive, hausTable.dative, hausTable.accusative,
            kindTable.nominative, kindTable.genitive, kindTable.dative, kindTable.accusative,
        ).forEach { row ->
            assertEquals(DeclensionTable.NUMBERS.size, row.size)
        }
    }

    // ---- when there is no table ----

    @Test
    fun `a word with no forms has no table`() {
        assertNull(Declension.tableFor(emptyList()))
    }

    @Test
    fun `a verb's conjugation rows are not a declension`() {
        // `gehen`'s rows are all moods and persons; none is a case, so there is nothing to build.
        val verb = forms(KaikkiFixtures.GEHEN_FORMS)
        assertNull(Declension.tableFor(verb))
    }

    @Test
    fun `a noun with no case rows has no table`() {
        // Diminutives and derived words carry a gender tag and no case at all.
        val diminutive = listOf(InflectedForm("Häuschen", listOf("diminutive", "neuter")))
        assertNull(Declension.tableFor(diminutive))
    }

    @Test
    fun `a missing cell stays blank rather than shifting`() {
        val sparse = listOf(
            InflectedForm("Haus", listOf("nominative", "singular")),
            InflectedForm("Häuser", listOf("nominative", "plural")),
        )
        val built = requireNotNull(Declension.tableFor(sparse))
        assertEquals("Haus", built.nominative[0].form)
        assertEquals("Häuser", built.nominative[1].form)
        assertEquals("", built.genitive[0].form)
        assertEquals("", built.genitive[1].form)
    }

    @Test
    fun `a bare row is preferred over a definite one for the same cell`() {
        val both = listOf(
            InflectedForm("Kind", listOf("nominative", "singular")),
            InflectedForm("das Kind", listOf("definite", "nominative", "singular")),
        )
        val built = requireNotNull(Declension.tableFor(both))
        // The article is not part of the word, and a table cell reading "das Kind" teaches a case
        // ending that is not one.
        assertEquals("Kind", built.nominative[0].form)
    }

    @Test
    fun `a duplicated row does not repeat in a cell`() {
        val duplicated = listOf(
            InflectedForm("Haus", listOf("nominative", "singular")),
            InflectedForm("Haus", listOf("nominative", "singular")),
        )
        val built = requireNotNull(Declension.tableFor(duplicated))
        assertEquals("Haus", built.nominative[0].form)
    }

    @Test
    fun `the table knows when it is empty`() {
        assertFalse(hausTable.isEmpty)
        assertTrue(DeclensionTable().isEmpty)
    }
}
