package com.lingodeck.reader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.lingodeck.reader.data.Article
import com.lingodeck.reader.data.DictEntry
import com.lingodeck.reader.data.DictResult
import com.lingodeck.reader.data.ThemeMode
import com.lingodeck.reader.data.VocabItem
import com.lingodeck.reader.library.LibraryScreen
import com.lingodeck.reader.reader.ConjugationGrid
import com.lingodeck.reader.dict.WordRelations
import com.lingodeck.reader.reader.DeclensionGrid
import com.lingodeck.reader.reader.WordRelationsBlock
import com.lingodeck.reader.reader.LookupCard
import com.lingodeck.reader.reader.ReaderScreen
import com.lingodeck.reader.settings.SettingsScreen
import com.lingodeck.reader.words.DictionaryScreen
import com.lingodeck.reader.store.Settings
import com.lingodeck.reader.data.Example
import com.lingodeck.reader.data.InflectedForm
import com.lingodeck.reader.data.Sense
import com.lingodeck.reader.ui.LookupUi
import com.lingodeck.reader.ui.SessionWord
import com.lingodeck.reader.ui.UiState
import com.lingodeck.reader.ui.theme.LingoDeckTheme
import com.lingodeck.reader.util.VocabFilter
import com.lingodeck.reader.util.VocabSort
import com.lingodeck.reader.words.WordListScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the four screens, in both brightnesses and in their empty states.
 *
 * The project had no UI test coverage of any kind before the redesign, which is a poor thing to
 * hand a redesign to: nothing catches a colour that regresses, a card that loses its padding, a
 * screen that goes blank in dark mode, or an empty state that stops explaining itself. These
 * capture each screen against a fixed state, so a change anywhere in the design system shows up
 * as a diff rather than as a surprise on a device.
 *
 * Each screen is rendered directly rather than through `AppShell`. That is deliberate. The four
 * screens are already data-in / callbacks-out, whereas `AppShell` takes a concrete `MainViewModel`,
 * and driving it would mean either constructing a real one against the real JSON store — which
 * would make a screenshot depend on whatever happens to be on disk — or extracting an interface
 * over thirty methods purely for this. The cost is that the bottom navigation bar is not in these
 * images; everything above it is, including each screen's own collapsing top bar.
 *
 * Robolectric rather than an instrumented test, so `./gradlew :app:testDebugUnitTest` covers the
 * UI as well and no device is needed. Robolectric fetches its Android runtime on first run, which
 * is why the first execution is slow and the rest are not.
 *
 * Recording new baselines:
 *
 *     ./gradlew :app:recordRoborazziDebug
 *
 * Comparing, which is what an ordinary run does:
 *
 *     ./gradlew :app:verifyRoborazziDebug
 *
 * The committed PNGs are the point: they live in `app/src/test/screenshots/`, and a screenshot
 * test that does not commit its images is a test that cannot fail.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
