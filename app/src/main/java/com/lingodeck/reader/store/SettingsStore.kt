package com.lingodeck.reader.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lingodeck.reader.data.ThemeMode
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
                dictionaryRecordings = prefs[KEY_RECORDINGS] ?: true,
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

    suspend fun setDictionaryRecordings(enabled: Boolean) = edit { it[KEY_RECORDINGS] = enabled }

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
        val KEY_RECORDINGS = booleanPreferencesKey("dictionary_recordings")

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
    /**
     * Prefer the dictionary's own recording of a word over the device's synthesised voice.
     *
     * On by default, and the recording is the better voice — it is a German speaker saying the
     * word the way it is said. It costs a small download the first time a word is spoken, which is
     * what the switch is for: someone on a metered connection who would rather have the offline
     * voice every time.
     */
    val dictionaryRecordings: Boolean = true,
) {
    companion object {
        const val DEFAULT_DECK = "LingoDeck"
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 1.3f
        const val TEXT_SCALE_STEP = 0.1f
    }
}
