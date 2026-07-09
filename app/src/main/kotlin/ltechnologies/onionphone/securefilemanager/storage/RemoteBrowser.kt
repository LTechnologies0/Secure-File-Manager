package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import ltechnologies.onionphone.securefilemanager.extensions.getFilenameFromPath
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import java.io.File

object RemoteBrowser {
    private const val MAX_REMOTE_BYTES = 100L * 1024 * 1024

    private fun credentials(context: Context, parsed: RemotePath.Parsed): RemoteCredentialStore.Credentials {
        parsed.connectionId?.let { RemoteCredentialStore.load(context, it) }?.let { return it }
        return RemoteCredentialStore.loadByEndpoint(context, parsed.scheme, parsed.host, parsed.username)
            ?: error("remote credentials missing")
    }

    fun list(context: Context, virtualPath: String): ArrayList<FileDirItem> {
        val parsed = RemotePath.parse(virtualPath) ?: return arrayListOf()
        val credentials = credentials(context, parsed)
        val dir = parsed.remotePath.ifEmpty { "/" }
        val items = when (parsed.scheme) {
            RemotePath.SFTP_SCHEME -> SftpStorage.list(context, credentials, dir)
            RemotePath.FTP_SCHEME -> FtpStorage.list(credentials, dir)
            RemotePath.WEBDAV_SCHEME -> WebDavStorage.list(credentials, dir)
            else -> emptyList()
        }
        return ArrayList(items)
    }

    fun downloadToCache(context: Context, virtualPath: String): String {
        val parsed = RemotePath.parse(virtualPath) ?: error("invalid remote path")
        val credentials = credentials(context, parsed)
        val cacheDir = File(context.cacheDir, "remote").apply { mkdirs() }
        val local = File(cacheDir, parsed.remotePath.substringAfterLast('/'))
        when (parsed.scheme) {
            RemotePath.SFTP_SCHEME -> SftpStorage.download(context, credentials, parsed.remotePath, local)
            RemotePath.FTP_SCHEME -> FtpStorage.download(credentials, parsed.remotePath, local)
            RemotePath.WEBDAV_SCHEME -> WebDavStorage.download(credentials, parsed.remotePath, local)
            else -> error("unsupported protocol")
        }
        if (local.length() > MAX_REMOTE_BYTES) {
            local.delete()
            error("remote file exceeds ${MAX_REMOTE_BYTES / (1024 * 1024)}MB cap")
        }
        return local.absolutePath
    }

    fun uploadLocal(
        context: Context,
        localFile: File,
        destDir: String,
        destParsed: RemotePath.Parsed,
    ) {
        val credentials = credentials(context, destParsed)
        val remotePath = destDir.trimEnd('/') + "/" + localFile.name
        when (destParsed.scheme) {
            RemotePath.SFTP_SCHEME -> SftpStorage.upload(context, credentials, localFile, remotePath)
            RemotePath.FTP_SCHEME -> FtpStorage.upload(credentials, localFile, remotePath)
            RemotePath.WEBDAV_SCHEME -> WebDavStorage.upload(credentials, localFile, remotePath)
            else -> error("unsupported protocol")
        }
    }

    fun delete(context: Context, virtualPath: String) {
        val parsed = RemotePath.parse(virtualPath) ?: error("invalid remote path")
        val credentials = credentials(context, parsed)
        val isDirectory = virtualPath.endsWith("/")
        when (parsed.scheme) {
            RemotePath.SFTP_SCHEME -> SftpStorage.delete(context, credentials, parsed.remotePath, isDirectory)
            RemotePath.FTP_SCHEME -> FtpStorage.delete(credentials, parsed.remotePath, isDirectory)
            RemotePath.WEBDAV_SCHEME -> WebDavStorage.delete(credentials, parsed.remotePath, isDirectory)
            else -> error("unsupported protocol")
        }
    }

