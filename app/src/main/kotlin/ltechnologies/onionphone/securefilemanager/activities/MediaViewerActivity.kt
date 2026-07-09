package ltechnologies.onionphone.securefilemanager.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.ActivityMediaViewerBinding
import ltechnologies.onionphone.securefilemanager.extensions.getUriForFile
import ltechnologies.onionphone.securefilemanager.extensions.sharePaths
import java.io.File

class MediaViewerActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityMediaViewerBinding
    private lateinit var path: String
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.getStringExtra(EXTRA_PATH) ?: return finish()
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = File(path).name
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            val uri = getUriForFile(File(path))
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
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
        fun intent(context: Context, path: String) =
            Intent(context, MediaViewerActivity::class.java).putExtra(EXTRA_PATH, path)
    }
}
