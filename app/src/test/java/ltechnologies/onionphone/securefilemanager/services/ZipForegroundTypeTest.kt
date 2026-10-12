package ltechnologies.onionphone.securefilemanager.services

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ZipForegroundTypeTest {
    @Test
    fun zip_manager_declares_data_sync_fgs_type() {
        @Suppress("DEPRECATION")
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getServiceInfo(
            android.content.ComponentName(context, ZipManagerService::class.java),
            0,
        )
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
    }
}
