package ltechnologies.onionphone.securefilemanager.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.ActivityTextViewerBinding
import ltechnologies.onionphone.securefilemanager.extensions.sharePaths
import ltechnologies.onionphone.securefilemanager.extensions.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextViewerActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityTextViewerBinding
    private lateinit var path: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.getStringExtra(EXTRA_PATH) ?: return finish()
        val file = File(path)
        if (!file.isFile || file.length() > MAX_BYTES) {
            toast(R.string.text_viewer_too_large)
            finish()
            return
        }
        binding = ActivityTextViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = file.name
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { file.readText() }
            binding.textContent.text = text
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.viewer_share -> sharePaths(arrayListOf(path))
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val MAX_BYTES = 2L * 1024 * 1024
        fun intent(context: Context, path: String) =
            Intent(context, TextViewerActivity::class.java).putExtra(EXTRA_PATH, path)
    }
}
