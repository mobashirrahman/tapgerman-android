package com.lingodeck.reader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lingodeck.reader.anki.AnkiSaver
import com.lingodeck.reader.anki.NoteIdentity
import com.lingodeck.reader.anki.TsvExport
import com.lingodeck.reader.anki.toVocabItem
import com.lingodeck.reader.R
import com.lingodeck.reader.data.Article
import com.lingodeck.reader.data.ArticleFetcher
import com.lingodeck.reader.data.DictEntry
import com.lingodeck.reader.data.DictResult
import com.lingodeck.reader.data.LookupCardBuilder
import com.lingodeck.reader.data.Pronouncer
import com.lingodeck.reader.data.VocabItem
import com.lingodeck.reader.dict.KaikkiClient
import com.lingodeck.reader.dict.LemmaResolver
import com.lingodeck.reader.store.Settings
import com.lingodeck.reader.store.SettingsStore
import com.lingodeck.reader.store.Store
import com.lingodeck.reader.text.GermanTokenizer
import com.lingodeck.reader.util.VocabFilter
import com.lingodeck.reader.util.VocabSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The destinations. [Reader] and [Dictionary] are pushed levels, not tabs. */
enum class Screen { Library, Reader, Words, Dictionary, Settings }

/**
 * A word on the lookup card, with whatever context it was opened from.
 *
 * The three position fields are nullable rather than defaulted to a sentinel because a lookup no
 * longer has to come from an article: typing a word into the dictionary screen produces the same
 * card with nothing behind it. A `-1` here would have been the obvious cheaper choice and would
 * have meant every reader of this class had to know that `-1` meant "no article" — which is how
 * the lemma bug happened in the first place, with one source of truth quietly not being the one
 * being read. Nullability makes the distinction a compile error instead of a convention.
 */
data class LookupUi(
    val word: String,
    /** The sentence the word was tapped in. Empty for a lookup with no article behind it. */
    val sentence: String = "",
    /** Where in the article, or null when this lookup did not come from one. */
    val paragraphIndex: Int? = null,
    val start: Int? = null,
    val end: Int? = null,
    /** Tap position in window pixels; the card opens beside the word instead of over the article. */
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val loading: Boolean = true,
    val result: DictResult? = null,
    /** Every gloss the sense picker offers, in display order. */
    val glosses: List<String> = emptyList(),
    /** The sense the learner says they met. Narrowing to it is what splits homonyms apart. */
    val chosenGloss: String? = null,
    val error: UiText? = null,
    val saved: Boolean = false,
    val savedMessage: UiText? = null,
) {
    /**
     * Whether this lookup came from a word tapped in an open article.
     *
     * Everything that only makes sense with an article behind it — the session word list, jumping
     * back to the word, quoting the sentence on a saved card — asks this first.
     */
    val isFromArticle: Boolean
        get() = paragraphIndex != null && start != null && end != null

    /** The character range in the article, or null when there is no article. */
    val range: IntRange?
        get() = if (start != null && end != null) start until end else null
}

/**
 * A word looked at during this reading session, kept so the reader can offer a list of them.
 *
 * The pre-redesign app had no way back to the word list from inside an article at all: the
 * bottom navigation disappeared on the reader, so once you were reading, the only way to see what
 * you had collected was to go back, then across. This is that list.
 */
data class SessionWord(
    val word: String,
    val gloss: String?,
    val saved: Boolean,
)

data class UiState(
    val screen: Screen = Screen.Library,
    val loading: Boolean = false,
    val error: UiText? = null,
    val article: Article? = null,
    val library: List<Article> = emptyList(),
    val vocab: List<VocabItem> = emptyList(),
    val lookup: LookupUi? = null,
    val message: UiText? = null,
    val deckName: String = "LingoDeck",
    /** Words tapped during this article, in the order they were tapped. */
    val sessionWords: List<SessionWord> = emptyList(),
    /** Set when a word from the word list should reopen its article at that exact word. */
    val pendingJump: PendingJump? = null,
    /** A removal the snackbar can offer to take back. Cleared once acted on or replaced. */
    val undo: Undo? = null,
    /** Words looked up from the dictionary screen, most recent first. */
    val recentLookups: List<String> = emptyList(),
)

