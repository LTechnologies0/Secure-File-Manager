package ltechnologies.onionphone.securefilemanager.fragments.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.activities.LegacyMigrationActivity
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.getDrawableById
import ltechnologies.onionphone.securefilemanager.extensions.isAuthenticatorSet
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.SETTINGS_OPENKEYCHAIN_MANAGE_KEYS
import ltechnologies.onionphone.securefilemanager.helpers.SETTINGS_OPENKEYCHAIN_STATUS
import ltechnologies.onionphone.securefilemanager.helpers.SETTINGS_OPEN_INTERNAL
import ltechnologies.onionphone.securefilemanager.helpers.SETTINGS_REQUIRE_AUTH_FOR_CRYPTO
import ltechnologies.onionphone.securefilemanager.openpgp.PgpShieldBridge

class SettingsEncryptionFragment : SettingsAbstractFragment() {

    private var preferenceOpenKeychainStatus: Preference? = null
    private var preferenceOpenKeychainManageKeys: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = initScreen(
            listOf(
                initOpenKeychainStatus(),
                initOpenKeychainManageKeys(),
                initOpenFilesInternally(),
                initKeepAfterEncryption(),
                initRequireAuthForCrypto(),
                initLegacyMigration(),
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        refreshOpenKeychainStatus()
    }

    private fun refreshOpenKeychainStatus() {
        val installed = PgpShieldBridge.isPackageInstalled(requireContext().packageManager)
        preferenceOpenKeychainStatus?.summary = getString(
            if (installed) R.string.openkeychain_status_installed
            else R.string.openkeychain_status_not_installed,
        )
        preferenceOpenKeychainManageKeys?.isEnabled = installed
    }

    private fun initOpenKeychainStatus(): Preference =
        Preference(requireContext()).apply {
            val activity = requireActivity()
            key = SETTINGS_OPENKEYCHAIN_STATUS
            title = getString(R.string.openkeychain_status_title)
            icon = activity.getDrawableById(R.drawable.ic_folder_key_vector)
            isSelectable = false
            preferenceOpenKeychainStatus = this
        }

    private fun initOpenKeychainManageKeys(): Preference =
        Preference(requireContext()).apply {
            val activity = requireActivity()
            key = SETTINGS_OPENKEYCHAIN_MANAGE_KEYS
            title = getString(R.string.openkeychain_manage_keys)
            icon = activity.getDrawableById(R.drawable.ic_lock_vector)
            preferenceOpenKeychainManageKeys = this
            setOnPreferenceClickListener {
                val intent = PgpShieldBridge.buildManageKeysIntent(requireContext().packageManager)
                if (intent == null) {
                    activity.toast(R.string.pgpshield_missing)
                } else {
                    startActivity(intent)
                }
                true
            }
        }

    private fun initOpenFilesInternally(): SwitchPreferenceCompat =
        SwitchPreferenceCompat(requireContext()).apply {
            val config = requireActivity().config
            key = SETTINGS_OPEN_INTERNAL
            title = getString(R.string.open_files_internally_title)
            summary = getString(R.string.open_files_internally_summary)
            isChecked = config.openFilesInternally
            setOnPreferenceChangeListener { _, newValue ->
                config.openFilesInternally = newValue as Boolean
                true
            }
        }

    private fun initRequireAuthForCrypto(): SwitchPreferenceCompat =
        SwitchPreferenceCompat(requireContext()).apply {
            val activity = requireActivity()
            val config = activity.config
            key = SETTINGS_REQUIRE_AUTH_FOR_CRYPTO
            title = getString(R.string.require_auth_for_crypto_title)
            summary = getString(R.string.require_auth_for_crypto_summary)
            isChecked = config.requireAuthForFileCrypto
            isEnabled = activity.isAuthenticatorSet()
            setOnPreferenceChangeListener { _, newValue ->
                config.requireAuthForFileCrypto = newValue as Boolean
                true
            }
        }

    private fun initLegacyMigration(): Preference =
        Preference(requireContext()).apply {
            title = getString(R.string.legacy_migration_title)
            summary = getString(R.string.legacy_migration_summary_short)
            setOnPreferenceClickListener {
                LegacyMigrationActivity.start(requireActivity() as BaseAbstractActivity)
                true
            }
        }
}
