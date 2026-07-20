package com.caipan.music.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/** 完整 Muse 配置备份：SharedPreferences、插件包及应用生成的媒体文件。 */
class MuseBackupManager(context: Context) {
    private val app = context.applicationContext
    private val filesDir get() = app.filesDir
    private val prefsDir get() = File(app.applicationInfo.dataDir, "shared_prefs")

    fun exportTo(uri: Uri): Result<Unit> = runCatching {
        app.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                val manifest = """{"format":"muse-backup","version":1,"filesDir":"${filesDir.absolutePath.replace("\\", "\\\\")}"}"""
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                if (prefsDir.isDirectory) prefsDir.listFiles().orEmpty()
                    .filter { it.isFile && it.extension == "xml" }
                    .forEach { addFile(zip, it, "shared_prefs/${it.name}") }

                // filesDir 下的所有持久文件都纳入，避免新增模块或未来功能漏进备份；cacheDir 不属于持久数据。
                filesDir.listFiles().orEmpty()
                    .filter { it.name != "cache" && it.name != ".staging" }
                    .forEach { item ->
                        if (item.isFile) addFile(zip, item, "files/${item.name}")
                        else addTree(zip, item, "files/${item.name}")
                    }
            }
        } ?: error("无法写入备份位置")
    }

    fun importFrom(uri: Uri): Result<Unit> = runCatching {
        val staging = File(app.cacheDir, "restore-${System.currentTimeMillis()}").apply {
            deleteRecursively(); mkdirs()
        }
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.replace('\\', '/')
                        require(!entry.isDirectory && name.length <= 240 && !name.startsWith('/') &&
                            !name.split('/').any { it == ".." || it.isBlank() }) { "备份包含非法路径" }
                        val target = File(staging, name)
                        require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "备份路径越界" }
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        zip.closeEntry()
                    }
                }
            } ?: error("无法读取备份文件")

            val manifest = JSONObject(File(staging, "manifest.json").readText(Charsets.UTF_8))
            require(manifest.optString("format") == "muse-backup") { "不是有效的 Muse 备份" }
            val oldRoot = manifest.optString("filesDir").takeIf { it.isNotBlank() }
            File(staging, "shared_prefs").takeIf { it.isDirectory }?.listFiles().orEmpty()
                .filter { it.extension == "xml" }
                .forEach { source ->
                    val text = source.readText(Charsets.UTF_8).let { xml ->
                        if (oldRoot != null) xml.replace(oldRoot, filesDir.absolutePath) else xml
                    }
                    File(prefsDir, source.name).apply { parentFile?.mkdirs(); writeText(text, Charsets.UTF_8) }
                }
            val filesBackup = File(staging, "files")
            if (filesBackup.isDirectory) {
                filesBackup.listFiles().orEmpty().forEach { source ->
                    val target = File(filesDir, source.name)
                    if (source.isDirectory) {
                        target.deleteRecursively()
                        source.copyRecursively(target, overwrite = true)
                    } else {
                        target.parentFile?.mkdirs()
                        source.copyTo(target, overwrite = true)
                    }
                }
            }
        } finally { staging.deleteRecursively() }
    }

    private fun addTree(zip: ZipOutputStream, root: File, prefix: String) {
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(root).path.replace('\\', '/')
            addFile(zip, file, "$prefix/${relative}")
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
