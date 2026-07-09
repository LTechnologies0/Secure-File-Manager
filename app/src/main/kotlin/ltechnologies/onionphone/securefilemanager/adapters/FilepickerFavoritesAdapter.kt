package ltechnologies.onionphone.securefilemanager.adapters

import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.extensions.config
import ltechnologies.onionphone.securefilemanager.extensions.getTextSize
import ltechnologies.onionphone.securefilemanager.extensions.standardizePath
import ltechnologies.onionphone.securefilemanager.databinding.FilepickerFavoriteBinding
import ltechnologies.onionphone.securefilemanager.views.MyRecyclerView

class FilepickerFavoritesAdapter(
    activity: BaseAbstractActivity,
    var paths: List<String>,
    recyclerView: MyRecyclerView,
    isMovingOperation: Boolean,
    itemClick: (Any) -> Unit
) : RecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private var fontSize = 0f

    init {
        if (isMovingOperation) {
            val hiddenPath = activity.config.hiddenPath
            paths = paths.filter { !it.startsWith(hiddenPath) }
        }
        fontSize = activity.getTextSize()
    }

    override fun getActionMenuId() = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        createViewHolder(R.layout.filepicker_favorite, parent)

    override fun onBindViewHolder(holder: RecyclerViewAdapter.ViewHolder, position: Int) {
        val path = paths[position]
        holder.bindView(
            path,
            allowSingleClick = true,
            allowLongClick = false
        ) { itemView, _ ->
            setupView(itemView, activity.standardizePath(path))
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = paths.size

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = paths.size

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemKeyPosition(key: Int) = paths.indexOfFirst { it.hashCode() == key }

    override fun getItemSelectionKey(position: Int) = paths.getOrNull(position)?.hashCode()

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun setupView(view: View, path: String) {
        val itemBinding = FilepickerFavoriteBinding.bind(view)
        itemBinding.filepickerFavoriteLabel.text = path
        itemBinding.filepickerFavoriteLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
    }
}
