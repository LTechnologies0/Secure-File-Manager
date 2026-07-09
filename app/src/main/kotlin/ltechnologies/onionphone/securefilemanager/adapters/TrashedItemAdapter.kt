package ltechnologies.onionphone.securefilemanager.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.ItemTrashBinding
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.formatDate
import ltechnologies.onionphone.securefilemanager.extensions.getTimeFormat
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import ltechnologies.onionphone.securefilemanager.storage.TrashManager
import ltechnologies.onionphone.securefilemanager.views.MyRecyclerView

class TrashedItemAdapter(
    activity: BaseAbstractActivity,
    private var items: ArrayList<TrashManager.TrashedItem>,
    private val onChanged: () -> Unit,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
) : RecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    init {
        setupDragListener()
    }

    override fun getActionMenuId() = R.menu.menu_trash

    override fun prepareActionMode(menu: Menu) {
        menu.findItem(R.id.empty_trash)?.isVisible = false
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_restore -> restoreSelection()
            R.id.cab_delete_permanent -> deleteSelection()
        }
    }

    override fun getSelectableItemCount() = items.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.trashPath?.hashCode()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.trashPath.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        createViewHolder(R.layout.item_trash, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bindView(item, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, item, selectedKeys.contains(item.trashPath.hashCode()))
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<TrashManager.TrashedItem>) {
        items = ArrayList(newItems)
        notifyItemRangeChanged(0, itemCount)
    }

    private fun setupView(view: View, item: TrashManager.TrashedItem, isSelected: Boolean) {
        val binding = ItemTrashBinding.bind(view)
        val ctx = view.context
        binding.trashName.text = item.name
        binding.trashOrigin.text = item.originalPath
        binding.trashDate.text = item.deletedAt.formatDate(
            ctx,
            ctx.config.dateFormat,
            ctx.getTimeFormat(),
        )
        binding.trashHolder.isSelected = isSelected
    }

    private fun restoreSelection() {
        val selected = getSelectedItems()
        ensureBackgroundThread {
            selected.forEach { TrashManager.restore(activity, it) }
            activity.runOnUiThread {
                finishActMode()
                onChanged()
            }
        }
    }

    private fun deleteSelection() {
        val selected = getSelectedItems()
        ensureBackgroundThread {
            selected.forEach { TrashManager.deletePermanently(it) }
            activity.runOnUiThread {
                finishActMode()
                onChanged()
            }
        }
    }

    private fun getSelectedItems(): List<TrashManager.TrashedItem> =
        selectedKeys.mapNotNull { key ->
            items.firstOrNull { it.trashPath.hashCode() == key }
        }
}
