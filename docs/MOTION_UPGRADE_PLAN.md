# Muse 动效升级方案(界面切换 / 加载 / 增加·修改·升级)

> 调研日期:2026-08-11 · 范围:`app/src/main/java/com/caipan/music/`
> 依据:代码实读 + 设计标准(Apple 流体交互「Designing Fluid Interfaces」+ Emil Kowalski 设计工程决策框架)
> 结论先行:**项目动效底子很好(液态 spring 控件族、播放页圆形 reveal、雨滴 shader 都是原创资产),但「页面转场」这一层是复制粘贴的重灾区、参数偏慢、无 reduced-motion、Loading 全转圈**。方案按 增加 / 修改 / 升级 三类给出,每条标注文件:行、现状、方案、参数、风险。

---

## 0.5 实施状态(2026-08-12)

| 项 | 状态 | 说明 |
|---|---|---|
| N1 转场封装层 | ✅ 已实施 | `MusePageTransition.kt`:`fullScreenOverlayEnter/Exit` + `Modifier.rectReveal`;MainScreen 4 处矩形 reveal + 6 处 overlay 收编 |
| M1 时长曲线统一 | ✅ 已实施 | `MuseMotion.kt` 常量;进入 300/320/500、退出 200/220/280;scale 起点 .92→.95;硬编码 tween(380/460/560) 清零 |
| U1 播放页 spring 化 | ✅ 已实施 | reveal 打开/关闭改 `spring(damping 1.0)`,可打断、从当前值平滑反向 |
| M3 搜索微调 | ✅ 已实施 | scale .98→.96,走 `SearchEnter 220 / SearchExit 180` 常量,退出快于进入 |
| U4 歌词高亮 spring | ✅ 已实施 | `LyricsView.kt` 高亮 alpha(damping .8)与 blur(damping 1.0)改 spring |
| N5 按压反馈 | ✅ 已实施 | `Modifier.pressScale()`(按下 100ms→0.97,抬起 spring);应用:songlistitem、PlaylistList、PlaylistDetail、homescreen×8、OnlineSearch |
| N2 骨架屏 | ✅ 已实施 | `Modifier.shimmer` + `SkeletonSongRows`;替换本地歌曲扫描、在线搜索、WebDAV 目录三处转圈 |
| N6 列表 stagger | ✅ 已实施 | `Modifier.staggeredEnter`(40ms 间隔,animatedKeys 防重放);应用本地歌曲文件夹行 + 歌曲行 |
| N3 图片过渡 | ⏳ 未做 | 下一批(以 AlbumArtwork 为模板统一 AsyncImage) |
| N4 reduced-motion | ⏳ 未做 | 下一批(系统 animatorDurationScale + 设置开关) |
| M2 设置 sheet spring | ⏳ 未做 | 下一批 |
| U2 Tab 视差 / U3 切歌 velocity / U5 黑胶 | ⏳ 未做 | 机动批 |

备份:`.ui-backup-20260812-motion-p0/ui/`(57 文件)。

---


## 0. 现状摘要(调研结论)

### 导航架构
- 无 Navigation Compose。单 Activity + `MainScreen.kt`(1651 行)为唯一路由宿主,全部页面以 `remember { mutableStateOf(Boolean/String?) }` 叠加,`Animatable` + `derivedStateOf` 手写驱动进度。
- 切换链:`MainActivity` → `SplashScreen`(alpha/scale)→ `MainScreen.kt:751` 的 Box 按 z-order 叠加:背景(壁纸/视频+雨滴)→ 主内容(Home/本地歌曲)→ 中间 overlay(均衡器/插件/搜索/歌单/个人/WebDAV)→ MiniPlayer → 全屏 PlayerScreen → Mineradio 歌词 → 插件 WebUI → 播放设置。
- 返回键:单个 `BackHandler`(MainScreen.kt:471-494)按优先级逐层弹出。

