# Muse 插件开发指南

本文档适用于 Muse 插件 API v1。插件包扩展名为 `.museplugin`，本质是 ZIP。Muse 允许插件通过受限 JavaScript Hook 改变选曲逻辑，并通过隔离 WebUI 调用音乐库、播放器、队列、歌单、歌词、主题、均衡器等宿主能力。

## Muse LAN Remote

Muse 支持带配对认证的设备间播放控制。公网 `network.request` 仍拒绝私网、回环和链路本地地址；LAN API 不接受任意 URL、IP、端口或认证头。

插件清单中的权限只是申请项，不代表已经获得授权。用户可在“播放插件”页面逐项允许或撤销权限；宿主会在每次 Host API 调用时同时校验“清单已声明”和“用户已授权”。新安装插件默认不授予任何权限，启用插件也不会自动授权。

权限：

- `lan.discovery`：发现同一局域网中的 Muse。
- `lan.pairing`：用目标设备显示的一次性 6 位码配对。
- `lan.state`：列出已配对设备并读取播放状态。
- `lan.control`：向已配对设备发送固定播放命令。
- `lan.hosting`：开启或关闭本机 LAN 服务、生成配对码和撤销控制端授权。
- `lan.transfer`：把当前播放队列及目标设备缺失的歌曲发送到已配对 Muse。

消息：

- `lan.discover {}`：开始 NSD/mDNS 发现并返回当前结果。
- `lan.localState {}`：读取本机服务、配对码有效期和已授权控制端。
- `lan.setHosting { enabled }`：开启或关闭承载 LAN Remote 的前台服务。
- `lan.generatePairingCode {}`：生成约 3 分钟有效的一次性 6 位配对码。
- `lan.revokeClient { clientId }`：撤销一个控制端对本机的授权。
- `lan.forgetDevice { deviceId }`：删除本机保存的远端设备凭据。
- `lan.stopDiscovery {}`：停止发现。
- `lan.devices {}`：列出已配对设备。宿主不会返回 token、IP 或端口。
- `lan.pair { deviceId, code }`：与发现结果配对；`code` 为 6 位字符串。
- `lan.getState { deviceId }`：读取当前歌曲、播放状态、进度、随机和循环模式。
- `lan.command { deviceId, command, payload }`：命令限于 `play`、`pause`、`next`、`previous`、`seek`、`setShuffle`、`setRepeatMode`。
- `lan.transferPlayback { deviceId }`：流转当前队列、歌曲文件、进度和播放模式。仅可发送到已配对设备；文件路径、地址和令牌不会暴露给插件。

### 播放页手势贡献

插件可在 `plugin.json` 中声明播放页手势入口。Muse 只提供通用扩展点，不内置具体业务界面：

```json
{
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

当前支持 `artwork.swipeUp` 和 `openWebUi`。可通过可选的 `entry` 打开插件内的专用页面；省略时打开默认 WebUI。插件未启用时贡献点不会生效；插件的设备选择和流转交互由其 WebUI 实现。

配对码有效期约 3 分钟且成功后立即作废。长期随机 token 由宿主管理，不向插件 JavaScript 暴露。用户可在“资料库 → LAN 远程控制”撤销控制端授权或忘记远程设备。

## 1. 插件包结构

```text
plugin.json
index.js
web/                 # 可选
  index.html
  app.js
  style.css
  assets...
