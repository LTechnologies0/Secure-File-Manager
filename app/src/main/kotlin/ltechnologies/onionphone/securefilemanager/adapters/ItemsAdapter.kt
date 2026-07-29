package ltechnologies.onionphone.securefilemanager.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.ItemListFileDirBinding
import ltechnologies.onionphone.securefilemanager.databinding.ItemSectionBinding
import ltechnologies.onionphone.securefilemanager.dialogs.*
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.*
import ltechnologies.onionphone.securefilemanager.openpgp.PgpShieldBridge
import ltechnologies.onionphone.securefilemanager.storage.RemotePath
import ltechnologies.onionphone.securefilemanager.interfaces.ItemOperationsListener
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import ltechnologies.onionphone.securefilemanager.models.ListItem
import ltechnologies.onionphone.securefilemanager.models.RadioItem
import ltechnologies.onionphone.securefilemanager.services.ZipManagerService
import ltechnologies.onionphone.securefilemanager.services.ZipManagerService.Companion.EXTRA_DESTINATION
import ltechnologies.onionphone.securefilemanager.services.ZipManagerService.Companion.EXTRA_PATH
import ltechnologies.onionphone.securefilemanager.views.FastScroller
import ltechnologies.onionphone.securefilemanager.views.MyRecyclerView
import java.io.File

