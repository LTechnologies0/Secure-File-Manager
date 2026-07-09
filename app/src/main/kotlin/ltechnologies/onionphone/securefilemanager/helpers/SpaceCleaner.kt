package ltechnologies.onionphone.securefilemanager.helpers

import java.io.File
import java.security.MessageDigest

enum class SpaceCategory {
    DUPLICATES,
    TEMP,
    CACHE,
    EMPTY,
}

data class ReclaimGroup(
    val category: SpaceCategory,
    /** All paths in this group (for display). */
    val paths: List<String>,
    /** Paths to move to trash when this group is cleaned. */
    val pathsToDelete: List<String>,
    /** Bytes reclaimed if this group is cleaned. */
    val reclaimableBytes: Long,
) {
    val key: Int get() = category.ordinal * 31 + paths.joinToString().hashCode()
}

object SpaceCleaner {

    private val TEMP_EXTENSIONS = setOf(
        "tmp", "temp", "bak", "old", "swp", "download", "partial", "crdownload", "part",
    )

    private val CACHE_DIR_NAMES = setOf(
        "cache", ".cache", "caches", "tmp", "temp", ".thumbnails", "thumbdata", "thumbnail",
    )

    // ponytail: single BFS pass — duplicates + temp/cache/empty heuristics; upgrade: MIME rules, age filter
    fun scan(root: String, isActive: () -> Boolean): List<ReclaimGroup>? {
        val rootFile = File(root)
        if (!rootFile.exists()) {
            return emptyList()
        }

        val bySize = HashMap<Long, MutableList<File>>()
        val tempFiles = ArrayList<File>()
        val cacheFiles = ArrayList<File>()
        val emptyFiles = ArrayList<File>()

        val queue = ArrayDeque<File>()
        queue.add(rootFile)
        while (queue.isNotEmpty()) {
            if (!isActive()) {
                return null
            }
            val dir = queue.removeFirst()
            dir.listFiles()?.forEach { file ->
                if (!isActive()) {
                    return null
                }
                if (file.isDirectory) {
                    if (!file.name.startsWith(".")) {
                        queue.add(file)
                    }
                } else if (!file.name.startsWith(".")) {
                    when {
                        file.length() == 0L -> emptyFiles.add(file)
                        isTempFile(file) -> tempFiles.add(file)
                        isInCacheDir(file, root) -> cacheFiles.add(file)
                        file.length() > 0L -> bySize.getOrPut(file.length()) { mutableListOf() }.add(file)
                    }
                }
            }
        }

        val groups = ArrayList<ReclaimGroup>()
        groups.addAll(findDuplicateGroups(bySize, isActive) ?: return null)
        addBatchGroup(groups, SpaceCategory.TEMP, tempFiles)
        addBatchGroup(groups, SpaceCategory.CACHE, cacheFiles)
        addBatchGroup(groups, SpaceCategory.EMPTY, emptyFiles)
        return groups.sortedWith(
            compareByDescending<ReclaimGroup> { it.reclaimableBytes }
                .thenBy { it.category.ordinal },
        )
    }

    internal fun isTempFile(file: File): Boolean {
        val name = file.name.lowercase()
        val ext = file.extension.lowercase()
        return ext in TEMP_EXTENSIONS ||
            name.endsWith(".tmp") ||
            name.endsWith("~") ||
            name.startsWith("~$")
    }

    internal fun isInCacheDir(file: File, root: String): Boolean {
        val prefix = root.trimEnd('/')
        val relative = file.absolutePath
            .removePrefix(prefix)
            .trimStart('/')
            .lowercase()
        return relative.split('/').any { segment -> segment in CACHE_DIR_NAMES }
    }

    private fun addBatchGroup(groups: MutableList<ReclaimGroup>, category: SpaceCategory, files: List<File>) {
        if (files.isEmpty()) {
            return
        }
        val paths = files.map { it.absolutePath }
        val reclaimable = files.sumOf { it.length() }
        groups.add(
            ReclaimGroup(
                category = category,
                paths = paths,
                pathsToDelete = paths,
                reclaimableBytes = reclaimable,
            ),
        )
    }

    private fun findDuplicateGroups(
        bySize: Map<Long, List<File>>,
        isActive: () -> Boolean,
    ): List<ReclaimGroup>? {
        val partialMatches = HashMap<String, MutableList<File>>()
        val partialSize = 64 * 1024
        bySize.values.filter { it.size > 1 }.forEach { files ->
            files.forEach { file ->
                if (!isActive()) {
                    return null
                }
                val partial = partialHash(file, partialSize)
                val key = "${file.length()}:$partial"
                partialMatches.getOrPut(key) { mutableListOf() }.add(file)
            }
        }

        val results = ArrayList<ReclaimGroup>()
        partialMatches.values.filter { it.size > 1 }.forEach { candidates ->
            if (!isActive()) {
                return null
            }
            val byFullHash = HashMap<String, MutableList<File>>()
            candidates.forEach { file ->
                val full = fullHash(file)
                byFullHash.getOrPut(full) { mutableListOf() }.add(file)
            }
            byFullHash.values.filter { it.size > 1 }.forEach { dupes ->
                val paths = dupes.map { it.absolutePath }
                val perFile = dupes.first().length()
                val toDelete = paths.drop(1)
                results.add(
                    ReclaimGroup(
                        category = SpaceCategory.DUPLICATES,
                        paths = paths,
                        pathsToDelete = toDelete,
                        reclaimableBytes = perFile * toDelete.size,
                    ),
                )
            }
        }
        return results
    }

    private fun partialHash(file: File, maxBytes: Int): String {
        val digest = MessageDigest.getInstance(MD5)
        file.inputStream().use { input ->
            val buffer = ByteArray(minOf(maxBytes, 8192))
            var remaining = maxBytes
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fullHash(file: File): String {
        val digest = MessageDigest.getInstance(SHA256)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read > 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
