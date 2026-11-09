package com.vita.everysearch.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class OpProgress(val title: String, val current: String, val done: Long, val total: Long) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

data class OpResult(val ok: Int, val errors: List<String>)

/** Copy / move / delete / rename that falls back to the root shell for protected locations. */
object FileOps {

    private fun canUseNormal(src: String?, destDir: String?): Boolean {
        val s = src?.let { File(it) }
        val d = destDir?.let { File(it) }
        return (s == null || (s.exists() && s.canRead())) && (d == null || (d.exists() && d.canWrite()))
    }

    private fun rootExists(path: String) =
        RootShell.run("[ -e ${RootShell.quote(path)} ] && echo yes").out.any { it.trim() == "yes" }

    /** "name.ext" → "name (1).ext" until it doesn't clash. */
    fun uniqueName(destDir: String, name: String, exists: (String) -> Boolean): String {
        if (!exists("$destDir/$name")) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var i = 1
        while (exists("$destDir/$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    private fun sizeOf(f: File): Long =
        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()

    suspend fun transfer(
        sources: List<String>,
        destDir: String,
        move: Boolean,
        useRoot: Boolean,
        onProgress: (OpProgress) -> Unit,
    ): OpResult = withContext(Dispatchers.IO) {
        val title = if (move) "انتقال" else "کپی"
        val errors = ArrayList<String>()
        var ok = 0
        val normalSources = sources.filter { canUseNormal(it, destDir) }
        val total = normalSources.sumOf { sizeOf(File(it)) }
        var done = 0L

        for (src in sources) {
            coroutineContext.ensureActive()
            val srcFile = File(src)
            if (srcFile.parent == destDir && move) { ok++; continue }
            if (destDir == src || destDir.startsWith("$src/")) {
                errors.add("${srcFile.name}: نمی‌توان پوشه را داخل خودش ${title} کرد")
                continue
            }
            try {
                if (canUseNormal(src, destDir)) {
                    val name = uniqueName(destDir, srcFile.name) { File(it).exists() }
                    val target = File(destDir, name)
                    val renamed = move && srcFile.renameTo(target)
                    if (!renamed) {
                        copyRecursive(srcFile, target) { n, cur ->
                            done += n
                            onProgress(OpProgress(title, cur, done, total))
                        }
                        if (move && !srcFile.deleteRecursively()) errors.add("${srcFile.name}: حذف مبدأ ناموفق بود")
                    } else {
                        done += sizeOf(target)
                    }
                    onProgress(OpProgress(title, srcFile.name, done, total))
                    if (move) FileIndex.removePath(src)
                    FileIndex.addPath(target.absolutePath, viaRoot = false)
                    ok++
                } else if (useRoot && RootShell.isAvailable()) {
                    onProgress(OpProgress("$title (روت)", srcFile.name, 0, 0))
                    val name = uniqueName(destDir, srcFile.name, ::rootExists)
                    val target = "$destDir/$name"
                    val cmd = if (move) "mv" else "cp -a"
                    val r = RootShell.run("$cmd ${RootShell.quote(src)} ${RootShell.quote(target)}")
                    if (r.ok) {
                        if (move) FileIndex.removePath(src)
                        FileIndex.addPath(target, viaRoot = !File(target).canRead())
                        ok++
                    } else errors.add("${srcFile.name}: ${r.err.ifBlank { "خطای روت" }}")
                } else {
                    errors.add("${srcFile.name}: دسترسی ندارید (روت را فعال کنید)")
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                errors.add("${srcFile.name}: ${t.message}")
            }
        }
        OpResult(ok, errors)
    }

    private suspend fun copyRecursive(src: File, dst: File, onBytes: (Long, String) -> Unit) {
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) error("ساخت پوشه ${dst.name} ناموفق بود")
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name), onBytes) }
        } else {
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    val buf = ByteArray(1 shl 17)
                    var since = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        since += n
                        if (since >= (1 shl 20)) { onBytes(since, src.name); since = 0 }
                    }
                    if (since > 0) onBytes(since, src.name)
                }
            }
            dst.setLastModified(src.lastModified())
        }
    }

    suspend fun delete(paths: List<String>, useRoot: Boolean): OpResult = withContext(Dispatchers.IO) {
        val errors = ArrayList<String>()
        var ok = 0
        for (p in paths) {
            val f = File(p)
            val normalOk = f.exists() && f.parentFile?.canWrite() == true && f.deleteRecursively()
            if (normalOk) {
                ok++; FileIndex.removePath(p)
            } else if (useRoot && RootShell.isAvailable()) {
                val r = RootShell.run("rm -rf ${RootShell.quote(p)}")
                if (r.ok) { ok++; FileIndex.removePath(p) } else errors.add("${f.name}: ${r.err}")
            } else errors.add("${f.name}: حذف ناموفق بود")
        }
        OpResult(ok, errors)
    }

    suspend fun rename(path: String, newName: String, useRoot: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/')) return@withContext false
        val f = File(path)
        val target = File(f.parentFile, newName)
        if (target.exists()) return@withContext false
        val done = f.renameTo(target) ||
            (useRoot && RootShell.isAvailable() &&
                RootShell.run("mv ${RootShell.quote(path)} ${RootShell.quote(target.absolutePath)}").ok)
        if (done) {
            FileIndex.removePath(path)
            FileIndex.addPath(target.absolutePath, viaRoot = !target.canRead())
        }
        done
    }

    suspend fun mkdir(parent: String, name: String, useRoot: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (name.isBlank() || name.contains('/')) return@withContext false
        val f = File(parent, name)
        val done = f.mkdir() ||
            (useRoot && RootShell.isAvailable() && RootShell.run("mkdir ${RootShell.quote(f.absolutePath)}").ok)
        if (done) FileIndex.addPath(f.absolutePath, viaRoot = !f.canRead())
        done
    }

    /** Returns a readable file for viewers: the original, or a root-made copy in the cache. */
    suspend fun readableCopy(ctx: Context, path: String): File? = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.canRead()) return@withContext f
        if (!RootShell.isAvailable()) return@withContext null
        val dir = File(ctx.cacheDir, "root_open").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, f.name)
        val r = RootShell.run(
            "cp ${RootShell.quote(path)} ${RootShell.quote(out.absolutePath)} && chmod 666 ${RootShell.quote(out.absolutePath)}",
        )
        if (r.ok && out.canRead()) out else null
    }
}
