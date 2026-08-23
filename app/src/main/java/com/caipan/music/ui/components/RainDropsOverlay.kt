/*
 * 雨滴玻璃叠层（AGSL）。
 *
 * 分层算法 adapted from `Lavender-z/demo` 的 WebGL/GLSL 雨滴效果（Curtains.js 驱动）：
 * Hash/Rotate/EllipseMask/DropCell/ImpactLayer 的分层思路来自该实现，
 * Muse 改为局部水滴折射、不做全屏模糊，并以 AGSL 重写。
 *
 * 该上游项目的许可证尚未确认；若原作者有署名或移除要求，请通过仓库 Issues 联系，
 * 我们会立即补充署名或替换该实现。详见 THIRD_PARTY_NOTICES.md。
 */
package com.caipan.music.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.IntSize
import com.caipan.music.plugin.FrameRateScene
import com.caipan.music.ui.effects.rememberSceneFrameThrottle
import kotlinx.coroutines.isActive

/**
 * 把雨玻璃效果应用到调用者提供的背景内容。
 *
 * 重要:这个组件不创建 Dialog、SurfaceView 或 PixelCopy 快照,也不覆盖前景控件。
 * RuntimeShader 的 inputShader 直接读取当前背景 Box 的内容,因此没有自反馈和闪屏。
 * API 33 以下保持原始背景不变。
 */
@Composable
fun RainGlassBackground(
    intensity: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (Build.VERSION.SDK_INT < 33 || intensity <= 0f) {
        Box(modifier) { content() }
        return
    }

    val shader = remember {
        try {
            RuntimeShader(AGSL_RAIN_GLASS)
        } catch (e: Throwable) {
            Log.e(TAG, "复杂雨滴 AGSL 编译失败，回退到最小透传 shader", e)
            try {
                RuntimeShader(AGSL_FALLBACK)
            } catch (e2: Throwable) {
                Log.e(TAG, "最小 shader 也失败，雨滴层停用", e2)
                null
            }
        }
    }
    if (shader == null) {
        Box(modifier) { content() }
        return
    }

    // 帧率节流：雨滴是慢速效果，按「雨滴」场景的帧率上限节流，减少 GPU 负载。
    // 用帧时间戳换算真实秒数，消除帧率依赖（60/90/120Hz 屏幕行为一致）。
    val startNanos = remember { System.nanoTime() }
    val frameNanosState = remember { mutableLongStateOf(System.nanoTime()) }
    val throttle = rememberSceneFrameThrottle(FrameRateScene.RAIN)

    LaunchedEffect(shader, intensity, throttle) {
        shader.setFloatUniform("uDist", lerp(4.0f, 8.0f, intensity))
        shader.setFloatUniform("uIntensity", intensity)
        while (isActive) withFrameNanos { nanos ->
            if (throttle.shouldUpdate(nanos)) {
                frameNanosState.longValue = nanos
            }
        }
    }

    // RenderEffect 只在 shader 实例创建时构建一次并复用；uniform（uTime/uReso）每帧更新
    // 不需要重建 effect，避免每帧分配 RenderEffect 并触发 shader 重链接。
    val rainEffect = remember(shader) {
        try {
            RenderEffect
                .createRuntimeShaderEffect(shader, "inputShader")
                .asComposeRenderEffect()
        } catch (e: Throwable) {
            Log.e(TAG, "创建 RenderEffect 失败，雨滴层停用", e)
            null
        }
    }

    Box(
        modifier.then(
            RainGlassNodeElement(shader, rainEffect, frameNanosState, startNanos)
        )
    ) { content() }
}

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/** 最小合法 AGSL：只透传背景；当复杂雨滴 shader 编译失败时回退使用。 */
private const val AGSL_FALLBACK = """
uniform shader inputShader;
half4 main(float2 fragCoord) {
    return inputShader.eval(fragCoord);
}
"""

private const val TAG = "MuseRainDrops"

// ============================================================================
// 性能优化配置
// ============================================================================

/**
 * 降采样系数：2 = 半分辨率渲染（像素数减 4 倍），1 = 关闭降采样。
 * 缩放在 Compose 端用 GraphicsLayer 手动完成，shader 内 uReso 随之缩小。
 */
private const val RAIN_DOWNSCALE = 2

// ============================================================================
// 降采样渲染节点：用半尺寸离屏 GraphicsLayer + RenderEffect 实现，
// 避免全屏逐像素重算 4 倍像素。与 com.kyant.backdrop 的 layerBackdrop 机制相同。
// ============================================================================

