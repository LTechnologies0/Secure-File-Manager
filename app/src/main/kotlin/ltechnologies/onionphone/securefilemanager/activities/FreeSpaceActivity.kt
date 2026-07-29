package ltechnologies.onionphone.securefilemanager.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.adapters.FreeSpaceGroupAdapter
import ltechnologies.onionphone.securefilemanager.databinding.ActivityFreeSpaceBinding
import ltechnologies.onionphone.securefilemanager.dialogs.ConfirmationDialog
import ltechnologies.onionphone.securefilemanager.extensions.beGone
import ltechnologies.onionphone.securefilemanager.extensions.beVisible
import ltechnologies.onionphone.securefilemanager.extensions.beVisibleIf
import ltechnologies.onionphone.securefilemanager.extensions.formatSize
import ltechnologies.onionphone.securefilemanager.extensions.getInternalStoragePath
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.ReclaimGroup
import ltechnologies.onionphone.securefilemanager.helpers.SpaceCleaner
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.storage.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File

class FreeSpaceActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityFreeSpaceBinding
    private var scanJob: Job? = null
    private var groups = listOf<ReclaimGroup>()
    private var adapter: FreeSpaceGroupAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFreeSpaceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.freeSpaceToolbar)
        binding.freeSpaceToolbar.setNavigationOnClickListener {
            scanJob?.cancel()
            finish()
        }
        binding.freeSpaceCancel.setOnClickListener {
            scanJob?.cancel()
            finish()
        }
        startScan()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_free_space, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.clean_selected)?.isVisible = groups.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clean_selected -> cleanSelected()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startScan() {
        binding.freeSpaceSummary.beGone()
        binding.freeSpaceProgress.beVisible()
        binding.freeSpaceCancel.beVisible()
        scanJob = lifecycleScope.launch {
            val root = getInternalStoragePath()
            val job = coroutineContext[Job]!!
            val found = withContext(Dispatchers.IO) {
                SpaceCleaner.scan(root) { job.isActive }
            }
            binding.freeSpaceProgress.beGone()
            binding.freeSpaceCancel.beGone()
            groups = found ?: emptyList()
            showGroups()
        }
    }

    private fun showGroups() {
        invalidateOptionsMenu()
        val reclaimable = groups.sumOf { it.reclaimableBytes }
        binding.freeSpaceSummary.beVisibleIf(groups.isNotEmpty())
        binding.freeSpaceSummary.text = getString(
            R.string.free_space_summary,
            reclaimable.formatSize(),
            groups.size,
        )
        binding.freeSpacePlaceholder.beVisibleIf(groups.isEmpty())
        binding.freeSpaceList.beVisibleIf(groups.isNotEmpty())
        adapter = FreeSpaceGroupAdapter(
            this,
            ArrayList(groups),
            binding.freeSpaceList,
            onCleanSelected = { cleanSelected() },
        ) { group ->
            val position = groups.indexOfFirst { it.key == (group as ReclaimGroup).key }
            if (position >= 0) {
                adapter?.selectItemAt(position)
            }
        }
        binding.freeSpaceList.adapter = adapter
    }

    private fun cleanSelected() {
        val selected = adapter?.getSelectedGroups().orEmpty()
        if (selected.isEmpty()) {
            toast(R.string.free_space_select_items)
            return
        }
        ConfirmationDialog(this, getString(R.string.free_space_clean_confirm)) {
            ensureBackgroundThread {
                selected.flatMap { it.pathsToDelete }.distinct().forEach { path ->
                    TrashManager.moveToTrash(this, File(path))
                }
                runOnUiThread {
                    adapter?.finishActMode()
                    startScan()
                }
            }
        }
    }
}
