package ltechnologies.onionphone.securefilemanager.dialogs

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.extensions.showM3FormDialog
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.storage.TrashManager

class TrashRestoreDialog(
    private val activity: BaseAbstractActivity,
) {
    init {
        ensureBackgroundThread {
            TrashManager.purgeExpired(activity)
            val items = TrashManager.listTrashed(activity)
            activity.runOnUiThread {
                if (items.isEmpty()) {
                    activity.toast(R.string.trash_empty)
                    return@runOnUiThread
                }
                val padding = activity.resources.getDimensionPixelSize(R.dimen.normal_margin)
                val list = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(padding, padding, padding, 0)
                }
                items.forEach { item ->
                    list.addView(
                        TextView(activity).apply {
                            text = "${item.name}\n${item.originalPath}"
                            setPadding(0, padding, 0, padding)
                        },
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
                activity.showM3FormDialog(
                    titleId = R.string.trash_restore_title,
                    customView = list,
                    positiveTextId = 0,
                    negativeTextId = R.string.cancel,
                ) { _, _, _ ->
                    items.forEachIndexed { index, item ->
                        (list.getChildAt(index) as TextView).setOnClickListener {
                            ensureBackgroundThread {
                                val ok = TrashManager.restore(activity, item)
                                activity.runOnUiThread {
                                    activity.toast(if (ok) R.string.trash_restored else R.string.unknown_error_occurred)
                                }
                            }
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}
