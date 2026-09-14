package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import ltechnologies.onionphone.securefilemanager.extensions.isPathOnHidden
import ltechnologies.onionphone.securefilemanager.helpers.crypto.HiddenFileCrypto
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import java.io.File

object RemoteTransfer {
    fun copyToRemote(
        context: Context,
        files: List<FileDirItem>,
        destinationVirtualPath: String,
        isCopyOperation: Boolean,
    ): Pair<Int, Int> {
        val destParsed = RemotePath.parse(destinationVirtualPath) ?: return 0 to files.size
        val destDir = destParsed.remotePath.trimEnd('/').ifEmpty { "/" }
        var ok = 0
        var fail = 0
        for (file in files) {
            try {
                if (RemotePath.isRemote(file.path)) {
                    RemoteBrowser.copyRemoteToRemote(
                        context,
                        file.path,
                        destDir,
                        destParsed,
                        moveWithinServer = !isCopyOperation,
                    )
                    if (!isCopyOperation) {
                        val sourceParsed = RemotePath.parse(file.path)
                        val sameServer = sourceParsed?.let { sp ->
                            sp.scheme == destParsed.scheme &&
                                sp.host == destParsed.host &&
                                sp.username == destParsed.username
                        } == true
                        if (!sameServer) {
                            RemoteBrowser.delete(context, file.path)
                        }
                    }
                } else {
                    uploadLocalMaybeVault(context, File(file.path), destDir, destParsed)
                    if (!isCopyOperation && !File(file.path).delete()) {
                        error("local delete failed")
                    }
                }
                ok++
            } catch (_: Exception) {
                fail++
            }
        }
        return ok to fail
    }

    fun copyFromRemote(
        context: Context,
        files: List<FileDirItem>,
        localDestination: String,
        isCopyOperation: Boolean,
    ): Boolean {
        val destFolder = File(localDestination)
        if (!destFolder.isDirectory) {
            return false
        }
        var allOk = true
        for (file in files) {
            if (!RemotePath.isRemote(file.path)) {
                allOk = false
                continue
            }
            try {
                val cached = RemoteBrowser.downloadToCache(context, file.path)
                val source = File(cached)
                val target = File(destFolder, source.name)
                if (context.isPathOnHidden(target.absolutePath) &&
                    !HiddenFileCrypto.isPgpPath(target.absolutePath)
                ) {
                    HiddenFileCrypto.openOutput(context, target.absolutePath).use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    }
                } else {
                    source.copyTo(target, overwrite = true)
                }
                source.delete()
                if (!isCopyOperation) {
                    RemoteBrowser.delete(context, file.path)
                }
            } catch (_: Exception) {
                allOk = false
            }
        }
        return allOk
    }

    fun deleteRemote(context: Context, files: List<FileDirItem>): Boolean {
        var allOk = true
        for (file in files) {
            if (!RemotePath.isRemote(file.path)) {
                allOk = false
                continue
            }
            try {
                RemoteBrowser.delete(context, file.path)
            } catch (_: Exception) {
                allOk = false
            }
        }
        return allOk
    }

    fun renameRemote(context: Context, oldPath: String, newPath: String): Boolean =
        try {
            RemoteBrowser.rename(context, oldPath, newPath)
            true
        } catch (_: Exception) {
            false
        }

    private fun uploadLocalMaybeVault(
        context: Context,
        local: File,
        destDir: String,
        destParsed: RemotePath.Parsed,
    ) {
        if (HiddenFileCrypto.appliesTo(context, local.absolutePath) &&
            HiddenFileCrypto.isEncrypted(context, local)
        ) {
            val staged = File(context.cacheDir, "remote-up/${local.name}").also {
                it.parentFile?.mkdirs()
            }
            try {
                HiddenFileCrypto.openInput(context, local.absolutePath).use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
                RemoteBrowser.uploadLocal(context, staged, destDir, destParsed)
            } finally {
                staged.delete()
            }
        } else {
            RemoteBrowser.uploadLocal(context, local, destDir, destParsed)
        }
    }
}
