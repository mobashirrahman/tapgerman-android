package com.lingodeck.reader.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingodeck.reader.brand.BrandMark
import com.lingodeck.reader.data.Article
import com.lingodeck.reader.ui.UiState
import com.lingodeck.reader.ui.components.EmptyState
import com.lingodeck.reader.ui.components.ErrorSurface
import com.lingodeck.reader.ui.components.LingoCard
import com.lingodeck.reader.ui.components.MetaChip
import com.lingodeck.reader.ui.components.MorphingLoadingIndicator
import com.lingodeck.reader.ui.components.SectionHeader
import com.lingodeck.reader.ui.components.StatTile
import com.lingodeck.reader.ui.theme.Gutter
import com.lingodeck.reader.ui.theme.LingoTheme
import com.lingodeck.reader.ui.theme.Space
import com.lingodeck.reader.util.ReadingStats
import com.lingodeck.reader.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import com.lingodeck.reader.ui.UiText

/**
 * The library: paste a link, see what you have read, see what you have collected.
 *
 * The pre-redesign version was one `LazyColumn` holding an intro paragraph, a text field, a button,
 * a second button, a "Recently read" label and then either the grey words "Nothing here yet." or a
 * run of cards that were a title, a byline and a raw URL. Three things were missing that a shelf
 * cannot do without: a sense of how much is here, any way to get rid of something, and any
 * indication of what an article was like before opening it. This one has a stats row, a reading
 * estimate and a progress bar per card, and a menu per card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: UiState,
    contentPadding: PaddingValues,
    onReadUrl: (String) -> Unit,
    onOpenArticle: (Article) -> Unit,
    onRemoveArticle: (Article) -> Unit,
    onOpenWords: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val listState = rememberLazyListState()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
    val accent = LingoTheme.colors.accent

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // The outer AppShell already has a Scaffold, and this one only exists to host a
        // collapsing bar. Consuming no insets here stops the status bar padding being applied
        // twice; the bars themselves still pad for it.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        BrandMark(size = 32.dp)
                        Text(
                            text = stringResource(R.string.brand_name),
                            style = LingoTheme.emphasized.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.tab_settings))
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
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            item(key = "read") {
                ReadLinkCard(
                    loading = state.loading,
                    error = state.error,
                    onRead = onReadUrl,
                )
            }

            if (state.library.isNotEmpty() || state.vocab.isNotEmpty()) {
                item(key = "stats") {
                    StatsRow(
                        articles = state.library.size,
                        words = state.vocab.size,
                        sentToAnki = state.vocab.count { it.sentToAnkiAt != null },
                        onOpenWords = onOpenWords,
                    )
                }
            }

            // Only when there is something under it. A "Recently read 0" heading above an empty
            // state is a label for a list that does not exist, and on a fresh install that was
            // the first thing on the screen after the hero card.
            if (state.library.isNotEmpty()) {
                item(key = "recent-header") {
                    SectionHeader(
                        title = stringResource(R.string.library_recently_read),
                        count = state.library.size,
                    )
                }
            }

            if (state.library.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.AutoStories,
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_body),
                    )
                }
            } else {
                items(state.library, key = { it.url }) { article ->
                    ArticleCard(
                        article = article,
                        savedCount = state.vocab.count { it.sourceUrl == article.url },
                        onClick = { onOpenArticle(article) },
                        onRemove = { onRemoveArticle(article) },
                    )
                }
            }

            if (state.vocab.isEmpty() && state.library.isNotEmpty()) {
                item(key = "no-words") {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.library_no_words_title),
                        body = stringResource(R.string.library_no_words_body),
                        action = { TextButton(onClick = onOpenWords) { Text(stringResource(R.string.library_go_to_words)) } },
                    )
                }
            }
        }
    }
}

/**
 * The hero: a link field and a read button on the brand gradient.
 *
 * The field is the whole entry point of the app, and it used to sit between two paragraphs of
 * explanatory prose with a full-width button underneath. This puts it on the one surface in the
 * app that is allowed to use the accent, so it is the first thing the eye lands on.
 */
