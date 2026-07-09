package ltechnologies.onionphone.securefilemanager.adapters

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.activities.BaseAbstractActivity
import ltechnologies.onionphone.securefilemanager.extensions.formatSize
import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import ltechnologies.onionphone.securefilemanager.databinding.FilepickerListItemBinding
import ltechnologies.onionphone.securefilemanager.views.MyRecyclerView

class FilePickerItemsAdapter(
    activity: BaseAbstractActivity,
    fileDirItems: List<FileDirItem>,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : ItemAbstractAdapter(activity, fileDirItems, recyclerView, null, itemClick) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        createViewHolder(R.layout.filepicker_list_item, parent)

    override fun setupView(view: View, fileDirItem: FileDirItem) {
        val itemBinding = FilepickerListItemBinding.bind(view)
        itemBinding.listItemName.text = fileDirItem.name
        itemBinding.listItemName.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

        itemBinding.listItemDetails.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)

        super.setFileIcon(itemBinding.listItemIcon, fileDirItem)

        if (fileDirItem.isDirectory) {
            itemBinding.listItemDetails.text = getChildrenCnt(fileDirItem)
        } else {
            itemBinding.listItemDetails.text = fileDirItem.size.formatSize()
        }
    }

}
