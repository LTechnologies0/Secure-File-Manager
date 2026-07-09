package ltechnologies.onionphone.securefilemanager.helpers

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

object RecyclerViewTuning {
    fun apply(recyclerView: RecyclerView, fixedSize: Boolean = true) {
        recyclerView.setHasFixedSize(fixedSize)
        recyclerView.setItemViewCacheSize(24)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }
}
