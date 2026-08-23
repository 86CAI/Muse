# Muse UI 设计语言深度评审报告

> 调研对象：Muse（Android 本地音乐播放器，Kotlin + Jetpack Compose + Material 3），`app/src/main/java/com/caipan/music/ui/` 共 40 个文件、约 12246 行。
> 调研依据：Apple HIG + WWDC25 Liquid Glass 官方资料、Material Design 3、Fluent 2、Ant Design 5、GNOME HIG、NN/g（Nielsen 10 条启发式 / 动效时序 / Liquid Glass 批评）、Norman《设计心理学》、Fitts/Hick 定律、格式塔原则、W3C DTCG design tokens 规范、Brad Frost《Atomic Design》、Raycast/GitHub Primer/Radix 设计系统实践、优设网音乐 App 分析等（完整来源见文末）。
> 结论先行：**Muse 在"质感工程"上远超一般个人项目（实时玻璃材质、按压缩放反馈、背景亮度感知），但在"设计语言"层面是断裂的——有 token 却不用、有按钮体系却混用、有两套视觉却未完成、动效很炫但全部超时。它缺的不是审美，而是把决策收敛成一条可执行的规则。**

---

## 一、优秀 UI 设计语言的跨规范共识（调研结论）

把 Apple HIG、Material 3、Fluent 2、Ant Design、GNOME HIG、NN/g 论文、社区实践交叉对比，一套合格的 UI 设计语言由六块组成：

| # | 组成 | 共识要点 | 关键数字 |
|---|------|---------|---------|
| 1 | Design tokens | 颜色/间距/圆角/字号/动效全部 token 化，组件只消费语义层（DTCG 规范、Material tokens） | 间距 8dp（M3）/4px（Fluent）基准；圆角 4/6/8px（M3） |
| 2 | 层级表达 | 用布局分组而非装饰表达层级；z 轴（阴影/材质）只服务导航层；**禁止玻璃叠玻璃**（Liquid Glass 官方准则） | Elevation 静止态 ≤ +3，+4/+5 留给交互态 |
| 3 | 按钮分级 | 同屏一个主操作；Filled/Tonal/Outlined/Text 四级；危险操作红色且不默认推荐 | Ant：每组最多 1 个 Primary |
| 4 | 反馈分级 | 轻量反馈不打断（toast/snackbar 3s），重要失败必须用 Dialog；**破坏性操作优先用"撤销"而非确认框**（GNOME HIG 金句、启发式 #3/#5） | Ant：Message 3s；GNOME：取消在前 |
| 5 | 无障碍硬下限 | 触控目标 ≥ 48dp（Android）/44pt（Apple）；文本对比 ≥ 4.5:1；动效可关闭 | WCAG 2.5.8 24px 最低 / 2.5.5 44px 推荐 |
| 6 | 动效克制 | 时长 150–300ms（M3）/ 0.2–0.4s（Apple）；消失比出现快；**持续动画是"第 10 次就烦"的路障**（NN/g） | 按压反馈 ≤ 0.1s；全链 ≤ 400ms（Doherty） |

反面教材（2025 年最有信息量）：NN/g《Liquid Glass Is Cracked》批评 iOS 26 的四宗罪——**透明导致看不清、无意义动效、触控目标缩小、控件位置漂移**。Neumorphism 因低对比度+状态不可见被社区判死刑。这两件事对 Muse 尤其有参照价值，因为 Muse 正在走同一条"质感优先"的路。

---

## 二、合理之处（Muse 做得对的地方）

