# Muse 插件开发文档（API v1，源码审计版）

> 本文档依据当前代码实际行为整理，适用于当前 Muse 插件 API v1。

## 1. 插件类型

外部插件包扩展名为 `.museplugin`，本质为 ZIP。插件有两种执行入口：

1. **JavaScript Hook**
   - 入口固定为 `index.js`。
   - 在受限 Rhino ES6 环境中执行。
   - 用于修改随机队列、选择下一首、监听播放完成等。
2. **WebUI**
   - 页面必须位于 `web/`。
   - 通过 `window.museHost.postMessage()` 调用宿主 API。
   - 不能直接访问公网、Android API、媒体文件或本地文件系统。

## 2. 插件包结构

```text
plugin.json
index.js
web/                    # 可选
  index.html
  app.js
  style.css
  assets...
```

### 2.1 包限制

| 项目 | 限制 |
|---|---:|
| 压缩包大小 | 最大 4 MiB |
| 解压后总大小 | 最大 8 MiB |
| ZIP 条目数量 | 最多 64，目录也计数 |
| `plugin.json` | 最大 64 KiB |
| `index.js` | 最大 1 MiB |
| 单个 Web 文件 | 最大 2 MiB |
| 根目录文件 | 仅允许 `plugin.json`、`index.js` |
| 子目录 | 仅允许 `web/` |

WebUI 允许的扩展名：`html`、`js`、`css`、`json`、`png`、`jpg`、`jpeg`、`gif`、`webp`、`svg`、`woff`、`woff2`。

路径不得包含绝对路径、空路径段、`.`、`..`、冒号、NUL 或目录穿越，长度不得超过 240 个字符。

## 3. `plugin.json`

### 3.1 完整示例

```json
{
  "apiVersion": 1,
  "id": "com.example.muse.my-plugin",
  "name": "示例插件",
  "version": "1.0.0",
  "author": "Developer",
  "description": "插件说明",
  "entry": "index.js",
  "hooks": [
    "onEnable",
    "onDisable",
    "onShuffle",
    "onNextTrack",
    "onTrackFinished"
  ],
  "permissions": [
    "config",
    "player.read",
    "player.control",
    "queue.read",
    "library.read",
    "network.request"
  ],
  "network": {
    "allowHosts": ["api.example.com"]
  },
  "webUi": {
    "entry": "web/index.html"
  },
  "contributes": {
    "playerGestures": [
      {
        "gesture": "artwork.swipeUp",
        "action": "openWebUi",
        "entry": "web/transfer.html"
      }
    ]
  }
}
```

### 3.2 字段规则

| 字段 | 必需 | 规则 |
|---|---:|---|
| `apiVersion` | 是 | 当前只能为整数 `1` |
| `id` | 是 | 3–128 字符，首字符为字母，之后允许字母、数字、`_`、`.`、`-` |
| `name` | 是 | 非空，最多 128 字符 |
| `version` | 是 | 非空，最多 64 字符，不强制语义版本 |
| `author` | 否 | 默认“未知作者”，最多 128 字符 |
| `description` | 否 | 最多 1024 字符 |
| `entry` | 否 | 默认且只能为 `index.js` |
| `hooks` | 否 | 不得重复，只能使用受支持的 Hook |
| `permissions` | 否 | 不得重复，只能使用受支持权限 |
| `network.allowHosts` | 否 | 公网代理域名白名单 |
| `webUi.entry` | 否 | 必须是 `web/` 下的 HTML |
| `contributes.playerGestures` | 否 | 最多 4 项 |

未知字段目前会被忽略。

## 4. 权限模型

插件清单中的权限只是申请项。调用受保护接口时通常需要同时满足：

1. 插件已启用；
2. 清单声明了对应权限；
3. 用户已授予对应权限。

新安装插件默认关闭，且默认不授予任何权限。

