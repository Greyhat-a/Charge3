package com.charge3d.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Simple persistent storage: current theme, saved presets and app settings. */
class Store(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("charge3d", Context.MODE_PRIVATE)

    var current: ThemeConfig
        get() = runCatching { ThemeConfig.fromJson(JSONObject(sp.getString("current", "")!!)) }
            .getOrDefault(ThemeConfig())
        set(v) { sp.edit().putString("current", v.toJson().toString()).apply() }

    /** Master switch: pop the charging screen up automatically when plugged in. */
    var autoPopup: Boolean
        get() = sp.getBoolean("autoPopup", true)
        set(v) { sp.edit().putBoolean("autoPopup", v).apply() }

    var quality: Quality
        get() = runCatching { Quality.valueOf(sp.getString("quality", "MEDIUM")!!) }
            .getOrDefault(Quality.MEDIUM)
        set(v) { sp.edit().putString("quality", v.name).apply() }

    /** Screen auto-closes this many seconds after full charge (0 = stay until unplugged). */
    var closeAfterFullSec: Int
        get() = sp.getInt("closeAfterFull", 30)
        set(v) { sp.edit().putInt("closeAfterFull", v).apply() }

    fun presets(): List<ThemeConfig> {
        val arr = runCatching { JSONArray(sp.getString("presets", "[]")) }.getOrDefault(JSONArray())
        return List(arr.length()) { ThemeConfig.fromJson(arr.getJSONObject(it)) }
    }

    /** Saves under [config].name, replacing any preset with the same name. */
    fun savePreset(config: ThemeConfig) {
        val all = presets().filter { it.name != config.name } + config
        sp.edit().putString("presets", JSONArray(all.map { it.toJson() }).toString()).apply()
    }

    fun deletePreset(name: String) {
        val all = presets().filter { it.name != name }
        sp.edit().putString("presets", JSONArray(all.map { it.toJson() }).toString()).apply()
    }
}
