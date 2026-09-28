package com.lingodeck.reader.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingodeck.reader.data.VocabItem
import com.lingodeck.reader.ui.components.EmptyState
import com.lingodeck.reader.ui.components.LingoCard
import com.lingodeck.reader.ui.components.MetaChip
import com.lingodeck.reader.ui.components.SectionHeader
import com.lingodeck.reader.ui.theme.Gutter
import com.lingodeck.reader.ui.theme.LingoTheme
import com.lingodeck.reader.ui.theme.Space
import com.lingodeck.reader.util.VocabFilter
import com.lingodeck.reader.util.VocabFilterEngine
import com.lingodeck.reader.util.VocabSort
import com.lingodeck.reader.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * The word list: everything kept, searchable, and one tap from the article it came from.
 *
 * The pre-redesign version was a `Column` holding a "Saved words" heading that duplicated the app
 * bar's own title, an "Export TSV" button that sat next to it, and a list of cards whose glosses
 * were literal `"• "` strings prefixed onto body text. There was no search, no filter, no way to
 * hear a word, no way to get back to the article, and no way to tell which words had reached
 * AnkiDroid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    vocab: List<VocabItem>,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onFilterChange: (VocabFilter) -> Unit,
    onSortChange: (VocabSort) -> Unit,
    onDelete: (String) -> Unit,
    onSendToAnki: (VocabItem) -> Unit,
    onSpeak: (String) -> Unit,
    onReopenInArticle: (VocabItem) -> Unit,
    onExport: () -> Unit,
    onBrowseLibrary: () -> Unit,
    query: String = "",
    filter: VocabFilter = VocabFilter.All,
    sort: VocabSort = VocabSort.Newest,
) {
    val listState = rememberLazyListState()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)
    val visible = VocabFilterEngine.apply(vocab, filter, sort, query)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.words_title)) },
                actions = {
                    TextButton(
                        onClick = {
                            onSortChange(
                                if (sort == VocabSort.Newest) VocabSort.Alphabetical else VocabSort.Newest,
                            )
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            stringResource(
                                if (sort == VocabSort.Newest) R.string.words_sort_newest
                                else R.string.words_sort_alphabetical,
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = Gutter.standard,
                end = Gutter.standard,
                top = inner.calculateTopPadding(),
                bottom = Space.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (vocab.isNotEmpty()) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.words_search_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                androidx.compose.material3.IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.words_clear_search),
                                    )
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.large,
                    )
                }

                item(key = "filters") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        VocabFilter.entries.forEach { option ->
                            FilterChip(
                                selected = filter == option,
                                onClick = { onFilterChange(option) },
                                label = { Text(stringResource(filterLabelRes(option))) },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LingoTheme.colors.accentContainer,
                                    selectedLabelColor = LingoTheme.colors.onAccentContainer,
                                ),
                            )
                        }
                    }
                }
            }

            if (vocab.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Bookmarks,
                        title = stringResource(R.string.words_empty_title),
                        body = stringResource(R.string.words_empty_body),
                        action = {
                            androidx.compose.material3.FilledTonalButton(onClick = onBrowseLibrary) {
                                Text(stringResource(R.string.words_empty_action))
                            }
                        },
                    )
                }
            } else if (visible.isEmpty()) {
                item(key = "no-match") {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = stringResource(R.string.words_no_match_title),
                        body = if (query.isNotBlank()) {
                            stringResource(R.string.words_no_match_query, query)
                        } else {
                            stringResource(R.string.words_no_match_filter)
                        },
                    )
                }
            } else {
                item(key = "count") {
                    SectionHeader(title = stringResource(R.string.words_saved_header), count = visible.size)
                }

                items(visible, key = { it.id }) { item ->
                    WordCard(
                        item = item,
                        onDelete = { onDelete(item.id) },
                        onSendToAnki = { onSendToAnki(item) },
                        onSpeak = { onSpeak(item.lemma.ifBlank { item.word }) },
                        onReopen = { onReopenInArticle(item) },
                    )
                }

                item(key = "footer") {
                    Spacer(Modifier.height(Space.lg))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        // Attribution stays on this screen, as it was before the redesign, and is
                        // now joined by the export action that used to sit awkwardly beside the
                        // duplicate page heading.
                        Text(
                            text = stringResource(R.string.attribution),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onExport) {
                            Text(stringResource(R.string.words_export_all))
                        }
                    }
                }
            }
        }
    }
}

private fun filterLabelRes(filter: VocabFilter): Int = when (filter) {
    VocabFilter.All -> R.string.words_filter_all
    VocabFilter.NotSent -> R.string.words_filter_not_sent
    VocabFilter.Sent -> R.string.words_filter_sent
    VocabFilter.ByArticle -> R.string.words_filter_by_article
}

/**
 * One saved word.
 *
 * The glosses are a real list rather than `"• $gloss"` strings prefixed onto body text, the
 * sentence is set in the reading serif because it is quoted prose, and the word can be heard and
 * can take you back to where you met it.
 */
@Composable
private fun WordCard(
    item: VocabItem,
    onDelete: () -> Unit,
    onSendToAnki: () -> Unit,
    onSpeak: () -> Unit,
    onReopen: () -> Unit,
) {
    LingoCard(modifier = Modifier.fillMaxWidth(), onClick = onReopen) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = item.word,
                    style = LingoTheme.reading.word,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.words_hear, item.word),
                        modifier = Modifier.size(20.dp),
                    )
                }
                androidx.compose.material3.IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.words_remove, item.word),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (item.lemma.isNotBlank() && !item.lemma.equals(item.word, ignoreCase = true)) {
                Text(
                    text = stringResource(R.string.words_of_lemma, item.lemma),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (item.partOfSpeech.isNotBlank()) MetaChip(text = item.partOfSpeech)
                if (item.ipa.isNotBlank()) MetaChip(text = "/${item.ipa}/")
            }

            val glosses = item.glosses.ifEmpty { listOf(item.meaning) }.filter { it.isNotBlank() }
            glosses.take(MAX_VISIBLE_GLOSSES).forEach { gloss ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = gloss,
                        style = LingoTheme.reading.gloss,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (glosses.size > MAX_VISIBLE_GLOSSES) {
                Text(
                    text = pluralStringResource(
                        R.plurals.words_more_glosses,
                        glosses.size - MAX_VISIBLE_GLOSSES,
                        glosses.size - MAX_VISIBLE_GLOSSES,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.sentence.isNotBlank()) {
                Text(
                    text = item.sentence,
                    style = LingoTheme.reading.quote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }

            Row(
                modifier = Modifier.padding(top = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = item.articleTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.sentToAnkiAt != null) {
                    MetaChip(
                        text = stringResource(R.string.words_on_anki),
                        containerColor = LingoTheme.colors.accentContainer,
                        contentColor = LingoTheme.colors.onAccentContainer,
                        leadingIcon = Icons.Rounded.School,
                    )
                } else {
                    TextButton(
                        onClick = onSendToAnki,
                        contentPadding = PaddingValues(horizontal = Space.sm),
                    ) {
                        Icon(Icons.Rounded.School, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            stringResource(R.string.words_send_to_anki),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** Glosses per card. A word with more senses shows a count rather than growing the card. */
private const val MAX_VISIBLE_GLOSSES = 3