```

限制：包最大 4 MiB，解压最大 8 MiB，最多 64 个条目；`index.js` 最大 1 MiB，单个 Web 文件最大 2 MiB。WebUI 仅允许 HTML、JS、CSS、JSON、常用图片、SVG 和 WOFF 字体。

## 2. plugin.json

```json
{
  "apiVersion": 1,
  "id": "com.example.muse.my-plugin",
  "name": "我的插件",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "插件说明",
  "entry": "index.js",
  "hooks": ["onNextTrack"],
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
  }
}
```

- `apiVersion`：当前只能为 `1`。
- `id`：全局唯一，建议反向域名格式。
- `entry`：当前固定为 `index.js`。
- `hooks`：实际实现的 Hook 名称。
- `permissions`：WebUI 申请的宿主能力。
- `network.allowHosts`：允许宿主代为访问的精确 HTTPS 域名。
- `webUi.entry`：可选 WebUI HTML，必须位于 `web/`。

## 3. JavaScript Hook

入口脚本必须设置 `globalThis.musePlugin`：

```js
globalThis.musePlugin = {
  onEnable() {},
  onDisable() {},
  onShuffle(songs) {
    return songs;
  },
  onNextTrack(request) {
    return null;
  },
  onTrackFinished(song) {}
};
```

### 数据结构

基础歌曲对象：

```json
{
  "id": "123",
  "title": "Title",
  "artist": "Artist",
  "album": "Album",
  "durationMs": 180000
}
```

`onShuffle(songs)` 必须返回包含相同歌曲且不重不漏的新数组。无效结果会被宿主忽略。

`onNextTrack(request)` 的请求包含：

```json
{
  "trigger": "MANUAL",
  "currentSong": {},
  "queue": [],
  "currentIndex": 0
}
```

可返回队列中的某个歌曲对象；返回 `null` 表示交给下一个插件或默认逻辑。多个选择型插件按注册顺序执行，第一个合法结果生效。

`globalThis.museConfig` 是当前插件的 JSON 配置快照。WebUI 调用 `config.set` 后，下一次 Hook 会取得新配置：

```js
globalThis.musePlugin = {
  onNextTrack(request) {
    if (globalThis.museConfig.reverse && request.currentIndex > 0) {
      return request.queue[request.currentIndex - 1];
    }
    return null;
  }
};
```

Hook 在受限 Rhino 环境中运行：没有 Android `Context`、Java/Kotlin对象、反射、文件系统、裸网络客户端或任意原生代码执行能力。Hook 应快速返回，超时或异常可能导致插件被关闭。

## 4. WebUI 消息协议

WebUI 通过 `window.museHost.postMessage()` 发送 JSON 字符串。推荐包装：

```js
const pending = new Map();

window.museHost.onmessage = event => {
  const message = JSON.parse(event.data);
  pending.get(message.id)?.(message);
  pending.delete(message.id);
};

function hostCall(type, payload = {}) {
  const id = crypto.randomUUID();
  return new Promise((resolve, reject) => {
    pending.set(id, message => message.ok
      ? resolve(message.response)
      : reject(new Error(message.error)));
    window.museHost.postMessage(JSON.stringify({ id, type, payload }));
  });
}
```

WebUI 运行于隔离的本地 HTTPS 域名中，不能直接访问公网、文件或 Android对象。所有宿主操作必须经过消息 API。

## 5. 权限

| 权限 | 能力 |
|---|---|
| `config` | 插件私有 JSON 配置 |
| `player.read` | 当前歌曲及播放状态 |
| `player.control` | 播放、暂停、切歌、seek、播放模式、播放指定歌曲 |
| `queue.read` | 读取播放队列 |
| `queue.control` | 播放队列索引、替换队列 |
| `library.read` | 浏览和搜索媒体库 |
| `library.refresh` | 请求重新扫描媒体库 |
| `playlists.read` | 读取歌单 |
| `playlists.write` | 创建、重命名、增删歌单曲目 |
| `playlists.delete` | 删除歌单 |
| `lyrics.read` | 读取本地歌词 |
| `stats.read` | 收听统计 |
| `theme.read` | 读取主题状态 |
| `theme.write` | 修改主题令牌 |
| `equalizer.read` | 读取均衡器 |
| `equalizer.control` | 修改均衡器和预设 |
| `profile.read` | 读取用户显示资料 |
| `profile.write` | 修改用户显示名称 |
| `network.request` | 通过宿主代理访问清单白名单中的 HTTPS 服务 |

权限遵循最小授权原则：插件只能调用同时满足“清单声明”和“用户授权”的能力。用户撤销权限后立即生效，不需要重启插件。高权限插件（例如未来的设备间音乐流转插件）仍应通过专用受控 Host API 操作媒体；即使用户全部授权，也不会获得 Android `Context`、真实文件路径、认证令牌或任意内网访问能力。

未声明权限的调用会失败。插件不能读取歌曲真实文件路径、WebDAV凭据、壁纸路径、头像路径或其他插件配置。

## 6. 配置 API

### `config.get`

```js
const { config } = await hostCall('config.get');
```

### `config.set`

```js
await hostCall('config.set', { config: { reverse: true } });
```

配置按插件 ID 隔离，必须是 JSON 对象，最大 64 KiB。`config.set` 是整对象覆盖。

## 7. 播放器 API

### `player.getState`

返回 `isPlaying`、`isLoading`（若宿主版本提供）、`progressMs`、`durationMs`、`isShuffled`、`repeatMode` 和 `currentSong`。

```js
const state = await hostCall('player.getState');
```

### 控制

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

重复模式为 `NONE`、`ALL`、`ONE`。歌曲 ID 可传 JSON 数字；建议插件内部按字符串保存，发送时转换为安全整数。

## 8. 队列 API

```js
const queue = await hostCall('queue.get');
// { currentIndex, songs }

