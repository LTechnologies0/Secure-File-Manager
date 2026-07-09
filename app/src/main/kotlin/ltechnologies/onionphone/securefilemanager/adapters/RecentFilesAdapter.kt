package ltechnologies.onionphone.securefilemanager.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ltechnologies.onionphone.securefilemanager.databinding.ItemRecentFileBinding
import ltechnologies.onionphone.securefilemanager.extensions.humanizePath
import java.io.File

class RecentFilesAdapter(
    private val paths: List<String>,
    private val onClick: (String) -> Unit,
) : RecyclerView.Adapter<RecentFilesAdapter.Holder>() {

    class Holder(val binding: ItemRecentFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemRecentFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount() = paths.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val path = paths[position]
        val file = File(path)
        holder.binding.recentName.text = file.name
        holder.binding.recentPath.text = holder.itemView.context.humanizePath(file.parent ?: "")
        holder.itemView.setOnClickListener { onClick(path) }
    }
}
