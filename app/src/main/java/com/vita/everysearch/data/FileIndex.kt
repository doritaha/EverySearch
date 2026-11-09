package com.vita.everysearch.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

data class ScanState(
    val running: Boolean = false,
    val found: Int = 0,
    val phase: String = "",
    val lastScan: Long = 0L,
)

/**
 * In-memory index of every file on the device (Everything-style): scan once, then search is a fast
 * linear pass over names. The index is persisted so the app opens instantly next time.
 */
object FileIndex {

    @Volatile
    var entries: List<FileEntry> = emptyList()
        private set

    /** Bumped whenever [entries] changes so the UI re-runs the current search. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private const val CACHE_VERSION = 2

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scanDispatcher = Dispatchers.IO.limitedParallelism(6)

    // ---------------------------------------------------------------- storage roots

    fun internalRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    /** Internal storage plus any SD card / USB drive currently mounted. */
    fun storageRoots(ctx: Context): List<String> {
        val roots = linkedSetOf(internalRoot())
        ctx.getExternalFilesDirs(null).filterNotNull().forEach { f ->
            val p = f.absolutePath
            val i = p.indexOf("/Android/data")
            if (i > 0) roots.add(p.substring(0, i))
        }
        return roots.filter { File(it).canRead() }
    }

    // ---------------------------------------------------------------- scanning

    private class Interner {
        private val map = ConcurrentHashMap<String, String>()
        fun of(s: String): String = map.putIfAbsent(s, s) ?: s
    }

    suspend fun scan(ctx: Context, useRoot: Boolean) = withContext(Dispatchers.IO) {
        if (_state.value.running) return@withContext
        val counter = AtomicInteger(0)
        _state.value = _state.value.copy(running = true, found = 0, phase = "حافظه گوشی")
        try {
            val interner = Interner()
            val result = ArrayList<FileEntry>(entries.size.coerceAtLeast(50_000))
            val rootOk = useRoot && RootShell.isAvailable()

            for (root in storageRoots(ctx)) {
                _state.value = _state.value.copy(phase = root)
                result.addAll(scanTree(File(root), interner, counter, skipAndroidPrivate = rootOk))
            }

            if (rootOk) {
                _state.value = _state.value.copy(phase = "فایل‌های سیستمی (روت)")
                result.addAll(scanRoot(interner, counter))
            }
            coroutineContext.ensureActive()
            entries = result
            _version.value++
            saveCache(ctx)
        } finally {
            _state.value = _state.value.copy(
                running = false, found = entries.size, phase = "", lastScan = System.currentTimeMillis(),
            )
        }
    }

    private fun tick(counter: AtomicInteger, n: Int = 1) {
        val c = counter.addAndGet(n)
        if (c % 500 < n) _state.value = _state.value.copy(found = c)
    }

    /** Scans a tree with the normal Java file API, top-level folders in parallel. */
    private suspend fun scanTree(
        root: File,
        interner: Interner,
        counter: AtomicInteger,
        skipAndroidPrivate: Boolean,
    ): List<FileEntry> = coroutineScope {
        val top = root.listFiles() ?: return@coroutineScope emptyList()
        val rootPath = interner.of(root.absolutePath)
        val out = ArrayList<FileEntry>()
        val dirs = ArrayList<File>()
        for (f in top) {
            val isDir = f.isDirectory
            out.add(FileEntry(f.name, rootPath, if (isDir) 0 else f.length(), f.lastModified(), isDir, false))
            if (isDir) dirs.add(f)
        }
        tick(counter, top.size)
        val parts = dirs.map { d ->
            async(scanDispatcher) {
                val local = ArrayList<FileEntry>()
                val stack = ArrayDeque<File>()
                stack.addLast(d)
                while (stack.isNotEmpty()) {
                    coroutineContext.ensureActive()
                    val dir = stack.removeLast()
                    val dirPath = dir.absolutePath
                    if (skipAndroidPrivate && (dirPath.endsWith("/Android/data") || dirPath.endsWith("/Android/obb"))) {
                        // Covered by the root scan (normal apps can't read these on Android 11+).
                        continue
                    }
                    val children = dir.listFiles() ?: continue
                    val parent = interner.of(dirPath)
                    for (c in children) {
                        val isDir = c.isDirectory
                        local.add(FileEntry(c.name, parent, if (isDir) 0 else c.length(), c.lastModified(), isDir, false))
                        // Don't follow symlinked folders to avoid loops.
                        if (isDir && !java.nio.file.Files.isSymbolicLink(c.toPath())) stack.addLast(c)
                    }
                    tick(counter, children.size)
                }
                local
            }
        }
        parts.awaitAll().forEach { out.addAll(it) }
        out
    }

