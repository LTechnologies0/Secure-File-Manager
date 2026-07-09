package ltechnologies.onionphone.securefilemanager.helpers

import android.util.Log
import ltechnologies.onionphone.securefilemanager.BuildConfig

/**
 * Privacy-safe logging: release builds emit only boolean flags (event=true/false).
 */
object PrivacyLog {
    private const val TAG = "OpPrivacy"

    /** Logs `[event]=true|false` under the `OpPrivacy` tag — safe for release builds. */
    fun flag(event: String, ok: Boolean) {
        Log.i(TAG, sanitizeEvent(event) + "=" + if (ok) "true" else "false")
    }

    /** Same as [flag], but appends [detail] only when [BuildConfig.DEBUG] is true. */
    fun flag(event: String, ok: Boolean, detail: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "${sanitizeEvent(event)}=$ok detail=$detail")
        } else {
            flag(event, ok)
        }
    }

    /** Strips [event] down to `[a-zA-Z0-9._-]`, truncated to 64 chars, to keep log lines predictable. */
    private fun sanitizeEvent(event: String): String =
        event.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)
}
