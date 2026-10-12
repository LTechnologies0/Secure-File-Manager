package ltechnologies.onionphone.securefilemanager.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class FtpsProtectionTest {
    @Test
    fun applyDataChannelProtection_sends_pbsz0_and_prot_p() {
        val calls = mutableListOf<String>()
        FtpStorage.applyDataChannelProtection(
            { size -> calls.add("PBSZ $size") },
            { level -> calls.add("PROT $level") },
        )
        assertEquals(listOf("PBSZ 0", "PROT P"), calls)
    }
}
