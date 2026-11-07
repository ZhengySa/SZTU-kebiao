package com.classflow.timetable.ui.screen.sztu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.core.owner.OwnerView
import com.classflow.timetable.data.local.SztuAccountStore
import com.classflow.timetable.data.remote.sztu.LoginOutcome
import com.classflow.timetable.data.remote.sztu.SessionExpiredException
import com.classflow.timetable.data.remote.sztu.SztuAuthClient
import com.classflow.timetable.domain.imports.ImportOutcome
import com.classflow.timetable.domain.imports.ImportPreview
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.usecase.ApplySztuImportUseCase
import com.classflow.timetable.domain.usecase.SztuSnapshot
import com.classflow.timetable.domain.usecase.SztuSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SztuUiState {

    data object Loading : SztuUiState

    /**
     * 需要用户输入账号密码 + 验证码（走学校自己的验证码，App 不做任何识别或绕过）。
     *
     * [forPartner] = true 时这条登录是给「情侣课表」用的：
     * 登录成功后课表写进另一个归属，密码一律不落盘。
     */
    data class NeedInput(
        val form: CredentialsForm,
        val error: String? = null,
        val forPartner: Boolean = false,
    ) : SztuUiState

    data object Working : SztuUiState

    data class Preview(
        val semesterName: String,
        val preview: ImportPreview,
        val forPartner: Boolean = false,
    ) : SztuUiState

    data class Done(
        val semesterName: String,
        val outcome: ImportOutcome,
        val forPartner: Boolean = false,
    ) : SztuUiState

    data class Failed(val message: String, val forPartner: Boolean = false) : SztuUiState
}

/** 登录表单。验证码图片来自学校，App 只是把字节原样显示出来。 */
data class CredentialsForm(
    val username: String = "",
    val password: String = "",
    val captcha: String = "",
    val rememberPassword: Boolean = false,
    val captchaJpeg: ByteArray? = null,
    val captchaLoading: Boolean = false,
)

