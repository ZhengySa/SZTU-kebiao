package com.classflow.timetable.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃（Liquid Glass）视觉系统。
 *
 * 三层结构，缺一层就会"塑料感"：
 *  1. 底层：缓慢漂移的彩色光斑（大半径、低透明度、互相叠加）
 *  2. 玻璃层：半透明白 + 顶部高光渐变 + 极细渐变描边（描边比填充更关键）
 *  3. 内容层：文字用高对比深色，避免玻璃一变背景就糊
 *
 * 没有用真正的背景模糊（backdrop blur）：Compose 在 Android 12 以下没有这项能力，
 * 强行做会变成"卡片里再画一遍背景"，性能很差且截断位置难对齐。
 * 用「半径极大的柔光 + 渐变描边 + 高光」在观感上更稳，也不会在老机器上卡。
 */
@Immutable
data class GlassPalette(
    val isDark: Boolean,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glassFill: Color,
    val glassFillTop: Color,
    val glassFillBottom: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val shadow: Color,
) {
    val background: List<Color>
        get() = if (isDark) {
            listOf(Color(0xFF080B14), Color(0xFF0C1120), Color(0xFF101426))
        } else {
            // 不能太接近纯白：玻璃是"半透明白"，底越白越看不出层次，最后会退化成一片白块。
            // 也不能太艳：一旦底色饱和度过高，白色玻璃卡就会显脏，文字对比度也压不住。
            // 取中间——很浅的蓝紫，让玻璃"有东西可透"，但不抢文字的戏。
            listOf(Color(0xFFEDF1FE), Color(0xFFF2ECFD), Color(0xFFE9F6FA))
        }

    val blobs: List<Pair<Color, Float>>
        get() = if (isDark) {
            listOf(
                Color(0xFF4C63FF) to 0.55f,
                Color(0xFF9B3FF0) to 0.45f,
                Color(0xFF12A0C8) to 0.38f,
                Color(0xFF2A4BC8) to 0.36f,
            )
        } else {
            // 只用同色系（蓝→紫→青）：混进橙色会让整体像儿童 App。
            // alpha 控制在 0.35~0.5，观感是"光在纸后面"，不是"彩虹渐层"。
            listOf(
                Color(0xFF9DB4FF) to 0.50f,
                Color(0xFFC9A8FF) to 0.42f,
                Color(0xFF8FDCDC) to 0.36f,
                Color(0xFFA8B8FF) to 0.30f,
            )
        }
}

private val LightGlass = GlassPalette(
    isDark = false,
    textPrimary = Color(0xFF11141C),
    textSecondary = Color(0xFF4E5666),
    textTertiary = Color(0xFF7E879B),
    // 之前 top 用了 62% 白，等于把卡片糊成一块白板。
    // 现在主填充降到 ~40%，让底下的光斑真的透上来；层次靠"顶部高光 + 描边 + 阴影"撑，而不是靠填充不透明。
    glassFill = Color(0x66FFFFFF),
    glassFillTop = Color(0x8AFFFFFF),
    glassFillBottom = Color(0x40FFFFFF),
    glassBorderTop = Color(0xD9FFFFFF),
    glassBorderBottom = Color(0x1FFFFFFF),
    accent = Color(0xFF3D5AFE),
    accentSoft = Color(0x1F3D5AFE),
    danger = Color(0xFFD93F3F),
    shadow = Color(0x1F2D4585),
)

private val DarkGlass = GlassPalette(
    isDark = true,
    textPrimary = Color(0xFFF3F5FA),
    textSecondary = Color(0xFFAEB6C8),
    textTertiary = Color(0xFF7E8799),
    glassFill = Color(0x14FFFFFF),
    glassFillTop = Color(0x1FFFFFFF),
    glassFillBottom = Color(0x0AFFFFFF),
    glassBorderTop = Color(0x33FFFFFF),
    glassBorderBottom = Color(0x0AFFFFFF),
    accent = Color(0xFF8FA4FF),
    accentSoft = Color(0x338FA4FF),
    danger = Color(0xFFFF8A8A),
    shadow = Color(0x66000000),
)

val LocalGlassPalette = staticCompositionLocalOf { LightGlass }

@Composable
fun rememberGlassPalette(dark: Boolean = isSystemInDarkTheme()): GlassPalette =
    if (dark) DarkGlass else LightGlass

/** 渐变描边 + 半透明填充 + 顶部高光，构成一块"玻璃"。 */
fun Modifier.glassSurface(
    palette: GlassPalette,
    shape: Shape,
    fillTop: Color = palette.glassFillTop,
    fillBottom: Color = palette.glassFillBottom,
    borderAlpha: Float = 1f,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(fillTop, palette.glassFill, fillBottom),
        ),
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                palette.glassBorderTop.copy(alpha = palette.glassBorderTop.alpha * borderAlpha),
                palette.glassBorderBottom.copy(alpha = palette.glassBorderBottom.alpha * borderAlpha),
            ),
        ),
        shape = shape,
    )

/** 玻璃卡片：自带柔和外阴影，避免"贴纸感"。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    palette: GlassPalette = LocalGlassPalette.current,
    fillTop: Color = palette.glassFillTop,
    fillBottom: Color = palette.glassFillBottom,
    /**
     * 外阴影高度。列表里的卡片建议给 0~8dp：
     * 每一层阴影都是一次额外的离屏渲染，一屏放七八张卡时它是掉帧的主要来源。
     */
    elevation: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        ambientColor = palette.shadow,
                        spotColor = palette.shadow,
                    )
                } else {
                    Modifier
                },
            )
            .glassSurface(palette, shape, fillTop, fillBottom)
            .drawTopHighlight(cornerRadius),
        content = content,
    )
}

