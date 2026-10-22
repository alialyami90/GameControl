package com.gamecontroller.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Generated from the icon sources in the UI designer's icon sheet: 24dp grid, 2dp round stroke,
// shapes after the ISO 2575 tell-tales drivers already know. Edit the paths here directly.

data class IconEntry(val id: String, val name: String, val category: String, val vector: ImageVector)

private fun rig(name: String, vararg parts: Pair<String, Boolean>): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            for ((d, filled) in parts) {
                if (filled) {
                    addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
                } else {
                    addPath(
                        pathData = addPathNodes(d),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }
        .build()

private const val S = false
private const val F = true

/** Every icon a control can show, keyed by the string id stored in profiles. */
object IconLibrary {
    val categories: List<String> = listOf("Lights", "Driving", "Drivetrain", "Camera & tools", "General")

    val entries: List<IconEntry> = listOf(
        // Lights
        IconEntry("low_beam", "Low beam", "Lights", rig("low_beam", "M13 5.5c4 0 7 2.9 7 6.5s-3 6.5-7 6.5c-.8 0-1.5-.2-1.5-1V6.5c0-.8.7-1 1.5-1z" to S, "M8 7.5 3 9M8 11 3 12.5M8 14.5 3 16" to S)),
        IconEntry("high_beam", "High beam", "Lights", rig("high_beam", "M13 5.5c4 0 7 2.9 7 6.5s-3 6.5-7 6.5c-.8 0-1.5-.2-1.5-1V6.5c0-.8.7-1 1.5-1z" to S, "M8 7.5H3M8 12H3M8 16.5H3" to S)),
        IconEntry("fog_light", "Fog light", "Lights", rig("fog_light", "M13 5.5c4 0 7 2.9 7 6.5s-3 6.5-7 6.5c-.8 0-1.5-.2-1.5-1V6.5c0-.8.7-1 1.5-1z" to S, "M8.5 7.5 3 7.5M8.5 12H3M8.5 16.5H3" to S, "M6 4.5c-1 2.5 1 4.5 0 7.5s1 5 0 8" to S)),
        IconEntry("light_bar", "Light bar", "Lights", rig("light_bar", "M4 14.5h16a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-2a1 1 0 0 1 1-1z" to S, "M7 16.5h.01M12 16.5h.01M17 16.5h.01" to S, "M12 4v6M5.5 6l2 4M18.5 6l-2 4" to S)),
        IconEntry("hazard", "Hazard", "Lights", rig("hazard", "M12 3.5 21.5 20h-19z" to S, "M12 9.2l4.3 7.3H7.7z" to S)),
        IconEntry("signal_left", "Signal left", "Lights", rig("signal_left", "M10 5 3 12l7 7v-4h5.5a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2H10z" to S, "M21 9v6" to S)),
        IconEntry("signal_right", "Signal right", "Lights", rig("signal_right", "M14 5l7 7-7 7v-4H8.5a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2H14z" to S, "M3 9v6" to S)),
        IconEntry("interior_light", "Interior light", "Lights", rig("interior_light", "M6 15h12l-2-7H8z" to S, "M12 8V4" to S, "M8 18.5l-1 2M12 18.5v2.5M16 18.5l1 2" to S)),
        // Driving
        IconEntry("ignition", "Ignition", "Driving", rig("ignition", "M8 5.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9z" to S, "M12.5 10H21M18 10v3M21 10v2.5" to S, "M8 8.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z" to F)),
        IconEntry("starter", "Starter", "Driving", rig("starter", "M12 3.5v7" to S, "M7.2 6.3a7.5 7.5 0 1 0 9.6 0" to S)),
        IconEntry("horn", "Horn", "Driving", rig("horn", "M3 10v4h3l7 4.5v-13L6 10z" to S, "M16.5 9a4 4 0 0 1 0 6M19 6.5a7.5 7.5 0 0 1 0 11" to S)),
        IconEntry("cruise", "Cruise", "Driving", rig("cruise", "M4.5 17.5a8.5 8.5 0 1 1 15 0" to S, "M12 15l3.5-5" to S, "M12 14a1.4 1.4 0 1 0 0 2.8 1.4 1.4 0 0 0 0-2.8z" to F, "M7 8l1 1M12 5.5V7M17 8l-1 1" to S, "M8.5 20.5h7" to S)),
        IconEntry("parking_brake", "Parking brake", "Driving", rig("parking_brake", "M12 5a7 7 0 1 0 0 14 7 7 0 0 0 0-14z" to S, "M10 15.5v-7h2.5a2.2 2.2 0 0 1 0 4.4H10" to S, "M4.2 6.5a10 10 0 0 0 0 11M19.8 6.5a10 10 0 0 1 0 11" to S)),
        IconEntry("esc", "ESC", "Driving", rig("esc", "M5 13.5V11l1.6-3.6A2 2 0 0 1 8.4 6.2h7.2a2 2 0 0 1 1.8 1.2L19 11v2.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z" to S, "M5 11h14" to S, "M7.5 14.5v1.5M16.5 14.5v1.5" to S, "M5 20c1-1.5 2-1.5 3 0M10.5 20c1-1.5 2-1.5 3 0M16 20c1-1.5 2-1.5 3 0" to S)),
        IconEntry("esc_off", "ESC off", "Driving", rig("esc_off", "M5 13.5V11l1.6-3.6A2 2 0 0 1 8.4 6.2h7.2a2 2 0 0 1 1.8 1.2L19 11v2.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z" to S, "M5 20c1-1.5 2-1.5 3 0M10.5 20c1-1.5 2-1.5 3 0M16 20c1-1.5 2-1.5 3 0" to S, "M3 3l18 18" to S)),
        IconEntry("tcs", "Traction control", "Driving", rig("tcs", "M12 4.5a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15z" to S, "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" to S, "M12 4.5V9M12 15v4.5M4.5 12H9M15 12h4.5" to S, "M1.5 8.5c.8 2.3.8 4.7 0 7M22.5 8.5c-.8 2.3-.8 4.7 0 7" to S)),
        IconEntry("abs", "ABS", "Driving", rig("abs", "M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12z" to S, "M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z" to F, "M4.2 6.5a10 10 0 0 0 0 11M19.8 6.5a10 10 0 0 1 0 11" to S)),
        IconEntry("wiper", "Wiper", "Driving", rig("wiper", "M3 17a12 12 0 0 1 18 0" to S, "M12 18.5 6.5 9" to S, "M12 17.5a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 0 0 0-2.4z" to F)),
        IconEntry("steering", "Steering", "Driving", rig("steering", "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17z" to S, "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" to S, "M3.8 11 10 12M20.2 11 14 12M12 14v6.3" to S)),
        IconEntry("fuel", "Fuel", "Driving", rig("fuel", "M5 20.5V5a1.5 1.5 0 0 1 1.5-1.5h6A1.5 1.5 0 0 1 14 5v15.5M3.5 20.5h12" to S, "M7 7h5v4H7z" to S, "M14 9h2a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 0 3 0V8.5L18 6" to S)),
        IconEntry("engine", "Engine", "Driving", rig("engine", "M7 8h6l2 2h2.5v2.5H20V9.5h1.5v7H20v-3h-2.5V16H15l-2 2H8.5L6 15.5H4.5v-5H6V9z" to S, "M9 8V5.5M7 5.5h4" to S)),
        // Drivetrain
        IconEntry("gearbox", "Gearbox", "Drivetrain", rig("gearbox", "M5 5v14M12 5v14M19 5v7H5" to S, "M5 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM12 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM19 3.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM5 17.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3zM12 17.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z" to F)),
        IconEntry("gear_up", "Shift up", "Drivetrain", rig("gear_up", "M6 14l6-6 6 6" to S, "M6 19l6-6 6 6" to S)),
        IconEntry("gear_down", "Shift down", "Drivetrain", rig("gear_down", "M6 10l6 6 6-6" to S, "M6 5l6 6 6-6" to S)),
        IconEntry("diff_lock", "Diff lock", "Drivetrain", rig("diff_lock", "M3 6.5v11M5.5 6.5v11M18.5 6.5v11M21 6.5v11" to S, "M5.5 12H9M15 12h3.5" to S, "M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z" to S, "M9.5 9.5l5 5M14.5 9.5l-5 5" to S)),
        IconEntry("differential", "Differential", "Drivetrain", rig("differential", "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10z" to S, "M3 12h4M17 12h4M12 3v4" to S, "M3 8.5v7M21 8.5v7" to S)),
        IconEntry("awd", "4WD / AWD", "Drivetrain", rig("awd", "M8 4.5h8M8 19.5h8M12 4.5v15" to S, "M4.5 2.5h3v5h-3zM16.5 2.5h3v5h-3zM4.5 16.5h3v5h-3zM16.5 16.5h3v5h-3z" to F, "M7.5 5h1M15.5 5h1M7.5 19h1M15.5 19h1" to S)),
        IconEntry("low_range", "Low range", "Drivetrain", rig("low_range", "M4 17a8 8 0 0 1 16 0" to S, "M12 17 7.5 12" to S, "M12 16a1 1 0 1 0 0 2 1 1 0 0 0 0-2z" to F, "M4 20.5h16" to S)),
        IconEntry("suspension", "Suspension", "Drivetrain", rig("suspension", "M12 2.5v3M12 18.5v3" to S, "M8 5.5h8M8 18.5h8" to S, "M9 7.5l6 1.7-6 1.8 6 1.7-6 1.8 6 1.7" to S)),
        IconEntry("winch", "Winch", "Drivetrain", rig("winch", "M5 7.5h8v9H5z" to S, "M3 7.5v9M15 7.5v9" to S, "M5 10.5h8M5 13.5h8" to S, "M13 9.5h5.5v4.5" to S, "M18.5 14a2 2 0 1 0 2 2" to S)),
        IconEntry("trailer", "Trailer", "Drivetrain", rig("trailer", "M2.5 6.5h13v9h-13z" to S, "M15.5 13.5h6" to S, "M7 15.5a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" to S, "M21.5 12v3" to S)),
        IconEntry("jack", "Jack", "Drivetrain", rig("jack", "M4 20.5h16" to S, "M8 20.5 12 10l4 10.5" to S, "M9.5 7h5v3h-5z" to S, "M12 7V3.5M9.5 3.5h5" to S)),
        // Camera & tools
        IconEntry("camera", "Camera", "Camera & tools", rig("camera", "M3.5 7.5h11a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z" to S, "M15.5 11l5-3v8l-5-3" to S)),
        IconEntry("camera_cycle", "Change camera", "Camera & tools", rig("camera_cycle", "M3.5 9h9a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1z" to S, "M13.5 12l4-2.5v7l-4-2.5" to S, "M8 5.5a7 7 0 0 1 11.5 1.5" to S, "M19.8 3.8l-.3 3.2-3.1-.6" to S)),
        IconEntry("look_back", "Look back", "Camera & tools", rig("look_back", "M12 6c-5 0-8.5 4.5-9.5 6 1 1.5 4.5 6 9.5 6s8.5-4.5 9.5-6c-1-1.5-4.5-6-9.5-6z" to S, "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z" to F)),
        IconEntry("photo", "Photo", "Camera & tools", rig("photo", "M4 7.5h3l1.5-2h7l1.5 2h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-10a1 1 0 0 1 1-1z" to S, "M12 10a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z" to S)),
        IconEntry("map", "Map", "Camera & tools", rig("map", "M3.5 6.5 9 4.5l6 2 5.5-2v13l-5.5 2-6-2-5.5 2z" to S, "M9 4.5v13M15 6.5v13" to S)),
        IconEntry("recover", "Recover vehicle", "Camera & tools", rig("recover", "M4 13.5V12l1.4-3A1.6 1.6 0 0 1 6.8 8h6.4a1.6 1.6 0 0 1 1.4 1l1.4 3v1.5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z" to S, "M6.5 14.5v1.5M13.5 14.5v1.5" to S, "M18.5 4.5a5 5 0 0 1 1.5 8" to S, "M17.5 2.5l1 2.2-2.2 1" to S, "M3 19.5h17" to S)),
        IconEntry("reset", "Reset", "Camera & tools", rig("reset", "M5 12a7 7 0 1 0 2-4.9" to S, "M4.5 3.5v4.2h4.2" to S)),
        IconEntry("replay", "Replay", "Camera & tools", rig("replay", "M4 12a8 8 0 1 0 2.3-5.6" to S, "M5.5 3v4h4" to S, "M10.5 9v6l5-3z" to F)),
        IconEntry("slowmo", "Slow motion", "Camera & tools", rig("slowmo", "M7 3.5h10M7 20.5h10" to S, "M8 3.5c0 4 4 5 4 8.5s-4 4.5-4 8.5M16 3.5c0 4-4 5-4 8.5s4 4.5 4 8.5" to S, "M9.5 18.5h5L12 16z" to F)),
        IconEntry("pause", "Pause", "Camera & tools", rig("pause", "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z" to F)),
        // General
        IconEntry("gamepad", "Gamepad", "General", Icons.Rounded.SportsEsports),
        IconEntry("keyboard", "Keyboard", "General", Icons.Rounded.Keyboard),
        IconEntry("power", "Power", "General", Icons.Rounded.PowerSettingsNew),
        IconEntry("bolt", "Boost", "General", Icons.Rounded.Bolt),
        IconEntry("flag", "Flag", "General", Icons.Rounded.Flag),
        IconEntry("speed", "Speed", "General", Icons.Rounded.Speed),
        IconEntry("timer", "Timer", "General", Icons.Rounded.Timer),
        IconEntry("place", "Waypoint", "General", Icons.Rounded.Place),
        IconEntry("navigation", "Navigation", "General", Icons.Rounded.Navigation),
        IconEntry("chat", "Chat", "General", Icons.Rounded.Chat),
        IconEntry("mic", "Mic", "General", Icons.Rounded.Mic),
        IconEntry("mic_off", "Mic off", "General", Icons.Rounded.MicOff),
        IconEntry("volume_up", "Volume up", "General", Icons.Rounded.VolumeUp),
        IconEntry("volume_down", "Volume down", "General", Icons.Rounded.VolumeDown),
        IconEntry("music", "Music", "General", Icons.Rounded.MusicNote),
        IconEntry("screenshot", "Screenshot", "General", Icons.Rounded.Screenshot),
        IconEntry("menu", "Menu", "General", Icons.Rounded.Menu),
        IconEntry("settings", "Settings", "General", Icons.Rounded.Settings),
        IconEntry("home", "Home", "General", Icons.Rounded.Home),
        IconEntry("star", "Star", "General", Icons.Rounded.Star),
        IconEntry("build", "Repair", "General", Icons.Rounded.Build),
        IconEntry("play", "Play", "General", Icons.Rounded.PlayArrow),
        IconEntry("stop", "Stop", "General", Icons.Rounded.Stop),
        IconEntry("fast_forward", "Fast forward", "General", Icons.Rounded.FastForward),
        IconEntry("skip_next", "Next", "General", Icons.Rounded.SkipNext),
        IconEntry("skip_previous", "Previous", "General", Icons.Rounded.SkipPrevious),
        IconEntry("zoom_in", "Zoom in", "General", Icons.Rounded.ZoomIn),
        IconEntry("zoom_out", "Zoom out", "General", Icons.Rounded.ZoomOut),
        IconEntry("visibility", "View", "General", Icons.Rounded.Visibility),
        IconEntry("lightbulb", "Light", "General", Icons.Rounded.Lightbulb),
        IconEntry("inventory", "Inventory", "General", Icons.Rounded.Inventory2),
        IconEntry("target", "Target", "General", Icons.Rounded.GpsFixed),
        IconEntry("shield", "Shield", "General", Icons.Rounded.Shield),
        IconEntry("radio", "Radio", "General", Icons.Rounded.Radio),
        IconEntry("arrow_up", "Up", "General", Icons.Rounded.KeyboardArrowUp),
        IconEntry("arrow_down", "Down", "General", Icons.Rounded.KeyboardArrowDown),
        IconEntry("arrow_left", "Left", "General", Icons.Rounded.KeyboardArrowLeft),
        IconEntry("arrow_right", "Right", "General", Icons.Rounded.KeyboardArrowRight),
        IconEntry("plus", "Plus", "General", Icons.Rounded.Add),
        IconEntry("minus", "Minus", "General", Icons.Rounded.Remove),
    )

    private val byId: Map<String, IconEntry> = entries.associateBy { it.id }

    operator fun get(id: String?): ImageVector? = id?.let { byId[it]?.vector }

    fun entry(id: String?): IconEntry? = id?.let { byId[it] }

    fun inCategory(category: String): List<IconEntry> = entries.filter { it.category == category }

    fun search(query: String): List<IconEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries
        return entries.filter { q in it.name.lowercase() || q in it.id || q in it.category.lowercase() }
    }
}