| 权限 | 能力 |
|---|---|
| `config` | 读写插件私有配置 |
| `player.read` | 读取本机播放器状态 |
| `player.control` | 控制本机播放 |
| `queue.read` | 读取播放队列 |
| `queue.control` | 播放队列索引、替换队列 |
| `library.read` | 浏览、搜索媒体库 |
| `library.refresh` | 请求刷新媒体库 |
| `playlists.read` | 读取歌单 |
| `playlists.write` | 创建、重命名、修改歌单歌曲 |
| `playlists.delete` | 删除歌单 |
| `lyrics.read` | 读取歌词 |
| `stats.read` | 读取收听统计 |
| `theme.read` | 读取主题状态 |
| `theme.write` | 修改主题 |
| `equalizer.read` | 读取均衡器状态 |
| `equalizer.control` | 控制均衡器 |
| `profile.read` | 读取用户显示资料 |
| `profile.write` | 修改用户显示名称 |
| `lan.discovery` | 发现局域网 Muse |
| `lan.pairing` | 配对、忘记设备 |
| `lan.state` | 读取 LAN 设备及服务状态 |
| `lan.control` | 控制已配对 Muse |
| `lan.hosting` | 管理本机 LAN 服务及授权 |
| `lan.transfer` | 流转当前歌曲 |
| `network.request` | 通过宿主代理访问公网 HTTPS |

插件不会获得真实文件路径、媒体 URI、WebDAV 凭据、LAN token、Android `Context` 或任意原生调用能力。

## 5. JavaScript Hook

入口脚本必须设置：

```js
globalThis.musePlugin = {
  onEnable(context) {},
  onDisable(context) {},
  onShuffle(queue) {
    return queue.map(song => song.id);
  },
  onNextTrack(request) {
    return null;
  },
  onTrackFinished(song) {}
};
```

只有已在清单 `hooks` 中声明且对应属性是函数的 Hook 才会执行。

### 5.1 Hook 歌曲对象

```json
{
  "id": "123",
  "title": "Title",
  "artist": "Artist",
  "album": "Album",
  "durationMs": 180000
}
```

歌曲 ID 建议始终作为字符串处理。

### 5.2 `onEnable(context)`

插件成功加载后调用。参数固定为 `{}`，返回值被忽略。抛出异常或执行超时会导致插件启用失败。

### 5.3 `onDisable(context)`

插件关闭、升级替换或因异常自动停用时调用。参数固定为 `{}`，返回值被忽略。

### 5.4 `onShuffle(queue)`

参数为歌曲对象数组，必须返回由原队列歌曲 ID 组成的新数组：

```js
onShuffle(queue) {
  return queue.slice().reverse().map(song => song.id);
}
```

正确返回格式：

```json
["123", "456", "789"]
```

要求：

- 返回数组长度与原队列相同；
- 所有 ID 均来自原队列；
- 不得遗漏或重复歌曲；
- 队列少于两首时不会调用；
- 多个插件按注册顺序调用，第一个合法结果生效；
- 不能返回歌曲对象数组。

### 5.5 `onNextTrack(request)`

```json
{
  "trigger": "MANUAL",
  "currentSong": {},
  "currentIndex": 0,
  "queue": []
}
```

`trigger`：

| 值 | 来源 |
|---|---|
| `COMPLETION` | 当前歌曲自然播放完成 |
| `MANUAL` | 应用内下一首或 LAN 远程下一首 |
| `SYSTEM` | MediaSession/系统媒体控制下一首 |

必须返回目标歌曲的 ID 字符串：

```js
onNextTrack(request) {
  const next = request.queue[request.currentIndex + 1];
  return next ? next.id : null;
}
```

返回 `null` 或 `undefined` 表示不处理。返回歌曲对象不会生效。

### 5.6 `onTrackFinished(song)`

歌曲自然播放完成后调用，返回值被忽略。

### 5.7 `museConfig`

每次 Hook 调用前，宿主更新 `globalThis.museConfig`：

```js
globalThis.musePlugin = {
  onNextTrack(request) {
    if (globalThis.museConfig.reverse && request.currentIndex > 0) {
      return request.queue[request.currentIndex - 1].id;
    }
    return null;
  }
};
```

入口脚本首次顶层执行时，`museConfig` 尚未注入。

### 5.8 Hook 运行限制

- Rhino ES6 解释执行；
- 无 Java/Android 类访问；
- 无反射、文件系统、裸网络或原生代码；
- 每次脚本求值或 Hook 调用约有 100 ms 执行期限；
- 每 10,000 条 Rhino 指令检查一次超时；
- Hook 返回 JSON 最大 1 MiB；
- 同一插件的 Hook 串行执行；
- Hook 抛出异常、超时或返回值转换失败时，插件会被自动关闭。

## 6. WebUI 环境

WebUI 从以下隔离域名加载：

```text
https://muse-plugin.local/plugin/
```

限制：JavaScript 可用；文件、Content URI、DOM Storage、数据库、定位和混合内容被禁用；所有 Web 权限请求被拒绝；外部导航和外部子资源被阻止；不能直接访问公网。

