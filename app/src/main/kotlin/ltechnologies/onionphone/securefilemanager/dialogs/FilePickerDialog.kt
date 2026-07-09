package ltechnologies.onionphone.securefilemanager.dialogs

import android.os.Parcelable
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.adapters.FilePickerItemsAdapter
import ltechnologies.onionphone.securefilemanager.adapters.FilepickerFavoritesAdapter
import ltechnologies.onionphone.securefilemanager.databinding.DialogFilepickerBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.*
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import ltechnologies.onionphone.securefilemanager.storage.RemoteBrowser
import ltechnologies.onionphone.securefilemanager.storage.RemotePath
import ltechnologies.onionphone.securefilemanager.views.Breadcrumbs
import java.io.File
import java.util.*

/**
 * The only filepicker constructor with a couple optional parameters
 *
 * @param activity has to be activity to avoid some Theme.AppCompat issues
 * @param currPath initial path of the dialog, defaults to the external storage
 * @param pickFile toggle used to determine if we are picking a file or a folder
 * @param showFAB toggle the displaying of a Floating Action Button for creating new folders
 * @param callback the callback used for returning the selected file/folder
 */
class FilePickerDialog(
    val activity: BaseAbstractActivity,
    var currPath: String = activity.config.homeFolder,
    val pickFile: Boolean = true,
    val showFAB: Boolean = false,
    private val isMovingOperation: Boolean = false,
    private val hideAction: HideAction = HideAction.NONE,
    val finishOnBackPress: Boolean = false,
    val callbackNegative: (() -> Unit)? = null,
    val callback: (pickedPath: String) -> Unit,
) : Breadcrumbs.BreadcrumbsListener {

    private var mFirstUpdate = true
    private var mPrevPath = ""
    private val mScrollStates = ScrollStateCache()

    private lateinit var mDialog: AlertDialog
    private val binding = DialogFilepickerBinding.inflate(activity.layoutInflater)

    init {
        if (RemotePath.isRemote(currPath)) {
            // keep remote path
        } else if (!activity.getDoesFilePathExist(currPath) || isUnhide(hideAction)) {
            currPath = activity.internalStoragePath
        }

        if (!activity.getIsPathDirectory(currPath)) {
            currPath = currPath.getParentPath()
        }

        binding.filepickerBreadcrumbs.apply {
            listener =
                if ((isMovingOperation && activity.isPathOnHidden(currPath)) || isHide(hideAction))
                    null
                else this@FilePickerDialog
            updateFontSize(activity.getTextSize())
        }

        tryUpdateItems()
        setupFavorites()

        if (showFAB) {
            binding.filepickerFab.apply {
                beVisible()
                setOnClickListener { createNewFolder() }
            }
        }

        val secondaryFabBottomMargin =
            activity.resources.getDimension(
                if (showFAB) R.dimen.secondary_fab_bottom_margin else R.dimen.activity_margin
            ).toInt()
        binding.fabsHolder.apply {
            (layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = secondaryFabBottomMargin
        }

        binding.filepickerFavoritesLabel.text = activity.getString(R.string.favorites)
        binding.filepickerFabShowFavorites.apply {
            beVisibleIf(context.config.favorites.isNotEmpty() && !isHide(hideAction))
            setOnClickListener {
                if (binding.filepickerFavoritesHolder.isVisible()) {
                    hideFavorites()
                } else {
                    showFavorites()
                }
            }
        }

        binding.filepickerFabShowHidden.setOnClickListener {
            currPath = activity.hiddenPath
            tryUpdateItems()
        }

        mDialog = activity.showM3FormDialog(
            titleId = getTitle(),
            customView = binding.root,
            positiveTextId = if (!pickFile) R.string.ok else 0,
            negativeTextId = R.string.cancel,
        ) { primary, negative, _ ->
            setOnKeyListener { _, i, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_UP && i == KeyEvent.KEYCODE_BACK) {
                    val breadcrumbs = binding.filepickerBreadcrumbs
                    if (breadcrumbs.childCount > 1) {
                        breadcrumbs.removeBreadcrumb()
                        currPath = breadcrumbs.getLastItem().path.trimEnd('/')
                        tryUpdateItems()
                    } else {
                        dismiss()
                        if (finishOnBackPress) {
                            activity.finish()
                        }
                    }
                }
                true
            }

            negative.setOnClickListener {
                callbackNegative?.invoke()
                dismiss()
            }

            if (!pickFile) {
                primary.setOnClickListener { verifyPath() }
            }

            if (isHide(hideAction) && !activity.config.isHideTutorialShowed) {
                TapTargetView.showFor(
                    this,
                    TapTarget.forView(
                        primary,
                        activity.getString(R.string.confirm_selection),
                        htmlText(activity.getString(R.string.tutorial_hide_description))
                    ).transparentTarget(true)
                )
                activity.config.isHideTutorialShowed = true
            }
        }
    }

    private fun getTitle() = if (pickFile) R.string.select_file else R.string.select_folder

    private fun createNewFolder() {
        CreateNewFolderDialog(activity, currPath) {
            callback(it)
            mDialog.dismiss()
        }
    }

    private fun tryUpdateItems() {
        setHiddenVisibility()
        ensureBackgroundThread {
            getItems(currPath) {
                activity.runOnUiThread {
                    updateItems(it as ArrayList<FileDirItem>)
                }
            }
        }
    }

    private fun updateItems(items: ArrayList<FileDirItem>) {
        if (!containsDirectory(items) && !mFirstUpdate && !pickFile && !showFAB) {
            verifyPath()
            return
        }

        val sortedItems = items.sortedWith(compareBy({ !it.isDirectory }, {
            it.name.lowercase(Locale.getDefault())
        }))

        val adapter = FilePickerItemsAdapter(activity, sortedItems, binding.filepickerList) {
            if ((it as FileDirItem).isDirectory) {
                currPath = it.path
                tryUpdateItems()
            } else if (pickFile) {
                currPath = it.path
                verifyPath()
            }
        }

        val layoutManager = binding.filepickerList.layoutManager as LinearLayoutManager
        layoutManager.onSaveInstanceState()?.let { mScrollStates.put(mPrevPath.trimEnd('/'), it) }

        binding.apply {
            filepickerList.adapter = adapter
            filepickerBreadcrumbs.setBreadcrumb(currPath)
            filepickerFastscroller.setViews(filepickerList) {
                filepickerFastscroller.updateBubbleText(
                    sortedItems.getOrNull(it)?.getBubbleText(root.context) ?: ""
                )
            }

            filepickerList.scheduleLayoutAnimation()
            mScrollStates.get(currPath.trimEnd('/'))?.let { layoutManager.onRestoreInstanceState(it) }
            filepickerList.onGlobalLayout {
                filepickerFastscroller.setScrollToY(filepickerList.computeVerticalScrollOffset())
            }
        }

        mFirstUpdate = false
        mPrevPath = currPath
    }

    private fun verifyPath() {
        if (RemotePath.isRemote(currPath)) {
            if (!pickFile) {
                if (!currPath.endsWith("/")) {
                    currPath = "$currPath/"
                }
                sendSuccess()
            } else {
                sendSuccess()
            }
            return
        }
        val file = File(currPath)
        if ((pickFile && file.isFile) || (!pickFile && file.isDirectory)) {
            sendSuccess()
        }
    }

    private fun sendSuccess() {
        if (RemotePath.isRemote(currPath)) {
            if (!pickFile && !currPath.endsWith("/")) {
                currPath = "$currPath/"
            }
            callback(currPath)
            mDialog.dismiss()
            return
        }
        currPath = if (currPath.length == 1) {
            currPath
        } else {
            currPath.trimEnd('/')
        }
        callback(currPath)
        mDialog.dismiss()
    }

    private fun getItems(
        path: String,
        callback: (List<FileDirItem>) -> Unit
    ) {
        val lastModifieds = activity.getFolderLastModifieds(path)
        getRegularItems(path, lastModifieds, callback)
    }

    private fun getRegularItems(
        path: String,
        lastModifieds: HashMap<String, Long>,
        callback: (List<FileDirItem>) -> Unit
    ) {
        if (RemotePath.isRemote(path)) {
            try {
                callback(RemoteBrowser.list(activity, path))
            } catch (_: Exception) {
                callback(emptyList())
            }
            return
        }
        val items = ArrayList<FileDirItem>()
        val base = File(path)
        val files = base.listFiles()
        if (files == null) {
            callback(items)
            return
        }

        for (file in files) {
            val curPath = file.absolutePath
            val curName = curPath.getFilenameFromPath()
            val size = file.length()
            var lastModified = lastModifieds.remove(curPath)
            val isDirectory = file.isDirectory
            if (lastModified == null) {
                lastModified = file.lastModified()
            }

            val children = if (isDirectory) file.getDirectChildrenCount() else 0
            items.add(FileDirItem(curPath, curName, isDirectory, children, size, lastModified))
        }
        callback(items)
    }

    private fun setupFavorites() {
        FilepickerFavoritesAdapter(
            activity,
            activity.config.favorites.toList(),
            binding.filepickerFavoritesList,
            isMovingOperation
        ) {
            currPath = it as String
            verifyPath()
        }.apply {
            binding.filepickerFavoritesList.adapter = this
        }
    }

    private fun setHiddenVisibility() {
        binding.filepickerFabShowHidden.apply {
            beGoneIf(
                isUnhide(hideAction) ||
                    activity.isPathOnHidden(currPath) ||
                    binding.filepickerFavoritesHolder.isVisible
            )
        }
    }

    private fun showFavorites() {
        binding.apply {
            filepickerFavoritesHolder.beVisible()
            filepickerFilesHolder.beGone()
            filepickerFabShowFavorites.setImageResource(R.drawable.ic_folder_vector)
        }
        setHiddenVisibility()
    }

    private fun hideFavorites() {
        binding.apply {
            filepickerFavoritesHolder.beGone()
            filepickerFilesHolder.beVisible()
            filepickerFabShowFavorites.setImageResource(R.drawable.ic_star_on_vector)
        }
        setHiddenVisibility()
    }

    private fun containsDirectory(items: List<FileDirItem>) = items.any { it.isDirectory }

    override fun breadcrumbClicked(id: Int) {
        if (id == 0) {
            StoragePickerDialog(
                activity,
                currPath,
                isMovingOperation = isMovingOperation,
                hideAction = hideAction
            ) {
                currPath = it
                tryUpdateItems()
            }
        } else {
            val item = binding.filepickerBreadcrumbs.getChildAt(id).tag as FileDirItem
            if (currPath != item.path.trimEnd('/')) {
                currPath = item.path
                tryUpdateItems()
            }
        }
    }
}
