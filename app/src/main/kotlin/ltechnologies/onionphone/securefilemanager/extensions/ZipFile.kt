package ltechnologies.onionphone.securefilemanager.extensions

import android.content.Context
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.helpers.crypto.HiddenFileCrypto
import kotlinx.coroutines.delay
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File

const val DELAY = 10L

fun ZipFile.checkDecompressionCollision(
    context: Context,
    destination: String,
    callback: (collision: Boolean) -> Unit
) {
    this.fileHeaders.forEach { fileHeader ->
        val name = fileHeader.fileName
        val extractionPath = "${destination.trimEnd('/')}/${name}"
        if (context.getDoesFilePathExist(extractionPath)) {
            context.toast(R.string.decompressing_failed)
            context.toastLong(
                String.format(
                    context.getString(R.string.decompress_conflicts),
                    name.trimEnd('/')
                )
            )
            callback.invoke(true)
            return
        }
    }
    callback.invoke(false)
}

suspend fun ZipFile.insertAll(context: Context, sourcePaths: List<String>, parameters: ZipParameters) {
    val zipFile: ZipFile = this
    sourcePaths
        .map { sourcePath -> File(sourcePath) }
        .partition { file -> context.getIsPathDirectory(file.absolutePath) }
        .apply {
            zipFile.addFoldersAndFiles(context, this, parameters)
        }
}

suspend fun ZipFile.addFoldersAndFiles(
    context: Context,
    foldersAndFilesToAdd: Pair<List<File>, List<File>>,
    parameters: ZipParameters,
) {
    val (folders: List<File>, files: List<File>) = foldersAndFilesToAdd
    waitAndAddFiles(context, files, parameters)
    waitAndAddFolders(context, folders, parameters)
}

suspend fun ZipFile.waitAndAddFiles(
    context: Context,
    filesToAdd: List<File>,
    parameters: ZipParameters,
) {
    if (filesToAdd.isEmpty()) return
    for (file in filesToAdd) {
        waitToReady(this) {
            addFileMaybeVault(context, file, parameters, file.name)
        }
    }
}

suspend fun ZipFile.waitAndAddFolders(
    context: Context,
    foldersToAdd: List<File>,
    parameters: ZipParameters,
) {
    val zipFile: ZipFile = this
    foldersToAdd.forEach { folder ->
        if (HiddenFileCrypto.appliesTo(context, folder.absolutePath)) {
            folder.walkTopDown()
                .filter { it.isFile && !HiddenFileCrypto.isPgpPath(it.absolutePath) }
                .forEach { file ->
                    val relative = file.absolutePath
                        .removePrefix(folder.parentFile!!.absolutePath)
                        .trimStart('/')
                    waitToReady(zipFile) {
                        zipFile.addFileMaybeVault(context, file, parameters, relative)
                    }
                }
        } else {
            waitToReady(zipFile) {
                zipFile.addFolder(folder, parameters)
            }
        }
    }
}

private fun ZipFile.addFileMaybeVault(
    context: Context,
    file: File,
    parameters: ZipParameters,
    nameInZip: String,
) {
    if (HiddenFileCrypto.appliesTo(context, file.absolutePath) &&
        HiddenFileCrypto.isEncrypted(context, file)
    ) {
        val params = ZipParameters(parameters).apply {
            fileNameInZip = nameInZip
        }
        HiddenFileCrypto.openInput(context, file.absolutePath).use { input ->
            addStream(input, params)
        }
    } else if (nameInZip != file.name) {
        val params = ZipParameters(parameters).apply {
            fileNameInZip = nameInZip
        }
        file.inputStream().use { input -> addStream(input, params) }
    } else {
        addFile(file, parameters)
    }
}

private suspend fun waitToReady(zipFile: ZipFile, callback: () -> Unit) {
    while (zipFile.progressMonitor.state != ProgressMonitor.State.READY) {
        delay(DELAY)
    }
    callback.invoke()
}
