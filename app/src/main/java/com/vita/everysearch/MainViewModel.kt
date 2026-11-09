package com.vita.everysearch

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vita.everysearch.data.Category
import com.vita.everysearch.data.FileEntry
import com.vita.everysearch.data.FileIndex
import com.vita.everysearch.data.FileOps
import com.vita.everysearch.data.OpProgress
import com.vita.everysearch.data.RootShell
import com.vita.everysearch.data.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SearchParams(
    val query: String = "",
    val category: Category = Category.ALL,
    val sort: SortMode = SortMode.NAME,
    val descending: Boolean = false,
    val showHidden: Boolean = false,
)

data class Clipboard(val paths: List<String>, val move: Boolean)

data class BrowserState(
    val path: String,
    val items: List<FileEntry> = emptyList(),
    val loading: Boolean = true,
    val highlight: String? = null,
    val selection: Set<String> = emptySet(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _params = MutableStateFlow(
        SearchParams(
            sort = runCatching { SortMode.valueOf(prefs.getString("sort", "NAME")!!) }.getOrDefault(SortMode.NAME),
            descending = prefs.getBoolean("desc", false),
            showHidden = prefs.getBoolean("hidden", false),
        ),
    )
    val params: StateFlow<SearchParams> = _params.asStateFlow()

    private val _useRoot = MutableStateFlow(prefs.getBoolean("root", false))
    val useRoot: StateFlow<Boolean> = _useRoot.asStateFlow()

    private val _results = MutableStateFlow<List<FileEntry>>(emptyList())
    val results: StateFlow<List<FileEntry>> = _results.asStateFlow()

    private val _counts = MutableStateFlow<Map<Category, Int>>(emptyMap())
    val counts: StateFlow<Map<Category, Int>> = _counts.asStateFlow()

    val scanState = FileIndex.state

    private val _browser = MutableStateFlow<BrowserState?>(null)
    val browser: StateFlow<BrowserState?> = _browser.asStateFlow()

    private val _clipboard = MutableStateFlow<Clipboard?>(null)
    val clipboard: StateFlow<Clipboard?> = _clipboard.asStateFlow()

    private val _progress = MutableStateFlow<OpProgress?>(null)
    val progress: StateFlow<OpProgress?> = _progress.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var started = false
    private var opJob: Job? = null

    init {
        viewModelScope.launch {
            var first = true
            combine(_params, FileIndex.version) { p, _ -> p }.collect { p ->
                if (!first) delay(120) // debounce typing
                first = false
                val list = withContext(Dispatchers.Default) {
                    FileIndex.search(p.query, p.category, p.sort, p.descending, p.showHidden)
                }
                _results.value = list
                _counts.value = withContext(Dispatchers.Default) { FileIndex.statsByCategory(p.showHidden) }
            }
        }
    }

    /** Called once storage permission is granted. */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val hadCache = FileIndex.loadCache(getApplication())
            if (_useRoot.value) RootShell.isAvailable()
            // Refresh in the background; the cached index is usable meanwhile.
            if (!hadCache || System.currentTimeMillis() - FileIndex.state.value.lastScan > 5 * 60_000) rescan()
        }
    }

    fun rescan() {
        viewModelScope.launch { FileIndex.scan(getApplication(), _useRoot.value) }
    }

    // ---------------------------------------------------------------- search params

    fun setQuery(q: String) = _params.update { it.copy(query = q) }
    fun setCategory(c: Category) = _params.update { it.copy(category = c) }
    fun setSort(s: SortMode) {
        _params.update {
            // Tapping the active sort flips direction.
            if (it.sort == s) it.copy(descending = !it.descending)
            else it.copy(sort = s, descending = s == SortMode.SIZE || s == SortMode.DATE)
        }
        prefs.edit().putString("sort", _params.value.sort.name).putBoolean("desc", _params.value.descending).apply()
    }

    fun setShowHidden(v: Boolean) {
        _params.update { it.copy(showHidden = v) }
        prefs.edit().putBoolean("hidden", v).apply()
        _browser.value?.let { openFolder(it.path, it.highlight) }
    }

    fun setUseRoot(v: Boolean) {
        viewModelScope.launch {
            if (v && !RootShell.isAvailable(force = true)) {
                _messages.send("دسترسی روت پیدا نشد یا رد شد")
                return@launch
            }
            _useRoot.value = v
            prefs.edit().putBoolean("root", v).apply()
            _messages.send(if (v) "روت فعال شد — نمایه‌سازی دوباره…" else "روت غیرفعال شد")
            rescan()
        }
    }

    private inline fun <T> MutableStateFlow<T>.update(f: (T) -> T) { value = f(value) }

    // ---------------------------------------------------------------- browser

    fun openFolder(path: String, highlight: String? = null) {
        val prevSelection = _browser.value?.takeIf { it.path == path }?.selection ?: emptySet()
        _browser.value = BrowserState(path, _browser.value?.takeIf { it.path == path }?.items ?: emptyList(),
            loading = true, highlight = highlight, selection = prevSelection)
        viewModelScope.launch {
            val (items, err) = withContext(Dispatchers.IO) { listFolder(path) }
            val showHidden = _params.value.showHidden
            val sorted = items
                .filter { showHidden || !it.name.startsWith('.') || it.name == highlight }
                .sortedWith(compareBy<FileEntry> { !it.isDir }.thenBy { it.lower })
            if (_browser.value?.path == path) {
                _browser.value = _browser.value!!.copy(items = sorted, loading = false, error = err)
            }
        }
    }

    private suspend fun listFolder(path: String): Pair<List<FileEntry>, String?> {
        val dir = File(path)
        val files = dir.listFiles()
        if (files != null && (files.isNotEmpty() || !_useRoot.value)) {
            return files.map {
                val d = it.isDirectory
                FileEntry(it.name, path, if (d) 0 else it.length(), it.lastModified(), d, false)
            } to null
        }
        if (_useRoot.value && RootShell.isAvailable()) {
            return RootShell.listDir(path) to null
        }
        return emptyList<FileEntry>() to if (files == null) "دسترسی به این پوشه ممکن نیست (روت لازم است)" else null
    }

    fun closeBrowser() { _browser.value = null }

    /** Back inside the browser: clear selection, then go up, then close. Returns false when closed. */
    fun browserBack(): Boolean {
        val b = _browser.value ?: return false
        if (b.selection.isNotEmpty()) { _browser.value = b.copy(selection = emptySet()); return true }
        val parent = File(b.path).parent
        if (parent == null || b.path == "/") { closeBrowser(); return false }
        openFolder(parent, highlight = File(b.path).name)
        return true
    }

    fun toggleSelect(path: String) {
        val b = _browser.value ?: return
        _browser.value = b.copy(selection = if (path in b.selection) b.selection - path else b.selection + path)
    }

    fun selectAll() {
        val b = _browser.value ?: return
        _browser.value = b.copy(selection = if (b.selection.size == b.items.size) emptySet() else b.items.map { it.path }.toSet())
    }

    // ---------------------------------------------------------------- clipboard & operations

    fun setClipboard(paths: List<String>, move: Boolean) {
        if (paths.isEmpty()) return
        _clipboard.value = Clipboard(paths, move)
        _browser.value?.let { _browser.value = it.copy(selection = emptySet()) }
        viewModelScope.launch {
            _messages.send("${paths.size} مورد آماده ${if (move) "انتقال" else "کپی"} — پوشه مقصد را باز کنید و «چسباندن» را بزنید")
        }
    }

    fun clearClipboard() { _clipboard.value = null }

    fun paste() {
        val clip = _clipboard.value ?: return
        val dest = _browser.value?.path ?: return
        runOp {
            val r = FileOps.transfer(clip.paths, dest, clip.move, _useRoot.value) { _progress.value = it }
            _clipboard.value = null
            report(r.ok, r.errors, if (clip.move) "منتقل شد" else "کپی شد")
            openFolder(dest)
        }
    }

    fun delete(paths: List<String>) = runOp {
        _progress.value = OpProgress("حذف", "", 0, 0)
        val r = FileOps.delete(paths, _useRoot.value)
        report(r.ok, r.errors, "حذف شد")
        _browser.value?.let { openFolder(it.path) }
    }

    fun rename(path: String, newName: String) = runOp {
        val ok = FileOps.rename(path, newName, _useRoot.value)
        _messages.send(if (ok) "نام تغییر کرد" else "تغییر نام ناموفق بود")
        _browser.value?.let { openFolder(it.path, highlight = newName) }
    }

    fun mkdir(name: String) {
        val dir = _browser.value?.path ?: return
        runOp {
            val ok = FileOps.mkdir(dir, name, _useRoot.value)
            _messages.send(if (ok) "پوشه ساخته شد" else "ساخت پوشه ناموفق بود")
            openFolder(dir, highlight = name)
        }
    }

    fun cancelOp() { opJob?.cancel() }

    private fun runOp(block: suspend () -> Unit) {
        if (opJob?.isActive == true) {
            viewModelScope.launch { _messages.send("یک عملیات در حال انجام است") }
            return
        }
        opJob = viewModelScope.launch {
            try { block() } finally { _progress.value = null }
        }
    }

    private suspend fun report(ok: Int, errors: List<String>, verb: String) {
        _messages.send(
            if (errors.isEmpty()) "$ok مورد $verb"
            else "$ok مورد $verb، ${errors.size} خطا: ${errors.first()}",
        )
    }

    fun message(text: String) { viewModelScope.launch { _messages.send(text) } }
}