private class RainGlassNode(
    var shader: RuntimeShader,
    var effect: androidx.compose.ui.graphics.RenderEffect?,
    var frameNanosState: MutableLongState,
    var startNanos: Long
) : Modifier.Node(), DrawModifierNode {

    private var fullLayer: GraphicsLayer? = null
    private var smallLayer: GraphicsLayer? = null

    override fun ContentDrawScope.draw() {
        val e = effect
        if (e == null) {
            drawContent()
            return
        }
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) {
            drawContent()
            return
        }

        val ctx = requireGraphicsContext()
        val full = fullLayer ?: ctx.createGraphicsLayer().also { fullLayer = it }
        val small = smallLayer ?: ctx.createGraphicsLayer().also { smallLayer = it }

        // 更新 uniform
        shader.setFloatUniform(
            "uTime",
            ((frameNanosState.longValue - startNanos) / 1_000_000_000.0).toFloat()
        )
        val sw = (w / RAIN_DOWNSCALE).toInt().coerceAtLeast(1)
        val sh = (h / RAIN_DOWNSCALE).toInt().coerceAtLeast(1)
        shader.setFloatUniform("uResoW", sw.toFloat())
        shader.setFloatUniform("uResoH", sh.toFloat())

        // 1. 录制 content 到全尺寸 layer
        full.record { this@draw.drawContent() }

        // 2. 降采样：以 half 尺寸录制到小 layer（canvas.scale 默认以原点为 pivot，即左上对齐）
        val inv = 1f / RAIN_DOWNSCALE
        small.record(size = IntSize(width = sw, height = sh)) {
            drawContext.canvas.scale(inv, inv)
            drawLayer(full)
        }

        // 3. 应用雨滴效果到小 layer（RenderEffect 的 inputShader 读小 layer 的内容）
        small.renderEffect = e

        // 4. 放大绘制回屏幕
        drawContext.canvas.save()
        drawContext.canvas.scale(RAIN_DOWNSCALE.toFloat(), RAIN_DOWNSCALE.toFloat())
        drawLayer(small)
        drawContext.canvas.restore()
    }

    override fun onDetach() {
        fullLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        smallLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        fullLayer = null
        smallLayer = null
    }
}

private data class RainGlassNodeElement(
    val shader: RuntimeShader,
    val effect: androidx.compose.ui.graphics.RenderEffect?,
    val frameNanosState: MutableLongState,
    val startNanos: Long
) : ModifierNodeElement<RainGlassNode>() {
    override fun create(): RainGlassNode =
        RainGlassNode(shader, effect, frameNanosState, startNanos)

    override fun update(node: RainGlassNode) {
        node.shader = shader
        node.effect = effect
        node.frameNanosState = frameNanosState
        node.startNanos = startNanos
    }
}

// ============================================================================
// AGSL 着色器（雨滴玻璃效果）
// ============================================================================

