package com.vita.everysearch.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.core.content.FileProvider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.vita.everysearch.data.Category
import com.vita.everysearch.data.FileEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Category.icon(): ImageVector = when (this) {
    Category.ALL -> Icons.Outlined.GridView
    Category.FOLDER -> Icons.Outlined.Folder
    Category.VIDEO -> Icons.Outlined.Movie
    Category.AUDIO -> Icons.Outlined.MusicNote
    Category.IMAGE -> Icons.Outlined.Image
    Category.PDF -> Icons.Outlined.PictureAsPdf
    Category.DOCUMENT -> Icons.AutoMirrored.Outlined.Article
    Category.ARCHIVE -> Icons.Outlined.FolderZip
    Category.APK -> Icons.Outlined.Android
    Category.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

fun Category.color(): Color = when (this) {
    Category.ALL -> Color(0xFF8B8CFF)
    Category.FOLDER -> Color(0xFFF5B83D)
    Category.VIDEO -> Color(0xFFEF5DA8)
    Category.AUDIO -> Color(0xFF34D399)
    Category.IMAGE -> Color(0xFF60A5FA)
    Category.PDF -> Color(0xFFF87171)
    Category.DOCUMENT -> Color(0xFF38BDF8)
    Category.ARCHIVE -> Color(0xFFA78BFA)
    Category.APK -> Color(0xFF84CC16)
    Category.OTHER -> Color(0xFF94A3B8)
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

private val dateFmt = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US)
fun formatDate(ms: Long): String = if (ms <= 0) "" else dateFmt.format(Date(ms))

/** Bold-highlights each plain search word inside [text]. */
fun highlight(text: String, query: String, color: Color): AnnotatedString {
    val words = query.lowercase(Locale.ROOT).split(' ')
        .filter { it.isNotBlank() && !it.startsWith("!") && !it.contains('*') && !it.contains('?') && !it.contains('/') }
    if (words.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase(Locale.ROOT)
    val marks = BooleanArray(text.length)
    for (w in words) {
        var i = lower.indexOf(w)
        while (i >= 0) {
            for (k in i until (i + w.length).coerceAtMost(text.length)) marks[k] = true
            i = lower.indexOf(w, i + w.length)
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val m = marks[i]
            var j = i
            while (j < text.length && marks[j] == m) j++
            if (m) pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, background = color.copy(alpha = 0.15f)))
            append(text.substring(i, j))
            if (m) pop()
            i = j
        }
    }
}

/** Photos and videos the app can read directly get a real thumbnail. */
val FileEntry.hasPreview: Boolean
    get() = !viaRoot && (category == Category.IMAGE || category == Category.VIDEO)

/** Thumbnail of a photo, or a frame from a video, cached by Coil (memory + disk). */
@Composable
fun MediaThumb(entry: FileEntry, sizePx: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val request = remember(entry.path, entry.modified, sizePx) {
        ImageRequest.Builder(ctx)
            .data(File(entry.path))
            .size(sizePx)
            .memoryCacheKey("${entry.path}#${entry.modified}#$sizePx")
            .diskCacheKey("${entry.path}#${entry.modified}#$sizePx")
            .apply { if (entry.category == Category.VIDEO) videoFrameMillis(1000) }
            .build()
    }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
fun PlayBadge(modifier: Modifier = Modifier, size: Int = 16) {
    Box(
        modifier.size((size + 8).dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size.dp)) }
}

@Composable
fun FileIcon(entry: FileEntry, selected: Boolean = false) {
    val c = entry.category.color()
    val preview = entry.hasPreview && !selected
    Box(
        Modifier.size(if (entry.hasPreview) 56.dp else 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (selected) Icons.Outlined.CheckCircle else entry.category.icon(), null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else c,
            modifier = Modifier.size(24.dp),
        )
        if (preview) {
            MediaThumb(entry, 192, Modifier.matchParentSize())
            if (entry.category == Category.VIDEO) PlayBadge(Modifier.align(Alignment.Center), 14)
        }
        if (entry.viaRoot && !selected) {
            Icon(
                Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).padding(1.dp),
            )
        }
    }
}

private val ltr = TextStyle(textDirection = TextDirection.Ltr)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    entry: FileEntry,
    query: String,
    showPath: Boolean,
    selected: Boolean = false,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    selected -> cs.primaryContainer
                    highlighted -> cs.secondaryContainer
                    else -> Color.Transparent
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(entry, selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                highlight(entry.name, query, cs.primary),
                style = MaterialTheme.typography.bodyLarge.merge(ltr),
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showPath) {
                Text(
                    entry.parent, style = MaterialTheme.typography.bodySmall.merge(ltr),
                    color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                buildString {
                    if (!entry.isDir) append(formatSize(entry.size)).append("   ")
                    append(formatDate(entry.modified))
                },
                style = MaterialTheme.typography.labelSmall.merge(ltr), color = cs.outline,
            )
        }
        trailing()
    }
}

// ------------------------------------------------------------------ intents

fun mimeOf(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
        "apk", "apks", "xapk" -> "application/vnd.android.package-archive"
        "mkv" -> "video/x-matroska"
        else -> "*/*"
    }
}

fun uriFor(ctx: Context, file: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)

fun openFile(ctx: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(ctx, file), mimeOf(file.name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(Intent.createChooser(intent, "باز کردن با").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "برنامه‌ای برای باز کردن این فایل نیست", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "باز کردن ناموفق: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(ctx: Context, files: List<File>) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { uriFor(ctx, it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).setType(mimeOf(files[0].name)).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "اشتراک‌گذاری ناموفق: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
