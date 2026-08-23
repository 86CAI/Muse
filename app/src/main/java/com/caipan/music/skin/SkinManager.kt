package com.caipan.music.skin

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 皮肤管理器：负责皮肤包导入（zip 解压）、列表、切换、删除。
 * 当前激活皮肤用 Compose state 持有，主题层观察后热切换。
 *
 * 存储位置：filesDir/skins/<skinId>/
 * 激活记录：SharedPreferences "muse_skins" -> "active_skin"
 */
class SkinManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("muse_skins", Context.MODE_PRIVATE)
    private val skinsDir = File(context.filesDir, "skins").apply { mkdirs() }

    /** 激活皮肤（null = 使用内置默认外观），Compose 可观察 */
    var activeSkinId by mutableStateOf(prefs.getString("active_skin", null))
        private set

    /** 已安装皮肤列表 */
    val skins: List<MuseSkin>
        get() = skinsDir.listFiles()?.filter { it.isDirectory }
            ?.mapNotNull { dir -> runCatching { MuseSkin.loadFromDir(dir) }.getOrNull() }
            ?.sortedBy { it.name } ?: emptyList()

    /** 当前激活皮肤对象 */
    fun activeSkin(): MuseSkin? {
        val id = activeSkinId ?: return null
        return skins.firstOrNull { it.id == id }
    }

    /** 导入皮肤包（zip/SAF uri），返回皮肤 */
    fun import(uri: Uri): Result<MuseSkin> = runCatching {
        val zip = context.contentResolver.openInputStream(uri) ?: error("无法读取皮肤包")
        zip.use { input ->
            val tempDir = File(context.cacheDir, "skin_import_${System.currentTimeMillis()}").apply { mkdirs() }
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                var foundJson = false
                while (entry != null) {
                    val name = entry.name.removePrefix("skin/").removePrefix("/")
                    if (entry.isDirectory) {
                        entry = zis.nextEntry
                        continue
                    }
                    if (name == "skin.json") foundJson = true
                    val outFile = File(tempDir, name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                    entry = zis.nextEntry
                }
                if (!foundJson) error("皮肤包缺少 skin.json")
            }
            // 校验并读取
            val skin = MuseSkin.loadFromDir(tempDir)
            val target = File(skinsDir, sanitizeId(skin.id))
            if (target.exists()) target.deleteRecursively()
            tempDir.copyRecursively(target, overwrite = true)
            tempDir.deleteRecursively()
            skin.copy(directory = target.absolutePath)
        }
    }

    /** 激活皮肤 */
    fun apply(id: String) {
        if (skins.any { it.id == id }) {
            activeSkinId = id
            prefs.edit().putString("active_skin", id).apply()
        }
    }

    /** 恢复默认（无皮肤） */
    fun clear() {
        activeSkinId = null
        prefs.edit().remove("active_skin").apply()
    }

    /** 删除皮肤（激活中的自动回退默认） */
    fun delete(id: String) {
        if (activeSkinId == id) clear()
        File(skinsDir, sanitizeId(id)).deleteRecursively()
    }

    private fun sanitizeId(id: String): String =
        id.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
}
