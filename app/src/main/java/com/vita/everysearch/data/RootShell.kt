package com.vita.everysearch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader

/** Minimal `su` wrapper (Magisk / KernelSU / APatch all accept commands on stdin). */
object RootShell {

    @Volatile
    private var cached: Boolean? = null

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Asks for root once; the root manager shows its grant dialog the first time. */
    suspend fun isAvailable(force: Boolean = false): Boolean {
        if (!force) cached?.let { return it }
        val ok = withTimeoutOrNull(15_000) {
            withContext(Dispatchers.IO) {
                runCatching { run("id").out.any { it.contains("uid=0") } }.getOrDefault(false)
            }
        } ?: false
        cached = ok
        return ok
    }

    val knownAvailable: Boolean get() = cached == true

    class Result(val code: Int, val out: List<String>, val err: String) {
        val ok get() = code == 0
    }

    /** Runs a command as root and returns the whole output. */
    fun run(cmd: String): Result {
        val out = ArrayList<String>()
        var err = ""
        val code = stream(cmd, onErr = { err = it }) { out.add(it) }
        return Result(code, out, err)
    }

    /** Runs a command as root, delivering stdout line by line (for large listings). */
    fun stream(cmd: String, onErr: (String) -> Unit = {}, onLine: (String) -> Unit): Int {
        val p = ProcessBuilder("su").start()
        val errThread = Thread {
            onErr(runCatching { p.errorStream.bufferedReader().readText() }.getOrDefault(""))
        }.apply { start() }
        p.outputStream.bufferedWriter().use {
            it.write(cmd)
            it.write("\nexit\n")
            it.flush()
        }
        BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8), 1 shl 16).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                onLine(line)
            }
        }
        val code = p.waitFor()
        errThread.join(2000)
        return code
    }

    /**
     * Lists entries using toybox `find` + `stat`. Each output line is `type|size|mtime|path`.
     * [parseStat] turns those lines into [FileEntry]s.
     */
    const val STAT_FMT = "'%F|%s|%Y|%n'"

    fun parseStat(line: String, intern: (String) -> String = { it }): FileEntry? {
        val a = line.indexOf('|'); if (a < 0) return null
        val b = line.indexOf('|', a + 1); if (b < 0) return null
        val c = line.indexOf('|', b + 1); if (c < 0) return null
        val type = line.substring(0, a)
        val size = line.substring(a + 1, b).toLongOrNull() ?: 0L
        val mtime = (line.substring(b + 1, c).toLongOrNull() ?: 0L) * 1000L
        val path = line.substring(c + 1)
        if (path.isEmpty() || path == "/") return null
        val slash = path.lastIndexOf('/')
        val parent = intern(if (slash <= 0) "/" else path.substring(0, slash))
        val name = path.substring(slash + 1)
        if (name.isEmpty()) return null
        val isDir = type == "directory"
        return FileEntry(name, parent, if (isDir) 0 else size, mtime, isDir, viaRoot = true)
    }

    fun listDir(dir: String): List<FileEntry> {
        val res = ArrayList<FileEntry>()
        stream("find ${quote(dir)} -mindepth 1 -maxdepth 1 -exec stat -c $STAT_FMT {} + 2>/dev/null") { line ->
            parseStat(line)?.let { res.add(it) }
        }
        return res
    }
}