    fun mkdir(context: Context, virtualPath: String, name: String) {
        val parsed = RemotePath.parse(virtualPath) ?: error("invalid remote path")
        val credentials = credentials(context, parsed)
        val dir = parsed.remotePath.trimEnd('/')
        val remotePath = if (dir.isEmpty()) "/$name" else "$dir/$name"
        when (parsed.scheme) {
            RemotePath.SFTP_SCHEME -> SftpStorage.mkdir(context, credentials, remotePath)
            RemotePath.FTP_SCHEME -> FtpStorage.mkdir(credentials, remotePath)
            RemotePath.WEBDAV_SCHEME -> WebDavStorage.mkdir(credentials, remotePath)
            else -> error("unsupported protocol")
        }
    }

    fun rename(context: Context, oldVirtualPath: String, newVirtualPath: String) {
        val oldParsed = RemotePath.parse(oldVirtualPath) ?: error("invalid remote path")
        val newParsed = RemotePath.parse(newVirtualPath) ?: error("invalid remote path")
        if (oldParsed.scheme != newParsed.scheme ||
            oldParsed.host != newParsed.host ||
            oldParsed.username != newParsed.username
        ) {
            error("cross-server rename not supported")
        }
        val credentials = credentials(context, oldParsed)
        when (oldParsed.scheme) {
            RemotePath.SFTP_SCHEME -> SftpStorage.rename(
                context,
                credentials,
                oldParsed.remotePath.trimEnd('/'),
                newParsed.remotePath.trimEnd('/'),
            )
            RemotePath.FTP_SCHEME -> FtpStorage.rename(
                credentials,
                oldParsed.remotePath.trimEnd('/'),
                newParsed.remotePath.trimEnd('/'),
            )
            RemotePath.WEBDAV_SCHEME -> WebDavStorage.rename(
                credentials,
                oldParsed.remotePath.trimEnd('/'),
                newParsed.remotePath.trimEnd('/'),
            )
            else -> error("unsupported protocol")
        }
    }

    fun copyRemoteToRemote(
        context: Context,
        sourceVirtualPath: String,
        destDir: String,
        destParsed: RemotePath.Parsed,
        moveWithinServer: Boolean,
    ) {
        val sourceParsed = RemotePath.parse(sourceVirtualPath) ?: error("invalid source")
        val fileName = sourceVirtualPath.trimEnd('/').getFilenameFromPath()
        val destPath = destDir.trimEnd('/') + "/" + fileName
        val sameServer = sourceParsed.scheme == destParsed.scheme &&
            sourceParsed.host == destParsed.host &&
            sourceParsed.username == destParsed.username
        if (moveWithinServer && sameServer && sourceParsed.scheme == RemotePath.SFTP_SCHEME) {
            val credentials = credentials(context, sourceParsed)
            SftpStorage.rename(context, credentials, sourceParsed.remotePath.trimEnd('/'), destPath)
            return
        }
        if (moveWithinServer && sameServer && sourceParsed.scheme == RemotePath.FTP_SCHEME) {
            val credentials = credentials(context, sourceParsed)
            FtpStorage.rename(credentials, sourceParsed.remotePath.trimEnd('/'), destPath)
            return
        }
        if (moveWithinServer && sameServer && sourceParsed.scheme == RemotePath.WEBDAV_SCHEME) {
            val credentials = credentials(context, sourceParsed)
            WebDavStorage.rename(credentials, sourceParsed.remotePath.trimEnd('/'), destPath)
            return
        }
        val cache = downloadToCache(context, sourceVirtualPath)
        val local = File(cache)
        try {
            uploadLocal(context, local, destDir, destParsed)
        } finally {
            local.delete()
        }
    }

    fun mkdir(context: Context, virtualPath: String) {
        val parsed = RemotePath.parse(virtualPath) ?: error("invalid remote path")
        val credentials = credentials(context, parsed)
        when (parsed.scheme) {
            RemotePath.SFTP_SCHEME -> SftpStorage.mkdir(context, credentials, parsed.remotePath)
            RemotePath.FTP_SCHEME -> FtpStorage.mkdir(credentials, parsed.remotePath)
            RemotePath.WEBDAV_SCHEME -> WebDavStorage.mkdir(credentials, parsed.remotePath)
            else -> error("unsupported protocol")
        }
    }
}