## 7. WebUI 消息协议

### 7.1 请求格式

```json
{
  "id": "request-id",
  "type": "player.getState",
  "payload": {}
}
```

`id`、`type` 必须是字符串，`payload` 必须是 JSON 对象。即使接口无参数，也必须发送 `{}`。

### 7.2 响应格式

成功：

```json
{
  "id": "request-id",
  "ok": true,
  "response": {}
}
```

失败：

```json
{
  "id": "request-id",
  "ok": false,
  "error": "错误信息"
}
```

### 7.3 推荐封装

```js
const pending = new Map();

window.addEventListener('message', event => {
  let message;
  try { message = JSON.parse(event.data); } catch (_) { return; }

  const request = pending.get(message.id);
  if (!request) return;
  pending.delete(message.id);

  if (message.ok) request.resolve(message.response);
  else request.reject(new Error(message.error || '请求失败'));
});

function hostCall(type, payload = {}) {
  const id = crypto.randomUUID();
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    window.museHost.postMessage(JSON.stringify({ id, type, payload }));
  });
}
```

响应必须通过顶层 `window` 的 `message` 事件接收，不能使用 `window.museHost.onmessage`。当前协议没有宿主主动事件、取消请求、调用超时、速率限制或权限查询接口。

## 8. 配置 API

### `config.get`

权限：`config`

```js
const { config } = await hostCall('config.get');
```

### `config.set`

权限：`config`

```js
const result = await hostCall('config.set', {
  config: { reverse: true }
});
```

配置按插件 ID 隔离，必须为 JSON 对象，序列化后最大 64 KiB；`config.set` 整对象覆盖，不执行深度合并。

## 9. 播放器 API

### `player.getState`

权限：`player.read`

```js
const state = await hostCall('player.getState');
```

```json
{
  "isPlaying": true,
  "progressMs": 60000,
  "durationMs": 180000,
  "isShuffled": false,
  "repeatMode": "NONE",
  "currentSong": {}
}
```

当前不返回 `isLoading`。

### 播放控制

以下接口均需 `player.control`：

```js
await hostCall('player.play');
await hostCall('player.pause');
await hostCall('player.next');
await hostCall('player.previous');
await hostCall('player.seek', { positionMs: 60000 });
await hostCall('player.setRepeatMode', { mode: 'ALL' });
await hostCall('player.setShuffle', { enabled: true });
await hostCall('player.playSong', { songId: 123 });
```

重复模式为 `NONE`、`ALL`、`ONE`。本机 `player.seek` 将负值修正为 `0`，但 Host API 层不主动限制到歌曲时长。

## 10. 队列 API

### `queue.get`

权限：`queue.read`

```js
const queue = await hostCall('queue.get');
```

响应包含 `currentIndex` 和 `songs`。

### `queue.playIndex`

权限：`queue.control`

```js
await hostCall('queue.playIndex', { index: 2 });
```

### `queue.replace`

权限：`queue.control`

```js
await hostCall('queue.replace', {
  songIds: [123, 456, 789],
  startIndex: 0
});
```

限制：必须提交 1–500 个歌曲 ID；歌曲必须存在；重复 ID 会去重；队列不能为空；`startIndex` 必须有效。

## 11. 媒体库 API

以下读取接口需 `library.read`：

```js
await hostCall('library.listSongs', { offset: 0, limit: 100 });
await hostCall('library.search', { query: 'Muse', offset: 0, limit: 50 });
await hostCall('library.getSong', { songId: 123 });
await hostCall('library.getSummary');
```

分页响应包含 `total`、`offset`、`limit`、`songs`。

规则：`offset` 默认 0 且不小于 0；`limit` 默认 100，范围 1–200；搜索词 trim 后最多 128 字符；空搜索词匹配全部歌曲。

完整歌曲 DTO：

```json
{
  "id": "123",
  "title": "Title",
  "artist": "Artist",
  "album": "Album",
  "durationMs": 180000,
  "albumId": "456",
  "mimeType": "audio/mpeg",
  "sizeBytes": 12345678,
  "bitrate": 320000,
  "sampleRate": 44100
}
```

不返回真实路径、Content URI 或文件名。

### `library.refresh`

权限：`library.refresh`

```js
await hostCall('library.refresh');
```

返回 `{ "accepted": true }`，刷新请求为异步执行。

