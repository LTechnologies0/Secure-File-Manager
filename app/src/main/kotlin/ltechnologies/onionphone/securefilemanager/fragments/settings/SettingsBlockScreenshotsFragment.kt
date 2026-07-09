package ltechnologies.onionphone.securefilemanager.fragments.settings

import android.os.Bundle

class SettingsBlockScreenshotsFragment : SettingsAbstractFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        this.preferenceScreen = this.initScreen(listOf(this.initBlockScreenshots()))
    }
}
