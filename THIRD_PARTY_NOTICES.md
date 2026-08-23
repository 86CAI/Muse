# THIRD PARTY NOTICES / 第三方开源声明

Muse 本身以 **GNU Affero General Public License v3.0 或更高版本** 发布
（见根目录 `LICENSE` 与 `COPYRIGHT.md`）。

Muse 使用、移植或改编了以下开源项目的代码与素材。各项目版权归其原作者所有，并按其各自的许可证条款使用。
本文件同时作为应用内「设置 → 关于 → 开源许可」页面的数据来源。

Muse itself is licensed under the **GNU Affero General Public License v3.0 or later**
(see `LICENSE` and `COPYRIGHT.md`).

Muse incorporates, ports, or adapts code and assets from the open-source projects listed below.
Copyright remains with the respective authors; each item is used under its own license.

许可证全文位于 `licenses/` 目录，并随 APK 打包到 `assets/licenses/`，可在应用内查看。
Full license texts live in `licenses/` and are bundled into the APK at `assets/licenses/` for in-app display.

---

## 1. 代码移植 / Ported & adapted source code

### Mei_MeloX_Android (MeiloX)

- 仓库 / Repository: https://github.com/NEORUAA/Mei_MeloX_Android
  （现名 https://github.com/NEORUAA/MeiloX ，是 https://github.com/ljyh223/Mei 的 fork）
- 许可证 / License: GNU General Public License v3.0 — `licenses/GPL-3.0.txt`
- 上游自身的第三方声明 / Upstream notices: https://github.com/NEORUAA/MeiloX/blob/main/THIRD_PARTY_NOTICES
- Muse 中的位置 / Location in Muse: `app/src/main/java/com/caipan/music/ui/melox/**`（MeloX 界面风格整体）

移植内容：iOS 风格的分组设置列表、液态玻璃底栏与胶囊标签、大标题首页与置顶列表页、
Ios27 弹窗与控件、迷你播放器与全屏播放器布局、SF Symbol 名称映射、关于页结构。
每个文件头部注释均标注了对应的上游文件路径。业务逻辑、数据层与播放实现为 Muse 自有。