### 现有转场(全是手写、大量复制粘贴)
| 转场 | 参数 | 位置 | 问题 |
|---|---|---|---|
| 播放页圆形 reveal | 开 `tween(560)` / 关 `tween(380)`,FastOutSlowIn;origin=mini 封面中心 | MainScreen.kt:364/382/395 | 打开偏慢;纯脚本不可打断(有 job 取消保护) |
| 矩形 reveal(本地歌曲/歌单列表/歌单详情/WebDAV) | 开 `tween(460)` / 关 `tween(380)` | MainScreen.kt:406/416/429/436/449/459/466 + GenericShape 801-814/1146/1190/1299 | **4 处复制粘贴**,无封装 |
| 全屏 overlay scale+fade(均衡器/插件/关于/个人/UI设置/皮肤) | `scale(.92)+fade tween(380)` 进 / 同参数退 | MainScreen.kt:1033/1056/1127/1245/1375/1404 | **6 处同式复制**;进出等时长(退出应更快);380ms 偏慢 |
| 在线搜索 | fade 220 + scale(.98) 300 | MainScreen.kt:1106-1110 | 已较合理,微调即可 |
| 播放设置 sheet | slideIn 340 + fade 240 | MainScreen.kt:1631-1635 | 自定义,尚可 |
| 设置 ModalBottomSheet | 系统默认动画 | MainScreen.kt:501 | 与全 app 手感脱节 |
| 底部 Tab | AnimatedContent 方向感知 slide 250 FOS | homescreen.kt:133-158 | 方向逻辑正确,是亮点 |

### 加载现状
- **无骨架屏**:全 app `shimmer` 零匹配;Loading 全是 `CircularProgressIndicator`(扫描 MainScreen.kt:968-976、搜索 OnlineSearchScreen.kt:169-174、WebDAV 164-167、裁剪 ImageCropDialog 168-186)+ 插件安装 LinearProgressIndicator(PluginListScreen.kt:148-150)。
- 图片:Coil。仅 `songlistitem.kt:56-75 AlbumArtwork`(SubcomposeAsyncImage + crossfade + 双层防闪,实现优秀)、`OnlineSearchScreen.kt:378`、`PlaylistListScreen.kt:151` 有 crossfade;其余 AsyncImage 无占位/无过渡。

### 动效资产盘点(这些是「升级」的资本,不是推倒重来)
- **液态 spring 控件族**:`LiquidControlMotion.kt`(滑杆物理)、`MuseLiquidActions.kt`(按压 tanh 位移)、`MuseLiquidBottomTabs.kt`(胶囊 spring + 横滑 dragStretch)——参数从 `museglassconfig.kt` elasticity 推导,手感统一,是项目最接近 Apple 流体标准的部分。
- `RainDropsOverlay.kt`:AGSL RuntimeShader + withFrameNanos(API 33+)。
- `MuseGlass.kt`:kyant backdrop 静态材质(blur+vibrancy+lens+chromaticAberration+highlight),非动画。
- `FrostedGlass.kt`:Modifier.Node 实时模糊,仅 AppleMusicScreen 用(该屏是死代码)。

### 缺失项(负面结论)
- 无全局 spring 常量;MainScreen 硬编码 `tween(380/460/560)` 不走 `MuseDesign.kt:87-89` 时长常量。
- **无 reduced-motion 处理**(`reducedMotion|animatorDurationScale|DISABLE_ANIMATIONS` 全零匹配),UISettingsScreen 也无「减少动效」开关。
- 普通列表项/卡片点击无 press 缩放反馈(液态按钮有,列表项没有,不一致)。

---

## 1. 增加(New)——当前没有的动效能力

### N1. 统一转场封装层 `MusePageTransition`(新文件,一切后续修改的地基)
**现状**:MainScreen 10+ 处手写转场,4 处矩形 reveal(GenericShape 计算重复 4 次)+ 6 处 scale+fade 同式表达式。
**方案**:新建 `ui/components/MusePageTransition.kt`,提供 4 种枚举模式 + 1 个组件,把「进度 Animatable + 关闭回调 + 起点 bounds」收进去:

```kotlin
enum class PageTransitionMode { RevealRect, ScaleFade, SlideSheet, Crossfade }

@Composable
fun MusePageTransition(
    visible: Boolean, mode: PageTransitionMode, origin: Rect? = null,
    cornerRadius: Dp = 18.dp, onDismiss: () -> Unit, content: @Composable () -> Unit
)
```

