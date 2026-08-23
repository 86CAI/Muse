package com.caipan.music.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Muse 皮肤（方案 A：纯声明式资源包）。
 *
 * 皮肤 = zip 包（.museskin），仅声明参数与资源，不执行代码：
 *   skin.json     元信息 + 主题参数（必填）
 *   preview.png   皮肤预览图（可选）
 *   wallpaper.png 皮肤背景图（可选）
 *   icons/        图标包（v2 预留）
 *
 * skin.json schema:
 * {
 *   "id": "com.example.skin",
 *   "name": "皮肤名",
 *   "author": "作者",
 *   "version": "1.0.0",
 *   "description": "说明",
 *   "colors": { "primary": "#FA2D55", "background": "#09090B", ... },   // 深色模式
 *   "colorsLight": { ... },                                             // 浅色模式（可选，缺省回退 colors）
 *   "radii": { "compact": 12, "standard": 16, "card": 20, "floating": 28 },
 *   "blur": { "lyrics": 0, "background": 24 },
 *   "layout": {
 *     "showPrevNext": false,        // 播放器显示上一首/下一首按钮
 *     "showMiniArtwork": true,      // 迷你播放器显示封面
 *     "showMiniPlayer": true,       // 显示迷你播放器
 *     "miniPlayerStyle": "record",  // record=悬浮黑胶 | classic=传统底部长条
 *     "albumArtShape": "rounded",   // circle=圆形 | rounded=圆角方形 | square=方形
 *     "progressStyle": "thin",      // thin=细 | thick=粗 | none=隐藏进度条
 *     "showTimeLabels": true        // 播放器显示时间标签
 *   },
 *   "font": { "scale": 1.0 },       // 字号缩放 0.8~1.3
 *   "wallpaper": { "dim": 0.0 }     // 背景暗化 0~0.6（壁纸/封面背景上加暗色）
 * }
 */
data class MuseSkin(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val colors: SkinColors,
    val colorsLight: SkinColors?,
    val radii: SkinRadii,
    val blur: SkinBlur,
    val layout: SkinLayout,
    val font: SkinFont,
    val wallpaper: SkinWallpaper,
    val hasWallpaper: Boolean,
    val hasPreview: Boolean,
    val directory: String
) {
    companion object {
        /** 从已解压目录读取 skin.json，解析失败抛异常 */
        fun loadFromDir(dir: java.io.File): MuseSkin {
            val jsonFile = java.io.File(dir, "skin.json")
            if (!jsonFile.isFile) throw IllegalArgumentException("皮肤包缺少 skin.json")
            val json = JSONObject(jsonFile.readText())
            val id = json.optString("id").ifBlank { dir.name }
            val name = json.optString("name").ifBlank { id }
            val author = json.optString("author", "未知")
            val version = json.optString("version", "1.0.0")
            val description = json.optString("description", "")
            val colors = SkinColors.fromJson(json.optJSONObject("colors") ?: JSONObject())
            val colorsLight = json.optJSONObject("colorsLight")?.let { SkinColors.fromJson(it) }
            val radii = SkinRadii.fromJson(json.optJSONObject("radii") ?: JSONObject())
            val blur = SkinBlur.fromJson(json.optJSONObject("blur") ?: JSONObject())
            val layout = SkinLayout.fromJson(json.optJSONObject("layout") ?: JSONObject())
            val font = SkinFont.fromJson(json.optJSONObject("font") ?: JSONObject())
            val wallpaper = SkinWallpaper.fromJson(json.optJSONObject("wallpaper") ?: JSONObject())
            return MuseSkin(
                id = id,
                name = name,
                author = author,
                version = version,
                description = description,
                colors = colors,
                colorsLight = colorsLight,
                radii = radii,
                blur = blur,
                layout = layout,
                font = font,
                wallpaper = wallpaper,
                hasWallpaper = java.io.File(dir, "wallpaper.png").isFile,
                hasPreview = java.io.File(dir, "preview.png").isFile,
                directory = dir.absolutePath
            )
        }
    }
}

data class SkinColors(
    val primary: Color? = null,
    val onPrimary: Color? = null,
    val primaryContainer: Color? = null,
    val secondary: Color? = null,
    val background: Color? = null,
    val surface: Color? = null,
    val surfaceVariant: Color? = null,
    val onBackground: Color? = null,
    val onSurface: Color? = null,
    val onSurfaceVariant: Color? = null,
    val error: Color? = null,
    val outline: Color? = null,
    val scrim: Color? = null
) {
    companion object {
        fun fromJson(json: JSONObject): SkinColors = SkinColors(
            primary = parseColor(json.optString("primary")),
            onPrimary = parseColor(json.optString("onPrimary")),
            primaryContainer = parseColor(json.optString("primaryContainer")),
            secondary = parseColor(json.optString("secondary")),
            background = parseColor(json.optString("background")),
            surface = parseColor(json.optString("surface")),
            surfaceVariant = parseColor(json.optString("surfaceVariant")),
            onBackground = parseColor(json.optString("onBackground")),
            onSurface = parseColor(json.optString("onSurface")),
            onSurfaceVariant = parseColor(json.optString("onSurfaceVariant")),
            error = parseColor(json.optString("error")),
            outline = parseColor(json.optString("outline")),
            scrim = parseColor(json.optString("scrim"))
        )
    }
}

