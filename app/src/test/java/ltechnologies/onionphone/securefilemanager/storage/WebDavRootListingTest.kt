package ltechnologies.onionphone.securefilemanager.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebDavRootListingTest {
    private val credentials = RemoteCredentialStore.Credentials(
        displayName = "test",
        protocol = RemotePath.WEBDAV_SCHEME,
        host = "example.onion",
        port = 443,
        username = "user",
        password = "x",
    )

    @Test
    fun root_self_href_is_skipped_but_children_are_kept() {
        val self = WebDavStorage.hrefToItem("/", "", true, 0, credentials, "/")
        assertNull(self)

        val encodedSelf = WebDavStorage.hrefToItem("%2F", "", true, 0, credentials, "/")
        assertNull(encodedSelf)

        val child = WebDavStorage.hrefToItem(
            "/readme.txt",
            "readme.txt",
            false,
            12,
            credentials,
            "/",
        )
        assertNotNull(child)
        assertEquals("readme.txt", child!!.name)
        assertEquals(false, child.isDirectory)
    }

    @Test
    fun href_is_url_decoded_and_only_exact_folder_is_skipped() {
        val folder = WebDavStorage.hrefToItem(
            "/photos%20x/",
            "photos x",
            true,
            0,
            credentials,
            "/photos x/",
        )
        assertNull(folder)

        val child = WebDavStorage.hrefToItem(
            "/photos%20x/img.jpg",
            "",
            false,
            4,
            credentials,
            "/photos x/",
        )
        assertNotNull(child)
        assertEquals("img.jpg", child!!.name)
    }

    @Test
    fun href_path_strips_host_and_slashes() {
        assertEquals("", WebDavStorage.hrefPath("https://host.example/"))
        assertEquals("a/b", WebDavStorage.hrefPath("https://host.example/a/b"))
        assertEquals("a b", WebDavStorage.hrefPath(WebDavStorage.decodeHref("/a%20b")))
    }
}