/** 卡片顶部的 1px 高光，模拟玻璃边缘反光。 */
private fun Modifier.drawTopHighlight(cornerRadius: Dp): Modifier = drawBehind {
    val inset = cornerRadius.toPx() * 0.6f
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.18f),
                Color.Transparent,
            ),
        ),
        start = Offset(inset, 1f),
        end = Offset(size.width - inset, 1f),
        strokeWidth = 1.2f,
    )
}

/**
 * 全屏液态背景。
 *
 * 性能上最关键的一点：**光斑只画一次，之后只挪图层**。
 *
 * 老实现把 5 个大半径径向渐变放在 `animateFloat` 的读取路径里，等于每帧重新栅格化
 * 5 张全屏渐变 —— 在 60Hz 下就是每秒 300 次全屏绘制，任何页面滑动都会被它拖住。
 * 现在光斑画在 [graphicsLayer] 里的静态画布上（`drawWithCache` 缓存 draw 指令），
 * 每帧只改图层的 translate 矩阵，不再重画画布；位移量经过放大，观感反而比原来更"活"。
 */
@Composable
fun LiquidBackdrop(
    palette: GlassPalette,
    modifier: Modifier = Modifier,
    /**
     * 用户自选的背景图；null 时用默认渐变。
     *
     * 有图时不是直接把图画上去就完事：中间要压一层"磨砂"色的 scrim，
     * 否则玻璃卡那点半透明白压不住照片的亮部，深色文字直接糊掉。
     * 光斑也降到 38% 透明——留着是为了"液体"还在动，不是为了抢戏。
     */
    backdrop: ImageBitmap? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // 背景漂移故意跑在 ~12fps：它一个来回要 40 多秒，每帧位移不到 3px，
    // 60fps 纯属浪费 —— 少更新几次，合成次数直接降到原来的 1/5。
    // 状态只在 graphicsLayer 的 block 里被读，所以更新不会触发重组，只更新图层矩阵。
    val drift = remember { mutableStateOf(Drift(0f, 0f)) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val seconds = (withFrameNanos { it } - start) / 1_000_000_000.0
            drift.value = Drift(
                x = (0.5 + 0.5 * sin(seconds * 2 * PI / 42.0)).toFloat(),
                y = (0.5 + 0.5 * cos(seconds * 2 * PI / 57.0)).toFloat(),
            )
            delay(80)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(palette.background)),
    ) {
        if (backdrop != null) {
            Image(
                bitmap = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // 真模糊（RenderEffect）只在 Android 12+ 生效，低版本会退化成清晰图。
                    // 所以下面那层 scrim 不能省：它才是压住照片、保住文字对比度的底线。
                    .blur(18.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            if (palette.isDark) {
                                listOf(
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Black.copy(alpha = 0.84f),
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.60f),
                                    Color.White.copy(alpha = 0.72f),
                                )
                            },
                        ),
                    ),
            )
        }

        val blobAlpha = if (backdrop != null) 0.38f else 1f

        BlobLayer(
            palette = palette,
            blobs = listOf(
                Blob(0, Offset(0.16f, 0.10f), 0.86f),
                Blob(1, Offset(0.92f, 0.34f), 0.72f),
                Blob(2, Offset(0.22f, 0.88f), 0.78f),
            ),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 放大一点，位移时不会露出边。
                    // 读 drift 必须写在 block 里面：写在组合里就等于每次更新都重组整棵树。
                    val d = drift.value
                    alpha = blobAlpha
                    scaleX = 1.30f
                    scaleY = 1.30f
                    translationX = (d.x - 0.5f) * 130f
                    translationY = (d.y - 0.5f) * 100f
                },
        )
        BlobLayer(
            palette = palette,
            blobs = listOf(
                Blob(3, Offset(0.88f, 0.82f), 0.64f),
                Blob(0, Offset(0.55f, 0.52f), 0.56f, alphaScale = 0.5f),
            ),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val d = drift.value
                    alpha = blobAlpha
                    scaleX = 1.24f
                    scaleY = 1.24f
                    translationX = (d.y - 0.5f) * -110f
                    translationY = (d.x - 0.5f) * -90f
                },
        )

        CompositionLocalProvider(LocalGlassPalette provides palette) {
            content()
        }
    }
}

/** 背景漂移的归一化位置（0~1）。 */
private data class Drift(val x: Float, val y: Float)

/** 一个光斑：调色板里的第几个 + 中心点（0~1 相对坐标）+ 半径系数。 */
private data class Blob(
    val paletteIndex: Int,
    val center: Offset,
    val radius: Float,
    val alphaScale: Float = 1f,
)

/** 画一组光斑；画布内容会被缓存成图层，只有图层变换在动。 */
@Composable
private fun BlobLayer(
    palette: GlassPalette,
    blobs: List<Blob>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawWithCache {
            val brushes = blobs.map { blob ->
                val (color, alpha) = palette.blobs[blob.paletteIndex]
                val center = Offset(size.width * blob.center.x, size.height * blob.center.y)
                val radius = size.minDimension * blob.radius
                Triple(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha * blob.alphaScale), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    center,
                    radius,
                )
            }
            onDrawBehind {
                brushes.forEach { (brush, center, radius) ->
                    drawCircle(brush = brush, radius = radius, center = center)
                }
            }
        },
    )
}

/**
 * 统一的玻璃页头，替代 Material 的 TopAppBar。
 *
 * 直接塞进 Scaffold 的 topBar 槽位即可：Scaffold 只按它的高度让位，
 * 不会替它处理系统栏，所以状态栏内边距必须在这里自己加。
 */
@Composable
fun GlassScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .glassSurface(palette, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}
