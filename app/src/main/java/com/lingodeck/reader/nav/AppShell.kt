package com.lingodeck.reader.nav

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.lingodeck.reader.anki.AnkiDroid
import com.lingodeck.reader.library.LibraryScreen
import com.lingodeck.reader.reader.LookupCardHost
import com.lingodeck.reader.reader.ReaderScreen
import com.lingodeck.reader.settings.LicenceDialog
import com.lingodeck.reader.settings.SettingsScreen
import com.lingodeck.reader.store.Settings
import com.lingodeck.reader.ui.MainViewModel
import com.lingodeck.reader.ui.Screen
import com.lingodeck.reader.ui.UiState
import com.lingodeck.reader.ui.Undo
import com.lingodeck.reader.ui.components.MorphingLoadingIndicator
import com.lingodeck.reader.ui.theme.LingoTheme
import com.lingodeck.reader.ui.theme.Space
import com.lingodeck.reader.words.DictionaryScreen
import com.lingodeck.reader.words.WordListScreen
import com.lingodeck.reader.R
import androidx.compose.ui.res.stringResource
import com.lingodeck.reader.ui.UiText

/** The three tabs. The reader is a pushed level on top of them, not a fourth tab. */
private enum class Tab(val screen: Screen, val labelRes: Int) {
    Library(Screen.Library, R.string.tab_library),
    Words(Screen.Words, R.string.tab_words),
    Settings(Screen.Settings, R.string.tab_settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    state: UiState,
    settings: Settings,
    model: MainViewModel,
    onReleaseSplash: () -> Unit,
    onExportTsv: () -> Unit,
) {
    LaunchedEffect(Unit) { onReleaseSplash() }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val vocabView by model.vocabView.collectAsStateWithLifecycle()
    var showLicences by remember { mutableStateOf(false) }
    val expandedSenses by model.expandedSenses.collectAsStateWithLifecycle()
    val currentTab = Tab.entries.firstOrNull { it.screen == state.screen }

    // AnkiDroid's ContentProvider needs a runtime permission the first time. The launcher lives
    // here rather than in the Activity so the request is scoped to the composable that asked for
    // it, and the save resumes on the callback.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) model.sendLookupToAnki()
        else model.showMessage(UiText.of(R.string.anki_permission_needed))
    }

    // Read out here because the LaunchedEffect block below is not a composable context.
    val undoLabel = stringResource(R.string.undo)

    // Messages and errors, with an undo action whenever there is something to take back. Every
    // deletion in the redesign is undoable, because swipe-to-dismiss with no way back is a worse
    // trap than a confirmation dialog.
    LaunchedEffect(state.message, state.error, state.undo) {
        // Resolved here rather than in the view model, which cannot call getString.
        val pending = state.message ?: state.error ?: return@LaunchedEffect
        val text = pending.resolve(context)
        val undoable = state.undo != null
        val result = snackbar.showSnackbar(
            message = text,
            actionLabel = if (undoable) undoLabel else null,
            duration = if (undoable) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        model.consumeMessage()
        if (result == SnackbarResult.ActionPerformed) {
            when (state.undo) {
                is Undo.RestoreVocab -> model.undoRemoveVocab()
                is Undo.RestoreArticle -> model.undoRemoveArticle()
                null -> Unit
            }
        }
    }

    // Back from a pushed level returns to the tab behind it. `enableOnBackInvokedCallback` is on,
    // so this BackHandler is what answers the gesture; without it the app would be dismissed
    // instead.
    BackHandler(enabled = state.screen == Screen.Reader) { model.closeArticle() }
    BackHandler(enabled = state.screen == Screen.Dictionary) { model.closeDictionary() }

    // Read out here rather than inside transitionSpec: that lambda is not a composable
    // context, and reaching for the theme from inside it does not compile.
    val slideSpec = LingoTheme.motion.screenOffset
    val scaleSpec = LingoTheme.motion.screen

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Hidden on the reader: an article is a level you go into, and the in-article words
            // sheet is the route back to the word list from there.
            if (currentTab != null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                when (tab) {
                                    Tab.Library -> model.openLibrary()
                                    Tab.Words -> model.openWords()
                                    Tab.Settings -> model.openSettings()
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        Tab.Library -> Icons.AutoMirrored.Rounded.MenuBook
                                        Tab.Words -> Icons.AutoMirrored.Filled.List
                                        Tab.Settings -> Icons.Filled.Settings
                                    },
                                    // Null, because the label below is what a screen reader
                                    // announces. These used to be null with no label, which left
                                    // the bottom bar entirely unlabelled.
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // Deeper levels slide in from the side; sibling tabs cross-fade with a slight
                // scale, so moving between Library and Words does not read as pushing a new screen.
                val slide = slideSpec
                // Both pushed levels slide in from the side; tabs cross-fade between themselves.
                if (targetState == Screen.Reader || targetState == Screen.Dictionary) {
                    (slideInHorizontally(slide) { it / 6 } + fadeIn(tween(220)))
                        .togetherWith(fadeOut(tween(160)))
                } else if (initialState == Screen.Reader || initialState == Screen.Dictionary) {
                    fadeIn(tween(220))
                        .togetherWith(slideOutHorizontally(slide) { it / 6 } + fadeOut(tween(180)))
                } else {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = scaleSpec))
                        .togetherWith(fadeOut(tween(160)))
                }
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Library -> LibraryScreen(
                    state = state,
                    contentPadding = padding,
                    onReadUrl = model::loadUrl,
                    onOpenArticle = model::openArticle,
                    onRemoveArticle = model::removeArticle,
                    onOpenWords = model::openWords,
                    onOpenSettings = model::openSettings,
                )

