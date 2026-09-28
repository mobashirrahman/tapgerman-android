package com.lingodeck.reader.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingodeck.reader.data.Article
import com.lingodeck.reader.text.GermanTokenizer

private const val WORD_TAG = "word"

/**
 * The article reader: German paragraphs where every word is a tap target.
 *
 * Words are annotated as string ranges rather than as one link per word, so a long article costs
 * one layout pass per paragraph instead of one click handler per token.
 */
@Composable
fun ReaderScreen(
    article: Article,
    onTapWord: (paragraphIndex: Int, start: Int, end: Int, anchor: Offset) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (article.byline.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = article.byline,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = article.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap any word for its meaning.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        itemsIndexed(article.paragraphs) { index, paragraph ->
            TappableParagraph(
                text = paragraph,
                onClick = { start, end, anchor -> onTapWord(index, start, end, anchor) },
            )
        }

        item(key = "footer") {
            Spacer(Modifier.height(8.dp))
            Text(
                text = article.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renders one paragraph with every word as a tap target.
 *
 * The click reports the tap position in *window* coordinates so the lookup card can open beside
 * the word rather than as a detached panel: the paragraph's own position in the window is sampled
 * via `onGloballyPositioned` and the local tap offset is added to it.
 */
@Composable
private fun TappableParagraph(text: String, onClick: (Int, Int, Offset) -> Unit) {
    val spans = remember(text) { GermanTokenizer.words(text) }
    val annotated = remember(text, spans) {
        buildAnnotatedString {
            var cursor = 0
            for (span in spans) {
                if (span.start > cursor) append(text.substring(cursor, span.start))
                pushStringAnnotation(WORD_TAG, "${span.start}:${span.end}")
                append(span.text)
                pop()
                cursor = span.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val originInWindow = remember { mutableStateOf(Offset.Zero) }

    BasicText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 28.sp,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        onTextLayout = { layout.value = it },
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates -> originInWindow.value = coordinates.positionInWindow() }
            .pointerInput(text, spans) {
                // Named `onTap`, not the trailing lambda: `detectTapGestures`' last parameter is
                // `onPress`, which would fire on the finger landing mid-scroll.
                detectTapGestures(onTap = { offset ->
                    val result = layout.value ?: return@detectTapGestures
                    val position = result.getOffsetForPosition(offset)
                    annotated.getStringAnnotations(WORD_TAG, position, position)
                        .firstOrNull()
                        ?.let { annotation ->
                            val parts = annotation.item.split(":")
                            onClick(parts[0].toInt(), parts[1].toInt(), originInWindow.value + offset)
                        }
                })
            },
    )
}
