package ltechnologies.onionphone.securefilemanager.dialogs

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.DialogRadioGroupBinding
import ltechnologies.onionphone.securefilemanager.extensions.onGlobalLayout
import ltechnologies.onionphone.securefilemanager.extensions.showM3FormDialog
import ltechnologies.onionphone.securefilemanager.models.RadioItem
import java.util.*

class RadioGroupDialog(
    val activity: Activity,
    val items: ArrayList<RadioItem>,
    private val checkedItemId: Int = -1,
    private val titleId: Int = 0,
    showOKButton: Boolean = false,
    private val cancelCallback: (() -> Unit)? = null,
    val callback: (newValue: Any) -> Unit
) {
    private val dialog: AlertDialog
    private var wasInit = false
    private var selectedItemId = -1

    init {
        val binding = DialogRadioGroupBinding.inflate(activity.layoutInflater)
        binding.dialogRadioGroup.apply {
            for (i in 0 until items.size) {
                val radioButton = (View.inflate(
                    activity,
                    R.layout.radio_button,
                    null
                ) as RadioButton).apply {
                    text = items[i].title
                    isChecked = items[i].id == checkedItemId
                    id = i
                    setOnClickListener { itemSelected(i) }
                }

                if (items[i].id == checkedItemId) {
                    selectedItemId = i
                }

                addView(
                    radioButton,
                    RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        dialog = activity.showM3FormDialog(
            titleId = titleId,
            customView = binding.root,
            positiveTextId = if (selectedItemId != -1 && showOKButton) R.string.ok else 0,
            negativeTextId = 0,
            onCancel = { cancelCallback?.invoke() },
        ) { primary, _, _ ->
            if (selectedItemId != -1 && showOKButton) {
                primary.setOnClickListener { itemSelected(selectedItemId) }
            }
        }

        if (selectedItemId != -1) {
            binding.dialogRadioHolder.apply {
                onGlobalLayout {
                    scrollY =
                        binding.dialogRadioGroup.findViewById<View>(selectedItemId).bottom - height
                }
            }
        }

        wasInit = true
    }

    private fun itemSelected(checkedId: Int) {
        if (wasInit) {
            callback(items[checkedId].value)
            dialog.dismiss()
        }
    }
}
