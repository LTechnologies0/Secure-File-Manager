package ltechnologies.onionphone.securefilemanager.fragments.intro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ltechnologies.onionphone.securefilemanager.activities.SettingsActivity
import ltechnologies.onionphone.securefilemanager.databinding.FragmentIntroLockBinding

class IntroLockFragment : Fragment() {

    private var _binding: FragmentIntroLockBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentIntroLockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.configureButton.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): IntroLockFragment = IntroLockFragment()
    }
}
