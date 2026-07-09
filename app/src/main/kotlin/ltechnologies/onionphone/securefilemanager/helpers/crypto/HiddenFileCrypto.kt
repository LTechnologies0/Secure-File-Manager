package ltechnologies.onionphone.securefilemanager.helpers.crypto

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import ltechnologies.onionphone.securefilemanager.extensions.getHiddenPath
import ltechnologies.onionphone.securefilemanager.extensions.isPathOnHidden
import ltechnologies.onionphone.securefilemanager.extensions.isOpenPgpFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Keystore-backed encryption for files in the app-private hidden directory. Never touches PGP files. */
object HiddenFileCrypto {
    private const val FILE_KEY_ALIAS = "__sfm_hidden_files__"
    private const val META_DIR = ".sfm-meta"
    private const val VIEW_CACHE_DIR = "hidden-view"

    /** PGP ciphertext must never pass through this layer — OpenPGP stays in PGP Shield. */
    fun isPgpPath(path: String): Boolean = path.isOpenPgpFile()

    fun appliesTo(context: Context, path: String): Boolean =
        context.isPathOnHidden(path) && !isPgpPath(path)

    fun openInput(context: Context, path: String): InputStream {
        require(!isPgpPath(path)) { "PGP files must not use HiddenFileCrypto: $path" }
        val file = File(path)
        return if (isEncrypted(context, file)) {
            encryptedFile(context, file).openFileInput()
        } else {
            FileInputStream(file)
        }
    }

    fun openOutput(context: Context, path: String): OutputStream {
        require(!isPgpPath(path)) { "PGP files must not use HiddenFileCrypto: $path" }
        return EncryptedOutputStream(context, File(path))
    }

    fun isEncrypted(context: Context, file: File): Boolean {
        if (!file.isFile) {
            return false
        }
        if (readMeta(context, file.absolutePath) != null) {
            return true
        }
        return try {
            encryptedFile(context, file).openFileInput().use { it.read() }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getPlaintextSize(context: Context, path: String): Long {
        readMeta(context, path)?.size?.let { return it }
        return File(path).length()
    }

    fun getPlaintextLastModified(context: Context, path: String): Long {
        readMeta(context, path)?.modified?.let { return it }
        return File(path).lastModified()
    }

    fun getViewablePath(context: Context, path: String): String {
        if (!context.isPathOnHidden(path) || isPgpPath(path)) {
            return path
        }
        val source = File(path)
        if (source.isDirectory || !isEncrypted(context, source)) {
            return path
        }
        val cacheRoot = File(context.cacheDir, VIEW_CACHE_DIR)
        cacheRoot.mkdirs()
        val cacheFile = File(cacheRoot, cacheName(path))
        val sourceModified = source.lastModified()
        if (cacheFile.isFile && cacheFile.lastModified() >= sourceModified) {
            return cacheFile.absolutePath
        }
        encryptedFile(context, source).openFileInput().use { input ->
            FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
        }
        cacheFile.setLastModified(sourceModified)
        return cacheFile.absolutePath
    }

    fun migratePlaintextFiles(context: Context) {
        val hiddenRoot = File(context.getHiddenPath())
        if (!hiddenRoot.isDirectory) {
            return
        }
        val metaRoot = metaRoot(context)
        metaRoot.mkdirs()
        hiddenRoot.walkTopDown()
            .filter { it.isFile && !it.absolutePath.startsWith(metaRoot.absolutePath) }
            .filter { !isPgpPath(it.absolutePath) }
            .forEach { file ->
                if (!isEncrypted(context, file)) {
                    migrateFile(context, file)
                }
            }
    }

    private fun migrateFile(context: Context, file: File) {
        val plaintext = file.readBytes()
        val modified = file.lastModified()
        file.delete()
        EncryptedOutputStream(context, file, plaintext.size.toLong(), modified).closeWith(plaintext)
    }

    private fun encryptedFile(context: Context, file: File): EncryptedFile =
        EncryptedFile.Builder(
            context,
            file,
            fileMasterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    private fun fileMasterKey(context: Context): MasterKey =
        MasterKey.Builder(context, FILE_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun metaRoot(context: Context) = File(context.getHiddenPath(), META_DIR)

    private fun metaFile(context: Context, path: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(metaRoot(context), "$digest.meta")
    }

    private fun readMeta(context: Context, path: String): FileMeta? {
        val meta = metaFile(context, path)
        if (!meta.isFile) {
            return null
        }
        val map = meta.readLines().mapNotNull { line ->
            val parts = line.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
        val size = map["size"]?.toLongOrNull() ?: return null
        val modified = map["modified"]?.toLongOrNull() ?: return null
        return FileMeta(size, modified)
    }

    private fun writeMeta(context: Context, path: String, size: Long, modified: Long) {
        metaRoot(context).mkdirs()
        metaFile(context, path).writeText("size=$size\nmodified=$modified\n")
    }

    private fun cacheName(path: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class FileMeta(val size: Long, val modified: Long)

    private class EncryptedOutputStream(
        context: Context,
        private val file: File,
        private val plaintextSize: Long = -1L,
        private val plaintextModified: Long = -1L,
    ) : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        private val appContext = context.applicationContext

        override fun write(b: Int) {
            buffer.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            buffer.write(b, off, len)
        }

        override fun close() {
            closeWith(buffer.toByteArray())
        }

        fun closeWith(data: ByteArray) {
            file.parentFile?.mkdirs()
            if (file.exists()) {
                file.delete()
            }
            encryptedFile(appContext, file).openFileOutput().use { it.write(data) }
            val size = if (plaintextSize >= 0) plaintextSize else data.size.toLong()
            val modified = if (plaintextModified >= 0) plaintextModified else System.currentTimeMillis()
            writeMeta(appContext, file.absolutePath, size, modified)
            file.setLastModified(modified)
        }
    }
}
