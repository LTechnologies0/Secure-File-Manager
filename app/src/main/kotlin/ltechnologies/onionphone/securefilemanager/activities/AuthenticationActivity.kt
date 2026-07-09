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

        mPasswordIsSet = config.isPasswordSet()
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
        authenticate()
    }

    private fun authenticate() {
        if (!config.isAppLock) {
            mAuthenticationSuccessCallback.invoke(true)
            return
        }
        when {
            isBiometricSet() -> mBiometricPrompt.authenticate(createPromptInfo())
            mPasswordIsSet -> PasswordAuthenticationDialog(this, mAuthenticationSuccessCallback) {}
            else -> {
                toast(R.string.app_lock_summary_on_warning)
                quitApp(false)
            }
        }
    }

    private fun createBiometricPrompt(callback: (success: Boolean) -> Unit): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(this)
        val activity = this
        return BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when {
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && mPasswordIsSet ->
                            PasswordAuthenticationDialog(activity, callback) { authenticate() }
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && !mPasswordIsSet ->
                            quitApp(false)
                        else -> callback(false)
                    }
                }

                override fun onAuthenticationFailed() {
                    // allow retry
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    callback(true)
                }
            },
        )
    }

    private fun createPromptInfo(): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.prompt_info_title))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .setNegativeButtonText(
                getString(
                    if (mPasswordIsSet) R.string.prompt_info_use_app_password else R.string.cancel,
                ),
            )
            .build()

    companion object {
        fun getIntent(context: Context) =
            Intent(context, AuthenticationActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
