package ltechnologies.onionphone.securefilemanager.dialogs

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
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
                showItems(items)
            }
        }
    }

    private fun showItems(items: List<TrashManager.TrashedItem>) {
        val padding = activity.resources.getDimensionPixelSize(R.dimen.normal_margin)
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        lateinit var dialog: AlertDialog
        items.forEach { item ->
            list.addView(
                MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "${item.name}\n${item.originalPath}"
                    isAllCaps = false
                    textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
                    setOnClickListener {
                        ensureBackgroundThread {
                            val ok = TrashManager.restore(activity, item)
                            activity.runOnUiThread {
                                activity.toast(
                                    if (ok) R.string.trash_restored else R.string.unknown_error_occurred,
                                )
                            }
                        }
                        dialog.dismiss()
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = padding / 2
                },
            )
        }
        list.addView(
            MaterialTextView(activity).apply {
                setText(R.string.trash_restore_summary)
                setPadding(0, padding, 0, padding)
            },
        )
        dialog = activity.showM3FormDialog(
            titleId = R.string.trash_restore_title,
            customView = list,
            positiveTextId = 0,
            negativeTextId = R.string.cancel,
        )
    }
}
