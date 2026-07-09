package ltechnologies.onionphone.securefilemanager.helpers

object FileClipboard {
    var paths: List<String> = emptyList()
        private set
    var isCut: Boolean = false
        private set

    fun set(items: List<String>, isCut: Boolean) {
        paths = items
        this.isCut = isCut
    }

    fun hasContent() = paths.isNotEmpty()

    fun clear() {
        paths = emptyList()
        isCut = false
    }
}
