package ltechnologies.onionphone.securefilemanager.extensions

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.Window
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.DialogCardM3Binding

/** ponytail: M3 card shell — migrate dialogs here from setupDialogStuff incrementally */
fun Activity.showM3MessageDialog(
    message: String,
    positiveTextId: Int,
    onPositive: () -> Unit,
    titleId: Int = 0,
    titleText: String = "",
    negativeTextId: Int = 0,
    onNegative: (() -> Unit)? = null,
    cancelable: Boolean = true,
): AlertDialog {
    if (isDestroyed || isFinishing) {
        return AlertDialog.Builder(this).create()
    }

    val binding = DialogCardM3Binding.inflate(layoutInflater)
    when {
        titleText.isNotEmpty() -> binding.dialogCardTitle.text = titleText
        titleId != 0 -> binding.dialogCardTitle.setText(titleId)
        else -> binding.dialogCardTitle.visibility = View.GONE
    }
    binding.dialogCardBody.text = message
    binding.dialogCardCustom.visibility = View.GONE

    val dialog = AlertDialog.Builder(this).create()
    if (positiveTextId != 0) {
        binding.dialogCardBtnPrimary.setText(positiveTextId)
        binding.dialogCardBtnPrimary.setOnClickListener {
            dialog.dismiss()
            onPositive()
        }
    } else {
        binding.dialogCardBtnPrimary.visibility = View.GONE
    }

    if (negativeTextId != 0) {
        binding.dialogCardBtnSecondary.visibility = View.VISIBLE
        binding.dialogCardBtnSecondary.setText(negativeTextId)
        binding.dialogCardBtnSecondary.setOnClickListener {
            dialog.dismiss()
            onNegative?.invoke()
        }
    } else {
        binding.dialogCardBtnSecondary.visibility = View.GONE
    }
    binding.dialogCardBtnNeutral.visibility = View.GONE

    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setView(binding.root)
    dialog.setCancelable(cancelable)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
    return dialog
}

fun Activity.showM3FormDialog(
    titleId: Int = 0,
    customView: View,
    positiveTextId: Int = R.string.ok,
    negativeTextId: Int = 0,
    neutralTextId: Int = 0,
    titleText: String = "",
    canceledOnTouchOutside: Boolean = true,
    cancelable: Boolean = true,
    onCancel: (() -> Unit)? = null,
    callback: (AlertDialog.(primaryButton: View, negativeButton: View, neutralButton: View) -> Unit)? = null,
): AlertDialog {
    if (isDestroyed || isFinishing) {
        return AlertDialog.Builder(this).create()
    }

    val binding = DialogCardM3Binding.inflate(layoutInflater)
    when {
        titleText.isNotEmpty() -> binding.dialogCardTitle.text = titleText
        titleId != 0 -> binding.dialogCardTitle.setText(titleId)
        else -> binding.dialogCardTitle.visibility = View.GONE
    }
    binding.dialogCardBody.visibility = View.GONE
    binding.dialogCardCustom.visibility = View.VISIBLE
    binding.dialogCardCustom.removeAllViews()
    binding.dialogCardCustom.addView(customView)

    val dialog = AlertDialog.Builder(this).create()
    if (positiveTextId != 0) {
        binding.dialogCardBtnPrimary.setText(positiveTextId)
    } else {
        binding.dialogCardBtnPrimary.visibility = View.GONE
    }
    if (negativeTextId != 0) {
        binding.dialogCardBtnSecondary.visibility = View.VISIBLE
        binding.dialogCardBtnSecondary.setText(negativeTextId)
        binding.dialogCardBtnSecondary.setOnClickListener { dialog.dismiss() }
    } else {
        binding.dialogCardBtnSecondary.visibility = View.GONE
    }
    if (neutralTextId != 0) {
        binding.dialogCardBtnNeutral.visibility = View.VISIBLE
        binding.dialogCardBtnNeutral.setText(neutralTextId)
        binding.dialogCardBtnNeutral.setOnClickListener { dialog.dismiss() }
    } else {
        binding.dialogCardBtnNeutral.visibility = View.GONE
    }

    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setView(binding.root)
    dialog.setCancelable(cancelable)
    dialog.setCanceledOnTouchOutside(canceledOnTouchOutside)
    onCancel?.let { dialog.setOnCancelListener { it() } }
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.show()
    callback?.invoke(dialog, binding.dialogCardBtnPrimary, binding.dialogCardBtnSecondary, binding.dialogCardBtnNeutral)
    return dialog
}