- 内部持有单个 `Animatable(1f)`,打开/关闭统一走 `MuseMotion.open/close`(见 M1),对外只暴露 `visible`。
- RevealRect 的 GenericShape 只写一处(MainScreen.kt:801-814 收编)。
- **替换顺序**(降风险):先 4 处矩形 reveal → 再 6 处 scale+fade → 最后播放页(播放页有 playerIsClosing 特殊保护,单独处理,见 U1)。
- **风险**:MainScreen.kt 1651 行,一次全替换回归面大;建议一次替换一类、每类自测,保留 `.ui-backup-*` 备份习惯。
- **验收**:MainScreen.kt 内 `GenericShape` 出现次数 4 → 1,`scaleIn(initialScale = .92f` 出现次数 6 → 0。

### N2. 骨架屏系统(替换纯转圈)
**现状**:本地歌曲扫描、在线搜索、WebDAV 目录加载全是转圈。Emil 的「感知性能」原则:转圈让人感觉更慢。
**方案**:手写 shimmer Modifier(约 40 行,无需引库):

```kotlin
fun Modifier.shimmer(active: Boolean = true): Modifier
// rememberInfiniteTransition + Brush.linearGradient(0.15f alpha 高光) + graphicsLayer.translationX
```

落地点(按收益排序):
1. 本地歌曲列表加载(MainScreen.kt:968-976 的转圈区)→ 6-8 行列表形状骨架(头像圆 + 两行文字条)。
2. 在线搜索 Loading(OnlineSearchScreen.kt:169-174)→ 歌曲行骨架。
3. WebDAV 目录加载(164-167)、歌单详情,同款复用。
- **注意(Emil 决策框架)**:列表加载通常 <1s,骨架屏只应在「预计 >400ms」时出现,否则直接显示内容更优;本地扫描是全库级(可能数秒),骨架屏收益最大。
- **风险**:低。shimmer 是纯装饰,不阻塞交互;注意 `alpha` 高光幅度别超过 0.15(避免闪烁)。

### N3. 图片加载统一过渡 `FadeInArtwork`
**现状**:多数 AsyncImage 无占位/无过渡;只有 AlbumArtwork 双层防闪(crossfade)。
**方案**:以 `songlistitem.kt:56-75` 为模板抽公共组件 `FadeInArtwork(model, modifier, placeholder, error)`:
- 加载中:占位(黑胶 DefaultRecordArtwork 或纯色+icon),**不闪旧图**;
- 成功:`crossfade(true)` + 入场 `scale 0.97→1`(120ms ease-out)+ `blur(2px→0)` 收敛(Emil:blur 掩蔽 crossfade 的双图重叠感,blur < 20px);
- 失败:error 占位淡入。
- 落地:MainScreen.kt:606/674/759 壁纸、PlayerScreen.kt:620/628 封面、MiniPlayerBar.kt:125/319、PlaylistDetailScreen.kt:118/226、homescreen.kt:281 等 20 处统一替换。
- **风险**:低;注意 `SubcomposeAsyncImage` 的旧图保留逻辑(AlbumArtwork 已有)别丢。

### N4. 全局 reduced-motion(目前完全缺失,属基础能力缺口)
**现状**:零匹配,连设置开关都没有。
**方案**:
1. `MuseMotion` 提供 `val reducedMotion: Boolean`(读 `Settings.Global.ANIMATOR_DURATION_SCALE` + UISettingsScreen 新开关,二选一取 or);
2. 开启时:所有页面转场降级为 `Crossfade(150ms)`(苹果标准:reduced motion = 交叉淡入淡出,保留帮助理解的 opacity 变化);取消 spring 回弹、parallax、雨滴强度减半、shimmer 停用;
3. UISettingsScreen(22182 字节,设置面板已有「模糊/背景/风格」区)加「减少动效」项。
- **风险**:低,纯增量;注意雨滴 shader 归 RainDropsPlugin 管,reduced-motion 只降强度不断功能。

