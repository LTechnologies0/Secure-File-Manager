package ltechnologies.onionphone.securefilemanager.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.dialogs.FilePickerDialog
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.EncryptionAction
import ltechnologies.onionphone.securefilemanager.helpers.HideAction
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import java.io.File
import java.util.LinkedHashMap

class SaveAsActivity : BaseAbstractActivity() {

    private var pendingMultiple = false
    private var saveStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_save_as)

        if (intent.extras?.containsKey(Intent.EXTRA_STREAM) != true) {
            this.error()
            return
        }

        pendingMultiple = when (intent?.action) {
            Intent.ACTION_SEND -> false
            Intent.ACTION_SEND_MULTIPLE -> true
            else -> {
                error()
                return
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (saveStarted) {
            return
        }
        if (isAuthenticatorSet() && !config.wasAppProtectionHandled) {
            return
        }
        saveStarted = true
        handleSave(pendingMultiple)
    }

    private fun handleSave(isMultiple: Boolean) {
        FilePickerDialog(
            this,
            pickFile = false,
            showFAB = true,
            finishOnBackPress = true,
            callbackNegative = { finish() }
        ) { destination ->
            handleSAFDialog(destination) {
                if (!it) {
                    return@handleSAFDialog
                }

                this.toast(R.string.saving)
                try {
                    val sources = intent.readStreamUris(isMultiple)
                        ?: return@handleSAFDialog error()

                    val stagingDir = File(cacheDir, "incoming").apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    val files = ArrayList<FileDirItem>()
                    for (source in sources) {
                        val mimeType = contentResolver.getType(source) ?: "application/octet-stream"
                        val inputStream = contentResolver.openInputStream(source) ?: continue
                        val filename = source.safeIncomingFilename()
                        val staged = File(stagingDir, filename)
                        inputStream.use { input ->
                            staged.outputStream().use { output ->
                                input.copyToLimited(output, MAX_INCOMING_BYTES)
                            }
                        }
                        files.add(FileDirItem(staged.path, filename, false))
                    }
                    if (files.isEmpty()) {
                        error()
                        return@handleSAFDialog
                    }
                    transferCompleteCallback = { success ->
                        if (success) {
                            rescanPaths(ArrayList(files.map { "$destination/${it.name}" }))
                            toast(R.string.file_saved)
                        } else {
                            toast(R.string.copy_move_failed)
                        }
                        finish()
                    }
                    startTransfer(
                        files = files,
                        destinationPath = destination,
                        isCopyOperation = true,
                        conflictResolutions = LinkedHashMap(),
                    )
                } catch (e: Exception) {
                    showErrorToast(e)
                    finish()
                }
            }
        }
    }

    private fun error() {
        this.toast(R.string.unknown_error_occurred)
        this.finish()
    }

    private fun Uri.safeIncomingFilename(): String {
        val fromProvider = contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        return sanitizeIncomingFilename(fromProvider ?: lastPathSegment ?: "shared")
    }

    private fun sanitizeIncomingFilename(raw: String): String {
        val name = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        require(
            name.isNotEmpty() &&
                ".." !in name &&
                '/' !in name &&
                '\\' !in name &&
                '\u0000' !in name,
        ) { "invalid filename" }
        return name.take(255)
    }

    private fun java.io.InputStream.copyToLimited(out: java.io.OutputStream, limit: Long): Long {
        var total = 0L
        val buffer = ByteArray(256 * 1024)
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            if (read > 0) {
                total += read
                if (total > limit) error("incoming file too large")
                out.write(buffer, 0, read)
            }
        }
        return total
    }

    companion object {
        private const val MAX_INCOMING_BYTES = 512L * 1024 * 1024
    }
}

@Suppress("DEPRECATION")
private fun Intent.readStreamUris(multiple: Boolean): ArrayList<Uri>? {
    return if (multiple) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
    } else {
        val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (single == null) null else arrayListOf(single)
    }
}