/** A request to open an article and highlight a word inside it. */
data class PendingJump(
    val url: String,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application.filesDir)
    private val settingsStore = SettingsStore(application)
    private val pronouncer = Pronouncer(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Settings, straight from the store. The theme and the reader subscribe to this. */
    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    // Word list view state, kept here rather than in the composable so it survives scrolling and
    // survives the screen being left and come back to.
    private val _vocabView = MutableStateFlow(VocabViewState())
    val vocabView: StateFlow<VocabViewState> = _vocabView.asStateFlow()

    /** Which words are showing their full sense list rather than the first four. */
    private val _expandedSenses = MutableStateFlow<Set<String>>(emptySet())
    val expandedSenses: StateFlow<Set<String>> = _expandedSenses.asStateFlow()

    init {
        refreshLibrary()
        // Keep the deck name the UI edits and the one AnkiSaver uses in step.
        viewModelScope.launch {
            settingsStore.settings.collect { s -> _state.update { it.copy(deckName = s.deckName) } }
        }
    }

    override fun onCleared() {
        pronouncer.close()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            val (library, vocab) = withContext(Dispatchers.IO) {
                store.listArticles() to store.listVocab()
            }
            _state.update { it.copy(library = library, vocab = vocab) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, error = null) }

    fun showMessage(text: UiText) = _state.update { it.copy(message = text) }

    // --- Navigation -----------------------------------------------------------------------

    fun openLibrary() = _state.update { it.copy(screen = Screen.Library) }

    fun openWords() = _state.update { it.copy(screen = Screen.Words) }

    fun openSettings() = _state.update { it.copy(screen = Screen.Settings) }

    /** The dictionary, as a pushed level on top of whichever tab asked for it. */
    fun openDictionary() {
        _state.update { it.copy(screen = Screen.Dictionary, lookup = null) }
        refreshRecentLookups()
    }

    fun closeDictionary() = _state.update { it.copy(screen = Screen.Words, lookup = null) }

    fun closeArticle() {
        // Persist how far the reader got before leaving, so the library card can show it.
        val article = _state.value.article
        if (article != null) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { store.saveArticle(article) }
            }
        }
        _state.update {
            it.copy(screen = Screen.Library, article = null, lookup = null, sessionWords = emptyList())
        }
    }

    /** Entry point for a URL shared from the browser, or typed on the library screen. */
    fun loadUrl(rawUrl: String?) {
        val url = rawUrl?.let { ArticleFetcher.extractUrlFromSharedText(it) } ?: rawUrl?.trim()
        if (url.isNullOrBlank()) {
            _state.update { it.copy(error = UiText.of(R.string.library_share_error)) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(loading = true, error = null, screen = Screen.Reader, lookup = null, sessionWords = emptyList())
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val article = ArticleFetcher.fetch(url)
                    store.saveArticle(article)
                    article
                }
            }.onSuccess { article ->
                refreshLibrary()
                _state.update { it.copy(loading = false, article = article) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message?.let { UiText.Raw(it) } ?: UiText.of(R.string.library_unreadable),
                        screen = Screen.Library,
                    )
                }
            }
        }
    }

    fun openArticle(article: Article) {
        _state.update {
            it.copy(screen = Screen.Reader, article = article, lookup = null, error = null, sessionWords = emptyList())
        }
    }

    fun removeArticle(article: Article) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                store.deleteArticle(article.url)
                article
            }
            refreshLibrary()
            // Undo restores the same article object rather than re-fetching it, so the shelf
            // comes back exactly as it was without another network round trip.
            showMessage(UiText.of(R.string.article_removed, article.title.take(28)))
            _state.value = _state.value.copy(undo = Undo.RestoreArticle(removed))
        }
    }

    fun undoRemoveArticle() {
        val pending = _state.value.undo as? Undo.RestoreArticle ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveArticle(pending.article) }
            _state.update { it.copy(undo = null) }
            refreshLibrary()
        }
    }

    fun clearArticleHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearArticles() }
            _state.update { it.copy(undo = null) }
            refreshLibrary()
        }
    }

    // --- Lookup --------------------------------------------------------------------------

    fun openLookup(paragraphIndex: Int, start: Int, end: Int, anchorX: Float = 0f, anchorY: Float = 0f) {
        val article = _state.value.article ?: return
        val paragraph = article.paragraphs.getOrNull(paragraphIndex) ?: return
        val word = paragraph.substring(start, end)
        val sentence = GermanTokenizer.sentenceContaining(paragraph, start).text

        startLookup(
            LookupUi(
                word = word,
                sentence = sentence,
                paragraphIndex = paragraphIndex,
                start = start,
                end = end,
                anchorX = anchorX,
                anchorY = anchorY,
            ),
        )
    }

    /**
     * Looks up a word typed into the dictionary screen, with no article behind it.
     *
     * The same card and the same save path as a tapped word, minus everything that needs a
     * sentence. Worth having on its own: a reader who meets a word in conversation, or wants to
     * check a verb's senses before reading anything, currently cannot do that at all.
     */
    fun openStandaloneLookup(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        rememberRecentLookup(trimmed)
        startLookup(LookupUi(word = trimmed))
    }

    /**
     * Publishes the card, then resolves it.
     *
     * Shared by both entry points so the lemma resolution, sense picking and error handling cannot
     * drift between a tapped word and a typed one — the two cards are meant to be the same card.
     */
    private fun startLookup(lookup: LookupUi) {
        _state.update { it.copy(lookup = lookup) }

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { KaikkiClient.lookup(lookup.word, "de") }
            when (outcome) {
                is KaikkiClient.LookupOutcome.Found -> {
                    val lemma = LemmaResolver.lemmaFor(outcome.result)
                    val lemmaEntries = if (lemma != null) {
                        withContext(Dispatchers.IO) { KaikkiClient.lookupLemma(lemma, "de") }
                    } else {
                        emptyList()
                    }
                    // The lemma's entries have to be folded back into `result` itself. Carrying
                    // them alongside it — as this once did — left every renderer reading the
                    // surface form, so tapping a conjugated verb showed "inflection of sagen:
                    // singular" and never the meaning.
                    val result = LemmaResolver.resolve(outcome.result, lemmaEntries)
                    val glosses = LookupCardBuilder.glosses(result)
                    _state.update { current ->
                        current.copy(lookup = current.lookup?.copy(
                            loading = false,
                            result = result,
                            glosses = glosses,
                            chosenGloss = glosses.firstOrNull(),
                        ))
                    }
                }
                is KaikkiClient.LookupOutcome.NotFound -> _state.update { current ->
                    current.copy(lookup = current.lookup?.copy(
                        loading = false,
                        result = outcome.result,
                        error = UiText.of(R.string.lookup_no_entry, outcome.result.word),
                    ))
                }
                is KaikkiClient.LookupOutcome.Failed -> _state.update { current ->
                    current.copy(
                        lookup = current.lookup?.copy(loading = false, error = UiText.Raw(outcome.message)),
                    )
                }
            }
        }
    }

    fun dismissLookup() = _state.update { it.copy(lookup = null) }

    /**
     * Records a dictionary lookup, most recent first.
     *
     * Loaded on open rather than kept for the life of the process, because the point is to survive
     * being killed — a reader who mistyped "Entlastungen" three articles ago should not have to
     * remember it was spelled with an s.
     */
    private fun rememberRecentLookup(word: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.rememberLookup(word) }
            refreshRecentLookups()
        }
    }

    fun refreshRecentLookups() {
        viewModelScope.launch {
            val recent = withContext(Dispatchers.IO) { store.listRecentLookups() }
            _state.update { it.copy(recentLookups = recent) }
        }
    }

    fun clearRecentLookups() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearRecentLookups() }
            refreshRecentLookups()
        }
    }


    /**
     * Speaks the word as it appears in the article, so the learner hears the form they actually
     * met in context. (`extension/content.js` prefers the lemma; for reading practice the
     * inflected surface is the one worth hearing.)
     */
    fun speakLookupWord() {
        val lookup = _state.value.lookup ?: return
        pronouncer.speak(lookup.word)
    }

    /** Speaks a stored word, from the word list rather than from a lookup. */
    fun speakWord(word: String) = pronouncer.speak(word)

    /** The learner's sense choice. Saves narrow the card to exactly this gloss. */
    fun chooseGloss(gloss: String) = _state.update { it.copy(lookup = it.lookup?.copy(chosenGloss = gloss)) }

    fun toggleAllSenses(word: String) = _expandedSenses.update { current ->
        if (word in current) current - word else current + word
    }

    fun isShowingAllSenses(word: String): Boolean = word in _expandedSenses.value

    // --- Saving --------------------------------------------------------------------------

    /**
     * The card behind the currently open lookup. Built once so the local vocabulary id and the
     * Anki note's StableId can never diverge.
     */
    /**
     * The card a lookup stands for, and the article it came from if there was one.
     *
     * The article is optional because a word looked up from the dictionary screen has none, and
     * refusing to save it would have made the feature useless. Such a card cites the dictionary
     * rather than a piece of news, and carries no sentence — which the Anki note and the stored
     * item both already treat as optional.
     */
    private fun cardForLookup(): Pair<NoteIdentity.Card, Article?>? {
        val lookup = _state.value.lookup ?: return null
        val result = lookup.result ?: return null
        val article = _state.value.article.takeIf { lookup.isFromArticle }
        val card = LookupCardBuilder.build(
            result = result,
            chosenGloss = lookup.chosenGloss,
            sentence = lookup.sentence,
        )
        val source = if (article != null) {
            "${article.title} — ${article.url}"
        } else {
            result.sourceUrl ?: result.sourceName
        }
        return card.copy(source = source) to article
    }

    /**
     * The stored form of a card.
     *
     * With no article — a dictionary lookup — the item cites the dictionary and leaves the
     * position fields null, which is the same shape older items have and the one the word list
     * already knows how to present: it offers "open the article" rather than "open the word".
     */
    private fun NoteIdentity.Card.toItem(article: Article?, sentToAnkiAt: Long? = null): VocabItem {
        val lookup = _state.value.lookup
        val item = toVocabItem(
            articleTitle = article?.title.orEmpty(),
            sourceUrl = article?.url.orEmpty(),
            source = source.orEmpty(),
        )
        return if (article != null) {
            item.copy(
                // Where the word sits in the article, so the word list can reopen it there.
                paragraphIndex = lookup?.paragraphIndex,
                wordStart = lookup?.start,
                wordEnd = lookup?.end,
                sentToAnkiAt = sentToAnkiAt,
            )
        } else {
            item.copy(sentToAnkiAt = sentToAnkiAt)
        }
    }

    /** Builds the card for the current lookup and saves it locally. */
    fun saveLookupToVocab() {
        val (card, article) = cardForLookup() ?: return
        val item = card.toItem(article)

        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveVocab(item) }
            refreshLibrary()
            markSessionWord(saved = true)
            _state.update {
                it.copy(lookup = it.lookup?.copy(saved = true, savedMessage = UiText.of(R.string.lookup_saved_message)))
            }
        }
    }

    /** Saves to AnkiDroid, growing the existing note when this word and sense already exist. */
    fun sendLookupToAnki() {
        val (card, article) = cardForLookup() ?: return
        val item = card.toItem(article, sentToAnkiAt = System.currentTimeMillis())

        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveVocab(item) }
            refreshLibrary()
            val outcome = withContext(Dispatchers.IO) {
                AnkiSaver.save(getApplication<Application>(), item, _state.value.deckName)
            }
            val message = when (outcome) {
                is AnkiSaver.SaveOutcome.Created -> UiText.of(R.string.anki_created)
                is AnkiSaver.SaveOutcome.Appended -> UiText.of(R.string.anki_appended)
                is AnkiSaver.SaveOutcome.AlreadyPresent -> UiText.of(R.string.anki_already_present)
                is AnkiSaver.SaveOutcome.Failed -> UiText.Raw(outcome.message)
            }
            markSessionWord(saved = true)
            _state.update {
                it.copy(lookup = it.lookup?.copy(saved = true, savedMessage = message))
            }
        }
    }

    /** Sends a word straight from the word list, without reopening its article. */
    fun sendStoredToAnki(item: VocabItem) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                AnkiSaver.save(getApplication<Application>(), item, settings.value.deckName)
            }
            val message = when (outcome) {
                is AnkiSaver.SaveOutcome.Created -> UiText.of(R.string.anki_created)
                is AnkiSaver.SaveOutcome.Appended -> UiText.of(R.string.anki_appended_short)
                is AnkiSaver.SaveOutcome.AlreadyPresent -> UiText.of(R.string.anki_already_present)
                is AnkiSaver.SaveOutcome.Failed -> UiText.Raw(outcome.message)
            }
            // Only stamp the item as sent when Anki actually took it, so a failure does not leave
            // the word list claiming a card exists that does not.
            if (outcome !is AnkiSaver.SaveOutcome.Failed) {
                withContext(Dispatchers.IO) {
                    store.saveVocab(item.copy(sentToAnkiAt = System.currentTimeMillis()))
                }
                refreshLibrary()
            }
            showMessage(message)
        }
    }

    private fun markSessionWord(saved: Boolean) {
        val lookup = _state.value.lookup ?: return
        _state.update { current ->
            val word = lookup.word
            val existing = current.sessionWords.indexOfFirst { it.word == word }
            val updated = if (existing >= 0) {
                current.sessionWords.toMutableList().apply { this[existing] = this[existing].copy(saved = true) }
            } else {
                current.sessionWords + SessionWord(word, lookup.chosenGloss, saved)
            }
            current.copy(sessionWords = updated)
        }
    }

    /** Records a tapped word even when nothing was saved, so the in-article list is a real log. */
    fun recordSessionWord(word: String, gloss: String?) {
        // No article means no session. The card calls this on open, and without the guard a
        // dictionary lookup would quietly appear in the next article's word list.
        if (_state.value.screen != Screen.Reader) return
        _state.update { current ->
            if (current.sessionWords.any { it.word == word }) {
                current
            } else {
                current.copy(sessionWords = current.sessionWords + SessionWord(word, gloss, false))
            }
        }
    }

    // --- Word list -----------------------------------------------------------------------

    fun setVocabQuery(query: String) = _vocabView.update { it.copy(query = query) }

    fun setVocabFilter(filter: VocabFilter) = _vocabView.update { it.copy(filter = filter) }

    fun setVocabArticleFilter(article: String?) = _vocabView.update { it.copy(article = article) }

    fun setVocabSort(sort: VocabSort) = _vocabView.update { it.copy(sort = sort) }

    fun deleteVocab(id: String) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                store.listVocab().firstOrNull { it.id == id }?.also { store.deleteVocab(id) }
            }
            refreshLibrary()
            if (removed != null) {
                _state.update { it.copy(undo = Undo.RestoreVocab(removed)) }
                showMessage(UiText.of(R.string.words_removed, removed.word))
            }
        }
    }

    fun undoRemoveVocab() {
        val pending = _state.value.undo as? Undo.RestoreVocab ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.restoreVocab(pending.item) }
            _state.update { it.copy(undo = null) }
            refreshLibrary()
        }
    }

    fun clearVocab() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearVocab() }
            _state.update { it.copy(undo = null) }
            refreshLibrary()
        }
    }

    /** Reopens the article a stored word came from, at that word when the offsets are known. */
    fun reopenInArticle(item: VocabItem) {
        val article = _state.value.library.firstOrNull { it.url == item.sourceUrl }
        if (article != null) {
            openArticle(article)
            if (item.canReopenInArticle) {
                _state.update {
                    it.copy(
                        pendingJump = PendingJump(
                            url = item.sourceUrl,
                            paragraphIndex = item.paragraphIndex!!,
                            start = item.wordStart!!,
                            end = item.wordEnd!!,
                        ),
                    )
                }
            }
            return
        }
        // The article is no longer on the shelf (the history was cleared, or the 50-article cap
        // rolled it off). Re-fetching it is the honest thing to do rather than silently doing
        // nothing on a tap.
        showMessage(UiText.of(R.string.words_rereading, item.articleTitle))
        loadUrl(item.sourceUrl)
    }

    fun consumeJump() = _state.update { it.copy(pendingJump = null) }

    /** Remembers where the reader got to, so the library card can show progress. */
    fun recordProgress(paragraphIndex: Int) {
        val article = _state.value.article ?: return
        if (article.lastParagraph == paragraphIndex) return
        _state.update { it.copy(article = article.copy(lastParagraph = paragraphIndex)) }
    }

    // --- Settings ------------------------------------------------------------------------

    fun setThemeMode(mode: com.lingodeck.reader.data.ThemeMode) =
        viewModelScope.launch { settingsStore.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }

    fun setTextScale(scale: Float) =
        viewModelScope.launch { settingsStore.setTextScale(scale) }

    fun setHaptics(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setHaptics(enabled) }

    fun setHighlightTappableWords(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setHighlightTappableWords(enabled) }

    /**
     * The Anki deck name, which the pre-redesign app hardcoded to "LingoDeck" while exposing a
     * setter that nothing called. Settings edits it and AnkiSaver reads it.
     */
    fun setDeckName(name: String) = viewModelScope.launch { settingsStore.setDeckName(name) }

    fun onAnkiPermissionResult(granted: Boolean) {
        if (granted) sendLookupToAnki()
    }

    // --- Export --------------------------------------------------------------------------

    fun exportTsv(): String = TsvExport.build(_state.value.vocab)

    fun exportTsvFileName(): String {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "lingodeck-anki-$today.txt"
    }
}

/** The word list's search, filter and sort, as one snapshot. */
data class VocabViewState(
    val query: String = "",
    val filter: VocabFilter = VocabFilter.All,
    val sort: VocabSort = VocabSort.Newest,
    /** The article [filter] is narrowed to. Only meaningful for [VocabFilter.ByArticle]. */
    val article: String? = null,
)

/** A removal that can be taken back from the snackbar. */
sealed interface Undo {
    data class RestoreVocab(val item: VocabItem) : Undo
    data class RestoreArticle(val article: Article) : Undo
}
