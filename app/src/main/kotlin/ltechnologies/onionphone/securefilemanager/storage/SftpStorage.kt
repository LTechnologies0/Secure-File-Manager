package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import java.io.File

object SftpStorage {
    private const val REMOTE_LIST_CAP = 5_000
    private const val CONNECT_TIMEOUT_MS = 30_000

    fun list(
        context: Context,
        credentials: RemoteCredentialStore.Credentials,
        remotePath: String,
        hostKeyPrompt: HostKeyPrompt? = null,
    ): List<FileDirItem> =
        withClient(context, credentials, hostKeyPrompt) { sftp ->
            val dir = normalizeDir(remotePath)
            sftp.ls(dir).asSequence()
                .mapNotNull { it.toFileDirItem(credentials, dir) }
                .take(REMOTE_LIST_CAP)
                .toList()
        }

    fun download(
        context: Context,
        credentials: RemoteCredentialStore.Credentials,
        remoteFilePath: String,
        destination: File,
    ) = withClient(context, credentials) { sftp ->
        destination.parentFile?.mkdirs()
        sftp.get(remoteFilePath, destination.absolutePath)
    }

    fun upload(
        context: Context,
        credentials: RemoteCredentialStore.Credentials,
        localFile: File,
        remoteFilePath: String,
    ) = withClient(context, credentials) { sftp ->
        sftp.put(localFile.absolutePath, remoteFilePath)
    }

    fun delete(
        context: Context,
        credentials: RemoteCredentialStore.Credentials,
        remotePath: String,
        isDirectory: Boolean,
    ) = withClient(context, credentials) { sftp ->
        if (isDirectory) {
            sftp.rmdir(remotePath.trimEnd('/'))
        } else {
            sftp.rm(remotePath)
        }
    }

    fun rename(
        context: Context,
        credentials: RemoteCredentialStore.Credentials,
        oldPath: String,
        newPath: String,
    ) = withClient(context, credentials) { sftp ->
        sftp.rename(oldPath, newPath)
    }

    fun mkdir(
        context: Context,
        credentials: RemoteCredentialStore.Credentials,
        remotePath: String,
    ) = withClient(context, credentials) { sftp ->
        sftp.mkdirs(remotePath.trimEnd('/'))
    }

    private fun <T> withClient(
        context: Context,
        credentials: RemoteCredentialStore.Credentials,
        hostKeyPrompt: HostKeyPrompt? = null,
        block: (SFTPClient) -> T,
    ): T {
        SSHClient().use { ssh ->
            ssh.timeout = CONNECT_TIMEOUT_MS
            val port = credentials.port.takeIf { it > 0 } ?: 22
            ssh.addHostKeyVerifier(
                SftpHostKeyVerifier(context, credentials.host, port, hostKeyPrompt),
            )
            ssh.connect(credentials.host, port)
            authenticate(context, ssh, credentials)
            return ssh.newSFTPClient().use { block(it) }
        }
    }

    private fun authenticate(
        context: Context,
        ssh: SSHClient,
        credentials: RemoteCredentialStore.Credentials,
    ) {
        val keyPath = credentials.privateKeyPath
        if (keyPath.isNotEmpty()) {
            val keyFile = openKeyFile(context, keyPath)
            ssh.authPublickey(credentials.username, ssh.loadKeys(keyFile.absolutePath))
        } else {
            ssh.authPassword(credentials.username, credentials.password)
        }
    }

    private fun openKeyFile(context: Context, keyPath: String): File {
        if (keyPath.startsWith("content://")) {
            val cache = File(context.cacheDir, "sftp_key").apply { mkdirs() }
            val dest = File(cache, "id_key")
            context.contentResolver.openInputStream(android.net.Uri.parse(keyPath))?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("cannot read private key")
            return dest
        }
        return File(keyPath)
    }

    private fun normalizeDir(path: String): String =
        if (path.endsWith("/")) path else "$path/"

    private fun RemoteResourceInfo.toFileDirItem(
        credentials: RemoteCredentialStore.Credentials,
        parentDir: String,
    ): FileDirItem? {
        val entryName = name
        if (entryName == "." || entryName == "..") {
            return null
        }
        val childRemote = parentDir.trimEnd('/') + "/" + entryName
        val virtualPath = RemotePath.build(
            RemotePath.SFTP_SCHEME,
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
            size = attributes.size,
        )
    }
}
