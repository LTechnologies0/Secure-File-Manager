package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import ltechnologies.onionphone.securefilemanager.extensions.hiddenPath
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import java.io.File

object TrashManager {
    const val TRASH_DIR = ".sfm_trash"
    private const val ORIGIN_SUFFIX = ".origin"
    private const val TTL_MS = 30L * 24 * 60 * 60 * 1000

    data class TrashedItem(
        val trashPath: String,
        val originalPath: String,
        val name: String,
        val deletedAt: Long,
        val isDirectory: Boolean,
    )

    fun trashDir(context: Context): File =
        File(context.hiddenPath, TRASH_DIR).apply { mkdirs() }

    fun isTrashPath(context: Context, path: String): Boolean =
        path.startsWith(trashDir(context).absolutePath)

    fun moveToTrash(context: Context, source: File): Boolean {
        if (!source.exists()) {
            return true
        }
        val dir = trashDir(context)
        val stamp = System.currentTimeMillis()
        val dest = uniqueTrashFile(dir, stamp, source.name)
        val moved = source.renameTo(dest) || copyAndDelete(source, dest)
        if (!moved) {
            return false
        }
        File("${dest.absolutePath}$ORIGIN_SUFFIX").writeText(source.absolutePath)
        return true
    }

    fun purgeExpired(context: Context) {
        val cutoff = System.currentTimeMillis() - TTL_MS
        trashDir(context).listFiles()?.forEach { entry ->
            if (entry.name.endsWith(ORIGIN_SUFFIX)) {
                return@forEach
            }
            if (entry.lastModified() < cutoff) {
                deleteTrashedEntry(entry)
            }
        }
    }

    fun listTrashed(context: Context): List<TrashedItem> {
        val dir = trashDir(context)
        return dir.listFiles()
            ?.filter { !it.name.endsWith(ORIGIN_SUFFIX) }
            ?.mapNotNull { entry ->
                val originFile = File("${entry.absolutePath}$ORIGIN_SUFFIX")
                val originalPath = originFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
                TrashedItem(
                    trashPath = entry.absolutePath,
                    originalPath = originalPath,
                    name = entry.name,
                    deletedAt = entry.lastModified(),
                    isDirectory = entry.isDirectory,
                )
            }
            ?.sortedByDescending { it.deletedAt }
            ?: emptyList()
    }

    fun restore(context: Context, item: TrashedItem): Boolean {
        val trashFile = File(item.trashPath)
        if (!trashFile.exists()) {
            return false
        }
        val targetPath = item.originalPath.ifEmpty {
            File(context.hiddenPath, trashFile.name).absolutePath
        }
        val target = File(targetPath)
        target.parentFile?.mkdirs()
        if (target.exists()) {
            return false
        }
        val ok = trashFile.renameTo(target)
        if (ok) {
            File("${trashFile.absolutePath}$ORIGIN_SUFFIX").delete()
        }
        return ok
    }

    fun deletePermanently(item: TrashedItem): Boolean {
        val trashFile = File(item.trashPath)
        val ok = if (trashFile.isDirectory) {
            trashFile.deleteRecursively()
        } else {
            trashFile.delete()
        }
        File("${trashFile.absolutePath}$ORIGIN_SUFFIX").delete()
        return ok
    }

    fun toFileDirItem(item: TrashedItem): FileDirItem =
        FileDirItem(
            path = item.trashPath,
            name = item.name,
            isDirectory = item.isDirectory,
            children = 0,
            size = File(item.trashPath).length(),
            modified = item.deletedAt,
        )

    private fun uniqueTrashFile(dir: File, stamp: Long, name: String): File {
        var candidate = File(dir, "${stamp}_$name")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stamp}_${index}_$name")
            index++
        }
        return candidate
    }

    private fun copyAndDelete(source: File, dest: File): Boolean {
        return try {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            source.deleteRecursively()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteTrashedEntry(entry: File) {
        if (entry.isDirectory) {
            entry.deleteRecursively()
        } else {
            entry.delete()
        }
        File("${entry.absolutePath}$ORIGIN_SUFFIX").delete()
    }
}
