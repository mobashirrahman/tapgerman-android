package com.tapgerman.reader.util

/**
 * Ports of the exact string transforms used by the TapGerman Chrome extension
 * (`extension/src/anki.js`, `extension/src/dictionary.js`).
 *
 * These are deliberately byte-compatible with the JavaScript originals rather than merely
 * "equivalent", because [com.tapgerman.reader.anki.NoteIdentity] must derive the same Anki note
 * identity as the desktop extension: a card saved from the phone and a card saved from the browser
 * have to merge instead of duplicating.
 *
 * That includes whitespace. ECMAScript's `\s` and `trim()` are *not* Java's `Character.isWhitespace`:
 * ES keeps NBSP (U+00A0), figure/narrow spaces (U+2007, U+202F) and the BOM (U+FEFF) in the set and
 * drops U+001C–U+001F; Java does the opposite. Article text is full of `&nbsp;`, so the difference
 * would change card hashes. These helpers implement the ES set.
 */

/**
 * ES `WhiteSpace` plus `LineTerminator`, i.e. exactly what `\s`, `trim()`, `trimStart()`
 * and `trimEnd()` use.
 */
private val ES_SPACE = setOf(
    '\t', '\n', '\u000B', '\u000C', '\r', ' ',
    '\u00A0', '\u1680',
    '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005', '\u2006', '\u2007', '\u2008',
    '\u2009', '\u200A',
    '\u2028', '\u2029', '\u202F', '\u205F', '\u3000', '\uFEFF',
)

fun isEsWhitespace(ch: Char): Boolean = ch in ES_SPACE

/** ES `String.prototype.trim`. */
fun esTrim(value: String): String {
    var start = 0
    var end = value.length
    while (start < end && isEsWhitespace(value[start])) start++
    while (end > start && isEsWhitespace(value[end - 1])) end--
    return value.substring(start, end)
}

/** ES `String.prototype.trimStart`. */
fun esTrimStart(value: String): String {
    var start = 0
    while (start < value.length && isEsWhitespace(value[start])) start++
    return value.substring(start)
}

/** ES `value.replace(/\s+/g, " ")`, i.e. collapse runs of ES whitespace to one ASCII space. */
fun esCollapseWhitespace(value: String): String {
    val out = StringBuilder(value.length)
    var inRun = false
    for (ch in value) {
        if (isEsWhitespace(ch)) {
            if (!inRun) out.append(' ')
            inRun = true
        } else {
            out.append(ch)
            inRun = false
        }
    }
    return out.toString()
}

/** `String.prototype.replaceAll("&","&amp;")...` from anki.js `escapeHtml`. */
fun escapeHtml(value: String?): String {
    val text = value ?: ""
    return buildString(text.length + 8) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#039;")
                else -> append(ch)
            }
        }
    }
}

/**
 * anki.js `escapeRegExp` — the character set is the JS one (`[.*+?^${}()|[\]\\]`), so `/` and `-`
 * are intentionally not escaped. Implemented as a character loop rather than a nested regex so the
 * set cannot be misread (Java, unlike JavaScript, treats `[` as starting a nested character class).
 */
private val REGEX_METACHARACTERS =
    setOf('.', '*', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\')

fun escapeRegExp(value: String): String = buildString(value.length * 2) {
    for (ch in value) {
        if (ch in REGEX_METACHARACTERS) append('\\')
        append(ch)
    }
}

/**
 * `JSON.stringify` for a plain string. Needed because `buildAnkiStableId` hashes
 * `JSON.stringify([...])`, so an escaping mismatch would change every card ID.
 */
fun escapeJsonString(value: String): String {
    val out = StringBuilder(value.length + 8)
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        val code = ch.code
        when {
            ch == '"' -> out.append("\\\"")
            ch == '\\' -> out.append("\\\\")
            ch == '\b' -> out.append("\\b")
            ch == '\t' -> out.append("\\t")
            ch == '\n' -> out.append("\\n")
            ch == '\u000C' -> out.append("\\f")
            ch == '\r' -> out.append("\\r")
            code < 0x20 -> out.append("\\u%04x".format(code))
            code in 0xD800..0xDBFF -> {
                // Well-formed JSON.stringify escapes unpaired surrogates and keeps paired ones raw.
                val next = value.getOrNull(index + 1)
                if (next != null && next.code in 0xDC00..0xDFFF) {
                    out.append(ch).append(next)
                    index++
                } else {
                    out.append("\\u%04x".format(code))
                }
            }
            code in 0xDC00..0xDFFF -> out.append("\\u%04x".format(code))
            else -> out.append(ch)
        }
        index++
    }
    return out.toString()
}

/** `JSON.stringify` for a flat array of strings. */
fun jsonArrayOfStrings(values: List<String>): String =
    values.joinToString(",", prefix = "[", postfix = "]") { "\"${escapeJsonString(it)}\"" }

/**
 * `encodeURIComponent`. Hand-rolled because `java.net.URLEncoder.encode` is
 * `application/x-www-form-urlencoded` (spaces become `+`, `~` gets encoded) and would produce
 * different Kaikki URLs than the extension.
 */
fun encodeUriComponent(value: String): String {
    val out = StringBuilder(value.length + 8)
    for (byte in value.encodeToByteArray()) {
        val unsigned = byte.toInt() and 0xFF
        val ch = unsigned.toChar()
        val safe = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
            ch == '-' || ch == '_' || ch == '.' || ch == '!' || ch == '~' ||
            ch == '*' || ch == '\'' || ch == '(' || ch == ')'
        if (safe) {
            out.append(ch)
        } else {
            out.append('%')
            out.append("%02X".format(unsigned))
        }
    }
    return out.toString()
}
