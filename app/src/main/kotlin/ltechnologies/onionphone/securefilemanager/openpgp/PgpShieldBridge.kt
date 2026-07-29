package ltechnologies.onionphone.securefilemanager.openpgp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.extensions.getUriForFile
import ltechnologies.onionphone.securefilemanager.extensions.isOpenPgpFile
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.SessionLog
import java.io.File

object PgpShieldBridge {
    const val ACTION_ENCRYPT = "org.sufficientlysecure.keychain.action.ENCRYPT_DATA"
    const val ACTION_DECRYPT = "org.sufficientlysecure.keychain.action.DECRYPT_DATA"
    const val ACTION_ENCRYPT_FOLDER = "ltechnologies.onionphone.pgpshield.action.ENCRYPT_FOLDER"
    const val PACKAGE = "ltechnologies.onionphone.pgpshield"
    const val CLASS_ENCRYPT_FILE = "ltechnologies.onionphone.pgpshield.intent.EncryptFileActivity"
    const val CLASS_ENCRYPT_MULTIPLE = "ltechnologies.onionphone.pgpshield.intent.EncryptMultipleActivity"
    const val CLASS_ENCRYPT_FOLDER = "ltechnologies.onionphone.pgpshield.intent.EncryptFolderActivity"
    const val CLASS_DECRYPT_FILE = "ltechnologies.onionphone.pgpshield.intent.DecryptFileActivity"
    const val EXTRA_ENABLE_COMPRESSION = "enable_compression"
    const val EXTRA_RELATIVE_PATHS = "ltechnologies.onionphone.pgpshield.extra.RELATIVE_PATHS"
    const val EXTRA_OUTPUT_PATH = "ltechnologies.onionphone.pgpshield.extra.OUTPUT_PATH"
    const val EXTRA_ARCHIVE_NAME = "ltechnologies.onionphone.pgpshield.extra.ARCHIVE_NAME"
    const val EXTRA_FOLDER_LABEL = "ltechnologies.onionphone.pgpshield.extra.FOLDER_LABEL"
    const val EXTRA_SOURCE_PATHS = "ltechnologies.onionphone.pgpshield.extra.SOURCE_PATHS"
    const val EXTRA_DELETE_SOURCE = "ltechnologies.onionphone.pgpshield.extra.DELETE_SOURCE"

    private val PACKAGES = listOf(PACKAGE)

    fun isInstalled(activity: BaseAbstractActivity): Boolean =
        resolvePackage(activity.packageManager, ACTION_ENCRYPT) != null

    fun isPackageInstalled(packageManager: PackageManager): Boolean =
        resolvePackage(packageManager, ACTION_ENCRYPT) != null

    fun buildManageKeysIntent(packageManager: PackageManager): Intent? =
        packageManager.getLaunchIntentForPackage(PACKAGE)

    fun encrypt(activity: BaseAbstractActivity, paths: List<String>) {
        activity.launchPgpShield(paths, ACTION_ENCRYPT)
    }

    fun encryptFolder(activity: BaseAbstractActivity, folderPath: String) {
        val intent = buildFolderEncryptIntent(activity, folderPath)
        if (intent == null) {
            activity.toast(
                if (resolvePackage(activity, ACTION_ENCRYPT) == null) {
                    ltechnologies.onionphone.securefilemanager.R.string.pgpshield_missing
                } else {
                    ltechnologies.onionphone.securefilemanager.R.string.unknown_error_occurred
                },
            )
            return
        }
        activity.launchPgpShieldIntent(intent, ACTION_ENCRYPT_FOLDER)
    }

    /**
     * Packs [sourcePaths] (files and/or folders) into one OpenPGP archive via PGP Shield.
     * Compression is handled by PGP Shield (GpgTar), not local zip.
     */
    fun encryptAsArchive(
        activity: BaseAbstractActivity,
        sourcePaths: List<String>,
        outputDir: String,
        archiveName: String,
    ) {
        val intent = buildSelectionArchiveIntent(activity, sourcePaths, outputDir, archiveName)
        if (intent == null) {
            activity.toast(
                if (resolvePackage(activity, ACTION_ENCRYPT) == null) {
                    ltechnologies.onionphone.securefilemanager.R.string.pgpshield_missing
                } else {
                    ltechnologies.onionphone.securefilemanager.R.string.unknown_error_occurred
                },
            )
            return
        }
        activity.launchPgpShieldIntent(intent, ACTION_ENCRYPT_FOLDER)
    }

    fun decrypt(activity: BaseAbstractActivity, paths: List<String>) {
        activity.launchPgpShield(paths, ACTION_DECRYPT)
    }

