package ltechnologies.onionphone.securefilemanager.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.adapters.DecompressItemsAdapter
import ltechnologies.onionphone.securefilemanager.databinding.ActivityDecompressBinding
import ltechnologies.onionphone.securefilemanager.dialogs.FilePickerDialog
import ltechnologies.onionphone.securefilemanager.dialogs.PasswordPromptDialog
import ltechnologies.onionphone.securefilemanager.extensions.decompressZip
import ltechnologies.onionphone.securefilemanager.extensions.getFilenameFromPath
import ltechnologies.onionphone.securefilemanager.extensions.getParentPath
import ltechnologies.onionphone.securefilemanager.extensions.showErrorToast
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.models.ListItem
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException

class DecompressActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityDecompressBinding
    private val allFiles = ArrayList<ListItem>()
    private var currentPath = ""
    private var zipPath = ""
    private var zipPassword: CharArray? = null

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
        loadArchive()

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

    private fun loadArchive() {
        ensureBackgroundThread {
            try {
                val zip = ZipFile(zipPath)
                if (zip.isEncrypted) {
                    runOnUiThread {
                        PasswordPromptDialog(
                            this,
                            String.format(
                                getString(R.string.decompress_password_title),
                                zipPath.getFilenameFromPath(),
                            ),
                            onCancel = { finish() },
                        ) { password ->
                            zipPassword = password
                            ensureBackgroundThread {
                                try {
                                    val items = loadZipListItems(zipPath, password)
                                    runOnUiThread {
                                        allFiles.clear()
                                        allFiles.addAll(items)
                                        updateCurrentPath("")
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        showErrorToast(e)
                                        finish()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val items = loadZipListItems(zipPath, null)
                    runOnUiThread {
                        allFiles.clear()
                        allFiles.addAll(items)
                        updateCurrentPath("")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showErrorToast(e)
                    finish()
                }
            }
        }
    }

    private fun updateCurrentPath(path: String) {
        currentPath = path
        try {
            val listItems = getFolderItems(currentPath)
            DecompressItemsAdapter(this, listItems, binding.decompressList) {
                val listItem = it as ListItem
                if (listItem.isDirectory) {
                    updateCurrentPath(listItem.path)
                } else {
                    extractSingleFile(listItem.path)
                }
            }.apply {
                binding.decompressList.adapter = this
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun extractSingleFile(entryName: String) {
        FilePickerDialog(
            this,
            pickFile = false,
            showFAB = true,
        ) { destination ->
            ensureBackgroundThread {
                try {
                    val pass = zipPassword
                    val zip = if (pass == null || pass.isEmpty()) {
                        ZipFile(zipPath)
                    } else {
                        ZipFile(zipPath, pass)
                    }
                    zip.extractFile(entryName, destination)
                    runOnUiThread {
                        toast(R.string.decompression_successful)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        toast(R.string.decompressing_failed)
                        if (e is ZipException && e.message != null) {
                            toast(e.message!!)
                        } else {
                            showErrorToast(e)
                        }
                    }
                }
            }
        }
    }

    private fun decompress() {
        this.decompressHandle(zipPath) { destination, password ->
            this.decompressZip(zipPath, destination, password ?: zipPassword)
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

    private fun loadZipListItems(path: String, password: CharArray?): List<ListItem> {
        val zip = if (password == null || password.isEmpty()) {
            ZipFile(path)
        } else {
            ZipFile(path, password)
        }
        val items = ArrayList<ListItem>()
        zip.fileHeaders.forEach { fileHeader ->
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