### N5. 统一按压反馈 `Modifier.pressScale()`
**现状**:液态按钮有 `LiquidPress`(MuseLiquidActions.kt:76-113),但普通列表项/卡片点击无 press 反馈,手感不一致。
**方案**:

```kotlin
fun Modifier.pressScale(active: Boolean = true): Modifier
// pointerInput + Animatable,按下 scale 0.97(tween 100ms ease-out),抬起回弹 spring(damping 1.0, response 0.3)
```

落地:`songlistitem.kt` 歌曲行、`homescreen.kt` 卡片/入口、`PlaylistDetailScreen.kt` 行、`PlaylistListScreen.kt` 卡片。数值对齐 Emil:100-160ms,scale 0.95-0.98。
- **风险**:低;注意不要盖住已有 `LiquidPress` 的组件(液态按钮已自带)。

### N6. 列表内容 stagger 入场
**现状**:homescreen.kt:57-65、PlaylistListScreen.kt:119-132、OnlineSearchScreen.kt:329-337 已有交错入场 spring,但本地歌曲/歌单详情列表没有。
**方案**:抽公共 `Modifier.staggeredEnter(index, delayMs=40)`(fade + 8dp 上移,spring damping 1.0),应用到本地歌曲列表与歌单详情。stagger 间隔 30-80ms(Emil),**绝不阻塞交互**(入场期间可点)。
- **风险**:低;列表 >20 项时 stagger 上限截断(第 20 项后同时进入),避免慢。

---

## 2. 修改(Modify)——现有动效的参数/机制修正

### M1. 全局时长曲线统一(MainScreen 硬编码清零)
**现状**:MainScreen.kt 硬编码 `tween(380/460/560)`,进出等时长;不走 `MuseDesign.kt:87-89`。
**方案**:新建 `MuseMotion` 常量(放 `ui/theme/MuseDesign.kt`):

```kotlin
// 进入 vs 退出:退出快于进入(Emil asymmetric enter/exit)
object MuseMotion {
    const val EnterFull = 300    // 全屏 overlay 进入
    const val ExitFull = 200     // 全屏 overlay 退出
    const val EnterReveal = 320  // 矩形 reveal 进入
    const val ExitReveal = 220   // 矩形 reveal 退出
    const val PlayerOpen = 500   // 播放页打开(保留戏剧感,560→500)
    const val PlayerClose = 280  // 播放页关闭(380→280)
}
```

依据:Emil 决策框架「UI 动画 <300ms、退出 < 进入」;Apple 表格 sheet/抽屉 response 0.3-0.4s。播放页 500ms 是「delight 级」例外(Apple Music 展开本就有仪式感),保留但收窄。
- scale 起点 `.92 → .95`(Emil:不要从接近 0 的 scale 起;0.95+opacity 更自然)。
- **落地**:替换 MainScreen.kt:364/382/395/406/416/429/436/449/459/466 及 6 处 AnimatedVisibility 的 380。
- **风险**:低(纯参数),但必须与 N1 封装同步做,否则改 10 处又制造新硬编码。

### M2. 设置 sheet 升级为 spring sheet
**现状**:`ModalBottomSheet`(MainScreen.kt:501)用系统默认动画,与全 app 液态手感脱节。
**方案**:保持 ModalBottomSheet 的拖拽语义,但转场动画自定义:
- `Modifier.animateItem`/自定义 enter:`slideInVertically(全高, spring(dampingRatio=0.8f, stiffness=StiffnessMedium))`(Apple 表格:drawer/sheet damping 0.8, response 0.3);
- 圆角 28dp 保持;背景 scrim 同步 fade(Apple:dim to focus)。
- **风险**:中低——ModalBottomSheet 的自定义转场 API 在 Compose M3 里用 `SheetState` + `AnimatedVisibility` 参数需小实验;若麻烦,退而求其次:仅替换为自定义 `AnimatedVisibility` + `slideInVertically` 的透明 sheet(保留拖拽用 anchor 逻辑)。

### M3. 在线搜索微调(低优先)
`fadeIn(220)+scaleIn(.98, 300)` 已合理;仅把 scale 起点 0.98 → 0.96、退出 `scaleOut(.98,240)` → `scaleOut(.96,180)`,与 M1 曲线对齐。

