# Muse

Muse 是一款面向个人音乐库的 Android 本地音乐播放器，使用 Kotlin、Jetpack Compose 和 Material 3 构建。

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

```powershell
.\gradlew.bat assembleDebug --no-daemon --max-workers=1
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。发布构建不包含仓库中的签名密钥；请在本地按 Android 官方方式配置自己的签名。

## 开源来源

项目包含 Symphony UI 的移植与适配内容，具体来源、Commit SHA、许可证和移植说明见：

- `docs/OPEN_SOURCE_UI_PORT.md`
- `licenses/SYMPHONY-AGPL-3.0.txt`

如再分发或修改相关组件，请遵守对应许可证。

## 隐私

Muse 的核心播放与数据管理默认在本地完成。WebDAV、局域网遥控和插件网络能力仅在用户主动配置或使用时启用。

## 许可证

仓库中不同部分可能适用不同许可证；请以各目录说明和第三方许可证文件为准。