/** Lavender-z/demo 的 Layer 算法,但合成改为局部水滴折射,不做全屏模糊。 */
private const val AGSL_RAIN_GLASS = """
uniform shader inputShader;
uniform float uTime;
uniform float uDist;
uniform float uIntensity;
uniform float uResoW;
uniform float uResoH;

// 每个网格使用独立 seed 决定形状、尺度、旋转、位置和运动，避免复制同一颗水滴。
float Hash12(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

vec2 Hash22(vec2 p) {
    return vec2(Hash12(p), Hash12(p + vec2(41.37, 13.71)));
}

vec2 Rotate(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, -s, s, c) * p;
}

// 返回椭圆内的柔和 mask。边缘收窄到 0.90-1.0（约1-2px），
// 避免软边过宽把 Fresnel 环糊成宽带，产生"脏水印"感。
float EllipseMask(vec2 p, vec2 axes) {
    float d = length(p / axes);
    return 1.0 - smoothstep(0.90, 1.0, d);
}

// 单格水滴场。返回 vec2(mask, sizeFactor)：sizeFactor 供折射/高光按滴尺寸调制。
// 大雨滴（等效 3mm 级）用更大的 scale，其球冠更厚 → 折射更强。
vec2 DropCell(vec2 uv, vec2 grid, float layerSeed, float time) {
    vec2 cellUv = uv * grid;
    vec2 id = floor(cellUv);
    vec2 local = fract(cellUv) - 0.5;
    float seed = Hash12(id + layerSeed);

    // 不是每个格子都放滴：近层约 45%，远层约 35%，留下大块无水区域。
    // 强度真正控制附着滴密度：小雨稀疏，暴雨才填满更多格子。
    // 底部聚集：重力使下部滴更密更大（uv.y 向上，底部≈0）。
    float bottomGather = mix(0.75, 1.25, 1.0 - uv.y);
    float exists = step(mix(0.75, 0.30, pow(uIntensity, 0.72)) * bottomGather, seed);
    if (exists < 0.5) return vec2(0.0, 0.0);

    vec2 random = Hash22(id + layerSeed + 9.13);
    float type = Hash12(id + layerSeed + 28.54);
    // 尺寸偏斜分布：多数小滴，少数大雨滴（scale>1 即等效 3mm 级）。
    // 大雨滴球冠更厚、折射更强，但仍是"附着水滴"，不能无限大。
    float sizeHash = Hash12(id + layerSeed + 6.17);
    float bottomScale = mix(0.8, 1.3, 1.0 - uv.y);
    float scale = sizeHash < 0.88
        ? mix(0.42, 0.85, pow(sizeHash / 0.88, 1.6)) * bottomScale
        : mix(1.05, 1.55, Hash12(id + layerSeed + 6.18)) * bottomScale;
    float angle = (random.x - 0.5) * 1.25;

    // 附着滴被接触角滞后钉扎在玻璃上，物理上是静止的——不漂移、不游动
    // （任何持续漂移看起来都像虫子爬）。下滑运动全部交给 FallingDrops。
    vec2 centre = (random - 0.5) * vec2(0.34, 0.42);
    vec2 p = Rotate(local - centre, angle);

    float drop = 0.0;
    if (type < 0.25) {
        // 近圆静止水滴。
        drop = EllipseMask(p, vec2(0.047, 0.047) * scale);
    } else if (type < 0.48) {
        // 受重力拉长的倾斜椭圆滴。
        drop = EllipseMask(p, vec2(0.035, 0.070) * scale);
    } else if (type < 0.70) {
        // 饱满水滴：圆润球冠 + 短粗尾（真实窗上水滴是扁球冠，不是细尖滴管）。
        float body = EllipseMask(p - vec2(0.0, 0.010 * scale), vec2(0.046, 0.058) * scale);
        vec2 tailP = p + vec2(0.0, 0.030 * scale);
        float tail = EllipseMask(tailP, vec2(0.030, 0.040) * scale);
        tail *= smoothstep(0.045 * scale, -0.010 * scale, tailP.y);
        drop = max(body, tail);
    } else if (type < 0.87) {
        // 合并滴：两个不同半径、略错位的滴珠融合，形成花生/葫芦而不是复制圆。
        float a = EllipseMask(p + vec2(0.055, 0.018) * scale, vec2(0.039, 0.047) * scale);
        float b = EllipseMask(p - vec2(0.060, 0.025) * scale, vec2(0.028, 0.036) * scale);
        drop = max(a, b);
    } else {
        // 滑落滴：饱满大滴 + 宽扁水痕（水痕比滴径略窄，不是细针）。
        float head = EllipseMask(p - vec2(0.0, 0.020 * scale), vec2(0.045, 0.065) * scale);
        vec2 streakP = p + vec2(0.0, 0.060 * scale);
        float streak = EllipseMask(streakP, vec2(0.026, 0.060) * scale);
        // 真实水痕沿长度平滑收尾变细，不叠高频正弦条纹（那是脏水痕来源）。
        streak *= smoothstep(0.070 * scale, -0.010 * scale, streakP.y);
        drop = max(head, streak);
    }
    return vec2(drop * exists, scale);
}

// 全局下滑雨道：以横向 lane 固定 seed，而非格子固定 seed，水滴能连续穿过整屏。
// 给定世界 y 的雨道中心线。玻璃微观不均匀仅造成小幅横向摆动，重力主轴保持竖直。
float RainPathX(float y, float laneX, float bendPhase, float drive) {
    return laneX + sin(y * 17.0 + bendPhase) * (0.003 + 0.008 * drive);
}

// 事件驱动滑滴。无 history texture 时不能做真实粒子状态，
// 但可从 (lane, eventId, time) 重建稳定且不重复的雨滴生命周期。
// stick-slip：接触角滞后钉扎 → 重力积累 → 突进一小段 → 再钉住。
// 位移必须单调不减（钉住=平台、突进=上升），绝不回退。
// 旧写法 speed*(t*0.4 + pulse*0.15) 里 pulse 是单峰脉冲：脉冲结束位移从峰值掉回基线，
// 每个周期水滴向上回跳一大段（= 用户看到的"向上跑"）。改为按周期累积。
float StickSlip(float t, float speed, float phaseShift) {
    float cycles = t * 4.0 + phaseShift;
    float fullCycles = floor(cycles);
    float phase = fract(cycles);
    // 当前周期内：前 30% 突进（0→1 单调），之后钉住保持。
    float slip = smoothstep(0.0, 0.30, phase);
    // 已完成周期数 + 当前进度 → 位移单调不减。系数 0.1125 使平均速度≈旧版 0.45*speed。
    return speed * (fullCycles + slip) * 0.1125;
}

vec2 FallingDrops(vec2 uv, float time) {
    float laneCount = floor(mix(14.0, 48.0, pow(uIntensity, 0.78)));
    float laneId = floor(uv.x * laneCount);
    // 事件长度拉长且仅在最后一小段淡出；避免短周期重置造成肉眼闪烁。
    // 事件周期必须匹配滑完整屏的时间：速度 0.008-0.034 屏高/s 时需 30-130s。
    // 之前 5s 周期导致滴只走 0.1 屏高就被重置，看起来"只在顶部出现就消失"。
    float eventDuration = 60.0;
    // lane 相位错开：每个 lane 的起点不同，避免所有雨道同步"定时撒"。
    float lanePhase = Hash12(vec2(laneId, 201.7));
    float eventT = fract(time / eventDuration + lanePhase);
    float eventId = floor(time / eventDuration + lanePhase);
    vec2 eventKey = vec2(laneId + 17.0, eventId * 13.7 + 91.2);

    // 每次重生都重抽：半径采用偏斜分布（多数小滴、少数大滴），
    // pinning 模拟接触角滞后/表面缺陷，决定这滴是否达到起滑阈值。
    // uv 单位需按屏高换算：1920px 屏上半径 0.005≈10px。
    // 滑滴主体半径约 3-10px，合并后最大约 13px，才是雨打玻璃而非水蛭。
    float radius = mix(0.0040, 0.0120, pow(Hash12(eventKey + 1.3), 2.35));
    // 钉扎门槛降低：保持"小滴钉住、大滴滑动"的规律，但让约一半滑滴事件能启动，
    // 避免满屏静态滴+只有零星几颗在动。
    float pinning = mix(0.012, 0.085, Hash12(eventKey + 5.7));
    float bo = radius * radius * 12000.0;
    float drive = clamp((bo - pinning) / 0.15, 0.0, 1.0);
    if (drive < 0.012) return vec2(0.0, 0.0); // 被钉扎的小滴保持静止，由静态层承担。

    // 尺寸越大、越超过临界体积才越快；上限压到视觉上有重量而非滚屏。
    // 约 15-70 px/s（以常见 1600-2000px 高屏幕计），让雨滴有重量感而不是滚屏。
    // 物理速度：真实垂直玻璃上 mm 级滑滴 1-20mm/s ≈ 30-70px/s @1920px。
    // 速度随体积更强增长，小滴基本钉住不动。
    float speed0 = (0.008 + 0.034 * pow(drive, 1.3)) * mix(0.7, 1.0, uIntensity);
    float startDelay = mix(0.03, 0.24, Hash12(eventKey + 7.9));
    float activeT = clamp((eventT - startDelay) / (1.0 - startDelay), 0.0, 1.0);
    float laneX = (laneId + 0.5) / laneCount
        + (Hash12(eventKey + 11.1) - 0.5) * 0.12;
    float startY = 1.10 + Hash12(eventKey + 15.8) * 0.16;

    // 预排一次吞并：主滴遇到一颗钉扎小滴，半径按 r³ 合并，速度随之增加。
    float mergeT = mix(0.30, 0.72, Hash12(eventKey + 19.4));
    // 猎物必须真是小滴；合并后按像素 cap 截断，禁止再次生成巨滴。
    float radiusCap = min(16.0 / uResoH, 0.009 * min(uResoW, uResoH) / uResoH);
    float preyRadius = min(mix(0.0007, 0.0025, Hash12(eventKey + 23.6)), radiusCap * 0.55);
    float mergedRadius = min(radiusCap, pow(radius * radius * radius + preyRadius * preyRadius * preyRadius, 1.0 / 3.0));
    float mergedBo = mergedRadius * mergedRadius * 12000.0;
    float mergedDrive = clamp((mergedBo - pinning) / 0.15, 0.0, 1.0);
    float speed1 = (0.008 + 0.034 * pow(mergedDrive, 1.3)) * mix(0.7, 1.0, uIntensity);
    float phaseShift = Hash12(eventKey + 41.3);
    float beforeMerge = StickSlip(min(activeT, mergeT), speed0, phaseShift);
    float afterMerge = StickSlip(max(activeT - mergeT, 0.0), speed1, phaseShift + 0.5);
    float headY = startY - beforeMerge * eventDuration - afterMerge * eventDuration;
    float mergeY = startY - StickSlip(mergeT, speed0, phaseShift) * eventDuration;
    float merge = smoothstep(mergeT - 0.025, mergeT + 0.035, activeT);
    float currentRadius = mix(radius, mergedRadius, merge);

    // 轻微横向蛇形来自玻璃微观不均匀，不做任意旋转，重力方向保持竖直。
    float bendPhase = Hash12(eventKey + 28.2) * 6.28318;
    float headX = RainPathX(headY, laneX, bendPhase, drive);
    vec2 q = uv - vec2(headX, headY);

    // 动态接触角的不对称：下方(运动前沿)宽而饱满，上方(后沿)颈缩、拉细。
    float fallingStretch = 1.0 + drive * (0.35 + 0.75 * sin(activeT * 3.14159));
    float progressY = smoothstep(-currentRadius * 0.9, currentRadius * 1.3, q.y);
    float halfWidth = mix(currentRadius * (1.03 + drive * 0.32), currentRadius * (0.36 + 0.18 * (1.0 - drive)), progressY);
    float body = 1.0 - smoothstep(0.78, 1.0,
        length(vec2(q.x / halfWidth, q.y / (currentRadius * fallingStretch))));

    // 合并前显示猎物静滴；合并窗口显示双核+液桥；合并后猎物消失且主滴变大加速。
    float preyX = RainPathX(mergeY, laneX, bendPhase, drive) + (Hash12(eventKey + 31.9) - 0.5) * preyRadius * 0.9;
    float prey = EllipseMask(uv - vec2(preyX, mergeY), vec2(preyRadius)) * (1.0 - merge);
    float bridge = EllipseMask(uv - vec2((headX + preyX) * 0.5, (headY + mergeY) * 0.5),
        vec2(currentRadius * 0.46, currentRadius * 1.35))
        * smoothstep(mergeT - 0.045, mergeT, activeT)
        * (1.0 - smoothstep(mergeT + 0.06, mergeT + 0.12, activeT));

    // 解析沉积尾迹：只出现于滴头已经走过的路径，在世界坐标固定，按通过后的年龄衰减。
    float behind = uv.y - headY; // uv y 向上；滴向下，头部上方为已经经过的轨迹。
    float topLimit = startY - headY;
    float pathX = RainPathX(uv.y, laneX, bendPhase, drive);
    float pathDistance = abs(uv.x - pathX);
    float localSpeed = mix(speed0, speed1, step(mergeT, activeT));
    float wetAge = behind / max(localSpeed, 0.008);
    float trailWidth = currentRadius * (0.055 + 0.075 * exp(-wetAge * 0.65));
    float trailLine = (1.0 - smoothstep(trailWidth * 0.35, trailWidth, pathDistance));
    float trailLife = exp(-wetAge / mix(1.0, 2.8, Hash12(eventKey + 37.5)));
    float segmentId = floor(behind * 145.0 + eventId * 7.0);
    float broken = step(0.30, Hash12(vec2(laneId + 5.0, segmentId)));
    float trail = step(0.0, behind) * step(behind, topLimit) * trailLine * trailLife * broken;

    // 尾迹旁残留卫星滴：位置属于已走过路径，并按湿润年龄自然变暗，而非附着在滴头。
    float satellite = 0.0;
    float band = floor(behind * 30.0);
    float side = Hash12(vec2(laneId + 31.0, band + eventId * 3.0)) - 0.5;
    float satelliteY = (band + 0.5) / 30.0;
    vec2 satellitePos = vec2(RainPathX(headY + satelliteY, laneX, bendPhase, drive) + side * currentRadius * 2.1, headY + satelliteY);
    float satelliteSize = currentRadius * mix(0.10, 0.22, Hash12(vec2(band, laneId)));
    satellite = EllipseMask(uv - satellitePos, vec2(satelliteSize))
        * step(0.0, behind) * step(behind, topLimit) * trailLife
        * step(0.76, Hash12(vec2(band + 9.0, laneId + eventId)));

    // 生命周期：正常情况滴一路向下滑出底部，由 exitFade 在出屏后淡出。
    // 少数慢滴一个周期走不到底部，用 endFade 在周期末柔和收尾，避免硬切 pop。
    // 淡入保持短促（顶部外不可见时就完成）。全程单调向下，绝不向上。
    float exitFade = 1.0 - smoothstep(-0.05, -0.20, headY); // headY < -0.05 才开始淡出
    float endFade = 1.0 - smoothstep(0.96, 1.0, eventT);
    float life = smoothstep(0.0, 0.06, activeT) * exitFade * endFade;
    // sizeFactor 让大雨滴折射更强、高光更柔。
    return vec2(max(max(body, prey), max(bridge, max(trail * 0.30, satellite * 0.52))) * life,
                clamp(radius / 0.0046, 0.5, 2.2));
}

// 雨点击中：一瞬薄环和周围微滴，随后留下一颗静止小水珠；不是水面涟漪。
float ImpactDrops(vec2 uv, float time) {
    vec2 grid = vec2(4.0, 7.0);
    vec2 cell = floor(uv * grid);
    vec2 local = fract(uv * grid) - 0.5;
    float seed = Hash12(cell + vec2(541.2, 82.7));
    if (seed < mix(0.90, 0.30, pow(uIntensity, 1.25))) return 0.0;
    float period = mix(8.5, 2.2, uIntensity) * mix(0.75, 1.25, Hash12(cell + 12.4));
    float age = fract((time + seed * period) / period);
    vec2 centre = (Hash22(cell + 91.0) - 0.5) * 0.36;
    vec2 p = local - centre;
    float baseSize = mix(0.018, 0.038, Hash12(cell + 31.7));
    // 撞击后 0~12% 周期: 极薄扩张环和三颗飞溅微滴。
    float hit = 1.0 - smoothstep(0.0, 0.13, age);
    float ringRadius = baseSize * (0.7 + age * 5.0);
    float ring = smoothstep(baseSize * 0.16, 0.0, abs(length(p) - ringRadius)) * hit;
    float splash = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float a = seed * 18.0 + fi * 2.094;
        vec2 sp = vec2(cos(a), sin(a)) * baseSize * (1.4 + age * 5.0);
        splash = max(splash, EllipseMask(p - sp, vec2(baseSize * 0.18)) * hit);
    }
    // 撞击后沉积成一滴，寿命结束前淡出以等待下一次命中。
    float bead = EllipseMask(p, vec2(baseSize * (1.0 + age * 0.24)))
               * smoothstep(0.04, 0.20, age)
               * (1.0 - smoothstep(0.78, 1.0, age));
    return max(bead, max(ring * 0.55, splash * 0.72));
}

// 独立撞击层：雨滴持续打上窗户，高频出现、短命。
// 按调研（Mundo K 判据），普通小雨在干净玻璃上不溅散，
// 撞击表现为：半径短暂膨胀 + 边缘 Fresnel 闪一下 + 定格成附着滴。
vec2 ImpactLayer(vec2 uv, float time) {
    vec2 grid = vec2(8.0, 14.0);
    vec2 cell = floor(uv * grid);
    vec2 local = fract(uv * grid) - 0.5;
    float seed = Hash12(cell + vec2(541.2, 82.7));
    // 高频：任意时刻约 25% 格子处于撞击中，保证"雨持续打上来"。
    float period = mix(3.0, 6.0, Hash12(cell + 12.4));
    float age = fract((time + seed * period) / period);
    if (seed < 0.75) return vec2(0.0, 0.0);
    vec2 centre = (Hash22(cell + 91.0) - 0.5) * 0.36;
    vec2 p = local - centre;
    float baseSize = mix(0.006, 0.020, pow(Hash12(cell + 31.7), 2.2));
    // 撞击滴：快速成形（0.15 周期）后定格为附着滴，直到周期末才淡出等待下一次。
    // 去掉 swell 0.6→1.3 的膨胀-收缩呼吸（窗上水滴不会忽大忽小，那像活物）。
    float swell = mix(0.9, 1.0, smoothstep(0.0, 0.15, age));
    float settle = 1.0 - smoothstep(0.82, 1.0, age);
    float mask = EllipseMask(p / swell, vec2(baseSize)) * settle;
    // 撞击高光闪：Fresnel 边缘在撞击瞬间更亮。
    float flash = (1.0 - age) * smoothstep(0.6, 1.0, EllipseMask(p, vec2(baseSize * 0.9)));
    return vec2(max(mask, flash * 0.35), clamp(baseSize / 0.0046, 0.5, 3.0));
}

// 稳定附着滴 + 连续下滑滴 + 高频撞击。返回 vec2(field, sizeFactor)。
vec2 WaterField(vec2 uv, float time) {
    vec2 largeDrops = DropCell(uv + vec2(0.13, -0.07), vec2(9.0, 16.0), 17.4, time);
    vec2 smallDrops = DropCell(uv + vec2(-0.31, 0.19), vec2(15.0, 26.0), 83.7, time);
    // 大雨滴层：更稀疏但规模更大，暴雨时明显的大雨珠。
    vec2 stormDrops = DropCell(uv + vec2(0.47, 0.11), vec2(4.0, 7.0), 51.9, time);
    stormDrops.y = stormDrops.y * 1.9; // 大雨滴折射/高光权重更高
    vec2 falling = FallingDrops(uv, time);
    vec2 impacts = ImpactLayer(uv, time);
    float m = max(max(max(largeDrops.x, smallDrops.x), max(stormDrops.x, falling.x)), impacts.x);
    // sizeFactor 取"当前像素处 mask 最大那一层"的尺寸因子。
    float size = largeDrops.x;
    size = mix(size, smallDrops.y, step(size, smallDrops.x));
    size = mix(size, stormDrops.y, step(size, stormDrops.x));
    size = mix(size, falling.y, step(size, falling.x));
    size = mix(size, impacts.y, step(size, impacts.x));
    return vec2(m, max(size, 0.5));
}

// 球冠高度场：h=√(max(0,1-(1-m)²))。中心平板、边缘陡峭（dh/dm→∞ at edge），
// 符合球冠 h(r)=√(R²-r²)-(R-h₀) 的"折射能量集中在边缘过渡带"特征。
float CapHeight(float m) {
    float t = clamp(1.0 - m, 0.0, 1.0);
    return sqrt(max(0.0, 1.0 - t * t));
}

half4 main(float2 fragCoord) {
    vec2 uv = vec2(fragCoord.x / uResoW, 1.0 - fragCoord.y / uResoH);
    float time = mod(uTime, 7200.0);
    vec2 fieldInfo = WaterField(uv, time);
    float field = fieldInfo.x;
    float sizeFactor = fieldInfo.y;

    // child shader 只接收像素坐标；无水区域直接返还真实背景，绝不盖色。
    half4 base = inputShader.eval(fragCoord);
    if (field < 0.004) return base;

    // 用水滴场梯度构造局部表面法线。基于屏幕分辨率的一像素采样，避免分辨率变化造成形变。
    vec2 px = vec2(1.0 / uResoW, 1.0 / uResoH);
    // 大雨滴中心平板化：等效直径>3mm（sizeFactor>1）时重力压扁球冠，
    // 中心接近平板（几乎不折射），折射能量集中在边缘过渡带（透镜环）。
    float centerFlat = clamp(sizeFactor - 1.0, 0.0, 1.0);
    float capShape = CapHeight(field);
    float centerRegion = 1.0 - smoothstep(0.25, 0.7, capShape);
    float height = mix(capShape, capShape * (1.0 - 0.35 * centerRegion), centerFlat) * sizeFactor;
    // 性能优化：梯度从 4 次采样（中心差分 ±x/±y）改为 2 次前向差分（+x/+y），
    // 复用中心已算的 capShape，*2.0 保持与中心差分相当的数值量级。
    float hx = (CapHeight(WaterField(uv + vec2(px.x, 0.0), time).x) - capShape) * 2.0;
    float hy = (CapHeight(WaterField(uv + vec2(0.0, px.y), time).x) - capShape) * 2.0;
    // 局部表面斜率 = 高度梯度；折射位移 δ≈t·(n-1)/n·tan(α) 是厚度×斜率，两者都要参与。
    float slope = length(vec2(hx, hy)) * uResoH; // 换算到像素尺度
    vec2 normalUv = normalize(vec2(hx, hy) + vec2(0.00001, 0.0));

    // uv 的 Y 向上，fragCoord 的 Y 向下；normalPx 指向滴中心。
    // 凸透镜放大 = 采样点从像素向滴心移动但不越过滴心（D < 滴半径）。
    // 之前 uDist=10-20 过大，D 越过滴心 → 半倒像糊成一片脏水；缩到 4-8 保持纯放大。
    vec2 normalPx = vec2(normalUv.x, -normalUv.y);
    float lensStrength = 0.35 + 0.65 * min(slope * 0.5, 1.0);
    vec2 refractedCoord = clamp(
        fragCoord + normalPx * uDist * height * lensStrength,
        vec2(0.0), vec2(uResoW, uResoH)
    );

    // 性能优化：色散（RGB 分通道折射）对视觉收益极低（水 Δn≈0.012），
    // 改为单次采样，减少 2 次 inputShader.eval 调用。
    half4 refracted = inputShader.eval(refractedCoord);
    // 水滴本体 = 透过水滴看到的背景（折射后的背景），不是半透明贴图。
    // 中心最厚 → 最接近折射结果；边缘薄 → 逐渐回到原背景。
    // 折射混合：过渡区间拉到 0.55，减少中心大面积高饱和折射（产生"脏"感的来源之一）。
    half4 color = mix(base, refracted, half(smoothstep(0.0, 0.55, height) * 0.88));
    // 内部微对比增强：让暗背景水滴也有水膜层次（非乘法提亮，是局部对比）。
    float lum = dot(color.rgb, half3(0.299, 0.587, 0.114));
    color.rgb = mix(color.rgb, color.rgb + (color.rgb - lum), half(height * 0.18));

    // 二、贴边高光：球面镜反射像在光源同侧边缘内侧（0.3-0.6R 处）。
    //    法线来自高度梯度，方向稳定（非 1px 噪声）。
    vec3 N = normalize(vec3(-normalUv.x * 1.8, -normalUv.y * 1.8, 1.0));
    vec3 L = normalize(vec3(-0.45, 0.62, 1.0));
    vec3 V = vec3(0.0, 0.0, 1.0);
    vec3 H = normalize(L + V);
    // 高光锐度提高（大滴 power=28→80，小滴维持尖锐），产生玻璃水滴的点状亮斑而非漫散光晕。
    // 颜色改为纯冷白，去掉偏黄的色调（雨水是无色的）。
    float specularPower = mix(80.0, 28.0, clamp((sizeFactor - 0.7) / 1.3, 0.0, 1.0));
    float specular = pow(max(dot(N, H), 0.0), specularPower) * height;
    color.rgb += half3(0.97, 0.98, 1.0) * half(specular * 1.1);

    // 背景亮度适应：下限提高到 0.82，避免暗背景时水滴整体变灰/脏。
    // 水滴本身是透明的，不应该比背景更暗，只有边缘遮光区才略压暗。
    float baseLum = dot(base.rgb, half3(0.299, 0.587, 0.114));
    float adapt = mix(0.82, 1.0, baseLum * 1.6);
    color.rgb *= half(adapt);

    // 三、边缘单侧光照：亮边集中在光源方向（顶部/左侧），对侧暗。
    //    用局部法线的 y 分量加权——不再是全周均匀描边（那是"脏水印"来源）。
    // Fresnel 边缘：窄化到 0.80-0.96，去掉外侧宽带（"脏水印"来源）。
    // 颜色改为冷白，强度略降低；暗线压暗量也稍微收窄，避免整体发灰。
    float rimBand = smoothstep(0.80, 0.96, field) * (1.0 - smoothstep(0.96, 1.0, field));
    float lightSide = clamp(0.45 + 0.55 * normalUv.y, 0.0, 1.0); // 上侧亮
    float fresnel = rimBand * lightSide * (0.5 + 0.4 * sizeFactor);
    color.rgb += half3(0.92, 0.95, 1.0) * half(fresnel * 0.45);
    // 对侧暗线：收窄压暗量，保留立体感但不发灰。
    float darkLine = rimBand * (1.0 - lightSide) * (0.4 + 0.4 * sizeFactor);
    color.rgb *= 1.0 - half(darkLine * 0.22);
    color.a = max(base.a, half(field));
    return color;
}
"""