@Composable
private fun ReadLinkCard(
    loading: Boolean,
    error: UiText?,
    onRead: (String) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val lingo = LingoTheme.colors

    LingoCard(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(Space.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    text = stringResource(R.string.library_read_headline),
                    style = LingoTheme.emphasized.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.library_read_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !loading,
                placeholder = { Text(stringResource(R.string.library_url_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                trailingIcon = {
                    if (typed.isNotEmpty()) {
                        FilledTonalIconButton(onClick = { typed = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_clear_url))
                        }
                    }
                },
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { if (typed.isNotBlank() && !loading) onRead(typed) },
                ),
            )

            // The one place in the app where the accent is a fill rather than a highlight, so
            // "read this" is unmistakable and everything else can stay quiet.
            Button(
                onClick = { onRead(typed) },
                enabled = typed.isNotBlank() && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = lingo.accent,
                    contentColor = lingo.onAccent,
                ),
            ) {
                if (loading) {
                    MorphingLoadingIndicator(size = 20.dp, color = lingo.onAccent, thickness = 2.dp)
                    Spacer(Modifier.size(Space.sm))
                    Text(stringResource(R.string.library_reading), style = LingoTheme.emphasized.labelLarge)
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(Space.sm))
                    Text(stringResource(R.string.library_read_action), style = LingoTheme.emphasized.labelLarge)
                }
            }

            if (error != null) {
                Spacer(Modifier.height(Space.xs))
                ErrorSurface(message = error.resolve(LocalContext.current))
            }
        }
    }
}

@Composable
private fun StatsRow(
    articles: Int,
    words: Int,
    sentToAnki: Int,
    onOpenWords: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        StatTile(
            value = articles.toString(),
            label = pluralStringResource(R.plurals.stat_articles, articles, articles),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = words.toString(),
            label = pluralStringResource(R.plurals.stat_words, words, words),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenWords),
        )
        StatTile(
            value = sentToAnki.toString(),
            label = stringResource(R.string.stat_sent_to_anki),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One article on the shelf.
 *
 * Everything here is a decision to show the reader something before they commit to opening the
 * thing: how long it is, how long ago it was, whether they kept any words from it, and how far
 * through it they got. The pre-redesign card had a title, a byline and a truncated URL, which is
 * the minimum possible and told you nothing.
 */
@Composable
private fun ArticleCard(
    article: Article,
    savedCount: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    LingoCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            ArticleMonogram(article = article, modifier = Modifier.size(52.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = article.title,
                    style = LingoTheme.emphasized.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (article.domain.isNotBlank()) {
                        MetaChip(text = article.domain)
                    }
                    MetaChip(text = stringResource(R.string.article_minutes, ReadingStats.readingMinutes(article)))
                    if (savedCount > 0) {
                        MetaChip(
                            text = pluralStringResource(R.plurals.article_words_saved, savedCount, savedCount),
                            containerColor = LingoTheme.colors.accentContainer,
                            contentColor = LingoTheme.colors.onAccentContainer,
                        )
                    }
                }

                Text(
                    text = ReadingStats.relativeTime(article.retrievedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Progress through the article, from the paragraph the reader last reached.
                val progress = ReadingStats.progressThrough(article, article.lastParagraph)
                if (progress > 0.01f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }

            Box {
                FilledTonalIconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.library_more_options, article.title),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_read_menu)) },
                        onClick = { menuOpen = false; onClick() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_remove_menu)) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}

/**
 * A letter tile standing in for the publication.
 *
 * No favicons: fetching one per article would mean a network request per card and a cache to go
 * with it, for a 16-pixel image. A letter in a tonal container is stable, instant, and does not
 * need the network.
 */
@Composable
private fun ArticleMonogram(article: Article, modifier: Modifier = Modifier) {
    val lingo = LingoTheme.colors
    val letter = article.title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(listOf(lingo.gradientStart, lingo.gradientEnd)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = LingoTheme.emphasized.titleLarge,
            color = Color.White,
        )
    }
}
