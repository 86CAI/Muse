# Muse 外部插件示例

`reverse-shuffle` 是用于验证外部 JavaScript Hook 的最小插件。它在宿主生成随机队列时返回反转后的歌曲 ID 列表。

`lan-remote-webui` 是官方 LAN Remote 插件。它通过受限 `lan.*` Host API 完成服务启停、设备发现、一次性配对、授权撤销与远程播放控制；HTTP 服务、NSD 和认证令牌仍由 Muse 宿主管理。

## 包结构

`.museplugin` 是 ZIP 格式，必须包含：

- `plugin.json`
- `index.js`

声明 WebUI 时还可包含 `web/` 下的 HTML、JavaScript、CSS、图片和字体。`network-webui` 示例声明了 `api.github.com`，页面只能通过宿主消息桥访问该域名；WebView 不能直接联网。官方 LAN Remote 插件会在应用构建时自动打包并预装。

## 安装

1. 打开应用的“资料库 → 播放插件”。
2. 点击右上角导入按钮。
3. 选择 `reverse-shuffle.museplugin`。
4. 导入成功后核对插件信息并手动启用。
5. 关闭再开启随机播放，以触发 `onShuffle`。

外部插件运行在受限脚本与 WebUI 环境中。插件清单只代表权限申请，安装后默认不授权；请在插件页面逐项检查并只授予必要权限。即使如此，也不应导入来源不明的插件。