// The v1 createComposeRule is deprecated in favour of androidx.compose.ui.test.junit4.v2, which
// switches to a StandardTestDispatcher and so needs explicit synchronisation. For screenshot
// capture there is nothing to synchronise — the capture happens after setContent returns — so
// the deprecated-but-working v1 rule is kept rather than adding that hand for no gain.
@Suppress("DEPRECATION")
class AppScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun libraryLight() = screen("library-light") { Library(Fixtures.library) }

    @Test
    fun libraryDark() = screen("library-dark", ThemeMode.Dark) { Library(Fixtures.library) }

    @Test
    fun libraryEmpty() = screen("library-empty") { Library(UiState()) }

    @Test
    fun readerLight() = screen("reader-light") { Reader() }

    // The card as it appears for a verb: grammatical tags, a worked example with its translation,
    // and the article sentence with the tapped word marked. All of that is real Kaikki payload,
    // which is the point — this is the thing the previous card threw away.
    @Test
    fun lookupCardForAVerb() = screen("lookup-card-verb") { Lookup(Fixtures.verbLookup) }

    @Test
    fun readerDark() = screen("reader-dark", ThemeMode.Dark) { Reader() }

    @Test
    fun wordsLight() = screen("words-light") { Words(Fixtures.vocab) }

    @Test
    fun wordsDark() = screen("words-dark", ThemeMode.Dark) { Words(Fixtures.vocab) }

    @Test
    fun wordsEmpty() = screen("words-empty") { Words(emptyList()) }

    // A dictionary lookup with no article behind it: no sentence, no position. The card has to be
    // the same card, which is the whole point of sharing it rather than writing a second one.
    @Test
    fun dictionaryWithAResult() = screen("dictionary-result") {
        Dictionary(
            state = UiState(lookup = Fixtures.verbLookup.copy(sentence = "", paragraphIndex = null, start = null, end = null)),
        )
    }

    @Test
    fun dictionaryWithRecentLookups() = screen("dictionary-recent") {
        Dictionary(
            state = UiState(
                recentLookups = listOf("Entlastungen", "Bundesregierung", "Maßnahme", "Sparpaket"),
            ),
        )
    }

    @Test
    fun lookupCardForANoun() = screen("lookup-card-noun") { Lookup(Fixtures.nounLookup) }

    // The conjugation section, collapsed: the three forms a reader reaches for, on one line.
    @Test
    fun lookupCardWithConjugationCollapsed() = screen("lookup-card-conjugation") {
        Lookup(Fixtures.gehenLookup)
    }

    // The full grid. Its own baseline because the collapsed line is all the card screenshot shows,
    // and a conjugation table that has never been rendered is a conjugation table that does not work.
    @Test
    fun conjugationGrid() = screen("conjugation-grid") {
        val table = requireNotNull(
            com.lingodeck.reader.dict.Conjugation.tableFor(Fixtures.gehenForms),
        )
        Column(Modifier.padding(16.dp)) {
            ConjugationGrid(table)
        }
    }

    // Descent and the words a verb sits next to. `gehen` is the one with two roots and two
    // antonyms, so it exercises every branch of the relations block at once.
    @Test
    fun lookupCardWithRelations() = screen("lookup-card-relations") {
        Lookup(
            Fixtures.gehenLookup.copy(
                result = Fixtures.gehenLookup.result?.copy(
                    entries = listOf(
                        requireNotNull(Fixtures.gehenLookup.result?.entries?.first()).copy(
                            etymology = WordRelations.parseEtymology(
                                com.lingodeck.reader.dict.KaikkiFixtures.GEHEN_ETYMOLOGY,
                            ),
                            related = WordRelations.relatedWords(
                                antonyms = listOf("kommen", "rennen"),
                                related = listOf("gehts", "fahren"),
                                derived = listOf("abgehen", "angehen", "aufgehen", "eingehen"),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    // The relations block on its own. The card baseline can only show the first line of a six-stage
    // descent before the fold, so without this the chips have never been looked at.
    @Test
    fun wordRelationsBlock() = screen("word-relations") {
        Column(Modifier.padding(16.dp)) {
            WordRelationsBlock(
                etymology = WordRelations.parseEtymology(
                    com.lingodeck.reader.dict.KaikkiFixtures.GEHEN_ETYMOLOGY,
                ),
                related = WordRelations.relatedWords(
                    antonyms = listOf("kommen", "rennen"),
                    related = listOf("gehts", "fahren"),
                    derived = listOf("abgehen", "angehen", "aufgehen", "eingehen"),
                ),
                onLookupWord = {},
            )
        }
    }

    // A noun's collapsed declension line, in card context, beside the verb's.
    @Test
    fun lookupCardWithDeclension() = screen("lookup-card-declension") { Lookup(Fixtures.hausLookup) }

    // The noun case table on its own, for the same reason the conjugation grid has one: the card
    // baseline only ever shows it collapsed.
    @Test
    fun declensionGrid() = screen("declension-grid") {
        val table = requireNotNull(
            com.lingodeck.reader.dict.Declension.tableFor(Fixtures.hausForms),
        )
        Column(Modifier.padding(16.dp)) {
            DeclensionGrid(table)
        }
    }

    @Test
    fun settingsLight() = screen("settings-light") { SettingsScreenView() }

    @Test
    fun settingsDark() = screen("settings-dark", ThemeMode.Dark) { SettingsScreenView() }

    /**
     * Renders [content] inside the app's real theme and captures the root.
     *
     * The theme is the point. A screenshot taken outside it would not catch a palette, typography
     * or shape regression, which is most of what there is to catch here.
     */
    private fun screen(
        name: String,
        theme: ThemeMode = ThemeMode.Light,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            LingoDeckTheme(themeMode = theme) {
                Surface { content() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

// --- Thin wrappers, so each test states only what it is actually varying ------------------
// Every callback is a no-op: a screenshot test is about how a fixed state renders, and driving
// the interactions belongs in a UI test, not here.

private val NoPadding = PaddingValues(0.dp)

@Composable
private fun Library(state: UiState) = LibraryScreen(
    state = state,
    contentPadding = NoPadding,
    onReadUrl = {},
    onOpenArticle = {},
    onRemoveArticle = {},
    onOpenWords = {},
    onOpenSettings = {},
)

@Composable
private fun Reader() = ReaderScreen(
    article = Fixtures.article,
    settings = Settings(),
    sessionWords = listOf(
        SessionWord("Entlastung", "relief, easing (of a burden)", saved = true),
        SessionWord("Freibetrag", "tax allowance, exemption", saved = false),
    ),
    savedWords = setOf("entlastung"),
    pendingJump = null,
    openLookup = null,
    contentPadding = NoPadding,
    onBack = {},
    onTapWord = { _, _, _, _ -> },
    onLongPressWord = {},
    onProgress = {},
    onConsumeJump = {},
    onTextScale = {},
)

/**
 * The lookup card, shown in a Box rather than as a Popup.
 *
 * A real lookup is a `Popup`, which is a separate window and cannot be composed inside a test's
 * content, so the card composable is rendered directly on a surface of the same colour. Everything
 * inside the card is what the Popup would draw; only the floating-window part is absent.
 */
@Composable
private fun Lookup(state: LookupUi) {
    LookupCard(
        lookup = state,
        onDismiss = {},
        onSave = {},
        onSendToAnki = {},
        onChooseGloss = {},
        onSpeak = {},
        onToggleAllSenses = {},
        isShowingAllSenses = false,
        onLookupWord = {},
    )
}

/** The dictionary screen, with the callbacks inert. */
@Composable
private fun Dictionary(state: UiState) = DictionaryScreen(
    state = state,
    contentPadding = NoPadding,
    onLookup = {},
    onClearRecent = {},
    onDismiss = {},
    onSave = {},
    onSendToAnki = {},
    onSpeak = {},
    onLookupWord = {},
)

@Composable
private fun Words(items: List<VocabItem>) = WordListScreen(
    articleFilter = null,
    onArticleFilterChange = {},
    onOpenDictionary = {},
    vocab = items,
    contentPadding = NoPadding,
    onQueryChange = {},
    onFilterChange = {},
    onSortChange = {},
    onDelete = {},
    onSendToAnki = {},
    onSpeak = {},
    onReopenInArticle = {},
    onExport = {},
    onBrowseLibrary = {},
    query = "",
    filter = VocabFilter.All,
    sort = VocabSort.Newest,
)

@Composable
private fun SettingsScreenView() = SettingsScreen(
    settings = Settings(deckName = "LingoDeck", textScale = 1.1f),
    counts = SettingsCounts(articles = 2, words = 2, sentToAnki = 1),
    contentPadding = NoPadding,
    onThemeChange = {},
    onDynamicColorChange = {},
    onTextScaleChange = {},
    onDeckNameChange = {},
    onHapticsChange = {},
    onHighlightChange = {},
    onRecordingsChange = {},
    onExport = {},
    onClearWords = {},
    onClearHistory = {},
    onShowLicences = {},
)

private typealias SettingsCounts = com.lingodeck.reader.nav.SettingsCounts

/** Fixed states, so a screenshot never depends on real data, the network, or the clock. */
private object Fixtures {

    private const val URL = "https://www.spiegel.de/politik/koalition-einig-ueber-entlastungen-a-1"

    val article = Article(
        url = URL,
        title = "Koalition einig über Entlastungen für Familien",
        byline = "Redaktion",
        paragraphs = listOf(
            "Die Koalition hat sich am Donnerstag auf ein Paket zur Entlastung von Familien " +
                "geeinigt. Nach mehreren Monaten Verhandlung sollen die Maßnahmen zum kommenden " +
                "Jahr in Kraft treten.",
            "Kritikerinnen und Kritiker bemängeln unterdessen, dass der Kinderfreibetrag nur für " +
                "bestimmte Einkommensklassen angehoben wird. Der höchste Betrag liegt dabei " +
                "deutlich unter der inflation.",
        ),
        // Fixed rather than System.currentTimeMillis, so the relative-date chip is stable.
        retrievedAt = 1_757_000_000_000L,
    )

    val vocab = listOf(
        VocabItem(
            id = "1",
            createdAt = 1_757_000_000_000L,
            word = "Entlastung",
            lemma = "entlasten",
            partOfSpeech = "Substantiv, fem.",
            ipa = "ɛntˈlastʊŋ",
            audioUrl = "",
            glosses = listOf("relief, easing (of a burden)"),
            meaning = "relief",
            sentence = "Die Koalition hat sich auf ein Paket zur Entlastung von Familien geeinigt.",
            articleTitle = "Koalition einig über Entlastungen für Familien",
            sourceUrl = URL,
            source = "",
            sentToAnkiAt = 1_757_100_000_000L,
            paragraphIndex = 0,
            wordStart = 36,
            wordEnd = 45,
        ),
        VocabItem(
            id = "2",
            createdAt = 1_756_000_000_000L,
            word = "Kinderfreibetrag",
            lemma = "Kinderfreibetrag",
            partOfSpeech = "Substantiv, mask.",
            ipa = "ˈkɪndɐˌfʁaɪ̯bəˌtʁaːk",
            audioUrl = "",
            glosses = listOf("child allowance, tax exemption for children", "child tax credit"),
            meaning = "",
            sentence = "Der Kinderfreibetrag wird angehoben.",
            articleTitle = "Steuern 2026",
            sourceUrl = "https://www.zeit.de/wirtschaft/steuern-2026-a-1",
            source = "",
        ),
    )

    /** A verb, from the real kaikki.org payload for "sagen". */
    /** `gehen`'s real conjugation rows, parsed the way the app parses them. */
    val gehenForms: List<InflectedForm> =
        com.lingodeck.reader.dict.KaikkiFixtures.GEHEN_FORMS.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val o = org.json.JSONObject(line)
                InflectedForm(
                    form = o.getString("form"),
                    tags = o.getJSONArray("tags").let { t -> (0 until t.length()).map { t.getString(it) } },
                    source = o.optString("source").takeIf { it.isNotEmpty() },
                )
            }
            .toList()

    /**
     * `gehen`, carrying its real conjugation table.
     *
     * A separate fixture from [verbLookup] rather than an added field on it: the table belongs to a
     * particular verb, and hanging `gehen`'s forms off `sagen` would make a screenshot that looks
     * right and is about nothing.
     */
    /** `Haus`'s real declension rows, parsed the way the app parses them. */
    val hausForms: List<InflectedForm> =
        com.lingodeck.reader.dict.KaikkiFixtures.HAUS_FORMS.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val o = org.json.JSONObject(line)
                InflectedForm(
                    form = o.getString("form"),
                    tags = o.getJSONArray("tags").let { t -> (0 until t.length()).map { t.getString(it) } },
                    source = o.optString("source").takeIf { it.isNotEmpty() },
                )
            }
            .toList()

    /** `Haus` carrying its real case table, so the declension section appears on a card. */
    val hausLookup = LookupUi(
        word = "Haus",
        sentence = "Die Koalition hat sich am Donnerstag geeinigt.",
        paragraphIndex = 0,
        start = 3,
        end = 7,
        loading = false,
        result = DictResult(
            word = "Haus",
            language = "German",
            languageCode = "de",
            entries = listOf(
                DictEntry(
                    word = "Haus",
                    partOfSpeech = "noun",
                    head = "Haus n",
                    ipa = "[haʊ̯s]",
                    audioUrl = "",
                    formOf = "",
                    definitions = listOf(
                        Sense(
                            gloss = "house, building",
                            tags = listOf("neuter", "strong"),
                            examples = emptyList(),
                        ),
                    ),
                    forms = hausForms,
                ),
            ),
        ),
        glosses = listOf("house, building"),
        chosenGloss = "house, building",
    )

    val gehenLookup = LookupUi(
        word = "gehen",
        sentence = "Er sagte, dass es am Montag regnet.",
        paragraphIndex = 0,
        start = 3,
        end = 8,
        loading = false,
        result = DictResult(
            word = "gehen",
            language = "German",
            languageCode = "de",
            lemma = "gehen",
            entries = listOf(
                DictEntry(
                    word = "gehen",
                    partOfSpeech = "verb",
                    head = "gehen (class 7 strong)",
                    ipa = "ˈɡeːən",
                    audioUrl = "",
                    formOf = "",
                    definitions = listOf(
                        Sense(
                            gloss = "to go",
                            tags = listOf("table-tags", "class-7", "intransitive", "strong"),
                            examples = emptyList(),
                        ),
                        Sense(
                            gloss = "to walk",
                            tags = listOf("table-tags", "class-7", "intransitive", "strong"),
                            examples = emptyList(),
                        ),
                    ),
                    forms = gehenForms,
                ),
            ),
        ),
        glosses = listOf("to go", "to walk"),
        chosenGloss = "to go",
    )

    val verbLookup = LookupUi(
        word = "sagte",
        sentence = "Er sagte, dass es am Montag regnet.",
        paragraphIndex = 0,
        start = 3,
        end = 8,
        loading = false,
        result = DictResult(
            word = "sagen",
            language = "German",
            languageCode = "de",
            lemma = "sagen",
            entries = listOf(
                DictEntry(
                    word = "sagen",
                    partOfSpeech = "verb",
                    head = "sagen",
                    ipa = "ˈzaːɡn̩",
                    audioUrl = "",
                    formOf = "",
                    definitions = listOf(
                        Sense(
                            gloss = "to say (to pronounce; communicate verbally)",
                            tags = listOf("table-tags", "transitive", "weak", "form-of"),
                            examples = listOf(
                                Example(
                                    "Ich habe nicht verstanden, was sie gesagt hat.",
                                    "I didn't understand what she said.",
                                ),
                            ),
                        ),
                        Sense(
                            gloss = "to tell (to inform someone verbally)",
                            tags = listOf("ditransitive", "weak", "table-tags"),
                            examples = listOf(
                                Example(
                                    "Sie hat mir gesagt, dass sie später kommt.",
                                    "She told me that she would be late.",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        glosses = listOf(
            "to say (to pronounce; communicate verbally)",
            "to tell (to inform someone verbally)",
        ),
        chosenGloss = "to say (to pronounce; communicate verbally)",
    )

    /** A noun, from the real kaikki.org payload for "Griechenland". */
    val nounLookup = LookupUi(
        word = "Griechenland",
        sentence = "Die Euro-Zone hat Griechenland in diesem Sommer viel abverlangt.",
        paragraphIndex = 0,
        start = 19,
        end = 31,
        loading = false,
        result = DictResult(
            word = "Griechenland",
            language = "German",
            languageCode = "de",
            lemma = "Griechenland",
            entries = listOf(
                DictEntry(
                    word = "Griechenland",
                    partOfSpeech = "name",
                    head = "Griechenland",
                    ipa = "ˈɡʁiːçn̩lant",
                    audioUrl = "",
                    formOf = "",
                    definitions = listOf(
                        Sense(
                            gloss = "Greece (a country in Southeastern Europe)",
                            tags = listOf("neuter", "proper-noun", "table-tags"),
                            examples = emptyList(),
                        ),
                        Sense(
                            gloss = "Ancient Greece",
                            tags = listOf("neuter", "proper-noun", "table-tags"),
                            examples = emptyList(),
                        ),
                    ),
                ),
            ),
        ),
        glosses = listOf(
            "Greece (a country in Southeastern Europe)",
            "Ancient Greece",
        ),
        chosenGloss = "Greece (a country in Southeastern Europe)",
    )

    val library = UiState(
        screen = com.lingodeck.reader.ui.Screen.Library,
        library = listOf(
            article,
            article.copy(
                url = "https://www.zeit.de/politik/ausgabe-1",
                title = "Was die Koalition jetzt beschlossen hat",
                byline = "Ausgabe 1",
                retrievedAt = 1_756_000_000_000L,
                lastParagraph = 1,
            ),
        ),
        vocab = vocab,
    )
}
