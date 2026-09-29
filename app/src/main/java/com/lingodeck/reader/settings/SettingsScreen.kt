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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalResources
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
import com.lingodeck.reader.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lingodeck.reader.BuildConfig
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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
    onShowLicences: () -> Unit,
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
                title = { Text(stringResource(R.string.settings_title)) },
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

            item(key = "appearance-header") { SectionHeader(title = stringResource(R.string.settings_appearance)) }

            item(key = "theme") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                        Text(
                            text = stringResource(R.string.settings_theme),
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
                    title = stringResource(R.string.settings_dynamic_title),
                    body = stringResource(R.string.settings_dynamic_body),
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }

            item(key = "reading-header") { SectionHeader(title = stringResource(R.string.settings_reading)) }

            item(key = "text-size") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    val textSizeLabel = stringResource(R.string.settings_text_size)
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = textSizeLabel,
                                style = LingoTheme.emphasized.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_text_size_sp,
                                    (20f * settings.textScale).roundToInt(),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = settings.textScale,
                            onValueChange = onTextScaleChange,
                            valueRange = Settings.MIN_TEXT_SCALE..Settings.MAX_TEXT_SCALE,
                            // Continuous rather than stepped: the step marks were five prominent
                            // dots across the card, and a text size is a dial, not a multiple
                            // choice. The current size is already spelled out beside the label.
                            steps = 0,
                            modifier = Modifier.semantics { contentDescription = textSizeLabel },
                        )
                        // A live sample, so the effect is visible while choosing rather than after.
                        Text(
                            text = stringResource(R.string.settings_text_sample),
                            style = LingoTheme.reading.at(settings.textScale).body,
                            color = LingoTheme.colors.onReadingSurface,
                        )
                    }
                }
            }

            item(key = "haptics") {
                SettingSwitch(
                    title = stringResource(R.string.settings_haptics),
                    body = stringResource(R.string.settings_haptics_body),
                    checked = settings.haptics,
                    onCheckedChange = onHapticsChange,
                )
            }

            item(key = "highlight") {
                SettingSwitch(
                    title = stringResource(R.string.settings_tint_title),
                    body = stringResource(R.string.settings_tint_body),
                    checked = settings.highlightTappableWords,
                    onCheckedChange = onHighlightChange,
                )
            }

            item(key = "anki-header") { SectionHeader(title = stringResource(R.string.settings_anki)) }

            item(key = "deck") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(
                            text = stringResource(R.string.settings_deck),
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
                            text = stringResource(R.string.settings_deck_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "data-header") { SectionHeader(title = stringResource(R.string.settings_data)) }

            item(key = "counts") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        DataRow(stringResource(R.string.settings_data_articles), counts.articles.toString())
                        DataRow(stringResource(R.string.settings_data_words), counts.words.toString())
                        DataRow(stringResource(R.string.settings_data_sent), counts.sentToAnki.toString())
                    }
                }
            }

            item(key = "export") {
                TextButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_export))
                }
            }

            item(key = "clear-words") {
                TextButton(
                    onClick = { confirmClearWords = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = counts.words > 0,
                ) {
                    Text(stringResource(R.string.settings_clear_words), color = MaterialTheme.colorScheme.error)
                }
            }

            item(key = "clear-history") {
                TextButton(
                    onClick = { confirmClearHistory = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = counts.articles > 0,
                ) {
                    Text(stringResource(R.string.settings_clear_history), color = MaterialTheme.colorScheme.error)
                }
            }

            item(key = "about-header") { SectionHeader(title = stringResource(R.string.settings_about)) }

            item(key = "about") {
                LingoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = LingoTheme.emphasized.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.settings_about_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.attribution),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onShowLicences,
                            contentPadding = PaddingValues(horizontal = Space.sm),
                        ) {
                            Text(stringResource(R.string.licence_fonts))
                        }
                    }
                }
            }
        }
    }

    if (confirmClearWords) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_words_title),
            body = pluralStringResource(R.plurals.settings_clear_words_body, counts.words, counts.words),
            onConfirm = { onClearWords(); confirmClearWords = false },
            onDismiss = { confirmClearWords = false },
        )
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_history_title),
            body = pluralStringResource(R.plurals.settings_clear_history_body, counts.articles, counts.articles),
            onConfirm = { onClearHistory(); confirmClearHistory = false },
            onDismiss = { confirmClearHistory = false },
        )
    }
}

/** The theme segmented button needs a string, and a `when` on an enum is clearer than a map. */
private fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}

/**
 * The bundled font licences.
 *
 * The OFL requires the licence to accompany the font. Shipping it as a raw resource is the only
 * way it travels with the binary, and this is what makes those resources reachable: reading
 * `R.raw.inter_ofl` and `R.raw.literata_ofl` out of the APK and showing them.
 */
@Composable
fun LicenceDialog(onDismiss: () -> Unit) {
    // LocalResources rather than LocalContext.current.resources: it is the Compose-native way and
    // it is what keeps this correct under configuration changes and in a preview.
    val resources = LocalResources.current
    val text = remember(resources) {
        listOf(R.raw.inter_ofl, R.raw.literata_ofl).joinToString("\n\n") { id ->
            resources.openRawResource(id).bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.licences_title)) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
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
                    text = stringResource(R.string.app_name),
                    style = LingoTheme.emphasized.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
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
            // The title above is a separate semantics node, so without this the control is
            // announced as just "switch" with no indication of what it switches.
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title },
            )
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
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_confirm_clear), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/**
 * Provider labels and blurbs, kept beside the radio list so adding a provider is one enum entry
 * and one pair of string resources rather than a new branch in the settings composable.
 */
