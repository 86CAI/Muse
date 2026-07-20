package com.caipan.music.player

enum class PlayerBgMode {
    ALBUM_EXTEND,   // 专辑封面模糊延伸
    DYNAMIC_COLOR,  // 纯色动态律动填充
    CUSTOM;         // 自定义背景图

    companion object {
        fun fromName(name: String?): PlayerBgMode = when (name) {
            "DYNAMIC_COLOR" -> DYNAMIC_COLOR
            "CUSTOM" -> CUSTOM
            else -> ALBUM_EXTEND
        }
    }
}
