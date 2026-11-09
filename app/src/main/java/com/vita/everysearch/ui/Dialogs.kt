package com.vita.everysearch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.vita.everysearch.data.FileEntry
import com.vita.everysearch.data.OpProgress

enum class FileAction { OPEN, OPEN_FOLDER, COPY, MOVE, SHARE, RENAME, DELETE, COPY_PATH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsSheet(
    entry: FileEntry,
    showOpenFolder: Boolean,
    onDismiss: () -> Unit,
    onAction: (FileAction) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FileIcon(entry)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(entry.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    Text(
                        buildString {
                            if (!entry.isDir) append(formatSize(entry.size)).append(" • ")
                            append(formatDate(entry.modified))
                            if (entry.viaRoot) append(" • روت")
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            val items = buildList {
                add(Triple(FileAction.OPEN, Icons.AutoMirrored.Outlined.OpenInNew, if (entry.isDir) "باز کردن پوشه" else "باز کردن فایل"))
                if (showOpenFolder) add(Triple(FileAction.OPEN_FOLDER, Icons.Outlined.FolderOpen, "رفتن به پوشه محل فایل"))
                add(Triple(FileAction.COPY, Icons.Outlined.ContentCopy, "کپی به…"))
                add(Triple(FileAction.MOVE, Icons.AutoMirrored.Outlined.DriveFileMove, "انتقال به…"))
                if (!entry.isDir) add(Triple(FileAction.SHARE, Icons.Outlined.Share, "اشتراک‌گذاری"))
                add(Triple(FileAction.RENAME, Icons.Outlined.DriveFileRenameOutline, "تغییر نام"))
                add(Triple(FileAction.COPY_PATH, Icons.Outlined.Link, "کپی مسیر"))
                add(Triple(FileAction.DELETE, Icons.Outlined.Delete, "حذف"))
            }
            items.forEach { (a, icon, label) ->
                SheetItem(icon, label, if (a == FileAction.DELETE) MaterialTheme.colorScheme.error else null) {
                    onAction(a)
                }
            }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, tint: Color?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val dot = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, dot))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ onConfirm(value.text.trim()) }, enabled = value.text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun ProgressDialog(p: OpProgress, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${p.title}…") },
        text = {
            Column {
                Text(p.current, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.padding(6.dp))
                if (p.total > 0) {
                    LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                    Text("${formatSize(p.done)} / ${formatSize(p.total)}", style = MaterialTheme.typography.labelSmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onCancel) { Text("لغو") } },
    )
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
    Toast.makeText(ctx, "مسیر کپی شد", Toast.LENGTH_SHORT).show()
}

/** Shared state + dialogs for acting on one file, used by both screens. */
class ActionHost {
    var sheetFor by mutableStateOf<FileEntry?>(null)
    var renameFor by mutableStateOf<FileEntry?>(null)
    var deleteFor by mutableStateOf<List<String>?>(null)
}

@Composable
fun rememberActionHost() = remember { ActionHost() }

@Composable
fun ActionDialogs(
    host: ActionHost,
    onRename: (FileEntry, String) -> Unit,
    onDelete: (List<String>) -> Unit,
) {
    host.renameFor?.let { e ->
        TextInputDialog("تغییر نام", e.name, "تغییر", { host.renameFor = null }) {
            host.renameFor = null; onRename(e, it)
        }
    }
    host.deleteFor?.let { list ->
        ConfirmDialog(
            "حذف", if (list.size == 1) "«${list[0].substringAfterLast('/')}» برای همیشه حذف شود؟" else "${list.size} مورد برای همیشه حذف شوند؟",
            "حذف", { host.deleteFor = null },
        ) { host.deleteFor = null; onDelete(list) }
    }
}

@Composable
fun ctx(): Context = LocalContext.current
