package com.vita.everysearch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vita.everysearch.data.FileEntry
import com.vita.everysearch.data.FileIndex
import com.vita.everysearch.data.FileOps
import com.vita.everysearch.ui.ActionDialogs
import com.vita.everysearch.ui.BrowserScreen
import com.vita.everysearch.ui.FileAction
import com.vita.everysearch.ui.FileActionsSheet
import com.vita.everysearch.ui.ProgressDialog
import com.vita.everysearch.ui.SearchScreen
import com.vita.everysearch.ui.copyText
import com.vita.everysearch.ui.openFile
import com.vita.everysearch.ui.rememberActionHost
import com.vita.everysearch.ui.shareFiles
import com.vita.everysearch.ui.theme.EverySearchTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EverySearchTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        PermissionGate { App(vm) }
                    }
                }
            }
        }
    }
}

fun hasStorageAccess(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasStorageAccess(ctx)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasStorageAccess(ctx) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasStorageAccess(ctx)
    }
    if (granted) {
        content()
        return
    }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.FolderSpecial, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp))
        Text("دسترسی به همه فایل‌ها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "همه‌یاب برای پیدا کردن، کپی و جابجایی همه فایل‌های گوشی به اجازه «دسترسی به همه فایل‌ها» نیاز دارد. " +
                "هیچ اطلاعاتی از گوشی خارج نمی‌شود.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                    runCatching { ctx.startActivity(i) }
                        .onFailure { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                } else {
                    legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("اجازه دادن") }
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val params by vm.params.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val useRoot by vm.useRoot.collectAsStateWithLifecycle()
    val browser by vm.browser.collectAsStateWithLifecycle()
    val clipboard by vm.clipboard.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val host = rememberActionHost()
    val roots = remember(scan.lastScan) { FileIndex.storageRoots(ctx) }

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) } }

    fun open(e: FileEntry) {
        if (e.isDir) { vm.openFolder(e.path); return }
        scope.launch {
            val f = FileOps.readableCopy(ctx, e.path)
            if (f == null) vm.message("خواندن فایل ممکن نیست (روت لازم است)") else openFile(ctx, f)
        }
    }

    fun share(paths: List<String>) {
        scope.launch {
            val files = paths.map { File(it) }.filter { it.isFile }.mapNotNull { FileOps.readableCopy(ctx, it.path) }
            if (files.isEmpty()) vm.message("فقط فایل‌ها قابل اشتراک هستند") else shareFiles(ctx, files)
        }
    }

    fun action(e: FileEntry, a: FileAction) {
        host.sheetFor = null
        when (a) {
            FileAction.OPEN -> open(e)
            FileAction.OPEN_FOLDER -> vm.openFolder(e.parent, highlight = e.name)
            FileAction.COPY, FileAction.MOVE -> {
                vm.setClipboard(listOf(e.path), move = a == FileAction.MOVE)
                // Start the destination picker in the file's own folder.
                if (browser == null) vm.openFolder(e.parent, highlight = e.name)
            }
            FileAction.SHARE -> share(listOf(e.path))
            FileAction.RENAME -> host.renameFor = e
            FileAction.DELETE -> host.deleteFor = listOf(e.path)
            FileAction.COPY_PATH -> copyText(ctx, e.path)
        }
    }

    BackHandler(enabled = browser != null) { vm.browserBack() }

    Box(Modifier.fillMaxSize().imePadding()) {
        AnimatedContent(browser != null, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { inBrowser ->
            val b = browser
            if (inBrowser && b != null) {
                BrowserScreen(
                    state = b,
                    clipboard = clipboard,
                    roots = roots,
                    useRoot = useRoot,
                    onBack = { vm.browserBack() },
                    onClose = { vm.closeBrowser() },
                    onNavigate = { vm.openFolder(it) },
                    onOpen = ::open,
                    onLongClick = { host.sheetFor = it },
                    onToggleSelect = vm::toggleSelect,
                    onSelectAll = vm::selectAll,
                    onClipboard = vm::setClipboard,
                    onShare = ::share,
                    onDelete = { host.deleteFor = it },
                    onPaste = vm::paste,
                    onCancelClipboard = vm::clearClipboard,
                    onMkdir = vm::mkdir,
                )
            } else {
                SearchScreen(
                    params = params,
                    results = results,
                    counts = counts,
                    scan = scan,
                    useRoot = useRoot,
                    onQuery = vm::setQuery,
                    onCategory = vm::setCategory,
                    onSort = vm::setSort,
                    onShowHidden = vm::setShowHidden,
                    onUseRoot = vm::setUseRoot,
                    onRescan = vm::rescan,
                    onBrowse = { vm.openFolder(FileIndex.internalRoot()) },
                    onClick = ::open,
                    onLongClick = { host.sheetFor = it },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))
    }

    host.sheetFor?.let { e ->
        FileActionsSheet(e, showOpenFolder = browser == null, onDismiss = { host.sheetFor = null }) { action(e, it) }
    }
    ActionDialogs(host, onRename = { e, n -> vm.rename(e.path, n) }, onDelete = { vm.delete(it) })
    progress?.let { ProgressDialog(it, vm::cancelOp) }
}
