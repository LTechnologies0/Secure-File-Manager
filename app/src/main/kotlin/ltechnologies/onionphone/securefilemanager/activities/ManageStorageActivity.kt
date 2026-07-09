package ltechnologies.onionphone.securefilemanager.activities

import android.os.Bundle
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.fragments.settings.SettingsManageStorageFragment


class ManageStorageActivity : BaseAbstractActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_storage)
        this.supportFragmentManager
            .beginTransaction()
            .replace(R.id.frame_layout, SettingsManageStorageFragment())
            .commit()
    }

}
