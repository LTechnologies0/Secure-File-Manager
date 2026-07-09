package ltechnologies.onionphone.securefilemanager.storage

import android.util.Xml
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

object WebDavStorage {
    private const val REMOTE_LIST_CAP = 5_000
    private const val NETWORK_TIMEOUT_MS = 30_000

    fun list(credentials: RemoteCredentialStore.Credentials, remotePath: String): List<FileDirItem> {
        val dirUrl = buildUrl(credentials, normalizeDir(remotePath))
        val xml = request(credentials, dirUrl, "PROPFIND", mapOf("Depth" to "1"))
        return parsePropfind(xml, credentials, normalizeDir(remotePath))
            .take(REMOTE_LIST_CAP)
    }

    fun download(
        credentials: RemoteCredentialStore.Credentials,
        remoteFilePath: String,
        destination: File,
    ) {
        destination.parentFile?.mkdirs()
        val url = buildUrl(credentials, remoteFilePath)
        request(credentials, url, "GET").use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
    }

    fun upload(
        credentials: RemoteCredentialStore.Credentials,
        localFile: File,
        remoteFilePath: String,
    ) {
        val url = buildUrl(credentials, remoteFilePath)
        request(credentials, url, "PUT") { conn ->
            localFile.inputStream().use { input -> conn.outputStream.use { output -> input.copyTo(output) } }
        }
    }

    fun delete(
        credentials: RemoteCredentialStore.Credentials,
        remotePath: String,
        isDirectory: Boolean,
    ) {
        val path = if (isDirectory) remotePath.trimEnd('/') + "/" else remotePath
        request(credentials, buildUrl(credentials, path), "DELETE")
    }

    fun rename(
        credentials: RemoteCredentialStore.Credentials,
        oldPath: String,
        newPath: String,
    ) {
        request(
            credentials,
            buildUrl(credentials, oldPath),
            "MOVE",
            mapOf("Destination" to buildUrl(credentials, newPath)),
        )
    }

    fun mkdir(
        credentials: RemoteCredentialStore.Credentials,
        remotePath: String,
    ) {
        request(credentials, buildUrl(credentials, remotePath.trimEnd('/')), "MKCOL")
    }

    private fun request(
        credentials: RemoteCredentialStore.Credentials,
        urlString: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: ((HttpURLConnection) -> Unit)? = null,
    ): InputStream {
        val conn = openConnection(credentials, urlString, method, headers)
        body?.invoke(conn)
        val code = conn.responseCode
        if (code !in 200..299 && code != 204) {
            error("webdav $method failed: $code")
        }
        return conn.inputStream ?: java.io.ByteArrayInputStream(ByteArray(0))
    }

    private fun openConnection(
        credentials: RemoteCredentialStore.Credentials,
        urlString: String,
        method: String,
        headers: Map<String, String>,
    ): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = NETWORK_TIMEOUT_MS
        conn.readTimeout = NETWORK_TIMEOUT_MS
        conn.setRequestProperty("Authorization", basicAuth(credentials))
        headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
        if (method == "PROPFIND") {
            conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            conn.doOutput = true
            conn.outputStream.use {
                it.write(
                    """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><displayname/><getcontentlength/><resourcetype/></prop></propfind>"""
                        .toByteArray(),
                )
            }
        } else if (method == "PUT") {
            conn.doOutput = true
        }
        conn.connect()
        return conn
    }

    private fun basicAuth(credentials: RemoteCredentialStore.Credentials): String {
        val token = "${credentials.username}:${credentials.password}"
        return "Basic ${Base64.getEncoder().encodeToString(token.toByteArray())}"
    }

    private fun buildUrl(credentials: RemoteCredentialStore.Credentials, remotePath: String): String {
        val port = credentials.port.takeIf { it > 0 } ?: 443
        val scheme = if (port == 443) "https" else "http"
        val hostPort = if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) {
            credentials.host
        } else {
            "${credentials.host}:$port"
        }
        val path = remotePath.trimStart('/')
        return "$scheme://$hostPort/$path"
    }

    private fun normalizeDir(path: String): String =
        if (path.endsWith("/")) path else "$path/"

    private fun parsePropfind(
        xml: InputStream,
        credentials: RemoteCredentialStore.Credentials,
        parentDir: String,
    ): List<FileDirItem> {
        val items = mutableListOf<FileDirItem>()
        val parser = Xml.newPullParser()
        parser.setInput(xml, null)
        var event = parser.eventType
        var href = ""
        var isCollection = false
        var size = 0L
        var displayName = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "href" -> href = parser.nextText()
                    "collection" -> isCollection = true
                    "getcontentlength" -> size = parser.nextText().toLongOrNull() ?: 0L
                    "displayname" -> displayName = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "response") {
                    val entry = hrefToItem(href, displayName, isCollection, size, credentials, parentDir)
                    if (entry != null) {
                        items.add(entry)
                    }
                    href = ""
                    isCollection = false
                    size = 0L
                    displayName = ""
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun hrefToItem(
        href: String,
        displayName: String,
        isCollection: Boolean,
        size: Long,
        credentials: RemoteCredentialStore.Credentials,
        parentDir: String,
    ): FileDirItem? {
        val name = displayName.ifEmpty { href.trim('/').substringAfterLast('/') }
        if (name.isEmpty() || name == "." || name == "..") {
            return null
        }
        if (href.endsWith(parentDir.trimStart('/')) || href.endsWith(parentDir.trimStart('/').trimEnd('/'))) {
            return null
        }
        val childRemote = parentDir.trimEnd('/') + "/" + name + if (isCollection) "/" else ""
        val virtualPath = RemotePath.build(
            RemotePath.WEBDAV_SCHEME,
            credentials.host,
            credentials.username,
            childRemote,
            isCollection,
        )
        return FileDirItem(
            path = virtualPath,
            name = name,
            isDirectory = isCollection,
            children = 0,
            size = size,
        )
    }
}
