/*
 * 开源来源与许可证清单（应用内「关于 → 开源许可」的数据源）。
 *
 * 本文件与仓库根目录的 THIRD_PARTY_NOTICES.md 保持同步；许可证全文由
 * app/build.gradle.kts 的 bundleOpenSourceLicenses 任务打包到
 * assets/licenses/ 下，运行时按需读取。
 *
 * GPL-3.0 / AGPL-3.0 要求分发二进制时附带许可证副本与来源说明，
 * 这个页面即为该义务的应用内履行方式之一。
 */
package com.caipan.music.ui.components

import android.content.Context

/** 一条第三方来源记录。 */
data class OssEntry(
    /** 项目名。 */
    val name: String,
    /** 上游仓库地址；空串表示无公开仓库。 */
    val url: String,
    /** 许可证 SPDX 标识或说明。 */
    val license: String,
    /** assets/licenses/ 下的许可证全文文件名；null 表示本仓库未随附全文。 */
    val licenseAsset: String?,
    /** 中文说明：用到了什么。 */
    val usageZh: String,
    /** 英文说明。 */
    val usageEn: String,
    /** 该项目在 Muse 中的落地位置。 */
    val paths: List<String> = emptyList(),
    /** 需要用户注意的合规提示。 */
    val warningZh: String? = null,
    val warningEn: String? = null,
)

/** 分组。 */
data class OssSection(
    val titleZh: String,
    val titleEn: String,
    val entries: List<OssEntry>,
)

object OpenSourceRegistry {

    const val NOTICES_ASSET = "licenses/THIRD_PARTY_NOTICES.md"
    const val COPYRIGHT_ASSET = "licenses/COPYRIGHT.md"

    /** Muse 自身的许可证。AGPL-3.0 §5d 要求交互界面展示这段 Appropriate Legal Notice。 */
    val museSelf = OssEntry(
        name = "Muse",
        url = "https://github.com/86CAI/Muse",
        license = "AGPL-3.0-or-later",
        licenseAsset = "licenses/AGPL-3.0.txt",
        usageZh = "Muse 本身。Copyright (C) 2026 Cai & Caiyu。" +
            "本程序是自由软件，你可以在 AGPL-3.0 或更高版本的条款下重新分发和修改它。" +
            "本程序不提供任何担保。因包含 Symphony (AGPL-3.0) 与多个 GPL-3.0 项目的改编代码，" +
            "Muse 整体只能以 AGPL-3.0 分发；分发二进制时须一并提供对应源代码。",
        usageEn = "Muse itself. Copyright (C) 2026 Cai & Caiyu. This program is free software: " +
            "you may redistribute and modify it under the terms of the AGPL-3.0 or any later " +
            "version. It comes with ABSOLUTELY NO WARRANTY. Because it adapts code from Symphony " +
            "(AGPL-3.0) and several GPL-3.0 projects, the combined work can only be conveyed " +
            "under AGPL-3.0, and distributing binaries requires providing the corresponding source.",
    )

