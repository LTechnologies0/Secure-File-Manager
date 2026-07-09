package ltechnologies.onionphone.securefilemanager.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class ImagePagerAdapter(
    private val paths: List<String>,
) : RecyclerView.Adapter<ImagePagerAdapter.Holder>() {

    class Holder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val imageView = LayoutInflater.from(parent.context)
            .inflate(ltechnologies.onionphone.securefilemanager.R.layout.item_image_page, parent, false) as ImageView
        return Holder(imageView)
    }

    override fun getItemCount() = paths.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        Glide.with(holder.imageView).load(File(paths[position])).into(holder.imageView)
    }
}
