package ltechnologies.onionphone.securefilemanager.activities

import android.os.Bundle
import android.view.MenuItem
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.ActivitySettingsBinding
import ltechnologies.onionphone.securefilemanager.fragments.settings.SettingsEncryptionFragment
import ltechnologies.onionphone.securefilemanager.fragments.settings.SettingsFragment

class SettingsActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.settingsToolbar)
        binding.settingsToolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.frame_layout, SettingsFragment())
                .commit()
        }

        supportFragmentManager.addOnBackStackChangedListener { updateToolbar() }
        updateToolbar()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateToolbar() {
        val fragment = supportFragmentManager.findFragmentById(R.id.frame_layout)
        binding.settingsToolbar.title = when (fragment) {
            is SettingsEncryptionFragment -> getString(R.string.encryption_settings_title)
            else -> getString(R.string.settings)
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(
            supportFragmentManager.backStackEntryCount > 0,
        )
    }
}