### M4. 歌单列表等「无 loading」场景补空态动效(低优先)
歌单列表无显式 loading(MainScreen.kt:345-346),空态「还没有歌单」直接出现。给空态图标加一次入场(fade + 轻微 scale 0.95→1)+ 可选 2s 周期的微弱呼吸,让「空」也有质感,但**不做**循环大动画(Emil:常驻页面禁晃眼)。

---

## 3. 升级(Upgrade)——把现有动效升级为「流体交互」

### U1. MiniPlayer → 全屏播放页:圆形 reveal 升级为「共享元素式」流体过渡(本次最高价值项)
**现状**:`openPlayer`(MainScreen.kt:374-386)用 `tween(560)` 纯脚本;`playerRevealOrigin` 取自 mini 封面中心;圆形 mask 展开 + 全屏 alpha。方向正确、但两点不足:①tween 不可被手势/快速连点平滑反转(现有 job 取消是硬切);②只有「圆形遮罩展开」,封面本身没有从 mini 位置「放大迁移」的 zoom 感,缺少 shared-element 的连续感。
**方案**(对齐 Apple:可打断、从当前值起步、速度传递):
1. **tween → spring**:`playerOpenProgress.animateTo(1f, spring(dampingRatio=1f, stiffness=StiffnessMedium))`(Apple PiP 移动参数 damping 1.0 / response 0.4)。spring 天然可打断——快速连点 mini 条时,动画从当前 progress 反走,不再硬切。
2. **封面 zoom 迁移**:reveal 期间封面层 `graphicsLayer { scaleX/scaleY = lerp(miniScale, 1f, progress) }`,`miniScale = miniArtworkBounds.width / screenWidth`;即封面从小图尺寸同步放大到全屏,与圆形 mask 展开同源(origin 一致)。标题/副标题同理:从 mini 条文本位置 `lerp` 上移淡出。
3. **关闭保持对称**:`dismissPlayer`(355-373)的 380ms tween → spring 同参;`playerIsClosing` 保护保留(这是目前打断安全的根基)。
4. **手势联动(可选 P2)**:播放页顶部下拉时 `dragY` 直接驱动 progress(1:1 跟手),松手按速度判关闭/回弹——这就是 Apple 的 velocity handoff,做成后播放页关闭会「粘手」。
- **风险**:中。spring 无固定时长,`showFullPlayer=false` 的时机必须放在动画结束回调里(现有代码已如此);`playerTransitionRunning` 防重入逻辑保留。建议先在 openPlayerDirect(无 origin 路径)上验证再动 openPlayer。
- **验收**:打开中途点 mini 条,播放页平滑反向收回(无跳变);连点 5 次不闪屏。

### U2. 底部 Tab:选中胶囊 spring 位置与内容滑动同源(视差)
**现状**:`MuseLiquidBottomTabs.kt:108-121` 胶囊 spring 位置动画,与 `homescreen.kt:133-158` 的 AnimatedContent 滑动**互相独立**,两者相位不同步,切换时「胶囊先到、内容后到」或反之。
**方案**:把 Tab 切换动画改为由同一 `Animatable` 驱动:
- 胶囊当前 index 的 spring 位移(已有)作为「锚点」;
- 内容滑动 `initialOffsetX = { it * 胶囊位移方向 }`——内容位移量 = 全宽 ×(胶囊位移/胶囊间距),即「胶囊走一半,内容走一半」,视觉上胶囊和页面是一体的(Apple 的 Control Center「朝手指方向生长」同思路);
- 参数:内容滑动从 tween 250 改为 `spring(damping 1.0, response 0.35)`(与胶囊 spring 同族,见 MuseDesign 常量)。
- **风险**:中。AnimatedContent 的 offset lambda 需要拿到胶囊进度,得把「胶囊位置 Animatable」提升到 HomeScreen 共享(现在在 MuseLiquidBottomTabs 内部);若耦合成本高,退化为「内容滑动用同 response 的 spring」也能消除相位差(改动小、效果 80%)。

