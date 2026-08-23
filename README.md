# Muse

Muse 是一款面向个人音乐库的 Android 本地音乐播放器，使用 Kotlin、Jetpack Compose 和 Material 3 构建。

以 [AGPL-3.0-or-later](LICENSE) 发布。

## 功能

- 扫描并播放本地音乐
- 系统媒体控制与通知栏播放
- 歌单、歌词、均衡器和播放模式
- WebDAV 导入
- 局域网遥控
- 可扩展 Muse Plugin 示例
- 普通主题与 Monet/动态取色主题

## 构建

环境要求：Android Studio、JDK 17、Android SDK 36。

### 首次构建前

`app/src/main/res/font/sf_pro.ttf` **未纳入版本库** —— 该字体是 Apple SF Pro，属专有授权，
禁止再分发（详见 `THIRD_PARTY_NOTICES.md` 第 2 节）。构建前请自行放入一份，任选其一：

- 从 [Apple Developer](https://developer.apple.com/fonts/) 获取 SF Pro，重命名为
  `sf_pro.ttf`（仅限本地开发，Apple 条款不允许随构建产物分发）
- 放入任意可自由分发的字体并命名为 `sf_pro.ttf`，推荐 [Inter](https://rsms.me/inter/)
  或 Roboto Flex —— **公开发布的构建应采用此方案**

缺失时 Gradle 会在 `preBuild` 阶段给出提示。

```powershell
.\gradlew.bat assembleDebug --no-daemon --max-workers=1
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。发布构建不包含仓库中的签名密钥；请在本地按 Android 官方方式配置自己的签名。

## 开源来源

Muse 的界面大量移植、改编自开源项目。完整的来源清单、许可证与移植说明见：

- `THIRD_PARTY_NOTICES.md` —— 总清单（每个项目用到了什么、落在哪些文件）
- `licenses/` —— 各许可证全文（GPL-3.0、AGPL-3.0、Apache-2.0、MPL-2.0、BSD-3-Clause、ISC）
- `docs/OPEN_SOURCE_UI_PORT.md` —— Symphony 移植的详细记录

应用内也可查看：**设置 → 关于 → 开源许可**，可直接阅读随 APK 分发的许可证全文。

主要上游：

| 项目 | 许可证 | 用途 |
| --- | --- | --- |
| [Mei_MeloX_Android](https://github.com/NEORUAA/Mei_MeloX_Android) | GPL-3.0 | MeloX 界面风格（`ui/melox/**`） |
| [Symphony](https://github.com/zyrouge/symphony) | AGPL-3.0-only | 迷你播放条、歌曲行、媒体库首页 |
| [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Apache-2.0 | 液态玻璃材质与控件 |
| [MeloX-Android](https://github.com/lladlam/MeloX-Android) | GPL-3.0 | 网易云首页解析与 EAPI 传输 |
| [Mineradio](https://github.com/XxHuberrr/Mineradio) | GPL-3.0 | 沉浸歌词舞台 |
| [Lucide](https://github.com/lucide-icons/lucide) | ISC | 界面图标 |

## 许可证与再分发

Muse 的构建产物包含 AGPL-3.0（Symphony）与 GPL-3.0（Mei_MeloX_Android、MeloX-Android、
Mineradio）的改编代码。**分发 Muse 的二进制包时，必须同时提供对应的完整源代码**，
并随附上述许可证副本。详见 `THIRD_PARTY_NOTICES.md` 第 5 节。

> 已知合规问题：`app/src/main/res/font/sf_pro.ttf` 是 Apple 的 SF Pro，其授权禁止嵌入软件产品
> 分发（SF Symbols 字形同理）。公开发布构建产物前需替换为可自由分发的字体与图标。


## 隐私

Muse 的核心播放与数据管理默认在本地完成。WebDAV、局域网遥控和插件网络能力仅在用户主动配置或使用时启用。

## 许可证

Muse 以 **GNU AGPL-3.0 或更高版本** 发布 —— 见 [`LICENSE`](LICENSE) 与
[`COPYRIGHT.md`](COPYRIGHT.md)。

之所以是 AGPL：Muse 移植了 Symphony（AGPL-3.0-only）以及三个 GPL-3.0 项目的代码。
AGPL-3.0 是其中最严格的条款，其第 13 节允许把 GPL-3.0 代码合并进 AGPL 作品，
所以整体只能以 AGPL-3.0 分发；选 MIT / Apache-2.0 会违反上游条款。

分发时的主要义务：

- 提供与二进制对应的完整源代码，或有效的书面获取方式（§6）
- 若修改 Muse 并让用户经网络与其交互（Open API / 局域网遥控），须向这些用户提供源代码（§13）
- 标注已修改及修改日期（§5a）
- 保留应用内「关于 → 开源许可」页面（§5d 的 Appropriate Legal Notices）
- 随分发附带 `licenses/` 下的许可证副本

第三方库（Apache-2.0 / MPL-2.0 / BSD-3-Clause / ISC）、Apple 字体与用户导入的脚本/皮肤
不受 AGPL 覆盖，按各自条款授权。