1. **背景亮度感知自适应文字色**（`MainScreen.kt:216-251`）：采样壁纸平均亮度，自动切换白/黑文字与半透明副文本。这正是 Liquid Glass"符号/字标随背景自动明暗翻转"的工程化实现，且比多数商业 App 认真——它直接服务于"玻璃上文字可读"这个 Liquid Glass 最大的坑。
2. **迷你播放条常驻**（`FloatingRecordPlayer`/`MiniPlayerBar`）：Apple HIG 明确把"media playback controls that stay visible across your app"列为 tab bar 的 accessory view 推荐做法，音乐 App 的核心交互（切歌/进度）不随页面切换消失，符合"主操作最短路径"。
3. **3 Tab 底部导航**（`homescreen.kt:97-103`：现在听/资料库/个人）：符合 Hick 定律 3–5 个、Jakob's Law 平台惯例；"现在听"作为主 Tab 符合 80/20 法则（播放是最高频功能）。
4. **Token 骨架存在**（`MuseDesign.kt`）：间距 4 的倍数、4 级圆角、动效 150/250/400ms、玻璃 alpha——符合 DTCG"原始层+语义层"雏形，方向正确（问题在执行，见三-1）。
5. **统一按钮封装与按压反馈**（`MuseLiquidActions.kt:115-183`）：MuseButton/Outlined/Text 三级封装共享 LiquidAction 玻璃材质；按下 tanh 位移+弹性回弹，按压即时反馈 <0.1s，符合 WWDC18"连续反馈/1:1 追踪"。
6. **路由从点击位置展开**（`MainScreen.kt:801-815` 等 4 处 `boundsInRoot()` 展开动画）：空间连续性——解释"元素从哪里来"，比普通 fade 高级，符合 NN/g 动效三作用之一。
7. **歌曲操作 Sheet 用 ModalBottomSheet + 危险项 error 色**（`songlistitem.kt:142-173`）：符合 Material 弹层惯例；删除类操作红色标识。
8. **空态设计达标**（`PlaylistListScreen.kt:93-110` 图标+两行引导、`PlaylistDetailScreen.kt:286-306` SearchOff/MusicOff 图标）：符合 Ant 空状态"说明原因+给建议动作"双目标。
9. **歌词视口中心自动高亮跟焦**（`LyricsView.kt:48-98`）：derivedStateOf 计算视口中心行 + animateScrollBy 跟焦，是"系统状态可见性"+"识别优于回忆"的教科书实现。
10. **播放页圆形 reveal 开/关成对**（`MainScreen.kt:374-399`、1543-1550）：进入/退出沿对称路径（WWDC18 空间一致性），不是散乱的各玩各的。
11. **edge-to-edge + 透明系统栏**（`Theme.kt:147-158`）：现代 Android 惯例，沉浸式方向正确。
12. **皮肤系统声明式 schema**（`MuseSkin.kt`：skin.json 声明 colors/radii/blur/layout/font）：本质是"把 tokens 打包成可分发资产"，方向与 DTCG/Style Dictionary 一致，且支持运行时热切换。

---

## 三、不合理之处

按严重度分为 A（设计语言断裂）/ B（导航与信息架构）/ C（弹窗与反馈）/ D（动效）/ E（无障碍）/ F（内容分布与工程卫生）。

### A. 设计语言层：tokens 形同虚设、组件双轨、体系未完成

**A1. Design token 大规模闲置，间距/字号系统实际不存在**（严重）
- `MuseDesign.kt` 定义了完整 token（Spacing4–48、RadiusCompact–Floating、MinTouch、TopBarHeight、FontDisplay–Micro、语义色 Success/Warning/Error），但：
  - **语义色与全部字号 token 零使用**；NeutralDark/Light 全系列零使用；
  - 间距 token 仅 homescreen 与 Theme 使用，其余 9+ 页面全部 `.dp` 字面量：`PlaybackSettingsSheet.kt` 内 `Spacer(14.dp)` 出现 8 次（115/165/201/228/267/316/349…），`PlaylistDetailScreen.kt` 用 18/20/24/28/90/170/230/280.dp 等 7 种离散值；
  - `MinTouch=48dp` 定义了却没人遵守（见 E1）。
- 违反：DTCG"单一事实来源"、格式塔接近性（间距不统一则分组关系混乱）、启发式 #4 一致性。
- 后果：**看似有设计系统，实际每页都是新的设计**；换肤/换主题时这些硬编码值全部漂移。

**A2. 按钮体系双轨：Muse 封装与原生 material3 混用**（严重）
- `MuseLiquidActions.kt:186-207` 定义了胶囊(50)玻璃按钮体系，但以下位置直接用原生按钮：
  - `OnlineSearchScreen.kt:181-190` 原生 `Button` + `RoundedCornerShape(8.dp)`——圆角语言完全不同（8dp vs 50 胶囊）；
  - `PlayerScreen.kt:233` 原生 IconButton（横屏）、`:314` 原生 TextButton；
  - `PlaylistDetailScreen.kt:188` 原生 IconButton（注释自认"普通 IconButton：无玻璃效果"）；
  - `songlistitem.kt:127`、`MiniPlayerBar.kt:144/148` 原生 IconButton。
