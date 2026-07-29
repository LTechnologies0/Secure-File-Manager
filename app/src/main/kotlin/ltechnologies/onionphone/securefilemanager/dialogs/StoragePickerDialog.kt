package ltechnologies.onionphone.securefilemanager.dialogs

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.activities.RecentActivity
import ltechnologies.onionphone.securefilemanager.activities.TrashActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogRadioGroupBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.HideAction
import ltechnologies.onionphone.securefilemanager.helpers.isUnhide
import ltechnologies.onionphone.securefilemanager.storage.RemoteCredentialStore
import ltechnologies.onionphone.securefilemanager.storage.RemotePath

class StoragePickerDialog(
    val activity: BaseAbstractActivity,
    currPath: String,
    isMovingOperation: Boolean = false,
    hideAction: HideAction = HideAction.NONE,
    val callback: (pickedPath: String) -> Unit,
) {
    private var mDialog: AlertDialog
    private var radioGroup: RadioGroup
    private var nextRemoteId = 1000

    init {
        val layoutParams = RadioGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        val binding = DialogRadioGroupBinding.inflate(activity.layoutInflater)
        radioGroup = binding.dialogRadioGroup
        val basePath = currPath.getBasePath(activity)

        if (!isUnhide(hideAction) && !(isMovingOperation && !activity.isPathOnHidden(currPath))) {
            addOption(
                layoutParams,
                ID_HIDE,
                activity.getString(R.string.hidden),
                basePath == activity.hiddenPath,
            ) { hiddenPicked() }
        }

        addOption(
            layoutParams,
            ID_INTERNAL,
            activity.getString(R.string.internal),
            basePath == activity.internalStoragePath,
        ) { internalPicked() }

        if (activity.hasExternalSDCard()) {
            addOption(
                layoutParams,
                ID_SD,
                activity.getString(R.string.sd_card),
                basePath == activity.sdCardPath,
            ) { sdPicked() }
        }

        for (remote in RemoteCredentialStore.listAll(activity)) {
            val root = RemotePath.root(
                remote.protocol,
                remote.host,
                remote.username,
                connectionId = remote.id,
            )
            addOption(
                layoutParams,
                nextRemoteId++,
                remote.label(),
                RemotePath.isRemote(basePath) && RemotePath.parse(basePath)?.connectionId == remote.id,
            ) {
                remotePicked(root)
            }
        }

        addOption(
            layoutParams,
            ID_REMOTE_NEW,
            activity.getString(R.string.remote_connect_new),
            false,
        ) {
            mDialog.dismiss()
            RemoteServersDialog(activity) { root ->
                callback(root)
            }
        }

        addOption(
            layoutParams,
            ID_TRASH,
            activity.getString(R.string.trash_title),
            false,
        ) {
            mDialog.dismiss()
            activity.startActivity(Intent(activity, TrashActivity::class.java))
        }

        addOption(
            layoutParams,
            ID_RECENT,
            activity.getString(R.string.recent_files_title),
            false,
        ) {
            mDialog.dismiss()
            activity.startActivity(Intent(activity, RecentActivity::class.java))
        }

        mDialog = activity.showM3FormDialog(
            titleId = R.string.select_storage,
            customView = binding.root,
            positiveTextId = 0,
            negativeTextId = 0,
        )
    }

    private fun addOption(
        layoutParams: RadioGroup.LayoutParams,
        id: Int,
        label: String,
        checked: Boolean,
        onClick: () -> Unit,
    ) {
        val button = View.inflate(activity, R.layout.radio_button, null) as RadioButton
        button.apply {
            this.id = id
            text = label
            isChecked = checked
            setOnClickListener { onClick() }
        }
        radioGroup.addView(button, layoutParams)
    }

    private fun internalPicked() {
        mDialog.dismiss()
        callback(activity.internalStoragePath)
    }

    private fun hiddenPicked() {
        mDialog.dismiss()
        callback(activity.hiddenPath)
    }

    private fun sdPicked() {
        mDialog.dismiss()
        callback(activity.sdCardPath)
    }

    private fun remotePicked(root: String) {
        mDialog.dismiss()
        callback(root)
    }

    companion object {
        private const val ID_INTERNAL = 1
        private const val ID_SD = 2
        private const val ID_HIDE = 4
        private const val ID_TRASH = 5
        private const val ID_RECENT = 6
        private const val ID_REMOTE_NEW = 7
    }
}
