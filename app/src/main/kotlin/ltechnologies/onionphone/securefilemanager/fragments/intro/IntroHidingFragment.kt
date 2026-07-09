package ltechnologies.onionphone.securefilemanager.fragments.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.FragmentIntroHidingBinding
import ltechnologies.onionphone.securefilemanager.helpers.htmlText

class IntroHidingFragment : Fragment() {

    private var _binding: FragmentIntroHidingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntroHidingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.infoText.text = htmlText(this.getString(R.string.intro_hiding_info))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): IntroHidingFragment {
            return IntroHidingFragment()
        }
    }
}
