package ltechnologies.onionphone.securefilemanager.activities

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SaveAsUriTest {
    private val ownAuthority = "ltechnologies.onionphone.securefilemanager.provider"
    private val packageName = "ltechnologies.onionphone.securefilemanager"

    @Test
    fun rejects_file_and_own_content_uris() {
        assertFalse(isTrustedIncomingUri(Uri.parse("file:///sdcard/x"), ownAuthority, packageName))
        assertFalse(isTrustedIncomingUri(Uri.parse("content://$ownAuthority/x"), ownAuthority, packageName))
        assertFalse(isTrustedIncomingUri(Uri.parse("content://$packageName.documents/x"), ownAuthority, packageName))
        assertFalse(isTrustedIncomingUri(Uri.parse("http://example.com/x"), ownAuthority, packageName))
    }

    @Test
    fun accepts_foreign_content_uris() {
        assertTrue(
            isTrustedIncomingUri(
                Uri.parse("content://com.android.providers.downloads.documents/document/1"),
                ownAuthority,
                packageName,
            ),
        )
    }
}
