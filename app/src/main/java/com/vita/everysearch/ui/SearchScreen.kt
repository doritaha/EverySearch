package com.vita.everysearch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vita.everysearch.data.Category
import com.vita.everysearch.data.FileEntry
import com.vita.everysearch.data.ScanState
import com.vita.everysearch.SearchParams
import com.vita.everysearch.data.SortMode

@Composable
fun SearchScreen(
    params: SearchParams,
    results: List<FileEntry>,
    counts: Map<Category, Int>,
    scan: ScanState,
    useRoot: Boolean,
    onQuery: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onSort: (SortMode) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onUseRoot: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onBrowse: () -> Unit,
    onClick: (FileEntry) -> Unit,
    onLongClick: (FileEntry) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    // Photos and videos default to a gallery grid; the toggle switches back to the list.
    var gridMode by rememberSaveable { mutableStateOf(true) }
    val mediaCategory = params.category == Category.IMAGE || params.category == Category.VIDEO

    LaunchedEffect(params) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        // ------------------------------------------------ header
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(cs.primary.copy(alpha = 0.22f), cs.secondary.copy(alpha = 0.06f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(top = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("همه‌یاب", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (scan.running) "در حال نمایه‌سازی… ${"%,d".format(scan.found)} مورد"
                        else "${"%,d".format(counts[Category.ALL] ?: 0)} فایل و پوشه" + if (useRoot) " • روت" else "",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
                IconButton(onBrowse) { Icon(Icons.Outlined.FolderOpen, "مرورگر فایل") }
                IconButton(onRescan, enabled = !scan.running) { Icon(Icons.Outlined.Refresh, "نمایه‌سازی دوباره") }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "بیشتر") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("جستجو در فایل‌های سیستمی (روت)") },
                            leadingIcon = { Icon(Icons.Outlined.Security, null) },
                            trailingIcon = { Checkbox(useRoot, null) },
                            onClick = { menu = false; onUseRoot(!useRoot) },
                        )
                        DropdownMenuItem(
                            text = { Text("نمایش فایل‌های مخفی") },
                            leadingIcon = { Icon(Icons.Outlined.Visibility, null) },
                            trailingIcon = { Checkbox(params.showHidden, null) },
                            onClick = { menu = false; onShowHidden(!params.showHidden) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("راهنمای جستجو") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) },
                            onClick = { menu = false; help = true },
                        )
                    }
                }
            }

            // ------------------------------------------------ search field
            TextField(
                value = params.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                placeholder = { Text("جستجو در همه فایل‌ها…   مثال: *.pdf  یا  سفر 2024") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (params.query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Outlined.Close, "پاک کردن") }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            // ------------------------------------------------ categories
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Category.entries.toList()) { c ->
                    val sel = params.category == c
                    FilterChip(
                        selected = sel,
                        onClick = { onCategory(c) },
                        label = {
                            Text(c.label + (counts[c]?.let { "  " + compact(it) } ?: ""))
                        },
                        leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp), tint = if (sel) cs.onSecondaryContainer else c.color()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cs.secondaryContainer),
                    )
                }
            }

            // ------------------------------------------------ result count + sort
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${"%,d".format(results.size)} نتیجه",
                    style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                SortMode.entries.forEach { s ->
                    val active = params.sort == s
                    TextButton(onClick = { onSort(s) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(s.label, color = if (active) cs.primary else cs.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        if (active) Icon(
                            if (params.descending) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, null,
                            Modifier.size(14.dp), tint = cs.primary,
                        )
                    }
                }
                if (mediaCategory) {
                    IconButton({ gridMode = !gridMode }, Modifier.size(36.dp)) {
                        Icon(
                            if (gridMode) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                            if (gridMode) "نمای فهرست" else "نمای گالری", tint = cs.primary,
                        )
                    }
                }
            }
            AnimatedVisibility(scan.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
        }

        // ------------------------------------------------ results
        if (results.isEmpty()) {
            EmptyState(
                if (scan.running && (counts[Category.ALL] ?: 0) == 0) "در حال ساخت فهرست فایل‌ها…" else "چیزی پیدا نشد",
                if (scan.running) "اولین بار چند ثانیه طول می‌کشد" else "عبارت یا دسته‌بندی دیگری را امتحان کنید",
            )
        } else if (mediaCategory && gridMode) {
            MediaGrid(results, gridState, onClick, onLongClick)
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results) { e ->
                    FileRow(e, params.query, showPath = true, onClick = { onClick(e) }, onLongClick = { onLongClick(e) }) {
                        IconButton({ onLongClick(e) }) { Icon(Icons.Outlined.MoreVert, "گزینه‌ها", tint = cs.outline) }
                    }
                }
            }
        }
    }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            confirmButton = { TextButton({ help = false }) { Text("باشه") } },
            title = { Text("راهنمای جستجو") },
            text = {
                Text(
                    """
                    • چند کلمه با فاصله: همه باید در نام باشند
                       سفر شمال  ← فایل‌هایی که هر دو کلمه را دارند
                    • ستاره و علامت سؤال (مثل Everything):
                       *.pdf    IMG_2024*    ???.mp3
                    • کلمه دارای / در کل مسیر جستجو می‌شود:
                       dcim/ .jpg    whatsapp/ .opus
                    • ! برای حذف: video !whatsapp
                    • روی نتیجه بزنید تا باز شود؛ نگه دارید یا ⋮ برای
                       رفتن به پوشه، کپی، انتقال، اشتراک و حذف.
                    """.trimIndent(),
                )
            },
        )
    }
}

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1000.0)
    else -> n.toString()
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.SearchOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(1.dp))
    }
}
