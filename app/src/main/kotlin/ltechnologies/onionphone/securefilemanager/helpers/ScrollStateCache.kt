package ltechnologies.onionphone.securefilemanager.helpers

import android.os.Parcelable

/** Caps scroll-state maps so path navigation cannot grow heap without bound. */
class ScrollStateCache(private val maxEntries: Int = 20) {
    private val states = LinkedHashMap<String, Parcelable>()

    fun put(path: String, state: Parcelable) {
        if (path.isBlank()) return
        states.remove(path)
        states[path] = state
        while (states.size > maxEntries) {
            val oldest = states.keys.firstOrNull() ?: break
            states.remove(oldest)
        }
    }

    fun get(path: String): Parcelable? = states[path]
}
