package com.lingodeck.reader.settings

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lingodeck.reader.brand.BrandMark
import com.lingodeck.reader.data.ThemeMode
import com.lingodeck.reader.nav.SettingsCounts
import com.lingodeck.reader.store.Settings
import com.lingodeck.reader.ui.components.LingoCard
import com.lingodeck.reader.ui.components.SectionHeader
import com.lingodeck.reader.ui.theme.Gutter
import com.lingodeck.reader.ui.theme.LingoTheme
import com.lingodeck.reader.ui.theme.Space
import kotlin.math.roundToInt

/**
 * Settings, which the pre-redesign app did not have.
 *
 * It is not a list of nice-to-haves. Three of the controls here exist because something in the app
 * was previously unreachable: the Anki deck name, which `MainViewModel.setDeckName` set and
 * nothing ever called; the reader's text size, which was a hardcoded 18sp; and the theme, which
 * followed the system with no way to override it in either direction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    counts: SettingsCounts,
    contentPadding: PaddingValues,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onDeckNameChange: (String) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onHighlightChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onClearWords: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val listState = rememberLazyListState()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)
    var confirmClearWords by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            MediumTopAppBar(
                title = { Text("Settings") },
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
            item(key = "brand") { BrandHeader() }

            item(key = "appearance-header") { SectionHeader(title = "Appearance") }

            item(key = "theme") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        Text(
                            text = "Theme",
                            style = LingoTheme.emphasized.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { onThemeChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size,
                                    ),
                                    icon = {
                                        Icon(
                                            imageVector = when (mode) {
                                                ThemeMode.System -> Icons.Rounded.PhoneAndroid
                                                ThemeMode.Light -> Icons.Rounded.LightMode
                                                ThemeMode.Dark -> Icons.Rounded.DarkMode
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                ) {
                                    Text(mode.name)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "dynamic") {
                SettingSwitch(
                    title = "Use wallpaper colours",
                    body = "Material You, from the system palette. Off by default: the accent " +
                        "marks which words are tappable and which you have saved, and handing " +
                        "that to an arbitrary wallpaper colour makes the app's one real " +
                        "affordance unreliable.",
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }

            item(key = "reading-header") { SectionHeader(title = "Reading") }

            item(key = "text-size") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Article text size",
                                style = LingoTheme.emphasized.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${(20f * settings.textScale).roundToInt()} sp",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = settings.textScale,
                            onValueChange = onTextScaleChange,
                            valueRange = Settings.MIN_TEXT_SCALE..Settings.MAX_TEXT_SCALE,
                            steps = 4,
                        )
                        // A live sample, so the effect is visible while choosing rather than after.
                        Text(
                            text = "Das ist die höchste Zahl des Quartals.",
                            style = LingoTheme.reading.at(settings.textScale).body,
                            color = LingoTheme.colors.onReadingSurface,
                        )
                    }
                }
            }

            item(key = "highlight") {
                SettingSwitch(
                    title = "Tint tappable words",
                    body = "Colours every word in the article slightly, so it is visible that " +
                        "words can be tapped. Words you have saved are tinted further and " +
                        "underlined.",
                    checked = settings.highlightTappableWords,
                    onCheckedChange = onHighlightChange,
                )
            }

            item(key = "anki-header") { SectionHeader(title = "Anki") }

            item(key = "deck") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(
                            text = "Deck name",
                            style = LingoTheme.emphasized.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = settings.deckName,
                            onValueChange = onDeckNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                        )
                        Text(
                            text = "Cards are created in this deck in AnkiDroid, which must be " +
                                "installed and have granted LingoDeck access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "data-header") { SectionHeader(title = "Data") }

            item(key = "counts") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        DataRow("Articles in the library", counts.articles.toString())
                        DataRow("Words saved", counts.words.toString())
                        DataRow("Sent to Anki", counts.sentToAnki.toString())
                    }
                }
            }

            item(key = "export") {
                TextButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Text("Export all words as Anki TSV")
                }
            }

            item(key = "clear-words") {
                TextButton(
                    onClick = { confirmClearWords = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = counts.words > 0,
                ) {
                    Text("Clear word list", color = MaterialTheme.colorScheme.error)
                }
            }

            item(key = "clear-history") {
                TextButton(
                    onClick = { confirmClearHistory = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = counts.articles > 0,
                ) {
                    Text("Clear article history", color = MaterialTheme.colorScheme.error)
                }
            }

            item(key = "about-header") { SectionHeader(title = "About") }

            item(key = "about") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(
                            text = "LingoDeck Reader",
                            style = LingoTheme.emphasized.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "An Android companion to the LingoDeck Chrome extension. " +
                                "Words saved here and in the browser land on the same Anki card.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Definitions: Kaikki / English Wiktionary, CC BY-SA 4.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Typefaces: Literata and Inter, both under the SIL Open Font " +
                                "Licence 1.1.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmClearWords) {
        ConfirmDialog(
            title = "Clear the word list?",
            body = "This removes all ${counts.words} saved words from this device. " +
                "Cards already in AnkiDroid are not touched.",
            confirmLabel = "Clear",
            onConfirm = { onClearWords(); confirmClearWords = false },
            onDismiss = { confirmClearWords = false },
        )
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = "Clear article history?",
            body = "This removes all ${counts.articles} saved articles from this device. " +
                "Saved words are not touched.",
            confirmLabel = "Clear",
            onConfirm = { onClearHistory(); confirmClearHistory = false },
            onDismiss = { confirmClearHistory = false },
        )
    }
}

@Composable
private fun BrandHeader() {
    LingoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            BrandMark(size = 40.dp, contentDescription = "LingoDeck")
            Column {
                Text(
                    text = "LingoDeck Reader",
                    style = LingoTheme.emphasized.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Version 0.2.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    LingoCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    text = title,
                    style = LingoTheme.emphasized.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = LingoTheme.emphasized.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
