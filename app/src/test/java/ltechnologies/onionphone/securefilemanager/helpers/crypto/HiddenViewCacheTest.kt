package ltechnologies.onionphone.securefilemanager.helpers.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HiddenViewCacheTest {
    @Test
    fun clearViewCache_deletes_plaintext_preview_dir() {
        @Suppress("DEPRECATION")
        val context = RuntimeEnvironment.getApplication()
        val cache = File(context.cacheDir, "hidden-view").apply {
            deleteRecursively()
            mkdirs()
        }
        File(cache, "preview.txt").writeText("secret")
        assertTrue(cache.isDirectory)
        HiddenFileCrypto.clearViewCache(context)
        assertFalse(cache.exists())
    }
}
