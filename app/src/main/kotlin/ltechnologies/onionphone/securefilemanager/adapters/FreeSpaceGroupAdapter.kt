package ltechnologies.onionphone.securefilemanager.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.databinding.ItemFreeSpaceGroupBinding
import ltechnologies.onionphone.securefilemanager.extensions.formatSize
import ltechnologies.onionphone.securefilemanager.extensions.humanizePath
import ltechnologies.onionphone.securefilemanager.helpers.ReclaimGroup
import ltechnologies.onionphone.securefilemanager.helpers.SpaceCategory
import ltechnologies.onionphone.securefilemanager.views.MyRecyclerView

class FreeSpaceGroupAdapter(
    activity: BaseAbstractActivity,
    private val groups: ArrayList<ReclaimGroup>,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
) : RecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    init {
        setupDragListener()
    }

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = groups.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = groups.getOrNull(position)?.key

    override fun getItemKeyPosition(key: Int) = groups.indexOfFirst { it.key == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        createViewHolder(R.layout.item_free_space_group, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        holder.bindView(group, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, group, selectedKeys.contains(group.key))
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = groups.size

    fun getSelectedGroups(): List<ReclaimGroup> =
        selectedKeys.mapNotNull { key -> groups.firstOrNull { it.key == key } }

    private fun setupView(view: View, group: ReclaimGroup, isSelected: Boolean) {
        val binding = ItemFreeSpaceGroupBinding.bind(view)
        val categoryLabel = when (group.category) {
            SpaceCategory.DUPLICATES -> activity.getString(R.string.free_space_category_duplicates)
            SpaceCategory.TEMP -> activity.getString(R.string.free_space_category_temp)
            SpaceCategory.CACHE -> activity.getString(R.string.free_space_category_cache)
            SpaceCategory.EMPTY -> activity.getString(R.string.free_space_category_empty)
        }
        val count = when (group.category) {
            SpaceCategory.DUPLICATES -> group.paths.size
            else -> group.pathsToDelete.size
        }
        binding.freeSpaceTitle.text = activity.getString(
            R.string.free_space_group_title,
            categoryLabel,
            count,
            group.reclaimableBytes.formatSize(),
        )
        val previewLimit = 8
        val pathsText = group.paths.take(previewLimit).joinToString("\n") {
            activity.humanizePath(it)
        } + if (group.paths.size > previewLimit) {
            "\n…"
        } else {
            ""
        }
        binding.freeSpacePaths.text = pathsText
        binding.freeSpaceHolder.isSelected = isSelected
    }
}
