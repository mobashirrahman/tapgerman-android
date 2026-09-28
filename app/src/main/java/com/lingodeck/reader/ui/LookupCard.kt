package com.lingodeck.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lingodeck.reader.data.LookupCardBuilder

/**
 * The compact card that appears next to a tapped word.
 *
 * Deliberately a small [Popup] rather than a modal sheet: no scrim, no dimming, no takeover. The
 * article stays readable behind it and the reader is still in the text. Three things drive the
 * shape:
 *
 * - **Actions are pinned at the top.** Pronunciation, Save and Send to Anki never scroll away, so
 *   the common case never requires scrolling at all.
 * - **Bounded size.** ~320dp square at most; only the gloss list may scroll if a word has many
 *   senses, and the visible list is capped at four.
 * - **No provenance chrome.** Dictionary licensing is attributed once on the Words screen rather
 *   than on every lookup, so the card holds only what helps reading.
 */
@Composable
fun LookupCard(
    lookup: LookupUi,
    anchor: IntOffset,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSendToAnki: () -> Unit,
    onChooseGloss: (String) -> Unit,
    onSpeak: () -> Unit,
) {
    // `Dp.value` is dp, not pixels, so the gap has to go through the density to reach
    // [AnchoredPositionProvider], which works in raw pixels like the rest of PopupPositionProvider.
    val gapPx = with(LocalDensity.current) { CARD_GAP.roundToPx() }

    Popup(
        popupPositionProvider = AnchoredPositionProvider(anchor, gapPx = gapPx),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true, dismissOnBackPress = true),
    ) {
        ElevatedCard(
            modifier = Modifier
                .widthIn(max = CARD_MAX_WIDTH)
                .heightIn(max = CARD_MAX_HEIGHT),
        ) {
            Column(modifier = Modifier.padding(CARD_PADDING)) {
                // ---- pinned: title bar + actions, never scrolled ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = lookup.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onSpeak, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Pronounce")
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onSave,
                        enabled = !lookup.saved,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Text(
                            text = if (lookup.saved) "Saved" else "Save word",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Button(
                        onClick = onSendToAnki,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Text(
                            text = "Send to Anki",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                lookup.savedMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (lookup.loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text("Looking up…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                lookup.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // ---- the only part allowed to scroll: the sense list ----
                // `fill = false` matters: with `fill = true` this child is handed every pixel the
                // pinned rows left over and the card grows empty space below its content. Here it
                // takes only what it needs, up to the cap, so the card shrink-wraps short glosses.
                //
                // The sentence sits *above* this, pinned: it is the reason the card exists — which
                // sense of a word this particular article meant — and it must not be scrolled away
                // behind the picker.
                val firstEntry = lookup.result?.let { LookupCardBuilder.displayEntries(it).firstOrNull() }
                val meta = listOfNotNull(
                    lookup.result?.lemma?.takeIf { it.isNotBlank() && !it.equals(lookup.word, true) },
                    firstEntry?.partOfSpeech?.takeIf { it.isNotBlank() },
                    firstEntry?.ipa?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (lookup.sentence.isNotBlank()) {
                    Text(
                        text = lookup.sentence,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (lookup.glosses.size > 1) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Which meaning?",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        lookup.glosses.forEach { gloss ->
                            SenseRow(
                                gloss = gloss,
                                selected = lookup.chosenGloss == gloss,
                                onSelect = { onChooseGloss(gloss) },
                            )
                        }
                    }
                } else if (lookup.glosses.size == 1) {
                    // One sense is not a choice, so it is shown as the definition rather than as a
                    // picker. The saved card still carries this gloss.
                    Text(
                        text = lookup.glosses[0],
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SenseRow(gloss: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = gloss,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val CARD_MAX_WIDTH = 288.dp
private val CARD_MAX_HEIGHT = 300.dp
private val CARD_PADDING = 12.dp
private val CARD_GAP = 8.dp
