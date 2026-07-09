package ltechnologies.onionphone.securefilemanager.dialogs

import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogRenameItemBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import java.util.*

class RenameItemDialog(
    val activity: BaseAbstractActivity,
    val path: String,
    val callback: (newPath: String) -> Unit
) {
    init {
        var ignoreClicks = false
        val fullName = path.getFilenameFromPath()
        val dotAt = fullName.lastIndexOf(".")
        var name = fullName

        val binding = DialogRenameItemBinding.inflate(activity.layoutInflater).apply {
            if (dotAt > 0 && !activity.getIsPathDirectory(path)) {
                name = fullName.substring(0, dotAt)
                val extension = fullName.substring(dotAt + 1)
                renameItemExtension.setText(extension)
            } else {
                renameItemExtensionLabel.beGone()
                renameItemExtension.beGone()
            }

            renameItemName.setText(name)
            renameItemPath.text = "${activity.humanizePath(path.getParentPath()).trimEnd('/')}/"
        }

        activity.showM3FormDialog(
            titleId = R.string.rename,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
        ) { primary, _, _ ->
            showKeyboard(binding.renameItemName)
            primary.setOnClickListener {
                if (ignoreClicks) {
                    return@setOnClickListener
                }

                var newName = binding.renameItemName.value
                val newExtension = binding.renameItemExtension.value

                if (newName.isEmpty()) {
                    activity.toast(R.string.empty_name)
                    return@setOnClickListener
                }

                if (!newName.isAValidFilename()) {
                    activity.toast(R.string.invalid_name)
                    return@setOnClickListener
                }

                val updatedPaths = ArrayList<String>()
                updatedPaths.add(path)
                if (newExtension.isNotEmpty()) {
                    newName += ".$newExtension"
                }

                if (!activity.getDoesFilePathExist(path)) {
                    activity.toast(
                        String.format(
                            activity.getString(R.string.source_file_doesnt_exist),
                            path
                        )
                    )
                    return@setOnClickListener
                }

                val newPath = "${path.getParentPath()}/$newName"
                if (activity.getDoesFilePathExist(newPath)) {
                    activity.toast(R.string.name_taken)
                    return@setOnClickListener
                }

                updatedPaths.add(newPath)
                ignoreClicks = true
                activity.renameFile(path, newPath) {
                    ignoreClicks = false
                    if (it) {
                        callback(newPath)
                        dismiss()
                    } else {
                        activity.toast(R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }
}