- 违反：格式塔相似性（"同功能元素用同一样式"）、启发式 #4。同一屏内 MuseIconButton 与原生 IconButton 并存（PlayerScreen:226 vs 233）。
- 后果：玻璃质感时有时无，用户会以为玻璃=可点、无玻璃=不可点，signifier 体系崩溃。

**A3. 两套平行视觉体系未完成，且"Monet 动态取色"名不副实**（严重）
- `UiStyle.APPLE`（磨砂）与 `UiStyle.MONET`（液态玻璃折射）双体系并存，但：
  - `MusicPlayerTheme` 的 `dynamicColor` 在 `MainActivity.kt:28`、`MainScreen.kt:498` 均未传参，`Theme.kt:111-113` 的动态取色分支**永不触发**——所谓 "Monet 模式" 只切玻璃材质，与"动态取色"无关；
  - 死代码：`MonetPlayerScreen.kt`（仅 import 未调用）、`AppleMusicScreen.kt`（419 行无调用方）、homescreen.kt 的 `ProfileTab`(266-314)/`BrowseCard`(343)/`LibraryItem`(344)、`MuseBottomFrostSheet`（MuseLiquidBottomTabs.kt:304-329）。
- 违反：启发式 #4、"宁可砍掉也不留半成品"（Ant 克制价值观）。
- 后果：双倍维护成本 + 用户看到的"模式切换"其实只是材质切换，承诺与实现不符（错误的心智模型）。

**A4. 品牌色分裂：同项目三种强调色默认值**（中等）
- `0xFF1DB954`（绿）为 WebdavImportScreen:35、UISettingsScreen:31、EqualizerScreen:65、AboutScreen:46 默认值；
- `0xFFFA2D48`（红）为 PlaylistListScreen:50、PlaylistDetailScreen:75 默认值；
- `MuseDesign.Red = 0xFFFA2D55`（MuseDesign.kt:10）。
- 另：`WebdavImportScreen.kt:285-291` 的 `fieldColors` 硬编码 `0xFF1DB954`，与传入的 accentColor 脱钩。
- 违反：色彩系统一致性（Ant：功能色整套产品保持一致）；Material：语义色应来自 key color 派生。

**A5. "个人"页重复实现**（中等）
- Tab3 渲染 `ProfileVisuals`（ProfileCollage.kt:64，711 行）；另有全屏 `ProfileScreen`（184 行）从设置进入，两者功能高度重叠（头像、聆听时间、完整播放、循环时刻、私人曲库统计、改名对话框）。
- 违反：Atomic Design 单一职责、"组件谱系"；也是 A3 未完成体系的伴生症状。

### B. 导航与信息架构层

**B1. 布尔状态堆叠导航 + 巨型 MainScreen**（严重，工程根因）
- `MainScreen.kt` 1668 行，18 个布尔状态（166-187）+ 手写 18 分支 `BackHandler`（471-494）管理全部页面栈，无 Navigation 组件；`showHome`(277) 只赋值从未置 false，BackHandler 中 `!showHome` 分支是死逻辑。
- 违反：启发式 #3（用户控制与自由——返回栈一旦出错用户无法逃生）、Ant"确定性"、代码卫生。
- 后果：这是 A/B/C 几乎所有不一致的温床——没有统一的路由层，就没有统一的转场、返回、状态恢复。

**B2. 二级页转场违反平台惯例**（中等）
- 均衡器/插件/关于/UI 设置/皮肤/Profile 全部 `scaleIn(0.92)+fadeIn(tween(380))`（MainScreen:1033/1056/1127/1375/1404/1245），而非 Android/iOS 标准的水平推入（push）。
- 违反：Jakob's Law（用户预期二级页从右侧推入）、WWDC18 空间一致性（元素沿对称路径进出，而不是"弹出来"）。
- 说明：全屏播放器/路由展开的圆形 reveal 是"特殊页面特殊转场"的好用法，但**所有普通二级页共用弹跳放大**就把特殊变普通了——转场系统应当区分"沉浸式页面"与"普通页面"。

