package ltechnologies.onionphone.securefilemanager.dialogs

import android.view.View
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogCompressAsBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.openpgp.PgpShieldBridge

class CompressAsDialog(
    val activity: BaseAbstractActivity,
    val path: String,
    val callback: (destinationPath: String, encryptWithPgp: Boolean) -> Unit,
) {
    init {
        val filename = path.getFilenameFromPath()
        val indexOfDot =
            if (filename.contains('.') && !activity.getIsPathDirectory(path)) filename.lastIndexOf(".") else filename.length
        val baseFilename = filename.substring(0, indexOfDot)
        var realPath = path.getParentPath()
        val pgpInstalled = PgpShieldBridge.isInstalled(activity)

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

            encryptPgpSwitch.isEnabled = pgpInstalled
            if (!pgpInstalled) {
                encryptPgpHint.visibility = View.VISIBLE
                encryptPgpHint.setText(R.string.pgpshield_missing)
            }
            encryptPgpSwitch.setOnCheckedChangeListener { _, checked ->
                fileExtension.text = if (checked) ".gpg" else ".zip"
                encryptPgpHint.beVisibleIf(checked && pgpInstalled)
                if (checked && pgpInstalled) {
                    encryptPgpHint.setText(R.string.compress_encrypt_pgp_hint)
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
                val encryptWithPgp = binding.encryptPgpSwitch.isChecked
                when {
                    name.isEmpty() -> activity.toast(R.string.empty_name)
                    !name.isAValidFilename() -> activity.toast(R.string.invalid_name)
                    encryptWithPgp && !pgpInstalled -> activity.toast(R.string.pgpshield_missing)
                    else -> {
                        val extension = if (encryptWithPgp) "gpg" else "zip"
                        val newPath = "$realPath/$name.$extension"
                        if (activity.getDoesFilePathExist(newPath)) {
                            activity.toast(R.string.name_taken)
                            return@setOnClickListener
                        }
                        dismiss()
                        callback(newPath, encryptWithPgp)
                    }
                }
            }
        }
    }
}
