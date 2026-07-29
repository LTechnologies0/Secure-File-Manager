package ltechnologies.onionphone.securefilemanager.fragments

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.activities.MainActivity
import ltechnologies.onionphone.securefilemanager.adapters.ItemsAdapter
import ltechnologies.onionphone.securefilemanager.dialogs.CreateNewItemDialog
import ltechnologies.onionphone.securefilemanager.dialogs.StoragePickerDialog
import ltechnologies.onionphone.securefilemanager.databinding.FragmentItemsBinding
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.*
import ltechnologies.onionphone.securefilemanager.interfaces.ItemOperationsListener
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import ltechnologies.onionphone.securefilemanager.models.ListItem
import ltechnologies.onionphone.securefilemanager.viewers.ViewerRouter
import ltechnologies.onionphone.securefilemanager.storage.RemoteBrowser
import ltechnologies.onionphone.securefilemanager.storage.RemotePath
import ltechnologies.onionphone.securefilemanager.openpgp.PgpShieldBridge
import ltechnologies.onionphone.securefilemanager.views.Breadcrumbs
import ltechnologies.onionphone.securefilemanager.views.MyLinearLayoutManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class ItemsFragment : Fragment(), ItemOperationsListener, Breadcrumbs.BreadcrumbsListener {
    var currentPath = ""
    var isGetContentIntent = false
    var isGetRingtonePicker = false
    var isPickMultipleIntent = false

    private var isFABOpen = false
    private var isFirstResume = true
    private var skipItemUpdating = false
    private var isSearchOpen = false
    private var lastSearchedText = ""
    private val scrollStates = ScrollStateCache()
    private var storedDateFormat = ""
    private var storedTimeFormat = ""

    private var storedItems = ArrayList<ListItem>()
    private var currentMediaFile: File? = null
    private var currentMediaDestDir: String? = null

    private var _binding: FragmentItemsBinding? = null
    val binding get() = _binding!!

    private val mediaCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        handleMediaCaptureResult(result.resultCode, result.data)
    }

    lateinit var mView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemsBinding.inflate(inflater, container, false)
        mView = binding.root
        return mView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            itemsSwipeRefresh.setOnRefreshListener { refreshItems() }
            showFab.setOnClickListener { toggleFabMenu() }
            newFab.setOnClickListener {
                createNewItem()
                toggleFabMenu()
            }
            cameraFab.setOnClickListener {
                takeVideo()
                toggleFabMenu()
            }
            photoFab.setOnClickListener {
                takePicture()
                toggleFabMenu()
            }
            breadcrumbs.listener = this@ItemsFragment
            breadcrumbs.updateFontSize(requireContext().getTextSize())
            itemsEmpty.itemsEmptyCta.setOnClickListener { createNewItem() }
            applyLayoutManager()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ZipManagerEvents.complete.collect {
                    refreshItems()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PATH, currentPath)
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            savedInstanceState.getString(PATH)?.let { currentPath = it }
            storedItems.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        if (storedDateFormat != requireContext().config.dateFormat || storedTimeFormat != requireContext().getTimeFormat()) {
            getRecyclerAdapter()?.updateDateTimeFormat()
        }
        if (!isFirstResume) {
            refreshItems()
        }
        updatePgpShieldBanner()
        isFirstResume = false
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    private fun handleMediaCaptureResult(resultCode: Int, data: Intent?) {
        val cacheFile = currentMediaFile
        val destDir = currentMediaDestDir
        // #region agent log
        SessionLog.log(
            "ItemsFragment.handleMediaCaptureResult",
            "camera result",
            "F",
            mapOf(
                "resultCode" to resultCode,
                "cacheExists" to (cacheFile?.exists() == true),
                "cacheBytes" to (cacheFile?.length() ?: 0L),
                "hasDataUri" to (data?.data != null),
            ),
        )
        // #endregion
        if (resultCode == RESULT_OK && cacheFile != null && destDir != null) {
            val saved = when {
                cacheFile.exists() && cacheFile.length() > 0L -> {
                    moveCapturedMedia(cacheFile, destDir)
                }
                data?.data != null -> {
                    copyCapturedMedia(requireNotNull(data.data), cacheFile, destDir)
                }
                else -> {
                    val thumb = data?.extras?.get("data") as? Bitmap
                    if (thumb != null) {
                        cacheFile.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        moveCapturedMedia(cacheFile, destDir)
                    } else {
                        false
                    }
                }
            }
            if (saved) {
                if (activity?.isPathOnHidden(destDir) == true) {
                    activity?.rescanPaths(arrayListOf(File(destDir, cacheFile.name).absolutePath))
                }
                refreshItems()
            } else {
                activity?.toast(R.string.unknown_error_occurred)
                cacheFile.delete()
            }
        } else {
            cacheFile?.delete()
        }
        currentMediaFile = null
        currentMediaDestDir = null
    }

    private fun moveCapturedMedia(cacheFile: File, destDir: String): Boolean {
        val dest = File(destDir, cacheFile.name)
        return cacheFile.renameTo(dest) || cacheFile.copyTo(dest, overwrite = true).let { cacheFile.delete() }
    }

    private fun copyCapturedMedia(uri: Uri, cacheFile: File, destDir: String): Boolean {
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } != null && moveCapturedMedia(cacheFile, destDir)
        } catch (_: Exception) {
            false
        }
    }

    private fun storeStateVariables() {
        requireContext().config.apply {
            storedDateFormat = dateFormat
            storedTimeFormat = context.getTimeFormat()
        }
    }

    fun openPath(path: String, forceRefresh: Boolean = false) {
        if (!isAdded) {
            return
        }

        this.toggleFabMenu(true)

        var realPath = path.trimEnd('/')
        if (realPath.isEmpty()) {
            realPath = "/"
        }

        getScrollState()?.let { scrollStates.put(currentPath, it) }
        currentPath = realPath
        getItems(currentPath) { originalPath, listItems ->
            if (currentPath != originalPath || !isAdded) {
                return@getItems
            }

            FileDirItem.sorting = this.requireContext().config.getFolderSorting(currentPath)
            listItems.sort()
            activity?.runOnUiThread {
                activity?.invalidateOptionsMenu()
                addItems(listItems, forceRefresh)
                binding.itemsList.adapter = null
                addItems(storedItems, true)
            }
        }
    }

    private fun addItems(items: ArrayList<ListItem>, forceRefresh: Boolean = false) {
        skipItemUpdating = false
        binding.apply {
            activity?.runOnUiThread {
                itemsSwipeRefresh?.isRefreshing = false
                breadcrumbs.setBreadcrumb(currentPath)
                if (!forceRefresh && items.hashCode() == storedItems.hashCode()) {
                    return@runOnUiThread
                }

                storedItems = items
                if (itemsList.adapter == null) {
                    breadcrumbs.updateFontSize(requireContext().getTextSize())
                }

                ItemsAdapter(
                    activity as BaseAbstractActivity,
                    storedItems,
                    this@ItemsFragment,
                    itemsList,
                    isPickMultipleIntent,
                    itemsFastscroller,
                    itemsSwipeRefresh
                ) {
                    if ((it as? ListItem)?.isSectionTitle == true) {
                        openDirectory(it.mPath)
                        searchClosed()
                    } else {
                        itemClicked(it as FileDirItem)
                    }
                }.apply {
                    itemsList.adapter = this
                }
                updateGridSpanLookup()

                itemsFastscroller.setViews(itemsList, itemsSwipeRefresh) {
                    val listItem = getRecyclerAdapter()?.listItems?.getOrNull(it)
                    itemsFastscroller.updateBubbleText(
                        listItem?.getBubbleText(
                            requireContext(),
                            storedDateFormat,
                            storedTimeFormat
                        ) ?: ""
                    )
                }

                scrollStates.get(currentPath)?.let { getRecyclerLayoutManager().onRestoreInstanceState(it) }
                itemsList.onGlobalLayout {
                    itemsFastscroller.setScrollToY(itemsList.computeVerticalScrollOffset())
                }
                updateEmptyState()
            }
        }
    }

    private fun updateEmptyState() {
        val showEmpty = storedItems.isEmpty() && !isSearchOpen
        binding.apply {
            itemsEmpty.root.beVisibleIf(showEmpty)
            itemsList.beVisibleIf(!showEmpty)
        }
    }

    private fun getScrollState() = (binding.itemsList.layoutManager as? LinearLayoutManager)?.onSaveInstanceState()

    private fun getRecyclerLayoutManager(): LinearLayoutManager =
        binding.itemsList.layoutManager as? LinearLayoutManager ?: MyLinearLayoutManager(requireContext())

    fun toggleGridView() {
        val config = requireContext().config
        config.useGridView = !config.useGridView
        applyLayoutManager()
        updateGridSpanLookup()
        getRecyclerAdapter()?.let { adapter ->
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
        (activity as? MainActivity)?.invalidateOptionsMenu()
    }

    private fun applyLayoutManager() {
        val spanCount = 3
        val grid = requireContext().config.useGridView
        binding.itemsList.layoutManager = if (grid) {
            GridLayoutManager(requireContext(), spanCount)
        } else {
            MyLinearLayoutManager(requireContext())
        }
        RecyclerViewTuning.apply(binding.itemsList, fixedSize = !grid)
    }

    private fun updateGridSpanLookup() {
        val glm = binding.itemsList.layoutManager as? GridLayoutManager ?: return
        val spanCount = glm.spanCount
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val adapter = binding.itemsList.adapter as? ItemsAdapter
                return if (adapter?.isSectionAt(position) == true) spanCount else 1
            }
        }
    }

    private fun getItems(
        path: String,
        callback: (originalPath: String, items: ArrayList<ListItem>) -> Unit
    ) {
        skipItemUpdating = false
        ensureBackgroundThread {
            if (activity?.isDestroyed == false && activity?.isFinishing == false) {
                if (RemotePath.isRemote(path)) {
                    try {
                        val items = ArrayList(
                            RemoteBrowser.list(requireContext(), path).map {
                                ListItem(
                                    it.path,
                                    it.name,
                                    it.isDirectory,
                                    it.children,
                                    it.size,
                                    it.modified,
                                    false,
                                )
                            },
                        )
                        callback(path, items)
                    } catch (e: Exception) {
                        callback(path, ArrayList())
                    }
                    return@ensureBackgroundThread
                }
                getRegularItemsOf(path, callback)
            }
        }
    }

    private fun getRegularItemsOf(
        path: String,
        callback: (originalPath: String, items: ArrayList<ListItem>) -> Unit
    ) {
        val items = ArrayList<ListItem>()
        val files = File(path).listFiles()?.filterNotNull()
        if (context == null || files == null) {
            callback(path, items)
            return
        }

        val isSortingBySize =
            requireContext().config.getFolderSorting(currentPath) and SORT_BY_SIZE != 0
        val lastModifieds = requireContext().getFolderLastModifieds(path)

        for (file in files) {
            if (!requireContext().config.showHiddenFiles && file.name.startsWith(".")) {
                continue
            }
            val fileDirItem = getFileDirItemFromFile(file, isSortingBySize, lastModifieds)
            items.add(fileDirItem)
        }

        // send out the initial item list asap, get proper child count asynchronously as it can be slow
        callback(path, items)

        items.filter { it.mIsDirectory }.forEach {
            if (context != null) {
                val childrenCount = it.getDirectChildrenCount()
                if (childrenCount != 0) {
                    activity?.runOnUiThread {
                        getRecyclerAdapter()?.updateChildCount(it.mPath, childrenCount)
                    }
                }
            }
        }
    }

    private fun getFileDirItemFromFile(
        file: File,
        isSortingBySize: Boolean,
        lastModifieds: HashMap<String, Long> = HashMap<String, Long>()
    ): ListItem {
        val curPath = file.absolutePath
        val curName = file.name
        val isDirectory = file.isDirectory
        val children = if (isDirectory) file.getDirectChildrenCount() else 0
        val size = if (isDirectory) {
            if (isSortingBySize) {
                file.getProperSize()
            } else {
                0L
            }
        } else {
            file.length()
        }

        var lastModified = lastModifieds.remove(curPath)
        if (lastModified == null) {
            lastModified = file.lastModified()
        }

        return ListItem(curPath, curName, isDirectory, children, size, lastModified, false)
    }

    private fun itemClicked(item: FileDirItem) {
        if (item.isDirectory) {
            openDirectory(item.path)
        } else {
            val path = item.path
            if (isGetContentIntent) {
                (activity as MainActivity).pickedPath(path)
            } else if (isGetRingtonePicker) {
                if (path.isAudioFast()) {
                    (activity as MainActivity).pickedRingtone(path)
                } else {
                    activity?.toast(R.string.select_audio_file)
                }
            } else if (RemotePath.isRemote(path)) {
                ensureBackgroundThread {
                    try {
                        val local = RemoteBrowser.downloadToCache(requireContext(), path)
                        activity?.runOnUiThread {
                            (activity as? BaseAbstractActivity)?.let { ViewerRouter.open(it, local) }
                        }
                    } catch (e: Exception) {
                        activity?.runOnUiThread {
                            activity?.toast(R.string.unknown_error_occurred)
                        }
                    }
                }
            } else {
                RecentFilesStore.record(requireContext(), path)
                (activity as? BaseAbstractActivity)?.let { ViewerRouter.open(it, path) }
            }
        }
    }

    private fun openDirectory(path: String) {
        (activity as? MainActivity)?.apply {
            skipItemUpdating = isSearchOpen
            openedDirectory()
        }
        openPath(path)
    }

    fun searchQueryChanged(text: String) {
        val searchText = text.trim()
        lastSearchedText = searchText
        ensureBackgroundThread {
            if (context == null) {
                return@ensureBackgroundThread
            }

            val context = requireContext()

            when {
                searchText.isEmpty() -> activity?.runOnUiThread {
                    binding.apply {
                        itemsList.beVisible()
                        getRecyclerAdapter()?.updateItems(storedItems)
                        itemsPlaceholder.beGone()
                        itemsPlaceholder2.beGone()
                    }
                }
                searchText.length == 1 -> activity?.runOnUiThread {
                    binding.apply {
                        itemsList.beGone()
                        itemsPlaceholder.beVisible()
                        itemsPlaceholder2.beVisible()
                    }
                }
                else -> {
                    val files = if (isSearchOpen) {
                        FileSearch.searchGlobal(context, searchText)
                    } else {
                        searchFiles(searchText, currentPath)
                    }.apply {
                        sortBy { it.getParentPath() }
                    }

                    if (lastSearchedText != searchText) {
                        return@ensureBackgroundThread
                    }

                    val listItems = ArrayList<ListItem>()

                    var previousParent = ""
                    files.forEach {
                        val parent = it.mPath.getParentPath()
                        if (!it.isDirectory && parent != previousParent) {
                            val sectionTitle =
                                ListItem(parent, context.humanizePath(parent), false, 0, 0, 0, true)
                            listItems.add(sectionTitle)
                            previousParent = parent
                        }

                        if (it.isDirectory) {
                            val sectionTitle = ListItem(
                                it.path,
                                context.humanizePath(it.path),
                                true,
                                0,
                                0,
                                0,
                                true
                            )
                            listItems.add(sectionTitle)
                            previousParent = parent
                        }

                        if (!it.isDirectory) {
                            listItems.add(it)
                        }
                    }


                    activity?.runOnUiThread {
                        getRecyclerAdapter()?.updateItems(listItems, text)
                        binding.apply {
                            itemsList.beVisibleIf(listItems.isNotEmpty())
                            itemsPlaceholder.beVisibleIf(listItems.isEmpty())
                            itemsPlaceholder2.beGone()

                            itemsList.onGlobalLayout {
                                itemsFastscroller.setScrollToY(itemsList.computeVerticalScrollOffset())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun searchFiles(text: String, path: String): ArrayList<ListItem> {
        val files = ArrayList<ListItem>()
        if (context == null) {
            return files
        }

        val sorting = requireContext().config.getFolderSorting(path)
        FileDirItem.sorting = requireContext().config.getFolderSorting(currentPath)
        val isSortingBySize = sorting and SORT_BY_SIZE != 0
        File(path).listFiles()?.sortedBy { it.isDirectory }?.forEach {
            if (it.isDirectory) {
                if (it.name.contains(text, true)) {
                    val fileDirItem =
                        getFileDirItemFromFile(it, isSortingBySize, HashMap<String, Long>())
                    files.add(fileDirItem)
                }

                files.addAll(searchFiles(text, it.absolutePath))
            } else {
                if (it.name.contains(text, true)) {
                    val fileDirItem =
                        getFileDirItemFromFile(it, isSortingBySize, HashMap<String, Long>())
                    files.add(fileDirItem)
                }
            }
        }
        return files
    }

    fun searchOpened() {
        isSearchOpen = true
        lastSearchedText = ""
        binding.itemsSwipeRefresh.isEnabled = false
    }

    fun searchClosed() {
        isSearchOpen = false
        if (!skipItemUpdating) {
            getRecyclerAdapter()?.updateItems(storedItems)
        }
        skipItemUpdating = false
        lastSearchedText = ""

        binding.apply {
            itemsSwipeRefresh.isEnabled = true
            itemsList.beVisible()
            itemsPlaceholder.beGone()
            itemsPlaceholder2.beGone()
        }
    }

    private fun createNewItem() {
        CreateNewItemDialog(activity as BaseAbstractActivity, currentPath) {
            if (it) {
                refreshItems()
            } else {
                activity?.toast(R.string.unknown_error_occurred)
            }
        }
    }

    private fun takeMedia(action: String, extension: String) {
        val destDir = File(currentPath)
        if (!destDir.isDirectory) {
            activity?.toast(R.string.invalid_destination)
            return
        }
        val cacheDir = File(requireContext().cacheDir, "media").apply { mkdirs() }
        val cacheFile = File.createTempFile(
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss_", Locale.getDefault()).format(Date()),
            extension,
            cacheDir,
        )
        val intent = Intent(action)
        val resolveInfo = requireContext().packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        if (resolveInfo == null) {
            cacheFile.delete()
            activity?.toast(R.string.no_camera_app)
            return
        }
        val mediaURI = requireContext().getUriForFile(cacheFile)
        val cameraPkg = resolveInfo.activityInfo.packageName
        intent.component = ComponentName(cameraPkg, resolveInfo.activityInfo.name)
        intent.apply {
            putExtra(MediaStore.EXTRA_OUTPUT, mediaURI)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        requireContext().grantUriPermission(
            cameraPkg,
            mediaURI,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        // #region agent log
        SessionLog.log(
            "ItemsFragment.takeMedia",
            "dispatch camera",
            "E",
            mapOf(
                "action" to action,
                "cameraPkg" to cameraPkg,
                "uri" to mediaURI.toString(),
            ),
        )
        // #endregion
        currentMediaFile = cacheFile
        currentMediaDestDir = destDir.absolutePath
        mediaCaptureLauncher.launch(intent)
    }

    private fun takeVideo() {
        takeMedia(MediaStore.ACTION_VIDEO_CAPTURE, ".mp4")
    }

    private fun takePicture() {
        takeMedia(MediaStore.ACTION_IMAGE_CAPTURE, ".jpg")
    }

    private fun getRecyclerAdapter() = binding.itemsList.adapter as? ItemsAdapter

    override fun breadcrumbClicked(id: Int) {
        if (id == 0) {
            StoragePickerDialog(activity as BaseAbstractActivity, currentPath) {
                getRecyclerAdapter()?.finishActMode()
                openPath(it)
            }
        } else {
            val item = binding.breadcrumbs.getChildAt(id).tag as FileDirItem
            openPath(item.path)
        }
    }

    override fun refreshItems() {
        openPath(currentPath)
    }

    override fun deleteFiles(files: ArrayList<FileDirItem>) {
        val hasFolder = files.any { it.isDirectory }
        val firstPath = files.firstOrNull()?.path
        if (firstPath == null || firstPath.isEmpty() || context == null) {
            return
        }


        (activity as BaseAbstractActivity).deleteFiles(files, hasFolder) {
            if (!it) {
                requireActivity().runOnUiThread {
                    requireActivity().toast(R.string.unknown_error_occurred)
                }
            }
        }
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        (activity as MainActivity).pickedPaths(paths)
    }

    override fun getBrowsingPath(): String = currentPath

    private fun toggleFabMenu(forceClose: Boolean = false) {
        if(!forceClose) {
            binding.apply {
                showFab.animate().rotationBy(180f)
            }
        }

        if (forceClose || this.isFABOpen) {
            binding.apply {
                newFab.animate().translationY(0f)
                cameraFab.animate().translationY(0f)
                photoFab.animate().translationY(0f)
                photoFab.animate().translationY(0f).setListener(object : AnimatorListener {
                    override fun onAnimationStart(animator: Animator) {}
                    override fun onAnimationEnd(animator: Animator) {
                        if(!isFABOpen) {
                            cameraFab.isVisible = false
                            photoFab.isVisible = false
                            newFab.isVisible = false
                        }
                    }
                    override fun onAnimationCancel(animator: Animator) {}
                    override fun onAnimationRepeat(animator: Animator) {}
                })
            }
        } else {
            val context = this.requireContext()
            val isOnSdCard = context.isPathOnSD(this.currentPath)
            val hasDeviceCamera = context.hasDeviceCamera()
            val showMediaFab = !isOnSdCard && hasDeviceCamera
            binding.apply {
                cameraFab.isVisible = showMediaFab
                photoFab.isVisible = showMediaFab
                newFab.isVisible = true
                newFab.animate().translationY(-resources.getDimension(R.dimen.fab_move_1))
                cameraFab.animate().translationY(-resources.getDimension(R.dimen.fab_move_2))
                photoFab.animate().translationY(-resources.getDimension(R.dimen.fab_move_3))
            }
        }

        if(forceClose) {
            this.isFABOpen = false
        } else {
            this.isFABOpen = !this.isFABOpen
        }
    }

    private fun updatePgpShieldBanner() {
        val activity = activity as? BaseAbstractActivity ?: return
        val banner = binding.openkeychainBanner.root
        val installed = PgpShieldBridge.isInstalled(activity)
        banner.isVisible = !installed
        if (!installed) {
            binding.openkeychainBanner.openkeychainBannerAction.setOnClickListener {
                val market = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${PgpShieldBridge.PACKAGE}"),
                )
                try {
                    startActivity(market)
                } catch (_: Exception) {
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://play.google.com/store/apps/details?id=${PgpShieldBridge.PACKAGE}",
                                ),
                            ),
                        )
                    } catch (_: Exception) {
                        activity.toast(R.string.pgpshield_missing)
                    }
                }
            }
        }
    }
}
