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
import com.gamecontroller.protocol.Protocol
import kotlinx.coroutines.Dispatchers
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

    val playState: StateFlow<PlayState?> = combine(
        combine(activeProfile, pageIndex, values) { p, i, v -> Triple(p, i, v) },
        combine(game, pending, clock) { g, pe, t -> Triple(g, pe, t) },
        combine(companion.state, hid.state) { c, h -> c to h },
    ) { (p, i, v), (g, pe, _), (c, h) ->
        p ?: return@combine null
        val now = System.currentTimeMillis()
        val r = Feedback.resolve(p, v, g, pe, now)
        PlayState(
            profile = p, pageIndex = i.coerceIn(0, p.pages.lastIndex), values = v,
            indicators = r.indicators, unavailable = r.unavailable, game = g, connection = c, hid = h,
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
                notify("Connected to ${companion.state.value.serverName ?: "PC"}")
            }

            override fun onState(values: Map<String, Any>, full: Boolean) = applyGameState(values, full)
            override fun onBeamNGCommands(list: List<String>) { _beamngCommands.value = list }
            override fun onBindingsResult(ok: Boolean, message: String) = notify(message.ifEmpty { if (ok) "Bindings applied" else "Bindings failed" })
            override fun onDisconnected() {
                game.value = emptyMap()
                pending.value = emptyMap()
            }
        }
        hid.onConnectionChanged = {
            // Routes depend on which links are up: tell the companion which ids now go over HID.
            syncProfile()
        }
        settings.lastHost?.let { host -> if (settings.token(host.substringBefore(":")) != null) companion.connect(host) }
        if (hid.hasPermission() && hid.isBluetoothOn()) hid.enable()

        viewModelScope.launch {
            while (isActive) {
                delay(250)
                if (pending.value.isNotEmpty()) {
                    val p = activeProfile.value
                    val now = System.currentTimeMillis()
                    if (p != null) {
                        pending.value = Feedback.prunePending(p, values.value, game.value, pending.value, now)
                        values.value = Feedback.syncValues(p, values.value, game.value, pending.value, now)
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
            is Screen.Editor -> doneEditing()
            is Screen.Play, Screen.Connect -> openProfiles()
            Screen.Profiles -> Unit
        }
    }

    /** Releases held inputs when the app goes to the background. */
    fun onPause() {
        engine.releaseAll()
        values.update { v -> v.filterKeys { id -> activeProfile.value?.let { p -> LayoutOps.findControl(p, id)?.second?.type != ControlType.MOMENTARY } ?: true } }
    }

    // ---- play ------------------------------------------------------------------------------

    private fun control(id: String): Control? = activeProfile.value?.let { LayoutOps.findControl(it, id)?.second }

    fun onControlValue(id: String, value: Float) {
        val c = control(id) ?: return
        if (id in (playState.value?.unavailable ?: emptySet())) return
        values.update { it + (id to value) }
        val tracksGame = (c.type == ControlType.LATCHING && c.feedbackKey != null) ||
            (c.type in setOf(ControlType.SWITCH, ControlType.RADIO) && c.positionKey != null)
        if (tracksGame && game.value.isNotEmpty()) pending.update { it + (id to System.currentTimeMillis()) }
        engine.onValue(c, value)
        if (!links.hidConnected && !links.companionConnected) notifyOnceNoLink()
    }

    fun onRotaryStep(id: String, delta: Int) {
        val c = control(id) ?: return
        if (id in (playState.value?.unavailable ?: emptySet())) return
        engine.onRotary(c, delta)
        if (!links.hidConnected && !links.companionConnected) notifyOnceNoLink()
    }

    private var noLinkNotified = 0L
    private fun notifyOnceNoLink() {
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
        val p = activeProfile.value ?: return
        val now = System.currentTimeMillis()
        pending.value = Feedback.prunePending(p, values.value, game.value, pending.value, now)
        values.value = Feedback.syncValues(p, values.value, game.value, pending.value, now)
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
        val p = repo.get(profileId) ?: _editor.value?.profile?.takeIf { it.id == profileId } ?: return
        if (!companion.isConnected) {
            notify("Connect to the PC companion first. The BeamNG mod writes the bindings.")
            return
        }
        val bindings = gameBindings(p)
        if (bindings.isEmpty()) {
            notify("No controls in this profile have a BeamNG action to bind.")
            return
        }
        companion.send(Protocol.beamngBindingsApply(if (hid.isConnected) "hid" else "vjoy1", bindings))
        notify("Sent ${bindings.size} bindings to BeamNG…")
    }

    // ---- connection ------------------------------------------------------------------------

    fun connect(host: String) = companion.connect(host)
    fun submitPin(pin: String) = companion.submitPin(pin)
    fun disconnect() = companion.disconnect()
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
        _editor.value = EditorState(profile = p, pageIndex = page.coerceIn(0, p.pages.lastIndex))
        _screen.value = Screen.Editor(id)
    }

    private fun editProfile(f: (EditorState) -> EditorState) {
        _editor.update { s -> s?.let { f(it).copy(dirty = true) } }
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
        /** (BeamNG action, gamepad button) pairs for every control that asks to be bound in the game. */
        fun gameBindings(p: Profile): List<Pair<String, Int>> = p.pages.flatMap { it.controls }.mapNotNull { c ->
            val name = c.bindTo ?: return@mapNotNull null
            val button = (c.action as? Action.VJoyButton)?.button ?: return@mapNotNull null
            name to button
        }.distinct()
    }
}
