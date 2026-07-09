package ltechnologies.onionphone.securefilemanager.transfer

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.*
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.LinkedHashMap

class TransferEngine(
    private val context: Context,
    private val onError: (String) -> Unit,
) {

    interface ProgressCallback {
        fun onProgress(copiedBytes: Long, totalBytes: Long, currentFileName: String)
    }

    fun run(
        files: ArrayList<FileDirItem>,
        destinationPath: String,
        copyOnly: Boolean,
        copyMediaOnly: Boolean,
        conflictResolutions: LinkedHashMap<String, Int>,
        hideAction: HideAction,
        progress: ProgressCallback,
        isCancelled: () -> Boolean,
    ): TransferResult {
        val transferred = ArrayList<FileDirItem>()
        val failed = ArrayList<String>()
        var expectedCount = files.size
        var totalBytes = 0L
        val documents = LinkedHashMap<String, DocumentFile?>()

        for (file in files) {
            if (file.size == 0L) {
                file.size = file.getProperSize()
            }
            val newPath = "$destinationPath/${file.name}"
            val fileExists = context.getDoesFilePathExist(newPath)
            if (getConflictResolution(conflictResolutions, newPath) != CONFLICT_SKIP || !fileExists) {
                totalBytes += file.size
            }
        }

        var copiedBytes = 0L
        val bumpProgress = { bytes: Long, name: String ->
            copiedBytes += bytes
            progress.onProgress(copiedBytes, totalBytes, name)
        }

        for (file in files) {
            if (isCancelled()) {
                break
            }
            try {
                var newFileDirItem = destinationChild(file, destinationPath)

                if (context.getDoesFilePathExist(newFileDirItem.path)) {
                    when (getConflictResolution(conflictResolutions, newFileDirItem.path)) {
                        CONFLICT_SKIP -> {
                            expectedCount--
                            continue
                        }
                        CONFLICT_OVERWRITE -> {
                            newFileDirItem.isDirectory =
                                if (context.getDoesFilePathExist(newFileDirItem.path)) {
                                    File(newFileDirItem.path).isDirectory
                                } else {
                                    context.getSomeDocumentFile(newFileDirItem.path)!!.isDirectory
                                }
                            context.deleteFileBgSync(newFileDirItem, true)
                            if (!newFileDirItem.isDirectory) {
                                context.deleteFromMediaStore(newFileDirItem.path)
                            }
                        }
                        CONFLICT_KEEP_BOTH -> {
                            val alt = context.getAlternativeFile(File(newFileDirItem.path))
                            newFileDirItem =
                                FileDirItem(alt.path, alt.name, alt.isDirectory)
                        }
                    }
                }

                if (
                    copyEntry(
                        file,
                        newFileDirItem,
                        copyOnly,
                        copyMediaOnly,
                        hideAction,
                        documents,
                        bumpProgress,
                        isCancelled,
                    )
                ) {
                    transferred.add(file)
                } else {
                    failed.add(file.path)
                }
            } catch (e: Exception) {
                onError(e.message ?: e.toString())
                failed.add(file.path)
            }
        }

        return TransferResult(
            success = failed.isEmpty() && !isCancelled(),
            transferredCount = transferred.size,
            expectedCount = expectedCount,
            destinationPath = destinationPath,
            failedPaths = failed,
        )
    }

    private fun destinationChild(file: FileDirItem, destinationPath: String) =
        FileDirItem(
            "$destinationPath/${file.name}",
            file.name,
            file.isDirectory,
        )

    private fun copyEntry(
        source: FileDirItem,
        destination: FileDirItem,
        copyOnly: Boolean,
        copyMediaOnly: Boolean,
        hideAction: HideAction,
        documents: LinkedHashMap<String, DocumentFile?>,
        bumpProgress: (Long, String) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        if (isCancelled()) {
            return false
        }
        return if (source.isDirectory) {
            copyDirectory(
                source,
                destination.path,
                copyOnly,
                copyMediaOnly,
                hideAction,
                documents,
                bumpProgress,
                isCancelled,
            )
        } else {
            copyFile(
                source,
                destination,
                copyOnly,
                copyMediaOnly,
                hideAction,
                documents,
                bumpProgress,
            )
        }
    }

    private fun copyDirectory(
        source: FileDirItem,
        destinationPath: String,
        copyOnly: Boolean,
        copyMediaOnly: Boolean,
        hideAction: HideAction,
        documents: LinkedHashMap<String, DocumentFile?>,
        bumpProgress: (Long, String) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        if (!context.createDirectorySync(destinationPath)) {
            onError(
                String.format(
                    context.getString(ltechnologies.onionphone.securefilemanager.R.string.could_not_create_folder),
                    destinationPath,
                ),
            )
            return false
        }

        val sourceDir = File(source.path)
        if (!sourceDir.isDirectory) {
            return false
        }

        Files.newDirectoryStream(sourceDir.toPath()).use { stream ->
            for (childPath in stream) {
                if (isCancelled()) {
                    return false
                }
                val childName = childPath.fileName.toString()
                val newPath = "$destinationPath/$childName"
                if (context.getDoesFilePathExist(newPath)) {
                    continue
                }
                val oldFile = childPath.toFile()
                val oldFileDirItem = oldFile.toFileDirItem(context)
                val newFileDirItem =
                    FileDirItem(newPath, newPath.getFilenameFromPath(), oldFile.isDirectory)
                copyEntry(
                    oldFileDirItem,
                    newFileDirItem,
                    copyOnly,
                    copyMediaOnly,
                    hideAction,
                    documents,
                    bumpProgress,
                    isCancelled,
                )
            }
        }
        return true
    }

    private fun copyFile(
        source: FileDirItem,
        destination: FileDirItem,
        copyOnly: Boolean,
        copyMediaOnly: Boolean,
        hideAction: HideAction,
        documents: LinkedHashMap<String, DocumentFile?>,
        bumpProgress: (Long, String) -> Unit,
    ): Boolean {
        if (copyMediaOnly && !source.path.isMediaFile()) {
            bumpProgress(source.size, source.name)
            return true
        }

        val directory = destination.getParentPath()
        if (!context.createDirectorySync(directory)) {
            onError(
                String.format(
                    context.getString(ltechnologies.onionphone.securefilemanager.R.string.could_not_create_folder),
                    directory,
                ),
            )
            bumpProgress(source.size, source.name)
            return false
        }

        val sourceFile = File(source.path)
        val destFile = File(destination.path)
        var copiedSize = 0L

        try {
            if (LocalFileTransfer.canUseChannelTransfer(context, source.path, destination.path)) {
                copiedSize = LocalFileTransfer.copyFileChannel(context, sourceFile, destFile)
                bumpProgress(copiedSize, source.name)
            } else {
                if (!documents.containsKey(directory) &&
                    context.needsStupidWritePermissions(destination.path)
                ) {
                    documents[directory] = context.getDocumentFile(directory)
                }
                val output = context.getFileOutputStreamSync(
                    destination.path,
                    source.path.getMimeType(),
                    documents[directory],
                ) ?: return false
                val input = context.getFileInputStreamSync(source.path) ?: return false
                copiedSize = LocalFileTransfer.copyStreams(input, output, LocalFileTransfer.BUFFER_SIZE) { bytes ->
                    bumpProgress(bytes, source.name)
                }
            }

            if (context.getDoesFilePathExist(destination.path) && source.size == copiedSize) {
                applyPostCopy(source, destination, copyOnly, hideAction)
                return true
            }
        } catch (e: Exception) {
            onError(e.message ?: e.toString())
        }
        return false
    }

    private fun applyPostCopy(
        source: FileDirItem,
        destination: FileDirItem,
        copyOnly: Boolean,
        hideAction: HideAction,
    ) {
        if (copyOnly && destination.path.isAudioFast()) {
            context.rescanPath(destination.path) {
                if (context.config.keepLastModified) {
                    copyOldLastModified(source.path, destination.path)
                    File(destination.path).setLastModified(File(source.path).lastModified())
                }
            }
        } else if (context.config.keepLastModified) {
            copyOldLastModified(source.path, destination.path)
            File(destination.path).setLastModified(File(source.path).lastModified())
        }

        if (!copyOnly) {
            context.deleteFileBgSync(source, false)
            context.deleteFromMediaStore(source.path)
        } else if (isHide(hideAction)) {
            context.deleteFromMediaStore(source.path)
        }
    }

    private fun copyOldLastModified(sourcePath: String, destinationPath: String) {
        val projection = arrayOf(
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
        )
        val uri = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.MediaColumns.DATA} = ?"
        context.contentResolver.query(uri, projection, selection, arrayOf(sourcePath), null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val values = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DATE_TAKEN,
                            cursor.getLongValue(MediaStore.Images.Media.DATE_TAKEN),
                        )
                        put(
                            MediaStore.Images.Media.DATE_MODIFIED,
                            cursor.getIntValue(MediaStore.Images.Media.DATE_MODIFIED),
                        )
                    }
                    context.contentResolver.update(
                        uri,
                        values,
                        selection,
                        arrayOf(destinationPath),
                    )
                }
            }
    }

    companion object {
        fun tryFastMove(source: File, destination: File): Boolean {
            destination.parentFile?.mkdirs()
            return try {
                Files.move(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
                true
            } catch (_: Exception) {
                source.renameTo(destination)
            }
        }
    }
}
