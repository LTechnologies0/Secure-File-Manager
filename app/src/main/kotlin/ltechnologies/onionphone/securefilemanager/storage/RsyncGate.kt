package ltechnologies.onionphone.securefilemanager.storage

import android.content.Context
import ltechnologies.onionphone.securefilemanager.R

object RsyncGate {
    // ponytail: GPLv3 + per-ABI binary bloat — not bundled; flip when legal/size gate passes
    fun summary(context: Context): String = context.getString(R.string.rsync_not_bundled)

    fun isBundled(): Boolean = false
}