## 12. 歌单 API

### 读取

权限：`playlists.read`

```js
const { playlists } = await hostCall('playlists.list');
const songs = await hostCall('playlists.getSongs', {
  playlistId: 'playlist-id',
  offset: 0,
  limit: 100
});
```

歌单 DTO 包含 `id`、`name`、`songCount`、`hasCover`。

### 播放歌单

权限：`player.control`

```js
await hostCall('playlists.play', {
  playlistId: 'playlist-id',
  startIndex: 0
});
```

该接口当前不要求 `playlists.read`。

### 写入

权限：`playlists.write`

```js
const playlist = await hostCall('playlists.create', { name: '新歌单' });
await hostCall('playlists.rename', { playlistId: playlist.id, name: '新名称' });
await hostCall('playlists.addSongs', { playlistId: playlist.id, songIds: [123, 456] });
await hostCall('playlists.removeSongs', { playlistId: playlist.id, songIds: [123] });
```

名称 trim 后必须为 1–64 字符。单次增删必须提交 1–500 个有效歌曲 ID，重复 ID 会被去重。

### 删除

权限：`playlists.delete`

```js
await hostCall('playlists.delete', { playlistId: 'playlist-id' });
```

## 13. 歌词与统计 API

### 歌词

权限：`lyrics.read`

```js
const lyrics = await hostCall('lyrics.get', { songId: 123 });
const current = await hostCall('lyrics.getCurrent');
```

```json
{
  "songId": "123",
  "lines": [{ "timeMs": 1200, "text": "歌词" }]
}
```

歌曲必须存在于当前媒体库；没有当前歌曲时 `lyrics.getCurrent` 会失败。

### 统计

权限：`stats.read`

```js
const stats = await hostCall('stats.get');
```

响应包含 `listeningTimeMs`、`completedPlays`、`repeatCount`。

## 14. 主题 API

### `theme.get`

权限：`theme.read`

```js
const theme = await hostCall('theme.get');
```

响应包含 `isLight`、`accent`、`uiStyle`、`playerBgMode`、`hasWallpaper`、`hasVideo`。

### `theme.apply`

权限：`theme.write`

```js
await hostCall('theme.apply', {
  isLight: false,
  accent: '#6EE7B7',
  uiStyle: 'APPLE',
  playerBgMode: 'DYNAMIC_COLOR'
});
```

`uiStyle`：`APPLE`、`MONET`。

`playerBgMode`：`ALBUM_EXTEND`、`DYNAMIC_COLOR`、`CUSTOM`。

强调色必须为 `#RRGGBB`。字段逐项应用，不是事务操作；后续字段失败不会回滚之前已应用的字段。

### `theme.reset`

权限：`theme.write`

```js
await hostCall('theme.reset');
```

当前只清除自定义背景颜色，不会重置亮暗模式、UI 风格、背景模式、壁纸或视频。

## 15. 均衡器 API

### 读取

权限：`equalizer.read`

```js
const eq = await hostCall('equalizer.get');
```

响应包含 `enabled`、`presetName`、`presets` 以及频段列表 `bands`。频段包含 `freqHz`、`levelDb`、`minDb`、`maxDb`。

### 控制

权限：`equalizer.control`

```js
await hostCall('equalizer.setEnabled', { enabled: true });
await hostCall('equalizer.setBand', { index: 0, levelDb: 2.5 });
await hostCall('equalizer.reset');
await hostCall('equalizer.savePreset', { name: 'My EQ' });
await hostCall('equalizer.loadPreset', { name: 'My EQ' });
await hostCall('equalizer.deletePreset', { name: 'My EQ' });
```

保存预设名称必须为 1–64 字符；加载不存在的预设会失败。

## 16. 用户资料 API

### `profile.get`

权限：`profile.read`

```js
const profile = await hostCall('profile.get');
```

响应包含 `name`、`hasAvatar`，不会返回头像文件或 URI。

### `profile.setName`

权限：`profile.write`

```js
const profile = await hostCall('profile.setName', { name: 'New Name' });
```

名称会去除首尾空白并最多保留 24 字符；空名称自动替换为“`Muse 用户`”。

## 17. 公网网络 API

### `network.request`

权限：`network.request`

清单还必须声明精确域名：

```json
{
  "network": {
    "allowHosts": ["api.example.com"]
  }
}
```

调用：

```js
const response = await hostCall('network.request', {
  method: 'GET',
  url: 'https://api.example.com/data',
  headers: { "Accept": "application/json" }
});
```

