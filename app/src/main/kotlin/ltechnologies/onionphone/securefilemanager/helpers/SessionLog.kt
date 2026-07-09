package ltechnologies.onionphone.securefilemanager.helpers

/**
 * Debug-only session tracing, separate from [DebugAgentLog] for independent correlation ids.
 *
 * Configure via Gradle properties (never committed):
 * - `debugSessionEndpoint` — full HTTP ingest URL (empty = disabled)
 * - `debugSessionId` — optional session id header
 *
 * Release builds only ever emit a boolean flag via [PrivacyLog].
 */

import android.util.Log
import ltechnologies.onionphone.securefilemanager.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SessionLog {
    private const val TAG = "SFMSession"
    private val endpoint: String = BuildConfig.DEBUG_SESSION_ENDPOINT
    private val sessionId: String = BuildConfig.DEBUG_SESSION_ID

    /** Logs a structured debug event to logcat and, if configured, an HTTP ingest endpoint. */
    fun log(
        location: String,
        message: String,
        hypothesisId: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix",
    ) {
        val ok = data["ok"] as? Boolean
        if (!BuildConfig.DEBUG) {
            if (ok != null) PrivacyLog.flag(message, ok)
            return
        }
        val payload = JSONObject().apply {
            if (sessionId.isNotBlank()) put("sessionId", sessionId)
            put("runId", runId)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject(data))
        }
        Log.d(TAG, payload.toString())
        if (endpoint.isBlank()) return
        Thread {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    if (sessionId.isNotBlank()) {
                        setRequestProperty("X-Debug-Session-Id", sessionId)
                    }
                    doOutput = true
                    connectTimeout = 1500
                    readTimeout = 1500
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.inputStream.close()
            } catch (_: Exception) {
                // logcat fallback only
            }
        }.start()
    }
}
