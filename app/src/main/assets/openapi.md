# Muse 开放 API 开发文档

> 版本：v2.0 | 更新日期：2026-08-13
> 用途：供 MChat 等第三方 App 读取 Muse 播放状态（正在播放歌曲、听歌统计、曲库数量等信息）并控制播放

---

## 1. 概述

Muse 内置一个**常驻 HTTP API**，应用启动即自动监听，第三方 App（如 MChat 类应用）可通过局域网 HTTP 请求读取当前播放状态并控制播放。

**特点：**

- ✅ **常驻**：Muse 启动即监听，无需额外开启局域网遥控
- ✅ **读取**：查询播放状态、听歌总时间、曲库数量
- ✅ **控制**：播放 / 暂停 / 切歌 / 跳转 / 随机 / 循环等控制能力
- ✅ **匿名**：局域网内无需 token，即插即用
- ✅ **跨域**：响应带 `Access-Control-Allow-Origin: *`，网页工具可直接调试

---

## 2. 基础信息

| 项目 | 值 |
|---|---|
| 监听端口 | `24880` |
| 协议 | HTTP（局域网） |
| 读取请求方式 | `GET` |
| 控制请求方式 | `POST`（JSON body） |
| 响应格式 | JSON，`application/json; charset=utf-8` |
| 基础路径 | `http://<设备IP>:24880` |

> 设备 IP 可在 Muse 的局域网遥控页面查看，或用 `adb reverse tcp:24880 tcp:24880` 在本机调试。

---

## 3. 端点一览

**读取端点（GET）：**

| 端点 | 说明 | 认证 |
|---|---|---|
| `GET /api/info` | 应用与 API 信息 | 无 |
| `GET /api/health` | 健康检查 | 无 |
| `GET /api/now-playing` | **正在播放完整信息（核心）** | 无 |
| `GET /api/state` | 简版播放状态 | 无 |
| `GET /api/stats` | **听歌统计（听歌总时间、曲库数量）** | 无 |
| `GET /api/artwork` | 专辑封面图片（`?albumId=`） | 无 |
| `GET /docs` | 开发文档（Markdown） | 无 |

**控制端点（POST，匿名）：**

| 端点 | 说明 | body |
|---|---|---|
| `POST /api/play` | 播放 | 无 |
| `POST /api/pause` | 暂停 | 无 |
| `POST /api/toggle` | 播放 / 暂停切换 | 无 |
| `POST /api/next` | 下一首 | 无 |
| `POST /api/previous` | 上一首 | 无 |
| `POST /api/seek` | 跳转到指定进度 | `{"positionMs": 120000}` |
| `POST /api/shuffle` | 设置随机播放 | `{"enabled": true}` |
| `POST /api/repeat` | 设置循环模式 | `{"mode": "ALL"}` |

---

## 4. 读取端点详解

### 4.1 GET /api/info

返回 Muse 应用与 API 版本信息，以及当前登录的 MChat 账户 UID（未登录时为 `null`）。

```bash
curl http://192.168.1.100:24880/api/info
```

