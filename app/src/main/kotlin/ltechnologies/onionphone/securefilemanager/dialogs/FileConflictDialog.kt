package ltechnologies.onionphone.securefilemanager.dialogs

import android.app.Activity
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.DialogFileConflictBinding
import ltechnologies.onionphone.securefilemanager.extensions.beVisibleIf
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.showM3FormDialog
import ltechnologies.onionphone.securefilemanager.helpers.CONFLICT_KEEP_BOTH
import ltechnologies.onionphone.securefilemanager.helpers.CONFLICT_MERGE
import ltechnologies.onionphone.securefilemanager.helpers.CONFLICT_OVERWRITE
import ltechnologies.onionphone.securefilemanager.helpers.CONFLICT_SKIP
import ltechnologies.onionphone.securefilemanager.models.FileDirItem

class FileConflictDialog(
    val activity: Activity,
    private val fileDirItem: FileDirItem,
    private val showApplyToAllCheckbox: Boolean,
    val callback: (resolution: Int, applyForAll: Boolean) -> Unit
) {
    val binding = DialogFileConflictBinding.inflate(activity.layoutInflater)

    init {
        val stringBase =
            if (fileDirItem.isDirectory) R.string.folder_already_exists else R.string.file_already_exists
        binding.conflictDialogTitle.text =
            String.format(activity.getString(stringBase), fileDirItem.name)
        binding.conflictDialogApplyToAll.isChecked = activity.config.lastConflictApplyToAll
        binding.conflictDialogApplyToAll.beVisibleIf(showApplyToAllCheckbox)
        binding.conflictDialogRadioMerge.beVisibleIf(fileDirItem.isDirectory)

        val resolutionButton = when (activity.config.lastConflictResolution) {
            CONFLICT_OVERWRITE -> binding.conflictDialogRadioOverwrite
            CONFLICT_MERGE -> binding.conflictDialogRadioMerge
            else -> binding.conflictDialogRadioSkip
        }
        resolutionButton.isChecked = true

        activity.showM3FormDialog(
            titleId = 0,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
        ) { primary, _, _ ->
            primary.setOnClickListener { dialogConfirmed() }
        }
    }

    private fun dialogConfirmed() {
        val resolution = when (binding.conflictDialogRadioGroup.checkedRadioButtonId) {
            R.id.conflict_dialog_radio_skip -> CONFLICT_SKIP
            R.id.conflict_dialog_radio_merge -> CONFLICT_MERGE
            R.id.conflict_dialog_radio_keep_both -> CONFLICT_KEEP_BOTH
            else -> CONFLICT_OVERWRITE
        }

        val applyToAll = binding.conflictDialogApplyToAll.isChecked
        activity.config.apply {
            lastConflictApplyToAll = applyToAll
            lastConflictResolution = resolution
        }

        callback(resolution, applyToAll)
    }
}
