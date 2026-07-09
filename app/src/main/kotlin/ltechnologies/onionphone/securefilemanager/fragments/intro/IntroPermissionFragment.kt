package ltechnologies.onionphone.securefilemanager.fragments.intro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.github.appintro.SlidePolicy
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.FragmentIntroPermissionBinding
import ltechnologies.onionphone.securefilemanager.extensions.beGoneIf
import ltechnologies.onionphone.securefilemanager.extensions.hasPermission
import ltechnologies.onionphone.securefilemanager.extensions.openSystemSettings
import ltechnologies.onionphone.securefilemanager.extensions.toastLong
import ltechnologies.onionphone.securefilemanager.helpers.PERMISSION_WRITE_STORAGE
import ltechnologies.onionphone.securefilemanager.helpers.getPermissionString

class IntroPermissionFragment : Fragment(), SlidePolicy {

    private var _binding: FragmentIntroPermissionBinding? = null
    private val binding get() = _binding!!

    private var actionOnPermission: ((granted: Boolean) -> Unit)? = { granted: Boolean ->
        setUiVisibility(granted)
        if (!granted) {
            showToast()
        }
    }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        actionOnPermission?.invoke(granted)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentIntroPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.enablePermissionButton.setText(R.string.intro_permission_button_all_files_text)
        }
        handlePermission()
        binding.enablePermissionButton.setOnClickListener { handlePermission() }
        binding.systemSettingsButton.setOnClickListener {
            requireActivity().openSystemSettings()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (isPermissionGranted()) {
            actionOnPermission?.invoke(true)
        }
    }

    override val isPolicyRespected: Boolean get() = isPermissionGranted()

    override fun onUserIllegallyRequestedNextPage() {
        showToast()
    }

    private fun handlePermission() {
        if (isPermissionGranted()) {
            actionOnPermission?.invoke(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pkg = Uri.parse("package:${requireContext().packageName}")
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)
            try {
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermissionLauncher.launch(getPermissionString(PERMISSION_WRITE_STORAGE))
        }
    }

    private fun isPermissionGranted() = requireContext().hasPermission(PERMISSION_WRITE_STORAGE)

    private fun showToast() {
        requireActivity().toastLong(R.string.intro_permission_illegally_request_next_page)
    }

    private fun setUiVisibility(permissionGranted: Boolean) {
        binding.enablePermissionButton.beGoneIf(permissionGranted)
        binding.systemSettingsButton.beGoneIf(permissionGranted)
        binding.summary2Text.beGoneIf(permissionGranted)
        binding.permissionEnabledText.beGoneIf(!permissionGranted)
    }

    companion object {
        fun newInstance(): IntroPermissionFragment = IntroPermissionFragment()
    }
}
