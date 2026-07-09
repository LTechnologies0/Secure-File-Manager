package ltechnologies.onionphone.securefilemanager.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.adapters.DecompressItemsAdapter
import ltechnologies.onionphone.securefilemanager.extensions.decompressZip
import ltechnologies.onionphone.securefilemanager.extensions.getFilenameFromPath
import ltechnologies.onionphone.securefilemanager.extensions.getParentPath
import ltechnologies.onionphone.securefilemanager.extensions.showErrorToast
import ltechnologies.onionphone.securefilemanager.databinding.ActivityDecompressBinding
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.models.ListItem
import net.lingala.zip4j.ZipFile

class DecompressActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityDecompressBinding
    private val allFiles = ArrayList<ListItem>()
    private var currentPath = ""
    private var zipPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDecompressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.decompressToolbar)
        binding.decompressToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        zipPath = intent.getStringExtra(EXTRA_PATH) ?: run {
            showErrorToast(getString(R.string.unknown_error_occurred))
            finish()
            return
        }
        binding.decompressToolbar.title = zipPath.getFilenameFromPath()
        ensureBackgroundThread {
            val items = loadZipListItems(zipPath)
            runOnUiThread {
                allFiles.addAll(items)
                updateCurrentPath("")
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentPath.isEmpty()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        val newPath = if (currentPath.contains("/")) currentPath.getParentPath() else ""
                        updateCurrentPath(newPath)
                    }
                }
            },
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_decompress, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.decompress -> decompress()
        }

        return true
    }

    private fun updateCurrentPath(path: String) {
        currentPath = path
        try {
            val listItems = getFolderItems(currentPath)
            DecompressItemsAdapter(this, listItems, binding.decompressList) {
                if ((it as ListItem).isDirectory) {
                    updateCurrentPath(it.path)
                }
            }.apply {
                binding.decompressList.adapter = this
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun decompress() {
        this.decompressHandle(zipPath) { destination, password ->
            this.decompressZip(zipPath, destination, password)
        }
    }

    private fun getFolderItems(parent: String): ArrayList<ListItem> {
        return allFiles
            .filter {
                val fileParent = if (it.path.contains("/")) {
                    it.path.getParentPath()
                } else {
                    ""
                }

                fileParent == parent
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.mName }))
            .toMutableList() as ArrayList<ListItem>
    }

    private fun loadZipListItems(path: String): List<ListItem> {
        val items = ArrayList<ListItem>()
        ZipFile(path).fileHeaders.forEach { fileHeader ->
            val filename = fileHeader.fileName.removeSuffix("/")
            items.add(
                ListItem(
                    filename,
                    filename.getFilenameFromPath(),
                    fileHeader.isDirectory,
                    0,
                    fileHeader.uncompressedSize,
                    fileHeader.lastModifiedTime,
                    false,
                ),
            )
        }
        return items
    }

    companion object {
        const val EXTRA_PATH = "EXTRA_PATH"
    }
}