                Screen.Reader -> {
                    val article = state.article
                    if (article == null) {
                        // Only ever null while the first fetch is in flight. The pre-redesign app
                        // showed this as a bare centred CircularProgressIndicator with no
                        // explanation of what was being waited on.
                        ReaderLoadingState(padding)
                    } else {
                        ReaderScreen(
                            article = article,
                            settings = settings,
                            sessionWords = state.sessionWords,
                            savedWords = state.vocab
                                .filter { it.sourceUrl == article.url }
                                .map { it.word.lowercase() }
                                .toSet(),
                            pendingJump = state.pendingJump,
                            openLookup = state.lookup,
                            contentPadding = padding,
                            onBack = model::closeArticle,
                            onTapWord = { index, start, end, anchor ->
                                model.openLookup(index, start, end, anchor.x, anchor.y)
                            },
                            onLongPressWord = model::speakWord,
                            onProgress = model::recordProgress,
                            onConsumeJump = model::consumeJump,
                            onTextScale = model::setTextScale,
                        )
                    }
                }

                Screen.Words -> WordListScreen(
                    vocab = state.vocab,
                    query = vocabView.query,
                    filter = vocabView.filter,
                    sort = vocabView.sort,
                    contentPadding = padding,
                    onQueryChange = model::setVocabQuery,
                    onFilterChange = model::setVocabFilter,
                    articleFilter = vocabView.article,
                    onArticleFilterChange = model::setVocabArticleFilter,
                    onSortChange = model::setVocabSort,
                    onDelete = model::deleteVocab,
                    onSendToAnki = model::sendStoredToAnki,
                    onSpeak = model::speakWord,
                    onReopenInArticle = model::reopenInArticle,
                    onExport = onExportTsv,
                    onBrowseLibrary = model::openLibrary,
                    onOpenDictionary = model::openDictionary,
                )

                Screen.Dictionary -> DictionaryScreen(
                    state = state,
                    contentPadding = padding,
                    onLookup = model::openStandaloneLookup,
                    onClearRecent = model::clearRecentLookups,
                    onDismiss = model::closeDictionary,
                    onSave = model::saveLookupToVocab,
                    onSendToAnki = {
                        if (AnkiDroid.hasPermission(context)) model.sendLookupToAnki()
                        else permissionLauncher.launch(AnkiDroid.PERMISSION)
                    },
                    onSpeak = model::speakLookupWord,
                )

                Screen.Settings -> SettingsScreen(
                    settings = settings,
                    counts = SettingsCounts(
                        articles = state.library.size,
                        words = state.vocab.size,
                        sentToAnki = state.vocab.count { it.sentToAnkiAt != null },
                    ),
                    contentPadding = padding,
                    onThemeChange = model::setThemeMode,
                    onDynamicColorChange = model::setDynamicColor,
                    onTextScaleChange = model::setTextScale,
                    onDeckNameChange = model::setDeckName,
                    onHapticsChange = model::setHaptics,
                    onHighlightChange = model::setHighlightTappableWords,
                    onExport = onExportTsv,
                    onClearWords = model::clearVocab,
                    onClearHistory = model::clearArticleHistory,
                    onShowLicences = { showLicences = true },
                )
            }
        }
    }

    if (showLicences) {
        LicenceDialog(onDismiss = { showLicences = false })
    }

    // The lookup card is an overlay, not a screen, so it lives above the shell rather than inside
    // any of its branches and survives the screen underneath it changing.
    // Reader only. The card is an overlay anchored to a word in the text, so it has nothing to
    // anchor to on the dictionary screen, which renders the same card inline below its field
    // instead. Left ungated the two would both appear.
    state.lookup?.takeIf { state.screen == Screen.Reader }?.let { lookup ->
        LookupCardHost(
            lookup = lookup,
            onDismiss = model::dismissLookup,
            onSave = model::saveLookupToVocab,
            onSendToAnki = {
                if (AnkiDroid.hasPermission(context)) model.sendLookupToAnki()
                else permissionLauncher.launch(AnkiDroid.PERMISSION)
            },
            onChooseGloss = model::chooseGloss,
            onSpeak = model::speakLookupWord,
            onToggleAllSenses = { model.toggleAllSenses(lookup.word) },
            isShowingAllSenses = expandedSenses.contains(lookup.word),
            onRecord = { model.recordSessionWord(lookup.word, lookup.chosenGloss) },
        )
    }
}

@Composable
private fun ReaderLoadingState(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        MorphingLoadingIndicator(size = Space.huge)
    }
}

/** What the Settings screen reports about the user's data. */
data class SettingsCounts(val articles: Int, val words: Int, val sentToAnki: Int)
