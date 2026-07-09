package ltechnologies.onionphone.securefilemanager.dialogs

import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.storage.RemoteCredentialStore
import ltechnologies.onionphone.securefilemanager.storage.RemotePath

class RemoteServersDialog(
    private val activity: BaseAbstractActivity,
    private val callback: ((virtualRoot: String) -> Unit)? = null,
) {
    init {
        showList()
    }

    private fun showList() {
        val servers = RemoteCredentialStore.listAll(activity)
        if (servers.isEmpty()) {
            activity.toast(R.string.remote_no_saved_servers)
            RemoteConnectDialog(activity, callback ?: {})
            return
        }
        val labels = servers.map { it.label() }.toMutableList()
        labels.add(activity.getString(R.string.remote_connect_new))
        AlertDialog.Builder(activity)
            .setTitle(R.string.remote_saved_servers)
            .setItems(labels.toTypedArray()) { dialog, which ->
                if (which == servers.size) {
                    RemoteConnectDialog(activity, callback ?: {})
                } else {
                    val cred = servers[which]
                    val root = RemotePath.root(
                        cred.protocol,
                        cred.host,
                        cred.username,
                        connectionId = cred.id,
                    )
                    callback?.invoke(root) ?: activity.toast(R.string.remote_server_selected)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
