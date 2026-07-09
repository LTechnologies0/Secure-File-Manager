package ltechnologies.onionphone.securefilemanager.dialogs

import android.app.Activity
import android.widget.Toast
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.DialogDeleteAppDataBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.htmlText

class DeleteAppDataDialog(activity: Activity) {

    private var previousToast: Toast? = null
    private var appDataDeleteConfirmationCount: Int = 0

    init {
        val binding = DialogDeleteAppDataBinding.inflate(activity.layoutInflater)
        binding.message.text = htmlText(activity.getString(R.string.app_data_clear_summary))

        activity.showM3FormDialog(
            titleId = R.string.app_data_clear_title,
            customView = binding.root,
            positiveTextId = R.string.clear,
            negativeTextId = R.string.cancel,
            canceledOnTouchOutside = false,
        ) { primary, _, _ ->
            primary.setOnClickListener {
                previousToast?.cancel()
                appDataDeleteConfirmationCount++
                when (appDataDeleteConfirmationCount) {
                    1 -> previousToast =
                        activity.toastLong(R.string.app_data_clear_confirmation_1)
                    2 -> previousToast =
                        activity.toastLong(R.string.app_data_clear_confirmation_2)
                    3 -> previousToast =
                        activity.toastLong(R.string.app_data_clear_confirmation_3)
                    4 -> previousToast =
                        activity.toastLong(R.string.app_data_clear_confirmation_4)
                    5 -> try {
                        activity.deleteAppData()
                    } catch (e: Exception) {
                        activity.toast(R.string.app_data_error)
                    }
                }
            }
        }
    }

}
