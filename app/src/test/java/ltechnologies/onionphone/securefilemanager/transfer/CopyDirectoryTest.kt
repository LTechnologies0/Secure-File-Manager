package ltechnologies.onionphone.securefilemanager.transfer

import ltechnologies.onionphone.securefilemanager.helpers.HideAction
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.LinkedHashMap

@RunWith(RobolectricTestRunner::class)
class CopyDirectoryTest {
    @Test
    fun copyDirectory_does_not_delete_sources_when_a_child_fails() {
        @Suppress("DEPRECATION")
        val context = RuntimeEnvironment.getApplication()
        val root = File(context.cacheDir, "copy-dir-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val source = File(root, "src").apply { mkdirs() }
        val keep = File(source, "keep.txt").apply { writeText("keep-me") }
        File(source, "nested").mkdirs()
        File(source, "nested/file.txt").writeText("nested")

        val dest = File(root, "dest").apply { mkdirs() }
        File(dest, "src").mkdirs()
        File(dest, "src/nested").writeText("not-a-directory")

        val engine = TransferEngine(context) { }
        val result = engine.run(
            files = arrayListOf(
                FileDirItem(
                    path = source.absolutePath,
                    name = source.name,
                    isDirectory = true,
                    size = keep.length() + 6,
                ),
            ),
            destinationPath = dest.absolutePath,
            copyOnly = false,
            copyMediaOnly = false,
            conflictResolutions = LinkedHashMap(),
            hideAction = HideAction.NONE,
            progress = object : TransferEngine.ProgressCallback {
                override fun onProgress(copiedBytes: Long, totalBytes: Long, currentFileName: String) = Unit
            },
            isCancelled = { false },
        )

        assertFalse(result.success)
        assertTrue("source keep.txt must remain when a sibling copy fails", keep.isFile)
        assertTrue(File(source, "nested/file.txt").isFile)
        assertTrue(File(dest, "src/keep.txt").isFile)
    }
}
