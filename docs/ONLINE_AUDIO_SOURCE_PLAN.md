# Muse 在线音源接入方案

更新日期：2026-08-02

## 结论

Muse 不应直接移植 `mineradio` 中的网易云、QQ、酷狗、汽水等非官方接口，也不应让插件向播放器任意注入 URL。当前最稳妥的路线是：

1. 先把播放内核迁移到 AndroidX Media3。
2. 用现有 WebDAV 的用户自有文件做远程直播放技术验证。
3. 以 OpenSubsonic/Navidrome 作为第一个完整在线音源。
4. 播客 RSS、用户添加的网络电台和有明确授权条款的开放曲库随后接入。
5. 原生链路稳定后，再设计受宿主管控的音源插件 v2。

这条路线能验证搜索、分页、远程队列、鉴权、缓存、临时 URL 和后台播放，同时避免把私有协议、会员绕过或易失效签名变成应用的核心依赖。

## 当前限制

### 曲目模型只表示本地 MediaStore

`model/Song.kt` 使用 `Long id`，`uri` 固定由 `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` 生成。远程曲目没有稳定的来源 ID、播放引用、封面 URL、请求头或 URL 过期时间，不能可靠地放进现有队列。

不要用负数 ID 或哈希值伪装远程 MediaStore 曲目。跨来源 ID 冲突、收藏恢复、缓存和统计都会因此变得不可控。

### 播放内核不适合正式流媒体

`player/MusicPlayer.kt` 当前使用 `android.media.MediaPlayer + MediaSessionCompat`，通知和状态同步均由应用手工维护。它可以做简单 URL 试播，但不适合作为正式在线架构：

- 不便于统一 HLS、DASH、HTTP 请求头和 OkHttp 数据源。
- 不便于处理临时签名 URL 过期后的重新解析。
- 缺少标准化缓存、预加载、错误分类和断网恢复。
- 播放不在 `MediaSessionService` 中，长时间后台播放链路不完整。
- 异步切歌需要额外防止旧请求迟到后覆盖新曲。

### 插件 v1 只能获取小型 JSON

`plugin/PluginNetworkProxy.kt` 有意限制为精确公网域名、HTTPS 443、GET/POST、少量请求头和最多 1 MiB UTF-8 响应；它不支持 Authorization、Cookie、重定向或二进制流。现有插件也不能创建远程队列项。

这些限制不应被整体取消。直接放宽会扩大 SSRF、凭据泄漏、恶意重定向和任意媒体注入风险。

## 目标模型

本地和远程曲目应共享展示模型，但保留不同的播放引用：

```kotlin
data class TrackKey(
    val sourceId: String,
    val itemId: String,
)

data class Track(
    val key: TrackKey,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long?,
    val artwork: ArtworkRef?,
    val playback: PlaybackRef,
)

sealed interface PlaybackRef {
    data class Local(val contentUri: Uri) : PlaybackRef
    data class Remote(val sourceId: String, val itemId: String) : PlaybackRef
}

data class ResolvedMedia(
    val uri: Uri,
    val mimeType: String?,
    val headers: Map<String, String>,
    val expiresAtEpochMs: Long?,
)
```

远程 URL 只作为短生命周期的 `ResolvedMedia` 存在，不作为曲目的永久 ID。播放前或 URL 即将过期时由来源适配器重新解析。

建议的来源边界：

```kotlin
interface MusicSource {
    val descriptor: SourceDescriptor
    suspend fun search(query: String, cursor: String?): TrackPage
    suspend fun resolve(key: TrackKey, quality: AudioQuality): ResolvedMedia
    suspend fun lyrics(key: TrackKey): List<LyricLine>?
}
```

专辑、歌单、收藏和账号能力通过显式 capability 声明，不为所有来源假设同一套功能。

## 播放层改造

采用同一版本的以下 Media3 组件：

- `media3-exoplayer`
- `media3-session`
- `media3-datasource-okhttp`
- 按来源实际需要增加 `media3-exoplayer-hls` 或 `media3-exoplayer-dash`

目标结构：

- `player/MusicPlaybackService.kt`：`MediaSessionService` 和 ExoPlayer 生命周期。
- `player/MediaItemMapper.kt`：`Track -> MediaItem`，宿主在这里附加受控请求头。
- `source/MusicSourceRegistry.kt`：根据 `TrackKey.sourceId` 找到来源并解析临时媒体。
- `data/OnlineMusicRepository.kt`：搜索、分页、元数据缓存和统一错误。
- `data/SourceAccountStore.kt`：用 Android Keystore 保护令牌和密码。
- `viewmodel/OnlineMusicViewModel.kt`：独立于已经较大的 `MusicViewModel`。

每次播放解析必须带递增 request token。用户切歌后，旧解析即使成功返回也不能恢复旧曲。这一点可以借鉴 Mineradio 的异步状态机思想，但不复制其平台私有实现。

## 来源优先级

| 来源 | 建议 | 原因 |
| --- | --- | --- |
| WebDAV 直播放 | 第一阶段 | 已有目录、下载和账号配置；内容归用户所有，改造范围最小 |
| OpenSubsonic / Navidrome | 首个正式来源 | 开放协议，具备搜索、专辑、歌单、封面和流媒体能力 |
| 播客 RSS | 第二阶段 | 标准协议，授权关系清楚，适合验证长音频与断点恢复 |
| 用户网络电台 URL | 第二阶段 | 实现简单，但需要处理 ICY 元数据、断线重连和明文局域网例外 |
| Jamendo 等开放曲库 | 条款复核后 | 必须核对当前 API、署名、地区和商业使用条款 |
| Spotify | 只接官方能力 | Web API 不提供可交给 Muse 的通用完整音频 URL；可评估 App Remote/外部播放器 |
| Apple Music | 独立评估 | 需要 MusicKit、开发者令牌、用户订阅及相应播放约束 |
| 网易云、QQ、酷狗、汽水私有接口 | 不纳入核心 | 私有签名/Cookie 易失效，存在平台条款、版权、会员和再分发风险 |

