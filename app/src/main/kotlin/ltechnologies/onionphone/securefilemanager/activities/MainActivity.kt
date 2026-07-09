package ltechnologies.onionphone.securefilemanager.activities

import android.app.Activity
import android.app.SearchManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.getkeepsafe.taptargetview.TapTargetSequence
import ltechnologies.onionphone.securefilemanager.BuildConfig
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.dialogs.BetaWarningDialog
import ltechnologies.onionphone.securefilemanager.dialogs.ChangeSortingDialog
import ltechnologies.onionphone.securefilemanager.dialogs.RadioGroupDialog
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.fragments.ItemsFragment
import ltechnologies.onionphone.securefilemanager.helpers.PERMISSION_WRITE_STORAGE
import ltechnologies.onionphone.securefilemanager.helpers.TapTargetTutorial
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.databinding.ActivityMainBinding
import ltechnologies.onionphone.securefilemanager.models.RadioItem
import kotlinx.coroutines.launch
import java.io.File
import java.util.*


class MainActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isSearchOpen = false
    private var wasBackJustPressed = false
    private var searchMenuItem: MenuItem? = null

    private lateinit var primaryFragment: ItemsFragment
    private var secondaryFragment: ItemsFragment? = null

    private val introLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            tryInitFileManager()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        config.internalStoragePath = getInternalStoragePath()
        updateSDCardPath()

        primaryFragment = supportFragmentManager.findFragmentById(R.id.fragment_holder) as ItemsFragment
        secondaryFragment = supportFragmentManager.findFragmentById(R.id.fragment_secondary_pane) as? ItemsFragment

        primaryFragment.apply {
            isGetRingtonePicker = intent.action == RingtoneManager.ACTION_RINGTONE_PICKER
            isGetContentIntent = intent.action == Intent.ACTION_GET_CONTENT
            isPickMultipleIntent = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        applyDualPane()
        setupNavigation()
        onPgpShieldResult = { refreshFragments() }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (primaryFragment.binding.breadcrumbs.childCount <= 1) {
                        if (!wasBackJustPressed) {
                            wasBackJustPressed = true
                            toast(R.string.press_back_again)
                            Handler(Looper.getMainLooper()).postDelayed({
                                wasBackJustPressed = false
                            }, BACK_PRESS_TIMEOUT.toLong())
                        } else {
                            quitApp()
                        }
                    } else {
                        primaryFragment.binding.breadcrumbs.removeBreadcrumb()
                        openPath(primaryFragment.binding.breadcrumbs.getLastItem().path)
                    }
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation?.menu?.findItem(R.id.nav_files)?.isChecked = true
        binding.navigationRail?.menu?.findItem(R.id.nav_files)?.isChecked = true
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        if (!config.isAppWizardDone) {
            introLauncher.launch(IntroActivity.getIntent(applicationContext, true))
        } else {
            if (savedInstanceState == null) {
                tryInitFileManager()
                checkInvalidFavorites()
            }
        }

        if (!config.isAppBetaWarningShowed) {
            BetaWarningDialog(this)
        }
    }

    override fun onPostResume() {
        super.onPostResume()

        if (config.isAppWizardDone && !config.isAppTutorialShowed) {
            this.config.isAppTutorialShowed = true
            this.openTutorial(delayed = true, cancellable = false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        setupSearch(menu)
        return true
    }

    override fun onFragmentActionModeActiveChanged(active: Boolean) {
        super.onFragmentActionModeActiveChanged(active)
        if (::binding.isInitialized) {
            binding.appBar.isVisible = !active
            // #region agent log
            ltechnologies.onionphone.securefilemanager.helpers.DebugAgentLog.log(
                location = "MainActivity.kt:onFragmentActionModeActiveChanged",
                message = "app bar visibility toggled",
                data = mapOf("active" to active, "appBarVisible" to !active),
                hypothesisId = "A",
            )
            // #endregion
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (isFragmentActionModeActive) {
            menu?.let { m ->
                for (i in 0 until m.size()) {
                    m.getItem(i).isVisible = false
                }
            }
            return true
        }
        val favorites = config.favorites
        menu?.apply {
            findItem(R.id.add_favorite).isVisible = !favorites.contains(primaryFragment.currentPath)
            findItem(R.id.remove_favorite).isVisible = favorites.contains(primaryFragment.currentPath)
            findItem(R.id.go_to_favorite).isVisible = favorites.isNotEmpty()
            findItem(R.id.go_home).isVisible = primaryFragment.currentPath != config.homeFolder
            findItem(R.id.set_as_home).isVisible = primaryFragment.currentPath != config.homeFolder
            findItem(R.id.toggle_grid_view)?.title = getString(
                if (config.useGridView) R.string.view_as_list else R.string.view_as_grid,
            )
            findItem(R.id.toggle_hidden_files)?.title = getString(
                if (config.showHiddenFiles) R.string.hide_hidden_files else R.string.show_hidden_files,
            )
            findItem(R.id.toggle_dual_pane)?.title = getString(
                if (config.dualPaneEnabled) R.string.dual_pane_off else R.string.dual_pane_on,
            )
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.go_home -> goHome()
            R.id.go_to_favorite -> goToFavorite()
            R.id.sort -> showSortingDialog()
            R.id.toggle_grid_view -> primaryFragment.toggleGridView()
            R.id.toggle_hidden_files -> toggleHiddenFiles()
            R.id.toggle_dual_pane -> toggleDualPane()
            R.id.open_trash -> startActivity(Intent(this, TrashActivity::class.java))
            R.id.open_recent -> startActivity(Intent(this, RecentActivity::class.java))
            R.id.storage_analyzer -> startActivity(Intent(this, StorageAnalyzerActivity::class.java))
            R.id.free_space -> startActivity(Intent(this, FreeSpaceActivity::class.java))
            R.id.add_favorite -> addFavorite()
            R.id.remove_favorite -> removeFavorite()
            R.id.set_as_home -> setAsHome()
            R.id.intro -> startActivity(IntroActivity.getIntent(applicationContext, false))
            R.id.tutorial -> openTutorial(delayed = false, cancellable = true)
            R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
            R.id.about -> startActivity(Intent(applicationContext, AboutActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PICKED_PATH, primaryFragment.currentPath)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val path = savedInstanceState.getString(PICKED_PATH) ?: internalStoragePath
        openPath(path, true)
    }

    private fun applyDualPane() {
        val wideEnough = resources.configuration.screenWidthDp >= 840
        val enabled = config.dualPaneEnabled && wideEnough
        binding.fragmentSecondary?.isVisible = enabled
        if (enabled && secondaryFragment?.currentPath.isNullOrEmpty()) {
            secondaryFragment?.openPath(config.homeFolder)
        }
    }

    private fun setupNavigation() {
        val showNav = intent.action != RingtoneManager.ACTION_RINGTONE_PICKER &&
            intent.action != Intent.ACTION_GET_CONTENT
        binding.bottomNavigation?.apply {
            isVisible = showNav
            if (showNav) {
                setOnItemSelectedListener { item -> handleNavSelection(item.itemId) }
            }
        }
        binding.navigationRail?.apply {
            isVisible = showNav
            if (showNav) {
                setOnItemSelectedListener { item -> handleNavSelection(item.itemId) }
            }
        }
    }

    private fun handleNavSelection(itemId: Int): Boolean {
        return when (itemId) {
            R.id.nav_files -> true
            R.id.nav_recent -> {
                startActivity(Intent(this, RecentActivity::class.java))
                false
            }
            R.id.nav_favorites -> {
                startActivity(Intent(this, FavoritesActivity::class.java))
                false
            }
            R.id.nav_more -> {
                showMoreMenu()
                false
            }
            else -> false
        }
    }

    private fun showMoreMenu() {
        val anchor = binding.bottomNavigation?.findViewById<View>(R.id.nav_more)
            ?: binding.navigationRail?.findViewById<View>(R.id.nav_more)
            ?: binding.toolbar
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_more, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.open_trash -> startActivity(Intent(this@MainActivity, TrashActivity::class.java))
                    R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
                    R.id.storage_analyzer -> startActivity(Intent(this@MainActivity, StorageAnalyzerActivity::class.java))
                    R.id.free_space -> startActivity(Intent(this@MainActivity, FreeSpaceActivity::class.java))
                    R.id.intro -> startActivity(IntroActivity.getIntent(applicationContext, false))
                    R.id.tutorial -> openTutorial(delayed = false, cancellable = true)
                    R.id.about -> startActivity(Intent(applicationContext, AboutActivity::class.java))
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun toggleDualPane() {
        config.dualPaneEnabled = !config.dualPaneEnabled
        applyDualPane()
        invalidateOptionsMenu()
    }

    private fun toggleHiddenFiles() {
        config.showHiddenFiles = !config.showHiddenFiles
        invalidateOptionsMenu()
        refreshFragments()
    }

    private fun refreshFragments() {
        primaryFragment.refreshItems()
        secondaryFragment?.refreshItems()
    }

    private fun openTutorial(delayed: Boolean = false, cancellable: Boolean = true) {
        val activity = this
        val tapTarget = TapTargetTutorial(this)
        lifecycleScope.launch {
            if (delayed) {
                kotlinx.coroutines.delay(1000L)
            }

            activity.runOnUiThread {
                TapTargetSequence(activity)
                    .targets(tapTarget.getTutorialTapTargets(cancellable))
                    .listener(tapTarget.getTutorialListener())
                    .start()
            }
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val item = menu.findItem(R.id.search) ?: return
        searchMenuItem = item
        (item.actionView as? SearchView)?.apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        primaryFragment.searchQueryChanged(newText)
                    }
                    return true
                }
            })
        }
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                isSearchOpen = true
                primaryFragment.searchOpened()
                primaryFragment.searchQueryChanged("")
                return true
            }

            override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                isSearchOpen = false
                primaryFragment.searchClosed()
                return true
            }
        })
    }

    private fun tryInitFileManager() {
        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                initFileManager()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private fun initFileManager() {
        intent.getStringExtra(EXTRA_OPEN_PATH)?.let {
            openPath(it)
            return
        }

        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            val resolvedPath = when {
                data.scheme == "file" -> data.path
                else -> getRealPathFromURI(data)
            }
            if (!resolvedPath.isNullOrEmpty()) {
                openPath(resolvedPath)
                val file = File(resolvedPath)
                if (file.exists() && !file.isDirectory) {
                    ltechnologies.onionphone.securefilemanager.viewers.ViewerRouter.open(this, resolvedPath)
                }
            } else {
                openPath(config.homeFolder)
            }
        } else {
            openPath(config.homeFolder)
        }
    }

    private fun openPath(path: String, forceRefresh: Boolean = false) {
        var newPath = path
        val file = File(path)
        if (file.exists() && !file.isDirectory) {
            newPath = file.parent!!
        } else if (!file.exists()) {
            newPath = internalStoragePath
        }

        primaryFragment.openPath(newPath, forceRefresh)
    }

    private fun goHome() {
        if (config.homeFolder != primaryFragment.currentPath) {
            openPath(config.homeFolder)
        }
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, primaryFragment.currentPath) {
            primaryFragment.refreshItems()
        }
    }

    private fun addFavorite() {
        config.addFavorite(primaryFragment.currentPath)
    }

    private fun removeFavorite() {
        config.removeFavorite(primaryFragment.currentPath)
    }

    private fun goToFavorite() {
        val favorites = config.favorites
        val items = ArrayList<RadioItem>(favorites.size)
        var currFavoriteIndex = -1

        favorites.forEachIndexed { index, path ->
            val titlePath = this.standardizePath(path)
            items.add(RadioItem(index, titlePath, path))
            if (path == primaryFragment.currentPath) {
                currFavoriteIndex = index
            }
        }

        RadioGroupDialog(this, items, currFavoriteIndex, R.string.go_to_favorite) {
            openPath(it.toString())
        }
    }

    private fun setAsHome() {
        config.homeFolder = primaryFragment.currentPath
        toast(R.string.home_folder_updated)
    }

    private fun checkInvalidFavorites() {
        ensureBackgroundThread {
            config.favorites.forEach {
                if (!isPathOnSD(it) && !File(it).exists()) {
                    config.removeFavorite(it)
                }
            }
        }
    }

    fun pickedPath(path: String) {
        val resultIntent = Intent()
        val uri = getFilePublicUri(File(path), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        resultIntent.setDataAndType(uri, type)
        resultIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    fun pickedRingtone(path: String) {
        val uri = getFilePublicUri(File(path), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        Intent().apply {
            setDataAndType(uri, type)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    fun pickedPaths(paths: ArrayList<String>) {
        val newPaths =
            paths.map { getFilePublicUri(File(it), BuildConfig.APPLICATION_ID) } as ArrayList
        val clipData = ClipData(
            "Attachment",
            arrayOf(paths.getMimeType()),
            ClipData.Item(newPaths.removeAt(0))
        )

        newPaths.forEach {
            clipData.addItem(ClipData.Item(it))
        }

        Intent().apply {
            this.clipData = clipData
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    fun openedDirectory() {
        searchMenuItem?.collapseActionView()
    }

    companion object {
        const val EXTRA_OPEN_PATH = "open_path"
        private const val BACK_PRESS_TIMEOUT = 5000
        private const val PICKED_PATH = "picked_path"
    }

}