await hostCall('queue.playIndex', { index: 2 });
await hostCall('queue.replace', {
  songIds: [123, 456, 789],
  startIndex: 0
});
```

`queue.replace` 只接受当前媒体库中存在的歌曲 ID，一次最多 500 首。

## 9. 媒体库 API

所有列表接口支持 `offset` 和 `limit`，`limit` 最大 200。

```js
await hostCall('library.listSongs', { offset: 0, limit: 100 });
await hostCall('library.search', { query: 'Muse', offset: 0, limit: 50 });
await hostCall('library.getSong', { songId: 123 });
await hostCall('library.getSummary');
await hostCall('library.refresh');
```

歌曲 DTO 可包含 `id`、标题、艺术家、专辑、时长、专辑 ID、MIME、大小、码率和采样率，不包含文件路径和 Content URI。

## 10. 歌单 API

```js
const { playlists } = await hostCall('playlists.list');
await hostCall('playlists.getSongs', { playlistId: '...', offset: 0, limit: 100 });
await hostCall('playlists.play', { playlistId: '...', startIndex: 0 });

const playlist = await hostCall('playlists.create', { name: '新歌单' });
await hostCall('playlists.rename', { playlistId: playlist.id, name: '新名称' });
await hostCall('playlists.addSongs', { playlistId: playlist.id, songIds: [123, 456] });
await hostCall('playlists.removeSongs', { playlistId: playlist.id, songIds: [123] });
await hostCall('playlists.delete', { playlistId: playlist.id });
```

名称为 1–64 个字符；单次增删为 1–500 首。删除需要独立的 `playlists.delete` 权限。

## 11. 歌词和统计

```js
const current = await hostCall('lyrics.getCurrent');
const selected = await hostCall('lyrics.get', { songId: 123 });
// { songId, lines: [{ timeMs, text }] }

const stats = await hostCall('stats.get');
// { listeningTimeMs, completedPlays, repeatCount }
```

## 12. 主题 API

```js
const theme = await hostCall('theme.get');

await hostCall('theme.apply', {
  isLight: false,
  accent: '#6EE7B7',
  uiStyle: 'APPLE',
  playerBgMode: 'DYNAMIC_COLOR'
});

await hostCall('theme.reset');
```

`uiStyle`：`APPLE`、`MONET`。`playerBgMode`：`ALBUM_EXTEND`、`DYNAMIC_COLOR`、`CUSTOM`。强调色必须为 `#RRGGBB`。插件能修改受控主题令牌，但不能注入或替换 Compose 代码。

## 13. 均衡器 API

```js
const eq = await hostCall('equalizer.get');
await hostCall('equalizer.setEnabled', { enabled: true });
await hostCall('equalizer.setBand', { index: 0, levelDb: 2.5 });
await hostCall('equalizer.reset');
await hostCall('equalizer.savePreset', { name: 'My EQ' });
await hostCall('equalizer.loadPreset', { name: 'My EQ' });
await hostCall('equalizer.deletePreset', { name: 'My EQ' });
```

频段增益会由宿主限制在设备支持范围内。均衡器通常在存在有效音频会话后才有频段数据。

## 14. 用户资料 API

```js
const profile = await hostCall('profile.get');
// { name, hasAvatar }
await hostCall('profile.setName', { name: 'Muse User' });
```

不会向插件返回头像文件或 URI。

## 15. 网络 API

网络请求同时需要用户授予 `network.request` 权限，并由 `network.allowHosts` 精确限制目标域名：

```js
const response = await hostCall('network.request', {
  method: 'GET',
  url: 'https://api.example.com/data',
  headers: { Accept: 'application/json' }
});
```

仅允许 HTTPS 443、GET/POST、白名单域名以及有限请求头；禁止重定向、Cookie、代理、私网、回环、链路本地和组播地址。请求体最大 256 KiB，响应最大 1 MiB。因此公网代理不能用于局域网设备控制；LAN 远程能力将使用独立的发现、配对和认证机制。

## 16. 打包和调试

使用任意 ZIP 工具打包“目录内容”，不要把外层目录本身放入包中。ZIP 条目应使用 `/`：

```text
plugin.json
index.js
web/index.html
```

将 ZIP 改名为 `.museplugin`，通过 Muse 插件页面导入。首次导入后插件默认关闭，需要用户检查来源并启用。开发时可使用 Android 日志标签 `MusePlugins` 查看插件加载、Hook 调度、异常和自动关闭信息。

## 17. 当前边界

API 追求自由和多样，但仍不开放任意原生代码执行、反射、宿主文件系统、真实媒体路径、凭据、裸 Intent、裸 Content URI 或无约束内网访问。此边界用于避免一个音乐插件直接获得与完整 Android 应用相同的权限，同时绝大多数播放器增强、智能队列、资料库面板、歌词页、主题面板和均衡器插件都可以仅靠上述 API 实现。
