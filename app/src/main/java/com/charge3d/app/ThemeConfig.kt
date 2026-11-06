package com.charge3d.app

import org.json.JSONArray
import org.json.JSONObject

/** The 8 animation styles. [id] is passed to the shader as an int. */
enum class Style(val id: Int, val label: String) {
    ENERGY_CORE(0, "Energy Core"),
    PLASMA_RING(1, "Plasma Ring"),
    LIQUID_BATTERY(2, "Liquid Battery"),
    ELECTRIC_PULSE(3, "Electric Pulse"),
    HOLOGRAPHIC(4, "Holographic Battery"),
    GALAXY(5, "Galaxy Charge"),
    NEON_WAVE(6, "Neon Wave"),
    QUANTUM(7, "Quantum Energy");
}

/** What fills up as the phone charges. */
enum class FillShape(val id: Int, val label: String) {
    BATTERY(0, "Battery"),
    DRUM(1, "Drum"),
    CIRCLE(2, "Circle");
}

enum class Background(val id: Int, val label: String) {
    BLACK(0, "Black"),
    DEEP_GRADIENT(1, "Gradient"),
    STARS(2, "Stars"),
    GRID(3, "Grid");
}

enum class Quality(val label: String, val scale: Float, val fps: Int) {
    LOW("Battery saver", 0.5f, 24),
    MEDIUM("Balanced", 0.75f, 30),
    HIGH("High", 1.0f, 60);
}

/** Everything a user can customise. Stored as JSON so presets are easy to save. */
data class ThemeConfig(
    val name: String = "Default",
    val style: Style = Style.ENERGY_CORE,
    val shape: FillShape = FillShape.BATTERY,
    val colors: List<Int> = listOf(0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt()), // ARGB, 1..4
    val speed: Float = 1f,        // 0.2 .. 3
    val brightness: Float = 1f,   // 0.4 .. 1.6
    val glow: Float = 1f,         // 0 .. 2
    val particles: Float = 0.5f,  // 0 .. 1
    val size: Float = 1f,         // 0.6 .. 1.4
    val background: Background = Background.DEEP_GRADIENT,
    val sound: Boolean = false,
    val showClock: Boolean = true,
    val showTimeLeft: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("style", style.name); put("shape", shape.name)
        put("colors", JSONArray(colors)); put("speed", speed.toDouble())
        put("brightness", brightness.toDouble()); put("glow", glow.toDouble())
        put("particles", particles.toDouble()); put("size", size.toDouble())
        put("background", background.name); put("sound", sound)
        put("showClock", showClock); put("showTimeLeft", showTimeLeft)
    }

    companion object {
        private inline fun <reified E : Enum<E>> JSONObject.enumOr(k: String, d: E): E =
            runCatching { enumValueOf<E>(getString(k)) }.getOrDefault(d)

        fun fromJson(o: JSONObject): ThemeConfig {
            val d = ThemeConfig()
            val cols = o.optJSONArray("colors")
            val list = if (cols != null && cols.length() in 1..4)
                List(cols.length()) { cols.getInt(it) } else d.colors
            return ThemeConfig(
                name = o.optString("name", d.name),
                style = o.enumOr("style", d.style),
                shape = o.enumOr("shape", d.shape),
                colors = list,
                speed = o.optDouble("speed", d.speed.toDouble()).toFloat(),
                brightness = o.optDouble("brightness", d.brightness.toDouble()).toFloat(),
                glow = o.optDouble("glow", d.glow.toDouble()).toFloat(),
                particles = o.optDouble("particles", d.particles.toDouble()).toFloat(),
                size = o.optDouble("size", d.size.toDouble()).toFloat(),
                background = o.enumOr("background", d.background),
                sound = o.optBoolean("sound", d.sound),
                showClock = o.optBoolean("showClock", d.showClock),
                showTimeLeft = o.optBoolean("showTimeLeft", d.showTimeLeft),
            )
        }
    }
}
