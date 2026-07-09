package ltechnologies.onionphone.securefilemanager.dialogs

import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogRenameItemsBinding
import ltechnologies.onionphone.securefilemanager.extensions.*

class RenameItemsDialog(
    val activity: BaseAbstractActivity,
    val paths: ArrayList<String>,
    val callback: () -> Unit
) {
    init {
        var ignoreClicks = false
        val binding = DialogRenameItemsBinding.inflate(activity.layoutInflater)

        activity.showM3FormDialog(
            titleId = R.string.rename,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
        ) { primary, _, _ ->
            showKeyboard(binding.renameItemsValue)
            primary.setOnClickListener {
                if (ignoreClicks) {
                    return@setOnClickListener
                }

                val valueToAdd = binding.renameItemsValue.text.toString()
                val append =
                    binding.renameItemsRadioGroup.checkedRadioButtonId == R.id.rename_items_radio_append

                if (valueToAdd.isEmpty()) {
                    callback()
                    dismiss()
                    return@setOnClickListener
                }

                if (!valueToAdd.isAValidFilename()) {
                    activity.toast(R.string.invalid_name)
                    return@setOnClickListener
                }

                val validPaths = paths.filter { activity.getDoesFilePathExist(it) }
                val sdFilePath = validPaths.firstOrNull { activity.isPathOnSD(it) }
                    ?: validPaths.firstOrNull()
                if (sdFilePath == null) {
                    activity.toast(R.string.unknown_error_occurred)
                    dismiss()
                    return@setOnClickListener
                }

                activity.handleSAFDialog(sdFilePath) {
                    if (!it) {
                        return@handleSAFDialog
                    }

                    ignoreClicks = true
                    var pathsCnt = validPaths.size
                    for (path in validPaths) {
                        val fullName = path.getFilenameFromPath()
                        var dotAt = fullName.lastIndexOf(".")
                        if (dotAt == -1) {
                            dotAt = fullName.length
                        }

                        val name = fullName.substring(0, dotAt)
                        val extension =
                            if (fullName.contains(".")) ".${fullName.getFilenameExtension()}" else ""

                        val newName = if (append) {
                            "$name$valueToAdd$extension"
                        } else {
                            "$valueToAdd$fullName"
                        }

                        val newPath = "${path.getParentPath()}/$newName"

                        if (activity.getDoesFilePathExist(newPath)) {
                            continue
                        }

                        activity.renameFile(path, newPath) {
                            if (it) {
                                pathsCnt--
                                if (pathsCnt == 0) {
                                    callback()
                                    dismiss()
                                }
                            } else {
                                ignoreClicks = false
                                activity.toast(R.string.unknown_error_occurred)
                                dismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}