### U3. PlayerScreen 封面横滑切歌:回弹 + 速度传递
**现状**:`dragX` 跟手 + 回弹 `tween(150)`(PlayerScreen.kt:498/552/576),跟手正确但回弹无速度感,快速甩动时松手即刹停。
**方案**:
1. 松手回弹:`Animatable.animateTo(0f, spring(dampingRatio=0.9f, stiffness=StiffnessMedium))`,初速度传 `velocityTracker` 的释放速度(Apple velocity handoff;Compose `Animatable.animateTo` 支持 `initialVelocity`——注意单位是 px/s 归一化问题,需 `dragVelocity / 屏幕宽` 换算成 progress 速度);
2. 封面层加轻微视差:拖动 100px 封面只移 60px(`dragX * 0.6f`),拖到底时露出下一张封面的边缘——Apple Music 的切歌预览感;
3. 阈值判定:位移 > 屏宽 1/4 **或** 甩动速度 > 阈值(Emil:velocity ≈ 0.11 的判据,单位换算后约 800px/s)即切歌。
- **风险**:中低。已有 dragX 骨架,主要是参数和初速度传递。

### U4. 歌词高亮滚动 spring 化
**现状**:`LyricsView.kt:86` 滚动已用 spring,但高亮 alpha 是 `animateFloatAsState`(132)默认 tween。
**方案**:高亮 alpha 换 `spring(dampingRatio=0.8f, stiffness=StiffnessMedium)`——跟随卡拉 OK 进度的行高亮会有轻微「呼吸感」,与歌词滚动 spring 同源;滚动定位用「速度感知」spring(当前行切换时,滚动距离大则速度自然更快)。
- **风险**:低。

### U5. 黑胶旋转细节(低优先)
MiniPlayerBar.kt:192-208 黑胶 `tween(9000, LinearEasing)` 无限旋转。打磨点:切歌/拖动时转速平滑变化(当前是状态切换硬切转速);可把旋转 speed 做成 `animateFloatAsState` 平滑过渡。纯细节,优先级最低。

---

## 4. 优先级与排期建议

| 优先级 | 项 | 工作量 | 理由 |
|---|---|---|---|
| **P0**(基础正确性) | N1 转场封装 + M1 时长曲线 + N5 按压反馈 + N4 reduced-motion | 1.5-2 天 | 消除复制粘贴与超时动画;reduced-motion 是能力缺口 |
| **P1**(感知提升) | N2 骨架屏 + N3 图片过渡 + N6 stagger + U1 播放页 spring 化 + M2 sheet spring | 2.5-3 天 | 加载感知与播放页手感是用户最高频接触点 |
| **P2**(锦上添花) | U2 Tab 视差 + U3 切歌 velocity + M3/M4 微调 + U4/U5 | 机动 | 低频打磨,穿插进行 |

**建议执行顺序**:N1 先于一切(N4 的降级逻辑也依赖它)→ N5/N6 顺手 → M1 与 N1 同步 → N2/N3 → U1(分两次:先 spring 化,再封面 zoom)→ U2/U3。

## 5. 关键风险与验收红线
1. **播放页 spring 化不可打断保护**:`playerIsClosing`/`playerTransitionRunning`/`playerTransitionJob`(MainScreen.kt:171-175)三件套必须保留,验收=连点与中途反开无跳变。
2. **N1 封装回归**:MainScreen 1651 行,逐步替换、每类自测;替换后 `GenericShape` 计数从 4→1。
3. **shimmer 不过度**:仅 >400ms 的加载显示;本地扫描外不做全屏骨架。
4. **reduced-motion 默认跟随系统**:不要默认开启(用户没提过,默认关,提供开关)。
5. 全部改动保留 `.ui-backup-*` 备份惯例;每次改动跑 `./gradlew :app:compileDebugKotlin` 验证编译。

## 6. 一句话总结
> **把「复制粘贴的 tween 脚本」升级成「一套 spring 驱动的统一转场层」,给加载加骨架屏与图片渐变,把播放页 reveal 做成可打断的共享元素迁移——其余液态控件资产(spring 滑杆、液态 Tab、雨滴)保持不动,它们是 Muse 的差异化优势。**
