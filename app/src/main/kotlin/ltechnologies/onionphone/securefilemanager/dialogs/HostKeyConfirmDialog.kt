package ltechnologies.onionphone.securefilemanager.dialogs

import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.extensions.showM3MessageDialog

class HostKeyConfirmDialog(
    activity: BaseAbstractActivity,
    fingerprint: String,
    callback: (accepted: Boolean) -> Unit,
) {
    init {
        activity.showM3MessageDialog(
            message = activity.getString(R.string.remote_host_key_prompt, fingerprint),
            titleId = R.string.remote_host_key_title,
            positiveTextId = R.string.remote_host_key_accept,
            onPositive = { callback(true) },
            negativeTextId = R.string.cancel,
            onNegative = { callback(false) },
            cancelable = false,
        )
    }
}
