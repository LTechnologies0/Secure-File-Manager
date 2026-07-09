package ltechnologies.onionphone.securefilemanager.interfaces

import ltechnologies.onionphone.securefilemanager.models.FileDirItem
import java.util.*

interface ItemOperationsListener {
    fun refreshItems()

    fun deleteFiles(files: ArrayList<FileDirItem>)

    fun selectedPaths(paths: ArrayList<String>)

    fun getBrowsingPath(): String
}
