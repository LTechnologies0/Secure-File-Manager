package ltechnologies.onionphone.securefilemanager.dialogs

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.activities.MainActivity
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
            RemoteConnectDialog(activity) { root -> openRoot(root) }
            return
        }
        val labels = servers.map { it.label() }.toMutableList()
        labels.add(activity.getString(R.string.remote_connect_new))
        AlertDialog.Builder(activity)
            .setTitle(R.string.remote_saved_servers)
            .setItems(labels.toTypedArray()) { dialog, which ->
                if (which == servers.size) {
                    RemoteConnectDialog(activity) { root -> openRoot(root) }
                } else {
                    val cred = servers[which]
                    val root = RemotePath.root(
                        cred.protocol,
                        cred.host,
                        cred.username,
                        connectionId = cred.id,
                    )
                    openRoot(root)
                }
                dialog.dismiss()
            }
            .setNeutralButton(R.string.remote_delete_server) { _, _ ->
                showDeleteList(servers)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteList(servers: List<RemoteCredentialStore.Credentials>) {
        val labels = servers.map { it.label() }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.remote_delete_server)
            .setItems(labels) { dialog, which ->
                RemoteCredentialStore.delete(activity, servers[which])
                activity.toast(R.string.remote_server_deleted)
                dialog.dismiss()
                showList()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> showList() }
            .show()
    }

    private fun openRoot(root: String) {
        if (callback != null) {
            callback.invoke(root)
        } else {
            activity.startActivity(
                Intent(activity, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_PATH, root)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
        }
    }
}
