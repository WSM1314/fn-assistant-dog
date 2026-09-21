package com.example.fn_app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fn_app.copy.Copy
import com.example.fn_app.di.AppContainer
import com.fnassistantdog.core.auth.AccountRecord
import com.fnassistantdog.core.error.EpicException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val accounts: StateFlow<List<AccountRecord>> = container.accountStore.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedAccountId: StateFlow<String?> = container.settings.selectedAccountId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        // 选中账号被删空/失效时自动落到第一个账号
        viewModelScope.launch {
            combine(container.accountStore.observe(), container.settings.selectedAccountId) { list, selected ->
                val stale = selected == null || list.none { it.accountId == selected }
                if (stale && list.isNotEmpty()) list.first().accountId else null
            }.collect { target ->
                if (target != null) container.settings.select(target)
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    /** UI 侧的非网络提示（如打不开浏览器）也走同一条回显通道 */
    fun notice(message: String) {
        _message.value = message
    }

    fun select(accountId: String) {
        viewModelScope.launch { container.settings.select(accountId) }
    }

    /** 多行授权码批量绑定（原 App 交互） */
    fun bind(lines: List<String>, onSaved: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                val outcome = container.authRepository.bind(lines)
                val parts = ArrayList<String>()
                if (outcome.bound.isNotEmpty()) {
                    parts += "${outcome.bound.size} ${Copy.boundAccountsSuffix}"
                }
                outcome.errors.forEach { parts += it.display }
                _message.value = parts.joinToString("\n").ifBlank { Copy.atLeastOneCode }
                if (outcome.bound.isNotEmpty()) {
                    if (selectedAccountId.value == null) container.settings.select(outcome.bound.first().accountId)
                    onSaved()
                }
            } catch (failure: EpicException) {
                _message.value = failure.failure.displayMessage()
            } catch (failure: Exception) {
                _message.value = "未预期的错误：${failure.javaClass.simpleName} ${failure.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun refresh(accountId: String) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                container.authRepository.refresh(accountId)
                _message.value = Copy.tokenRefreshed
            } catch (failure: EpicException) {
                _message.value = failure.failure.displayMessage()
                container.authRepository.markError(accountId, failure.failure.displayMessage())
            } catch (failure: Exception) {
                _message.value = "未预期的错误：${failure.javaClass.simpleName} ${failure.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun remove(accountId: String) {
        viewModelScope.launch {
            try {
                container.authRepository.remove(accountId)
            } catch (failure: Exception) {
                _message.value = "删除本地记录已完成，但回收远端设备凭据失败：${failure.message}"
            }
        }
    }

    fun now(): Long = container.clock.nowMillis()
}
