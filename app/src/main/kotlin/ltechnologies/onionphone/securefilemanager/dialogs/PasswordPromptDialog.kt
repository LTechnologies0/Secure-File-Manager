package ltechnologies.onionphone.securefilemanager.dialogs

import androidx.appcompat.app.AppCompatActivity
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.DialogPasswordPromptBinding
import ltechnologies.onionphone.securefilemanager.extensions.showM3FormDialog
import ltechnologies.onionphone.securefilemanager.extensions.value

class PasswordPromptDialog(
    val activity: AppCompatActivity,
    val title: String?,
    val onCancel: (() -> Unit)? = null,
    val callback: (password: CharArray) -> Unit
) {
    private val binding = DialogPasswordPromptBinding.inflate(activity.layoutInflater)

    init {
        activity.showM3FormDialog(
            titleId = R.string.password_prompt_title,
            titleText = title ?: "",
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
            canceledOnTouchOutside = false,
            onCancel = { onCancel?.invoke() },
        ) { primary, negative, _ ->
            val password = binding.password
            negative.setOnClickListener {
                dismiss()
                onCancel?.invoke()
            }
            primary.setOnClickListener {
                dismiss()
                callback(password.value.toCharArray())
            }
        }
    }

}
