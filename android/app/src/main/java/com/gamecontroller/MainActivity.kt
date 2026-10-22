package com.gamecontroller

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamecontroller.app.AppViewModel
import com.gamecontroller.app.Screen
import com.gamecontroller.ui.screens.ConnectScreen
import com.gamecontroller.ui.screens.EditorScreen
import com.gamecontroller.ui.screens.PlayScreen
import com.gamecontroller.ui.screens.ProfilesScreen
import com.gamecontroller.ui.theme.GcTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GcTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Full-screen play surface: hide system bars, let a swipe bring them back briefly.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        vm.hid.refreshHosts()
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val hid by vm.hidState.collectAsStateWithLifecycle()
    val play by vm.playState.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        vm.consumeNotice()
        snackbar.showSnackbar(n.text)
    }

    // Bluetooth permission, then make the phone discoverable so a PC can pair with "GameCtl Pad".
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.enableHid() }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) {
            vm.enableHid()
            discoverable.launch(vm.hid.discoverableIntent())
        } else {
            vm.notify("Bluetooth permission is needed to act as a controller.")
        }
    }
    val enableHid = {
        if (vm.hid.hasPermission()) {
            vm.enableHid()
            discoverable.launch(vm.hid.discoverableIntent())
        } else {
            btPermission.launch(vm.hid.requiredPermissions())
        }
    }

    // Profile import / export through the system file picker.
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingExport
        if (uri != null && text != null) scope.launch {
            runCatching { vm.readText { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }; "" } }
                .onSuccess { vm.notify("Profile exported") }
                .onFailure { vm.notify("Export failed: ${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { vm.readText { context.contentResolver.openInputStream(uri)!!.bufferedReader().readText() } }
                .onSuccess { vm.importJson(it) }
                .onFailure { vm.notify("Couldn't read that file.") }
        }
    }

    // The editor composes its own BackHandler (discard confirm), so the outer one stays out of its way.
    BackHandler(enabled = screen != Screen.Profiles && screen !is Screen.Editor) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        when (val s = screen) {
            Screen.Profiles -> ProfilesScreen(
                profiles = profiles,
                connection = connection,
                hid = hid,
                onOpen = vm::play,
                onCreate = vm::createProfile,
                onDuplicate = vm::duplicate,
                onDelete = vm::delete,
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onExport = { id ->
                    vm.exportJson(id)?.let { (name, text) ->
                        pendingExport = text
                        exportLauncher.launch(name)
                    }
                },
                onOpenConnection = vm::openConnect,
            )
            Screen.Connect -> ConnectScreen(
                connection = connection,
                servers = servers,
                hid = hid,
                onConnect = vm::connect,
                onSubmitPin = vm::submitPin,
                onDisconnect = vm::disconnect,
                onRescan = vm::rescan,
                onHidEnable = enableHid,
                onHidConnect = vm::connectHid,
                onHidDisconnect = vm::disconnectHid,
                onBack = vm::back,
            )
            is Screen.Play -> play?.let { state ->
                PlayScreen(
                    state = state,
                    onControlValue = vm::onControlValue,
                    onRotaryStep = vm::onRotaryStep,
                    onPageChange = vm::onPageChange,
                    onEdit = { vm.edit(s.profileId) },
                    onBack = vm::back,
                    imperialUnits = vm.settings.imperialUnits,
                    onOpenConnection = vm::openConnect,
                )
            }
            is Screen.Editor -> editor?.let { state ->
                EditorScreen(
                    state = state,
                    beamngCommands = vm.beamngCommands.collectAsStateWithLifecycle().value,
                    onSelect = vm::editorSelect,
                    onAddControl = vm::editorAdd,
                    onMove = vm::editorMove,
                    onResize = vm::editorResize,
                    onDelete = vm::editorDelete,
                    onUpdate = vm::editorUpdate,
                    onAddPage = vm::editorAddPage,
                    onRenamePage = vm::editorRenamePage,
                    onDeletePage = vm::editorDeletePage,
                    onPageChange = vm::editorPage,
                    onGridChange = vm::editorGrid,
                    onProfileChange = vm::editorProfile,
                    onApplyGameBindings = { vm.applyGameBindings(state.profile.id) },
                    onPreviewGameBindings = { vm.previewGameBindings(state.profile.id) },
                    onDiscard = vm::discardEditing,
                    onDone = vm::doneEditing,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
