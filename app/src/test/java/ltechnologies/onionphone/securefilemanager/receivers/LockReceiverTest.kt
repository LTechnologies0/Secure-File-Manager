package ltechnologies.onionphone.securefilemanager.receivers

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [IntentFilter.hasAction] needs a real (or Robolectric-shadowed) Android framework. */
@RunWith(RobolectricTestRunner::class)
class LockReceiverTest {
    @Test
    fun receiver_declaresScreenOffAction() {
        val filter = LockReceiver.getIntent()
        assertTrue(filter.hasAction(android.content.Intent.ACTION_SCREEN_OFF))
    }
}
