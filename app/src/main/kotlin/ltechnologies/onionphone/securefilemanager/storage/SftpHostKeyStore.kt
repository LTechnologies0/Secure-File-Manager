package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import ltechnologies.onionphone.securefilemanager.extensions.getEncryptedSharedPrefs
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

object SftpHostKeyStore {
    private const val PREFIX = "sftp_hostkey_v1|"

    fun fingerprint(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey.encoded)
        return Base64.getEncoder().encodeToString(hash)
    }

    fun hostPortKey(host: String, port: Int) = "$PREFIX$host|$port"

    fun get(context: Context, host: String, port: Int): String? =
        context.getEncryptedSharedPrefs().getString(hostPortKey(host, port), null)

    fun save(context: Context, host: String, port: Int, fingerprint: String) {
        context.getEncryptedSharedPrefs().edit()
            .putString(hostPortKey(host, port), fingerprint)
            .apply()
    }

    fun delete(context: Context, host: String, port: Int) {
        context.getEncryptedSharedPrefs().edit()
            .remove(hostPortKey(host, port))
            .apply()
    }
}