## 对 LX Music 音源索引的评估

2026-08-02 对用户提供的 [LX Music 音源索引](https://blog.umrs.cc/archives/lx-music-zui-xin-zui-quan-yin-yuan-chi-xu-geng-xin-zhong-geng-xin) 及其链接仓库进行了只读核实。

页面列出的 SixYin、Huibq、Flower、LX、ikun、Grass、JuheApi 和“SVIP 音源”不是独立、正式的音乐服务 API，而是 LX Music 自定义源 JavaScript。它们依赖 `globalThis.lx` 运行时，主要接收 LX 已有目录中的 `hash`、`songmid` 等平台字段，再向第三方聚合服务请求播放 URL。Muse 当前没有 LX 的目录模型或脚本宿主，因此不能把这些文件当成普通 HTTP 音源直接接入。

本次检查还发现：

- `pdone/lx-music-source` 仓库没有声明许可证，README 仅说明“内容源于网络”。没有明确许可时，不能复制、修改或随 APK 分发其中脚本。
- “SVIP 音源”所在的 `LuoXiaohei-2025/LX-music-collection` 同样没有许可证，并且已经归档。
- 七个 `pdone` 最新脚本中有四个使用动态 `Function(...)` 构造或高度压缩代码；SixYin 文件约 333 KiB、只有少量超长代码行并带明显混淆，无法达到生产依赖需要的可审计性。
- Huibq、ikun、JuheApi 等可读脚本仍依赖无正式服务契约的第三方聚合域名，包含共享 key、频率限制或由服务端下发二次请求规则。
- 页面推荐的 `latest.js` 会在同一 URL 下被覆盖更新；直接在线导入等同于没有版本锁定、签名或哈希校验的远程代码执行。GitHub 加速代理还增加了额外供应链节点。

因此 Muse 不会内置、自动下载或执行这些脚本，也不会把它们列为正式在线播放后端。它们可以用于理解 LX 的 `musicUrl` 适配流程，但不能替代 Media3、统一 `TrackKey` 和受控来源适配器。

若以后确实提供“用户自行导入 LX 源”的实验能力，必须作为独立的插件 v2 课题处理：固定版本与 SHA-256、首次和每次升级重新授权、声明并逐个批准网络域名、禁止读取 Cookie/账号令牌、限制 CPU/内存/响应大小、由宿主校验最终媒体 URL，并明确显示来源未经 Muse 审核。现有插件 v1 不具备这些边界，不能承载该功能。

## 分阶段实施

### 阶段 0：播放内核迁移

- 保持现有本地歌曲行为和系统控制不变。
- 用 Media3 重建播放队列、通知、重复、随机、seek 和音频会话。
- 为本地播放补回归测试，再开始任何在线 UI。

验收：本地曲库的切歌、后台、耳机按键、通知、横竖屏和进程恢复不回退。

### 阶段 1：WebDAV 远程直播放

- 在现有 WebDAV 浏览结果中增加“在线播放”。
- 使用临时远程队列，不写入 MediaStore，也不自动下载完整文件。
- 复用账号配置，但凭据只由原生数据层读取，不能进入普通插件配置。
- 对 Range、重定向、超时、弱网和服务器不支持 seek 的情况给出明确状态。

验收：用户自有 MP3/FLAC 可搜索或浏览后播放，切歌不会被迟到请求覆盖，退出页面后后台继续播放。

### 阶段 2：OpenSubsonic/Navidrome

- 增加服务器账号、连通性测试、搜索、专辑、歌单和封面。
- 支持服务端转码质量选择，并缓存元数据和小尺寸封面。
- 歌词优先使用来源返回结果，本地歌词管理器作为可选兜底。

### 阶段 3：开放内容与插件 v2

- 接入播客 RSS、网络电台或经过条款复核的开放曲库。
- 插件 v2 只暴露类型化能力，例如 `catalog.search`、`catalog.resolve`、`online.enqueue`。
- 宿主负责凭据、域名策略、重定向、URL 校验和 `MediaItem` 构造；插件不能读取账号明文或注入任意请求头。

## 安全、隐私与许可

- 公网媒体默认强制 HTTPS。当前全局明文放行应收紧，仅对用户明确配置的局域网/self-hosted 地址提供单独例外和警告。
- OAuth token、密码和长期 Cookie 存入 Keystore 支持的加密存储，不进入日志、崩溃报告或插件 SharedPreferences。
- 临时媒体 URL 不持久化；日志只记录来源、状态码和脱敏后的曲目 key。
- 跟随重定向时每一跳重新校验 scheme、目标主机和公网地址，防止 DNS rebinding/SSRF。
- 区分网络失败、未登录、无权益、地区限制、DRM、不支持格式和 URL 过期，不能把所有错误都表现为“播放失败”。
- `mineradio` 目录采用 GPL-3.0。可以参考状态机和交互思路，但复制实现会带来相应许可义务；平台私有接口、Cookie、签名和音频解密链路不应移植。

## 下一步建议

下一次实现应只做“阶段 0 + 一个 WebDAV 远程文件的受控播放验证”，不要同时加入多个商业平台。这个切片能先验证最关键的模型和播放内核，且失败时容易回退和定位。