**B3. 滚动位置不保存**（中等）
- 仅 query/tab 用 rememberSaveable（homescreen:94/213、OnlineSearchScreen:94-98、PlaylistDetailScreen:103），列表滚动位置返回后全部重置。
- 违反：系统状态可见性/心智连续性——用户翻到第 50 首歌，误点一首返回后回到顶部。

### C. 弹窗与反馈层

**C1. PlaybackSettingsSheet 不是 BottomSheet**（中等）
- `PlaybackSettingsSheet.kt:57-86` 实际是自定义全屏 overlay（Box + BottomCenter + 全屏 scrim），与 MainScreen:1631 的 AnimatedVisibility 配合，和真正的 `ModalBottomSheet`（设置、歌曲操作）动画语言完全不同。
- 违反：signifier（名字与行为不符）、启发式 #4——用户会以为它是可下滑关闭的 sheet，实际只能点 scrim 关。

**C2. Toast 遍地、零 Snackbar、破坏性操作无撤销**（中等）
- 全项目无 `SnackbarHost`；反馈全用 Toast：MainScreen:1100/1428/1591-1596/1666、AboutScreen:61、PluginListScreen:110、SkinSettingsScreen:58。
- 删除歌单/歌曲走确认 Dialog（合理），但确认后无撤销路径（GNOME HIG 金句："破坏性操作优先用撤销而非确认框"；启发式 #3）。
- 违反：Ant 反馈分级（重要失败必须 Dialog——播放失败用了 Dialog，这点对；但成功/轻量反馈应可带动作，Snackbar 比 Toast 多一个"撤销"机会）。

**C3. 同一 Dialog 组件四种样式**（中等）
- `MuseAlertDialog` 传入不同容器/圆角：透明+28dp（MainScreen:1368-1370）、surfaceContainerHighest+24dp（ProfileScreen:138、EqualizerScreen:214）、surfaceContainerHigh+extraLarge（PlaylistListScreen:197）、默认 30dp（MuseLiquidActions:275）。
- 违反：对话框也是组件——组件在不同调用点应保持同一解剖结构（Apple"共享 anatomy"）。

**C4. 菜单极简是优点，但溢出点过少**（轻）
- 全项目仅 2 处 DropdownMenu（背景选择、歌单更多），无长按菜单（无 combinedClickable）、无溢出菜单——这对 3 Tab 的 App 是合理的；但"歌曲详细信息"被埋在操作 sheet 二级 Dialog（songlistitem.kt:168→174），识别性弱于直接可见。

### D. 动效层：方向对，全部超时

**D1. 动效时长普遍超规范**（严重）
- 二级页 scaleIn 380ms、路由展开 460ms/收起 380ms、播放器 reveal 打开 560ms/关闭 380ms（MainScreen:364-399、406/416、429/436、459/466）——全部超过 Material 150-300ms 与 Apple 0.2-0.4s 区间。
- 违反：Doherty 阈值（<400ms 保持心流）、Ant"消失比出现快"（Muse 是出现慢、消失 380ms 也不快）。
- 注意：Apple 0.2-0.4s 建议是针对"过渡动画"；播放器圆形 reveal 属"沉浸式转场"，560ms 可辩护，但**所有普通二级页 380ms 弹跳是不可辩护的**。

**D2. 持续动画未考虑"第 10 次就烦"**（中等）
- 黑胶旋转 9000ms 匀速（MiniPlayerBar:206-209）、呼吸 2000ms 往复（PlayerScreen:181-190）、Splash 脉冲 1400ms、雨滴实时 shader（RainDropsOverlay，withFrameNanos 每帧更新）。
- 违反：NN/g"重复出现的动效是路障"；**没有任何证据表明适配系统"减少动画"（Reduce Motion）设置**——Apple 明确要求，Android 也推荐检查 Settings.Global.ANIMATOR_DURATION_SCALE。
- 建议：持续装饰动画（黑胶/雨滴/呼吸）应提供开关或跟随系统减弱动态。

