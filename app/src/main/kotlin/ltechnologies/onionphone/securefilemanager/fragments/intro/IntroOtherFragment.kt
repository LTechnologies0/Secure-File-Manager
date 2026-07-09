package ltechnologies.onionphone.securefilemanager.fragments.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.FragmentIntroOtherBinding
import ltechnologies.onionphone.securefilemanager.fragments.settings.SettingsBlockScreenshotsFragment
import ltechnologies.onionphone.securefilemanager.fragments.settings.SettingsMediaThumbnailFragment
import ltechnologies.onionphone.securefilemanager.helpers.htmlText

class IntroOtherFragment : Fragment() {

    private var _binding: FragmentIntroOtherBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntroOtherBinding.inflate(inflater, container, false)

        this.childFragmentManager
            .beginTransaction()
            .replace(R.id.settings_disable_screenshots, SettingsBlockScreenshotsFragment())
            .commit()

        this.childFragmentManager
            .beginTransaction()
            .replace(R.id.settings_show_media_thumbnail, SettingsMediaThumbnailFragment())
            .commit()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.featureEncryptedZipText.text =
            htmlText(this.getString(R.string.intro_other_feature_encrypted_zip))
        binding.featureMediaSaving.text = htmlText(this.getString(R.string.feature_media_saving))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): IntroOtherFragment {
            return IntroOtherFragment()
        }
    }
}
