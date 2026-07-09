package ltechnologies.onionphone.securefilemanager.dialogs

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.DialogPasswordSetupBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.crypto.Password
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class PasswordSetupDialog(
    val activity: Activity,
    val callback: () -> Unit = {}
) : View.OnKeyListener {
    private val binding = DialogPasswordSetupBinding.inflate(activity.layoutInflater)
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordAgainLayout: TextInputLayout

    /**
     * ^.*              : Start
     * (?=.{8,})        : Length
     * (?=.*[a-zA-Z])   : Letters
     * (?=.*\d)         : Digits
     * .*$              : End
     */
    private val passwordPattern =
        "^.*(?=.{8,})(?=.*[a-zA-Z])(?=.*\\d).*$".toRegex()

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        this.setPasswordLayoutWithoutError()
        return false
    }

    init {
        val passwordSetupDialog: PasswordSetupDialog = this

        activity.showM3FormDialog(
            titleId = R.string.password_setup_title,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
            neutralTextId = R.string.password_remove,
            canceledOnTouchOutside = false,
        ) { primary, _, neutral ->
            val alertDialog = this
            passwordLayout = binding.passwordLayout
            passwordAgainLayout = binding.passwordAgainLayout
            val password: EditText = binding.password
            val passwordAgain: EditText = binding.passwordAgain

            password.setOnKeyListener(passwordSetupDialog)
            passwordAgain.setOnKeyListener(passwordSetupDialog)
            showKeyboard(password)

            neutral.setOnClickListener {
                activity.config.passwordRemove()
                alertDialog.dismiss()
                callback.invoke()
            }

            primary.setOnClickListener {
                when {
                    !passwordPattern.matches(password.value) ->
                        passwordLayout.setError(activity, R.string.invalid_password_pattern)
                    password.value != passwordAgain.value ->
                        passwordAgainLayout.setError(activity, R.string.password_not_match)
                    else -> {
                        setPasswordLayoutWithoutError()
                        binding.progressbar.visibility = View.VISIBLE

                        GlobalScope.launch {
                            activity.config.passwordHash =
                                Password().hash(password.value.toByteArray())
                            activity.runOnUiThread {
                                alertDialog.dismiss()
                                callback.invoke()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setPasswordLayoutWithoutError() {
        this.passwordLayout.error = null
        this.passwordAgainLayout.error = null
    }

}