响应包含 `status`、按 UTF-8 解码的 `body`、`contentType`。

### 17.1 域名规则

- 最多 16 个；
- 精确域名匹配；
- 不支持通配符；
- 不自动包含子域；
- 必须至少包含一个点；
- 不接受 URL、端口或纯数字 IP；
- 经 IDN ASCII 化并转为小写。

### 17.2 请求限制

- 仅 HTTPS 443；
- 仅 `GET`、`POST`；
- 禁止 URL 用户名和密码；
- 请求体最大 256 KiB；
- 仅允许 `Accept`、`Content-Type` 请求头；
- 单个头值最大 1024 字符；
- 禁止 Cookie、代理和重定向；
- 禁止私网、回环、链路本地和组播目标；
- 连接超时 10 秒，读写超时 15 秒，总调用超时 20 秒。

### 17.3 响应限制

- 最大 1 MiB；
- 二进制内容会被强制按 UTF-8 转为字符串；
- 3xx 视为错误；
- 4xx、5xx 正常返回对应 `status`；
- 不返回普通响应头；
- 空响应 body 会被视为错误。

## 18. LAN Remote API

LAN API 不接受插件提供 URL、IP、端口、认证头或 token。发现、配对、HTTP 请求、认证及媒体访问均由宿主管理。

### 18.1 `lan.discover`

权限：`lan.discovery`

```js
const result = await hostCall('lan.discover');
```

响应设备包含 `id`、`name`、`paired`。调用会启动异步 NSD/mDNS 发现并立即返回当前缓存，插件应定期重新调用以取得新结果。

### 18.2 `lan.stopDiscovery`

权限：`lan.discovery`

```js
await hostCall('lan.stopDiscovery');
```

### 18.3 `lan.devices`

权限：`lan.state`

```js
const result = await hostCall('lan.devices');
```

返回已配对设备的 `id`、`name`，不暴露远端 IP、端口或 token。

### 18.4 `lan.pair`

权限：`lan.pairing`

```js
const result = await hostCall('lan.pair', {
  deviceId: 'device-id',
  code: '123456'
});
```

配对码必须是 6 位数字，有效期约 3 分钟，配对成功后立即失效。

### 18.5 `lan.getState`

权限：`lan.state`

```js
const state = await hostCall('lan.getState', { deviceId: 'device-id' });
```

返回远端播放器公开状态。

### 18.6 `lan.command`

权限：`lan.control`

```js
await hostCall('lan.command', {
  deviceId: 'device-id',
  command: 'seek',
  payload: { positionMs: 60000 }
});
```

支持命令：

| 命令 | `payload` |
|---|---|
| `play` | `{}` |
| `pause` | `{}` |
| `next` | `{}` |
| `previous` | `{}` |
| `seek` | `{positionMs}` |
| `setShuffle` | `{enabled}` |
| `setRepeatMode` | `{mode}` |

### 18.7 `lan.transferPlayback`

权限：`lan.transfer`

```js
await hostCall('lan.transferPlayback', { deviceId: 'device-id' });
```

当前实际只流转正在播放的单首歌曲，而不是完整播放队列。目标已有相同歌曲时复用，缺失时由宿主发送媒体文件，并可附带同名本地歌词。接收端恢复进度、播放状态和循环模式，但当前会关闭随机播放。成功后，如果发送端正在播放，则暂停发送端。插件不会获得媒体路径、文件流或认证 token。

### 18.8 `lan.localState`

权限：`lan.state`

```js
const state = await hostCall('lan.localState');
```

响应包含：`hosting`、本机 `port`、`pairingCode`、`pairingExpiresAt`、`discovering`、`message`、`clients`。

### 18.9 `lan.setHosting`

权限：`lan.hosting`

```js
await hostCall('lan.setHosting', { enabled: true });
```

### 18.10 `lan.generatePairingCode`

权限：`lan.hosting`

```js
const result = await hostCall('lan.generatePairingCode');
```

返回 `code`、`expiresAt`。

### 18.11 `lan.revokeClient`

权限：`lan.hosting`

```js
await hostCall('lan.revokeClient', { clientId: 'client-id' });
```

### 18.12 `lan.forgetDevice`

权限：`lan.pairing`

```js
await hostCall('lan.forgetDevice', { deviceId: 'device-id' });
```

## 19. 播放页手势贡献

