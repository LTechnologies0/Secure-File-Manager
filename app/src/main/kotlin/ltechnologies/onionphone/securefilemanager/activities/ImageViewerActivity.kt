package ltechnologies.onionphone.securefilemanager.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.adapters.ImagePagerAdapter
import ltechnologies.onionphone.securefilemanager.databinding.ActivityImageViewerBinding
import ltechnologies.onionphone.securefilemanager.extensions.isImageFast
import ltechnologies.onionphone.securefilemanager.extensions.sharePaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageViewerActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityImageViewerBinding
    private var paths: List<String> = emptyList()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH) ?: return finish()
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = File(path).name
        binding.imagePager.adapter = ImagePagerAdapter(listOf(path))
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { loadImagePaths(path) }
            paths = loaded
            currentIndex = paths.indexOf(path).coerceAtLeast(0)
            binding.imagePager.adapter = ImagePagerAdapter(paths)
            binding.imagePager.setCurrentItem(currentIndex, false)
        }
        binding.imagePager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentIndex = position
                if (paths.isNotEmpty()) {
                    title = File(paths[position]).name
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_viewer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.viewer_share -> {
                if (paths.isNotEmpty()) {
                    sharePaths(arrayListOf(paths[currentIndex]))
                }
            }
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadImagePaths(path: String): List<String> {
        val dir = File(path).parentFile
        return dir?.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.absolutePath.isImageFast() }
            ?.sortedBy { it.name.lowercase() }
            ?.map { it.absolutePath }
            ?.toList()
            ?: listOf(path)
    }

    companion object {
        private const val EXTRA_PATH = "path"
        fun intent(context: Context, path: String) =
            Intent(context, ImageViewerActivity::class.java).putExtra(EXTRA_PATH, path)
    }
}
