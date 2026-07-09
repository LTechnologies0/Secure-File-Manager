package ltechnologies.onionphone.securefilemanager.helpers

import android.app.Application
import android.os.StrictMode
import android.util.Log
import ltechnologies.onionphone.securefilemanager.BuildConfig

/**
 * Debug-only tracing hook: enables [StrictMode] `detectAll().penaltyLog()` for disk/network-on-main-thread
 * detection, and a simple logcat tag for ad-hoc traces. Perfetto capture works via the `profileable`
 * manifest attribute without any code here. No-op entirely in release builds.
 */
object DebugTrace {
    private const val TAG = "SFM"

    /** Enables StrictMode logging for [app]. Call once from `Application.onCreate()`. No-op in release. */
    fun init(@Suppress("UNUSED_PARAMETER") app: Application) {
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, "trace on: adb logcat -s SFM:* ; perfetto via profileable shell")
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build(),
        )
    }

    /** Logs [msg] under the `SFM` logcat tag. No-op in release builds. */
    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }
}
