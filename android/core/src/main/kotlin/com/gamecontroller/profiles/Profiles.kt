package com.gamecontroller.profiles

import com.gamecontroller.model.Accent
import com.gamecontroller.model.Action
import com.gamecontroller.model.AxisRange
import com.gamecontroller.model.Control
import com.gamecontroller.model.Joystick
import com.gamecontroller.model.ControlType
import com.gamecontroller.model.GameId
import com.gamecontroller.model.Page
import com.gamecontroller.model.Position
import com.gamecontroller.model.Profile
import com.gamecontroller.protocol.Protocol
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** Import and export of `.gcprofile.json` files (PROTOCOL.md, Profile file format). */
object ProfileJson {
    /** Newest schema this app reads. 2 added the joystick control and Control.actionY. */
    const val SCHEMA = 2

    /**
     * A profile is written with the oldest schema that can hold it, so a profile without
     * joysticks still opens in an app that only knows schema 1. An app that reads the schema
     * before decoding (this one, from 1.0) refuses a newer file with a clear "newer version"
     * message; the betas decoded first, so they show a parse error for a schema 2 file.
     */
    fun schemaFor(profile: Profile): Int =
        if (profile.pages.any { pg -> pg.controls.any { it.type == ControlType.JOYSTICK } }) 2 else 1

    fun export(profile: Profile): String =
        Protocol.prettyJson.encodeToString(Profile.serializer(), profile.copy(schema = schemaFor(profile)))

    /**
     * Parses an exported profile. The imported copy always gets a fresh id, is never built-in,
     * and has its layout clamped to the grid so a hand-edited file cannot break the editor.
     */
    fun import(text: String, newId: String = newId()): Profile {
        // The schema is read before decoding: a newer file may hold controls this app can't parse (R6-4).
        val schema = runCatching {
            Protocol.json.parseToJsonElement(text).jsonObject["schema"]?.jsonPrimitive?.intOrNull
        }.getOrNull() ?: 1
        require(schema <= SCHEMA) { "This profile was made by a newer version of the app (schema $schema)." }
        val p = Protocol.json.decodeFromString(Profile.serializer(), text)
        require(p.pages.isNotEmpty()) { "The profile has no pages." }
        return LayoutOps.sanitize(p.copy(id = newId, builtIn = false))
    }

    fun fileName(profile: Profile): String =
        profile.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "profile" } + ".gcprofile.json"

    fun newId(): String = "p-" + UUID.randomUUID().toString().take(8)
}

/** Pure layout edits used by the editor. Every function returns a new Profile. */
object LayoutOps {
    const val MIN_COLUMNS = 4
    const val MAX_COLUMNS = 12
    const val MIN_ROWS = 2
    const val MAX_ROWS = 8

    fun newProfile(name: String, game: GameId): Profile = Profile(
        id = ProfileJson.newId(),
        name = name.ifBlank { "New profile" },
        game = game,
        pages = listOf(Page(id = newPageId(), name = "Main")),
    )

    fun duplicate(profile: Profile): Profile = profile.copy(
        id = ProfileJson.newId(),
        name = profile.name + " copy",
        builtIn = false,
    )

    fun newPageId() = "pg-" + UUID.randomUUID().toString().take(6)
    fun newControlId() = "c-" + UUID.randomUUID().toString().take(8)

    /** A new control of [type] with sensible defaults, placed at (x, y) on [page]. */
    fun defaultControl(type: ControlType, x: Int, y: Int, page: Page): Control {
        val base = Control(id = newControlId(), type = type, label = defaultLabel(type), x = x, y = y)
        val c = when (type) {
            ControlType.MOMENTARY -> base.copy(action = Action.VJoyButton(1, 1), accent = Accent.NEUTRAL)
            ControlType.LATCHING -> base.copy(action = Action.VJoyButton(1, 1), accent = Accent.AMBER)
            ControlType.SWITCH -> base.copy(w = 3, accent = Accent.CYAN, positions = listOf(Position("A"), Position("B"), Position("C")))
            ControlType.RADIO -> base.copy(w = 3, accent = Accent.CYAN, positions = listOf(Position("1"), Position("2"), Position("3")))
            ControlType.SLIDER -> base.copy(h = 2, accent = Accent.GREEN, action = Action.VJoyAxis(1, "SL0", AxisRange.UNIPOLAR), range = AxisRange.UNIPOLAR)
            ControlType.ROTARY -> base.copy(w = 2, h = 2, accent = Accent.CYAN)
            ControlType.JOYSTICK -> base.copy(w = 2, h = 2, accent = Accent.AMBER, action = Action.VJoyAxis(1, "X"), actionY = Action.VJoyAxis(1, "Y"))
        }
        return clamp(c, page)
    }

