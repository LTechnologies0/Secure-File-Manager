package ltechnologies.onionphone.securefilemanager.storage

import android.net.Uri

object RemotePath {
    const val SFTP_SCHEME = "sftp"
    const val FTP_SCHEME = "ftp"
    const val WEBDAV_SCHEME = "webdav"
    private const val QUERY_CONNECTION_ID = "cid"

    fun isRemote(path: String): Boolean =
        path.startsWith("$SFTP_SCHEME://") ||
            path.startsWith("$FTP_SCHEME://") ||
            path.startsWith("$WEBDAV_SCHEME://")

    fun build(
        scheme: String,
        host: String,
        username: String,
        remotePath: String,
        isDirectory: Boolean,
        connectionId: String? = null,
    ): String {
        val authHost = if (username.isNotEmpty()) "$username@$host" else host
        val normalized = remotePath.ifEmpty { "/" }
        val withSlash = if (isDirectory && !normalized.endsWith("/")) "$normalized/" else normalized
        val builder = Uri.Builder()
            .scheme(scheme)
            .encodedAuthority(authHost)
            .encodedPath(withSlash)
        if (!connectionId.isNullOrEmpty()) {
            builder.appendQueryParameter(QUERY_CONNECTION_ID, connectionId)
        }
        return builder.build().toString()
    }

    fun root(
        protocol: String,
        host: String,
        username: String,
        remotePath: String = "/",
        connectionId: String? = null,
    ): String = build(protocol, host, username, remotePath, isDirectory = true, connectionId = connectionId)

    fun parse(path: String): Parsed? {
        if (!isRemote(path)) {
            return null
        }
        val uri = Uri.parse(path)
        val hostPart = uri.host ?: return null
        val scheme = uri.scheme ?: return null
        val (username, host) = splitUserHost(hostPart, uri.userInfo)
        val remoteDir = uri.path?.ifEmpty { "/" } ?: "/"
        return Parsed(
            scheme,
            host,
            username,
            uri.port,
            remoteDir,
            uri.getQueryParameter(QUERY_CONNECTION_ID),
        )
    }

    private fun splitUserHost(hostPart: String, userInfo: String?): Pair<String, String> {
        if (!userInfo.isNullOrEmpty()) {
            return userInfo.substringBefore(':') to hostPart
        }
        if (hostPart.contains("@")) {
            val user = hostPart.substringBefore('@')
            val host = hostPart.substringAfter('@')
            return user to host
        }
        return "" to hostPart
    }

    data class Parsed(
        val scheme: String,
        val host: String,
        val username: String,
        val port: Int,
        val remotePath: String,
        val connectionId: String? = null,
    ) {
        val effectivePort: Int
            get() = when {
                port > 0 -> port
                scheme == SFTP_SCHEME -> 22
                scheme == WEBDAV_SCHEME -> 443
                else -> 21
            }
    }
}
