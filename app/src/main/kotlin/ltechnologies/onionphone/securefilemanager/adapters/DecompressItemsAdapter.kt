package ltechnologies.onionphone.securefilemanager.adapters

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import ltechnologies.onionphone.securefilemanager.models.ListItem
import ltechnologies.onionphone.securefilemanager.databinding.ItemListFileDirBinding
import ltechnologies.onionphone.securefilemanager.views.MyRecyclerView

class DecompressItemsAdapter(
    activity: BaseAbstractActivity,
    listItems: MutableList<ListItem>,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) :
    ItemAbstractAdapter(activity, listItems, recyclerView, null, itemClick) {

    override fun getSelectableItemCount() = 0

    override fun getItemSelectionKey(position: Int) = 0

    override fun getItemKeyPosition(key: Int) = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        createViewHolder(R.layout.item_decompression_list_file_dir, parent)

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val icon = ItemListFileDirBinding.bind(holder.itemView).itemIcon
            Glide.with(activity).clear(icon)
        }
    }

    override fun setupView(view: View, fileDirItem: FileDirItem) {
        val itemBinding = ItemListFileDirBinding.bind(view)
        val fileName = fileDirItem.name
        itemBinding.itemName.text = fileName
        itemBinding.itemName.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

        super.setFileIcon(itemBinding.itemIcon, fileDirItem)
    }
}
