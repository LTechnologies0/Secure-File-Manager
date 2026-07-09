package ltechnologies.onionphone.securefilemanager.activities

import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.adapters.ManageFavoritesAdapter
import ltechnologies.onionphone.securefilemanager.dialogs.FilePickerDialog
import ltechnologies.onionphone.securefilemanager.extensions.beVisibleIf
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.getInternalStoragePath
import ltechnologies.onionphone.securefilemanager.databinding.ActivityFavoritesBinding
import ltechnologies.onionphone.securefilemanager.interfaces.RefreshRecyclerViewListener

class FavoritesActivity : BaseAbstractActivity(), RefreshRecyclerViewListener {
    private lateinit var binding: ActivityFavoritesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.favoritesToolbar)
        binding.favoritesToolbar.setNavigationOnClickListener { finish() }
        updateFavorites()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_favorites, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.add_favorite -> addFavorite()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun updateFavorites() {
        val favorites = ArrayList<String>()
        config.favorites.mapTo(favorites) { it }
        binding.manageFavoritesPlaceholder.beVisibleIf(favorites.isEmpty())

        binding.manageFavoritesPlaceholder2.apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            beVisibleIf(favorites.isEmpty())
            setOnClickListener {
                addFavorite()
            }
        }

        ManageFavoritesAdapter(this, favorites, this, binding.manageFavoritesList) { }.apply {
            binding.manageFavoritesList.adapter = this
        }
    }

    override fun refreshItems() {
        updateFavorites()
    }

    private fun addFavorite() {
        FilePickerDialog(
            this,
            currPath = getInternalStoragePath(),
            pickFile = false
        ) {
            config.addFavorite(it)
            updateFavorites()
        }
    }
}
