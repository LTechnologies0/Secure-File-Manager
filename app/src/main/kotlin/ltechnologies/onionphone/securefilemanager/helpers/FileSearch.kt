package ltechnologies.onionphone.securefilemanager.helpers

import android.content.Context
import android.provider.MediaStore
import ltechnologies.onionphone.securefilemanager.extensions.getLongValue
import ltechnologies.onionphone.securefilemanager.extensions.getStringValue
import ltechnologies.onionphone.securefilemanager.extensions.hiddenPath
import ltechnologies.onionphone.securefilemanager.extensions.getFilenameFromPath
import ltechnologies.onionphone.securefilemanager.models.ListItem
import ltechnologies.onionphone.securefilemanager.storage.TrashManager
import java.io.File

object FileSearch {
    const val MAX_RESULTS = 500

    fun searchGlobal(context: Context, query: String): ArrayList<ListItem> {
        val results = ArrayList<ListItem>()
        if (query.length < 2) {
            return results
        }
        searchMediaStore(context, query, results)
        if (results.size < MAX_RESULTS) {
            walkHidden(context, query, results)
        }
        return results
    }

    private fun searchMediaStore(context: Context, query: String, results: ArrayList<ListItem>) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'"
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val args = arrayOf("%$escaped%")
        val uri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            while (cursor.moveToNext() && results.size < MAX_RESULTS) {
                val path = cursor.getStringValue(MediaStore.Files.FileColumns.DATA) ?: continue
                if (!File(path).isFile) {
                    continue
                }
                val name = cursor.getStringValue(MediaStore.Files.FileColumns.DISPLAY_NAME) ?: path.getFilenameFromPath()
                val size = cursor.getLongValue(MediaStore.Files.FileColumns.SIZE)
                val modified = cursor.getLongValue(MediaStore.Files.FileColumns.DATE_MODIFIED) * 1000
                results.add(ListItem(path, name, false, 0, size, modified, false))
            }
        }
    }

    private fun walkHidden(context: Context, query: String, results: ArrayList<ListItem>) {
        val root = File(context.hiddenPath)
        if (!root.exists()) {
            return
        }
        val trashPrefix = TrashManager.trashDir(context).absolutePath
        walkDir(root, query, results, trashPrefix)
    }

    private fun walkDir(dir: File, query: String, results: ArrayList<ListItem>, trashPrefix: String) {
        if (results.size >= MAX_RESULTS) {
            return
        }
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (results.size >= MAX_RESULTS) {
                return
            }
            if (file.absolutePath.startsWith(trashPrefix)) {
                continue
            }
            if (file.name.contains(query, ignoreCase = true)) {
                results.add(
                    ListItem(
                        file.absolutePath,
                        file.name,
                        file.isDirectory,
                        if (file.isDirectory) file.listFiles()?.size ?: 0 else 0,
                        if (file.isDirectory) 0L else file.length(),
                        file.lastModified(),
                        false,
                    ),
                )
            }
            if (file.isDirectory) {
                walkDir(file, query, results, trashPrefix)
            }
        }
    }
}