**D3. 同一"交错淡入"逻辑三处重复、参数各异**（轻）
- homescreen `staggeredEnterModifier`(55-69) 40ms、PlaylistListScreen(119-126) 45ms、OnlineSearchScreen(331-338) 30ms——同一语义三种节奏。应抽成共享 token（Material：duration/easing 也是 token）。

### E. 无障碍层：触控目标与对比度不达标

**E1. 触控目标普遍 < 48dp**（严重，且与自建 token 矛盾）
- `MuseButton` `defaultMinSize(minHeight=44.dp)`（MuseLiquidActions.kt:175）低于 Material 48dp；
- MuseGlassSwitch 拇指 40×24dp（MuseGlassControls.kt:134）、MuseGlassRadioButton 30dp（:233）、MuseGlassSlider 拇指 40×24（:214）；
- 关闭按钮 40dp（PlaybackSettingsSheet:127）、预设删除 40dp（EqualizerScreen:152）、裁剪缩放 44dp（ImageCropDialog:215）、随机播放 46dp（PlaylistDetailScreen:260）、迷你条播放按钮 40dp（MiniPlayerBar:346）。
- 讽刺点：`MuseDesign.MinTouch = 48.dp` 明明白白定义了，却没人在用。
- 违反：Fitts 定律 + WCAG 2.5.8（24px 最低/44px 推荐）+ Material 48dp。

**E2. 玻璃上的文本对比度是最大隐患**（严重）
- 玻璃/透明容器组合（ModalBottomSheet 容器透明 + scrim 黑 .18，songlistitem.kt:144-147；设置 sheet 同构，MainScreen:502-505）+ 半透明白文本：
  - `AppleMusicScreen.kt:122` `White.copy(alpha=0.4f)` 在亮背景下对比度不足；
  - `PlaylistDetailScreen.kt:99` `White.copy(.72)/Black.copy(.62)`；
  - MainScreen 设置页硬编码 `Color(0xFF888888)`（528/543 等）不随背景亮度变化；
  - ProfileScreen 玻璃卡内 `White.copy(.12)` 分隔线在浅色皮肤下几乎不可见（:93）。
- 这正是 NN/g 批 iOS 26 Liquid Glass 的头号罪名（透明=难看清）。Muse 把玻璃铺到了**按钮、卡片、弹窗、全屏路由、列表项**所有层级，glass-on-glass 场景大量存在，而 Liquid Glass 官方准则明令"只用于导航层、禁止玻璃叠玻璃"。
- 违反：WCAG 1.4.3（4.5:1）+ Liquid Glass 官方准则 + Neumorphism 教训（低对比度美学上线前必须跑对比度测试）。

**E3. 少数语义缺失**（轻）
- `PlaylistDetailScreen.kt:243` 换封面角标是纯 `Text("✎")` 无语义；`:260`、`MiniPlayerBar.kt:346` 可点击 Box 无 semantics，仅靠内部 Icon 描述。大部分 contentDescription 良好（返回/播放/暂停均带）。

### F. 内容分布与工程卫生

**F1. 列表"行"四套视觉，信息不对称**（中等）
- SongListItem：48dp 封面圆角10、无时长（songlistitem.kt:89-130）；歌单卡片：64dp 渐变封面圆角16+玻璃卡（PlaylistListScreen:134-141）；歌单详情行：无封面只编号（PlaylistDetailScreen:313-337）；WebDAV 行：36dp 圆角8（WebdavImportScreen:196）。
- 同一语义（列表条目）四种样式，违反格式塔相似性；且 SongListItem 无时长显示，而 OnlineTrackRow 有（OnlineSearchScreen:410-416）——同一 App 内同种数据展示不一致，违反"识别优于回忆"。

**F2. i18n 三套并存**（轻）
- homescreen 硬编码中文；Equalizer/About 用 `Strings(isZh)`；PlaybackSettingsSheet/PlaylistList/Profile 全中文；设置页靠 `currentLang` 三元（MainScreen:521 等）。

**F3. 死代码与垃圾文件**（轻）
- `homescreen.kt.bak`（227 行旧代码）残留在源码目录；MonetPlayerScreen/AppleMusicScreen/ProfileTab/BrowseCard/LibraryItem/MuseBottomFrostSheet 均无调用方；时间格式化重复实现（AppleMusicScreen.formatTime:404-409 vs PlayerScreen.formatPlayerTime:666-672）。

