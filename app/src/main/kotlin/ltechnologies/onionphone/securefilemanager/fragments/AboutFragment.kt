package ltechnologies.onionphone.securefilemanager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.FragmentAboutBinding
import ltechnologies.onionphone.securefilemanager.extensions.getVersion
import ltechnologies.onionphone.securefilemanager.extensions.openUri

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val GITHUB_URI: String =
            "https://github.com/Secure-File-Manager/Secure-File-Manager"
        private const val CHANGELOG_URI: String = "$GITHUB_URI/blob/master/CHANGELOG.md"
        private const val PRIVACY_POLICY_URI: String = "$GITHUB_URI/blob/master/PRIVACY_POLICY.md"
        private const val LICENSE_URI: String = "$GITHUB_URI/blob/master/LICENSE"
        private const val FAQ_URI: String = "$GITHUB_URI/wiki/Frequently-Asked-Questions"
        private const val CONTRIBUTING_URI: String = "$GITHUB_URI/blob/master/CONTRIBUTING.md"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.aboutTextVersion.text = requireActivity().getVersion()

        binding.aboutLayoutLicense.setOnClickListener {
            requireActivity().openUri(LICENSE_URI)
        }

        binding.aboutLayoutPrivacyPolicy.setOnClickListener {
            requireActivity().openUri(PRIVACY_POLICY_URI)
        }

        binding.aboutLayoutChangelog.setOnClickListener {
            requireActivity().openUri(CHANGELOG_URI)
        }

        binding.aboutLayoutSource.setOnClickListener {
            requireActivity().openUri(GITHUB_URI)
        }

        binding.aboutLayoutFaq.setOnClickListener {
            requireActivity().openUri(FAQ_URI)
        }

        binding.aboutLayoutContribute.setOnClickListener {
            requireActivity().openUri(CONTRIBUTING_URI)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
