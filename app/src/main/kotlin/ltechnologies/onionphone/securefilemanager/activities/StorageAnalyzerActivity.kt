package ltechnologies.onionphone.securefilemanager.activities

import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.ActivityStorageAnalyzerBinding
import ltechnologies.onionphone.securefilemanager.extensions.beGone
import ltechnologies.onionphone.securefilemanager.extensions.beVisible
import ltechnologies.onionphone.securefilemanager.extensions.formatSize
import ltechnologies.onionphone.securefilemanager.extensions.getInternalStoragePath
import ltechnologies.onionphone.securefilemanager.extensions.humanizePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StorageAnalyzerActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityStorageAnalyzerBinding
    private var scanJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorageAnalyzerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.storageAnalyzerToolbar)
        binding.storageAnalyzerToolbar.setNavigationOnClickListener {
            scanJob?.cancel()
            finish()
        }
        binding.analyzerCancel.setOnClickListener {
            scanJob?.cancel()
            finish()
        }
        startScan()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    private fun startScan() {
        binding.analyzerProgress.beVisible()
        binding.analyzerCancel.beVisible()
        scanJob = lifecycleScope.launch {
            val root = getInternalStoragePath()
            val result = withContext(Dispatchers.IO) {
                scan(root) { isActive }
            }
            binding.analyzerProgress.beGone()
            binding.analyzerCancel.beGone()
            if (result != null) {
                showResults(result)
            }
        }
    }

    private fun showResults(result: ScanResult) {
        val extensions = result.extensions.entries.joinToString("\n") { (ext, size) ->
            "${ext.padEnd(12)} ${size.formatSize()}"
        }
        val folders = result.folders.entries.joinToString("\n") { (folder, size) ->
            "${humanizePath(folder).padEnd(24)} ${size.formatSize()}"
        }
        binding.analyzerResult.text = buildString {
            appendLine(getString(R.string.storage_analyzer_extensions))
            appendLine(extensions)
            appendLine()
            appendLine(getString(R.string.storage_analyzer_folders))
            appendLine(folders)
        }
    }

    private data class ScanResult(
        val extensions: Map<String, Long>,
        val folders: Map<String, Long>,
    )

    private fun scan(root: String, isActive: () -> Boolean): ScanResult? {
        val extensions = HashMap<String, Long>()
        val folders = HashMap<String, Long>()
        val rootFile = File(root)
        if (!rootFile.exists()) {
            return ScanResult(emptyMap(), emptyMap())
        }
        val queue = ArrayDeque<File>()
        queue.add(rootFile)
        while (queue.isNotEmpty()) {
            if (!isActive()) {
                return null
            }
            val dir = queue.removeFirst()
            dir.listFiles()?.forEach { file ->
                if (!isActive()) {
                    return null
                }
                if (file.isDirectory) {
                    if (!file.name.startsWith(".")) {
                        queue.add(file)
                    }
                } else if (!file.name.startsWith(".")) {
                    val ext = file.extension.ifEmpty { "(none)" }
                    extensions[ext] = (extensions[ext] ?: 0L) + file.length()
                    val topFolder = file.absolutePath
                        .removePrefix(root.trimEnd('/'))
                        .substringBefore('/', "")
                        .let { segment ->
                            if (segment.isEmpty()) root else "$root/$segment"
                        }
                    folders[topFolder] = (folders[topFolder] ?: 0L) + file.length()
                }
            }
        }
        return ScanResult(
            extensions = extensions.entries.sortedByDescending { it.value }.take(20).associate { it.key to it.value },
            folders = folders.entries.sortedByDescending { it.value }.take(20).associate { it.key to it.value },
        )
    }
}
