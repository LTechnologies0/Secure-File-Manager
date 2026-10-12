package ltechnologies.onionphone.securefilemanager.storage

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.SocketFactory

/**
 * Fail-closed OnionVPN SOCKS5 bridge (`127.0.0.1:18202`).
 * Remote storage must not fall back to clearnet if the proxy is down.
 */
object OnionVpnProxy {
    const val HOST = "127.0.0.1"
    const val PORT = 18_202
    private const val REACHABLE_TIMEOUT_MS = 3_000

    fun socks(): Proxy {
        requireReachable()
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress(HOST, PORT))
    }

    fun requireReachable() {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(HOST, PORT), REACHABLE_TIMEOUT_MS)
        }
    }

    fun socketFactory(): SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket {
            requireReachable()
            return Socket(socks())
        }

        override fun createSocket(host: String, port: Int): Socket {
            val socket = createSocket()
            socket.connect(InetSocketAddress(host, port), REACHABLE_TIMEOUT_MS)
            return socket
        }

        override fun createSocket(
            host: String,
            port: Int,
            localHost: java.net.InetAddress,
            localPort: Int,
        ): Socket = createSocket(host, port)

        override fun createSocket(host: java.net.InetAddress, port: Int): Socket =
            createSocket(host.hostAddress ?: host.hostName, port)

        override fun createSocket(
            address: java.net.InetAddress,
            port: Int,
            localAddress: java.net.InetAddress,
            localPort: Int,
        ): Socket = createSocket(address, port)
    }
}
