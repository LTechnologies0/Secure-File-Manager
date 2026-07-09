package ltechnologies.onionphone.securefilemanager.storage

import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
        val ftp = createClient(credentials)
        ftp.defaultTimeout = NETWORK_TIMEOUT_MS
        ftp.connectTimeout = NETWORK_TIMEOUT_MS
        val port = when {
            credentials.ftpsImplicit -> credentials.port.takeIf { it > 0 } ?: 990
            credentials.ftps -> credentials.port.takeIf { it > 0 } ?: 21
            else -> credentials.port.takeIf { it > 0 } ?: 21
        }
        ftp.connect(credentials.host, port)
        if (!ftp.login(credentials.username, credentials.password)) {
            error("ftp login failed")
        }
        if (credentials.ftps && !credentials.ftpsImplicit) {
            (ftp as FTPSClient).execPBSZ(0)
            ftp.execPROT("P")
        }
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
        if (credentials.ftps) {
            FTPSClient(credentials.ftpsImplicit)
        } else {
            FTPClient()
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
