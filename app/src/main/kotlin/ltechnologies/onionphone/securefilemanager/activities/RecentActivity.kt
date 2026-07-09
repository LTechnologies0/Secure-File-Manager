package ltechnologies.onionphone.securefilemanager.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.adapters.RecentFilesAdapter
import ltechnologies.onionphone.securefilemanager.databinding.ActivityRecentBinding
import ltechnologies.onionphone.securefilemanager.extensions.beVisibleIf
import ltechnologies.onionphone.securefilemanager.helpers.RecentFilesStore
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.viewers.ViewerRouter
import java.io.File

class RecentActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityRecentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.recentToolbar)
        binding.recentToolbar.setNavigationOnClickListener { finish() }
        showRecent()
    }

    override fun onResume() {
        super.onResume()
        showRecent()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    private fun showRecent() {
        ensureBackgroundThread {
            val paths = RecentFilesStore.list(this).filter { File(it).exists() }
            runOnUiThread { bindRecent(paths) }
        }
    }

    private fun bindRecent(paths: List<String>) {
        binding.recentPlaceholder.beVisibleIf(paths.isEmpty())
        binding.recentList.beVisibleIf(paths.isNotEmpty())
        binding.recentList.layoutManager = LinearLayoutManager(this)
        binding.recentList.adapter = RecentFilesAdapter(paths) { path ->
            val file = File(path)
            if (file.isDirectory) {
                startActivity(
                    Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PATH, path),
                )
            } else {
                ViewerRouter.open(this, path)
            }
        }
    }
}
