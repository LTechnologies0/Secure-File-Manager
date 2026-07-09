package ltechnologies.onionphone.securefilemanager.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.DialogWritePermissionBinding
import ltechnologies.onionphone.securefilemanager.extensions.showM3FormDialog

class WritePermissionDialog(activity: Activity, val callback: () -> Unit) {
    var dialog: AlertDialog

    init {
        val binding = DialogWritePermissionBinding.inflate(activity.layoutInflater)

        val glide = Glide.with(activity)
        val crossFade = DrawableTransitionOptions.withCrossFade()
        glide.load(R.drawable.img_write_storage).transition(crossFade).into(binding.writePermissionsDialogImage)
        glide.load(R.drawable.img_write_storage_sd).transition(crossFade).into(binding.writePermissionsDialogImageSd)

        dialog = activity.showM3FormDialog(
            titleId = R.string.confirm_storage_access_title,
            customView = binding.root,
            positiveTextId = R.string.ok,
            negativeTextId = 0,
            onCancel = {
                BaseAbstractActivity.funAfterSAFPermission?.invoke(false)
                BaseAbstractActivity.funAfterSAFPermission = null
            },
        ) { primary, _, _ ->
            primary.setOnClickListener { dialogConfirmed() }
        }
    }

    private fun dialogConfirmed() {
        dialog.dismiss()
        callback()
    }
}
