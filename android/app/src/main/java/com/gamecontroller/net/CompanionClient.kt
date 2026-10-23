package com.gamecontroller.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gamecontroller.model.Capabilities
import com.gamecontroller.model.ConnectionStatus
import com.gamecontroller.model.ConnectionUi
import com.gamecontroller.model.DiscoveredServer
import com.gamecontroller.protocol.Incoming
import com.gamecontroller.protocol.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Wi-Fi link to the Windows companion (PROTOCOL.md): WebSocket for pairing, profile sync,
 * discrete inputs and game state; UDP for momentary and slider events.
 * All public methods and callbacks run on the main thread.
 */
class CompanionClient(
    private val context: Context,
    private val settings: Settings,
    private val appVersion: String,
) {
    interface Listener {
        fun onWelcome()
        fun onState(values: Map<String, Any>, full: Boolean)
        fun onBeamNGCommands(list: List<String>) {}
        fun onBindingsResult(result: Incoming.BindingsResult) {}
        fun onDisconnected() {}
    }

    var listener: Listener? = null

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(ConnectionUi())
    val state: StateFlow<ConnectionUi> = _state.asStateFlow()

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()

    private var ws: WebSocket? = null
    private var host: String? = null
    private var wsPort = Protocol.WS_PORT
    private var udpPort = Protocol.UDP_PORT
    private var session: String? = null
    @Volatile private var udp: DatagramSocket? = null
    @Volatile private var udpTarget: InetAddress? = null
    private var lastPong = 0L
    private var userDisconnected = false
    private var generation = 0
    private var reconnectTask: Runnable? = null

    val isConnected: Boolean get() = _state.value.status == ConnectionStatus.CONNECTED

    // ---- connection ----------------------------------------------------------------------

    /** Connects to [hostSpec] ("192.168.1.20" or "192.168.1.20:47800"). Uses a saved token when there is one. */
    fun connect(hostSpec: String) {
        val spec = hostSpec.trim().removePrefix("ws://").removeSuffix("/")
        if (spec.isEmpty()) return
        // "host", "host:port", "[v6]" or "[v6]:port" (A12).
        val (h, port) = parseHostPort(spec)
        closeSocket()
        userDisconnected = false
        host = h
        wsPort = port
        settings.lastHost = spec
        open(pin = null)
    }

    /** Sends the 6-digit PIN shown by the companion. */
    fun submitPin(pin: String) {
        val h = host ?: return
        settings.clearToken(h)
        closeSocket()
        open(pin = pin.filter { it.isDigit() })
    }

    fun disconnect() {
        val wasConnected = isConnected
        userDisconnected = true
        closeSocket()
        // The state changes first, so the engine sees the link as down and forgets what it held there (A26, M1).
        _state.update { ConnectionUi(status = ConnectionStatus.DISCONNECTED) }
        if (wasConnected) listener?.onDisconnected()
    }

    private fun open(pin: String?) {
        val h = host ?: return
        // A PC whose IP changed (DHCP) is still recognised by name, so it doesn't ask for the PIN again (A19).
        val token = settings.token(h) ?: if (pin == null) nameOf(h)?.let { settings.namedToken(it) } else null
        if (token == null && pin == null) {
            _state.update { it.copy(status = ConnectionStatus.PAIRING, host = h, message = null) }
            return
        }
        cancelReconnect()
        ws?.close(1000, null)
        val gen = ++generation
        _state.update { it.copy(status = ConnectionStatus.CONNECTING, host = h, message = null) }
        val request = Request.Builder().url("ws://$h:$wsPort${Protocol.WS_PATH}").build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(Protocol.hello(settings.deviceId, settings.deviceName, appVersion, pin, token))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { if (gen == generation) handle(Protocol.parse(text)) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { if (gen == generation) lost("Can't reach $h. Is the companion running on that PC?") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { if (gen == generation) lost(null) }
            }
        })
    }

    private fun handle(msg: Incoming) {
        // Any message proves the PC is alive, not only a pong (A19-c).
        lastPong = System.currentTimeMillis()
        when (msg) {
            is Incoming.Welcome -> {
                host?.let { settings.saveToken(it, msg.token) }
                settings.saveNamedToken(msg.serverName, msg.token)
                settings.lastServerName = msg.serverName
                settings.lastServerHost = host
                session = msg.session
                udpPort = msg.udpPort
                lastPong = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        status = ConnectionStatus.CONNECTED, serverName = msg.serverName,
                        capabilities = msg.capabilities, message = null,
                    )
                }
                openUdp()
                startPing()
                listener?.onWelcome()
            }
            is Incoming.Error -> {
                val text = when (msg.code) {
                    "bad_pin" -> "Wrong PIN. Check the number on your PC and try again."
                    "bad_token", "not_paired" -> "This PC forgot the phone. Enter the PIN to pair again."
                    "rate_limited" -> "Too many wrong PINs. Wait a minute and try again."
                    "version_mismatch" -> "The companion needs a newer version of this app."
                    else -> msg.message.ifEmpty { "The companion reported an error (${msg.code})." }
                }
                if (msg.code in setOf("bad_pin", "bad_token", "not_paired", "rate_limited")) {
                    host?.let { h -> settings.clearToken(h); nameOf(h)?.let { settings.clearNamedToken(it) } }
                    closeSocket()
                    _state.update { it.copy(status = ConnectionStatus.PAIRING, message = text) }
                } else {
                    _state.update { it.copy(message = text) }
                }
            }
            is Incoming.Ping -> ws?.send("""{"v":1,"type":"pong","t":${msg.t}}""")
            is Incoming.Pong -> {
                lastPong = System.currentTimeMillis()
                val rtt = (System.currentTimeMillis() - msg.t).toInt().coerceAtLeast(0)
                _state.update { it.copy(latencyMs = rtt) }
            }
            is Incoming.CapabilitiesChanged -> _state.update { it.copy(capabilities = msg.capabilities) }
            is Incoming.State -> listener?.onState(msg.values, msg.full)
            is Incoming.BeamNGCommands -> listener?.onBeamNGCommands(msg.list)
            is Incoming.BindingsResult -> listener?.onBindingsResult(msg)
            is Incoming.VersionMismatch -> {
                ws?.send("""{"v":1,"type":"error","code":"version_mismatch","message":"phone supports v${Protocol.VERSION}"}""")
                userDisconnected = true // do not reconnect into the same mismatch
                closeSocket()
                _state.update { it.copy(status = ConnectionStatus.ERROR, message = "The companion uses a newer protocol (v${msg.remoteVersion}). Update the app.", latencyMs = null) }
            }
            else -> Unit
        }
    }

    private val pingTask = object : Runnable {
        override fun run() {
            if (!isConnected) return
            if (System.currentTimeMillis() - lastPong > PING_TIMEOUT_MS) {
                lost("Lost the connection to the PC.")
                return
            }
            ws?.send(Protocol.ping(System.currentTimeMillis()))
            main.postDelayed(this, PING_MS)
        }
    }

    private fun startPing() {
        main.removeCallbacks(pingTask)
        main.postDelayed(pingTask, PING_MS)
    }

    private fun lost(message: String?) {
        val wasConnected = isConnected
        closeSocket()
        if (userDisconnected) return
        val h = host
        // The state changes before the engine hears about it, so it forgets companion holds (M1).
        if (h != null && settings.token(h) != null) {
            _state.update { it.copy(status = ConnectionStatus.CONNECTING, message = message ?: "Reconnecting…", latencyMs = null) }
            if (wasConnected) listener?.onDisconnected()
            cancelReconnect()
            val task = Runnable { if (!userDisconnected && host == h && !isConnected) open(pin = null) }
            reconnectTask = task
            main.postDelayed(task, RECONNECT_MS)
        } else {
            if (_state.value.status != ConnectionStatus.PAIRING) {
                _state.update { it.copy(status = ConnectionStatus.ERROR, message = message, latencyMs = null) }
            } else if (wasConnected) {
                _state.update { it.copy(latencyMs = null) }
            }
            if (wasConnected) listener?.onDisconnected()
        }
    }

    private fun cancelReconnect() {
        reconnectTask?.let { main.removeCallbacks(it) }
        reconnectTask = null
    }

    private fun closeSocket() {
        generation++
        main.removeCallbacks(pingTask)
        ws?.close(1000, null)
        ws = null
        session = null
        val u = udp
        udp = null
        io.execute { u?.close() }
    }

    /** Opens the UDP socket for this session; a session closed meanwhile (generation moved on) is ignored (A10). */
    private fun openUdp() {
        val h = host ?: return
        val gen = generation
        io.execute {
            try {
                val target = InetAddress.getByName(h.removePrefix("[").removeSuffix("]"))
                val socket = DatagramSocket()
                main.post {
                    if (gen == generation && isConnected) {
                        udpTarget = target
                        udp = socket
                    } else {
                        socket.close()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "UDP unavailable, using WebSocket for all inputs", e)
            }
        }
    }

    // ---- sending -------------------------------------------------------------------------

    fun send(text: String) {
        ws?.send(text)
    }

    /** Sends an input event; unreliable ones go over UDP when it is available. */
    fun sendInput(id: String, value: Float, seq: Long, reliable: Boolean) {
        if (!isConnected) return
        val s = session ?: return
        val u = udp
        val target = udpTarget
        if (!reliable && u != null && target != null) {
            val bytes = Protocol.udpInput(s, seq, id, value).toByteArray()
            val port = udpPort
            io.execute {
                try {
                    u.send(DatagramPacket(bytes, bytes.size, target, port))
                } catch (e: Exception) {
                    main.post { ws?.send(Protocol.wsInput(seq, id, value)) }
                }
            }
        } else {
            ws?.send(Protocol.wsInput(seq, id, value))
        }
    }

    // ---- discovery -----------------------------------------------------------------------

    private var nsd: NsdManager? = null
    private var nsdListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /** Looks for companions with mDNS and the UDP broadcast fallback for [durationMs]. */
    fun startDiscovery(durationMs: Long = 8000) {
        stopDiscovery()
        _servers.value = emptyList()
        if (!isConnected) _state.update { if (it.status == ConnectionStatus.DISCONNECTED || it.status == ConnectionStatus.ERROR) it.copy(status = ConnectionStatus.SEARCHING) else it }
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        multicastLock = wifi?.createMulticastLock("gamectl")?.apply { setReferenceCounted(false); acquire() }
        val manager = context.getSystemService(NsdManager::class.java)
        nsd = manager
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                manager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = info.host ?: return
                        val raw = host.hostAddress ?: return
                        val addr = if (host is java.net.Inet6Address) "[${raw.substringBefore('%')}]" else raw
                        main.post { addServer(DiscoveredServer(info.serviceName, addr, info.port)) }
                    }
                })
            }
        }
        nsdListener = l
        runCatching { manager?.discoverServices(Protocol.SERVICE_TYPE.removeSuffix("."), NsdManager.PROTOCOL_DNS_SD, l) }
        io.execute { broadcastDiscover() }
        val myListener = l
        main.postDelayed({ if (nsdListener === myListener) stopDiscovery() }, durationMs)
    }

    fun stopDiscovery() {
        nsdListener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        nsdListener = null
        multicastLock?.let { runCatching { it.release() } }
        multicastLock = null
        _state.update { if (it.status == ConnectionStatus.SEARCHING) it.copy(status = ConnectionStatus.DISCONNECTED) else it }
    }

    private fun broadcastDiscover() {
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = 1500
                val out = Protocol.discover().toByteArray()
                s.send(DatagramPacket(out, out.size, InetAddress.getByName("255.255.255.255"), Protocol.DISCOVERY_PORT))
                val buf = ByteArray(2048)
                val end = System.currentTimeMillis() + 2500
                while (System.currentTimeMillis() < end) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(p)
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    val msg = Protocol.parse(String(p.data, 0, p.length))
                    if (msg is Incoming.DiscoverReply) {
                        val addr = p.address.hostAddress ?: continue
                        main.post { addServer(DiscoveredServer(msg.name, addr, msg.wsPort)) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.i(TAG, "Broadcast discovery failed", e)
        }
    }

    /** The PC name for [h], from discovery only: an old address may now belong to another PC (A19-a). */
    private fun nameOf(h: String): String? = _servers.value.firstOrNull { it.host == h }?.name

    private fun addServer(s: DiscoveredServer) {
        _servers.update { list -> if (list.any { it.host == s.host }) list else list + s }
        // The last PC came back on a new address: follow it without asking for the PIN (A19).
        // Discovery turns DISCONNECTED and ERROR into SEARCHING, so that counts as idle too (A19-b).
        val idle = _state.value.status in setOf(
            ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR, ConnectionStatus.SEARCHING, ConnectionStatus.CONNECTING,
        ) && !isConnected
        // Only while the app is retrying that same PC's old address: a PC the user picked since is never overridden (A-2).
        val retryingLast = host == null || host == settings.lastServerHost
        if (idle && retryingLast && !userDisconnected && s.name == settings.lastServerName && s.host != host && settings.namedToken(s.name) != null) {
            connect(if (s.host.contains(':')) "[${s.host}]:${s.wsPort}" else "${s.host}:${s.wsPort}")
        }
    }

    fun shutdown() {
        disconnect()
        stopDiscovery()
        io.shutdown()
    }

    companion object {
        private const val TAG = "CompanionClient"

        fun parseHostPort(spec: String): Pair<String, Int> {
            if (spec.startsWith("[")) {
                val end = spec.indexOf(']')
                if (end > 0) {
                    val port = spec.substring(end + 1).removePrefix(":").toIntOrNull() ?: Protocol.WS_PORT
                    return spec.substring(0, end + 1) to port
                }
            }
            val colons = spec.count { it == ':' }
            return when {
                colons == 0 -> spec to Protocol.WS_PORT
                colons == 1 -> spec.substringBefore(':') to (spec.substringAfter(':').toIntOrNull() ?: Protocol.WS_PORT)
                else -> "[$spec]" to Protocol.WS_PORT // bare IPv6
            }
        }
        private const val PING_MS = 2000L
        private const val PING_TIMEOUT_MS = 6000L
        private const val RECONNECT_MS = 3000L
    }
}

