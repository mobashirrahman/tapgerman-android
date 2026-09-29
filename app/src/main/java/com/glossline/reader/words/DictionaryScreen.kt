package com.glossline.reader.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.glossline.reader.R
import com.glossline.reader.reader.LookupCard
import com.glossline.reader.ui.UiState
import com.glossline.reader.ui.components.LingoCard
import com.glossline.reader.ui.components.SectionHeader
import com.glossline.reader.ui.theme.Gutter
import com.glossline.reader.ui.theme.Space

/**
 * A dictionary you can use without an article.
 *
 * The lookup card was reachable only by tapping a word in an open article. The client behind it
 * was complete — cached, case-ladder-aware, lemma-resolving — but its only call site returned
 * early unless an article was loaded, so looking a word up required already having German text in
 * hand. This is that entry point.
 *
 * It renders the same [LookupCard] rather than a second, simpler card, because a word has to
 * behave the same wherever it was found: same senses, same tags, same save and Anki paths. The
 * card sits below the field instead of in a popup, since a popup is right for a word tapped
 * beside the sentence it came from and there is no such word here to sit beside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    state: UiState,
    contentPadding: PaddingValues,
    onLookup: (String) -> Unit,
    onClearRecent: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSendToAnki: () -> Unit,
    onSpeak: () -> Unit,
    onLookupWord: (String) -> Unit = {},
) {
    val appBarState = rememberTopAppBarState()
    // Seeded from the loaded word, and keyed on it, so the field shows what the card below it is
    // actually about. Edits after that are not clobbered — the key only moves when a different
    // word is looked up.
    var query by remember(state.lookup?.word) { mutableStateOf(state.lookup?.word.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val lookup = state.lookup

    // The only reason to open this screen is to type a word, so the field takes focus. Deferred
    // past the first composition because there is nothing to focus until then.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Recent lookups are an alternative to typing, not a companion to a result, so they step
    // aside as soon as either exists.
    val showRecent = query.isBlank() && lookup == null && state.recentLookups.isNotEmpty()

    fun submit(word: String) {
        if (word.isBlank()) return
        query = word
        onLookup(word)
        keyboard?.hide()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        MediumTopAppBar(
            title = { Text(stringResource(R.string.dictionary_title)) },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.dictionary_back),
                    )
                }
            },
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(appBarState),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )

        Column(
            modifier = Modifier.padding(horizontal = Gutter.standard),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.dictionary_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.dictionary_clear),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                shape = MaterialTheme.shapes.large,
            )

            if (showRecent) {
                Spacer(Modifier.width(Space.xs))
                SectionHeader(title = stringResource(R.string.dictionary_recent))
                state.recentLookups.forEach { word ->
                    LingoCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { submit(word) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = word,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                TextButton(onClick = onClearRecent) {
                    Text(stringResource(R.string.dictionary_clear_recent))
                }
            }

            if (lookup != null) {
                LookupCard(
                    lookup = lookup,
                    onDismiss = onDismiss,
                    onSave = onSave,
                    onSendToAnki = onSendToAnki,
                    // The sense picker needs the view model's sense list, which the reader screen
                    // owns; a dictionary lookup has a single result, so choosing a narrower sense
                    // has nothing to choose between. Kept as a no-op rather than removed so the
                    // card's two entry points cannot drift apart.
                    onChooseGloss = { },
                    onSpeak = onSpeak,
                    onToggleAllSenses = { },
                    isShowingAllSenses = false,
                    onLookupWord = onLookupWord,
                )
            }
        }
    }
}
