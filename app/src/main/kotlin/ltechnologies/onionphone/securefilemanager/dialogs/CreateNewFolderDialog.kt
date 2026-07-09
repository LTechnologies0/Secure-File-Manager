package ltechnologies.onionphone.securefilemanager.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogCreateNewFolderBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.storage.RemoteBrowser
import ltechnologies.onionphone.securefilemanager.storage.RemotePath
import java.io.File

class CreateNewFolderDialog(
    val activity: BaseAbstractActivity,
    val path: String,
    val callback: (path: String) -> Unit
) {
    init {
        val binding = DialogCreateNewFolderBinding.inflate(activity.layoutInflater)
        binding.folderPath.text = "${activity.humanizePath(path).trimEnd('/')}/"

        activity.showM3FormDialog(
            titleId = R.string.create_new_folder,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
        ) { primary, _, _ ->
            showKeyboard(binding.folderName)
            primary.setOnClickListener(View.OnClickListener {
                val name = binding.folderName.value
                when {
                    name.isEmpty() -> activity.toast(R.string.empty_name)
                    name.isAValidFilename() -> {
                        val newPath = "$path/$name"
                        if (!RemotePath.isRemote(path) && File(newPath).exists()) {
                            activity.toast(R.string.name_taken)
                            return@OnClickListener
                        }
                        createFolder(newPath, this)
                    }
                    else -> activity.toast(R.string.invalid_name)
                }
            })
        }
    }

    private fun createFolder(path: String, alertDialog: AlertDialog) {
        try {
            when {
                RemotePath.isRemote(path) -> ensureBackgroundThread {
                    try {
                        RemoteBrowser.mkdir(activity, path)
                        activity.runOnUiThread { sendSuccess(alertDialog, path) }
                    } catch (e: Exception) {
                        activity.runOnUiThread { activity.showErrorToast(e) }
                    }
                }
                activity.needsStupidWritePermissions(path) -> activity.handleSAFDialog(path) {
                    if (it) {
                        try {
                            val documentFile = activity.getDocumentFile(path.getParentPath())
                            if (documentFile?.createDirectory(path.getFilenameFromPath()) != null) {
                                sendSuccess(alertDialog, path)
                            } else {
                                activity.toast(R.string.unknown_error_occurred)
                            }
                        } catch (e: SecurityException) {
                            activity.showErrorToast(e)
                        }
                    }
                }
                File(path).mkdirs() -> sendSuccess(alertDialog, path)
                else -> activity.toast(R.string.unknown_error_occurred)
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun sendSuccess(alertDialog: AlertDialog, path: String) {
        callback(path.trimEnd('/'))
        alertDialog.dismiss()
    }
}
