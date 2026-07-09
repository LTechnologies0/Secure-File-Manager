package ltechnologies.onionphone.securefilemanager.models

import android.content.Context
import com.bumptech.glide.signature.ObjectKey
import ltechnologies.onionphone.securefilemanager.extensions.*
import ltechnologies.onionphone.securefilemanager.helpers.*
import java.io.File
import java.util.*

open class FileDirItem(
    val path: String,
    val name: String = "",
    var isDirectory: Boolean = false,
    var children: Int = 0,
    var size: Long = 0L,
    var modified: Long = 0L
) :
    Comparable<FileDirItem> {
    companion object {
        var sorting = 0
    }

    override fun toString() =
        "FileDirItem(path=$path, name=$name, isDirectory=$isDirectory, children=$children, size=$size, modified=$modified)"

    override fun compareTo(other: FileDirItem): Int {
        val default = Locale.getDefault()
        return if (isDirectory && !other.isDirectory) {
            -1
        } else if (!isDirectory && other.isDirectory) {
            1
        } else {
            var result: Int
            when {
                sorting and SORT_BY_NAME != 0 -> result =
                    name.lowercase(default).compareTo(other.name.lowercase(default))
                sorting and SORT_BY_SIZE != 0 -> result = when {
                    size == other.size -> 0
                    size > other.size -> 1
                    else -> -1
                }
                sorting and SORT_BY_DATE_MODIFIED != 0 -> {
                    result = when {
                        modified == other.modified -> 0
                        modified > other.modified -> 1
                        else -> -1
                    }
                }
                else -> {
                    result =
                        getExtension().lowercase(default)
                            .compareTo(other.getExtension().lowercase(default))
                }
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }
            result
        }
    }

    private fun getExtension() = if (isDirectory) name else path.substringAfterLast('.', "")

    fun getBubbleText(context: Context, dateFormat: String? = null, timeFormat: String? = null) =
        when {
            sorting and SORT_BY_SIZE != 0 -> size.formatSize()
            sorting and SORT_BY_DATE_MODIFIED != 0 -> modified.formatDate(
                context,
                dateFormat,
                timeFormat
            )
            sorting and SORT_BY_EXTENSION != 0 -> getExtension().lowercase(Locale.getDefault())
            else -> name
        }

    fun getProperSize(): Long = File(path).getProperSize()

    fun getProperFileCount(): Int = File(path).getFileCount()

    fun getDirectChildrenCount(): Int = File(path).getDirectChildrenCount()

    fun getLastModified(): Long = File(path).lastModified()

    fun getParentPath() = path.getParentPath()

    fun getDuration() = path.getDuration()

    fun getArtist() = path.getFileArtist()

    fun getAlbum() = path.getFileAlbum()

    fun getSongTitle() = path.getFileSongTitle()

    fun getResolution(context: Context) = context.getResolution(path)

    fun isEncrypted() = name.isEncrypted()

    fun getSignature(): String {
        val lastModified = if (modified > 1) {
            modified
        } else {
            File(path).lastModified()
        }

        return "$path-$lastModified-$size"
    }

    fun getKey() = ObjectKey(getSignature())

}
