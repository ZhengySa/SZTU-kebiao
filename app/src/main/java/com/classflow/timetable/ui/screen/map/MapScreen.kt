package com.classflow.timetable.ui.screen.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classflow.timetable.R
import com.classflow.timetable.domain.model.ZhongdeFloors
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette

/**
 * 校园地图。
 *
 * 三个 Tab：
 *  - 校园导览图（学校官方彩绘地图，标了 A~E 区和楼栋）
 *  - C-5 公共教学楼教室分布图，**按楼层拆成 5 张**（1F~5F 各自一张，
 *    而不是把整条长图塞进一个框里，那样手机上字小到看不清）
 *  - 中德智能制造学院楼层图，13 层（1~6、8~10、12、13、17、18），
 *    楼层号不连续，所以选择器是按列表铺的而不是 `1..18` 遍历。
 *
 * 三张都支持双指缩放 + 拖动，双击复位。
 */
@Composable
fun MapScreen() {
    val palette = LocalGlassPalette.current
    var tab by remember { mutableIntStateOf(0) } // 0 = 校园导览, 1 = C-5, 2 = 中德学院

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "校园地图",
            subtitle = "双指缩放 · 拖动查看 · 双击复位",
            onBack = null,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapTab("校园导览", tab == 0, palette) { tab = 0 }
            MapTab("C-5 教学楼", tab == 1, palette) { tab = 1 }
            MapTab("中德学院", tab == 2, palette) { tab = 2 }
        }

        when (tab) {
            0 -> Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                ZoomableImage(
                    resId = R.drawable.map_campus,
                    ratio = 1920f / 1078f,
                    palette = palette,
                )
            }

            1 -> C5Floors(palette)

            else -> ZhongdeFloorsTab(palette)
        }
    }
}

/* ------------------------------ C-5 教学楼 ------------------------------ */

@Composable
private fun ColumnScope.C5Floors(palette: GlassPalette) {
    var floor by remember { mutableIntStateOf(1) }
    Column(Modifier.weight(1f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            (1..5).forEach { f ->
                MapTab(
                    text = "${f}F",
                    selected = floor == f,
                    palette = palette,
                    compact = true,
                ) { floor = f }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "C-5 公共教学楼",
                fontSize = 10.sp,
                color = palette.textTertiary,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
                .padding(top = 8.dp, bottom = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            ZoomableImage(
                resId = c5Drawable(floor),
                ratio = 1280f / 1164f,
                palette = palette,
            )
        }
    }
}

private fun c5Drawable(floor: Int): Int = when (floor) {
    1 -> R.drawable.map_floor_1
    2 -> R.drawable.map_floor_2
    3 -> R.drawable.map_floor_3
    4 -> R.drawable.map_floor_4
    else -> R.drawable.map_floor_5
}

/* ----------------------------- 中德智能制造学院 ----------------------------- */

/**
 * 中德学院：楼层选择器 + 平面图 + 楼层房间汇总。
 *
 * 楼层号不连续（缺 7/11/14/15/16），所以按钮按 [ZhongdeFloors.floors] 铺开，
 * 横向滚动而不是挤成两行 —— 13 个按钮挤两行会把每个压到 30dp 以下，手指点不准。
 */
@Composable
private fun ColumnScope.ZhongdeFloorsTab(palette: GlassPalette) {
    var floor by remember { mutableIntStateOf(1) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    val current = remember(floor) { ZhongdeFloors.of(floor) }
    val hits = remember(query) {
        if (query.isBlank()) emptyList() else ZhongdeFloors.find(query)
    }

    Column(Modifier.weight(1f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ZhongdeFloors.floors.forEach { f ->
                    MapTab(
                        text = "${f}F",
                        selected = floor == f,
                        palette = palette,
                        compact = true,
                    ) { floor = f }
                }
            }
            // 13 个楼层按钮一屏放不下，右边没提示的话用户不会想到还能滑
            if (scroll.canScrollForward) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = palette.textTertiary,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(start = 2.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (searching) palette.accent else palette.glassFillTop)
                    .clickable {
                        searching = !searching
                        if (!searching) {
                            query = ""
                            focus.clearFocus()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "搜房间号",
                    tint = if (searching) Color.White else palette.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        if (searching) {
            SearchField(
                query = query,
                palette = palette,
                onQuery = { query = it },
                onSubmit = { focus.clearFocus() },
            )
            if (hits.isNotEmpty()) {
                SearchResults(
                    hits = hits,
                    palette = palette,
                    onClick = { (f, _) ->
                        floor = f.floor
                        searching = false
                        query = ""
                        focus.clearFocus()
                    },
                )
            } else if (query.isNotBlank()) {
                Text(
                    text = "没找到「$query」",
                    fontSize = 11.sp,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
                .padding(top = 8.dp, bottom = 4.dp),
            // 图按宽度撑满、在上方对齐。横版平面图在竖屏里本来就只有这么高，
            // 居中反而上下各留一大块空白卡片，看着像加载失败。
            contentAlignment = Alignment.TopCenter,
        ) {
            ZoomableImage(
                resId = current.image,
                ratio = 1731f / 1126f,
                palette = palette,
            )
        }

        Text(
            text = "${current.floor}F · ${current.summary} · ${current.rooms.size} 个房间",
            fontSize = 10.sp,
            color = palette.textTertiary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    palette: GlassPalette,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 8.dp)
            .height(44.dp),
        placeholder = { Text("房间号，如 305 / 1301", fontSize = 12.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        leadingIcon = {
            Icon(Icons.Filled.Search, null, tint = palette.textTertiary, modifier = Modifier.size(16.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = palette.textTertiary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQuery("") },
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.glassFillTop,
            unfocusedContainerColor = palette.glassFillTop,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SearchResults(
    hits: List<Pair<ZhongdeFloors.Floor, ZhongdeFloors.Room>>,
    palette: GlassPalette,
    onClick: (Pair<ZhongdeFloors.Floor, ZhongdeFloors.Room>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassFillTop),
    ) {
        items(hits) { (f, room) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(f to room) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("${f.floor}F", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(room.no, fontSize = 12.sp, color = palette.textPrimary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    room.name,
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/* --------------------------------- 公共件 --------------------------------- */

@Composable
private fun MapTab(
    text: String,
    selected: Boolean,
    palette: GlassPalette,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else palette.glassFillTop)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 5.dp else 7.dp,
            ),
    ) {
        Text(
            text = text,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else palette.textSecondary,
        )
    }
}

/**
 * 可缩放的地图容器。
 *
 * 高度用 [ratio] 贴合图片本身的比例，而不是撑满整屏——
 * 横版地图塞进竖长容器会留出大片白边，看着像图加载失败。
 *
 * 手势用 [rememberTransformableState]：自带双指缩放 + 拖动，
 * 缩放到 1x 时把位移归零，避免图被拖出可视区。
 */
@Composable
private fun ZoomableImage(
    @DrawableRes resId: Int,
    ratio: Float,
    palette: GlassPalette,
) {
    var scale by remember(resId) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var offset by remember(resId) { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = newScale
        offset = if (newScale <= 1f) Offset.Zero else offset + panChange
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, palette.glassBorderTop, RoundedCornerShape(18.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(resId) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                    )
                }
                .transformable(transformState),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(resId),
                contentDescription = "校园地图",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }

        if (scale > 1.02f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.accent)
                    .clickable {
                        scale = 1f
                        offset = Offset.Zero
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CenterFocusStrong,
                    contentDescription = "复位",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
