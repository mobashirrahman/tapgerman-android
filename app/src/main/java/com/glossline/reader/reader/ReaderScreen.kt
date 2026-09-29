package com.glossline.reader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.glossline.reader.data.Article
import com.glossline.reader.store.Settings
import com.glossline.reader.text.GermanTokenizer
import com.glossline.reader.ui.LookupUi
import com.glossline.reader.ui.PendingJump
import com.glossline.reader.ui.SessionWord
import com.glossline.reader.ui.components.EmptyState
import com.glossline.reader.ui.components.MetaChip
import com.glossline.reader.ui.components.SectionHeader
import com.glossline.reader.ui.theme.Gutter
import com.glossline.reader.ui.theme.LingoTheme
import com.glossline.reader.ui.theme.Space
import com.glossline.reader.util.ReadingStats
import com.glossline.reader.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

/**
 * The reader: German prose where every word is a tap target.
 *
 * The pre-redesign reader was competent and, for its own central interaction, invisible. It laid
 * the article out in Roboto at a hardcoded 18sp and nothing whatsoever indicated that any word was
 * tappable, so the app's reason for existing had to be guessed at. Everything below the layout is
 * about fixing that without making the prose harder to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    article: Article,
    settings: Settings,
    sessionWords: List<SessionWord>,
    savedWords: Set<String>,
    pendingJump: PendingJump?,
    openLookup: LookupUi?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onTapWord: (Int, Int, Int, Offset) -> Unit,
    onLongPressWord: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onConsumeJump: () -> Unit,
    onTextScale: (Float) -> Unit,
) {
    val listState = rememberLazyListState()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)
    val reading = LingoTheme.reading.at(settings.textScale)
    val lingo = LingoTheme.colors
    var showWordsSheet by remember { mutableStateOf(false) }

    // Scroll progress, derived rather than stored so reading it costs nothing.
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) 0f
            else (listState.firstVisibleItemIndex.toFloat() / info.totalItemsCount).coerceIn(0f, 1f)
        }
    }

    // Persist how far the reader got, sampled on paragraph boundaries. That is plenty for a
    // resume marker, and it keeps the store write off the per-frame path.
    val visibleParagraph by remember {
        derivedStateOf { listState.firstVisibleItemIndex - 1 }
    }
    LaunchedEffect(visibleParagraph) {
        if (visibleParagraph >= 0) onProgress(visibleParagraph)
    }

    // Reopening a word from the word list: scroll to its paragraph, highlight the word briefly,
    // and leave the lookup to the reader rather than firing a network request they did not ask
    // for. The jump is consumed either way, so a stale one cannot re-fire on the next recompose.
    LaunchedEffect(pendingJump) {
        val jump = pendingJump ?: return@LaunchedEffect
        // +1 because item 0 is the header.
        listState.scrollToItem((jump.paragraphIndex + 1).coerceAtLeast(0))
        onConsumeJump()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // The shell already has a Scaffold; this one only hosts the bar and the progress line.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        FilledTonalIconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back_to_library),
                            )
                        }
                    },
                    actions = {
                        TextSizeControl(scale = settings.textScale, onScale = onTextScale)
                        FilledTonalIconButton(onClick = { showWordsSheet = true }) {
                            Icon(
                                imageVector = Icons.Rounded.FormatSize,
                                contentDescription = if (sessionWords.isEmpty()) {
                                    stringResource(R.string.reader_progress_words)
                                } else {
                                    stringResource(R.string.reader_progress_words_count, sessionWords.size)
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
                // A hairline progress bar rather than a percentage. The reader wants to know how
                // much is left, not what number they have reached.
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = lingo.accent,
                    trackColor = Color.Transparent,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = Gutter.reading,
                end = Gutter.reading,
                top = inner.calculateTopPadding() + Space.sm,
                bottom = Space.huge,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item(key = "header") {
                ArticleHeader(article = article, onShowWords = { showWordsSheet = true })
            }

            itemsIndexed(
                items = article.paragraphs,
                key = { index, _ -> "p$index" },
            ) { index, paragraph ->
                TappableParagraph(
                    text = paragraph,
                    style = reading.body,
                    bodyColor = lingo.onReadingSurface,
                    showHint = settings.highlightTappableWords,
                    savedWords = savedWords,
                    // Held for as long as this word's card is open, not just for the duration of
                    // the tap. The card is a popup beside the word, and for a reader looking at a
                    // whole sentence there is nothing else in it that says *which* word it is
                    // for. Clearing the highlight the instant the tap resolved left the two
                    // visually unconnected, which is the one thing a definition popup cannot
                    // afford to be.
                    activeRange = openLookup
                        ?.takeIf { it.paragraphIndex == index }
                        ?.range,
                    hintColor = lingo.wordHint,
                    savedColor = lingo.wordSaved,
                    pressedBackground = lingo.accent,
                    pressedColor = lingo.onAccent,
                    haptics = settings.haptics && LingoTheme.motionEnabled,
                    onClick = { start, end, anchor -> onTapWord(index, start, end, anchor) },
                    onLongPress = onLongPressWord,
                )
            }

            item(key = "footer") {
                Spacer(Modifier.height(Space.xl))
                ArticleFooter(article = article)
            }
        }
    }

    if (showWordsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWordsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ArticleWordsSheet(
                words = sessionWords,
                onOpenWordList = {
                    showWordsSheet = false
                    onBack()
                },
            )
        }
    }
}

@Composable
private fun ArticleHeader(article: Article, onShowWords: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        if (article.byline.isNotBlank()) {
            Text(
                text = article.byline,
                style = LingoTheme.reading.byline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = article.title,
            style = LingoTheme.reading.headline,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (article.domain.isNotBlank()) MetaChip(text = article.domain)
            MetaChip(
                text = pluralStringResource(
                    R.plurals.article_word_count,
                    ReadingStats.wordCount(article),
                    ReadingStats.wordCount(article),
                ),
            )
            MetaChip(
                text = stringResource(R.string.article_minutes_read, ReadingStats.readingMinutes(article)),
            )
        }

        // The affordance stated outright, once, at the top of the article rather than buried in a
        // footer where the pre-redesign version put a URL it had already shown twice.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(
                Icons.Rounded.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = LingoTheme.colors.accent,
            )
            Text(
                text = stringResource(R.string.reader_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = onShowWords, contentPadding = PaddingValues(horizontal = Space.sm)) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
            Text(stringResource(R.string.reader_words_here))
        }
    }
}

@Composable
private fun ArticleFooter(article: Article) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            text = article.domain,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The dictionary licence is a condition of using the definitions, so it has to be visible
        // where the definitions were used. The lookup card deliberately does not carry it — one
        // line per hundred lookups would bury the reading — but the reader is a place a licence can
        // live without being in the way.
        Text(
            text = stringResource(R.string.attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One paragraph, with every word tappable and visibly so.
 *
 * The architecture is unchanged from the pre-redesign reader, deliberately: words are annotated as
 * string ranges and rendered as a single `BasicText`, so a long article costs one layout pass per
 * paragraph rather than one click handler per token. What is new is the treatment of those spans.
 *
 * Three tiers, and the third is the one that earns its place:
 *
 * 1. **Tappable** — the reading colour walked a tenth of the way toward the accent. Quiet enough to
 *    read straight through, distinct enough to notice once. Carried by colour rather than by an
 *    underline because `SpanStyle` in Compose 1.11 has no `textDecorationColor`, so an underline
 *    always takes its span's text colour; underlining every word at full strength is a wall of
 *    noise, not a hint. Switchable in Settings.
 * 2. **Saved** — the same walk, a quarter of the way, and underlined. Saved words are few, so a
 *    full-strength underline costs nothing, and this tier is *information*: a second read of an
 *    article shows you what you collected the first time.
 * 3. **Pressed** — a filled accent highlight with a haptic tick on press. A tap with no feedback is
 *    indistinguishable from a tap that missed, which is the failure that made the pre-redesign
 *    interaction feel broken even when it worked.
 *
 * On accessibility: the paragraph carries **no** `contentDescription`, on purpose. A `BasicText`
 * with word annotations does not expose per-word nodes, which is a genuine limitation — but
 * setting a contentDescription here to describe the interaction would *override* the paragraph's
 * text in the semantics tree, so a screen reader user would hear "tap a word to see its meaning"
 * instead of the article. Being unable to read the text at all is far worse than not having the
 * gesture announced, and the same hint is visible, and therefore readable, once in the header.
 */
