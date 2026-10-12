package ltechnologies.onionphone.securefilemanager.storage

import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object FtpStorage {
    private const val REMOTE_LIST_CAP = 5_000
    private const val NETWORK_TIMEOUT_MS = 30_000

    fun list(credentials: RemoteCredentialStore.Credentials, remotePath: String): List<FileDirItem> =
        withClient(credentials) { ftp ->
            val dir = normalizeDir(remotePath)
            ftp.listFiles(dir)?.asSequence()
                ?.mapNotNull { it.toFileDirItem(credentials, dir) }
                ?.take(REMOTE_LIST_CAP)
                ?.toList()
                ?: emptyList()
        }

    fun download(
        credentials: RemoteCredentialStore.Credentials,
        remoteFilePath: String,
        destination: File,
    ) = withClient(credentials) { ftp ->
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { out ->
            if (!ftp.retrieveFile(remoteFilePath, out)) {
                error("ftp download failed")
            }
        }
    }

    fun upload(
        credentials: RemoteCredentialStore.Credentials,
        localFile: File,
        remoteFilePath: String,
    ) = withClient(credentials) { ftp ->
        FileInputStream(localFile).use { input ->
            if (!ftp.storeFile(remoteFilePath, input)) {
                error("ftp upload failed")
            }
        }
    }

    fun delete(
        credentials: RemoteCredentialStore.Credentials,
        remotePath: String,
        isDirectory: Boolean,
    ) = withClient(credentials) { ftp ->
        val ok = if (isDirectory) {
            ftp.removeDirectory(remotePath.trimEnd('/'))
        } else {
            ftp.deleteFile(remotePath)
        }
        if (!ok) {
            error("ftp delete failed")
        }
    }

    fun rename(
        credentials: RemoteCredentialStore.Credentials,
        oldPath: String,
        newPath: String,
    ) = withClient(credentials) { ftp ->
        if (!ftp.rename(oldPath, newPath)) {
            error("ftp rename failed")
        }
    }

    fun mkdir(
        credentials: RemoteCredentialStore.Credentials,
        remotePath: String,
    ) = withClient(credentials) { ftp ->
        if (!ftp.makeDirectory(remotePath.trimEnd('/'))) {
            error("ftp mkdir failed")
        }
    }

    private fun <T> withClient(
        credentials: RemoteCredentialStore.Credentials,
        block: (FTPClient) -> T,
    ): T {
        require(credentials.ftps || credentials.ftpsImplicit) {
            "Cleartext FTP is not allowed; enable FTPS (explicit or implicit)"
        }
        val ftp = createClient(credentials)
        ftp.defaultTimeout = NETWORK_TIMEOUT_MS
        ftp.connectTimeout = NETWORK_TIMEOUT_MS
        ftp.setProxy(OnionVpnProxy.socks())
        val port = when {
            credentials.ftpsImplicit -> credentials.port.takeIf { it > 0 } ?: 990
            credentials.ftps -> credentials.port.takeIf { it > 0 } ?: 21
            else -> credentials.port.takeIf { it > 0 } ?: 21
        }
        ftp.connect(credentials.host, port)
        if (!ftp.login(credentials.username, credentials.password)) {
            error("ftp login failed")
        }
        applyDataChannelProtection(ftp as FTPSClient)
        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)
        return try {
            block(ftp)
        } finally {
            runCatching { ftp.logout() }
            runCatching { ftp.disconnect() }
        }
    }

    private fun createClient(credentials: RemoteCredentialStore.Credentials): FTPClient =
        if (credentials.ftps || credentials.ftpsImplicit) {
            FTPSClient(credentials.ftpsImplicit).apply {
                // Default platform trust — before connect — so implicit FTPS cannot skip MITM checks.
                trustManager = defaultX509TrustManager()
                isEndpointCheckingEnabled = true
            }
        } else {
            error("Cleartext FTP is not allowed; enable FTPS")
        }

    /** PBSZ 0 + PROT P on the data channel, including implicit FTPS (port 990). */
    internal fun applyDataChannelProtection(ftp: FTPSClient) {
        applyDataChannelProtection(ftp::execPBSZ, ftp::execPROT)
    }

    internal fun applyDataChannelProtection(pbsz: (Long) -> Unit, prot: (String) -> Unit) {
        pbsz(0)
        prot("P")
    }

    private fun defaultX509TrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private fun normalizeDir(path: String): String =
        if (path.endsWith("/")) path else "$path/"

    private fun FTPFile.toFileDirItem(
        credentials: RemoteCredentialStore.Credentials,
        parentDir: String,
    ): FileDirItem? {
        val entryName = name ?: return null
        if (entryName == "." || entryName == "..") {
            return null
        }
        val childRemote = parentDir.trimEnd('/') + "/" + entryName
        val virtualPath = RemotePath.build(
            RemotePath.FTP_SCHEME,
            credentials.host,
            credentials.username,
            childRemote,
            isDirectory,
            connectionId = credentials.id,
        )
        return FileDirItem(
            path = virtualPath,
            name = entryName,
            isDirectory = isDirectory,
            children = 0,
            size = size,
        )
    }
}
