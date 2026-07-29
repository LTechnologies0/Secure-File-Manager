package ltechnologies.onionphone.securefilemanager.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.dialogs.PasswordAuthenticationDialog
import ltechnologies.onionphone.securefilemanager.databinding.ActivityAuthenticateBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.WindowInsetsUtil
import kotlin.properties.Delegates

class AuthenticationActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mAuthenticationSuccessCallback: (success: Boolean) -> Unit
    private lateinit var mBiometricPrompt: BiometricPrompt
    private var mPasswordIsSet by Delegates.notNull<Boolean>()
    private lateinit var binding: ActivityAuthenticateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsUtil.apply(this)

        binding.authenticateCoordinatorLayout.setOnClickListener(this)
        binding.authenticateButton.setOnClickListener(this)
        binding.authenticatePasswordButton.setOnClickListener(this)

        mPasswordIsSet = config.isPasswordSet()
        binding.authenticatePasswordButton.visibility =
            if (mPasswordIsSet) View.VISIBLE else View.GONE
        mAuthenticationSuccessCallback = {
            if (it) {
                config.wasAppProtectionHandled = true
                startUnlockAppService()
                finish()
            }
        }

        mBiometricPrompt = createBiometricPrompt(mAuthenticationSuccessCallback)
        addFlagsSecure()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    quitApp(false)
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        if (config.wasAppProtectionHandled) {
            finish()
        } else if (config.isAppLock) {
            authenticate()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.authenticate_password_button -> showAppPasswordDialog()
            else -> authenticate(preferSystemLock = true)
        }
    }

    private fun authenticate(preferSystemLock: Boolean = true) {
        if (!config.isAppLock) {
            mAuthenticationSuccessCallback.invoke(true)
            return
        }
        when {
            preferSystemLock && isBiometricSet() ->
                mBiometricPrompt.authenticate(createPromptInfo())
            mPasswordIsSet -> showAppPasswordDialog()
            isBiometricSet() -> mBiometricPrompt.authenticate(createPromptInfo())
            else -> {
                toast(R.string.app_lock_summary_on_warning)
                quitApp(false)
            }
        }
    }

    private fun showAppPasswordDialog() {
        PasswordAuthenticationDialog(this, mAuthenticationSuccessCallback) {
            authenticate(preferSystemLock = true)
        }
    }

    private fun createBiometricPrompt(callback: (success: Boolean) -> Unit): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(this)
        return BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Stay on lock screen so the user can retry or use the app-password button.
                    // DEVICE_CREDENTIAL prompts must not set a negative button (API rule).
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        -> callback(false)
                        else -> {
                            toast(errString.toString())
                            callback(false)
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    // allow retry inside the system sheet
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    callback(true)
                }
            },
        )
    }

    private fun createPromptInfo(): BiometricPrompt.PromptInfo {
        // Match OnionVPN: fingerprint/face OR device PIN/pattern/password (private profiles).
        // Must not call setNegativeButtonText when DEVICE_CREDENTIAL is allowed.
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.prompt_info_title))
            .setSubtitle(getString(R.string.prompt_info_subtitle))
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
    }

    companion object {
        fun getIntent(context: Context) =
            Intent(context, AuthenticationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
