package ltechnologies.onionphone.securefilemanager.fragments.settings

import android.os.Bundle
import androidx.preference.Preference

class SettingsFragment : SettingsAbstractFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = this.initScreen()

        this.initCategoryGeneral(
            screen,
            listOf(
                this.initTheme(),
                this.initFavorites(),
                this.initDateTimeFormat(),
                this.initChangeHourFormat(),
                this.initFontSize(),
                this.initShowThumbnailPreview(),
                this.initMediaThumbnailClear(),
                this.initAppSystemSettings(),
            ),
        )

        this.initCategoryFileOperations(
            screen,
            listOf(
                this.initKeepLastModified(),
                this.initEncryptionSettings(),
                this.initRemoteStorage(),
                this.initRsyncStatus(),
                this.initTrashRestore(),
                this.initOpenTrash(),
            ),
        )

        this.initCategorySecurity(
            screen,
            mutableListOf<Preference>(
                this.initBlockScreenshots(),
                this.initDocumentsProvider(),
            )
                .apply {
                    addAll(initAppLockSet())
                }
        )

        this.preferenceScreen = screen
    }

}
