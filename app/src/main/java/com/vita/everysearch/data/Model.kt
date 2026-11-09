package com.vita.everysearch.data

import java.util.Locale

enum class Category(val label: String) {
    ALL("همه"),
    FOLDER("پوشه"),
    VIDEO("فیلم"),
    AUDIO("صوت"),
    IMAGE("تصویر"),
    PDF("PDF"),
    DOCUMENT("سند"),
    ARCHIVE("فشرده"),
    APK("برنامه"),
    OTHER("سایر");

    companion object {
        private val map: Map<String, Category> = buildMap {
            listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "mpg", "mpeg", "vob", "m2ts", "rmvb")
                .forEach { put(it, VIDEO) }
            listOf("mp3", "wav", "flac", "aac", "ogg", "opus", "m4a", "wma", "amr", "mid", "midi", "3ga", "aiff", "ape", "m4b")
                .forEach { put(it, AUDIO) }
            listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "tif", "tiff", "dng", "raw", "ico", "avif")
                .forEach { put(it, IMAGE) }
            put("pdf", PDF)
            listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "csv", "md", "epub",
                "html", "htm", "xml", "json", "log", "srt", "ass")
                .forEach { put(it, DOCUMENT) }
            listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "zst", "lz4", "img", "iso")
                .forEach { put(it, ARCHIVE) }
            listOf("apk", "apks", "xapk", "apkm", "aab").forEach { put(it, APK) }
        }

        fun of(name: String, isDir: Boolean): Category {
            if (isDir) return FOLDER
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) return OTHER
            return map[name.substring(dot + 1).lowercase(Locale.ROOT)] ?: OTHER
        }
    }
}

/**
 * One indexed item. All children of a folder share the same [parent] String instance,
 * which keeps memory low on phones with hundreds of thousands of files.
 */
class FileEntry(
    val name: String,
    val parent: String,
    val size: Long,
    val modified: Long,
    val isDir: Boolean,
    /** true when the item was found through the root shell and is not readable by the app itself. */
    val viaRoot: Boolean,
) {
    val lower: String = name.lowercase(Locale.ROOT)
    val category: Category = Category.of(name, isDir)
    val path: String get() = if (parent == "/") "/$name" else "$parent/$name"
}

enum class SortMode(val label: String) {
    NAME("نام"), SIZE("حجم"), DATE("تاریخ"), PATH("مسیر")
}
