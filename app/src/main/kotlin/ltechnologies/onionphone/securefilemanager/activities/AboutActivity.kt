package ltechnologies.onionphone.securefilemanager.activities

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.mikepenz.aboutlibraries.LibsBuilder
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.ActivityAboutBinding
import ltechnologies.onionphone.securefilemanager.fragments.AboutFragment

// This activity is inspired by the andOTP - https://github.com/andOTP/andOTP
class AboutActivity : BaseAbstractActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.aboutToolbar)
        binding.aboutToolbar.setNavigationOnClickListener { finish() }

        binding.pager.adapter = AboutPageAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.about_tab_about)
                1 -> getString(R.string.about_tab_libraries)
                else -> null
            }
        }.attach()
    }

    private class AboutPageAdapter(fragmentActivity: FragmentActivity) :
        FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AboutFragment()
                1 -> LibsBuilder()
                    .withLicenseShown(true)
                    .withVersionShown(true)
                    .withAboutIconShown(false)
                    .withAboutVersionShown(false)
                    .supportFragment()
                else -> Fragment() // this should not happened
            }
        }
    }
}
