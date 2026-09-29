package com.lingodeck.reader.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lingodeck.reader.data.LibreTranslateTranslator
import com.lingodeck.reader.data.ThemeMode
import com.lingodeck.reader.data.TranslationProvider
import com.lingodeck.reader.data.Translator
import com.lingodeck.reader.data.translatorFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User settings, as a `Flow`.
 *
 * Before the redesign the app had nowhere to put a preference: `MainViewModel.setDeckName`
 * existed, was called by nothing, and reset to the hardcoded "LingoDeck" on every launch. Text
 * size, theme and haptics had no home at all.
 *
 * DataStore rather than SharedPreferences, because the rest of the app is already
 * `StateFlow`-shaped: preferences arrive as a flow the theme and the reader subscribe to, rather
 * than as a synchronous read that has to be pushed into a `StateFlow` by hand afterwards. It is
 * also the only option that survives the process being killed while Settings is open.
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = dataStore.data
        // A corrupt preferences file must not take the app down on launch.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            Settings(
                themeMode = prefs[KEY_THEME]?.let { stored ->
                    ThemeMode.entries.firstOrNull { it.name == stored }
                } ?: ThemeMode.System,
                dynamicColor = prefs[KEY_DYNAMIC] ?: false,
                textScale = (prefs[KEY_TEXT_SCALE] ?: DEFAULT_TEXT_SCALE).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
                deckName = prefs[KEY_DECK]?.takeIf { it.isNotBlank() } ?: DEFAULT_DECK,
                haptics = prefs[KEY_HAPTICS] ?: true,
                highlightTappableWords = prefs[KEY_HIGHLIGHT] ?: true,
                translationProvider = prefs[KEY_TRANSLATION_PROVIDER]?.let { stored ->
                    TranslationProvider.entries.firstOrNull { it.name == stored }
                } ?: TranslationProvider.None,
                translationApiKey = prefs[KEY_TRANSLATION_KEY].orEmpty(),
                translationEndpoint = prefs[KEY_TRANSLATION_ENDPOINT].orEmpty(),
                autoTranslate = prefs[KEY_AUTO_TRANSLATE] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KEY_DYNAMIC] = enabled }

    suspend fun setTextScale(scale: Float) = edit {
        it[KEY_TEXT_SCALE] = scale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
    }

    suspend fun setDeckName(name: String) = edit { it[KEY_DECK] = name.trim() }

    suspend fun setHaptics(enabled: Boolean) = edit { it[KEY_HAPTICS] = enabled }

    suspend fun setHighlightTappableWords(enabled: Boolean) = edit { it[KEY_HIGHLIGHT] = enabled }

    suspend fun setTranslationProvider(provider: TranslationProvider) =
        edit { it[KEY_TRANSLATION_PROVIDER] = provider.name }

    suspend fun setTranslationApiKey(key: String) = edit { it[KEY_TRANSLATION_KEY] = key.trim() }

    suspend fun setTranslationEndpoint(endpoint: String) =
        edit { it[KEY_TRANSLATION_ENDPOINT] = endpoint.trim() }

    suspend fun setAutoTranslate(enabled: Boolean) = edit { it[KEY_AUTO_TRANSLATE] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit(block)
        } catch (cause: IOException) {
            // A failed write is not worth crashing a reading session over.
        }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_TEXT_SCALE = floatPreferencesKey("text_scale")
        val KEY_DECK = stringPreferencesKey("anki_deck")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_HIGHLIGHT = booleanPreferencesKey("highlight_words")
        val KEY_TRANSLATION_PROVIDER = stringPreferencesKey("translation_provider")
        val KEY_TRANSLATION_KEY = stringPreferencesKey("translation_api_key")
        val KEY_TRANSLATION_ENDPOINT = stringPreferencesKey("translation_endpoint")
        val KEY_AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")

        fun emptyPreferences(): Preferences = androidx.datastore.preferences.core.emptyPreferences()

        const val DEFAULT_DECK = "LingoDeck"

        /** 1f is the designed reader size. 0.8 reads as small print, 1.3 as an accessibility need. */
        const val DEFAULT_TEXT_SCALE = 1.0f
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 1.3f
    }
}

/** Everything the user can change, in one immutable snapshot. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val textScale: Float = 1.0f,
    val deckName: String = "LingoDeck",
    val haptics: Boolean = true,
    val highlightTappableWords: Boolean = true,
    val translationProvider: TranslationProvider = TranslationProvider.None,
    val translationApiKey: String = "",
    val translationEndpoint: String = "",
    val autoTranslate: Boolean = false,
) {
    /**
     * The translator these settings describe, or null when the provider is unusable.
     *
     * Null rather than a translator that fails on every call, so the lookup card can simply not
     * offer the action instead of offering it and then explaining why it cannot work.
     */
    fun translator(): Translator? = translatorFor(
        provider = translationProvider,
        apiKey = translationApiKey,
        endpoint = LibreTranslateTranslator.normalize(translationEndpoint),
    )

    /** Whether a sentence translation is possible at all with the current settings. */
    val canTranslate: Boolean get() = translator() != null

    companion object {
        const val DEFAULT_DECK = "LingoDeck"
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 1.3f
        const val TEXT_SCALE_STEP = 0.1f
    }
}
