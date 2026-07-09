package ltechnologies.onionphone.securefilemanager.helpers

/**
 * Debug-only NDJSON log ingest for local agent tooling.
 *
 * Configure via Gradle properties (e.g. `~/.gradle/gradle.properties` or `-P` flags,
 * never committed):
 * - `debugAgentEndpoint` — full HTTP ingest URL (empty = disabled)
 * - `debugAgentSession` — optional session id header
 *
 * Requires `adb reverse` when targeting a host-side collector. No-op in release —
 * both fields are hardcoded to empty strings for [ltechnologies.onionphone.securefilemanager.BuildConfig.DEBUG] == false builds.
 */

import ltechnologies.onionphone.securefilemanager.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Posts structured debug events to a configured HTTP ingest endpoint. */
object DebugAgentLog {
    private val endpoint: String = BuildConfig.DEBUG_AGENT_ENDPOINT
    private val session: String = BuildConfig.DEBUG_AGENT_SESSION

    /**
     * Posts a structured debug event when [BuildConfig.DEBUG] is true and an endpoint is configured.
     *
     * @param location Source location label (typically `ClassName.method`).
     * @param message Human-readable event name.
     * @param data Arbitrary key/value payload (never include passwords or file contents).
     * @param hypothesisId Optional experiment id for agent correlation.
     * @param runId Optional run id for agent correlation.
     */
    fun log(
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        hypothesisId: String = "",
        runId: String = "pre-fix",
    ) {
        if (!BuildConfig.DEBUG) {
            data["ok"]?.let { ok ->
                if (ok is Boolean) PrivacyLog.flag(message, ok)
            }
            return
        }
        if (endpoint.isBlank()) return
        Thread {
            runCatching {
                val payload = JSONObject().apply {
                    if (session.isNotBlank()) put("sessionId", session)
                    put("location", location)
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                    put("hypothesisId", hypothesisId)
                    put("runId", runId)
                    put("data", JSONObject(data))
                }
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    if (session.isNotBlank()) {
                        setRequestProperty("X-Debug-Session-Id", session)
                    }
                    doOutput = true
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.inputStream.close()
                conn.disconnect()
            }
        }.start()
    }
}
