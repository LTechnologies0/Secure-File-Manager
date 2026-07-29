package ltechnologies.onionphone.securefilemanager.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogCreateNewBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.storage.RemoteBrowser
import ltechnologies.onionphone.securefilemanager.storage.RemotePath
import java.io.File
import java.io.IOException

class CreateNewItemDialog(
    val activity: BaseAbstractActivity,
    val path: String,
    val callback: (success: Boolean) -> Unit
) {
    private val binding = DialogCreateNewBinding.inflate(activity.layoutInflater)

    init {
        activity.showM3FormDialog(
            titleId = R.string.create_new,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
        ) { primary, _, _ ->
            showKeyboard(binding.itemName)
            primary.setOnClickListener(View.OnClickListener {
                val name = binding.itemName.value
                if (name.isEmpty()) {
                    activity.toast(R.string.empty_name)
                } else if (name.isAValidFilename()) {
                    val isDirectory =
                        binding.dialogRadioGroup.checkedRadioButtonId == R.id.dialog_radio_directory
                    if (RemotePath.isRemote(path) && !isDirectory) {
                        activity.toast(R.string.remote_create_file_unsupported)
                        return@OnClickListener
                    }
                    val newPath = if (RemotePath.isRemote(path)) {
                        val base = path.trimEnd('/')
                        if (isDirectory) "$base/$name/" else "$base/$name"
                    } else {
                        "$path/$name"
                    }
                    createWithExistsCheck(newPath, isDirectory, this)
                } else {
                    activity.toast(R.string.invalid_name)
                }
            })
        }
    }

    private fun createWithExistsCheck(
        newPath: String,
        isDirectory: Boolean,
        alertDialog: AlertDialog,
    ) {
        activity.getDoesFilePathExistAsync(newPath) { exists ->
            activity.runOnUiThread {
                if (exists) {
                    activity.toast(R.string.name_taken)
                    return@runOnUiThread
                }
                if (isDirectory) {
                    createDirectory(newPath, alertDialog) { callback(it) }
                } else {
                    createFile(newPath, alertDialog) { callback(it) }
                }
            }
        }
    }

    private fun createDirectory(
        path: String,
        alertDialog: AlertDialog,
        callback: (Boolean) -> Unit
    ) {
        when {
            RemotePath.isRemote(path) -> ensureBackgroundThread {
                try {
                    RemoteBrowser.mkdir(activity, path.trimEnd('/'))
                    activity.runOnUiThread { success(alertDialog) }
                } catch (e: Exception) {
                    activity.runOnUiThread {
                        activity.showErrorToast(e)
                        callback(false)
                    }
                }
            }
            activity.needsStupidWritePermissions(path) -> activity.handleSAFDialog(path) {
                if (!it) {
                    return@handleSAFDialog
                }

                val documentFile = activity.getDocumentFile(path.getParentPath())
                if (documentFile == null) {
                    val error =
                        String.format(activity.getString(R.string.could_not_create_folder), path)
                    activity.showErrorToast(error)
                    callback(false)
                    return@handleSAFDialog
                }
                documentFile.createDirectory(path.getFilenameFromPath())
                success(alertDialog)
            }
            else -> {
                if (File(path).mkdirs()) {
                    success(alertDialog)
                } else {
                    callback(false)
                }
            }
        }
    }

    private fun createFile(path: String, alertDialog: AlertDialog, callback: (Boolean) -> Unit) {
        try {
            when {
                activity.needsStupidWritePermissions(path) -> {
                    activity.handleSAFDialog(path) {
                        if (!it) {
                            return@handleSAFDialog
                        }

                        val documentFile = activity.getDocumentFile(path.getParentPath())
                        if (documentFile == null) {
                            val error = String.format(
                                activity.getString(R.string.could_not_create_file),
                                path
                            )
                            activity.showErrorToast(error)
                            callback(false)
                            return@handleSAFDialog
                        }
                        documentFile.createFile(path.getMimeType(), path.getFilenameFromPath())
                        success(alertDialog)
                    }
                }
                else -> {
                    if (File(path).createNewFile()) {
                        success(alertDialog)
                    } else {
                        callback(false)
                    }
                }
            }
        } catch (exception: IOException) {
            activity.showErrorToast(exception)
            callback(false)
        }
    }

    private fun success(alertDialog: AlertDialog) {
        alertDialog.dismiss()
        callback(true)
    }
}