class ItemsAdapter(
    activity: BaseAbstractActivity,
    var listItems: MutableList<ListItem>,
    private val listener: ItemOperationsListener?,
    recyclerView: MyRecyclerView,
    private val isPickMultipleIntent: Boolean,
    fastScroller: FastScroller,
    private val swipeRefreshLayout: SwipeRefreshLayout,
    itemClick: (Any) -> Unit
) :
    ItemAbstractAdapter(activity, listItems, recyclerView, fastScroller, itemClick) {

    private var currentItemsHash = listItems.hashCode()
    private var textToHighlight = ""
    private var sdActionsHintShown = false

    init {
        setHasStableIds(true)
        recyclerView.recycledViewPool.setMaxRecycledViews(TYPE_FILE_DIR, 32)
        recyclerView.recycledViewPool.setMaxRecycledViews(TYPE_SECTION, 8)
        setupDragListener()
    }

    override fun getItemId(position: Int): Long = listItems[position].path.hashCode().toLong()

    override fun getActionMenuId() = R.menu.cab

    override fun prepareActionMode(menu: Menu) {
        val isPathOnHidden = isPathOnHidden()
        val isPathOnSd = isPathOnSd()
        val isPathRemote = isPathRemote()
        val selectedFileDirItems = getSelectedFileDirItems()
        menu.apply {
            // visibility
            findItem(R.id.cab_decompress).isVisible =
                !isPathRemote &&
                    selectedFileDirItems.filter { it.path.isZipFile() }.size == selectedFileDirItems.size &&
                    !isPathOnSd
            findItem(R.id.cab_compress).isVisible = !isPathOnSd && !isPathRemote
            findItem(R.id.cab_confirm_selection).isVisible = isPickMultipleIntent
            findItem(R.id.cab_copy_path).isVisible = isOneItemSelected()
            findItem(R.id.cab_open_with).isVisible = isOneFileSelected() && !isPathRemote
            findItem(R.id.cab_open_as).isVisible = isOneFileSelected() && !isPathRemote
            findItem(R.id.cab_set_as).isVisible = isOneFileSelected() && !isPathRemote
            findItem(R.id.cab_share).isVisible = !isPathRemote
            findItem(R.id.cab_export).isVisible = isOneFileSelected() && !isPathRemote
            findItem(R.id.cab_hide).isVisible = !isPathOnHidden && !isPathRemote
            findItem(R.id.cab_unhide).isVisible = isPathOnHidden && !isPathRemote
            findItem(R.id.cab_encrypt).isVisible =
                !isPathRemote &&
                    PgpShieldBridge.isInstalled(activity) &&
                    selectedFileDirItems.any {
                        (it.isDirectory && PgpShieldBridge.folderHasEncryptableFiles(it.path)) ||
                            (!it.isDirectory && !it.path.isOpenPgpFile())
                    } &&
                    !isPathOnSd
            findItem(R.id.cab_decrypt).isVisible =
                !isPathRemote &&
                    selectedFileDirItems.any {
                        !it.isDirectory && it.path.isOpenPgpFile()
                    } &&
                    !isPathOnSd
            findItem(R.id.cab_properties).isVisible = true
            findItem(R.id.cab_cut).isVisible = !isPathRemote && !isPathOnSd
            findItem(R.id.cab_copy_clipboard).isVisible = !isPathRemote && !isPathOnSd
            findItem(R.id.cab_paste).isVisible =
                !isPathRemote && FileClipboard.hasContent() && !isPathOnSd
            findItem(R.id.cab_copy_to).isVisible = true
            findItem(R.id.cab_move_to).isVisible = true
            findItem(R.id.cab_delete).isVisible = true
        }
        if (isPathOnSd && selectedKeys.isNotEmpty()) {
            if (!sdActionsHintShown) {
                sdActionsHintShown = true
                activity.toast(R.string.sd_card_limited_actions)
            }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_confirm_selection -> confirmSelection()
            R.id.cab_rename -> displayRenameDialog()
            R.id.cab_properties -> showProperties()
            R.id.cab_share -> beforeShareFiles()
            R.id.cab_export -> exportDocument()
            R.id.cab_hide -> fileHide(HideAction.HIDE)
            R.id.cab_unhide -> fileHide(HideAction.UNHIDE)
            R.id.cab_encrypt -> pgpShieldEncrypt()
            R.id.cab_decrypt -> pgpShieldDecrypt()
            R.id.cab_cut -> cutToClipboard()
            R.id.cab_copy_clipboard -> copyToClipboard()
            R.id.cab_paste -> pasteFromClipboard()
            R.id.cab_copy_path -> copyPath()
            R.id.cab_set_as -> setAs()
            R.id.cab_open_with -> openWith()
            R.id.cab_open_as -> openAs()
            R.id.cab_copy_to -> copyMoveTo(true)
            R.id.cab_move_to -> copyMoveTo(false)
            R.id.cab_compress -> compressSelection()
            R.id.cab_decompress -> decompressSelection()
            R.id.cab_select_all -> selectAll()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = listItems.filter { !it.isSectionTitle }.size

    override fun getIsItemSelectable(position: Int) = !listItems[position].isSectionTitle

    override fun getItemSelectionKey(position: Int) =
        listItems.getOrNull(position)?.path?.hashCode()

    override fun getItemKeyPosition(key: Int) = listItems.indexOfFirst { it.path.hashCode() == key }

    override fun onActionModeCreated() {
        swipeRefreshLayout.isRefreshing = false
        swipeRefreshLayout.isEnabled = false
    }

    override fun onActionModeDestroyed() {
        swipeRefreshLayout.isEnabled = true
    }

    override fun getItemViewType(position: Int): Int {
        return if (listItems[position].isSectionTitle) {
            TYPE_SECTION
        } else {
            TYPE_FILE_DIR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout =
            if (viewType == TYPE_SECTION) R.layout.item_section else R.layout.item_list_file_dir
        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: RecyclerViewAdapter.ViewHolder, position: Int) {
        val fileDirItem = listItems[position]
        holder.bindView(
            fileDirItem,
            true,
            !fileDirItem.isSectionTitle
        ) { itemView, _ ->
            setupView(itemView, fileDirItem)
        }
        bindViewHolder(holder)
    }

    private fun isOneFileSelected() =
        isOneItemSelected() && getItemWithKey(selectedKeys.first())?.isDirectory == false

    private fun getItemWithKey(key: Int): FileDirItem? =
        listItems.firstOrNull { it.path.hashCode() == key }

    private fun confirmSelection() {
        if (selectedKeys.isNotEmpty()) {
            val paths =
                getSelectedFileDirItems().asSequence()
                    .filter { !it.isDirectory }
                    .map { it.path }
                    .toMutableList() as ArrayList<String>
            if (paths.isEmpty()) {
                finishActMode()
            } else {
                listener?.selectedPaths(paths)
            }
        }
    }

    private fun displayRenameDialog() {
        val fileDirItems = getSelectedFileDirItems()
        val paths = fileDirItems.asSequence().map { it.path }.toMutableList() as ArrayList<String>
        when (paths.size) {
            1 -> {
                val oldPath = paths.first()
                RenameItemDialog(activity, oldPath) {
                    activity.config.moveFavorite(oldPath, it)
                    activity.runOnUiThread {
                        listener?.refreshItems()
                        finishActMode()
                    }
                }
            }
            else -> RenameItemsDialog(activity, paths) {
                activity.runOnUiThread {
                    listener?.refreshItems()
                    finishActMode()
                }
            }
        }
    }

    private fun showProperties() {
        if (selectedKeys.size <= 1) {
            PropertiesDialog(activity, getFirstSelectedItemPath())
        } else {
            val paths = getSelectedFileDirItems().map { it.path }
            PropertiesDialog(activity, paths)
        }
    }

    private fun exportDocument() {
        activity.beginExportDocument(getFirstSelectedItemPath())
    }

    private fun beforeShareFiles() {
        val selected = getSelectedFileDirItems()
        when {
            selected.any { it.path.isOpenPgpFile() } -> askConfirmShareEncrypted()
            isPathOnHidden() -> askConfirmShareFromHidden()
            else -> shareFiles()
        }
    }

    private fun askConfirmShareEncrypted() {
        ConfirmationDialog(activity, activity.getString(R.string.share_encrypted_file_confirmation)) {
            shareFiles()
        }
    }

    private fun askConfirmShareFromHidden() {
        val selectionSize = selectedKeys.size
        val items =
            resources.getQuantityString(R.plurals.items, selectionSize, selectionSize)
        val question =
            String.format(resources.getString(R.string.share_hidden_file_confirmation), items)
        ConfirmationDialog(activity, question) {
            shareFiles()
        }
    }


    private fun shareFiles() {
        val selectedItems = getSelectedFileDirItems()
        val paths = ArrayList<String>(selectedItems.size)
        selectedItems.forEach {
            addFileUris(it.path, paths)
        }
        activity.sharePaths(paths)
    }

    private fun fileHide(hideAction: HideAction) {
        val (files, source) = getSelected()
        FilePickerDialog(
            activity,
            activity.hiddenPath,
            pickFile = false,
            showFAB = true,
            hideAction = hideAction
        ) {
            activity.copyMoveFilesTo(
                files,
                source,
                it,
                isCopyOperation = true,
                copyPhotoVideoOnly = false,
                hideAction = hideAction
            ) { _: String, copiedAll: Boolean ->
                if (copiedAll) {
                    listener?.deleteFiles(files)
                }
                activity.runOnUiThread {
                    listener?.refreshItems()
                    finishActMode()
                }
            }
        }
    }

    private fun pgpShieldEncrypt() {
        val items = getSelectedFileDirItems()
        val dirs = items.filter { it.isDirectory }
        val files = items.filter { !it.isDirectory }
        if (files.any { it.path.isOpenPgpFile() }) {
            activity.toast(R.string.already_encrypted_pgp)
            return
        }
        if (dirs.size > 1 || (dirs.isNotEmpty() && files.isNotEmpty())) {
            activity.toast(R.string.unknown_error_occurred)
            return
        }
        activity.onPgpShieldResult = { listener?.refreshItems() }
        when {
            dirs.size == 1 -> PgpShieldBridge.encryptFolder(activity, dirs.first().path)
            else -> {
                val paths = files.map { it.path }
                if (paths.isNotEmpty()) {
                    PgpShieldBridge.encrypt(activity, paths)
                }
            }
        }
        finishActMode()
    }

    private fun pgpShieldDecrypt() {
        val items = getSelectedFileDirItems().filter { !it.isDirectory }
        val pgp = items.filter { it.path.isOpenPgpFile() }.map { it.path }
        if (pgp.isEmpty()) {
            return
        }
        activity.onPgpShieldResult = { listener?.refreshItems() }
        PgpShieldBridge.decrypt(activity, pgp)
        finishActMode()
    }

    private fun cutToClipboard() {
        val paths = getSelectedFileDirItems().map { it.path }
        FileClipboard.set(paths, isCut = true)
        activity.toast(R.string.clipboard_cut)
        finishActMode()
    }

    private fun copyToClipboard() {
        val paths = getSelectedFileDirItems().map { it.path }
        FileClipboard.set(paths, isCut = false)
        activity.toast(R.string.clipboard_copied)
        finishActMode()
    }

    private fun pasteFromClipboard() {
        val paths = FileClipboard.paths
        if (paths.isEmpty()) {
            return
        }
        val isCut = FileClipboard.isCut
        val dest = listener?.getBrowsingPath() ?: return
        val files = paths.map { path ->
            FileDirItem(path, File(path).name, File(path).isDirectory)
        } as ArrayList
        activity.copyMoveFilesTo(
            files,
            files.first().path.getParentPath(),
            dest,
            isCopyOperation = !isCut,
            copyPhotoVideoOnly = false,
        ) { _, copiedAll ->
            if (isCut && copiedAll) {
                FileClipboard.clear()
            }
            activity.runOnUiThread {
                listener?.refreshItems()
                finishActMode()
            }
        }
    }

    private fun addFileUris(path: String, paths: ArrayList<String>) {
        if (activity.getIsPathDirectory(path)) {
            File(path).listFiles()
                ?.forEach {
                    addFileUris(it.absolutePath, paths)
                }
        } else {
            paths.add(path)
        }
    }

    private fun copyPath() {
        val clip =
            ClipData.newPlainText(activity.getString(R.string.app_name), getFirstSelectedItemPath())
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
            clip
        )
        finishActMode()
        activity.toast(R.string.path_copied)
    }

    private fun setAs() {
        activity.setAs(getFirstSelectedItemPath())
    }

    private fun openWith() {
        activity.tryOpenPathIntent(getFirstSelectedItemPath(), true)
    }

    private fun openAs() {
        val res = activity.resources
        val items = arrayListOf(
            RadioItem(OPEN_AS_TEXT, res.getString(R.string.text_file)),
            RadioItem(OPEN_AS_IMAGE, res.getString(R.string.image_file)),
            RadioItem(OPEN_AS_AUDIO, res.getString(R.string.audio_file)),
            RadioItem(OPEN_AS_VIDEO, res.getString(R.string.video_file)),
            RadioItem(OPEN_AS_OTHER, res.getString(R.string.other_file))
        )

        RadioGroupDialog(activity, items) {
            activity.tryOpenPathIntent(getFirstSelectedItemPath(), false, it as Int)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val (files, source) = getSelected()
        FilePickerDialog(
            activity,
            source,
            pickFile = false,
            showFAB = true,
            isMovingOperation = !isCopyOperation
        ) {
            activity.copyMoveFilesTo(
                files,
                source,
                it,
                isCopyOperation,
                false
            ) { _: String, _: Boolean ->
                activity.runOnUiThread {
                    listener?.refreshItems()
                    finishActMode()
                }
            }
        }
    }

    private fun getSelected(): Pair<ArrayList<FileDirItem>, String> {
        val files = getSelectedFileDirItems()
        val firstFile = files[0]
        val source = if (firstFile.isDirectory) firstFile.path else firstFile.getParentPath()
        return Pair(files, source)
    }

    private fun compressSelection() {
        val firstPath = getFirstSelectedItemPath()
        CompressAsDialog(activity, firstPath) { destination, encryptWithPgp ->
            val paths = getSelectedFileDirItems().map { it.path }
            if (encryptWithPgp) {
                val archiveName = destination.getFilenameFromPath()
                val outputDir = destination.getParentPath()
                activity.onPgpShieldResult = { listener?.refreshItems() }
                PgpShieldBridge.encryptAsArchive(activity, paths, outputDir, archiveName)
            } else {
                val startIntent = Intent(activity, ZipManagerService::class.java).apply {
                    action = ZipManagerService.ACTION_COMPRESSION
                    putStringArrayListExtra(EXTRA_PATH, ArrayList(paths))
                    putExtra(EXTRA_DESTINATION, destination)
                }
                activity.startService(startIntent)
            }
            activity.runOnUiThread {
                finishActMode()
            }
        }
    }

    private fun decompressSelection() {
        val firstPath = getFirstSelectedItemPath()

        val path = getSelectedFileDirItems().asSequence()
            .map { it.path }
            .find { it.isZipFile() }
            ?: return // this should not happen

        this.activity.decompressHandle(path, firstPath) { destination, password ->
            this.activity.decompressZip(path, destination, password)
            activity.runOnUiThread {
                finishActMode()
            }
        }
    }

    private fun askConfirmDelete() {
        val selectionSize = selectedKeys.size
        val items =
            resources.getQuantityString(R.plurals.delete_items, selectionSize, selectionSize)
        val question = String.format(resources.getString(R.string.deletion_confirmation), items)
        ConfirmationDialog(activity, question) {
            deleteFiles()
        }
    }

    private fun deleteFiles() {
        if (selectedKeys.isEmpty()) {
            return
        }

        activity.handleSAFDialog(getFirstSelectedItemPath()) {
            if (!it) {
                return@handleSAFDialog
            }

            val files = ArrayList<FileDirItem>(selectedKeys.size)
            val positions = ArrayList<Int>()
            selectedKeys.forEach { selectedKey ->
                activity.config.removeFavorite(getItemWithKey(selectedKey)?.path ?: "")
                val position = listItems.indexOfFirst { it.path.hashCode() == selectedKey }
                if (position != -1) {
                    positions.add(position)
                    files.add(listItems[position])
                }
            }

            positions.sortDescending()
            removeSelectedItems(positions)
            listener?.deleteFiles(files)
            positions.forEach {
                listItems.removeAt(it)
            }
        }
    }

    private fun getFirstSelectedItemPath() = getSelectedFileDirItems().first().path

    private fun getSelectedFileDirItems() =
        listItems.filter { selectedKeys.contains(it.path.hashCode()) } as ArrayList<FileDirItem>

    fun updateItems(newItems: ArrayList<ListItem>, highlightText: String = "") {
        val hashChanged = newItems.hashCode() != currentItemsHash
        val highlightChanged = textToHighlight != highlightText
        if (!hashChanged && !highlightChanged) {
            return
        }
        if (hashChanged) {
            currentItemsHash = newItems.hashCode()
            textToHighlight = highlightText
            val oldItems = ArrayList(listItems)
            listItems = newItems.clone() as ArrayList<ListItem>
            DiffUtil.calculateDiff(ListItemDiffCallback(oldItems, listItems)).dispatchUpdatesTo(this)
            finishActMode()
        } else {
            textToHighlight = highlightText
            notifyItemRangeChanged(0, itemCount)
        }
        fastScroller?.measureRecyclerView()
    }

    private class ListItemDiffCallback(
        private val old: List<ListItem>,
        private val new: List<ListItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].path == new[newPos].path
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == new[newPos]
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val icon = ItemListFileDirBinding.bind(holder.itemView).itemIcon
            Glide.with(activity).clear(icon)
        }
    }

    override fun setupView(view: View, fileDirItem: FileDirItem) {}

    private fun setupView(view: View, fileDirItem: ListItem) {
        val isSelected = selectedKeys.contains(fileDirItem.path.hashCode())
        if (fileDirItem.isSectionTitle) {
            val itemBinding = ItemSectionBinding.bind(view)
            itemBinding.itemSection.text =
                if (textToHighlight.isEmpty()) fileDirItem.mName else fileDirItem.mName.highlightTextPart(
                    textToHighlight,
                    adjustedPrimaryColor
                )
            itemBinding.itemSection.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
        } else {
            val itemBinding = ItemListFileDirBinding.bind(view)
            itemBinding.itemFrame.isSelected = isSelected
            val fileName = fileDirItem.name
            itemBinding.itemName.text =
                if (textToHighlight.isEmpty()) fileName else fileName.highlightTextPart(
                    textToHighlight,
                    adjustedPrimaryColor
                )
            itemBinding.itemName.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

            itemBinding.itemGpgBadge.beVisibleIf(!fileDirItem.isDirectory && fileDirItem.isEncrypted())

            itemBinding.itemDetails.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

            itemBinding.itemDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallerFontSize)

            super.setFileIcon(itemBinding.itemIcon, fileDirItem)

            if (fileDirItem.isDirectory) {
                itemBinding.itemDetails.text = getChildrenCnt(fileDirItem)
                itemBinding.itemDate.beGone()
            } else {
                itemBinding.itemDetails.text = fileDirItem.size.formatSize()
                itemBinding.itemDate.beVisible()
                itemBinding.itemDate.text =
                    fileDirItem.modified.formatDate(activity, dateFormat, timeFormat)
            }
        }
    }

    private fun isPathRemote(): Boolean =
        getSelectedFileDirItems().any { RemotePath.isRemote(it.path) }

    private fun isPathOnHidden(): Boolean {
        return getSelectedFileDirItems().any { activity.isPathOnHidden(it.path) }
    }

    private fun isPathOnSd(): Boolean {
        return getSelectedFileDirItems().any { activity.isPathOnSD(it.path) }
    }

    fun updateChildCount(path: String, count: Int) {
        val position = getItemKeyPosition(path.hashCode())
        val item = listItems.getOrNull(position) ?: return
        item.children = count
        notifyItemChanged(position, Unit)
    }

    fun isSectionAt(position: Int): Boolean = listItems.getOrNull(position)?.isSectionTitle == true

    companion object {
        private const val TYPE_FILE_DIR = 1
        private const val TYPE_SECTION = 2
    }
}