    private fun defaultLabel(type: ControlType) = when (type) {
        ControlType.MOMENTARY -> "Button"
        ControlType.LATCHING -> "Toggle"
        ControlType.SWITCH -> "Switch"
        ControlType.RADIO -> "Select"
        ControlType.SLIDER -> "Slider"
        ControlType.ROTARY -> "Dial"
        ControlType.JOYSTICK -> "Joystick"
    }

    /** Smallest span (w, h) a control may have so it stays usable at touch size. */
    fun minSpan(c: Control): Pair<Int, Int> = when (c.type) {
        ControlType.SWITCH, ControlType.RADIO -> c.positions.size.coerceAtLeast(2) to 1
        ControlType.ROTARY, ControlType.JOYSTICK -> 2 to 2
        ControlType.SLIDER -> if (c.vertical) 1 to 2 else 2 to 1
        else -> 1 to 1
    }

    /** Applies the minimum span, then keeps the control inside the page grid. */
    fun clamp(c: Control, page: Page): Control {
        val (minW, minH) = minSpan(c)
        val w = c.w.coerceAtLeast(minW).coerceIn(1, page.columns)
        val h = c.h.coerceAtLeast(minH).coerceIn(1, page.rows)
        return c.copy(w = w, h = h, x = c.x.coerceIn(0, page.columns - w), y = c.y.coerceIn(0, page.rows - h))
    }

    /** Clamps grids and layouts and makes every page id and control id unique across the whole profile. */
    fun sanitize(p: Profile): Profile {
        val seenControls = HashSet<String>()
        val seenPages = HashSet<String>()
        return p.copy(pages = p.pages.map { page ->
            val pageId = if (page.id.isBlank() || !seenPages.add(page.id)) newPageId().also { seenPages += it } else page.id
            val pg = page.copy(
                id = pageId,
                columns = page.columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS),
                rows = page.rows.coerceIn(MIN_ROWS, MAX_ROWS),
            )
            pg.copy(controls = pg.controls.map { c ->
                val id = if (c.id.isBlank() || !seenControls.add(c.id)) newControlId().also { seenControls += it } else c.id
                clamp(c.copy(id = id), pg)
            })
        })
    }

    private fun updatePage(p: Profile, pageIndex: Int, f: (Page) -> Page): Profile =
        p.copy(pages = p.pages.mapIndexed { i, pg -> if (i == pageIndex) f(pg) else pg })

    fun addControl(p: Profile, pageIndex: Int, type: ControlType, x: Int, y: Int): Pair<Profile, String> {
        val page = p.pages[pageIndex]
        val c = defaultControl(type, x, y, page)
        return updatePage(p, pageIndex) { it.copy(controls = it.controls + c) } to c.id
    }

    fun updateControl(p: Profile, pageIndex: Int, control: Control): Profile = updatePage(p, pageIndex) { pg ->
        pg.copy(controls = pg.controls.map { if (it.id == control.id) clamp(control, pg) else it })
    }

    fun move(p: Profile, pageIndex: Int, id: String, x: Int, y: Int): Profile = updatePage(p, pageIndex) { pg ->
        pg.copy(controls = pg.controls.map { if (it.id == id) clamp(it.copy(x = x, y = y), pg) else it })
    }

    fun resize(p: Profile, pageIndex: Int, id: String, w: Int, h: Int): Profile = updatePage(p, pageIndex) { pg ->
        pg.copy(controls = pg.controls.map {
            if (it.id == id) clamp(it.copy(w = w.coerceIn(1, pg.columns - it.x), h = h.coerceIn(1, pg.rows - it.y)), pg) else it
        })
    }

    fun delete(p: Profile, pageIndex: Int, id: String): Profile =
        updatePage(p, pageIndex) { pg -> pg.copy(controls = pg.controls.filterNot { it.id == id }) }

    fun addPage(p: Profile): Profile {
        val last = p.pages.lastOrNull()
        return p.copy(pages = p.pages + Page(
            id = newPageId(), name = "Page ${p.pages.size + 1}",
            columns = last?.columns ?: 8, rows = last?.rows ?: 4,
        ))
    }

    fun renamePage(p: Profile, index: Int, name: String): Profile =
        updatePage(p, index) { it.copy(name = name.ifBlank { it.name }) }

    /** The last page cannot be deleted. */
    fun deletePage(p: Profile, index: Int): Profile =
        if (p.pages.size <= 1) p else p.copy(pages = p.pages.filterIndexed { i, _ -> i != index })

    fun resizeGrid(p: Profile, index: Int, columns: Int, rows: Int): Profile = updatePage(p, index) { pg ->
        val n = pg.copy(columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS), rows = rows.coerceIn(MIN_ROWS, MAX_ROWS))
        n.copy(controls = n.controls.map { clamp(it, n) })
    }

    fun findControl(p: Profile, id: String): Pair<Int, Control>? {
        p.pages.forEachIndexed { i, pg -> Joystick.find(pg.controls, id)?.let { return i to it } }
        return null
    }
}
