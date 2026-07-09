package ltechnologies.onionphone.securefilemanager.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogCreateNewBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
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
                    val newPath = "$path/$name"
                    if (activity.getDoesFilePathExist(newPath)) {
                        activity.toast(R.string.name_taken)
                        return@OnClickListener
                    }

                    if (binding.dialogRadioGroup.checkedRadioButtonId == R.id.dialog_radio_directory) {
                        createDirectory(newPath, this) {
                            callback(it)
                        }
                    } else {
                        createFile(newPath, this) {
                            callback(it)
                        }
                    }
                } else {
                    activity.toast(R.string.invalid_name)
                }
            })
        }
    }

    private fun createDirectory(
        path: String,
        alertDialog: AlertDialog,
        callback: (Boolean) -> Unit
    ) {
        when {
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
