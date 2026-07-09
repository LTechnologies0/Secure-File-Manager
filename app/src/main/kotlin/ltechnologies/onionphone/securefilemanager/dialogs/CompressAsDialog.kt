package ltechnologies.onionphone.securefilemanager.dialogs

import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogCompressAsBinding
import ltechnologies.onionphone.securefilemanager.extensions.*

class CompressAsDialog(
    val activity: BaseAbstractActivity,
    val path: String,
    val callback: (destination: String) -> Unit,
) {
    init {
        val filename = path.getFilenameFromPath()
        val indexOfDot =
            if (filename.contains('.') && !activity.getIsPathDirectory(path)) filename.lastIndexOf(".") else filename.length
        val baseFilename = filename.substring(0, indexOfDot)
        var realPath = path.getParentPath()

        val binding = DialogCompressAsBinding.inflate(activity.layoutInflater)
        binding.apply {
            fileName.setText(baseFilename)
            filePath.text = activity.humanizePath(realPath)
            filePath.setOnClickListener {
                FilePickerDialog(
                    activity,
                    realPath,
                    pickFile = false,
                    showFAB = true,
                ) {
                    filePath.text = activity.humanizePath(it)
                    realPath = it
                }
            }
        }

        activity.showM3FormDialog(
            titleId = R.string.compress_as,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
        ) { primary, _, _ ->
            showKeyboard(binding.fileName)
            primary.setOnClickListener {
                val name = binding.fileName.value
                when {
                    name.isEmpty() -> activity.toast(R.string.empty_name)
                    name.isAValidFilename() -> {
                        val newPath = "$realPath/$name.zip"
                        if (activity.getDoesFilePathExist(newPath)) {
                            activity.toast(R.string.name_taken)
                            return@setOnClickListener
                        }
                        dismiss()
                        callback(newPath)
                    }
                    else -> activity.toast(R.string.invalid_name)
                }
            }
        }
    }
}