```json
{
  "code": 200,
  "data": {
    "api": "muse-open-api",
    "apiVersion": 2,
    "app": { "name": "Muse", "version": "2.804" },
    "uid": "10086",
    "documentation": "/docs"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `uid` | string \| null | 当前登录的 MChat 账户 UID；未登录为 `null` |

### 4.2 GET /api/health

健康检查，判断 API 是否存活。

```bash
curl http://192.168.1.100:24880/api/health
```

```json
{
  "code": 200,
  "data": { "status": "ok", "uptimeMs": 123456 }
}
```

### 4.3 GET /api/now-playing ⭐ 核心端点

返回当前正在播放的完整信息，包括歌曲、歌手、专辑、封面、进度、播放状态。

```bash
curl http://192.168.1.100:24880/api/now-playing
```

**正在播放（有歌）：**

```json
{
  "code": 200,
  "data": {
    "isPlaying": true,
    "isLoading": false,
    "song": {
      "id": "123456789",
      "title": "夜空中最亮的星",
      "artist": "逃跑计划",
      "album": "世界",
      "durationMs": 268000,
      "artworkUrl": "http://example.com/covers/xxx.jpg",
      "isOnline": false,
      "source": "local"
    },
    "progressMs": 120000,
    "durationMs": 268000,
    "positionSeconds": 120,
    "durationSeconds": 268,
    "repeatMode": "ALL",
    "isShuffled": false,
    "playbackSpeed": 1.0,
    "updatedAt": 1723184000000
  }
}
```

**未播放（无歌）：**

```json
{
  "code": 200,
  "data": {
    "isPlaying": false,
    "isLoading": false,
    "song": null,
    "progressMs": 0,
    "durationMs": 0,
    "positionSeconds": 0,
    "durationSeconds": 0,
    "repeatMode": "ALL",
    "isShuffled": false,
    "playbackSpeed": 1.0,
    "updatedAt": 1723184000000
  }
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `isPlaying` | Boolean | 是否正在播放 |
| `isLoading` | Boolean | 是否正在加载（缓冲/解析） |
| `song` | Object/null | 当前歌曲，无歌时为 null |
| `song.id` | String | 歌曲 ID（Long 转字符串，避免精度丢失） |
| `song.title` | String | 歌名 |
| `song.artist` | String | 歌手 |
| `song.album` | String | 专辑 |
| `song.durationMs` | Number | 歌曲时长（毫秒） |
| `song.artworkUrl` | String/null | 封面 URL：在线歌为真实图片 URL；本地歌为 `/api/artwork?albumId=<id>`（相对路径，需拼 `http://<设备IP>:24880` 前缀）；均不可得时为 null |
| `song.isOnline` | Boolean | 是否在线歌曲（网络音源） |
| `song.source` | String | 音源：`local` / `netease` / `kuwo` / `kugou` / `qq` 等 |
| `progressMs` | Number | 当前进度（毫秒） |
| `durationMs` | Number | 总时长（毫秒） |
| `positionSeconds` | Number | 当前进度（秒） |
| `durationSeconds` | Number | 总时长（秒） |
| `repeatMode` | String | 循环模式：`NONE` / `ALL` / `ONE` |
| `isShuffled` | Boolean | 是否随机播放 |
| `playbackSpeed` | Number | 播放倍速 |
| `updatedAt` | Number | 响应生成时间戳（毫秒，epoch） |

### 4.4 GET /api/stats ⭐ 听歌统计

返回累计听歌总时间与曲库数量等统计信息。

```bash
curl http://192.168.1.100:24880/api/stats
```

```json
{
  "code": 200,
  "data": {
    "listeningTimeMs": 36000000,
    "songCount": 128,
    "completedPlays": 42,
    "repeatCount": 5
  }
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `listeningTimeMs` | Number | 累计听歌总时间（毫秒） |
| `songCount` | Number | 曲库歌曲数量（本地媒体库，时长 > 30s 且排除录音） |
| `completedPlays` | Number | 完整播放次数 |
| `repeatCount` | Number | 单曲循环次数 |

### 4.5 GET /api/state

简版播放状态（字段与 LanRemote 局域网遥控一致，便于已接入 LanRemote 的客户端复用）。

```bash
curl http://192.168.1.100:24880/api/state
```

```json
{
  "code": 200,
  "data": {
    "isPlaying": true,
    "progressMs": 120000,
    "durationMs": 268000,
    "isShuffled": false,
    "repeatMode": "ALL",
    "currentSong": {
      "id": "123456789",
      "title": "夜空中最亮的星",
      "artist": "逃跑计划",
      "album": "世界",
      "durationMs": 268000
    }
  }
}
```

### 4.6 GET /api/artwork

返回专辑封面图片（代理 Muse 本地 MediaStore，解决本地歌曲封面跨应用不可达问题）。

```bash
curl http://192.168.1.100:24880/api/artwork?albumId=12345 -o cover.jpg
```

**参数：**

| 参数 | 必填 | 说明 |
|---|---|---|
| `albumId` | ✅ | 专辑 ID（来自 `now-playing` 的 `song` 字段，可在 `artworkUrl` 中取到） |

**响应：**

- 成功：`200 image/jpeg`，图片字节流（`Cache-Control: public, max-age=86400`）
- `400`：缺 `albumId` 或非数字
- `404`：该专辑无封面

### 4.7 GET /docs

返回完整的开发文档（Markdown 格式），供第三方开发者直接阅读。

```bash
curl http://192.168.1.100:24880/docs
```

响应：`text/markdown; charset=utf-8`，内容即本文档。

---

## 5. 控制端点详解

控制端点均为 `POST`，匿名可用（局域网内无需 token）。成功统一返回：

```json
{ "code": 200, "data": { "accepted": true } }
```

### 5.1 POST /api/play

恢复播放（若当前已暂停）。

```bash
curl -X POST http://192.168.1.100:24880/api/play
```

### 5.2 POST /api/pause

暂停播放。

```bash
curl -X POST http://192.168.1.100:24880/api/pause
```

### 5.3 POST /api/toggle

播放 / 暂停切换。

```bash
curl -X POST http://192.168.1.100:24880/api/toggle
```

### 5.4 POST /api/next

切换到下一首。

```bash
curl -X POST http://192.168.1.100:24880/api/next
```

### 5.5 POST /api/previous

切换到上一首。

```bash
curl -X POST http://192.168.1.100:24880/api/previous
```

### 5.6 POST /api/seek

跳转到指定进度。

```bash
curl -X POST http://192.168.1.100:24880/api/seek \
  -H "Content-Type: application/json" \
  -d '{"positionMs": 120000}'
```

**参数（body）：**

| 参数 | 必填 | 说明 |
|---|---|---|
| `positionMs` | 二选一 | 目标进度（毫秒），会被钳制到 `[0, durationMs]` 范围 |
| `positionSeconds` | 二选一 | 目标进度（秒），与 `positionMs` 同时给出时以 `positionMs` 优先 |

### 5.7 POST /api/shuffle

设置随机播放开关。

```bash
curl -X POST http://192.168.1.100:24880/api/shuffle \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'
```

**参数（body）：**

| 参数 | 必填 | 说明 |
|---|---|---|
| `enabled` | ✅ | 是否开启随机播放 |

### 5.8 POST /api/repeat

设置循环模式。

```bash
curl -X POST http://192.168.1.100:24880/api/repeat \
  -H "Content-Type: application/json" \
  -d '{"mode": "ALL"}'
```

**参数（body）：**

| 参数 | 必填 | 说明 |
|---|---|---|
| `mode` | ✅ | 循环模式：`NONE` / `ALL` / `ONE`（大小写不敏感） |

---

## 6. 错误响应

| HTTP 状态 | code | error | 说明 |
|---|---|---|---|
| 400 | 400 | `<错误信息>` | 参数错误（如 `mode` 非法、`positionMs` 缺失） |
| 404 | 404 | `not_found` | 端点不存在 |
| 405 | 405 | `method_not_allowed` | 方法不允许（如对控制端点用 GET） |
| 503 | 503 | `control_unavailable` | 播放器控制处理器尚未就绪 |

```json
{ "code": 404, "error": "not_found" }
```

---

## 7. 接入示例

### Kotlin（Android/桌面）

```kotlin
val client = OkHttpClient()
val request = Request.Builder()
    .url("http://192.168.1.100:24880/api/now-playing")
    .build()

client.newCall(request).execute().use { response ->
    val json = JSONObject(response.body?.string())
    val data = json.getJSONObject("data")
    val song = data.optJSONObject("song")
    if (song != null) {
        val title = song.getString("title")
        val artist = song.getString("artist")
        val isPlaying = data.getBoolean("isPlaying")
        println("正在${if (isPlaying) "播放" else "暂停"}: $title - $artist")
    }
}
```

### Python

```python
import requests

BASE = "http://192.168.1.100:24880"

# 读取播放状态
r = requests.get(f"{BASE}/api/now-playing", timeout=3)
data = r.json()["data"]
song = data["song"]
if song:
    print(f"正在播放: {song['title']} - {song['artist']}")
    print(f"进度: {data['positionSeconds']}/{data['durationSeconds']}s")

# 读取听歌统计
stats = requests.get(f"{BASE}/api/stats", timeout=3).json()["data"]
print(f"累计聆听 {stats['listeningTimeMs']/3600000:.1f} 小时, 曲库 {stats['songCount']} 首")

# 控制播放
requests.post(f"{BASE}/api/next", timeout=3)
requests.post(f"{BASE}/api/seek", json={"positionMs": 120000}, timeout=3)
requests.post(f"{BASE}/api/shuffle", json={"enabled": False}, timeout=3)
requests.post(f"{BASE}/api/repeat", json={"mode": "ONE"}, timeout=3)
```

### JavaScript（浏览器，可直接跨域）

```javascript
const BASE = "http://192.168.1.100:24880";

// 读取
const res = await fetch(`${BASE}/api/now-playing`);
const { data } = await res.json();
if (data.song) {
  document.title = `${data.song.title} - ${data.song.artist}`;
}

// 控制（跨域 POST 会触发预检，服务端已放行）
await fetch(`${BASE}/api/pause`, { method: "POST" });
await fetch(`${BASE}/api/seek`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ positionMs: 120000 }),
});
```

---

## 8. 注意事项

1. **控制开放（匿名）**：控制端点与读取端点一样匿名，局域网内任何能访问 `24880` 端口的设备都能控制播放，请仅在可信网络中使用。
2. **网络环境**：API 监听所有网卡，同一局域网（或设备本机）可访问。跨网段需自行做端口转发/VPN。
3. **隐私**：`artworkUrl` 可能指向内网/在线资源；本地歌曲封面为 `/api/artwork` 代理地址。
4. **端口占用**：若 24880 被占用，Muse 会在日志输出警告并跳过启动（不影响其他功能）。
5. **封面 URL**：在线歌曲的 `artworkUrl` 为可直连的图片 URL，可直接用于 `Coil`/`Glide`/`<img>` 加载；本地歌曲的 `artworkUrl` 是相对路径（`/api/artwork?albumId=<id>`），加载时需拼上主机前缀 `http://<设备IP>:24880`（如 `http://192.168.1.100:24880/api/artwork?albumId=123`）。
6. **听歌统计口径**：`listeningTimeMs` 为累计听歌总时长（按实际播放时长逐秒累加、持久化保存、跨重启保留），暂停期间不计入。
