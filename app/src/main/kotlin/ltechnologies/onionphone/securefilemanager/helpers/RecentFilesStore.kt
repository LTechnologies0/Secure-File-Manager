package ltechnologies.onionphone.securefilemanager.helpers

import android.content.Context
import ltechnologies.onionphone.securefilemanager.extensions.getSharedPrefs
import org.json.JSONArray

object RecentFilesStore {
    private const val KEY = "recent_files"
    private const val MAX = 50

    fun record(context: Context, path: String) {
        if (path.isEmpty()) {
            return
        }
        val prefs = context.getSharedPrefs()
        val current = load(context).toMutableList()
        current.remove(path)
        current.add(0, path)
        while (current.size > MAX) {
            current.removeAt(current.lastIndex)
        }
        prefs.edit().putString(KEY, JSONArray(current).toString()).apply()
    }

    fun load(context: Context): List<String> {
        val raw = context.getSharedPrefs().getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) { for (i in 0 until arr.length()) add(arr.getString(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun list(context: Context): List<String> = load(context)
}
