package com.caipan.music.online

/** Normalizes the mixed http/https and protocol-relative URLs returned by NetEase. */
fun normalizeNeteaseImageUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://", ignoreCase = true) -> "https://${value.substring(7)}"
        value.startsWith("https://", ignoreCase = true) -> "https://${value.substring(8)}"
        else -> null
    }
}

/** NetEase supports an on-URL size suffix, keeping artwork requests modest. */
fun neteaseImageRequestUrl(raw: String?, size: Int): String? =
    normalizeNeteaseImageUrl(raw)?.let { url ->
        val bounded = size.coerceIn(64, 1_080)
        if (url.contains("?param=")) url else "$url?param=${bounded}y$bounded"
    }
