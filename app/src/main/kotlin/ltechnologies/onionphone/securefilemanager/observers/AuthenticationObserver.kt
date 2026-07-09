package ltechnologies.onionphone.securefilemanager.observers

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.startAuthenticationActivity
import ltechnologies.onionphone.securefilemanager.extensions.startStopUnlockAppService

class AuthenticationObserver(private val activity: Activity) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        activity.config.isAppForeground = true
        activity.startStopUnlockAppService()
        activity.startAuthenticationActivity()
    }

    override fun onStop(owner: LifecycleOwner) {
        activity.config.isAppForeground = false
        activity.startStopUnlockAppService()
    }
}
