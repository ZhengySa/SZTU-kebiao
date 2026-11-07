package com.classflow.timetable.ui.screen.nbw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.data.local.NbwCacheStore
import com.classflow.timetable.data.remote.sztu.NbwRepository
import com.classflow.timetable.domain.model.NbwCategory
import com.classflow.timetable.domain.model.NbwDetail
import com.classflow.timetable.domain.model.NbwNotice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NbwUiState(
    val category: NbwCategory = NbwCategory.ALL,
    val notices: List<NbwNotice> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalCount: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** 正在加载下一页（列表底部的小转圈）。 */
    val loadingMore: Boolean = false,
    /** 一条都没拿到时的错误。已经有内容时不覆盖，改用 [notice]。 */
    val error: String? = null,
    /** 有内容但刷新失败：显示成一条顶部提示，不把列表清空。 */
    val notice: String? = null,
    val fromCache: Boolean = false,
    val cachedAt: Long = 0L,
) {
    val hasMore: Boolean get() = page < totalPages
}

data class NbwDetailState(
    val path: String = "",
    val loading: Boolean = false,
    val detail: NbwDetail? = null,
    val fromCache: Boolean = false,
    val error: String? = null,
)

/**
 * 公文通列表页的状态机。
 *
 * 和成绩页同一套思路：**先上缓存再联网**。因为公文通只在校内网可达，
 * 用户在校外打开这个页面的概率相当高 —— 这种时候如果没有缓存，
 * 页面就是一块"连不上"的白板。
 *
 * 翻页用「追加」而不是「替换」：公告列表是往下刷的习惯，替换式翻页
 * 在手机上会让人来回找位置。
 */
@HiltViewModel
class NbwViewModel @Inject constructor(
    private val repo: NbwRepository,
    private val cache: NbwCacheStore,
) : ViewModel() {

    private val _state = MutableStateFlow(NbwUiState())
    val state: StateFlow<NbwUiState> = _state.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        load(reset = true, silent = false)
    }

    fun select(category: NbwCategory) {
        if (_state.value.category == category) return
        _state.value = _state.value.copy(
            category = category,
            notices = emptyList(),
            page = 1,
            totalPages = 1,
            fromCache = false,
            error = null,
            notice = null,
        )
        load(reset = true, silent = false)
    }

    /** 下拉刷新。 */
    fun refresh() {
        if (_state.value.refreshing) return
        load(reset = true, silent = _state.value.notices.isNotEmpty(), pull = true)
    }

    /** 滑到底自动加载下一页；没有更多 / 正在加载时直接返回。 */
    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loadingMore || s.loading || s.fromCache) return
        load(reset = false, silent = true)
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    private fun load(reset: Boolean, silent: Boolean, pull: Boolean = false) {
        val s = _state.value
        val targetPage = if (reset) 1 else s.page + 1
        _state.value = s.copy(
            loading = reset && !silent,
            refreshing = pull || (reset && silent),
            loadingMore = !reset,
            error = if (reset) null else s.error,
        )
        viewModelScope.launch {
            // 首屏：先把上次缓存的 20 条贴上去，用户不用对着白屏等请求
            if (reset && targetPage == 1 && s.notices.isEmpty()) {
                val cached = cache.cache.first()
                if (!cached.isEmpty && _state.value.notices.isEmpty()) {
                    _state.value = _state.value.copy(
                        notices = cached.notices,
                        fromCache = true,
                        cachedAt = cached.savedAt,
                        loading = false,
                        refreshing = true,
                    )
                }
            }

            when (val r = repo.list(s.category, targetPage)) {
                is NbwRepository.Result.Ok -> {
                    val page = r.data
                    val merged = if (reset) page.notices else _state.value.notices + page.notices
                    _state.value = _state.value.copy(
                        notices = merged,
                        page = page.page,
                        totalPages = page.totalPages,
                        totalCount = page.totalCount,
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = null,
                        notice = null,
                        fromCache = false,
                    )
                    // 只缓存「全部」栏目的首页，别的组合进不了离线首页
                    if (reset && page.page == 1 && s.category == NbwCategory.ALL) {
                        cache.saveHome(page.notices)
                    }
                }

                is NbwRepository.Result.Offline -> finishWithError(r.message, pull)

                is NbwRepository.Result.Broken -> finishWithError(r.message, pull)
            }
        }
    }

    private fun finishWithError(message: String, pull: Boolean) {
        val s = _state.value
        val hasContent = s.notices.isNotEmpty()
        _state.value = s.copy(
            loading = false,
            refreshing = false,
            loadingMore = false,
            // 有内容时别把列表清空 —— 用户还能看缓存里的东西
            error = if (hasContent) null else message,
            notice = if (hasContent) "$message（下面是缓存）" else null,
        )
    }
}

/** 详情页状态机。点开即有缓存就直接显示，再后台刷新。 */
@HiltViewModel
class NbwDetailViewModel @Inject constructor(
    private val repo: NbwRepository,
    private val cache: NbwCacheStore,
) : ViewModel() {

    private val _state = MutableStateFlow(NbwDetailState())
    val state: StateFlow<NbwDetailState> = _state.asStateFlow()

    private var loadedPath: String? = null

    fun load(path: String) {
        if (loadedPath == path) return
        loadedPath = path
        _state.value = NbwDetailState(path = path, loading = true)
        viewModelScope.launch {
            val cached = runCatching { cache.cache.first().detailOf(path) }.getOrNull()
            if (cached != null) {
                _state.value = _state.value.copy(detail = cached, fromCache = true, loading = false)
            }
            when (val r = repo.detail(path)) {
                is NbwRepository.Result.Ok -> {
                    _state.value = _state.value.copy(
                        detail = r.data,
                        loading = false,
                        fromCache = false,
                        error = null,
                    )
                    runCatching { cache.saveDetail(r.data) }
                }

                is NbwRepository.Result.Offline ->
                    if (cached == null) {
                        _state.value = _state.value.copy(loading = false, error = r.message)
                    }

                is NbwRepository.Result.Broken ->
                    if (cached == null) {
                        _state.value = _state.value.copy(loading = false, error = r.message)
                    }
            }
        }
    }
}
