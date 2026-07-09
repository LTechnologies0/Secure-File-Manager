package ltechnologies.onionphone.securefilemanager

import android.app.Application
import android.util.Log
import ltechnologies.onionphone.securefilemanager.extensions.appLock
import ltechnologies.onionphone.securefilemanager.helpers.DebugTrace
import ltechnologies.onionphone.securefilemanager.helpers.PrivacyLog
import ltechnologies.onionphone.securefilemanager.helpers.crypto.HiddenFileCrypto
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
class SfmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugTrace.init(this)
        installCrashHandler()
        ensureBackgroundThread {
            HiddenFileCrypto.migratePlaintextFiles(this)
        }
    }

    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            ltechnologies.onionphone.securefilemanager.helpers.PrivacyLog.flag("crash_handler", ok = false)
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Uncaught on ${thread.name}", throwable)
            }
            try {
                Thread {
                    applicationContext.appLock()
                }.start()
            } catch (_: Exception) {
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "SFM"
    }
}
