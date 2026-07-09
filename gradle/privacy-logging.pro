# Privacy-safe release logging — strip debug-only profiling / NDJSON ingest.
# Release builds keep only PrivacyLog boolean flags (OpPrivacy tag).

-assumenosideeffects class ltechnologies.onionphone.securefilemanager.helpers.DebugAgentLog {
    public void log(...);
}

-assumenosideeffects class ltechnologies.onionphone.securefilemanager.helpers.SessionLog {
    public void log(...);
}

-assumenosideeffects class ltechnologies.onionphone.securefilemanager.helpers.DebugTrace {
    public static void d(...);
    public static void init(...);
}

-keep class ltechnologies.onionphone.**.PrivacyLog { *; }
