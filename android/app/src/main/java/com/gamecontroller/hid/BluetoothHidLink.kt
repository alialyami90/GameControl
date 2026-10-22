package com.gamecontroller.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.gamecontroller.model.BtHost
import com.gamecontroller.model.HidStatus
import com.gamecontroller.model.HidUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

/**
 * The phone as a Bluetooth HID device ("GameCtl Pad"): keyboard + 64-button / 8-axis gamepad.
 * Windows pairs it like any Bluetooth controller; no PC software is needed.
 */
@SuppressLint("MissingPermission") // every entry point checks hasPermission() first
class BluetoothHidLink(private val context: Context) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = manager?.adapter
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false
    private var wantRegistered = false

    private val _state = MutableStateFlow(HidUi(status = if (adapter == null) HidStatus.UNSUPPORTED else HidStatus.OFF))
    val state: StateFlow<HidUi> = _state.asStateFlow()

    /** Called on the main thread whenever the connected/disconnected state changes. */
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    /** Supplies the current input report for a GET_REPORT request; set by the input engine owner. */
    var currentReport: ((reportId: Int) -> ByteArray?)? = null

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> if (wantRegistered) enable()
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    val wasConnected = host != null
                    host = null
                    registered = false
                    setStatus(HidStatus.OFF, "Bluetooth is off.")
                    if (wasConnected) onConnectionChanged?.invoke(false)
                }
            }
        }
    }

    init {
        context.registerReceiver(stateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    val isConnected: Boolean get() = host != null && _state.value.status == HidStatus.CONNECTED

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyArray()
        }

    fun hasPermission(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Intent that makes the phone discoverable so a PC can pair with it the first time. */
    fun discoverableIntent(): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    /** Registers the HID app with the Bluetooth stack. Safe to call repeatedly. */
    fun enable() {
        val a = adapter ?: return setStatus(HidStatus.UNSUPPORTED, "This phone has no Bluetooth.")
        if (!hasPermission()) return setStatus(HidStatus.OFF, "Allow Nearby devices permission to use Bluetooth.")
        if (!a.isEnabled) return setStatus(HidStatus.OFF, "Turn Bluetooth on first.")
        wantRegistered = true
        if (hid != null) {
            register()
            return
        }
        setStatus(HidStatus.REGISTERING, null)
        val ok = a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hid = proxy as BluetoothHidDevice
                if (wantRegistered) register()
            }

            override fun onServiceDisconnected(profile: Int) {
                val wasConnected = host != null
                hid = null
                registered = false
                host = null
                setStatus(HidStatus.OFF, "Bluetooth stopped. Tap Enable to register the controller again.")
                if (wasConnected) onConnectionChanged?.invoke(false)
            }
        }, BluetoothProfile.HID_DEVICE)
        if (!ok) setStatus(HidStatus.UNSUPPORTED, "This phone does not support Bluetooth HID.")
    }

    private fun register() {
        val h = hid ?: return
        if (registered) {
            refreshHosts()
            return
        }
        val sdp = BluetoothHidDeviceAppSdpSettings(
            HidDescriptor.NAME,
            HidDescriptor.DESCRIPTION,
            HidDescriptor.PROVIDER,
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.REPORT_MAP,
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED, 800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX,
        )
        val ok = h.registerApp(sdp, null, qos, executor, callback)
        if (!ok) setStatus(HidStatus.OFF, "Bluetooth refused to register the controller. Try turning Bluetooth off and on.")
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            main.post {
                this@BluetoothHidLink.registered = registered
                if (registered) {
                    setStatus(HidStatus.READY, null)
                    refreshHosts()
                    // Reconnect to the PC we were plugged into, if Android remembers it.
                    if (pluggedDevice != null && hasPermission()) hid?.connect(pluggedDevice)
                } else {
                    host = null
                    setStatus(HidStatus.OFF, null)
                    onConnectionChanged?.invoke(false)
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            main.post {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        host = device
                        _state.update { it.copy(status = HidStatus.CONNECTED, hostName = safeName(device), message = null) }
                        onConnectionChanged?.invoke(true)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (host == null || host?.address == device.address) {
                            host = null
                            _state.update {
                                it.copy(status = if (registered) HidStatus.READY else HidStatus.OFF, hostName = null)
                            }
                            onConnectionChanged?.invoke(false)
                        }
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val h = hid ?: return
            val report = currentReport?.invoke(id.toInt()) ?: when (id.toInt()) {
                HidDescriptor.KEYBOARD_REPORT_ID -> ByteArray(8)
                HidDescriptor.GAMEPAD_REPORT_ID -> ByteArray(HidDescriptor.BUTTONS / 8 + HidDescriptor.AXES * 2)
                else -> null
            }
            if (report != null) h.replyReport(device, type, id, report)
            else h.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray?) {
            // Keyboard LED state from the PC. Not used, but must be acknowledged.
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    fun refreshHosts() {
        val a = adapter ?: return
        if (!hasPermission()) return
        val hosts = a.bondedDevices.orEmpty()
            .filter { d ->
                val major = d.bluetoothClass?.majorDeviceClass
                major == null || major == BluetoothClass.Device.Major.COMPUTER || major == BluetoothClass.Device.Major.UNCATEGORIZED
            }
            .map { BtHost(safeName(it), it.address) }
            .sortedBy { it.name.lowercase() }
        _state.update { it.copy(bondedHosts = hosts) }
    }

    fun connect(address: String) {
        val a = adapter ?: return
        if (!hasPermission()) return
        val device = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: return
        if (!registered) {
            enable()
            return
        }
        val ok = hid?.connect(device) == true
        if (!ok) _state.update { it.copy(message = "Couldn't reach ${safeName(device)}. Make sure the PC's Bluetooth is on.") }
    }

    fun disconnect() {
        val d = host ?: return
        if (hasPermission()) hid?.disconnect(d)
    }

    /** Sends one input report. Called on the main thread by the input engine. */
    fun send(reportId: Int, payload: ByteArray) {
        val d = host ?: return
        val h = hid ?: return
        if (!hasPermission()) return
        try {
            h.sendReport(d, reportId, payload)
        } catch (e: Exception) {
            Log.w(TAG, "sendReport failed", e)
        }
    }

    fun shutdown() {
        wantRegistered = false
        runCatching { context.unregisterReceiver(stateReceiver) }
        val h = hid
        if (h != null && hasPermission()) {
            host?.let { h.disconnect(it) }
            if (registered) h.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        }
        hid = null
        host = null
        registered = false
        executor.shutdown()
    }

    private fun safeName(d: BluetoothDevice): String =
        (if (hasPermission()) d.name else null) ?: d.address

    private fun setStatus(status: HidStatus, message: String?) {
        _state.update { it.copy(status = status, message = message, hostName = if (status == HidStatus.CONNECTED) it.hostName else null) }
    }

    companion object {
        private const val TAG = "BluetoothHidLink"
    }
}