    val sections: List<OssSection> = listOf(
        OssSection(
            titleZh = "代码移植与改编",
            titleEn = "Ported & adapted code",
            entries = listOf(
                OssEntry(
                    name = "Mei_MeloX_Android (MeiloX)",
                    url = "https://github.com/NEORUAA/Mei_MeloX_Android",
                    license = "GPL-3.0",
                    licenseAsset = "licenses/GPL-3.0.txt",
                    usageZh = "MeloX 界面风格的全部来源：iOS 分组设置列表、液态玻璃底栏与胶囊标签、" +
                        "大标题首页与置顶列表页、Ios27 弹窗与控件、迷你/全屏播放器布局、SF Symbol 名称映射。",
                    usageEn = "The entire MeloX UI style: iOS grouped settings lists, the liquid-glass tab " +
                        "bar, large-title home and pinned list pages, Ios27 dialogs and controls, mini/full " +
                        "player layouts, SF Symbol name mapping.",
                    paths = listOf("ui/melox/**"),
                ),
                OssEntry(
                    name = "Symphony",
                    url = "https://github.com/zyrouge/symphony",
                    license = "AGPL-3.0-only",
                    licenseAsset = "licenses/AGPL-3.0.txt",
                    usageZh = "迷你播放条、歌曲列表项、媒体库首页结构，以及全屏播放器的控件层级。" +
                        "对应 commit dd04b872。",
                    usageEn = "Mini player bar, song list item, library home structure, and the full-player " +
                        "control hierarchy. Pinned at commit dd04b872.",
                    paths = listOf(
                        "ui/components/MiniPlayerBar.kt",
                        "ui/components/songlistitem.kt",
                        "ui/components/homescreen.kt",
                        "ui/components/PlayerScreen.kt",
                    ),
                ),
                OssEntry(
                    name = "AndroidLiquidGlass / Backdrop (Kyant0)",
                    url = "https://github.com/Kyant0/AndroidLiquidGlass",
                    license = "Apache-2.0",
                    licenseAsset = "licenses/APACHE-2.0.txt",
                    usageZh = "液态玻璃材质库（backdrop / shapes / capsule）依赖，以及其 catalog 示例中的" +
                        "拖拽回弹模型、按压径向高光、玻璃开关与滑块、底部标签栏分层方案。",
                    usageEn = "The liquid glass library itself (backdrop / shapes / capsule) plus catalog " +
                        "code: damped drag motion, interactive press highlight, glass toggle/slider, and the " +
                        "layered bottom-tabs approach.",
                    paths = listOf(
                        "ui/components/MuseGlass.kt",
                        "ui/components/MuseGlassControls.kt",
                        "ui/components/LiquidControlMotion.kt",
                        "ui/components/MuseLiquidHighlight.kt",
                        "ui/components/MuseLiquidActions.kt",
                        "ui/components/MuseLiquidBottomTabs.kt",
                        "ui/melox/MeloXLiquidInteraction.kt",
                    ),
                ),
                OssEntry(
                    name = "MeloX-Android (lladlam)",
                    url = "https://github.com/lladlam/MeloX-Android",
                    license = "GPL-3.0",
                    licenseAsset = "licenses/GPL-3.0.txt",
                    usageZh = "网易云首页 block 响应解析，以及 EAPI 传输层与会话存储的结构参考。",
                    usageEn = "NetEase homepage block parsing, plus structural reference for the EAPI " +
                        "transport layer and session store.",
                    paths = listOf(
                        "online/NeteaseHomeBlockParser.kt",
                        "online/NeteaseOnlineClient.kt",
                        "data/NeteaseSessionStore.kt",
                        "ui/components/NeteaseLoginScreen.kt",
                    ),
                ),
                OssEntry(
                    name = "Mineradio",
                    url = "https://github.com/XxHuberrr/Mineradio",
                    license = "GPL-3.0",
                    licenseAsset = "licenses/GPL-3.0.txt",
                    usageZh = "沉浸歌词舞台：desktop-lyrics.html 的调色板、字号字重、入场缓动、" +
                        "自适应字号与边缘遮罩，由 CSS/JS 改写为 Compose。",
                    usageEn = "The immersive lyrics stage: the desktop-lyrics.html palette, type scale, " +
                        "entrance easing, auto-fit sizing and edge mask, rewritten from CSS/JS into Compose.",
                    paths = listOf("ui/components/mineradiolyricsscreen.kt"),
                ),
                OssEntry(
                    name = "LX Music 音源脚本接口",
                    url = "https://github.com/lyswhut/lx-music-desktop",
                    license = "Apache-2.0",
                    licenseAsset = "licenses/APACHE-2.0.txt",
                    usageZh = "Muse 实现了与 LX Music 自定义音源脚本兼容的宿主环境（globalThis.lx），" +
                        "以便运行用户自行导入的脚本。未复制上游源码，也不内置任何音源。",
                    usageEn = "Muse implements a host environment compatible with LX Music custom-source " +
                        "scripts (globalThis.lx) so user-imported scripts can run. No upstream code was " +
                        "copied and no sources are bundled.",
                    paths = listOf("online/LxSourceHost.kt"),
                ),
                OssEntry(
                    name = "Lavender-z/demo 雨滴效果",
                    url = "",
                    license = "未确认 / Unconfirmed",
                    licenseAsset = null,
                    usageZh = "雨滴玻璃叠层的分层折射算法思路来自该项目的 WebGL/GLSL 实现，" +
                        "Muse 以 AGSL 重写并改为局部折射。",
                    usageEn = "The layered refraction approach behind the rain overlay comes from that " +
                        "project's WebGL/GLSL implementation; Muse rewrote it in AGSL with local-only " +
                        "refraction.",
                    paths = listOf("ui/components/RainDropsOverlay.kt"),
                    warningZh = "该上游项目的许可证尚未确认。若原作者有署名或移除要求，请通过仓库 Issues 联系。",
                    warningEn = "This upstream's license is unconfirmed. If the author wants different " +
                        "attribution or removal, please open an issue.",
                ),
            ),
        ),
        OssSection(
            titleZh = "图标与字体",
            titleEn = "Icons & fonts",
            entries = listOf(
                OssEntry(
                    name = "Lucide Icons",
                    url = "https://github.com/lucide-icons/lucide",
                    license = "ISC（部分 MIT / partly MIT）",
                    licenseAsset = "licenses/ISC-LUCIDE.txt",
                    usageZh = "界面中的 51 个线性图标，由 Lucide 的 SVG 转换为 Android 矢量图。" +
                        "文件名里的 apple 只表示其在 Muse 中的用途分组，并非来自 Apple。",
                    usageEn = "The 51 line icons in the UI, converted from Lucide SVGs to Android vector " +
                        "drawables. The \"apple\" in their file names only marks their role in Muse's " +
                        "Apple-style UI; they are not from Apple.",
                    paths = listOf("res/drawable/ic_apple_*.xml"),
                ),
                OssEntry(
                    name = "SF Pro / SF Symbols (Apple)",
                    url = "https://developer.apple.com/fonts/",
                    license = "专有 / Proprietary（非开源 / not open source）",
                    licenseAsset = null,
                    usageZh = "MeloX 风格界面的字体与图标字形。",
                    usageEn = "The typeface and icon glyphs used by the MeloX-style UI.",
                    paths = listOf("res/font/sf_pro.ttf", "ui/melox/MeloXSfSymbol.kt"),
                    warningZh = "Apple 的字体授权仅允许用于为 Apple 平台制作界面示意图，禁止嵌入软件产品分发；" +
                        "SF Symbols 同样不得在非 Apple 平台复制其字形。公开发布前应替换为可自由分发的字体与图标。",
                    warningEn = "Apple's font license only allows interface mock-ups for Apple platforms and " +
                        "forbids embedding the font in shipped software; SF Symbols glyphs may not be " +
                        "reproduced on non-Apple platforms either. Replace both before publishing.",
                ),
            ),
        ),
        OssSection(
            titleZh = "运行时依赖",
            titleEn = "Runtime dependencies",
            entries = listOf(
                OssEntry(
                    name = "AndroidX · Jetpack Compose · Material 3",
                    url = "https://android.googlesource.com/platform/frameworks/support/",
                    license = "Apache-2.0",
                    licenseAsset = "licenses/APACHE-2.0.txt",
                    usageZh = "界面框架、生命周期、媒体与调色板。",
                    usageEn = "UI toolkit, lifecycle, media and palette.",
                ),
                OssEntry(
                    name = "Kotlin · kotlinx.coroutines",
                    url = "https://github.com/JetBrains/kotlin",
                    license = "Apache-2.0",
                    licenseAsset = "licenses/APACHE-2.0.txt",
                    usageZh = "语言与协程运行时。",
                    usageEn = "Language and coroutines runtime.",
                ),
                OssEntry(
                    name = "OkHttp",
                    url = "https://github.com/square/okhttp",
                    license = "Apache-2.0",
                    licenseAsset = "licenses/APACHE-2.0.txt",
                    usageZh = "在线模式、WebDAV 与插件网络代理的 HTTP 客户端。",
                    usageEn = "HTTP client for online mode, WebDAV and the plugin network proxy.",
                ),
                OssEntry(
                    name = "Coil",
                    url = "https://github.com/coil-kt/coil",
                    license = "Apache-2.0",
                    licenseAsset = "licenses/APACHE-2.0.txt",
                    usageZh = "封面与图片加载。",
                    usageEn = "Artwork and image loading.",
                ),
                OssEntry(
                    name = "Javet (V8)",
                    url = "https://github.com/caoccao/Javet",
                    license = "Apache-2.0",
                    licenseAsset = "licenses/APACHE-2.0.txt",
                    usageZh = "运行用户导入的音源脚本的 V8 沙箱。",
                    usageEn = "The V8 sandbox that runs user-imported source scripts.",
                ),
                OssEntry(
                    name = "Rhino",
                    url = "https://github.com/mozilla/rhino",
                    license = "MPL-2.0",
                    licenseAsset = "licenses/MPL-2.0.txt",
                    usageZh = "Muse 插件的 JavaScript 解释器。",
                    usageEn = "The JavaScript interpreter for Muse plugins.",
                ),
                OssEntry(
                    name = "NanoHTTPD",
                    url = "https://github.com/NanoHttpd/nanohttpd",
                    license = "BSD-3-Clause",
                    licenseAsset = "licenses/BSD-3-CLAUSE-NANOHTTPD.txt",
                    usageZh = "Open API 服务与局域网遥控的内置 HTTP 服务器。",
                    usageEn = "The embedded HTTP server behind the Open API and LAN remote.",
                ),
            ),
        ),
    )

    /** 读取 assets 中的许可证全文；失败时返回 null 交由界面提示。 */
    fun readAsset(context: Context, assetPath: String): String? =
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()
}
