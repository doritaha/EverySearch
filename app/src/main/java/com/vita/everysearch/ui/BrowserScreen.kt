package com.vita.everysearch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vita.everysearch.BrowserState
import com.vita.everysearch.Clipboard
import com.vita.everysearch.data.FileEntry
import com.vita.everysearch.data.FileIndex
import java.io.File

@Composable
fun BrowserScreen(
    state: BrowserState,
    clipboard: Clipboard?,
    roots: List<String>,
    useRoot: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpen: (FileEntry) -> Unit,
    onLongClick: (FileEntry) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClipboard: (List<String>, Boolean) -> Unit,
    onShare: (List<String>) -> Unit,
    onDelete: (List<String>) -> Unit,
    onPaste: () -> Unit,
    onCancelClipboard: () -> Unit,
    onMkdir: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val selecting = state.selection.isNotEmpty()
    val listState = rememberLazyListState()
    var newFolder by remember { mutableStateOf(false) }

    // Scroll to the file we came for (from search) and keep it highlighted.
    LaunchedEffect(state.path, state.loading, state.highlight) {
        if (!state.loading) {
            val i = state.items.indexOfFirst { it.name == state.highlight }
            listState.scrollToItem(if (i >= 0) (i - 3).coerceAtLeast(0) else 0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = if (selecting) cs.primaryContainer else cs.surfaceContainer, tonalElevation = 2.dp) {
            Column(Modifier.statusBarsPadding()) {
                // ------------------------------------------------ top bar
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selecting) {
                        IconButton(onBack) { Icon(Icons.Outlined.Close, "لغو انتخاب") }
                        Text("${state.selection.size} انتخاب شده", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onSelectAll) { Icon(Icons.Outlined.SelectAll, "انتخاب همه") }
                        IconButton({ onClipboard(state.selection.toList(), false) }) { Icon(Icons.Outlined.ContentCopy, "کپی") }
                        IconButton({ onClipboard(state.selection.toList(), true) }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "انتقال") }
                        IconButton({ onShare(state.selection.toList()) }) { Icon(Icons.Outlined.Share, "اشتراک") }
                        IconButton({ onDelete(state.selection.toList()) }) { Icon(Icons.Outlined.Delete, "حذف", tint = cs.error) }
                    } else {
                        IconButton(onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "بازگشت به جستجو") }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.path == "/") "ریشه سیستم" else File(state.path).name.ifEmpty { state.path },
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${state.items.count { it.isDir }} پوشه • ${state.items.count { !it.isDir }} فایل",
                                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                            )
                        }
                        IconButton({ newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, "پوشه جدید") }
                        IconButton(onSelectAll) { Icon(Icons.Outlined.SelectAll, "انتخاب همه") }
                    }
                }

                // ------------------------------------------------ storage roots
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val all = roots + if (useRoot) listOf("/") else emptyList()
                    itemsIndexed(all) { i, r ->
                        val active = state.path == r || (r != "/" && state.path.startsWith("$r/"))
                        val label = when {
                            r == "/" -> "ریشه (روت)"
                            r == FileIndex.internalRoot() -> "حافظه داخلی"
                            else -> "کارت حافظه ${if (i > 1) i else ""}".trim()
                        }
                        val icon = when {
                            r == "/" -> Icons.Outlined.Storage
                            r == FileIndex.internalRoot() -> Icons.Outlined.PhoneAndroid
                            else -> Icons.Outlined.SdCard
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (active) cs.secondaryContainer else cs.surfaceContainerHighest)
                                .clickable { onNavigate(r) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // ------------------------------------------------ breadcrumb (always LTR, like a path)
                val crumbs = remember(state.path) { breadcrumbs(state.path, roots) }
                val crumbState = rememberLazyListState()
                LaunchedEffect(crumbs) { if (crumbs.isNotEmpty()) crumbState.scrollToItem(crumbs.lastIndex) }
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
                ) {
                    LazyRow(
                        state = crumbState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        itemsIndexed(crumbs) { i, (label, path) ->
                            if (i > 0) Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                                Modifier.size(16.dp), tint = cs.outline,
                            )
                            Text(
                                label,
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onNavigate(path) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (i == crumbs.lastIndex) cs.primary else cs.onSurfaceVariant,
                                fontWeight = if (i == crumbs.lastIndex) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ list
        Box(Modifier.weight(1f)) {
            when {
                state.loading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> EmptyState("دسترسی ندارید", state.error)
                state.items.isEmpty() -> EmptyState("پوشه خالی است", state.path)
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                    itemsIndexed(state.items) { _, e ->
                        val sel = e.path in state.selection
                        FileRow(
                            e, "", showPath = false, selected = sel, highlighted = e.name == state.highlight,
                            onClick = {
                                when {
                                    selecting -> onToggleSelect(e.path)
                                    e.isDir -> onNavigate(e.path)
                                    else -> onOpen(e)
                                }
                            },
                            onLongClick = { if (selecting) onToggleSelect(e.path) else onLongClick(e) },
                        ) {
                            if (!selecting) {
                                IconButton({ onToggleSelect(e.path) }) {
                                    Box(
                                        Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                                            .background(cs.surfaceContainerHighest),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ paste bar
            SlideUp(clipboard != null, Modifier.align(Alignment.BottomCenter)) {
                val clip = clipboard ?: return@SlideUp
                Surface(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    shape = RoundedCornerShape(20.dp), color = cs.inverseSurface, shadowElevation = 8.dp,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${clip.paths.size} مورد برای ${if (clip.move) "انتقال" else "کپی"}",
                                color = cs.inverseOnSurface, style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "به: ${File(state.path).name.ifEmpty { "/" }}", color = cs.inverseOnSurface.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            )
                        }
                        OutlinedButton(onCancelClipboard) { Text("لغو", color = cs.inverseOnSurface) }
                        Spacer(Modifier.width(8.dp))
                        Button(onPaste) {
                            Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("چسباندن اینجا")
                        }
                    }
                }
            }
        }
    }

    if (newFolder) {
        TextInputDialog("پوشه جدید", "پوشه جدید", "ساختن", { newFolder = false }) { newFolder = false; onMkdir(it) }
    }
}

@Composable
private fun SlideUp(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        content()
    }
}

/** Path → clickable segments, starting at the storage root name when inside one. */
private fun breadcrumbs(path: String, roots: List<String>): List<Pair<String, String>> {
    val root = roots.filter { path == it || path.startsWith("$it/") }.maxByOrNull { it.length }
    val out = ArrayList<Pair<String, String>>()
    var base: String
    if (root != null) {
        out.add((if (root == FileIndex.internalRoot()) "حافظه داخلی" else File(root).name) to root)
        base = root
    } else {
        out.add("/" to "/")
        base = ""
    }
    val rest = path.removePrefix(base).trim('/')
    if (rest.isNotEmpty()) {
        for (seg in rest.split('/')) {
            base = "$base/$seg"
            out.add(seg to base)
        }
    }
    return out
}
