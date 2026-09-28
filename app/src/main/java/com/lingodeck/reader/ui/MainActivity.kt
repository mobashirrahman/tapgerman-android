package com.lingodeck.reader.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lingodeck.reader.anki.AnkiDroid
import com.lingodeck.reader.data.Article
import com.lingodeck.reader.ui.theme.LingoDeckTheme
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            LingoDeckTheme {
                App(model = model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!shared.isNullOrBlank()) model.loadUrl(shared)
            }
            Intent.ACTION_VIEW -> intent.dataString?.let { model.loadUrl(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(model: MainViewModel) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) model.sendLookupToAnki()
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbar.showSnackbar(text)
            model.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (state.screen) {
                            Screen.Library -> "LingoDeck Reader"
                            Screen.Words -> "Saved words"
                            Screen.Reader -> state.article?.title ?: "Reading"
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    if (state.screen == Screen.Reader) {
                        IconButton(onClick = model::closeArticle) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.screen != Screen.Reader) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.screen == Screen.Library,
                        onClick = model::openLibrary,
                        icon = { Icon(Icons.Filled.Book, contentDescription = null) },
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = state.screen == Screen.Words,
                        onClick = model::openWords,
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Words") },
                    )
                }
            }
        },
    ) { padding ->
        when (state.screen) {
            Screen.Library -> LibraryScreen(state = state, model = model, contentPadding = padding)

            Screen.Reader -> {
                val article = state.article
                if (article == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    ReaderScreen(
                        article = article,
                        onTapWord = { paragraphIndex, start, end, anchor ->
                            model.openLookup(paragraphIndex, start, end, anchor.x, anchor.y)
                        },
                        contentPadding = padding,
                    )
                }
            }

            Screen.Words -> WordListScreen(
                items = state.vocab,
                onDelete = model::deleteVocab,
                onExport = { exportTsv(context, model) },
                contentPadding = padding,
            )
        }
    }

    state.lookup?.let { lookup ->
        LookupCard(
            lookup = lookup,
            anchor = IntOffset(lookup.anchorX.roundToInt(), lookup.anchorY.roundToInt()),
            onDismiss = model::dismissLookup,
            onSave = model::saveLookupToVocab,
            onChooseGloss = model::chooseGloss,
            onSpeak = model::speakLookupWord,
            onSendToAnki = {
                if (AnkiDroid.hasPermission(context)) {
                    model.sendLookupToAnki()
                } else {
                    permissionLauncher.launch(AnkiDroid.PERMISSION)
                }
            },
        )
    }
}

private fun exportTsv(context: android.content.Context, model: MainViewModel) {
    val file = File(context.cacheDir, model.exportTsvFileName())
    file.writeText(model.exportTsv(), Charsets.UTF_8)
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/tab-separated-values"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Export Anki TSV"))
}

@Composable
private fun LibraryScreen(
    state: UiState,
    model: MainViewModel,
    contentPadding: PaddingValues,
) {
    var typed by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Read a German article without translating it.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Share a page from Chrome, or paste its address here. " +
                        "Tap any word to see what it means, then keep it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Article address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        model.loadUrl(typed)
                        typed = ""
                    },
                    enabled = typed.isNotBlank() && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.loading) "Reading…" else "Read article")
                }
                if (state.error != null) {
                    Text(text = state.error, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = model::openWords, modifier = Modifier.fillMaxWidth()) {
                    Text("Saved words (${state.vocab.size})")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Recently read",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (state.library.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Nothing here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.library, key = { it.url }) { article ->
            ArticleCard(article = article, onClick = { model.openArticle(article) })
        }
    }
}

@Composable
private fun ArticleCard(article: Article, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(article.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (article.byline.isNotBlank()) {
                Text(
                    article.byline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                article.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