@HiltViewModel
class SztuLoginViewModel @Inject constructor(
    private val sync: SztuSyncUseCase,
    private val applyImport: ApplySztuImportUseCase,
    private val accountStore: SztuAccountStore,
    private val ownerView: OwnerView,
) : ViewModel() {

    private val _state = MutableStateFlow<SztuUiState>(SztuUiState.Loading)
    val state: StateFlow<SztuUiState> = _state.asStateFlow()

    private var client: SztuAuthClient? = null
    private var snapshot: SztuSnapshot? = null

    /** 这次登录是给谁导入的：自己（默认）还是 TA（情侣课表）。 */
    private var target: String = OwnerKey.SELF

    private val form = MutableStateFlow(CredentialsForm())
    private var started = false

    /** 进页面时调用一次：[target] 决定写进谁的课表。 */
    fun start(targetKey: String = OwnerKey.SELF) {
        if (started) return
        started = true
        target = targetKey
        _state.value = SztuUiState.Loading
        viewModelScope.launch {
            val remembered = if (targetKey == OwnerKey.PARTNER) {
                accountStore.currentPartnerUsername().ifBlank { "" }
            } else {
                accountStore.currentUsername()
            }
            form.value = form.value.copy(
                username = remembered,
                rememberPassword = false,
                // TA 的密码从不保存
                password = if (targetKey == OwnerKey.SELF) {
                    accountStore.savedPassword().orEmpty()
                } else {
                    ""
                },
            )

            runCatching {
                val c = client ?: sync.restoreClient(targetKey).also { client = it }
                if (c.hasValidSession()) {
                    loadTimetable(c)
                    true
                } else {
                    false
                }
            }.getOrDefault(false).let { restored ->
                // 没会话或会话过期（含抓课表失败）：落到表单，顺手取一张验证码
                if (!restored) requestCaptcha()
            }
        }
    }

    // ---------- 表单输入 ----------

    fun onUsernameChange(value: String) {
        form.value = form.value.copy(username = value)
        publishForm()
    }

    fun onPasswordChange(value: String) {
        form.value = form.value.copy(password = value)
        publishForm()
    }

    fun onCaptchaChange(value: String) {
        form.value = form.value.copy(captcha = value)
        publishForm()
    }

    fun onRememberPasswordChange(value: Boolean) {
        form.value = form.value.copy(rememberPassword = value)
        publishForm()
    }

    /** 换一张验证码：不动已填的账号密码。 */
    fun refreshCaptcha() {
        viewModelScope.launch { requestCaptcha() }
    }

    fun login() {
        val cur = form.value
        if (cur.username.isBlank() || cur.password.isBlank() || cur.captcha.isBlank()) {
            publishForm(error = "学号、密码、验证码都要填。")
            return
        }
        val c = client
        if (c == null) {
            // 会话还没初始化成功（网络刚失败过）：重新取一张验证码，让用户再点一次登录
            viewModelScope.launch { requestCaptcha() }
            return
        }
        _state.value = SztuUiState.Working
        viewModelScope.launch {
            runCatching {
                when (val outcome = c.submit(cur.username, cur.password, cur.captcha)) {
                    LoginOutcome.Success -> {
                        if (target == OwnerKey.PARTNER) {
                            accountStore.savePartnerLogin(cur.username)
                        } else {
                            accountStore.saveLogin(
                                username = cur.username,
                                password = cur.password,
                                rememberPassword = cur.rememberPassword,
                            )
                        }
                        loadTimetable(c)
                    }
                    is LoginOutcome.CaptchaRejected -> {
                        form.value = form.value.copy(captcha = "")
                        requestCaptcha()
                        publishForm(error = outcome.message)
                    }
                    is LoginOutcome.Failed -> {
                        form.value = form.value.copy(captcha = "")
                        requestCaptcha()
                        publishForm(error = outcome.message)
                    }
                }
            }.onFailure { publishForm(error = friendly(it)) }
        }
    }

    fun apply(mode: ImportMode) {
        val snap = snapshot ?: return
        val previewState = _state.value as? SztuUiState.Preview ?: return
        _state.value = SztuUiState.Working
        viewModelScope.launch {
            runCatching {
                applyImport(snap, previewState.preview, mode, previewState.forPartner.ownerKey())
            }.onSuccess {
                // 导入的是 TA 的课表，就顺手把课表切到 TA，省得用户回去还要自己按一下
                if (previewState.forPartner) ownerView.set(OwnerKey.PARTNER)
                _state.value = SztuUiState.Done(previewState.semesterName, it, previewState.forPartner)
            }.onFailure {
                _state.value = SztuUiState.Failed("写入课表失败：${it.message ?: "未知错误"}", previewState.forPartner)
            }
        }
    }

    /** 「重新登录」：回到干净的表单（保留学号，密码清空）。 */
    fun relogin() {
        form.value = form.value.copy(password = "", captcha = "")
        viewModelScope.launch { requestCaptcha() }
    }

    // ---------- 内部 ----------

    private suspend fun requestCaptcha() {
        form.value = form.value.copy(captchaLoading = true, captcha = "")
        val c = client ?: sync.restoreClient(target).also { client = it }
        runCatching {
            val challenge = c.prepare()
            if (challenge.alreadyLoggedIn) {
                loadTimetable(c)
                return
            }
            form.value = form.value.copy(captchaJpeg = challenge.captchaJpeg)
        }.onFailure { e ->
            publishForm(error = friendly(e))
        }
        form.value = form.value.copy(captchaLoading = false)
        publishForm()
    }

    private suspend fun loadTimetable(c: SztuAuthClient) {
        _state.value = SztuUiState.Working
        val snap = sync.snapshot(c, null, target)
        snapshot = snap
        if (snap.timetable.entries.isEmpty()) {
            _state.value = SztuUiState.Failed(
                "登录成功了，但这个学期（${snap.semesterName}）课表是空的。可能学校还没排课。",
                target == OwnerKey.PARTNER,
            )
            return
        }
        sync.persistSession(c, snap, target)
        _state.value = SztuUiState.Preview(
            semesterName = snap.semesterName,
            preview = sync.preview(snap),
            forPartner = target == OwnerKey.PARTNER,
        )
    }

    private fun publishForm(error: String? = null) {
        _state.value = SztuUiState.NeedInput(
            form = form.value,
            error = error,
            forPartner = target == OwnerKey.PARTNER,
        )
    }

    private fun friendly(error: Throwable): String = when (error) {
        is SessionExpiredException -> "登录状态已失效，请重新登录学校账号。"
        is java.net.UnknownHostException -> "连不上学校服务器，请确认手机能打开校园网。"
        is java.net.SocketTimeoutException -> "学校服务器响应超时，换个网络（或稍后）再试。"
        else -> error.message ?: "同步失败，请重试。"
    }

    private fun Boolean.ownerKey(): String = if (this) OwnerKey.PARTNER else OwnerKey.SELF
}
