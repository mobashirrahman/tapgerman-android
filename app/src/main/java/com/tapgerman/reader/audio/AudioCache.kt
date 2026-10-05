package com.tapgerman.reader.audio

import java.io.File

/**
 * Pronunciation recordings on disk, bounded by bytes and by count.
 *
 * Wiktionary's audio is real speech by native speakers, and it is already in every payload the
 * dictionary fetch returns — `DictEntry.audioUrl` has been parsed and stored on every saved word
 * since the first version and has never once been played. This is the part that makes it usable
 * without spending the reader's data on every tap.
 *
 * Bounded on both axes because they fail differently. Bytes stop a few long words from filling the
 * device; count stops hundreds of tiny clips from making eviction walk the whole directory on every
 * write. A word is around 20 KB, so the defaults hold a few hundred words.
 *
 * Recency lives in the filename, not in the modification time. Filesystems timestamp to a second
 * or a millisecond, and a reader tapping through words in a row writes several inside one tick, so
 * ordering by mtime quietly degrades into "evict something arbitrary" — and the word most worth
 * keeping is the one heard yesterday. The sequence is a fixed-width hex prefix, which makes
 * sorting by name the same as sorting by age.
 *
 * No Android types, so the eviction arithmetic — which is where this kind of class actually goes
 * wrong — is unit tested directly.
 */
class AudioCache(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
) {

    init {
        directory.mkdirs()
    }

    /** Every stored recording, oldest first. */
    fun entries(): List<File> =
        directory.listFiles()
            ?.filter { it.isFile && nameParts(it.name) != null }
            ?.sortedBy { it.name }
            .orEmpty()

    /** The stored file for [url], newest first, or null. */
    fun fileFor(url: String): File? {
        if (url.isBlank()) return null
        val key = keyOf(url)
        return directory.listFiles()
            ?.filter { it.isFile && keyOfFile(it.name) == key }
            ?.sortedByDescending { it.name }
            ?.firstOrNull()
    }

    /** The cached recording for [url] worth playing, or null when there is nothing usable. */
    fun get(url: String): File? {
        val file = fileFor(url) ?: return null
        // Length is the check: a download cut short leaves a file that exists and is empty, and
        // playing that would be a speaker button that is confidently silent.
        return file.takeIf { it.length() > 0 }
    }

    /** Writes [bytes] for [url], replacing any previous copy. */
    fun put(url: String, bytes: ByteArray) {
        if (url.isBlank() || bytes.isEmpty()) return
        // Refuse anything implausible for a spoken word before it reaches the filesystem.
        if (bytes.size > MAX_FILE_BYTES) return
        fileFor(url)?.let { runCatching { it.delete() } }
        runCatching { File(directory, nextName(url)).writeBytes(bytes) }
    }

    /**
     * Marks [file] as just-used, so eviction takes the least recently played first.
     *
     * A rename rather than a timestamp, for the reason in the class comment. Silent on failure:
     * the worst outcome is that the file keeps its age and is evicted a little early.
     */
    fun touch(file: File) {
        val key = keyOfFile(file.name) ?: return
        runCatching {
            if (!file.renameTo(File(directory, nextName(key, extensionOfFile(file.name))))) {
                file.setLastModified(System.currentTimeMillis())
            }
        }
    }

    /** Total bytes held. */
    fun sizeBytes(): Long = entries().sumOf { it.length() }

    /** How many recordings are held. */
    fun count(): Int = entries().size

    fun clear() {
        entries().forEach { runCatching { it.delete() } }
    }

    /** Drops the least recently used recordings until both bounds hold. */
    fun evict() {
        val held = entries()
        var total = held.sumOf { it.length() }
        for (index in held.indices) {
            // `held.size - index` is how many recordings remain including this one.
            if (total <= maxBytes && held.size - index <= maxFiles) break
            total -= held[index].length()
            runCatching { held[index].delete() }
        }
    }

    /**
     * Identifies which recording a url is, independent of when it was last used.
     *
     * The query and fragment are dropped: a recording reachable at two urls differing only by a
     * cache-busting parameter is one recording, and storing it twice wastes the space the bounds
     * exist to protect.
     */
    private fun keyOf(url: String): String {
        val stable = url.substringBefore('?').substringBefore('#')
        return stable.hashCode().toUInt().toString(16) + "-" + stable.length.toString(16)
    }

    /** A name that sorts after everything already held. */
    private fun nextName(url: String): String = nextName(keyOf(url), extensionOf(url))

    private fun nextName(key: String, extension: String): String {
        val highest = entries().lastOrNull()?.let { nameParts(it.name)?.first } ?: 0L
        val sequence = "%08x".format(highest + 1)
        return "$sequence-$key" + if (extension.isEmpty()) "" else ".$extension"
    }

    /** The recency sequence of a stored name, or null when it is not one of ours. */
    private fun nameParts(name: String): Pair<Long, String>? {
        val dash = name.indexOf('-')
        if (dash <= 0) return null
        val sequence = name.substring(0, dash).toLongOrNull(16) ?: return null
        return sequence to name.substring(dash + 1)
    }

    private fun keyOfFile(name: String): String? = nameParts(name)?.second?.substringBefore('.')

    private fun extensionOfFile(name: String): String {
        val rest = nameParts(name)?.second ?: return ""
        val dot = rest.lastIndexOf('.')
        return if (dot < 0) "" else rest.substring(dot + 1)
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase().takeIf { it in KNOWN_EXTENSIONS }.orEmpty()
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 16L * 1024 * 1024
        const val DEFAULT_MAX_FILES = 400

        /** A spoken word is tens of KB. Anything past this is not a word recording. */
        const val MAX_FILE_BYTES = 2 * 1024 * 1024

        private val KNOWN_EXTENSIONS = setOf("mp3", "ogg", "oga", "m4a", "aac", "wav", "flac", "opus")
    }
}
