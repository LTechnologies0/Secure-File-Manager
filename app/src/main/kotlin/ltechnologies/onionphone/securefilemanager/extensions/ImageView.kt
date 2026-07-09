package ltechnologies.onionphone.securefilemanager.extensions

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.widget.ImageView

fun ImageView.applyColorFilter(color: Int) = setColorFilter(color, PorterDuff.Mode.SRC_IN)

fun ImageView.setImageFromPath(image: Any, placeholder: Drawable? = null) {
    if (image is String && image.isImageSlow()) {
        this.setImageBitmap(image.decodeSampledBitmap() ?: return)
    } else if (image is Drawable) {
        this.setImageDrawable(image)
    } else if (image is Int) {
        this.setImageResource(image)
    } else if (image is Bitmap) {
        this.setImageBitmap(image)
    } else if (image is Icon) {
        this.setImageIcon(image)
    } else if (image is Uri) {
        this.setImageURI(image)
    } else {
        this.setImageDrawable(placeholder)
    }
}
