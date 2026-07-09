package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import ltechnologies.onionphone.securefilemanager.extensions.getEncryptedSharedPrefs
import java.util.UUID

object RemoteCredentialStore {
    private const val CRED_PREFIX = "remote_creds_v2|"
    private const val LEGACY_PREFIX = "remote_creds_v1|"

    data class Credentials(
        val id: String = UUID.randomUUID().toString(),
        val displayName: String,
        val protocol: String,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val ftps: Boolean = false,
        val ftpsImplicit: Boolean = false,
        val privateKeyPath: String = "",
    ) {
        fun label(): String = displayName.ifBlank { "$protocol $username@$host" }
    }

    private fun key(id: String) = "$CRED_PREFIX$id"

    fun save(context: Context, credentials: Credentials) {
        val port = credentials.port.takeIf { it > 0 } ?: defaultPort(credentials.protocol)
        val blob = buildString {
            append(credentials.displayName).append('\n')
            append(credentials.protocol).append('\n')
            append(credentials.host).append('\n')
            append(port).append('\n')
            append(credentials.username).append('\n')
            append(credentials.password).append('\n')
            append(credentials.ftps).append('\n')
            append(credentials.ftpsImplicit).append('\n')
            append(credentials.privateKeyPath)
        }
        context.getEncryptedSharedPrefs().edit().putString(key(credentials.id), blob).apply()
    }

    fun load(context: Context, id: String): Credentials? {
        val blob = context.getEncryptedSharedPrefs().getString(key(id), null) ?: return null
        return parseBlob(blob, id)
    }

    fun loadByEndpoint(context: Context, protocol: String, host: String, username: String): Credentials? =
        listAll(context).firstOrNull {
            it.protocol == protocol && it.host == host && it.username == username
        }

    fun listAll(context: Context): List<Credentials> {
        val prefs = context.getEncryptedSharedPrefs()
        return prefs.all.entries.asSequence()
            .mapNotNull { (key, value) ->
                when {
                    key.startsWith(CRED_PREFIX) ->
                        parseBlob(value as? String, key.removePrefix(CRED_PREFIX))
                    key.startsWith(LEGACY_PREFIX) -> migrateLegacy(key, value as? String)
                    else -> null
                }
            }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    fun delete(context: Context, credentials: Credentials) {
        context.getEncryptedSharedPrefs().edit().remove(key(credentials.id)).apply()
        if (credentials.protocol == RemotePath.SFTP_SCHEME) {
            val port = credentials.port.takeIf { it > 0 } ?: 22
            SftpHostKeyStore.delete(context, credentials.host, port)
        }
    }

    private fun migrateLegacy(key: String, blob: String?): Credentials? {
        if (blob == null) {
            return null
        }
        val parts = blob.split('\n')
        if (parts.size < 5) {
            return null
        }
        val protocol = parts[0]
        val host = parts[1]
        val username = parts[3]
        val id = key.removePrefix(LEGACY_PREFIX).replace('|', '_')
        return Credentials(
            id = id,
            displayName = "$protocol $username@$host",
            protocol = protocol,
            host = host,
            port = parts[2].toIntOrNull() ?: 0,
            username = username,
            password = parts[4],
            ftps = parts.getOrNull(5)?.toBooleanStrictOrNull() ?: false,
            ftpsImplicit = parts.getOrNull(6)?.toBooleanStrictOrNull() ?: false,
            privateKeyPath = parts.getOrNull(7).orEmpty(),
        )
    }

    private fun defaultPort(protocol: String): Int = when (protocol) {
        RemotePath.SFTP_SCHEME -> 22
        RemotePath.WEBDAV_SCHEME -> 443
        else -> 21
    }

    private fun parseBlob(blob: String?, id: String): Credentials? {
        if (blob == null) {
            return null
        }
        val parts = blob.split('\n')
        if (parts.size < 6) {
            return null
        }
        return Credentials(
            id = id,
            displayName = parts[0],
            protocol = parts[1],
            host = parts[2],
            port = parts[3].toIntOrNull() ?: 0,
            username = parts[4],
            password = parts[5],
            ftps = parts.getOrNull(6)?.toBooleanStrictOrNull() ?: false,
            ftpsImplicit = parts.getOrNull(7)?.toBooleanStrictOrNull() ?: false,
            privateKeyPath = parts.getOrNull(8).orEmpty(),
        )
    }
}
