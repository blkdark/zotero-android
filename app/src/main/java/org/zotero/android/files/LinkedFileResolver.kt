package org.zotero.android.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.zotero.android.architecture.Defaults
import org.zotero.android.database.objects.Attachment
import org.zotero.android.sync.LibraryIdentifier
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkedFileResolver @Inject constructor(
    private val context: Context,
    private val defaults: Defaults,
    private val fileStore: FileStore,
) {

    fun isBaseDirectoryConfigured(): Boolean {
        val baseDirPath = defaults.getLinkedAttachmentBaseDirectory()
        val baseDirUri = defaults.getLinkedAttachmentBaseDirectoryUri()
        return !baseDirPath.isNullOrBlank() || !baseDirUri.isNullOrBlank()
    }

    fun getBaseDirectoryDisplay(): String? {
        val path = defaults.getLinkedAttachmentBaseDirectory()
        if (!path.isNullOrBlank()) {
            return path
        }
        val uriStr = defaults.getLinkedAttachmentBaseDirectoryUri()
        if (!uriStr.isNullOrBlank()) {
            return try {
                val uri = Uri.parse(uriStr)
                getPathFromTreeUri(uri) ?: uri.lastPathSegment ?: uriStr
            } catch (e: Exception) {
                uriStr
            }
        }
        return null
    }

    private val prefs by lazy { context.getSharedPreferences("ZoteroPrefs", Context.MODE_PRIVATE) }
    private val fileToContentUri = java.util.concurrent.ConcurrentHashMap<String, Uri>()

    fun registerContentUri(file: File, uri: Uri) {
        fileToContentUri[file.absolutePath] = uri
        prefs.edit().putString("linked_doc_uri_${file.name}", uri.toString()).apply()
    }

    fun getContentUri(file: File): Uri? {
        fileToContentUri[file.absolutePath]?.let { return it }
        val saved = prefs.getString("linked_doc_uri_${file.name}", null)
        if (saved != null) {
            val parsed = Uri.parse(saved)
            fileToContentUri[file.absolutePath] = parsed
            return parsed
        }
        return null
    }

    fun resolve(
        attachment: Attachment.Kind.file,
        libraryId: LibraryIdentifier? = null,
        key: String? = null,
    ): File? {
        val baseDirPath = defaults.getLinkedAttachmentBaseDirectory()
        val baseDirUri = defaults.getLinkedAttachmentBaseDirectoryUri()

        if (baseDirPath.isNullOrBlank() && baseDirUri.isNullOrBlank()) {
            Timber.w("LinkedFileResolver: Base directory is not configured")
            return null
        }

        // Fast path: if cache file exists and its content URI is already known, return immediately (0 ms)
        if (libraryId != null && key != null) {
            val cacheFile = fileStore.attachmentFile(libraryId, key, attachment.filename)
            if (cacheFile.exists() && cacheFile.length() > 0L && getContentUri(cacheFile) != null) {
                Timber.i("LinkedFileResolver: Fast-path return cached file at ${cacheFile.absolutePath}")
                return cacheFile
            }
        }

        val rawPath = attachment.path ?: ""
        val candidates = mutableListOf<String>()

        if (rawPath.startsWith("attachments:", ignoreCase = true)) {
            val relative = rawPath.substringAfter(":").replace('\\', '/').trimStart('/')
            if (relative.isNotBlank()) {
                candidates.add(relative)
            }
        } else if (rawPath.isNotBlank()) {
            val normalized = rawPath.replace('\\', '/')
            val filenameFromPath = File(normalized).name
            if (filenameFromPath.isNotBlank()) {
                candidates.add(filenameFromPath)
            }
        }

        if (attachment.filename.isNotBlank() && !candidates.contains(attachment.filename)) {
            candidates.add(attachment.filename)
        }

        // 1. Try direct filesystem access if baseDirPath is specified
        if (!baseDirPath.isNullOrBlank()) {
            val baseDir = File(baseDirPath)
            if (baseDir.exists() && baseDir.isDirectory) {
                // Direct relative candidate checks
                for (candidate in candidates) {
                    val directFile = File(baseDir, candidate)
                    if (directFile.exists() && directFile.isFile && directFile.canRead()) {
                        Timber.i("LinkedFileResolver: Resolved direct file at ${directFile.absolutePath}")
                        return directFile
                    }
                }

                // Subdirectory search by filename (up to 5 levels)
                try {
                    val found = baseDir.walkTopDown()
                        .maxDepth(5)
                        .firstOrNull { it.isFile && it.name.equals(attachment.filename, ignoreCase = true) && it.canRead() }
                    if (found != null) {
                        Timber.i("LinkedFileResolver: Resolved by recursive search at ${found.absolutePath}")
                        return found
                    }
                } catch (e: Exception) {
                    Timber.w(e, "LinkedFileResolver: Error traversing base directory")
                }
            }
        }

        // 2. Try Storage Access Framework (SAF) DocumentFile if baseDirUri is configured
        if (!baseDirUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(baseDirUri)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.exists()) {
                    val targetDoc = findDocumentFile(rootDoc, candidates, attachment.filename)
                    if (targetDoc != null && targetDoc.exists()) {
                        val cacheFile = if (libraryId != null && key != null) {
                            fileStore.attachmentFile(libraryId, key, attachment.filename)
                        } else {
                            File(context.cacheDir, "linked_files/${attachment.filename}").apply {
                                parentFile?.mkdirs()
                            }
                        }

                        // Register SAF Document URI for direct editing by external viewers
                        registerContentUri(cacheFile, targetDoc.uri)

                        val docModified = targetDoc.lastModified()
                        val cacheModified = if (cacheFile.exists()) cacheFile.lastModified() else 0L

                        if (!cacheFile.exists() || cacheFile.length() == 0L || (docModified > 0 && docModified > cacheModified)) {
                            context.contentResolver.openInputStream(targetDoc.uri)?.use { input ->
                                cacheFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (docModified > 0) {
                                cacheFile.setLastModified(docModified)
                            }
                            Timber.i("LinkedFileResolver: Synced from SAF to cache at ${cacheFile.absolutePath}")
                        } else if (cacheFile.exists() && cacheFile.length() > 0 && cacheModified > docModified && targetDoc.canWrite()) {
                            try {
                                cacheFile.inputStream().use { input ->
                                    context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Timber.i("LinkedFileResolver: Synced back from cache to SAF at ${targetDoc.uri}")
                            } catch (e: Exception) {
                                Timber.w(e, "LinkedFileResolver: Could not sync cache back to SAF")
                            }
                        }

                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            Timber.i("LinkedFileResolver: Resolved and cached via SAF at ${cacheFile.absolutePath}")
                            return cacheFile
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "LinkedFileResolver: Error resolving linked file via SAF")
            }
        }

        // 3. Fallback to existing cache if available
        if (libraryId != null && key != null) {
            val cachedFile = fileStore.attachmentFile(libraryId, key, attachment.filename)
            if (cachedFile.exists() && cachedFile.isFile && cachedFile.length() > 0) {
                Timber.i("LinkedFileResolver: Returning already cached file as fallback: ${cachedFile.absolutePath}")
                return cachedFile
            }
        }

        Timber.w("LinkedFileResolver: Could not find linked file: ${attachment.filename}")
        return null
    }

    private fun findDocumentFile(
        root: DocumentFile,
        candidates: List<String>,
        filename: String,
    ): DocumentFile? {
        val rootFiles = root.listFiles()

        // 1. Direct match by filename or simple candidate names in root folder (instant)
        val simpleNames = candidates.map { File(it.replace('\\', '/')).name }.filter { it.isNotBlank() }
        val rootMatch = rootFiles.firstOrNull { doc ->
            doc.isFile && (doc.name.equals(filename, ignoreCase = true) || simpleNames.any { it.equals(doc.name, ignoreCase = true) })
        }
        if (rootMatch != null) {
            return rootMatch
        }

        // 2. Relative paths with subfolders
        for (candidate in candidates) {
            if (candidate.contains('/') || candidate.contains('\\')) {
                val parts = candidate.replace('\\', '/').split('/').filter { it.isNotBlank() }
                var current: DocumentFile? = root
                for (part in parts) {
                    current = current?.findFile(part)
                    if (current == null) break
                }
                if (current != null && current.isFile) {
                    return current
                }
            }
        }

        return null
    }

    companion object {
        fun getPathFromTreeUri(uri: Uri): String? {
            return try {
                val documentId = DocumentsContract.getTreeDocumentId(uri) ?: return null
                val parts = documentId.split(":")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val relativePath = parts[1]
                    if ("primary".equals(type, ignoreCase = true)) {
                        "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                    } else {
                        "/storage/$type/$relativePath"
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