Mei_MeloX_Android 本身派生自 Apache-2.0 的 [Mei](https://github.com/ljyh223/Mei)，
并参考了 GPL-3.0 的 [MeloX (iOS)](https://github.com/youshen2/MeloX)。

### Symphony

- 仓库 / Repository: https://github.com/zyrouge/symphony
- Commit: `dd04b872b8b4e6dd56172c053a5776c4d56ad080`
- 许可证 / License: GNU Affero General Public License v3.0 only — `licenses/AGPL-3.0.txt`
- Muse 中的位置 / Location in Muse:
  - `ui/components/MiniPlayerBar.kt` ← `ui/components/NowPlayingBottomBar.kt`
  - `ui/components/songlistitem.kt` ← `ui/components/SongCard.kt`
  - `ui/components/homescreen.kt` ← `ui/view/Home.kt`、`ui/view/home/Songs.kt`、`ui/view/home/Playlists.kt`
  - `ui/components/PlayerScreen.kt` 的控件层级参考了 `ui/view/nowPlaying/BodyContent.kt`、`BottomBar.kt`

详见 `docs/OPEN_SOURCE_UI_PORT.md`。Muse 的领域模型与回调替换了 Symphony 的 service/navigation 层；
未复制其网络、账号、数据库与电台等业务代码。

### AndroidLiquidGlass / Backdrop (Kyant0)

- 仓库 / Repository: https://github.com/Kyant0/AndroidLiquidGlass
- 许可证 / License: Apache License 2.0 — `licenses/APACHE-2.0.txt`
- 二进制依赖 / Binary dependencies（`app/build.gradle.kts`）:
  `io.github.kyant0:backdrop`、`io.github.kyant0:shapes`、`io.github.kyant0:capsule`
- 源码改编 / Adapted catalog source:
  - `ui/components/LiquidControlMotion.kt` ← `DampedDragAnimation.kt`、`DragGestureInspector.kt`
  - `ui/components/MuseLiquidHighlight.kt` ← `InteractiveHighlight.kt`
  - `ui/components/MuseLiquidActions.kt` ← `InteractiveHighlight.kt`（按压高光）
  - `ui/components/MuseLiquidBottomTabs.kt` ← `LiquidBottomTabs.kt`、`LiquidBottomTab.kt`
  - `ui/components/MuseGlassControls.kt` ← `LiquidToggle.kt`、`LiquidSlider.kt`
  - `ui/melox/MeloXLiquidInteraction.kt` 中的 `DragGestureInspector`
- 说明：Muse 将上游的 `RuntimeShader` 高光替换为 `Brush.radialGradient` 以兼容更低版本。

### MeloX-Android (lladlam)

- 仓库 / Repository: https://github.com/lladlam/MeloX-Android
- 许可证 / License: GNU General Public License v3.0 — `licenses/GPL-3.0.txt`
- Muse 中的位置 / Location in Muse:
  - `online/NeteaseHomeBlockParser.kt` ← `core/library/NeteaseHomeBlockParser.kt`（首页 block 解析，近似逐行移植）
  - `online/NeteaseOnlineClient.kt` 的 EAPI 传输层参考了 `core/network/NeteaseAuthenticatedEapi.kt`
  - `data/NeteaseSessionStore.kt`、`ui/components/NeteaseLoginScreen.kt` 的结构参考了同名上游文件
- 说明：网易云 EAPI 的密钥与摘要格式是公开的协议常量，同时存在于多个开源项目中。

### Mineradio

- 仓库 / Repository: https://github.com/XxHuberrr/Mineradio
- 许可证 / License: GNU General Public License v3.0 — `licenses/GPL-3.0.txt`
- Muse 中的位置 / Location in Muse: `ui/components/mineradiolyricsscreen.kt`
- 移植内容：`public/desktop-lyrics.html` 的歌词舞台配色（`--lyric-primary` / `--lyric-highlight` /
  `--lyric-glow` / `--lyric-feather`）、字号与字重、入场缓动
  `cubic-bezier(.16,.84,.32,1.02) 820ms`、`fitLyricText()` 自适应与遮罩边缘逻辑，
  由 CSS/JS 改写为 Compose 实现。

### LX Music 音源脚本接口 / LX Music custom-source API

- 仓库 / Repository: https://github.com/lyswhut/lx-music-desktop
- 许可证 / License: Apache License 2.0（上游主体）；音源脚本接口本身为事实标准
- Muse 中的位置 / Location in Muse: `online/LxSourceHost.kt`
- 说明：Muse 实现的是一个与 LX Music 自定义音源脚本 API 兼容的**宿主环境**
  （`globalThis.lx` 的 `EVENT_NAMES` / `on` / `send` / `request` / `env` / `utils.crypto` /
  `utils.buffer` 等），运行在受限的 Javet V8 沙箱中。未复制 LX Music 的源码。
  Muse 不内置、不分发任何音源脚本，脚本由用户自行导入。

### Lavender-z 雨滴效果 / rain effect

- Muse 中的位置 / Location in Muse: `ui/components/RainDropsOverlay.kt`
- 说明：AGSL 雨滴折射的分层算法思路来自 `Lavender-z/demo` 的 WebGL/GLSL 实现（Curtains.js 驱动），
  Muse 改为局部水滴折射、不做全屏模糊，并以 AGSL 重写。
  **该上游的许可证尚未确认**；若原作者提出要求，Muse 将补充署名或移除该实现。

## 2. 图标与字体 / Icons & fonts

### Lucide Icons

- 仓库 / Repository: https://github.com/lucide-icons/lucide
- 许可证 / License: ISC（部分派生自 Feather，MIT）— `licenses/ISC-LUCIDE.txt`
- Muse 中的位置 / Location in Muse: `app/src/main/res/drawable/ic_apple_*.xml`（51 个矢量图标）
- 说明：这些文件由 Lucide 的 SVG 转换为 Android VectorDrawable，部分做了圆形路径改写与微调。
  文件名中的 `apple` 仅表示其在 Muse 中的用途分组（Apple 风格界面），并非来自 Apple。

### SF Pro / SF Symbols (Apple)

- 来源 / Source: Apple Inc.
- 许可证 / License: **专有 / Proprietary** — Apple Font License，非开源
- Muse 中的位置 / Location in Muse:
  `app/src/main/res/font/sf_pro.ttf`、`ui/melox/MeloXGlassTokens.kt`、`ui/melox/MeloXSfSymbol.kt`
- **合规提示 / Compliance warning**：Apple 的字体授权仅允许将该字体用于为 Apple 平台
  （iOS / iPadOS / macOS / tvOS）制作界面示意图，**明确禁止嵌入到软件产品中分发**；
  SF Symbols 亦不得在非 Apple 平台复制其形状或字形。
  公开发布 Muse 构建产物前，应将该字体替换为可自由分发的替代字体
  （如 Inter、Roboto Flex），并将 SF Symbols 字形替换为 Lucide 或自绘图标。

## 3. 运行时依赖 / Runtime dependencies

| 组件 / Component | 许可证 / License | 全文 / Text |
| --- | --- | --- |
| AndroidX / Jetpack Compose / Material 3 (Google) | Apache-2.0 | `licenses/APACHE-2.0.txt` |
| Kotlin, kotlinx-coroutines (JetBrains) | Apache-2.0 | `licenses/APACHE-2.0.txt` |
| OkHttp (Square) | Apache-2.0 | `licenses/APACHE-2.0.txt` |
| Coil (coil-kt) | Apache-2.0 | `licenses/APACHE-2.0.txt` |
| Javet (caoccao) | Apache-2.0 | `licenses/APACHE-2.0.txt` |
| Kyant0 backdrop / shapes / capsule | Apache-2.0 | `licenses/APACHE-2.0.txt` |
| Rhino (Mozilla) | MPL-2.0 | `licenses/MPL-2.0.txt` |
| NanoHTTPD | BSD-3-Clause | `licenses/BSD-3-CLAUSE-NANOHTTPD.txt` |
| org.json | Public Domain / JSON License | — |

## 4. 仅作参考、未移植代码 / Referenced but not ported

以下项目存在于开发工作区中，仅用于研究与对照，**没有代码进入 Muse 的 APK**：

- [Webamp](https://github.com/captbaritone/webamp) — MIT
- [wmp9clone](https://github.com/) — GPL-2.0
- y2k-player（本仓库内的桌面实验工程，MIT）

Muse 的皮肤系统（`skin/MuseSkin.kt`、`skin/SkinManager.kt`）是自有的声明式 JSON + ZIP 方案，
不包含 Winamp `.wsz` / WMP 皮肤格式的任何解析代码或素材。

## 5. Copyleft 义务 / Copyleft obligations

Muse 的构建产物包含来自 AGPL-3.0（Symphony）与 GPL-3.0（Mei_MeloX_Android、MeloX-Android、
Mineradio）项目的改编代码。AGPL-3.0 是其中最严格的条款，且其第 13 节允许合并 GPL-3.0 代码，
因此 **Muse 整体以 AGPL-3.0-or-later 分发**（见 `LICENSE`、`COPYRIGHT.md`）。义务包括：

- 分发 Muse 的二进制包时，必须同时提供对应的完整源代码，或提供获取源代码的书面说明（§6）；
- 网络交互条款：若你修改 Muse 并让用户通过网络与其交互（Muse 内置 Open API 与局域网遥控），
  必须向这些用户提供你修改版本的源代码（§13）；
- 修改版本须注明已被修改及修改日期（§5a）；
- 交互界面须展示 Appropriate Legal Notices —— 即应用内「关于 → 开源许可」页面，不得移除（§5d）；
- 不得附加与 GPL/AGPL 冲突的额外限制；
- 许可证副本必须随分发一并提供（见 `licenses/`，已打包进 `assets/licenses/`）。

不受 AGPL 覆盖的部分：Apache-2.0 / MPL-2.0 / BSD-3-Clause / ISC 的第三方库按其各自条款授权；
Apple SF Pro 字体与 SF Symbols 字形为专有授权；用户导入的音源脚本、皮肤与插件版权归其作者。

## 6. 免责声明 / Disclaimer

Muse 不是任何音乐平台的官方客户端，也不隶属于网易云音乐、QQ 音乐、酷我、酷狗或 Apple。
Muse 自身不提供音乐资源；在线内容来自用户自行配置的账号或音源脚本，版权归各权利人所有。

如发现署名遗漏、许可证标注错误或侵权内容，请通过仓库 Issues 反馈，我们会尽快更正或移除。
