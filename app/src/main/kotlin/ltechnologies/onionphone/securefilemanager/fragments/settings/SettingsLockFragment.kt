package ltechnologies.onionphone.securefilemanager.fragments.settings

import android.os.Bundle

class SettingsLockFragment : SettingsAbstractFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        this.preferenceScreen = this.initScreen(this.initAppLockSet())
    }
}