    private val ROOT_PRUNE = listOf(
        "/proc", "/sys", "/dev", "/storage", "/sdcard", "/mnt", "/acct", "/apex", "/config", "/d",
        "/data/media", "/data_mirror", "/debug_ramdisk", "/linkerconfig",
    )

    /** System partitions and /data through the root shell. */
    private fun scanRoot(interner: Interner, counter: AtomicInteger): List<FileEntry> {
        val out = ArrayList<FileEntry>()
        val prune = ROOT_PRUNE.joinToString(" -o ") { "-path $it" }
        val internal = internalRoot()
        val cmd = buildString {
            append("find / \\( $prune \\) -prune -o -exec stat -c ${RootShell.STAT_FMT} {} + 2>/dev/null\n")
            // App-private folders on internal storage, hidden from non-root apps since Android 11.
            append("find ${RootShell.quote("$internal/Android/data")} ${RootShell.quote("$internal/Android/obb")} ")
            append("-exec stat -c ${RootShell.STAT_FMT} {} + 2>/dev/null")
        }
        var n = 0
        RootShell.stream(cmd) { line ->
            RootShell.parseStat(line, interner::of)?.let {
                out.add(it)
                if (++n % 200 == 0) tick(counter, 200)
            }
        }
        return out
    }

    // ---------------------------------------------------------------- incremental updates

    /** Removes [path] and everything below it from the index. */
    fun removePath(path: String) {
        val prefix = "$path/"
        entries = entries.filterNot { e ->
            val p = e.path
            p == path || p.startsWith(prefix)
        }
        _version.value++
    }

    /** Adds [path] (and its subtree if it's a folder) to the index. */
    suspend fun addPath(path: String, viaRoot: Boolean) {
        val f = File(path)
        val added = ArrayList<FileEntry>()
        if (viaRoot) {
            withContext(Dispatchers.IO) {
                RootShell.stream("find ${RootShell.quote(path)} -exec stat -c ${RootShell.STAT_FMT} {} + 2>/dev/null") { l ->
                    RootShell.parseStat(l)?.let { added.add(it) }
                }
            }
        } else if (f.exists()) {
            val isDir = f.isDirectory
            added.add(FileEntry(f.name, f.parent ?: "/", if (isDir) 0 else f.length(), f.lastModified(), isDir, false))
            if (isDir) added.addAll(scanTree(f, Interner(), AtomicInteger(), false))
        }
        if (added.isNotEmpty()) {
            entries = entries + added
            _version.value++
        }
    }

