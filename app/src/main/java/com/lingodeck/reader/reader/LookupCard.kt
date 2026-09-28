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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
@Composable
private fun LookupCard(
    lookup: LookupUi,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSendToAnki: () -> Unit,
    onChooseGloss: (String) -> Unit,
    onSpeak: () -> Unit,
    onToggleAllSenses: () -> Unit,
    isShowingAllSenses: Boolean,
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

    ElevatedCard(
        modifier = Modifier
            .widthIn(max = CARD_MAX_WIDTH)
            .heightIn(max = CARD_MAX_HEIGHT)
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
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
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
                    visible.forEach { gloss ->
                        SenseRow(
                            gloss = gloss,
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
                if (lookup.sentence.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = lookup.sentence,
                            style = LingoTheme.reading.quote,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Space.md),
                        )
                    }
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
private fun SenseRow(gloss: String, selected: Boolean, onSelect: () -> Unit) {
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
            Text(
                text = gloss,
                style = LingoTheme.reading.gloss,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Space.sm),
            )
        }
    }
}

private val CARD_MAX_WIDTH = 320.dp
private val CARD_MAX_HEIGHT = 420.dp
private val CARD_PADDING = Space.lg
private val CARD_GAP = Space.sm

/** Glosses shown at once. A word with more senses than this gets a "Show all". */
private const val MAX_VISIBLE_SENSES = 4
