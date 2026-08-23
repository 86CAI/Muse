package com.caipan.music.ui.theme

/**
 * 全局动效时长(ms)约定。
 *
 * 原则(依据 Apple 流体交互 + Emil 设计工程决策框架):
 * - 进入快、退出更快——退出必须短于进入(asymmetric enter/exit),系统响应要利落;
 * - 全屏 overlay 与列表页转场收敛到 300ms 内(UI 动画 <300ms);
 * - 播放页打开保留仪式感(500ms,Apple Music 展开本就有戏剧性),但关闭收窄到 280ms。
 *
 * MainScreen.kt 中硬编码的 tween(380/460/560)逐步迁移到本常量,配合 [MuseDesign] 的
 * DurationFast/Normal/Slow 使用。
 */
object MuseMotion {
    /** 全屏 overlay(scale+fade:均衡器/插件/关于/个人/UI设置/皮肤)进入 */
    const val EnterFull = 300
    /** 全屏 overlay 退出 */
    const val ExitFull = 200
    /** 矩形 reveal(本地歌曲/歌单列表/歌单详情/WebDAV)进入 */
    const val EnterReveal = 320
    /** 矩形 reveal 退出 */
    const val ExitReveal = 220
    /** 播放页圆形 reveal 打开(保留仪式感) */
    const val PlayerOpen = 500
    /** 播放页关闭 */
    const val PlayerClose = 280
    /** 在线搜索浮层(轻量,进入即快) */
    const val SearchEnter = 220
    /** 在线搜索浮层退出(快于进入) */
    const val SearchExit = 180
}