    // ---------------------------------------------------------------- cache

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "index.bin")

    suspend fun loadCache(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val f = cacheFile(ctx)
        if (!f.exists()) return@withContext false
        runCatching {
            DataInputStream(BufferedInputStream(f.inputStream(), 1 shl 16)).use { inp ->
                if (inp.readInt() != CACHE_VERSION) return@withContext false
                val time = inp.readLong()
                val parents = Array(inp.readInt()) { inp.readUTF() }
                val n = inp.readInt()
                val list = ArrayList<FileEntry>(n)
                repeat(n) {
                    val parent = parents[inp.readInt()]
                    val name = inp.readUTF()
                    val size = inp.readLong()
                    val mod = inp.readLong()
                    val flags = inp.readByte().toInt()
                    list.add(FileEntry(name, parent, size, mod, flags and 1 != 0, flags and 2 != 0))
                }
                entries = list
                _version.value++
                _state.value = _state.value.copy(found = n, lastScan = time)
            }
            true
        }.getOrDefault(false)
    }

    private fun saveCache(ctx: Context) {
        runCatching {
            val list = entries
            val ids = HashMap<String, Int>()
            val parents = ArrayList<String>()
            for (e in list) if (ids.putIfAbsent(e.parent, parents.size) == null) parents.add(e.parent)
            val tmp = File(ctx.filesDir, "index.tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { o ->
                o.writeInt(CACHE_VERSION)
                o.writeLong(System.currentTimeMillis())
                o.writeInt(parents.size)
                parents.forEach { o.writeUTF(it) }
                o.writeInt(list.size)
                for (e in list) {
                    o.writeInt(ids[e.parent]!!)
                    o.writeUTF(e.name)
                    o.writeLong(e.size)
                    o.writeLong(e.modified)
                    o.writeByte((if (e.isDir) 1 else 0) or (if (e.viaRoot) 2 else 0))
                }
            }
            tmp.renameTo(cacheFile(ctx))
        }
    }

    // ---------------------------------------------------------------- search

    private class Term(val text: String, val regex: Regex?, val onPath: Boolean, val negate: Boolean) {
        fun matches(e: FileEntry): Boolean {
            val target = if (onPath) e.path.lowercase(Locale.ROOT) else e.lower
            val hit = if (regex != null) regex.matches(target) else target.contains(text)
            return hit != negate
        }
    }

    private fun parse(query: String): List<Term> =
        query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }.mapNotNull { raw ->
            val negate = raw.startsWith("!") && raw.length > 1
            val t = if (negate) raw.substring(1) else raw
            if (t.isEmpty()) return@mapNotNull null
            val wild = t.contains('*') || t.contains('?')
            val regex = if (wild) {
                Regex(t.split('*').joinToString(".*") { part -> part.split('?').joinToString(".") { Regex.escape(it) } })
            } else null
            Term(t, regex, t.contains('/'), negate)
        }

    /**
     * Query syntax (like Everything):
     *  - words separated by space must all match (AND)
     *  - `*` and `?` wildcards match the whole name, e.g. `*.pdf`, `IMG_2024*`
     *  - a word containing `/` is matched against the full path, e.g. `dcim/ .jpg`
     *  - `!word` excludes matches
     */
    fun search(
        query: String,
        category: Category,
        sort: SortMode,
        descending: Boolean,
        showHidden: Boolean,
    ): List<FileEntry> {
        val terms = parse(query)
        val src = entries
        val out = ArrayList<FileEntry>()
        for (e in src) {
            if (category != Category.ALL && e.category != category) continue
            if (!showHidden && (e.name.startsWith('.') || e.parent.contains("/."))) continue
            var ok = true
            for (t in terms) if (!t.matches(e)) { ok = false; break }
            if (ok) out.add(e)
        }
        val cmp: Comparator<FileEntry> = when (sort) {
            SortMode.NAME -> compareBy<FileEntry> { !it.isDir }.thenBy { it.lower }
            SortMode.SIZE -> compareBy { it.size }
            SortMode.DATE -> compareBy { it.modified }
            SortMode.PATH -> compareBy { it.path.lowercase(Locale.ROOT) }
        }
        out.sortWith(if (descending) cmp.reversed() else cmp)
        return out
    }

    fun statsByCategory(showHidden: Boolean): Map<Category, Int> {
        val m = HashMap<Category, Int>()
        for (e in entries) {
            if (!showHidden && (e.name.startsWith('.') || e.parent.contains("/."))) continue
            m[e.category] = (m[e.category] ?: 0) + 1
        }
        m[Category.ALL] = m.values.sum()
        return m
    }
}
