package ltechnologies.onionphone.securefilemanager.dialogs

import android.app.Activity
import android.view.View
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.showM3FormDialog

class BetaWarningDialog(activity: Activity) {

    init {
        val view = View.inflate(activity, R.layout.dialog_beta_warning, null)

        activity.showM3FormDialog(
            titleId = 0,
            customView = view,
            positiveTextId = R.string.ok,
            negativeTextId = 0,
        ) { primary, _, _ ->
            primary.setOnClickListener {
                activity.config.isAppBetaWarningShowed = true
                dismiss()
            }
        }
    }

}