当前唯一扩展点：手势 `artwork.swipeUp`，动作 `openWebUi`。

```json
{
  "webUi": {
    "entry": "web/index.html"
  },
  "contributes": {
    "playerGestures": [
      {
        "gesture": "artwork.swipeUp",
        "action": "openWebUi",
        "entry": "web/transfer.html"
      }
    ]
  }
}
```

规则：最多 4 项；同一插件不能重复相同手势；必须声明默认 `webUi`；`entry` 可省略，省略时打开默认 WebUI；插件未启用时不生效；多个插件贡献同一手势时使用第一个匹配项；自定义 `entry` 当前在实际打开时才检查是否存在。

## 20. API 快速索引

| 消息类型 | 权限 |
|---|---|
| `config.get`、`config.set` | `config` |
| `player.getState` | `player.read` |
| `player.play`、`player.pause`、`player.next`、`player.previous`、`player.seek`、`player.setRepeatMode`、`player.setShuffle`、`player.playSong` | `player.control` |
| `queue.get` | `queue.read` |
| `queue.playIndex`、`queue.replace` | `queue.control` |
| `library.listSongs`、`library.search`、`library.getSong`、`library.getSummary` | `library.read` |
| `library.refresh` | `library.refresh` |
| `playlists.list`、`playlists.getSongs` | `playlists.read` |
| `playlists.play` | `player.control` |
| `playlists.create`、`playlists.rename`、`playlists.addSongs`、`playlists.removeSongs` | `playlists.write` |
| `playlists.delete` | `playlists.delete` |
| `lyrics.get`、`lyrics.getCurrent` | `lyrics.read` |
| `stats.get` | `stats.read` |
| `theme.get` | `theme.read` |
| `theme.apply`、`theme.reset` | `theme.write` |
| `equalizer.get` | `equalizer.read` |
| `equalizer.setEnabled`、`equalizer.setBand`、`equalizer.reset`、`equalizer.loadPreset`、`equalizer.savePreset`、`equalizer.deletePreset` | `equalizer.control` |
| `profile.get` | `profile.read` |
| `profile.setName` | `profile.write` |
| `network.request` | `network.request` |
| `lan.discover`、`lan.stopDiscovery` | `lan.discovery` |
| `lan.pair`、`lan.forgetDevice` | `lan.pairing` |
| `lan.devices`、`lan.getState`、`lan.localState` | `lan.state` |
| `lan.command` | `lan.control` |
| `lan.setHosting`、`lan.generatePairingCode`、`lan.revokeClient` | `lan.hosting` |
| `lan.transferPlayback` | `lan.transfer` |

## 21. 错误处理

```js
try {
  const state = await hostCall('player.getState');
} catch (error) {
  console.error(error.message);
}
```

常见错误包括：插件未启用、未声明权限、用户未授权、消息类型不支持、字段缺失或类型错误、歌曲/歌单/设备不存在、参数范围无效、域名未列入白名单、网络目标解析到私网地址、请求或响应超过大小限制。

## 22. 打包与安装

打包时压缩目录内容，不要包含外层文件夹：

```text
plugin.json
index.js
web/index.html
web/app.js
web/style.css
```

将 ZIP 重命名为 `.museplugin`，然后：

1. 打开“资料库 → 播放插件”；
2. 导入 `.museplugin`；
3. 核对插件信息和权限；
4. 手动启用插件；
5. 按需逐项授权。

调试可查看 Android 日志标签 `MusePlugins`。Hook 异常、超时、加载失败和自动关闭都会记录在该标签下。

## 23. 当前实现注意事项

1. `onShuffle` 必须返回歌曲 ID 数组，不是歌曲对象数组。
2. `onNextTrack` 必须返回歌曲 ID 字符串，不是歌曲对象。
3. WebUI 响应应监听顶层 `window` 的 `message` 事件。
4. `player.getState` 当前不返回 `isLoading`。
5. `lan.transferPlayback` 当前只流转当前歌曲，不是完整队列。
6. `theme.reset` 当前只清除自定义背景色。
7. `playlists.play` 实际要求 `player.control`，不要求 `playlists.read`。
8. `lan.localState` 实际要求 `lan.state`。
9. `profile.setName` 会截断名称，并为空名称设置默认值。
10. `network.request` 当前内部执行路径未再次检查插件启用状态；正常情况下仅已启用插件可以打开 WebUI，但已打开页面后的插件状态变化属于当前实现边界。
