package ltechnologies.onionphone.securefilemanager.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.standardizePath
import ltechnologies.onionphone.securefilemanager.databinding.ItemManageFavoriteBinding
import ltechnologies.onionphone.securefilemanager.interfaces.RefreshRecyclerViewListener
import ltechnologies.onionphone.securefilemanager.views.MyRecyclerView
import java.util.*

class ManageFavoritesAdapter(
    activity: BaseAbstractActivity,
    private var favorites: ArrayList<String>,
    private val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : RecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private val config = activity.config

    init {
        setupDragListener()
    }

    override fun getActionMenuId() = R.menu.cab_remove_only

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_remove -> removeSelection()
        }
    }

    override fun getSelectableItemCount() = favorites.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = favorites.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = favorites.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun prepareActionMode(menu: Menu) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        createViewHolder(R.layout.item_manage_favorite, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val favorite = favorites[position]
        holder.bindView(
            favorite,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
            setupView(
                itemView,
                activity.standardizePath(favorite),
                selectedKeys.contains(favorite.hashCode())
            )
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = favorites.size

    private fun setupView(view: View, favorite: String, isSelected: Boolean) {
        val itemBinding = ItemManageFavoriteBinding.bind(view)
        itemBinding.manageFavoriteTitle.text = favorite
        itemBinding.manageFavoriteHolder.isSelected = isSelected
    }

    private fun removeSelection() {
        val removeFavorites = ArrayList<String>(selectedKeys.size)
        val positions = ArrayList<Int>()
        selectedKeys.forEach { selectedKey ->
            val position = favorites.indexOfFirst { it.hashCode() == selectedKey }
            if (position != -1) {
                positions.add(position)

                val favorite = getItemWithKey(selectedKey)
                if (favorite != null) {
                    removeFavorites.add(favorite)
                    config.removeFavorite(favorite)
                }
            }
        }

        positions.sortDescending()
        removeSelectedItems(positions)

        favorites.removeAll(removeFavorites)
        if (favorites.isEmpty()) {
            listener?.refreshItems()
        }
    }

    private fun getItemWithKey(key: Int): String? = favorites.firstOrNull { it.hashCode() == key }
}
