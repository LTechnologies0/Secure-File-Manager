package ltechnologies.onionphone.securefilemanager.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashTimestampTest {
    @Test
    fun sidecar_keeps_deletedAt_independent_of_mtime() {
        val parsed = TrashManager.parseSidecarText(
            "path=/hidden/notes.txt\ndeletedAt=1700000000000\n",
            fallbackDeletedAt = 1L,
        )
        assertEquals("/hidden/notes.txt", parsed.originalPath)
        assertEquals(1_700_000_000_000L, parsed.deletedAt)
    }

    @Test
    fun legacy_sidecar_falls_back_to_file_mtime() {
        val parsed = TrashManager.parseSidecarText("/hidden/old.txt", fallbackDeletedAt = 42L)
        assertEquals("/hidden/old.txt", parsed.originalPath)
        assertEquals(42L, parsed.deletedAt)
    }
}
