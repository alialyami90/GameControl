package com.gamecontroller.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gamecontroller.BuildConfig
import com.gamecontroller.data.ProfileRepository
import com.gamecontroller.engine.Feedback
import com.gamecontroller.engine.InputEngine
import com.gamecontroller.engine.OutputLinks
import com.gamecontroller.hid.BluetoothHidLink
import com.gamecontroller.model.Action
import com.gamecontroller.model.BindResult
import com.gamecontroller.model.ConnectionStatus
import com.gamecontroller.model.Control
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.EditorState
import com.gamecontroller.model.GameId
import com.gamecontroller.model.PlayState
import com.gamecontroller.model.Profile
import com.gamecontroller.net.CompanionClient
import com.gamecontroller.net.Settings
import com.gamecontroller.profiles.LayoutOps
import com.gamecontroller.profiles.ProfileJson
import com.gamecontroller.protocol.Incoming
import com.gamecontroller.protocol.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which screen is showing. */
sealed interface Screen {
    data object Profiles : Screen
    data object Connect : Screen
    data class Play(val profileId: String) : Screen
    data class Editor(val profileId: String) : Screen
}

/** One-off messages for a snackbar. */
data class Notice(val text: String, val id: Long = System.nanoTime())

/**
 * The app's single ViewModel: owns the links (Bluetooth HID + Wi-Fi companion), the input engine,
 * profiles, and the play/editor state. Everything runs on the main thread.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    val repo = ProfileRepository(app)
    val hid = BluetoothHidLink(app)
    val companion = CompanionClient(app, settings, BuildConfig.VERSION_NAME)

    private val links = object : OutputLinks {
        override val hidConnected get() = hid.isConnected
        override val companionConnected get() = companion.isConnected
        override fun sendHid(reportId: Int, payload: ByteArray) = hid.send(reportId, payload)
        override fun sendCompanion(id: String, value: Float, seq: Long, reliable: Boolean) =
            companion.sendInput(id, value, seq, reliable)
    }
    private val engine = InputEngine(viewModelScope, links)

    private val _screen = MutableStateFlow<Screen>(Screen.Profiles)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    val profiles = repo.profiles
    val connection = companion.state
    val servers = companion.servers
    val hidState = hid.state

    // ---- play state ------------------------------------------------------------------------

    private val activeProfile = MutableStateFlow<Profile?>(null)
    private val pageIndex = MutableStateFlow(0)
    private val values = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val game = MutableStateFlow<Map<String, Any>>(emptyMap())
    private val pending = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val clock = MutableStateFlow(0L) // ticks so pending indicators time out on screen
    private val linkDrops = MutableStateFlow(0)
    private val knownStateKeys = MutableStateFlow<Set<String>>(emptySet())
    private var lastBindRequest: Pair<String, Int>? = null // device, requested count
    private var bindTimeout: Job? = null
    /** The user tapped Disconnect on the companion: a Bluetooth connect must not bring it back. */
    private var userDisconnected = false

    val playState: StateFlow<PlayState?> = combine(
        combine(activeProfile, pageIndex, values) { p, i, v -> Triple(p, i, v) },
        combine(game, pending, clock) { g, pe, t -> Triple(g, pe, t) },
        combine(companion.state, hid.state, linkDrops) { c, h, d -> Triple(c, h, d) },
    ) { (p, i, v), (g, pe, _), (c, h, d) ->
        p ?: return@combine null
        val now = System.currentTimeMillis()
        val r = Feedback.resolve(p, v, g, pe, now)
        PlayState(
            profile = p, pageIndex = i.coerceIn(0, p.pages.lastIndex), values = v,
            indicators = r.indicators, unavailable = r.unavailable, game = g, connection = c, hid = h,
            linkDrops = d,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- editor state ----------------------------------------------------------------------

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val _beamngCommands = MutableStateFlow<List<String>>(emptyList())
    val beamngCommands: StateFlow<List<String>> = _beamngCommands.asStateFlow()

    init {
        companion.listener = object : CompanionClient.Listener {
            override fun onWelcome() {
                engine.resetSequence()
                game.value = emptyMap()
                syncProfile()
                replayHeld()
                notify("Connected to ${companion.state.value.serverName ?: "PC"}")
            }

            override fun onState(values: Map<String, Any>, full: Boolean) = applyGameState(values, full)
            override fun onBeamNGCommands(list: List<String>) {
                _beamngCommands.value = list
                _editor.update { it?.copy(beamngCommands = list) }
            }
            override fun onBindingsResult(r: Incoming.BindingsResult) {
                bindTimeout?.cancel()
                val (device, requested) = lastBindRequest ?: (r.device to r.count)
                val result = BindResult(
                    ok = r.ok, device = device,
                    message = if (r.busy && !r.ok) r.message.ifEmpty { "BeamNG is still writing the last request. Try again in a second." } else r.message, requested = requested, count = r.count,
                    unknown = r.unknown, written = r.written, alreadyBound = r.alreadyBound,
                    // The mod's busy is a rejection, not progress: it keeps a message so the UI shows a red row (U28).
                    busy = r.busy,
                    atMillis = System.currentTimeMillis(),
                )
                _editor.update { it?.copy(bindResult = result) }
                // In the editor the result row says it all; a snackbar would only repeat it (A-S2).
                if (_editor.value == null) notify(r.message.ifEmpty { if (r.ok) "Bound ${r.count} of $requested in BeamNG" else "Binding failed" })
            }
            override fun onDisconnected() {
                engine.onCompanionLost()
                game.value = emptyMap()
                pending.value = emptyMap()
            }
        }
        hid.currentReport = { id -> engine.hidReport(id) }
        hid.onConnectionChanged = { connected ->
            engine.onLinkChanged()
            if (connected) replayHeld()
            // Routes depend on which links are up: tell the companion which ids now go over HID.
            syncProfile()
            // Latches the companion just let go of continue over HID (A-S1).
            if (connected) engine.rehomeLatches()
            // Bluetooth up usually means the PC is in use: bring the Wi-Fi link up too, so the companion
            // shows the phone and game lamps keep working alongside the HID route.
            if (connected && !userDisconnected && companion.state.value.status in setOf(ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR)) {
                settings.lastHost?.let { host -> if (settings.token(host.substringBefore(":")) != null) companion.connect(host) }
            }
        }
        settings.lastHost?.let { host -> if (settings.token(host.substringBefore(":")) != null) companion.connect(host) }
        if (hid.hasPermission() && hid.isBluetoothOn()) hid.enable()

        viewModelScope.launch {
            companion.state.collect { c ->
                _editor.update { it?.copy(canBindInGame = c.status == com.gamecontroller.model.ConnectionStatus.CONNECTED && c.capabilities.beamng) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                if (pending.value.isNotEmpty()) {
                    val p = activeProfile.value
                    val now = System.currentTimeMillis()
                    if (p != null) {
                        pending.value = Feedback.prunePending(p, values.value, game.value, pending.value, now)
                        syncValuesFromGame(p, now)
                    }
                    clock.value = now
                }
            }
        }
    }

    fun notify(text: String) {
        _notice.value = Notice(text)
    }

    fun consumeNotice() {
        _notice.value = null
    }

    // ---- navigation ------------------------------------------------------------------------

    fun openProfiles() {
        stopPlaying()
        _screen.value = Screen.Profiles
    }

    fun openConnect() {
        _screen.value = Screen.Connect
        if (!companion.isConnected) companion.startDiscovery()
        hid.refreshHosts()
    }

    fun play(id: String) {
        val p = repo.get(id) ?: return
        settings.lastProfileId = id
        stopPlaying()
        activeProfile.value = p
        pageIndex.value = 0
        values.value = emptyMap()
        pending.value = emptyMap()
        engine.profile = p
        syncProfile()
        _screen.value = Screen.Play(id)
    }

    private fun stopPlaying() {
        engine.releaseAll()
        engine.profile = null
        activeProfile.value = null
    }

    fun back() {
        when (_screen.value) {
            // The editor owns Back (EditorBackHandler asks to discard when dirty), so nothing to do here.
            is Screen.Editor -> Unit
            is Screen.Play, Screen.Connect -> openProfiles()
            Screen.Profiles -> Unit
        }
    }

    /** Releases held inputs when the app goes to the background. */
    fun onPause() {
        engine.releaseAll()
        // Momentary buttons and sliders are physically let go when the app leaves the screen.
        values.update { v ->
            v.filterKeys { id ->
                val t = activeProfile.value?.let { p -> LayoutOps.findControl(p, id)?.second?.type }
                t != ControlType.MOMENTARY && t != ControlType.SLIDER
            }
        }
    }

    // ---- play ------------------------------------------------------------------------------

    private fun control(id: String): Control? = activeProfile.value?.let { LayoutOps.findControl(it, id)?.second }

    /**
     * After a link (re)connects the PC holds nothing, so re-drive what the user still holds on screen:
     * momentary buttons, sliders, and latched controls whose action is a hold (not a tap, which
     * would toggle the game a second time) (A16).
     */
    private fun replayHeld() {
        val p = activeProfile.value ?: return
        for ((id, v) in values.value) {
            val c = LayoutOps.findControl(p, id)?.second ?: continue
            val rest = InputEngine.restValue(c)
            if (v == rest) continue
            if (engine.isHeld(id)) continue // still driven on a live link: replaying would double-press it (A23)
            val replay = when (c.type) {
                ControlType.MOMENTARY, ControlType.SLIDER -> true
                ControlType.LATCHING -> when (val a = c.action) {
                    is Action.Key -> a.trigger == com.gamecontroller.model.KeyTrigger.HOLD
                    is Action.VJoyButton, is Action.XInputButton, is Action.VJoyAxis, is Action.XInputAxis -> true
                    is Action.BeamNG -> !a.command.endsWith(".toggle") // setters are idempotent
                    else -> false
                }
                else -> false
            }
            if (replay) {
                engine.syncValue(id, rest)
                engine.onValue(c, v)
            }
        }
    }

    fun onControlValue(id: String, value: Float) {
        val c = control(id) ?: return
        // An unavailable control ignores presses but a release must always get through (A2).
        if (id in (playState.value?.unavailable ?: emptySet()) && !InputEngine.isRelease(c, value)) return
        values.update { it + (id to value) }
        val tracksGame = (c.type == ControlType.LATCHING && c.feedbackKey != null) ||
            (c.type in setOf(ControlType.SWITCH, ControlType.RADIO) && c.positionKey != null)
        if (tracksGame && game.value.isNotEmpty()) pending.update { it + (id to System.currentTimeMillis()) }
        engine.onValue(c, value)
        // Only a press or a slider move counts as a dropped input; a release has nothing to drop (A31).
        if (!links.hidConnected && !links.companionConnected && !InputEngine.isRelease(c, value)) notifyOnceNoLink()
    }

    fun onRotaryStep(id: String, delta: Int) {
        val c = control(id) ?: return
        if (id in (playState.value?.unavailable ?: emptySet())) return
        engine.onRotary(c, delta)
        if (!links.hidConnected && !links.companionConnected) notifyOnceNoLink()
    }

    private var noLinkNotified = 0L
    private fun notifyOnceNoLink() {
        linkDrops.value++
        val now = System.currentTimeMillis()
        if (now - noLinkNotified > 10_000) {
            noLinkNotified = now
            notify("Not connected. Pair over Bluetooth or connect to the PC companion.")
        }
    }

    fun onPageChange(i: Int) {
        pageIndex.value = i
    }

    private fun applyGameState(update: Map<String, Any>, full: Boolean) {
        game.value = if (full) update else game.value + update
        if (update.keys.any { it !in knownStateKeys.value }) {
            knownStateKeys.value = knownStateKeys.value + update.keys
            _editor.update { it?.copy(stateKeys = knownStateKeys.value.sorted()) }
        }
        val p = activeProfile.value ?: return
        val now = System.currentTimeMillis()
        pending.value = Feedback.prunePending(p, values.value, game.value, pending.value, now)
        syncValuesFromGame(p, now)
    }

    /** Pulls local latch/switch values in line with the game and tells the engine (A1). */
    private fun syncValuesFromGame(p: Profile, now: Long) {
        val before = values.value
        val after = Feedback.syncValues(p, before, game.value, pending.value, now)
        if (after != before) {
            values.value = after
            for ((id, v) in after) if (before[id] != v) engine.syncValue(id, v)
        }
    }

    /** Sends profile.sync for the active profile (or nothing when not playing). */
    private fun syncProfile() {
        val p = activeProfile.value ?: return
        if (companion.isConnected) companion.send(Protocol.profileSync(p, engine.hidRoutedIds()))
    }

    /**
     * Asks the BeamNG mod to bind, inside the game, every gamepad button of [profileId] whose
     * control has a `bindTo` BeamNG action. Uses the Bluetooth pad when it is connected, else vJoy.
     */
    fun applyGameBindings(profileId: String) {
        // The editor's unsaved profile wins over the stored one, so what is previewed is what is sent (U28).
        val p = bindProfile(profileId) ?: return
        if (!companion.isConnected) {
            notify("Connect to the PC companion first. The BeamNG mod writes the bindings.")
            return
        }
        val bindings = gameBindings(p)
        if (bindings.isEmpty()) {
            notify("No controls in this profile have a BeamNG action to bind.")
            return
        }
        val device = if (hid.isConnected) "hid" else "vjoy1"
        lastBindRequest = device to bindings.size
        val sentAt = System.currentTimeMillis()
        // busy with an empty message = request in flight; the mod's reply or the timeout below replaces it.
        _editor.update { it?.copy(bindResult = BindResult(ok = false, message = "", device = device, requested = bindings.size, count = 0, busy = true, atMillis = sentAt)) }
        bindTimeout?.cancel()
        bindTimeout = viewModelScope.launch {
            delay(BIND_TIMEOUT_MS)
            _editor.update { e ->
                val r = e?.bindResult
                if (r != null && r.busy && r.atMillis == sentAt) e.copy(bindResult = r.copy(busy = false, message = "No answer from BeamNG. Is the game running with the mod enabled?"))
                else e
            }
        }
        companion.send(Protocol.beamngBindingsApply(device, bindings))
    }

    /** What "Bind in game" would send for [profileId]: (BeamNG action, button) pairs and the device. */
    fun previewGameBindings(profileId: String): Pair<String, List<Pair<String, Int>>> {
        val p = bindProfile(profileId)
        return (if (hid.isConnected) "hid" else "vjoy1") to (p?.let { gameBindings(it) } ?: emptyList())
    }

    private fun bindProfile(profileId: String): Profile? =
        _editor.value?.profile?.takeIf { it.id == profileId } ?: repo.get(profileId)

    // ---- connection ------------------------------------------------------------------------

    fun connect(host: String) { userDisconnected = false; companion.connect(host) }
    fun submitPin(pin: String) = companion.submitPin(pin)
    fun disconnect() { userDisconnected = true; companion.disconnect() }
    fun rescan() = companion.startDiscovery()
    fun enableHid() = hid.enable()
    fun connectHid(address: String) = hid.connect(address)
    fun disconnectHid() = hid.disconnect()

    // ---- profiles --------------------------------------------------------------------------

    fun createProfile(name: String, game: GameId) {
        val p = LayoutOps.newProfile(name, game)
        repo.save(p)
        edit(p.id)
    }

    fun duplicate(id: String) {
        val p = repo.get(id) ?: return
        val copy = LayoutOps.duplicate(p)
        repo.save(copy)
        notify("Duplicated as \"${copy.name}\"")
    }

    fun delete(id: String) {
        val wasBuiltIn = repo.get(id)?.builtIn == true
        repo.delete(id)
        notify(if (wasBuiltIn) "Restored the original layout" else "Profile deleted")
    }

    fun exportJson(id: String): Pair<String, String>? {
        val p = repo.get(id) ?: return null
        return ProfileJson.fileName(p) to ProfileJson.export(p)
    }

    fun importJson(text: String) {
        try {
            val p = repo.importJson(text)
            notify("Imported \"${p.name}\"")
        } catch (e: Exception) {
            notify("Couldn't import: ${e.message ?: "not a profile file"}")
        }
    }

    suspend fun readText(block: () -> String): String = withContext(Dispatchers.IO) { block() }

    // ---- editor ----------------------------------------------------------------------------

    fun edit(id: String) {
        val p = repo.get(id) ?: return
        val page = if ((_screen.value as? Screen.Play)?.profileId == id) pageIndex.value else 0
        stopPlaying()
        _editor.value = EditorState(
            profile = p, pageIndex = page.coerceIn(0, p.pages.lastIndex),
            stateKeys = knownStateKeys.value.sorted(), beamngCommands = _beamngCommands.value,
            canBindInGame = companion.isConnected && companion.state.value.capabilities.beamng,
        )
        _screen.value = Screen.Editor(id)
    }

    private fun editProfile(f: (EditorState) -> EditorState) {
        // Dirty means "differs from what is saved", so an undo back to the saved layout clears it.
        _editor.update { s -> s?.let { val n = f(it); n.copy(dirty = n.profile != repo.get(n.profile.id)) } }
    }

    fun editorSelect(id: String?) = _editor.update { it?.copy(selectedControlId = id) }

    fun editorAdd(type: ControlType, x: Int, y: Int) = editProfile { s ->
        val (p, id) = LayoutOps.addControl(s.profile, s.pageIndex, type, x, y)
        s.copy(profile = p, selectedControlId = id)
    }

    fun editorMove(id: String, x: Int, y: Int) = editProfile { s -> s.copy(profile = LayoutOps.move(s.profile, s.pageIndex, id, x, y)) }
    fun editorResize(id: String, w: Int, h: Int) = editProfile { s -> s.copy(profile = LayoutOps.resize(s.profile, s.pageIndex, id, w, h)) }
    fun editorDelete(id: String) = editProfile { s ->
        s.copy(profile = LayoutOps.delete(s.profile, s.pageIndex, id), selectedControlId = s.selectedControlId.takeIf { it != id })
    }
    fun editorUpdate(c: Control) = editProfile { s -> s.copy(profile = LayoutOps.updateControl(s.profile, s.pageIndex, c)) }
    fun editorAddPage() = editProfile { s -> val p = LayoutOps.addPage(s.profile); s.copy(profile = p, pageIndex = p.pages.lastIndex, selectedControlId = null) }
    fun editorRenamePage(i: Int, name: String) = editProfile { s -> s.copy(profile = LayoutOps.renamePage(s.profile, i, name)) }
    fun editorDeletePage(i: Int) = editProfile { s ->
        val p = LayoutOps.deletePage(s.profile, i)
        s.copy(profile = p, pageIndex = s.pageIndex.coerceAtMost(p.pages.lastIndex), selectedControlId = null)
    }
    fun editorPage(i: Int) = _editor.update { it?.copy(pageIndex = i, selectedControlId = null) }
    fun editorGrid(columns: Int, rows: Int) = editProfile { s -> s.copy(profile = LayoutOps.resizeGrid(s.profile, s.pageIndex, columns, rows)) }
    fun editorProfile(f: (Profile) -> Profile) = editProfile { s -> s.copy(profile = f(s.profile)) }

    fun doneEditing() {
        val s = _editor.value ?: return openProfiles()
        if (s.dirty) repo.save(s.profile)
        _editor.value = null
        play(s.profile.id)
    }

    fun discardEditing() {
        _editor.value = null
        openProfiles()
    }

    override fun onCleared() {
        engine.releaseAll()
        companion.shutdown()
        hid.shutdown()
    }

    companion object {
        const val BIND_TIMEOUT_MS = 10_000L
        /** (BeamNG action, gamepad button) pairs for every control that asks to be bound in the game. */
        fun gameBindings(p: Profile): List<Pair<String, Int>> = p.pages.flatMap { it.controls }.mapNotNull { c ->
            val name = c.bindTo ?: return@mapNotNull null
            val button = (c.action as? Action.VJoyButton)?.button ?: return@mapNotNull null
            name to button
        }.distinct()
    }
}
