package com.jonny.keyscope.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

/**
 * Lists and renames audio inside a folder you granted through the tree picker.
 *
 * Uses DocumentsContract directly rather than pulling in the documentfile library — the two calls
 * needed here are not worth a dependency.
 */
object FolderScanner {

    private val AUDIO_EXTENSIONS = setOf(
        "wav", "mp3", "m4a", "aac", "flac", "ogg", "opus", "aiff", "aif", "mp4", "3gp", "amr"
    )

    data class Entry(val uri: Uri, val name: String)

    fun listAudio(context: Context, treeUri: Uri): List<Entry> {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val entries = ArrayList<Entry>()
        runCatching {
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    if (!isAudio(name, mime)) continue
                    entries.add(
                        Entry(DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId), name)
                    )
                }
            }
        }
        return entries.sortedBy { it.name.lowercase(Locale.US) }
    }

    private fun isAudio(name: String, mime: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        return extension in AUDIO_EXTENSIONS
    }

    /**
     * The name a result would get. Key and tempo are appended rather than substituted so nothing
     * about the original name is ever lost, and a file that already carries its key is left alone.
     */
    fun proposedName(current: String, result: FileAnalyzer.Result): String? {
        val key = result.key ?: return null
        val base = current.substringBeforeLast('.', current)
        val extension = current.substringAfterLast('.', "")

        val tag = buildString {
            append(key.shortName)
            if (result.bpm > 0f) append(" ${Math.round(result.bpm)}")
        }
        // Already tagged, by us or by hand. Renaming again would just stack suffixes.
        if (base.endsWith(tag)) return null

        val renamed = "$base $tag"
        return if (extension.isEmpty()) renamed else "$renamed.$extension"
    }

    /** @return the new name on success, or null if the provider refused. */
    fun rename(context: Context, uri: Uri, newName: String): String? = runCatching {
        DocumentsContract.renameDocument(context.contentResolver, uri, newName)?.let { newName }
    }.getOrNull()
}
