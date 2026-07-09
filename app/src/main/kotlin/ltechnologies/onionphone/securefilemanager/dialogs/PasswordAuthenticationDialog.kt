package ltechnologies.onionphone.securefilemanager.dialogs

import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.DialogPasswordAuthenticationBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.crypto.Password
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class PasswordAuthenticationDialog(
    val activity: AppCompatActivity,
    val callbackPositive: (success: Boolean) -> Unit,
    private val callbackNegative: ((success: Boolean) -> Unit)? = null
) : View.OnKeyListener {
    private val binding = DialogPasswordAuthenticationBinding.inflate(activity.layoutInflater)
    private lateinit var passwordLayout: TextInputLayout

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        passwordLayout.error = null
        return false
    }

    init {
        val passwordAuthenticationDialog: PasswordAuthenticationDialog = this

        activity.showM3FormDialog(
            titleId = R.string.prompt_info_title,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = if (callbackNegative != null) R.string.cancel else 0,
            canceledOnTouchOutside = false,
        ) { primary, negative, _ ->
            val alertDialog = this
            passwordLayout = binding.passwordLayout
            val password: EditText = binding.password

            passwordLayout.setOnKeyListener(passwordAuthenticationDialog)
            showKeyboard(password)

            primary.setOnClickListener {
                if (password.value.isEmpty()) {
                    invalidPasswordAction(passwordLayout)
                } else {
                    binding.progressbar.visibility = View.VISIBLE

                    GlobalScope.launch {
                        val passwordMatch = Password().verify(
                            hash = activity.config.passwordHash,
                            password = password.value.toByteArray()
                        )

                        activity.runOnUiThread {
                            binding.progressbar.visibility = View.GONE
                            if (passwordMatch) {
                                positiveCallback(alertDialog)
                            } else {
                                invalidPasswordAction(passwordLayout)
                            }
                        }
                    }
                }
            }

            if (callbackNegative != null) {
                negative.setOnClickListener {
                    negativeCallback(alertDialog)
                }
            }
        }
    }

    private fun positiveCallback(alertDialog: AlertDialog) {
        alertDialog.dismiss()
        callbackPositive(true)
    }

    private fun negativeCallback(alertDialog: AlertDialog) {
        alertDialog.dismiss()
        callbackNegative?.invoke(true)
    }

    private fun invalidPasswordAction(passwordLayout: TextInputLayout) {
        passwordLayout.setError(activity, R.string.invalid_password)
    }

}
