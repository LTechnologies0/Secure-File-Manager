package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import ltechnologies.onionphone.securefilemanager.extensions.hiddenPath
import ltechnologies.onionphone.securefilemanager.helpers.crypto.HiddenFileCrypto
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import java.io.File

object TrashManager {
    const val TRASH_DIR = HiddenFileCrypto.TRASH_DIR_NAME
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
        val oldPath = source.absolutePath
        val moved = copyVaultAware(context, source, dest) && deleteSource(source)
        if (!moved) {
            dest.deleteRecursively()
            return false
        }
        writeSidecar(dest, oldPath, stamp)
        return true
    }

    fun purgeExpired(context: Context) {
        val cutoff = System.currentTimeMillis() - TTL_MS
        trashDir(context).listFiles()?.forEach { entry ->
            if (entry.name.endsWith(ORIGIN_SUFFIX)) {
                return@forEach
            }
            val deletedAt = readSidecar(entry)?.deletedAt ?: entry.lastModified()
            if (deletedAt < cutoff) {
                deleteTrashedEntry(entry)
            }
        }
    }

    fun listTrashed(context: Context): List<TrashedItem> {
        val dir = trashDir(context)
        return dir.listFiles()
            ?.filter { !it.name.endsWith(ORIGIN_SUFFIX) }
            ?.mapNotNull { entry ->
                val sidecar = readSidecar(entry)
                TrashedItem(
                    trashPath = entry.absolutePath,
                    originalPath = sidecar?.originalPath.orEmpty(),
                    name = entry.name,
                    deletedAt = sidecar?.deletedAt ?: entry.lastModified(),
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
        val ok = copyVaultAware(context, trashFile, target)
        if (ok) {
            deleteTrashedEntry(trashFile)
        }
        return ok
    }

    fun deletePermanently(item: TrashedItem): Boolean {
        val trashFile = File(item.trashPath)
        deleteTrashedEntry(trashFile)
        return !trashFile.exists()
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

    internal data class Sidecar(val originalPath: String, val deletedAt: Long)

    private fun writeSidecar(dest: File, originalPath: String, deletedAt: Long) {
        File("${dest.absolutePath}$ORIGIN_SUFFIX").writeText(
            "path=$originalPath\ndeletedAt=$deletedAt\n",
        )
    }

    private fun readSidecar(entry: File): Sidecar? {
        val originFile = File("${entry.absolutePath}$ORIGIN_SUFFIX")
        if (!originFile.isFile) {
            return null
        }
        return parseSidecarText(originFile.readText(), entry.lastModified())
    }

    internal fun parseSidecarText(text: String, fallbackDeletedAt: Long): Sidecar {
        val map = text.lines().mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        val path = map["path"] ?: text.trim().takeIf { !it.contains('=') }.orEmpty()
        val deletedAt = map["deletedAt"]?.toLongOrNull() ?: fallbackDeletedAt
        return Sidecar(path, deletedAt)
    }

    /**
     * Copy with decrypt/re-encrypt so EncryptedFile AAD matches the destination name.
     * Restore always uses this (never rename) so vault files stay readable.
     */
    private fun copyVaultAware(context: Context, source: File, dest: File): Boolean {
        return try {
            if (source.isDirectory) {
                dest.mkdirs()
                source.listFiles()?.forEach { child ->
                    if (child.name.endsWith(ORIGIN_SUFFIX)) {
                        return@forEach
                    }
                    if (!copyVaultAware(context, child, File(dest, child.name))) {
                        return false
                    }
                }
                true
            } else if (HiddenFileCrypto.appliesTo(context, source.absolutePath) &&
                HiddenFileCrypto.isEncrypted(context, source)
            ) {
                dest.parentFile?.mkdirs()
                HiddenFileCrypto.openInput(context, source.absolutePath).use { input ->
                    HiddenFileCrypto.openOutput(context, dest.absolutePath).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                dest.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteSource(source: File): Boolean =
        if (source.isDirectory) source.deleteRecursively() else source.delete()

    private fun deleteTrashedEntry(entry: File) {
        if (entry.isDirectory) {
            entry.deleteRecursively()
        } else {
            entry.delete()
        }
        File("${entry.absolutePath}$ORIGIN_SUFFIX").delete()
    }
}
