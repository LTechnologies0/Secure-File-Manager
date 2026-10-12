package ltechnologies.onionphone.securefilemanager.storage

import android.util.Xml
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
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
        request(credentials, url, "PUT", contentLength = localFile.length()) { conn ->
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
        contentLength: Long = -1L,
        body: ((HttpURLConnection) -> Unit)? = null,
    ): InputStream {
        val conn = openConnection(credentials, urlString, method, headers, contentLength)
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
        contentLength: Long = -1L,
    ): HttpURLConnection {
        val conn = URL(urlString).openConnection(OnionVpnProxy.socks()) as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = NETWORK_TIMEOUT_MS
        conn.readTimeout = NETWORK_TIMEOUT_MS
        conn.setRequestProperty("Authorization", basicAuth(credentials))
        headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
        if (method == "PROPFIND") {
            val payload =
                """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><displayname/><getcontentlength/><resourcetype/></prop></propfind>"""
                    .toByteArray()
            conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(payload.size)
            conn.outputStream.use { it.write(payload) }
        } else if (method == "PUT") {
            conn.doOutput = true
            if (contentLength >= 0) {
                conn.setFixedLengthStreamingMode(contentLength)
            }
        }
        conn.connect()
        return conn
    }

    private fun basicAuth(credentials: RemoteCredentialStore.Credentials): String {
        val token = "${credentials.username}:${credentials.password}"
        return "Basic ${Base64.getEncoder().encodeToString(token.toByteArray())}"
    }

    private fun buildUrl(credentials: RemoteCredentialStore.Credentials, remotePath: String): String {
        // Always HTTPS — never downgrade to cleartext based on non-443 ports (MITM / credential leak).
        val port = credentials.port.takeIf { it > 0 } ?: 443
        require(port != 80) {
            "WebDAV cleartext HTTP (port 80) is not allowed; use HTTPS (typically port 443)"
        }
        val hostPort = if (port == 443) credentials.host else "${credentials.host}:$port"
        val path = remotePath.trimStart('/')
        return "https://$hostPort/$path"
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

    internal fun hrefToItem(
        href: String,
        displayName: String,
        isCollection: Boolean,
        size: Long,
        credentials: RemoteCredentialStore.Credentials,
        parentDir: String,
    ): FileDirItem? {
        val decodedHref = decodeHref(href)
        val hrefPath = hrefPath(decodedHref)
        val parentNorm = parentDir.trim('/')
        // Skip only the listed folder itself. href.endsWith("") is true for every string.
        if (hrefPath == parentNorm) {
            return null
        }
        val name = displayName.ifEmpty { hrefPath.substringAfterLast('/') }
        if (name.isEmpty() || name == "." || name == "..") {
            return null
        }
        val childRemote = parentDir.trimEnd('/') + "/" + name + if (isCollection) "/" else ""
        val virtualPath = RemotePath.build(
            RemotePath.WEBDAV_SCHEME,
            credentials.host,
            credentials.username,
            childRemote,
            isCollection,
            connectionId = credentials.id,
        )
        return FileDirItem(
            path = virtualPath,
            name = name,
            isDirectory = isCollection,
            children = 0,
            size = size,
        )
    }

    internal fun decodeHref(href: String): String =
        try {
            URLDecoder.decode(href.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: Exception) {
            href
        }

    internal fun hrefPath(href: String): String {
        val withoutHost = if (href.contains("://")) {
            href.substringAfter("://").substringAfter('/', missingDelimiterValue = "")
        } else {
            href.trimStart('/')
        }
        return withoutHost.trim('/')
    }
}
