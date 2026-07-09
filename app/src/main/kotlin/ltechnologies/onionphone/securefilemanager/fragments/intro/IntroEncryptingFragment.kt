package ltechnologies.onionphone.securefilemanager.fragments.intro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.SettingsActivity
import ltechnologies.onionphone.securefilemanager.databinding.FragmentIntroEncryptingBinding
import ltechnologies.onionphone.securefilemanager.helpers.htmlText

class IntroEncryptingFragment : Fragment() {

    private var _binding: FragmentIntroEncryptingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentIntroEncryptingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.infoText1.text = htmlText(getString(R.string.intro_encrypting_info_1))
        binding.infoText2.text = htmlText(getString(R.string.intro_encrypting_info_2))
        binding.configureButton.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): IntroEncryptingFragment = IntroEncryptingFragment()
    }
}
