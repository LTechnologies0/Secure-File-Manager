package ltechnologies.onionphone.securefilemanager.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.adapters.TrashedItemAdapter
import ltechnologies.onionphone.securefilemanager.databinding.ActivityTrashBinding
import ltechnologies.onionphone.securefilemanager.dialogs.ConfirmationDialog
import ltechnologies.onionphone.securefilemanager.extensions.beVisibleIf
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.storage.TrashManager

class TrashActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityTrashBinding
    private var items = listOf<TrashManager.TrashedItem>()
    private var adapter: TrashedItemAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.trashToolbar)
        binding.trashToolbar.setNavigationOnClickListener { finish() }
        loadTrash()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_trash, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.empty_trash)?.isVisible = items.isNotEmpty()
        menu.findItem(R.id.cab_restore)?.isVisible = false
        menu.findItem(R.id.cab_delete_permanent)?.isVisible = false
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.empty_trash -> emptyTrash()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadTrash() {
        ensureBackgroundThread {
            TrashManager.purgeExpired(this)
            items = TrashManager.listTrashed(this)
            runOnUiThread { showItems() }
        }
    }

    private fun showItems() {
        invalidateOptionsMenu()
        binding.trashPlaceholder.beVisibleIf(items.isEmpty())
        binding.trashList.beVisibleIf(items.isNotEmpty())
        adapter = TrashedItemAdapter(this, ArrayList(items), ::loadTrash, binding.trashList) { item ->
            val position = items.indexOfFirst {
                it.trashPath == (item as TrashManager.TrashedItem).trashPath
            }
            if (position >= 0) {
                adapter?.selectItemAt(position)
            }
        }
        binding.trashList.adapter = adapter
    }

    private fun emptyTrash() {
        ConfirmationDialog(this, getString(R.string.trash_empty_confirm)) {
            ensureBackgroundThread {
                items.forEach { TrashManager.deletePermanently(it) }
                runOnUiThread { loadTrash() }
            }
        }
    }
}
