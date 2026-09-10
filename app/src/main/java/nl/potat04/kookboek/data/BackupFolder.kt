package nl.potat04.kookboek.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * The folder the reader pointed the system picker at, reached through the Storage
 * Access Framework.
 *
 * No SDK and no account code on purpose: the picker already offers Google Drive,
 * Nextcloud, a USB stick and whatever else is installed, and a folder that the phone
 * itself syncs to a cloud is exactly the case Anton asked for. All we do is write a
 * file into a tree we were granted.
 *
 * [DocumentsContract] straight rather than the `documentfile` library: it is four
 * queries, and this is the only place in the app that speaks SAF.
 */
object BackupFolder {

    private const val TAG = "BackupFolder"
    private const val MIME_ZIP = "application/zip"

    /** The MIME types the file picker offers when restoring. Providers disagree about zips. */
    val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

    val PERSIST_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * The folder's own name, for the settings row, or null when we cannot reach it any
     * more — the reader revoked the grant, or the card it lived on is out.
     */
    fun displayName(context: Context, tree: Uri): String? {
        if (!hasPermission(context, tree)) return null
        return runCatching {
            context.contentResolver.query(
                documentUri(tree),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    fun hasPermission(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions
            .any { it.uri == tree && it.isWritePermission && it.isReadPermission }

    /**
     * Writes [name] into the tree, replacing a file of that name if today's run already
     * left one. Deleting first matters: providers do not overwrite, they hand you
     * "kookboek-2026-09-10 (1).zip", and a week of those is not a week of backups.
     */
    suspend fun write(
        context: Context,
        tree: Uri,
        name: String,
        body: suspend (OutputStream) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            children(context, tree)
                .filter { (childName, _) -> childName == name }
                .forEach { (_, uri) -> DocumentsContract.deleteDocument(resolver, uri) }

            val target = DocumentsContract.createDocument(resolver, documentUri(tree), MIME_ZIP, name)
                ?: error("the folder would not take a new file")
            val stream = resolver.openOutputStream(target, "w")
                ?: error("no output stream for $name")
            // Closed here as well as by whoever writes into it: an unclosed document
            // uri leaves a zero-byte file behind that looks like a backup and is not.
            stream.use { body(it) }
            true
        }.onFailure { Log.w(TAG, "could not write $name", it) }.getOrDefault(false)
    }

    /** Drops our own daily files beyond the newest [keep]. Anything else in there is not ours. */
    suspend fun prune(context: Context, tree: Uri, keep: Int = Backup.KEEP) = withContext(Dispatchers.IO) {
        runCatching {
            val all = children(context, tree)
            val doomed = Backup.stale(all.map { it.first }, keep).toSet()
            all.filter { (name, _) -> name in doomed }
                .forEach { (_, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }.onFailure { Log.w(TAG, "could not tidy the backup folder", it) }
        Unit
    }

    private fun documentUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** Name and document uri of everything directly in the tree. */
    private fun children(context: Context, tree: Uri): List<Pair<String, Uri>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val out = mutableListOf<Pair<String, Uri>>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                out += name to DocumentsContract.buildDocumentUriUsingTree(tree, id)
            }
        }
        return out
    }
}