    fun buildIntent(activity: BaseAbstractActivity, paths: List<String>, action: String): Intent? {
        val uris = paths.mapNotNull { path ->
            File(path).takeIf { it.isFile }?.let { activity.getUriForFile(it) }
        }
        if (uris.isEmpty()) {
            return null
        }
        val pkg = resolvePackage(activity.packageManager, action) ?: return null
        val intent = if (uris.size == 1) {
            Intent(action, uris[0]).apply { type = guessMime(uris[0]) }
        } else {
            Intent(action).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }.apply {
            setPackage(pkg)
            when (action) {
                ACTION_ENCRYPT -> {
                    if (uris.size == 1) {
                        setClassName(pkg, CLASS_ENCRYPT_FILE)
                    } else {
                        setClassName(pkg, CLASS_ENCRYPT_MULTIPLE)
                    }
                }
                ACTION_DECRYPT -> setClassName(pkg, CLASS_DECRYPT_FILE)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putStringArrayListExtra(EXTRA_SOURCE_PATHS, ArrayList(paths))
            putExtra(EXTRA_DELETE_SOURCE, !activity.config.keepAfterEncryptionOperation)
            if (paths.size == 1) {
                putExtra(EXTRA_OUTPUT_PATH, File(paths.first()).parent)
            }
            if (action == ACTION_ENCRYPT) {
                putExtra(EXTRA_ENABLE_COMPRESSION, true)
            }
            uris.forEach { uri ->
                activity.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return intent
    }

    fun buildFolderEncryptIntent(activity: BaseAbstractActivity, folderPath: String): Intent? {
        val folder = File(folderPath)
        if (!folder.isDirectory) {
            return null
        }
        val files = mutableListOf<Pair<String, File>>()
        collectEncryptableFiles(folder, folder, files)
        if (files.isEmpty()) {
            return null
        }
        return buildArchiveIntent(
            activity = activity,
            files = files,
            sourcePaths = listOf(folderPath),
            outputDir = folder.parent ?: return null,
            archiveName = "${folder.name}.gpg",
            folderLabel = folder.name,
        )
    }

    fun buildSelectionArchiveIntent(
        activity: BaseAbstractActivity,
        sourcePaths: List<String>,
        outputDir: String,
        archiveName: String,
    ): Intent? {
        val files = mutableListOf<Pair<String, File>>()
        sourcePaths.forEach { path ->
            val item = File(path)
            when {
                item.isDirectory -> {
                    val parent = item.parentFile ?: return@forEach
                    collectEncryptableFiles(parent, item, files)
                }
                item.isFile && !path.isOpenPgpFile() -> files.add(item.name to item)
            }
        }
        if (files.isEmpty()) {
            return null
        }
        val label = archiveName.removeSuffix(".gpg").ifEmpty { archiveName }
        return buildArchiveIntent(
            activity = activity,
            files = files,
            sourcePaths = sourcePaths,
            outputDir = outputDir,
            archiveName = if (archiveName.endsWith(".gpg")) archiveName else "$archiveName.gpg",
            folderLabel = label,
        )
    }

    private fun buildArchiveIntent(
        activity: BaseAbstractActivity,
        files: List<Pair<String, File>>,
        sourcePaths: List<String>,
        outputDir: String,
        archiveName: String,
        folderLabel: String,
    ): Intent? {
        val pkg = resolvePackage(activity.packageManager, ACTION_ENCRYPT) ?: return null
        val uris = ArrayList(files.map { activity.getUriForFile(it.second) })
        val relativePaths = ArrayList(files.map { it.first })
        return Intent(ACTION_ENCRYPT_FOLDER).apply {
            setPackage(pkg)
            setClassName(pkg, CLASS_ENCRYPT_FOLDER)
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putStringArrayListExtra(EXTRA_RELATIVE_PATHS, relativePaths)
            putStringArrayListExtra(EXTRA_SOURCE_PATHS, ArrayList(sourcePaths))
            putExtra(EXTRA_OUTPUT_PATH, outputDir)
            putExtra(EXTRA_ARCHIVE_NAME, archiveName)
            putExtra(EXTRA_FOLDER_LABEL, folderLabel)
            putExtra(EXTRA_DELETE_SOURCE, !activity.config.keepAfterEncryptionOperation)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            uris.forEach { uri ->
                activity.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun folderHasEncryptableFiles(folderPath: String): Boolean {
        val folder = File(folderPath)
        if (!folder.isDirectory) {
            return false
        }
        val files = mutableListOf<Pair<String, File>>()
        collectEncryptableFiles(folder, folder, files)
        return files.isNotEmpty()
    }

    private fun collectEncryptableFiles(
        root: File,
        dir: File,
        out: MutableList<Pair<String, File>>,
    ) {
        dir.listFiles()?.forEach { child ->
            when {
                child.isDirectory -> collectEncryptableFiles(root, child, out)
                !child.absolutePath.isOpenPgpFile() -> {
                    val rel = root.toURI().relativize(child.toURI()).path.replace('\\', '/')
                    out.add(rel to child)
                }
            }
        }
    }

    fun resolvePackage(activity: BaseAbstractActivity, action: String): String? =
        resolvePackage(activity.packageManager, action)

    private fun resolvePackage(packageManager: PackageManager, action: String): String? =
        PACKAGES.firstOrNull { pkg ->
            try {
                packageManager.getPackageInfo(pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return@firstOrNull false
            }
            packageManager.resolveActivity(
                probeIntent(action).setPackage(pkg),
                PackageManager.MATCH_DEFAULT_ONLY,
            ) != null
        }.also { resolved ->
            SessionLog.log(
                "PgpShieldBridge.resolvePackage",
                "pgp-shield intent resolve",
                "I",
                mapOf("action" to action, "resolved" to resolved),
            )
        }

    private fun probeIntent(action: String): Intent =
        Intent(action).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_DEFAULT)
        }

    private fun guessMime(uri: Uri): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
