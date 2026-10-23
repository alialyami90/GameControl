package com.gamecontroller

import com.gamecontroller.engine.Feedback
import com.gamecontroller.engine.InputEngine
import com.gamecontroller.engine.OutputLinks
import com.gamecontroller.engine.Route
import com.gamecontroller.hid.HidDescriptor
import com.gamecontroller.hid.HidState
import com.gamecontroller.model.Action
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.Joystick
import com.gamecontroller.model.FeedbackSource
import com.gamecontroller.model.GameId
import com.gamecontroller.model.KeyTrigger
import com.gamecontroller.model.OutputRoute
import com.gamecontroller.profiles.BuiltInProfiles
import com.gamecontroller.profiles.LayoutOps
import com.gamecontroller.profiles.ProfileJson
import com.gamecontroller.protocol.Incoming
import com.gamecontroller.protocol.Protocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeLinks(override var hidConnected: Boolean = false, override var companionConnected: Boolean = false) : OutputLinks {
    val hid = mutableListOf<Pair<Int, ByteArray>>()
    val companion = mutableListOf<CompanionEvent>()
    data class CompanionEvent(val id: String, val value: Float, val seq: Long, val reliable: Boolean)
    override fun sendHid(reportId: Int, payload: ByteArray) { hid += reportId to payload.copyOf() }
    override fun sendCompanion(id: String, value: Float, seq: Long, reliable: Boolean) { companion += CompanionEvent(id, value, seq, reliable) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoreTest {
    @Test fun builtInProfilesRoundTripThroughJson() {
        for (p in BuiltInProfiles.all) {
            val text = ProfileJson.export(p)
            val back = ProfileJson.import(text, newId = p.id).copy(builtIn = p.builtIn, schema = p.schema)
            assertEquals(p, back, "round trip of ${p.name}")
        }
    }

    @Test fun builtInLayoutsFitTheirGridsWithoutOverlap() {
        for (p in BuiltInProfiles.all) for (page in p.pages) {
            val cells = HashMap<Pair<Int, Int>, String>()
            for (c in page.controls) {
                assertTrue(c.x >= 0 && c.y >= 0 && c.x + c.w <= page.columns && c.y + c.h <= page.rows, "${c.id} out of grid")
                for (x in c.x until c.x + c.w) for (y in c.y until c.y + c.h) {
                    val prev = cells.put(x to y, c.id)
                    assertEquals(null, prev, "${c.id} overlaps $prev on ${p.name}/${page.name}")
                }
            }
            assertEquals(page.controls.size, page.controls.map { it.id }.toSet().size, "duplicate ids")
        }
    }

    @Test fun beamngProfileCoversRequiredFunctions() {
        val cmds = BuiltInProfiles.beamng().pages.flatMap { it.controls }.flatMap { c ->
            listOf(c.action, c.cw, c.ccw) + c.positions.map { it.action }
        }.filterIsInstance<Action.BeamNG>().map { it.command }.toSet()
        val required = listOf("lights.low", "lights.high", "signal.left", "signal.right", "hazard", "horn", "ignition.toggle", "starter",
            "gearbox.mode", "esc", "diff.front", "diff.rear", "diff.center", "awd", "range", "parkingbrake", "cruise", "camera.next",
            "recover", "reset", "slowmo", "pause")
        for (r in required) assertTrue(r in cmds, "missing $r")
    }

    @Test fun actionSerializesToProtocolShape() {
        val o = Protocol.action(Action.Key(listOf("LCTRL", "H"), KeyTrigger.TAP)).jsonObject
        assertEquals("key", o["type"]!!.jsonPrimitive.content)
        assertEquals("tap", o["trigger"]!!.jsonPrimitive.content)
        assertEquals("none", Protocol.action(Action.None).jsonObject["type"]!!.jsonPrimitive.content)
        val b = Protocol.binding(BuiltInProfiles.beamng().pages[0].controls.first { it.type == ControlType.SWITCH })
        assertEquals("switch", b["kind"]!!.jsonPrimitive.content)
        assertEquals(6, b["positions"]!!.jsonArray.size)
    }

    @Test fun parsesWelcomeStateAndIgnoresUnknown() {
        val w = Protocol.parse("""{"v":1,"type":"welcome","serverName":"PC","token":"t","session":"abcd1234","udpPort":47801,"capabilities":{"vjoy":true,"beamng":true},"extra":1}""")
        assertIs<Incoming.Welcome>(w); assertTrue(w.capabilities.beamng)
        val s = Protocol.parse("""{"v":1,"type":"state","source":"beamng","values":{"lights.low":true,"gear":"D","speed":12.5}}""")
        assertIs<Incoming.State>(s); assertEquals(true, s.values["lights.low"]); assertEquals("D", s.values["gear"]); assertEquals(12.5, s.values["speed"])
        assertIs<Incoming.Unknown>(Protocol.parse("""{"v":1,"type":"future.thing"}"""))
        assertIs<Incoming.VersionMismatch>(Protocol.parse("""{"v":2,"type":"welcome"}"""))
        assertIs<Incoming.Malformed>(Protocol.parse("not json"))
        // unknown action type decodes as part of an import without crashing? -> handled by coerceInputValues for enums only
    }

    @Test fun hidKeyboardReportHasModifiersAndKeys() {
        val h = HidState()
        h.keyDown("LCTRL"); h.keyDown("H")
        val r = h.keyboardReport()
        assertEquals(0x01, r[0].toInt())
        assertEquals(0x0B, r[2].toInt())
        h.keyDown("H"); h.keyUp("H")
        assertEquals(0x0B, h.keyboardReport()[2].toInt(), "refcounted key stays down")
    }

    @Test fun hidGamepadReportLayout() {
        val h = HidState()
        h.buttonDown(1); h.buttonDown(64); h.setAxis("X", 1f); h.setAxis("SL1", -1f)
        val r = h.gamepadReport()
        assertEquals(24, r.size)
        assertEquals(1, r[0].toInt() and 1)
        assertEquals(0x80, r[7].toInt() and 0xFF)
        assertEquals(32767, (r[8].toInt() and 0xFF) or (r[9].toInt() shl 8))
        assertEquals(-32767, ((r[22].toInt() and 0xFF) or (r[23].toInt() shl 8)).toShort().toInt())
        assertTrue(HidDescriptor.REPORT_MAP.size > 50)
    }

    @Test fun momentaryOverHidPressesAndReleasesButton() = runTest {
        val links = FakeLinks(hidConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        links.hid.clear()
        val b1 = e.profile!!.pages[0].controls.first { it.id == "gp_b1" }
        assertEquals(Route.HID, e.routeFor(b1))
        e.onValue(b1, 1f)
        assertEquals(1, links.hid.last().second[0].toInt() and 1)
        e.onValue(b1, 0f)
        assertEquals(0, links.hid.last().second[0].toInt() and 1)
        advanceUntilIdle()
    }

    @Test fun latchingTapKeyOverHidTapsOncePerChange() = runTest {
        val links = FakeLinks(hidConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.mudrunner()
        links.hid.clear()
        val diff = e.profile!!.pages[0].controls.first { it.id == "mr_diff" }
        e.onValue(diff, 1f)
        e.onValue(diff, 1f) // repeated: ignored
        advanceUntilIdle()
        val keyReports = links.hid.filter { it.first == HidDescriptor.KEYBOARD_REPORT_ID }
        assertEquals(2, keyReports.size, "one down + one up")
        assertEquals(0x14, keyReports[0].second[2].toInt()) // Q
        assertEquals(0, keyReports[1].second[2].toInt())
    }

    @Test fun companionRoutingUsesReliableChannelForDiscreteAndResendsHeld() = runTest {
        val links = FakeLinks(companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.beamng()
        val c = e.profile!!.pages[0].controls
        val hazard = c.first { it.id == "bng_hazard" }
        val horn = c.first { it.id == "bng_horn" }
        assertEquals(Route.COMPANION, e.routeFor(hazard))
        e.onValue(hazard, 1f)
        assertTrue(links.companion.last().reliable)
        e.onValue(horn, 1f)
        assertFalse(links.companion.last().reliable)
        advanceTimeBy(600)
        val hornEvents = links.companion.filter { it.id == "bng_horn" }
        assertTrue(hornEvents.size >= 3, "held momentary resends, got ${hornEvents.size}")
        e.onValue(horn, 0f)
        advanceTimeBy(100)
        assertEquals(3, links.companion.filter { it.id == "bng_horn" && it.value == 0f }.size, "release sent 3 times")
        val seqs = links.companion.map { it.seq }
        assertEquals(seqs.sorted(), seqs, "sequence increases")
        e.releaseAll()
        advanceUntilIdle()
    }

    @Test fun hidModeMarksRoutedIdsAndKeepsBeamngOnCompanion() = runTest {
        val links = FakeLinks(hidConnected = true, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.beamng().copy(output = OutputRoute.AUTO)
        assertTrue(e.hidRoutedIds().isEmpty(), "beamng commands can't go over HID")
        e.profile = BuiltInProfiles.gamepad()
        assertTrue("gp_b1" in e.hidRoutedIds())
        val sync = Protocol.parse(Protocol.profileSync(e.profile!!, e.hidRoutedIds()))
        assertIs<Incoming.Unknown>(sync) // phone->pc message type, not parsed by phone
    }

    @Test fun feedbackPendingThenGameConfirms() {
        val p = BuiltInProfiles.beamng()
        val values = mapOf("bng_low" to 1f)
        var r = Feedback.resolve(p, values, mapOf("lights.low" to false), mapOf("bng_low" to 0L), now = 100)
        assertEquals(FeedbackSource.PENDING, r.indicators["bng_low"]!!.source)
        assertTrue(r.indicators["bng_low"]!!.lit)
        r = Feedback.resolve(p, values, mapOf("lights.low" to true), emptyMap(), now = 200)
        assertEquals(FeedbackSource.GAME, r.indicators["bng_low"]!!.source)
        // timed out -> game wins, and syncValues pulls the latch back
        val synced = Feedback.syncValues(p, values, mapOf("lights.low" to false), mapOf("bng_low" to 0L), now = 5000)
        assertEquals(0f, synced["bng_low"])
        r = Feedback.resolve(p, values, mapOf("has.fog" to false), emptyMap(), 0)
        assertTrue("bng_fog" in r.unavailable)
        val gear = Feedback.resolve(p, mapOf(), mapOf("gearbox.mode" to "D"), emptyMap(), 0).indicators["bng_gearbox"]!!
        assertEquals("D", gear.text)
    }

    @Test fun unknownActionTypeImportsAsNone() {
        val text = """{"id":"x","name":"X","pages":[{"id":"p","name":"P","controls":[{"id":"c1","type":"momentary","label":"A","action":{"type":"teleport","to":"mars"}}]}]}"""
        val p = ProfileJson.import(text, "x")
        assertEquals(Action.None, p.pages[0].controls[0].action)
    }

    @Test fun importDedupesIdsAcrossPages() {
        val text = """{"id":"x","name":"X","pages":[
            {"id":"p","name":"P","controls":[{"id":"c1","type":"momentary","label":"A"}]},
            {"id":"p","name":"Q","controls":[{"id":"c1","type":"momentary","label":"B"}]}]}"""
        val p = ProfileJson.import(text, "x")
        assertEquals(2, p.pages.map { it.id }.toSet().size)
        assertEquals(2, p.pages.flatMap { it.controls }.map { it.id }.toSet().size)
    }

    @Test fun unipolarAxesRestAtMinimumOverHid() = runTest {
        val links = FakeLinks(hidConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        val r = links.hid.last { it.first == HidDescriptor.GAMEPAD_REPORT_ID }.second
        // SL0 is axis index 6: bytes 8 + 6*2 = 20..21 -> -32767
        assertEquals(-32767, ((r[20].toInt() and 0xFF) or (r[21].toInt() shl 8)).toShort().toInt())
        // Z (bipolar, index 2) rests at centre
        assertEquals(0, ((r[12].toInt() and 0xFF) or (r[13].toInt() shl 8)).toShort().toInt())
        advanceUntilIdle()
    }

    @Test fun latchRepeatsAfterGameChangedState() = runTest {
        val links = FakeLinks(companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.beamng()
        val sig = e.profile!!.pages[0].controls.first { it.id == "bng_sig_l" }
        e.onValue(sig, 1f)
        e.syncValue(sig.id, 0f) // signal self-cancelled in game
        e.onValue(sig, 1f)
        assertEquals(2, links.companion.count { it.id == sig.id && it.value == 1f })
        advanceUntilIdle()
    }

    @Test fun releaseFollowsTheRouteOfItsPressAndLinkLossClearsHid() = runTest {
        val links = FakeLinks(hidConnected = true, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.mudrunner()
        val gas = e.profile!!.pages[0].controls.first { it.id == "mr_up" }
        e.onValue(gas, 1f)
        assertEquals(Route.HID, e.routeFor(gas))
        links.hidConnected = false
        e.onLinkChanged()
        // Release after the HID link dropped: nothing may go to the companion for a press it never saw.
        e.onValue(gas, 0f)
        assertTrue(links.companion.none { it.id == gas.id }, "release must not leak to the other route")
        links.hidConnected = true
        e.onLinkChanged()
        val kb = links.hid.last { it.first == HidDescriptor.KEYBOARD_REPORT_ID }.second
        assertTrue(kb.all { it.toInt() == 0 }, "fresh HID link starts with nothing held")
        advanceUntilIdle()
    }

    @Test fun releaseAllCancelsMacrosAndReleasesHeldCompanionInputs() = runTest {
        val links = FakeLinks(companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.beamng()
        val horn = e.profile!!.pages[0].controls.first { it.id == "bng_horn" }
        e.onValue(horn, 1f)
        e.releaseAll()
        assertEquals(0f, links.companion.last { it.id == horn.id }.value)
        advanceUntilIdle()
    }

    @Test fun a21_syncValueReleasesHidHoldSoRefcountDoesNotDrift() = runTest {
        val links = FakeLinks(hidConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        val t = e.profile!!.pages[0].controls.first { it.id == "gp_t13" } // latching HID button 13
        fun bit13() = links.hid.last { it.first == HidDescriptor.GAMEPAD_REPORT_ID }.second[1].toInt() and 0x10
        e.onValue(t, 1f); assertTrue(bit13() != 0)
        e.syncValue(t.id, 0f); assertEquals(0, bit13(), "game said off: button released")
        e.onValue(t, 1f); assertTrue(bit13() != 0)
        e.onValue(t, 0f); assertEquals(0, bit13(), "unlatch releases the button")
        advanceUntilIdle()
    }

    @Test fun a22_profileSetClearsStaleValues() = runTest {
        val links = FakeLinks(hidConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        val t = e.profile!!.pages[0].controls.first { it.id == "gp_t13" }
        e.onValue(t, 1f)
        e.profile = BuiltInProfiles.gamepad()
        assertEquals(null, e.currentValue(t.id))
        links.hid.clear()
        e.onValue(t, 1f)
        val bit = links.hid.last { it.first == HidDescriptor.GAMEPAD_REPORT_ID }.second[1].toInt() and 0x10
        assertTrue(bit != 0, "first tap after re-opening the profile must press")
        advanceUntilIdle()
    }

    @Test fun a23_isHeldTracksLiveHoldsOnly() = runTest {
        val links = FakeLinks(hidConnected = true, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.mudrunner()
        val gas = e.profile!!.pages[0].controls.first { it.id == "mr_up" }
        e.onValue(gas, 1f)
        assertTrue(e.isHeld(gas.id))
        links.hidConnected = false
        e.onLinkChanged()
        assertFalse(e.isHeld(gas.id), "forgotten after its link dropped, so a replay is allowed")
        advanceUntilIdle()
    }

    @Test fun a24_routeFlipReleasesOnOldRouteFirst() = runTest {
        val links = FakeLinks(hidConnected = false, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        val s = e.profile!!.pages[0].controls.first { it.id == "gp_throttle" }
        e.onValue(s, 0.6f)
        assertEquals(Route.COMPANION, e.routeFor(s))
        links.hidConnected = true
        e.onLinkChanged()
        e.onValue(s, 0.8f)
        assertEquals(0f, links.companion.last { it.id == s.id }.value, "companion axis parked before HID takes over")
        val r = links.hid.last { it.first == HidDescriptor.GAMEPAD_REPORT_ID }.second
        val sl0 = ((r[20].toInt() and 0xFF) or (r[21].toInt() shl 8)).toShort().toInt()
        assertTrue(sl0 > 15000, "HID axis follows the new value, got $sl0")
        advanceUntilIdle()
    }

    @Test fun a28_switchRouteFlipDoesNotTapPositionZeroOnCompanion() = runTest {
        val links = FakeLinks(hidConnected = false, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        val sw = e.profile!!.pages[0].controls.first { it.id == "gp_mode" }
        e.onValue(sw, 2f)
        links.hidConnected = true
        e.onLinkChanged()
        links.companion.clear()
        e.onValue(sw, 1f)
        assertTrue(links.companion.none { it.id == sw.id }, "a discrete control is not parked on the old route: ${links.companion}")
        advanceUntilIdle()
    }

    @Test fun a29_gameInitiatedOnDoesNotPressHidButton() = runTest {
        val links = FakeLinks(hidConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        val t = e.profile!!.pages[0].controls.first { it.id == "gp_t13" }
        fun bit13() = links.hid.lastOrNull { it.first == HidDescriptor.GAMEPAD_REPORT_ID }?.second?.get(1)?.toInt()?.and(0x10) ?: 0
        e.onValue(t, 1f); e.onValue(t, 0f); assertEquals(0, bit13())
        links.hid.clear()
        e.syncValue(t.id, 1f)
        assertEquals(0, bit13(), "the game turning a feature on must not press the button")
        e.syncValue(t.id, 0f)
        assertEquals(0, bit13())
        advanceUntilIdle()
    }

    @Test fun as1_latchOnCompanionIsPressedOverHidWhenBluetoothComesUp() = runTest {
        val links = FakeLinks(hidConnected = false, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        val t = e.profile!!.pages[0].controls.first { it.id == "gp_t13" }
        e.onValue(t, 1f)
        assertEquals(Route.COMPANION, e.routeFor(t))
        links.hidConnected = true
        e.onLinkChanged()
        e.rehomeLatches()
        val bit13 = links.hid.last { it.first == HidDescriptor.GAMEPAD_REPORT_ID }.second[1].toInt() and 0x10
        assertTrue(bit13 != 0, "latch is pressed over HID after the route moved")
        e.onValue(t, 0f)
        assertEquals(0, links.hid.last { it.first == HidDescriptor.GAMEPAD_REPORT_ID }.second[1].toInt() and 0x10)
        advanceUntilIdle()
    }

    @Test fun r6_4_newerSchemaIsRefusedBeforeDecoding() {
        val text = ProfileJson.export(BuiltInProfiles.bulldozer())
            .replace("\"schema\": 2", "\"schema\": 3").replace("\"joystick\"", "\"hologram\"")
        val e = runCatching { ProfileJson.import(text) }.exceptionOrNull()
        assertNotNull(e)
        assertTrue("newer version" in (e.message ?: ""), "got: ${e.message}")
    }

    @Test fun m1_companionLossForgetsLatchesEvenWhileLinkStillReadsUp() = runTest {
        val links = FakeLinks(hidConnected = false, companionConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        e.profile = BuiltInProfiles.gamepad()
        val t = e.profile!!.pages[0].controls.first { it.id == "gp_t13" }
        e.onValue(t, 1f)
        assertTrue(e.isHeld(t.id))
        // The client calls back before its state reads DISCONNECTED: the hold must still be forgotten.
        e.onCompanionLost()
        assertFalse(e.isHeld(t.id), "the next welcome must replay the latch")
        advanceUntilIdle()
    }

    @Test fun joystickDrivesTwoAxesAndSyncsAsTwoSliders() = runTest {
        val links = FakeLinks(hidConnected = true)
        val e = InputEngine(this, links) { testScheduler.currentTime }
        val p = BuiltInProfiles.bulldozer()
        e.profile = p
        val blade = p.pages[0].controls.first { it.id == "dz_blade" }
        assertEquals(ControlType.JOYSTICK, blade.type)
        val (bx, by) = Joystick.axes(blade)
        assertEquals("dz_blade.x", bx.id); assertEquals("dz_blade.y", by.id)
        assertEquals(by.id, LayoutOps.findControl(p, "dz_blade.y")?.second?.id)
        assertTrue("dz_blade.y" in e.hidRoutedIds())
        e.onValue(by, 1f)
        val r = links.hid.last { it.first == HidDescriptor.GAMEPAD_REPORT_ID }.second
        val rx = ((r[14].toInt() and 0xFF) or (r[15].toInt() shl 8)).toShort().toInt()
        assertTrue(rx > 30000, "forward on the blade stick drives RX, got $rx")
        val sync = Protocol.profileSync(p, emptySet())
        assertTrue("\"dz_blade.x\"" in sync && "\"dz_blade.y\"" in sync && "\"joystick\"" !in sync, sync)
        advanceUntilIdle()
    }

    @Test fun joystickProfilesExportAsSchema2AndKeepActionY() {
        val p = BuiltInProfiles.utility()
        val text = ProfileJson.export(p)
        assertTrue("\"schema\": 2" in text, "joystick profile is schema 2")
        assertTrue("\"schema\": 1" in ProfileJson.export(BuiltInProfiles.mudrunner()), "no joystick: schema 1")
        val back = ProfileJson.import(text)
        val stick = back.pages[1].controls.first { it.id == "ex_right_stick" }
        assertEquals(Action.VJoyAxis(1, "RX"), stick.actionY)
        assertEquals(Action.VJoyAxis(1, "SL1"), stick.action)
    }

    @Test fun machineButtonsFitIn32AndAreUniquePerPage() {
        for (p in listOf(BuiltInProfiles.bulldozer(), BuiltInProfiles.utility())) for (page in p.pages) {
            val buttons = page.controls.flatMap { c -> listOf(c.action, c.cw, c.ccw) + c.positions.map { it.action } }
                .filterIsInstance<Action.VJoyButton>().map { it.button }
            assertTrue(buttons.all { it in 1..32 }, "${page.name}: $buttons")
            assertEquals(buttons.size, buttons.toSet().size, "${page.name} reuses a button: $buttons")
        }
    }

    @Test fun a25_sliderRangeComesFromTheControl() {
        val base = BuiltInProfiles.gamepad().pages[0].controls.first { it.id == "gp_axis_z" }
        val bipolarControlUnipolarAction = base.copy(range = AxisRange.BIPOLAR, action = Action.VJoyAxis(1, "Z", AxisRange.UNIPOLAR))
        assertEquals(AxisRange.BIPOLAR, (Protocol.effectiveAction(bipolarControlUnipolarAction) as Action.VJoyAxis).range)
        assertFalse(InputEngine.isUnipolar(bipolarControlUnipolarAction, Action.VJoyAxis(1, "Z", AxisRange.UNIPOLAR)))
        val unipolarControl = base.copy(range = AxisRange.UNIPOLAR, action = Action.VJoyAxis(1, "Z", AxisRange.BIPOLAR))
        assertEquals(AxisRange.UNIPOLAR, (Protocol.effectiveAction(unipolarControl) as Action.VJoyAxis).range)
    }

    @Test fun r6_5_joystickIndicatorFollowsItsAxes() {
        val p = BuiltInProfiles.bulldozer()
        val r = Feedback.resolve(p, mapOf("dz_blade.y" to 0.8f), emptyMap(), emptyMap(), 0L)
        assertTrue(r.indicators["dz_blade"]!!.lit)
        assertFalse(Feedback.resolve(p, emptyMap(), emptyMap(), emptyMap(), 0L).indicators["dz_blade"]!!.lit)
    }

    @Test fun layoutOpsClampAndPages() {
        var p = LayoutOps.newProfile("Test", GameId.CUSTOM)
        val (p2, id) = LayoutOps.addControl(p, 0, ControlType.SLIDER, 100, 100)
        p = p2
        val c = p.pages[0].controls.single()
        assertEquals(id, c.id)
        assertTrue(c.x + c.w <= p.pages[0].columns && c.y + c.h <= p.pages[0].rows)
        p = LayoutOps.resize(p, 0, id, 50, 50)
        assertTrue(p.pages[0].controls.single().let { it.x + it.w <= 8 && it.y + it.h <= 4 })
        p = LayoutOps.addPage(p); assertEquals(2, p.pages.size)
        p = LayoutOps.deletePage(p, 1); p = LayoutOps.deletePage(p, 0); assertEquals(1, p.pages.size)
        assertNotNull(LayoutOps.findControl(p, id))
    }
}
