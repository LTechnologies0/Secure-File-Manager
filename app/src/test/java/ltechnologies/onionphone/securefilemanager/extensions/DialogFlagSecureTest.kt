package ltechnologies.onionphone.securefilemanager.extensions

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogFlagSecureTest {
    @Test
    fun applyDialogFlagSecure_sets_flag_when_screenshots_disabled() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.config.disableScreenshots = true
        val dialog = AlertDialog.Builder(activity).create()
        dialog.show()
        activity.applyDialogFlagSecure(dialog)
        val flags = dialog.window!!.attributes.flags
        assertEquals(WindowManager.LayoutParams.FLAG_SECURE, flags and WindowManager.LayoutParams.FLAG_SECURE)
        dialog.dismiss()
    }

    @Test
    fun applyDialogFlagSecure_skips_when_screenshots_allowed() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.config.disableScreenshots = false
        val dialog = AlertDialog.Builder(activity).create()
        dialog.show()
        activity.applyDialogFlagSecure(dialog)
        val flags = dialog.window!!.attributes.flags
        assertNotEquals(WindowManager.LayoutParams.FLAG_SECURE, flags and WindowManager.LayoutParams.FLAG_SECURE)
        dialog.dismiss()
    }
}
