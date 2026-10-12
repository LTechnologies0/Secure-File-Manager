package ltechnologies.onionphone.securefilemanager.observers

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ltechnologies.onionphone.securefilemanager.extensions.appLock
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.startAuthenticationActivity
import ltechnologies.onionphone.securefilemanager.extensions.startStopUnlockAppService

class AuthenticationObserver(private val activity: Activity) : DefaultLifecycleObserver {
    private val lockHandler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable {
        if (activity.config.isAppLock) {
            activity.appLock()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        lockHandler.removeCallbacks(lockRunnable)
        activity.config.isAppForeground = true
        activity.startStopUnlockAppService()
        activity.startAuthenticationActivity()
    }

    override fun onStop(owner: LifecycleOwner) {
        activity.config.isAppForeground = false
        activity.startStopUnlockAppService()
        if (!activity.config.isAppLock) {
            return
        }
        val delayMs = activity.config.backgroundRelockSeconds.coerceAtLeast(0) * 1000L
        lockHandler.removeCallbacks(lockRunnable)
        if (delayMs == 0L) {
            activity.appLock()
        } else {
            lockHandler.postDelayed(lockRunnable, delayMs)
        }
    }
}
