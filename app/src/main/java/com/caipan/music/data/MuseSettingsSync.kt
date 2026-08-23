package com.caipan.music.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File

/**
 * Muse 设置同步：收集所有本地设置 → JSON，从 JSON 恢复 → 本地。
 * 覆盖范围：主题、语言、UI 风格、强调色、背景透明度、头像昵称、均衡器预设、皮肤。
 * 不包含壁纸/视频/头像文件本身（太大），只同步路径引用。
 */
class MuseSettingsSync(context: Context) {
    private val app = context.applicationContext

    /** 收集所有需要同步的设置，返回 JSON 字符串。 */
    fun collect(): String {
        val root = JSONObject()
        val musePrefs = app.getSharedPreferences("muse_prefs", 0)

        root.put("profile_name", musePrefs.getString("profile_name", "Muse 用户") ?: "Muse 用户")
        root.put("profile_avatar", musePrefs.getString("profile_avatar", null)?.let { it } ?: JSONObject.NULL)
        root.put("light_theme", musePrefs.getBoolean("light_theme", false))
        root.put("ui_style", musePrefs.getString("ui_style", "APPLE") ?: "APPLE")
        if (musePrefs.contains("accent_color")) {
            root.put("accent_color", musePrefs.getLong("accent_color", 0L))
        } else {
            root.put("accent_color", JSONObject.NULL)
        }
        root.put("player_bg_mode", musePrefs.getString("player_bg_mode", null)?.let { it } ?: JSONObject.NULL)
        root.put("wallpaper_path", musePrefs.getString("wallpaper_path", null)?.let { it } ?: JSONObject.NULL)
        root.put("video_path", musePrefs.getString("video_path", null)?.let { it } ?: JSONObject.NULL)
        root.put("ui_bg_opacity", musePrefs.getFloat("ui_bg_opacity", -1f).toDouble())
        root.put("app_language", musePrefs.getString("app_language", "zh") ?: "zh")
        // The mode is safe to sync; the NetEase session cookie is deliberately not.
        root.put("music_mode", OnlineMusicPreferences(app).musicMode.name)

        // 均衡器
        val eqPrefs = app.getSharedPreferences("muse_eq", 0)
        root.put("eq_presets", eqPrefs.getString("eq_presets", "") ?: "")
        root.put("eq_enabled", eqPrefs.getBoolean("eq_enabled", false))
        root.put("eq_preset_name", eqPrefs.getString("eq_preset_name", "") ?: "")

        // 皮肤
        val skinPrefs = app.getSharedPreferences("muse_skins", 0)
        root.put("active_skin", skinPrefs.getString("active_skin", null)?.let { it } ?: JSONObject.NULL)

        return root.toString(2)
    }

    /**
     * 从 JSON 恢复设置到本地。
     * @return 实际写入的 key 数量
     */
    fun restore(json: String): Int {
        val root = JSONObject(json)
        var count = 0

        val musePrefs = app.getSharedPreferences("muse_prefs", 0)
        val editor = musePrefs.edit()

        root.optString("profile_name", null)?.let { editor.putString("profile_name", it); count++ }
        if (!root.isNull("profile_avatar")) {
            root.optString("profile_avatar", null)?.let { editor.putString("profile_avatar", it); count++ }
        }
        if (root.has("light_theme")) { editor.putBoolean("light_theme", root.getBoolean("light_theme")); count++ }
        root.optString("ui_style", null)?.let { editor.putString("ui_style", it); count++ }
        if (!root.isNull("accent_color")) {
            editor.putLong("accent_color", root.getLong("accent_color")); count++
        }
        root.optString("player_bg_mode", null)?.let { editor.putString("player_bg_mode", it); count++ }
        if (!root.isNull("wallpaper_path")) {
            root.optString("wallpaper_path", null)?.let { editor.putString("wallpaper_path", it); count++ }
        }
        if (!root.isNull("video_path")) {
            root.optString("video_path", null)?.let { editor.putString("video_path", it); count++ }
        }
        if (root.has("ui_bg_opacity")) {
            editor.putFloat("ui_bg_opacity", root.getDouble("ui_bg_opacity").toFloat()); count++
        }
        root.optString("app_language", null)?.let { editor.putString("app_language", it); count++ }
        editor.apply()

        root.optString("music_mode", null)?.let {
            OnlineMusicPreferences(app).musicMode = MusicMode.fromName(it)
            count++
        }

        // 均衡器
        val eqPrefs = app.getSharedPreferences("muse_eq", 0)
        val eqEditor = eqPrefs.edit()
        root.optString("eq_presets", null)?.let { eqEditor.putString("eq_presets", it); count++ }
        if (root.has("eq_enabled")) { eqEditor.putBoolean("eq_enabled", root.getBoolean("eq_enabled")); count++ }
        root.optString("eq_preset_name", null)?.let { eqEditor.putString("eq_preset_name", it); count++ }
        eqEditor.apply()

        // 皮肤
        val skinPrefs = app.getSharedPreferences("muse_skins", 0)
        if (!root.isNull("active_skin")) {
            root.optString("active_skin", null)?.let {
                skinPrefs.edit().putString("active_skin", it).apply(); count++
            }
        }

        return count
    }
}
