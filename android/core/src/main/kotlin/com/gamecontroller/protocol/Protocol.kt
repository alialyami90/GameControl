package com.gamecontroller.protocol

import com.gamecontroller.model.Action
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.Capabilities
import com.gamecontroller.model.Control
import com.gamecontroller.model.Joystick
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.Profile
import com.gamecontroller.model.Action as ModelAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Protocol v1 constants and message codec. See protocol/PROTOCOL.md. */
object Protocol {
    const val VERSION = 1
    const val WS_PORT = 47800
    const val UDP_PORT = 47801
    const val DISCOVERY_PORT = 47802
    const val SERVICE_TYPE = "_gamectl._tcp."
    const val WS_PATH = "/ws"

    /** Shared JSON config: actions use "type" as discriminator, unknown fields are ignored. */
    val json = Json {
        serializersModule = SerializersModule {
            // Unknown action types (from a newer app or a hand-edited file) decode as "none".
            polymorphic(ModelAction::class) {
                defaultDeserializer { ModelAction.None.serializer() }
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
        coerceInputValues = true
    }

    /** Pretty variant for profile export files. */
    val prettyJson = Json(json) { prettyPrint = true }

    fun hello(deviceId: String, deviceName: String, appVersion: String, pin: String?, token: String?): String =
        obj("hello") {
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("appVersion", appVersion)
            if (token != null) put("token", token) else if (pin != null) put("pin", pin)
        }

    fun ping(t: Long): String = obj("ping") { put("t", t) }

    fun discover(): String = obj("discover") {}

    /** WebSocket input event (discrete kinds, or fallback when UDP is unavailable). */
    fun wsInput(seq: Long, id: String, value: Float): String = obj("input") {
        put("s", seq)
        put("id", id)
        put("val", value.toDouble())
    }

    /** UDP datagram payload. */
    fun udpInput(session: String, seq: Long, id: String, value: Float): String = buildJsonObject {
        put("v", VERSION)
        put("k", session)
        put("s", seq)
        put("id", id)
        put("val", value.toDouble())
    }.toString()

    /**
     * profile.sync with one binding per control. [hidRouted] holds the control ids the phone sends
     * over Bluetooth HID; those are marked "route":"hid" so the companion does not inject them.
     */
    fun profileSync(profile: Profile, hidRouted: Set<String>): String = obj("profile.sync") {
        put("profileId", profile.id)
        put("game", profile.game.name.lowercase())
        put("bindings", buildJsonArray {
            // A joystick goes out as its two axes (Joystick): the companion only ever sees sliders.
            profile.pages.flatMap { Joystick.expand(it.controls) }.forEach { add(binding(it, it.id in hidRouted)) }
        })
    }

    fun binding(control: Control, hid: Boolean = false): JsonObject = buildJsonObject {
        put("id", control.id)
        put("kind", control.type.wire)
        when (control.type) {
            ControlType.SWITCH, ControlType.RADIO ->
                put("positions", JsonArray(control.positions.map { action(it.action) }))
            ControlType.ROTARY -> {
                put("cw", action(control.cw))
                put("ccw", action(control.ccw))
            }
            else -> put("action", action(effectiveAction(control)))
        }
        if (hid) put("route", "hid")
    }

    /** A slider that is unipolar on the control is reported unipolar on its axis action too (A7). */
    fun effectiveAction(control: Control): Action {
        val a = control.action
        // A slider's control range is the one truth for its axis (A25), both ways.
        return if (control.type == ControlType.SLIDER && a is Action.VJoyAxis) a.copy(range = control.range) else a
    }

    fun action(action: Action): JsonElement = json.encodeToJsonElement(Action.serializer(), action)

    fun beamngBindingsApply(device: String, bindings: List<Pair<String, Int>>): String = obj("beamng.bindings.apply") {
        put("device", device)
        put("bindings", buildJsonArray {
            bindings.forEach { (name, button) ->
                add(buildJsonObject { put("beamngAction", name); put("button", button) })
            }
        })
    }

    private inline fun obj(type: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        buildJsonObject {
            put("v", VERSION)
            put("type", type)
            body()
        }.toString()

    /** Parses one incoming message. Returns [Incoming.Unknown] for anything unrecognised (forward compatible). */
    fun parse(text: String): Incoming {
        val o = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return Incoming.Malformed(text)
        }
        val v = o["v"]?.jsonPrimitive?.intOrNull ?: return Incoming.Malformed(text)
        if (v > VERSION) return Incoming.VersionMismatch(v)
        val str = { k: String -> o[k]?.jsonPrimitive?.contentOrNull }
        return when (str("type")) {
            "welcome" -> Incoming.Welcome(
                serverName = str("serverName") ?: "PC",
                token = str("token") ?: "",
                session = str("session") ?: "",
                udpPort = o["udpPort"]?.jsonPrimitive?.intOrNull ?: UDP_PORT,
                capabilities = capabilities(o["capabilities"]),
            )
            "error" -> Incoming.Error(str("code") ?: "unknown", str("message") ?: "")
            "pong" -> Incoming.Pong(o["t"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L)
            "ping" -> Incoming.Ping(o["t"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L)
            "capabilities" -> Incoming.CapabilitiesChanged(capabilities(o["capabilities"]))
            "state" -> Incoming.State(
                source = str("source") ?: "companion",
                values = (o["values"] as? JsonObject)?.mapValues { (_, e) -> primitive(e) }
                    ?.filterValues { it != null }?.mapValues { it.value!! } ?: emptyMap(),
                full = o["full"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
            "profile.ack" -> Incoming.ProfileAck(str("profileId") ?: "", o["count"]?.jsonPrimitive?.intOrNull ?: 0)
            "beamng.commands" -> Incoming.BeamNGCommands(
                (o["list"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            )
            "bindings.result" -> Incoming.BindingsResult(
                ok = o["ok"]?.jsonPrimitive?.booleanOrNull ?: false,
                message = str("message") ?: "",
                written = o["written"]?.jsonPrimitive?.booleanOrNull ?: false,
                count = o["count"]?.jsonPrimitive?.intOrNull ?: 0,
                unknown = (o["unknown"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                device = str("device") ?: "",
                busy = o["busy"]?.jsonPrimitive?.booleanOrNull ?: false,
                alreadyBound = o["alreadyBound"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
            "discover.reply" -> Incoming.DiscoverReply(
                str("name") ?: "PC",
                o["wsPort"]?.jsonPrimitive?.intOrNull ?: WS_PORT,
                o["udpPort"]?.jsonPrimitive?.intOrNull ?: UDP_PORT,
            )
            else -> Incoming.Unknown(str("type"))
        }
    }

    private fun capabilities(e: JsonElement?): Capabilities {
        val o = e as? JsonObject ?: return Capabilities()
        fun b(k: String) = o[k]?.jsonPrimitive?.booleanOrNull ?: false
        return Capabilities(
            keyboard = b("keyboard"),
            vjoy = b("vjoy"),
            vigem = b("vigem"),
            beamng = b("beamng"),
            outgauge = b("outgauge"),
        )
    }

    /** Game state values are booleans, numbers or short strings. */
    private fun primitive(e: JsonElement): Any? {
        val p = e as? JsonPrimitive ?: return null
        if (p.isString) return p.content
        p.booleanOrNull?.let { return it }
        p.doubleOrNull?.let { return it }
        return null
    }
}

sealed interface Incoming {
    data class Welcome(
        val serverName: String,
        val token: String,
        val session: String,
        val udpPort: Int,
        val capabilities: Capabilities,
    ) : Incoming
    data class Error(val code: String, val message: String) : Incoming
    data class Ping(val t: Long) : Incoming
    data class Pong(val t: Long) : Incoming
    data class CapabilitiesChanged(val capabilities: Capabilities) : Incoming
    data class State(val source: String, val values: Map<String, Any>, val full: Boolean) : Incoming
    data class ProfileAck(val profileId: String, val count: Int) : Incoming
    data class BeamNGCommands(val list: List<String>) : Incoming
    data class BindingsResult(
        val ok: Boolean,
        val message: String,
        val written: Boolean = false,
        val count: Int = 0,
        val unknown: List<String> = emptyList(),
        val device: String = "",
        val busy: Boolean = false,
        val alreadyBound: Boolean = false,
    ) : Incoming
    data class DiscoverReply(val name: String, val wsPort: Int, val udpPort: Int) : Incoming
    data class VersionMismatch(val remoteVersion: Int) : Incoming
    data class Malformed(val text: String) : Incoming
    data class Unknown(val type: String?) : Incoming
}
