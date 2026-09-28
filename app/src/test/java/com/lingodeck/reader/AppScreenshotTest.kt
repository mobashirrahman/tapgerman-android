package com.lingodeck.reader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.lingodeck.reader.data.Article
import com.lingodeck.reader.data.ThemeMode
import com.lingodeck.reader.data.VocabItem
import com.lingodeck.reader.library.LibraryScreen
import com.lingodeck.reader.reader.ReaderScreen
import com.lingodeck.reader.settings.SettingsScreen
import com.lingodeck.reader.store.Settings
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

    @Test
    fun readerDark() = screen("reader-dark", ThemeMode.Dark) { Reader() }

    @Test
    fun wordsLight() = screen("words-light") { Words(Fixtures.vocab) }

    @Test
    fun wordsDark() = screen("words-dark", ThemeMode.Dark) { Words(Fixtures.vocab) }

    @Test
    fun wordsEmpty() = screen("words-empty") { Words(emptyList()) }

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
    contentPadding = NoPadding,
    onBack = {},
    onTapWord = { _, _, _, _ -> },
    onLongPressWord = {},
    onProgress = {},
    onConsumeJump = {},
    onTextScale = {},
)

@Composable
private fun Words(items: List<VocabItem>) = WordListScreen(
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