data class SkinRadii(
    val compact: Dp? = null,
    val standard: Dp? = null,
    val card: Dp? = null,
    val floating: Dp? = null
) {
    companion object {
        fun fromJson(json: JSONObject): SkinRadii = SkinRadii(
            compact = json.optDouble("compact", Double.NaN).takeIf { !it.isNaN() }?.dp,
            standard = json.optDouble("standard", Double.NaN).takeIf { !it.isNaN() }?.dp,
            card = json.optDouble("card", Double.NaN).takeIf { !it.isNaN() }?.dp,
            floating = json.optDouble("floating", Double.NaN).takeIf { !it.isNaN() }?.dp
        )
    }
}

data class SkinBlur(
    val lyrics: Float? = null,       // 0 清晰 ~ 30 重模糊（歌词背景，v2 完整接入）
    val background: Float? = null    // 播放器背景模糊强度
) {
    companion object {
        fun fromJson(json: JSONObject): SkinBlur = SkinBlur(
            lyrics = json.optDouble("lyrics", Double.NaN).takeIf { !it.isNaN() }?.toFloat(),
            background = json.optDouble("background", Double.NaN).takeIf { !it.isNaN() }?.toFloat()
        )
    }
}

/** 封面形状 */
enum class AlbumArtShape { CIRCLE, ROUNDED, SQUARE }

/** 迷你播放器样式 */
enum class MiniPlayerStyle { RECORD, CLASSIC }

/** 进度条样式 */
enum class ProgressStyle { THIN, THICK, NONE }

data class SkinLayout(
    val showPrevNext: Boolean? = null,      // 播放器显示上一首/下一首按钮（默认手势）
    val showMiniArtwork: Boolean? = null,   // 迷你播放器显示封面
    val showMiniPlayer: Boolean? = null,    // 显示迷你播放器（false 隐藏）
    val miniPlayerStyle: MiniPlayerStyle? = null, // record=悬浮黑胶 | classic=传统长条
    val albumArtShape: AlbumArtShape? = null,     // 播放器封面形状
    val progressStyle: ProgressStyle? = null,     // 进度条样式
    val showTimeLabels: Boolean? = null           // 播放器显示时间标签
) {
    companion object {
        fun fromJson(json: JSONObject): SkinLayout = SkinLayout(
            showPrevNext = json.optBoolean("showPrevNext", false).takeIf { json.has("showPrevNext") },
            showMiniArtwork = json.optBoolean("showMiniArtwork", true).takeIf { json.has("showMiniArtwork") },
            showMiniPlayer = json.optBoolean("showMiniPlayer", true).takeIf { json.has("showMiniPlayer") },
            miniPlayerStyle = runCatching {
                MiniPlayerStyle.valueOf(json.optString("miniPlayerStyle", "record").uppercase())
            }.getOrNull(),
            albumArtShape = runCatching {
                AlbumArtShape.valueOf(json.optString("albumArtShape", "rounded").uppercase())
            }.getOrNull(),
            progressStyle = runCatching {
                ProgressStyle.valueOf(json.optString("progressStyle", "thin").uppercase())
            }.getOrNull(),
            showTimeLabels = json.optBoolean("showTimeLabels", true).takeIf { json.has("showTimeLabels") }
        )
    }
}

data class SkinFont(
    val scale: Float? = null     // 0.8 ~ 1.3 字号缩放
) {
    companion object {
        fun fromJson(json: JSONObject): SkinFont = SkinFont(
            scale = json.optDouble("scale", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(0.8f, 1.3f)
        )
    }
}

data class SkinWallpaper(
    val dim: Float? = null       // 0 ~ 0.6 背景暗化强度
) {
    companion object {
        fun fromJson(json: JSONObject): SkinWallpaper = SkinWallpaper(
            dim = json.optDouble("dim", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(0f, 0.6f)
        )
    }
}

private fun parseColor(hex: String): Color? {
    if (hex.isBlank()) return null
    return try {
        val value = hex.removePrefix("#")
        when (value.length) {
            6 -> Color(0xFF000000 or value.toLong(16))
            8 -> Color(value.toLong(16))
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