---

## 四、优先修复建议（按投入产出比）

1. **先做"唯一主操作 + 按钮收敛"**：把 OnlineSearch/Player/PlaylistDetail 等处的原生按钮全部换回 Muse 封装（或反之），一处失败就全删玻璃——半吊子比没有更糟（A2）。
2. **把 token 用起来，而不是新建 token**：给所有硬编码间距/圆角/字号过一遍 MuseDesign 映射（14.dp→Spacing16 或新增 Spacing14，18/20/24/28→现有 4 的倍数），删除零使用 token（A1）。
3. **关掉或完成 Monet 线**：要么真开 dynamicColor 兑现"动态取色"承诺，要么把 UiStyle.MONET 降级为纯材质开关并删掉死代码（A3）。
4. **触控目标补到 48dp**：MuseButton minHeight 44→48，玻璃控件拇指热区用 padding 扩容（E1）——这是 Fitts 硬下限，也是所有规范共识。
5. **动效减半**：普通二级页 380ms→250ms，路由 460→300，播放器 reveal 保留 560 但支持跳过；给持续动画（雨滴/黑胶）加系统减弱动态适配（D1/D2）。
6. **导航重构**（长期）：把 18 个布尔状态收敛进 Navigation Compose 或一个 sealed route 栈，BackHandler 由系统管理（B1）。
7. **反馈升级**：Snackbar 带"撤销"替换部分 Toast；PlaybackSettingsSheet 改真 ModalBottomSheet 或改名（C1/C2）。

---

## 五、调研来源（节选，全部可复核）

- Apple HIG：https://developer.apple.com/design/human-interface-guidelines/ ；WWDC25 "Meet Liquid Glass"：https://developer.apple.com/videos/play/wwdc2025/219/ ；"Get to know the new design system"：https://developer.apple.com/videos/play/wwdc2025/356/
- Material 3（官方组件文档）：https://github.com/material-components/material-web/blob/main/docs/components/button.md （dialog/elevation 同仓库）
- Fluent 2：https://fluent2.microsoft.design/layout 、/color 、/elevation 、/design-tokens
- Ant Design 5：https://ant.design/docs/spec/values 、/buttons 、/feedback 、/research-empty 、/colors 、/layout 、/motion
- GNOME HIG：https://developer.gnome.org/hig/principles.html 、/patterns/feedback/dialogs.html
- NN/g：10 条启发式 https://www.nngroup.com/articles/ten-usability-heuristics/ ；Fitts https://www.nngroup.com/articles/fitts-law/ ；Touch target https://www.nngroup.com/articles/touch-target-size/ ；动效 https://www.nngroup.com/articles/animation-usability/ ；Liquid Glass 批评 https://www.nngroup.com/articles/liquid-glass/ ；Aesthetic-Usability https://www.nngroup.com/articles/aesthetic-usability-effect/
- Norman：https://jnd.org/signifiers-not-affordances/ ；LawsofUX（Fitts/Hick/Jakob's/Doherty/Paradox）：https://lawsofux.com/
- WWDC18 Designing Fluid Interfaces：https://asciiwwdc.com/2018/sessions/803
- W3C DTCG Design Tokens：https://tr.designtokens.org/format/ ；Style Dictionary：https://styledictionary.com/
- Atomic Design：https://atomicdesign.bradfrost.com/chapter-2/
- Raycast：https://www.raycast.com/blog/a-fresh-look-and-feel ；GitHub Primer：https://primer.style/ ；Radix：https://www.radix-ui.com/primitives
- 玻璃拟态/Neumorphism 批评：https://webflow.com/blog/glassmorphism 、https://webflow.com/blog/neumorphism
- TidBITS（Liquid Glass 可读性自救）：https://tidbits.com/2025/10/09/how-to-turn-liquid-glass-into-a-solid-interface/
- 音乐 App：优设网 https://www.uisdc.com/music-production-details 、https://www.uisdc.com/netease-cloud-music
- 开发者视角批评：https://invertedpassion.com/why-is-enterprise-software-ugly/
- WCAG：https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html 、https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html
