package ltechnologies.onionphone.securefilemanager.dialogs

import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogChangeSortingBinding
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.showM3FormDialog
import ltechnologies.onionphone.securefilemanager.helpers.*

class ChangeSortingDialog(
    val activity: BaseAbstractActivity,
    val path: String = "",
    val callback: () -> Unit
) {
    private var currSorting = 0
    private var config = activity.config
    private val binding = DialogChangeSortingBinding.inflate(activity.layoutInflater)

    init {
        binding.sortingDialogUseForThisFolder.isChecked = config.hasCustomSorting(path)

        currSorting = config.getFolderSorting(path)
        setupSortRadio()
        setupOrderRadio()

        activity.showM3FormDialog(
            titleId = R.string.sort_by,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
        ) { primary, _, _ ->
            primary.setOnClickListener {
                dialogConfirmed()
                dismiss()
            }
        }
    }

    private fun setupSortRadio() {
        val sortBtn = when {
            currSorting and SORT_BY_SIZE != 0 -> binding.sortingDialogRadioSize
            currSorting and SORT_BY_DATE_MODIFIED != 0 -> binding.sortingDialogRadioLastModified
            currSorting and SORT_BY_EXTENSION != 0 -> binding.sortingDialogRadioExtension
            else -> binding.sortingDialogRadioName
        }
        sortBtn.isChecked = true
    }

    private fun setupOrderRadio() {
        var orderBtn = binding.sortingDialogRadioAscending

        if (currSorting and SORT_DESCENDING != 0) {
            orderBtn = binding.sortingDialogRadioDescending
        }
        orderBtn.isChecked = true
    }

    private fun dialogConfirmed() {
        var sorting = when (binding.sortingDialogRadioSorting.checkedRadioButtonId) {
            R.id.sorting_dialog_radio_name -> SORT_BY_NAME
            R.id.sorting_dialog_radio_size -> SORT_BY_SIZE
            R.id.sorting_dialog_radio_last_modified -> SORT_BY_DATE_MODIFIED
            else -> SORT_BY_EXTENSION
        }

        if (binding.sortingDialogRadioOrder.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting = sorting or SORT_DESCENDING
        }

        if (binding.sortingDialogUseForThisFolder.isChecked) {
            config.saveCustomSorting(path, sorting)
        } else {
            config.removeCustomSorting(path)
            config.sorting = sorting
        }
        callback()
    }
}
