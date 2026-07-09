package ltechnologies.onionphone.securefilemanager.receivers

import org.junit.Assert.assertTrue
import org.junit.Test

class LockReceiverTest {
    @Test
    fun receiver_declaresScreenOffAction() {
        val filter = LockReceiver.getIntent()
        assertTrue(filter.hasAction(android.content.Intent.ACTION_SCREEN_OFF))
    }
}
