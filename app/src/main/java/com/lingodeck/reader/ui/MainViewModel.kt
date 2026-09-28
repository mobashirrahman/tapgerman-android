package com.lingodeck.reader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lingodeck.reader.anki.AnkiSaver
import com.lingodeck.reader.anki.NoteIdentity
import com.lingodeck.reader.anki.TsvExport
import com.lingodeck.reader.anki.toVocabItem
import com.lingodeck.reader.data.Article
import com.lingodeck.reader.data.ArticleFetcher
import com.lingodeck.reader.data.DictResult
import com.lingodeck.reader.data.LookupCardBuilder
import com.lingodeck.reader.data.Pronouncer
import com.lingodeck.reader.data.VocabItem
import com.lingodeck.reader.dict.KaikkiClient
import com.lingodeck.reader.store.Store
import com.lingodeck.reader.text.GermanTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Library, Reader, Words }

data class LookupUi(
    val word: String,
    val sentence: String,
    val paragraphIndex: Int,
    val start: Int,
    val end: Int,
    /** Tap position in window pixels; the card opens beside the word instead of over the article. */
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val loading: Boolean = true,
    val result: DictResult? = null,
    /** Every gloss the sense picker offers, in display order. */
    val glosses: List<String> = emptyList(),
    /** The sense the learner says they met. Narrowing to it is what splits homonyms apart. */
    val chosenGloss: String? = null,
    val error: String? = null,
    val saved: Boolean = false,
    val savedMessage: String? = null,
)

