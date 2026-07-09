package ltechnologies.onionphone.securefilemanager.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.extensions.showM3MessageDialog

/** M3 card confirmation dialog. */
class ConfirmationDialog(
    activity: Activity,
    message: String = "",
    messageId: Int = R.string.proceed_with_deletion,
    positive: Int = R.string.yes,
    negative: Int = R.string.no,
    val callback: () -> Unit,
) {
    val dialog: AlertDialog

    init {
        val body = if (message.isEmpty()) activity.getString(messageId) else message
        dialog = activity.showM3MessageDialog(
            message = body,
            positiveTextId = positive,
            onPositive = { callback() },
            negativeTextId = negative,
        )
    }
}
