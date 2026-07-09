package ltechnologies.onionphone.securefilemanager.viewers

import android.content.Intent
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.activities.ImageViewerActivity
import ltechnologies.onionphone.securefilemanager.activities.MediaViewerActivity
import ltechnologies.onionphone.securefilemanager.activities.TextViewerActivity
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.isPathOnHidden
import ltechnologies.onionphone.securefilemanager.helpers.crypto.HiddenFileCrypto
import ltechnologies.onionphone.securefilemanager.extensions.isAudioFast
import ltechnologies.onionphone.securefilemanager.extensions.isImageFast
import ltechnologies.onionphone.securefilemanager.extensions.isOpenPgpFile
import ltechnologies.onionphone.securefilemanager.extensions.isVideoFast
import ltechnologies.onionphone.securefilemanager.extensions.tryOpenPathIntent
import java.io.File

object ViewerRouter {
  private const val TEXT_MAX_BYTES = 2L * 1024 * 1024

    fun open(activity: BaseAbstractActivity, path: String, forceChooser: Boolean = false) {
        if (path.isOpenPgpFile()) {
            activity.tryOpenPathIntent(path, forceChooser)
            return
        }
        if (!activity.config.openFilesInternally) {
            activity.tryOpenPathIntent(path, forceChooser)
            return
        }
        val viewPath = if (activity.isPathOnHidden(path)) {
            HiddenFileCrypto.getViewablePath(activity, path)
        } else {
            path
        }
        val file = File(viewPath)
        when {
            isTextCandidate(file) -> activity.startActivity(
                TextViewerActivity.intent(activity, viewPath),
            )
            viewPath.isImageFast() -> activity.startActivity(
                ImageViewerActivity.intent(activity, viewPath),
            )
            viewPath.isAudioFast() || viewPath.isVideoFast() -> activity.startActivity(
                MediaViewerActivity.intent(activity, viewPath),
            )
            else -> activity.tryOpenPathIntent(viewPath, forceChooser)
        }
    }

    private fun isTextCandidate(file: File): Boolean {
        if (!file.isFile || file.length() > TEXT_MAX_BYTES) {
            return false
        }
        val ext = file.extension.lowercase()
        return ext in TEXT_EXTENSIONS
    }

    private val TEXT_EXTENSIONS = setOf(
        "txt", "log", "md", "json", "xml", "html", "htm", "css", "js", "kt", "java",
        "py", "sh", "csv", "yml", "yaml", "properties", "conf", "ini", "gradle",
    )
}
