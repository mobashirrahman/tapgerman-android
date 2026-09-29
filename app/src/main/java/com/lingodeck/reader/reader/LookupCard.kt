package com.lingodeck.reader.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lingodeck.reader.data.LookupCardBuilder
import com.lingodeck.reader.ui.LookupUi
import com.lingodeck.reader.ui.components.ErrorSurface
import com.lingodeck.reader.ui.components.LingoButtonGroup
import com.lingodeck.reader.ui.components.MorphingLoadingIndicator
import com.lingodeck.reader.ui.theme.LingoTheme
import com.lingodeck.reader.ui.theme.Space
import com.lingodeck.reader.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext

/**
 * The lookup card, and the plumbing that gets it on screen.
 *
 * The pre-redesign app had a `Popup` whose position came from a `PopupPositionProvider`; that
 * decision was good and is kept. The host is split out from the card's contents so positioning
 * and dismissal can be reasoned about without the sense list in the way.
 */
@Composable
fun LookupCardHost(
    lookup: LookupUi,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSendToAnki: () -> Unit,
    onChooseGloss: (String) -> Unit,
    onSpeak: () -> Unit,
    onToggleAllSenses: () -> Unit,
    isShowingAllSenses: Boolean,
    onRecord: () -> Unit,
    onTranslate: () -> Unit,
) {
    // `Dp.value` is dp, not pixels, so the gap goes through the density to reach
    // [AnchoredPositionProvider], which works in raw pixels like the rest of PopupPositionProvider.
    val gapPx = with(LocalDensity.current) { CARD_GAP.roundToPx() }

    // Log the word as looked-at, so the reader's "words in this article" sheet is a real log
    // rather than only a list of what was kept.
    LaunchedEffect(lookup.word, lookup.paragraphIndex) { onRecord() }

    Popup(
        popupPositionProvider = AnchoredPositionProvider(
            anchor = IntOffset(lookup.anchorX.toInt(), lookup.anchorY.toInt()),
            gapPx = gapPx,
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        LookupCard(
            lookup = lookup,
            onDismiss = onDismiss,
            onSave = onSave,
            onSendToAnki = onSendToAnki,
            onChooseGloss = onChooseGloss,
            onSpeak = onSpeak,
            onToggleAllSenses = onToggleAllSenses,
            isShowingAllSenses = isShowingAllSenses,
            onTranslate = onTranslate,
        )
    }
}

/**
 * The card that appears next to a tapped word.
 *
 * Still deliberately a small [Popup] rather than a modal sheet: no scrim, no dimming, no takeover.
 * The article stays readable behind it and the reader is still in the text. What changed is
 * everything about how it looks and how it arrives.
 *
 * - **It animates in.** Scale and fade on a bouncy spring, so the card reads as arriving from the
 *   word rather than appearing out of nowhere. `animateFloatAsState` cannot do this, because it
 *   starts at its target; the entrance needs an [Animatable] driven to 1.
 * - **The word is set in the reading serif, large.** It is the thing the reader came for.
 * - **It is bigger than it was** (320x420dp against 300x280dp), because the senses now have room
 *   to be read rather than truncated at three.
 * - **The senses are a list whose selected state you can see**, with "Show all" when a word has
 *   more than four. The pre-redesign card showed four and silently dropped the rest.
 * - **The actions are a button group**, so Save and Send to Anki read as one decision rather than
 *   as two unrelated buttons that happened to be adjacent.
 */
/**
 * The card itself, with no popup around it.
 *
 * Internal rather than private so the screenshot test can render the card on a plain surface: a
 * real lookup is a `Popup`, which is a separate window and cannot be composed inside a test's
 * content, so the test needs the card without the window. Not public API — the popup positioning
 * and the dismissal behaviour are what callers should go through [LookupCardHost].
 */
@Composable
internal fun LookupCard(
    lookup: LookupUi,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSendToAnki: () -> Unit,
    onChooseGloss: (String) -> Unit,
    onSpeak: () -> Unit,
    onToggleAllSenses: () -> Unit,
    isShowingAllSenses: Boolean,
    onTranslate: () -> Unit,
) {
    // Entrance. Honours the system's animation setting: with animations off the card is simply
    // there, which is what someone who has turned them off is asking for. Read outside the
    // `remember` because that block is not a composable context.
    val motionEnabled = LingoTheme.motionEnabled
    val entrance = remember { Animatable(if (motionEnabled) 0.92f else 1f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    // Grammatical tags and worked examples, aligned with `lookup.glosses` by position.
    val details = remember(lookup.result, lookup.glosses) { senseDetails(lookup) }
    val lingo = LingoTheme.colors

    // Which of the two actions is being held. Taken from the buttons' own interaction sources
    // rather than from an `onClick`, because a press that turns into a cancellation (a finger
    // dragged off the button) must stop the squeeze, and `onClick` would not report that.
    val saveInteraction = remember { MutableInteractionSource() }
    val ankiInteraction = remember { MutableInteractionSource() }
    val savePressed by saveInteraction.collectIsPressedAsState()
    val ankiPressed by ankiInteraction.collectIsPressedAsState()
    val expanded = when {
        savePressed -> 0
        ankiPressed -> 1
        else -> null
    }

    // The card's height is a fraction of the window rather than a fixed dp. A fixed height is
    // wrong at both ends: on a short phone it leaves the card hanging well above the fold, and on
    // a tablet or in landscape it wastes half the screen. Sixty percent leaves enough of the
    // article visible behind it to keep the reader oriented, which is the whole reason this is a
    // popup and not a bottom sheet.
    val windowHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val maxHeight = (windowHeight * CARD_MAX_HEIGHT_FRACTION)
        .coerceIn(CARD_MIN_HEIGHT, CARD_MAX_HEIGHT)

    ElevatedCard(
        modifier = Modifier
            .widthIn(max = CARD_MAX_WIDTH)
            .heightIn(max = maxHeight)
            .scale(entrance.value),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING)) {
            // ---- header: word, metadata, pronounce, close. Never scrolls. ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lookup.word,
                        style = LingoTheme.reading.word,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = lookupMeta(lookup)
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.lookup_pronounce),
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.lookup_close),
                    )
                }
            }

            Spacer(Modifier.height(Space.sm))

            // ---- body: the only part allowed to scroll ----
            Column(
                modifier = Modifier
                    // `fill = false` so the card is as tall as its content and only reaches the
                    // height cap when there is genuinely that much to show. With `fill = true`
                    // a two-sense noun with no examples still stretched to 60% of the window and
                    // left a band of dead space under the buttons.
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                // No: the region is already bounded by the card. What it needs is room at the
                // bottom so the last line of a scrolled list does not run flush into the action
                // bar and read as a rendering fault rather than as more content below.
                Spacer(Modifier.height(Space.xs))
                if (lookup.loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                        modifier = Modifier.padding(vertical = Space.sm),
                    ) {
                        MorphingLoadingIndicator(size = 20.dp)
                        Text(
                            stringResource(R.string.lookup_loading),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                lookup.error?.let { ErrorSurface(message = it.resolve(LocalContext.current)) }

                if (lookup.glosses.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.lookup_which_meaning),
                        style = LingoTheme.emphasized.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val visible = if (isShowingAllSenses) {
                        lookup.glosses
                    } else {
                        lookup.glosses.take(MAX_VISIBLE_SENSES)
                    }
                    visible.forEachIndexed { index, gloss ->
                        SenseRow(
                            gloss = gloss,
                            // Tags ride on the row so the metadata arrives with the meaning
                            // rather than as a separate block the reader has to correlate.
                            tags = details.getOrNull(index)?.tags.orEmpty(),
                            tagsLabel = details.getOrNull(index)?.tagsLabel.orEmpty(),
                            selected = lookup.chosenGloss == gloss,
                            onSelect = { onChooseGloss(gloss) },
                        )
                    }
                    if (lookup.glosses.size > MAX_VISIBLE_SENSES) {
                        TextButton(
                            onClick = onToggleAllSenses,
                            contentPadding = PaddingValues(horizontal = Space.sm),
                        ) {
                            Text(
                                text = if (isShowingAllSenses) {
                                    stringResource(R.string.lookup_show_fewer)
                                } else {
                                    stringResource(R.string.lookup_show_all, lookup.glosses.size)
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                // The sentence the word came from, set in the reading serif and on a tonal
                // surface so it reads as a quotation rather than as more UI.
                //
                // Above the worked examples, not below. This is the text the reader was actually
                // reading when they tapped, and it is what they asked the card about; the
                // dictionary's own example is an illustration of the sense, which is secondary.
                // It also puts the translated sentence above the fold — with a worked example in
                // between, the English the reader came for was pushed off the bottom of a card
                // that had to scroll.
                if (lookup.sentence.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column(Modifier.padding(Space.md)) {
                            Text(
                                // The tapped word is marked inside the quotation. The card sits
                                // beside the word on screen, but the sentence is a different
                                // region of the page, and without this the two are only connected
                                // by the reader remembering which word they touched.
                                text = lookup.sentence.markWord(
                                    word = lookup.word,
                                    color = lingo.wordSaved,
                                ),
                                style = LingoTheme.reading.quote,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SentenceTranslation(
                                lookup = lookup,
                                onTranslate = onTranslate,
                            )
                        }
                    }
                }

                // Worked examples for the sense that is currently chosen, each a German sentence
                // with its English translation. This is the part that makes the entry teach
                // something: a gloss tells you what a word means, an example tells you how it is
                // used, and the translation is the bridge between the two for someone reading
                // the article to learn the language rather than to translate it.
                val chosen = details.firstOrNull { it.gloss == lookup.chosenGloss }
                    ?: details.firstOrNull()
                chosen?.examples?.take(MAX_VISIBLE_EXAMPLES)?.forEach { example ->
                    ExampleBlock(
                        german = example.text,
                        english = example.translation,
                        // The lemma, not the surface form: Wiktionary's examples are conjugations
                        // of the lemma, so "sagte" never appears inside "hat gesagt". Falls back
                        // to the surface form for a word with no lemma.
                        headword = lookup.result?.lemma?.takeIf { it.isNotBlank() } ?: lookup.word,
                    )
                }

            }

            lookup.savedMessage?.let { message ->
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = message.resolve(LocalContext.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(Space.md))

            // ---- actions: a group, so the two read as one decision ----
            LingoButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                expandedIndex = expanded,
                content = {
                    OutlinedButton(
                        onClick = onSave,
                        enabled = !lookup.saved,
                        interactionSource = saveInteraction,
                        modifier = Modifier.groupButton(0, 2).height(46.dp),
                        shape = groupShape(0, 2, savePressed),
                        contentPadding = PaddingValues(horizontal = Space.sm),
                    ) {
                        Icon(
                            imageVector = if (lookup.saved) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            text = if (lookup.saved) stringResource(R.string.lookup_saved) else stringResource(R.string.lookup_save),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Button(
                        onClick = onSendToAnki,
                        interactionSource = ankiInteraction,
                        modifier = Modifier.groupButton(1, 2).height(46.dp),
                        shape = groupShape(1, 2, ankiPressed),
                        contentPadding = PaddingValues(horizontal = Space.sm),
                    ) {
                        Icon(
                            Icons.Rounded.School,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            stringResource(R.string.lookup_anki),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        }
    }
}

/** The `·`-joined metadata line: lemma, part of speech, IPA. */
private fun lookupMeta(lookup: LookupUi): String {
    val firstEntry = lookup.result?.let { LookupCardBuilder.displayEntries(it).firstOrNull() }
    return listOfNotNull(
        lookup.result?.lemma?.takeIf { it.isNotBlank() && !it.equals(lookup.word, true) },
        firstEntry?.partOfSpeech?.takeIf { it.isNotBlank() },
        firstEntry?.ipa?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

/**
 * One selectable sense.
 *
 * The selected row gets a filled container rather than only a filled radio button. The radio
 * alone is a small target in a list of dense text, and the thing the reader is actually choosing
 * between is the gloss, not the control next to it.
 */
@Composable
private fun SenseRow(
    gloss: String,
    tags: List<String>,
    tagsLabel: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(vertical = Space.xs, horizontal = Space.xs),
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.sm))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = gloss,
                    style = LingoTheme.reading.gloss,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (tags.isNotEmpty()) {
                    // "feminine · transitive · weak". Only the selected row shows them: three
                    // rows of tags at once is a wall, and the point is to inform the choice
                    // rather than to front-load every sense's metadata.
                    if (selected) {
                        Text(
                            text = tagsLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The translation of the article sentence, and the control that fetches it.
 *
 * Sits inside the quotation surface rather than on the action row, because it belongs to that
 * sentence: it is a rendering of it, and a button three elements lower down reads as translating
 * the word instead.
 *
 * Shown whether or not a provider is configured, because the alternative is a feature that does
 * not exist until it has been found and set up. Tapping it with nothing configured says which
 * provider to choose, which is the only place that sentence is ever needed.
 */
@Composable
private fun SentenceTranslation(
    lookup: LookupUi,
    onTranslate: () -> Unit,
) {
    val translation = lookup.sentenceTranslation
    if (translation != null) {
        Spacer(Modifier.height(Space.sm))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Space.sm))
        // Marked, because two paragraphs of the same serif with a hairline between them are not
        // distinguishable at a glance, and a bilingual reader should not have to work out which
        // one they can read. "EN" is the convention every dictionary already uses, including the
        // one that supplied the sense list above.
        Text(
            text = "EN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = translation,
            // The reading serif, matching the German above it: this is a reading of a sentence,
            // not a string of status text.
            style = LingoTheme.reading.quote,
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }

    lookup.translationError?.let { error ->
        Spacer(Modifier.height(Space.sm))
        Text(
            text = error.resolve(LocalContext.current),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    Spacer(Modifier.height(Space.xs))
    if (lookup.translating) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = "Translating\u2026",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        TextButton(
            onClick = onTranslate,
            contentPadding = PaddingValues(horizontal = Space.xs, vertical = 0.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Space.xs))
            Text("Translate this sentence", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * One worked example: the German sentence, then its English translation.
 *
 * The translation is shown under the German rather than beside it because the two are rarely the
 * same length, and a side-by-side pair on a 320dp card produces a narrow, hard-to-read column of
 * two interleaved languages.
 */
@Composable
private fun ExampleBlock(german: String, english: String, headword: String) {
    val lingo = LingoTheme.colors
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = german.markStem(headword, lingo.wordSaved),
                style = LingoTheme.reading.quote,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (english.isNotBlank()) {
                Text(
                    text = english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Underlines every whole-word occurrence of [word] in [text], case-insensitively.
 *
 * Used for the sentence taken from the article, where the reader tapped a specific occurrence and
 * the point is to show that this is the one. Word boundaries matter here: without them, tapping
 * "Tag" would light up every "Tag" inside "Tagesordnung".
 *
 * Best-effort by design: if the word is not in the string at all, the text comes back unchanged
 * and nothing is underlined, rather than anything being rewritten.
 */
private fun String.markWord(word: String, color: Color): AnnotatedString =
    mark(Regex("(?i)(?<![\\p{L}])" + Regex.escape(word.trim()) + "(?=\\p{L}|$)"), color)

/**
 * Finds [word] in a dictionary example, which will usually have conjugated it.
 *
 * Wiktionary's examples conjugate the headword, so the headword is often simply absent: "sagen"
 * does not appear in "Ich habe nicht verstanden, was sie gesagt hat", and a whole-word match would
 * never fire, leaving the example looking like an unrelated sentence.
 *
 * Rather than encode German morphology — an endings table has to be maintained, and gets it wrong
 * on the cases that matter most — this scans the word's own prefixes from longest to shortest and
 * takes the first that occurs. "sagen" fails, "sage" fails, "sag" matches inside "gesagt".
 * "gehen" gives "geh" inside "gegangen". A noun usually matches outright on the first try, because
 * examples inflect verbs far more than they decline nouns.
 *
 * The floor of [MIN_STEM] characters stops this degrading into a highlight on every "a"-containing
 * word in the sentence, and each prefix is matched case-insensitively as a plain substring because
 * the fragment is a fragment by construction and a word-boundary test would reject the exact case
 * this exists for.
 */
private fun String.markStem(word: String, color: Color): AnnotatedString {
    val trimmed = word.trim()
    if (trimmed.length < MIN_STEM) return markWord(trimmed, color)
    for (length in trimmed.length downTo MIN_STEM) {
        val prefix = trimmed.take(length)
        if (contains(prefix, ignoreCase = true)) {
            return mark(Regex(Regex.escape(prefix), RegexOption.IGNORE_CASE), color)
        }
    }
    return AnnotatedString(this)
}

/** Below this, a prefix stops being specific enough to be worth highlighting. */
private const val MIN_STEM = 4

/** Wraps every match of [pattern] in an underline, leaving the rest of the text untouched. */
private fun String.mark(pattern: Regex, color: Color): AnnotatedString {
    val matches = pattern.findAll(this).toList()
    if (matches.isEmpty()) return AnnotatedString(this)
    return buildAnnotatedString {
        var cursor = 0
        for (m in matches) {
            append(this@mark.substring(cursor, m.range.first))
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = color)) {
                append(m.value)
            }
            cursor = m.range.last + 1
        }
        append(this@mark.substring(cursor))
    }
}

private val CARD_MAX_WIDTH = 320.dp

/** Of the window. See the use site. */
private const val CARD_MAX_HEIGHT_FRACTION = 0.6f
private val CARD_MAX_HEIGHT = 560.dp
private val CARD_MIN_HEIGHT = 300.dp
private val CARD_PADDING = Space.lg
private val CARD_GAP = Space.sm

/** Glosses shown at once. A word with more senses than this gets a "Show all". */
private const val MAX_VISIBLE_SENSES = 4

/** Worked examples shown at once. Kaikki sends at most two per sense to begin with. */
private const val MAX_VISIBLE_EXAMPLES = 2