/** Small persisted settings: device identity, pairing tokens, last host. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("gamectl", Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString("deviceId", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceId", it).apply()
        }

    val deviceName: String = android.os.Build.MODEL ?: "Android"

    var lastHost: String?
        get() = prefs.getString("lastHost", null)
        set(v) = prefs.edit().putString("lastHost", v).apply()

    var lastProfileId: String?
        get() = prefs.getString("lastProfile", null)
        set(v) = prefs.edit().putString("lastProfile", v).apply()

    var lastHidHost: String?
        get() = prefs.getString("lastHidHost", null)
        set(v) = prefs.edit().putString("lastHidHost", v).apply()

    var imperialUnits: Boolean
        get() = prefs.getBoolean("imperial", false)
        set(v) = prefs.edit().putBoolean("imperial", v).apply()

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit().putBoolean("haptics", v).apply()

    fun token(host: String): String? = prefs.getString("token:$host", null)
    fun saveToken(host: String, token: String) = prefs.edit().putString("token:$host", token).apply()
    fun clearToken(host: String) = prefs.edit().remove("token:$host").apply()
    fun namedToken(serverName: String): String? = prefs.getString("token-name:$serverName", null)
    fun saveNamedToken(serverName: String, token: String) = prefs.edit().putString("token-name:$serverName", token).apply()
    fun clearNamedToken(serverName: String) = prefs.edit().remove("token-name:$serverName").apply()
    var lastServerName: String?
        get() = prefs.getString("lastServerName", null)
        set(v) = prefs.edit().putString("lastServerName", v).apply()
    /** The address [lastServerName] last welcomed this phone on. */
    var lastServerHost: String?
        get() = prefs.getString("lastServerHost", null)
        set(v) = prefs.edit().putString("lastServerHost", v).apply()
}
