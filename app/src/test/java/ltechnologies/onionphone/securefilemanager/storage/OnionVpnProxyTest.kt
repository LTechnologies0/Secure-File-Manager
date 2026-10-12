package ltechnologies.onionphone.securefilemanager.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.Proxy

class OnionVpnProxyTest {
    @Test
    fun socks_is_fail_closed_to_local_onionvpn_bridge() {
        assertEquals("127.0.0.1", OnionVpnProxy.HOST)
        assertEquals(18_202, OnionVpnProxy.PORT)
        try {
            val proxy = OnionVpnProxy.socks()
            assertEquals(Proxy.Type.SOCKS, proxy.type())
        } catch (e: Exception) {
            assertTrue(e is IOException)
        }
    }
}
