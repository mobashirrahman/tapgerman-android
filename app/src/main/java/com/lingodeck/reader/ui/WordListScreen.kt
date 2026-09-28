package com.lingodeck.reader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lingodeck.reader.data.VocabItem

/**
 * The saved-word shelf: every word the reader explicitly kept, with the sentence it came from and
 * the article it came from. Deleting is explicit; the Anki export is a single action.
 */
@Composable
fun WordListScreen(
    items: List<VocabItem>,
    onDelete: (String) -> Unit,
    onExport: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Saved words",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (items.isNotEmpty()) {
                OutlinedButton(onClick = onExport) {
                    Text("Export TSV")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text(
                text = "No words yet. Open an article and tap a word, then choose Save.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Attribution()
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                WordCard(item = item, onDelete = { onDelete(item.id) })
            }
            item(key = "attribution") { Attribution() }
        }
    }
}

/**
 * Dictionary attribution lives here rather than on every lookup card.
 *
 * Kaikki's English Wiktionary extraction is CC BY-SA 4.0, which requires attribution — but putting
 * the licence line on each of a hundred lookups buries the reading in provenance chrome. One
 * persistent credit outside the reading flow satisfies the licence and keeps the lookup card to
 * what helps comprehension.
 */
@Composable
private fun Attribution() {
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Definitions: Kaikki / English Wiktionary, CC BY-SA 4.0",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WordCard(item: VocabItem, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDelete) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }

            if (item.lemma.isNotBlank() && !item.lemma.equals(item.word, ignoreCase = true)) {
                Text(
                    text = item.lemma,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val glosses = item.glosses.ifEmpty { listOf(item.meaning) }.filter { it.isNotBlank() }
            glosses.take(4).forEach { gloss ->
                Text("• $gloss", style = MaterialTheme.typography.bodyMedium)
            }

            if (item.sentence.isNotBlank()) {
                Text(
                    text = item.sentence,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = item.articleTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
