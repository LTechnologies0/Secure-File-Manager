package ltechnologies.onionphone.securefilemanager.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Collections

/** Tracks temporary URI grants (camera, etc.) so lock/clearShareCache can revoke them. */
object ShareUriGrants {
    private data class Grant(val packageName: String, val uri: Uri, val flags: Int)

    private val grants = Collections.synchronizedList(mutableListOf<Grant>())

    fun track(context: Context, packageName: String, uri: Uri, flags: Int) {
        context.grantUriPermission(packageName, uri, flags)
        grants.add(Grant(packageName, uri, flags))
    }

    fun revokeAll(context: Context) {
        synchronized(grants) {
            grants.forEach { grant ->
                try {
                    context.revokeUriPermission(grant.packageName, grant.uri, grant.flags)
                } catch (_: Exception) {
                }
            }
            grants.clear()
        }
    }
}
