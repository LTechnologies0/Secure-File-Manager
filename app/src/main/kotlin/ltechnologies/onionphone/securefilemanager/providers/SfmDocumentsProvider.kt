package ltechnologies.onionphone.securefilemanager.providers

import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.hiddenPath
import java.io.File
import java.io.FileNotFoundException

class SfmDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        if (!isEnabled()) {
            return MatrixCursor(projection ?: emptyArray())
        }
        val result = MatrixCursor(projection ?: arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
        ))
        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_TITLE, "Secure File Manager")
            add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
            add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_manage)
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        return matrixForFile(resolveFile(documentId), projection)
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val dir = resolveFile(parentDocumentId)
        if (!dir.isDirectory) {
            throw FileNotFoundException("not a directory")
        }
        val result = MatrixCursor(projection ?: defaultProjection())
        dir.listFiles()
            ?.filter { includeFile(it) }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { result.addRow(rowForFile(it)) }
        return result
    }

    override fun openDocument(documentId: String, mode: String, signal: android.os.CancellationSignal?): android.os.ParcelFileDescriptor {
        val file = resolveFile(documentId)
        if (!file.isFile) {
            throw FileNotFoundException("not a file")
        }
        val access = when (mode) {
            "w", "wt" -> android.os.ParcelFileDescriptor.MODE_WRITE_ONLY or android.os.ParcelFileDescriptor.MODE_CREATE or android.os.ParcelFileDescriptor.MODE_TRUNCATE
            "rw", "rwt" -> android.os.ParcelFileDescriptor.MODE_READ_WRITE or android.os.ParcelFileDescriptor.MODE_CREATE
            else -> android.os.ParcelFileDescriptor.MODE_READ_ONLY
        }
        return android.os.ParcelFileDescriptor.open(file, access)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = resolveFile(parentDocumentId)
        if (!parent.isDirectory) {
            throw FileNotFoundException("parent missing")
        }
        val target = uniqueFile(parent, displayName)
        if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
            target.mkdirs()
        } else {
            target.createNewFile()
        }
        return toDocumentId(target)
    }

    override fun deleteDocument(documentId: String) {
        val file = resolveFile(documentId)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = resolveFile(documentId)
        val target = File(file.parentFile, displayName)
        if (!file.renameTo(target)) {
            throw FileNotFoundException("rename failed")
        }
        return toDocumentId(target)
    }

    private fun isEnabled(): Boolean = context?.config?.documentsProviderEnabled == true

    private fun resolveFile(documentId: String): File {
        if (!isEnabled()) {
            throw FileNotFoundException("provider disabled")
        }
        val hidden = context!!.hiddenPath
        if (documentId == ROOT_ID) {
            return File(hidden)
        }
        val relative = documentId.removePrefix("$ROOT_ID/")
        val file = File(hidden, relative)
        if (!file.canonicalPath.startsWith(File(hidden).canonicalPath)) {
            throw FileNotFoundException("outside root")
        }
        if (!file.exists()) {
            throw FileNotFoundException(documentId)
        }
        return file
    }

    private fun includeFile(file: File): Boolean = !file.name.startsWith(".")

    private fun toDocumentId(file: File): String {
        val hidden = context!!.hiddenPath
        val relative = file.canonicalPath.removePrefix(File(hidden).canonicalPath).trimStart('/')
        return if (relative.isEmpty()) ROOT_ID else "$ROOT_ID/$relative"
    }

    private fun matrixForFile(file: File, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: defaultProjection())
        result.addRow(rowForFile(file))
        return result
    }

    private fun rowForFile(file: File): Array<Any?> {
        val mime = if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
                ?: "application/octet-stream"
        }
        return arrayOf(
            toDocumentId(file),
            file.name,
            if (file.isDirectory) 0L else file.length(),
            mime,
            if (file.canWrite()) DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME else 0,
        )
    }

    private fun defaultProjection() = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
    )

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) {
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            candidate = File(dir, "$base ($index)$ext")
            index++
        }
        return candidate
    }

    companion object {
        private const val ROOT_ID = "sfm_hidden_root"

        fun setEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, SfmDocumentsProvider::class.java),
                if (enabled) {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
        }
    }
}