@Composable
private fun TappableParagraph(
    text: String,
    style: TextStyle,
    bodyColor: Color,
    showHint: Boolean,
    savedWords: Set<String>,
    activeRange: IntRange?,
    hintColor: Color,
    savedColor: Color,
    pressedBackground: Color,
    pressedColor: Color,
    haptics: Boolean,
    onClick: (Int, Int, Offset) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val spans = remember(text) { GermanTokenizer.words(text) }
    val haptic = LocalHapticFeedback.current
    // Transient, while the finger is down. `activeRange` covers the longer window.
    var pressed by remember(text) { mutableStateOf<IntRange?>(null) }
    val lit: IntRange? = pressed ?: activeRange

    val annotated = remember(text, spans, lit, showHint, savedWords, hintColor, savedColor) {
        buildAnnotatedString {
            var cursor = 0
            for (span in spans) {
                if (span.start > cursor) append(text.substring(cursor, span.start))

                val isPressed = lit?.let { span.start <= it.last && span.end > it.first } == true
                val isSaved = span.text.lowercase() in savedWords

                val spanStyle = when {
                    isPressed -> SpanStyle(background = pressedBackground, color = pressedColor)
                    isSaved -> SpanStyle(color = savedColor, textDecoration = TextDecoration.Underline)
                    showHint -> SpanStyle(color = hintColor)
                    else -> SpanStyle(color = bodyColor)
                }

                withStyle(spanStyle) {
                    pushStringAnnotation(WORD_TAG, "${span.start}:${span.end}")
                    append(span.text)
                    pop()
                }
                cursor = span.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val originInWindow = remember { mutableStateOf(Offset.Zero) }

    // Screen readers get the paragraph as one block of text plus a description of the
    // interaction, because a BasicText with annotations does not expose per-word nodes. Announcing
    // "tap any word" is the honest description of what a sighted reader can see.
    androidx.compose.foundation.text.BasicText(
        text = annotated,
        style = style.copy(color = bodyColor),
        onTextLayout = { layout.value = it },
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { originInWindow.value = it.positionInWindow() }
            .pointerInput(text, spans) {
                detectTapGestures(
                    onLongPress = { offset ->
                        val result = layout.value ?: return@detectTapGestures
                        val position = result.getOffsetForPosition(offset)
                        wordAt(annotated, position)?.let { range ->
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(text.substring(range.first, range.last + 1))
                        }
                    },
                    onTap = { offset ->
                        val result = layout.value ?: return@detectTapGestures
                        val position = result.getOffsetForPosition(offset)
                        wordAt(annotated, position)?.let { range ->
                            // Light the word up for as long as the finger is down, so the tap has
                            // a beginning. Cleared on release, just before the card takes over.
                            pressed = range
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick(range.first, range.last + 1, originInWindow.value + offset)
                        }
                    },
                )
            },
    )
}

/** The annotated word range under [position], or null for whitespace and punctuation. */
private fun wordAt(annotated: androidx.compose.ui.text.AnnotatedString, position: Int): IntRange? =
    annotated.getStringAnnotations(WORD_TAG, position, position)
        .firstOrNull()
        ?.item
        ?.split(":")
        ?.let { parts ->
            if (parts.size == 2) parts[0].toInt() until parts[1].toInt() else null
        }

/** A− / A+, the one control a reader actually reaches for mid-article. */
@Composable
private fun TextSizeControl(scale: Float, onScale: (Float) -> Unit) {
    val step = 0.1f
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = { onScale(scale - step) },
            enabled = scale > 0.8f,
            contentPadding = PaddingValues(horizontal = Space.sm),
        ) {
            Text(stringResource(R.string.reader_smaller), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(
            onClick = { onScale(scale + step) },
            enabled = scale < 1.3f,
            contentPadding = PaddingValues(horizontal = Space.sm),
        ) {
            Text(stringResource(R.string.reader_larger), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * The words looked at during this article.
 *
 * Its real job is structural. The pre-redesign app hid the bottom navigation on the reader, so
 * once you were reading there was no route at all back to your word list without going back and
 * then across. This is that route, and it doubles as a record of what you have met.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleWordsSheet(
    words: List<SessionWord>,
    onOpenWordList: () -> Unit,
) {
    if (words.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.AutoStories,
            title = stringResource(R.string.reader_session_empty_title),
            body = stringResource(R.string.reader_session_empty_body),
        )
        return
    }

    Column(
        modifier = Modifier.padding(
            start = Gutter.standard,
            end = Gutter.standard,
            bottom = Space.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        SectionHeader(title = stringResource(R.string.reader_words_here), count = words.size)
        words.forEach { word ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text(
                    text = word.word,
                    style = LingoTheme.reading.word,
                    modifier = Modifier.weight(1f),
                )
                MetaChip(
                    text = if (word.saved) {
                        stringResource(R.string.reader_session_saved)
                    } else {
                        stringResource(R.string.reader_session_looked_up)
                    },
                    containerColor = if (word.saved) {
                        LingoTheme.colors.accentContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (word.saved) {
                        LingoTheme.colors.onAccentContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        FilledTonalButton(
            onClick = onOpenWordList,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reader_session_open_list))
        }
    }
}

private const val WORD_TAG = "word"