data class UiState(
    val screen: Screen = Screen.Library,
    val loading: Boolean = false,
    val error: String? = null,
    val article: Article? = null,
    val library: List<Article> = emptyList(),
    val vocab: List<VocabItem> = emptyList(),
    val lookup: LookupUi? = null,
    val message: String? = null,
    val deckName: String = "LingoDeck",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application.filesDir)
    private val pronouncer = Pronouncer(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshLibrary()
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

    fun setDeckName(name: String) = _state.update { it.copy(deckName = name.trim().ifEmpty { "LingoDeck" }) }

    fun openLibrary() = _state.update { it.copy(screen = Screen.Library) }

    fun openWords() = _state.update { it.copy(screen = Screen.Words) }

    fun closeArticle() = _state.update { it.copy(screen = Screen.Library, article = null, lookup = null) }

    /** Entry point for a URL shared from the browser, or typed on the library screen. */
    fun loadUrl(rawUrl: String?) {
        val url = rawUrl?.let { ArticleFetcher.extractUrlFromSharedText(it) } ?: rawUrl?.trim()
        if (url.isNullOrBlank()) {
            _state.update { it.copy(error = "Share a page link to read it here.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, screen = Screen.Reader, lookup = null) }
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
                        error = error.message ?: "Could not read that page.",
                        screen = Screen.Library,
                    )
                }
            }
        }
    }

    fun openArticle(article: Article) {
        _state.update { it.copy(screen = Screen.Reader, article = article, lookup = null, error = null) }
    }

    fun openLookup(paragraphIndex: Int, start: Int, end: Int, anchorX: Float = 0f, anchorY: Float = 0f) {
        val article = _state.value.article ?: return
        val paragraph = article.paragraphs.getOrNull(paragraphIndex) ?: return
        val word = paragraph.substring(start, end)
        val sentence = GermanTokenizer.sentenceContaining(paragraph, start).text

        _state.update {
            it.copy(
                lookup = LookupUi(
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

        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { KaikkiClient.lookup(word, "de") }
            when (outcome) {
                is KaikkiClient.LookupOutcome.Found -> {
                    val lemma = outcome.result.entries
                        .mapNotNull { it.formOf.takeIf { form -> form.isNotEmpty() } }
                        .firstOrNull()
                    val lemmaEntries = if (lemma != null) {
                        withContext(Dispatchers.IO) { KaikkiClient.lookupLemma(lemma, "de") }
                    } else {
                        emptyList()
                    }
                    // The lemma's entries MUST land on the result, not on a side channel:
                    // `LookupCardBuilder.displayEntries` reads `result.lemmaEntries` and only then
                    // falls back to the surface entries. Keeping them anywhere else silently
                    // discards every lemma gloss and leaves the useless "inflection of X" senses.
                    val result = if (lemma != null) {
                        outcome.result.copy(lemma = lemma, lemmaEntries = lemmaEntries)
                    } else {
                        outcome.result
                    }
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
                        error = "No dictionary entry for \"${outcome.result.word}\".",
                    ))
                }
                is KaikkiClient.LookupOutcome.Failed -> _state.update { current ->
                    current.copy(lookup = current.lookup?.copy(loading = false, error = outcome.message))
                }
            }
        }
    }

    fun dismissLookup() = _state.update { it.copy(lookup = null) }

    /**
     * Speaks the word as it appears in the article, so the learner hears the form they actually
     * met in context. (`extension/content.js` prefers the lemma; for reading practice the
     * inflected surface is the one worth hearing.)
     */
    fun speakLookupWord() {
        val lookup = _state.value.lookup ?: return
        pronouncer.speak(lookup.word)
    }

    /** The learner's sense choice. Saves narrow the card to exactly this gloss. */
    fun chooseGloss(gloss: String) {
        _state.update { it.copy(lookup = it.lookup?.copy(chosenGloss = gloss)) }
    }

    /**
     * The card behind the currently open lookup. Built once so the local vocabulary id and the Anki
     * note's StableId can never diverge.
     */
    private fun cardForLookup(): Pair<NoteIdentity.Card, Article>? {
        val article = _state.value.article ?: return null
        val lookup = _state.value.lookup ?: return null
        val result = lookup.result ?: return null
        val card = LookupCardBuilder.build(
            result = result,
            chosenGloss = lookup.chosenGloss,
            sentence = lookup.sentence,
        )
        return card.copy(source = "${article.title} — ${article.url}") to article
    }

    private fun NoteIdentity.Card.toItem(article: Article): VocabItem =
        toVocabItem(
            articleTitle = article.title,
            sourceUrl = article.url,
            source = source ?: "${article.title} — ${article.url}",
        )

    /** Builds the card for the current lookup and saves it locally. */
    fun saveLookupToVocab() {
        val (card, article) = cardForLookup() ?: return
        val item = card.toItem(article)

        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveVocab(item) }
            refreshLibrary()
            _state.update {
                it.copy(lookup = it.lookup?.copy(saved = true, savedMessage = "Saved to your word list."))
            }
        }
    }

    /** Saves to AnkiDroid, growing the existing note when this word and sense already exist. */
    fun sendLookupToAnki() {
        val (card, article) = cardForLookup() ?: return
        val item = card.toItem(article)

        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.saveVocab(item) }
            refreshLibrary()
            val outcome = withContext(Dispatchers.IO) {
                AnkiSaver.save(getApplication<Application>(), item, _state.value.deckName)
            }
            val message = when (outcome) {
                is AnkiSaver.SaveOutcome.Created -> "Added to Anki."
                is AnkiSaver.SaveOutcome.Appended -> "Appended the sentence to the existing Anki card."
                is AnkiSaver.SaveOutcome.AlreadyPresent -> "That sentence is already on the Anki card."
                is AnkiSaver.SaveOutcome.Failed -> outcome.message
            }
            _state.update {
                it.copy(lookup = it.lookup?.copy(saved = true, savedMessage = message))
            }
        }
    }

    fun deleteVocab(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.deleteVocab(id) }
            refreshLibrary()
        }
    }

    fun exportTsv(): String = TsvExport.build(_state.value.vocab)

    fun exportTsvFileName(): String {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "lingodeck-anki-$today.txt"
    }
}
