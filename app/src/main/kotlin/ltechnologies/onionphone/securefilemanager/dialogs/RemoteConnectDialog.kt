package ltechnologies.onionphone.securefilemanager.dialogs

import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.extensions.showM3FormDialog
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.storage.FtpStorage
import ltechnologies.onionphone.securefilemanager.storage.HostKeyPrompt
import ltechnologies.onionphone.securefilemanager.storage.RemoteCredentialStore
import ltechnologies.onionphone.securefilemanager.storage.RemotePath
import ltechnologies.onionphone.securefilemanager.storage.SftpStorage
import ltechnologies.onionphone.securefilemanager.storage.WebDavStorage

class RemoteConnectDialog(
    private val activity: BaseAbstractActivity,
    private val callback: (virtualRoot: String) -> Unit,
) {
    private var privateKeyPath: String = ""

    init {
        val padding = activity.resources.getDimensionPixelSize(R.dimen.normal_margin)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        val protocols = arrayOf(
            RemotePath.SFTP_SCHEME.uppercase(),
            RemotePath.FTP_SCHEME.uppercase(),
            RemotePath.WEBDAV_SCHEME.uppercase(),
        )
        val nameInput = EditText(activity).apply {
            hint = activity.getString(R.string.remote_display_name)
        }
        val protocolSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, protocols)
        }
        val hostInput = EditText(activity).apply {
            hint = activity.getString(R.string.remote_host)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val portInput = EditText(activity).apply {
            hint = activity.getString(R.string.remote_port_optional)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val userInput = EditText(activity).apply {
            hint = activity.getString(R.string.remote_username)
        }
        val passwordInput = EditText(activity).apply {
            hint = activity.getString(R.string.remote_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val ftpsCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.remote_ftps)
            visibility = android.view.View.GONE
        }
        val ftpsImplicitCheckbox = CheckBox(activity).apply {
            text = activity.getString(R.string.remote_ftps_implicit)
            visibility = android.view.View.GONE
        }
        val keyButton = Button(activity).apply {
            text = activity.getString(R.string.remote_pick_ssh_key)
            visibility = android.view.View.GONE
            setOnClickListener {
                activity.pickPrivateKey { uri ->
                    privateKeyPath = uri
                    text = activity.getString(R.string.remote_ssh_key_selected)
                }
            }
        }
        protocolSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val isFtp = protocols[position].equals(RemotePath.FTP_SCHEME, true)
                val isSftp = protocols[position].equals(RemotePath.SFTP_SCHEME, true)
                ftpsCheckbox.visibility = if (isFtp) android.view.View.VISIBLE else android.view.View.GONE
                ftpsImplicitCheckbox.visibility =
                    if (isFtp && ftpsCheckbox.isChecked) android.view.View.VISIBLE else android.view.View.GONE
                keyButton.visibility = if (isSftp) android.view.View.VISIBLE else android.view.View.GONE
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        ftpsCheckbox.setOnCheckedChangeListener { _, checked ->
            ftpsImplicitCheckbox.visibility = if (checked) android.view.View.VISIBLE else android.view.View.GONE
        }
        layout.addView(nameInput)
        layout.addView(protocolSpinner)
        layout.addView(hostInput)
        layout.addView(portInput)
        layout.addView(userInput)
        layout.addView(passwordInput)
        layout.addView(ftpsCheckbox)
        layout.addView(ftpsImplicitCheckbox)
        layout.addView(keyButton)

        activity.showM3FormDialog(
            titleId = R.string.remote_connect,
            customView = layout,
            positiveTextId = R.string.ok,
            negativeTextId = R.string.cancel,
            canceledOnTouchOutside = false,
        ) { primary, _, _ ->
            primary.setOnClickListener {
                val displayName = nameInput.text.toString().trim()
                val host = hostInput.text.toString().trim()
                val user = userInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (displayName.isEmpty() || host.isEmpty() || user.isEmpty()) {
                    activity.toast(R.string.remote_missing_fields)
                    return@setOnClickListener
                }
                val protocol = protocols[protocolSpinner.selectedItemPosition].lowercase()
                val port = portInput.text.toString().toIntOrNull() ?: 0
                val credentials = RemoteCredentialStore.Credentials(
                    displayName = displayName,
                    protocol = protocol,
                    host = host,
                    port = port,
                    username = user,
                    password = password,
                    ftps = ftpsCheckbox.isChecked,
                    ftpsImplicit = ftpsImplicitCheckbox.isChecked,
                    privateKeyPath = privateKeyPath,
                )
                val hostKeyPrompt = HostKeyPrompt { fingerprint, onResult ->
                    activity.runOnUiThread {
                        HostKeyConfirmDialog(activity, fingerprint, onResult)
                    }
                }
                ensureBackgroundThread {
                    try {
                        RemoteCredentialStore.save(activity, credentials)
                        when (protocol) {
                            RemotePath.SFTP_SCHEME -> SftpStorage.list(activity, credentials, "/", hostKeyPrompt)
                            RemotePath.FTP_SCHEME -> FtpStorage.list(credentials, "/")
                            RemotePath.WEBDAV_SCHEME -> WebDavStorage.list(credentials, "/")
                        }
                        val root = RemotePath.root(protocol, host, user, connectionId = credentials.id)
                        activity.runOnUiThread {
                            dismiss()
                            callback(root)
                        }
                    } catch (_: Exception) {
                        activity.runOnUiThread {
                            activity.toast(R.string.remote_connect_failed)
                        }
                    }
                }
            }
        }
    }
}
