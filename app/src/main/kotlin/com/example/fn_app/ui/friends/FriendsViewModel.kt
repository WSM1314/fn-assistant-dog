package com.example.fn_app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fn_app.copy.Copy
import com.example.fn_app.di.AppContainer
import com.fnassistantdog.core.auth.AccountRecord
import com.fnassistantdog.core.error.EpicException
import com.fnassistantdog.core.friends.FriendsRepository.Overview
import com.fnassistantdog.core.model.UserSearchEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val loading: Boolean = false,
    val overview: Overview? = null,
    val errorMessage: String? = null,
    val notice: String? = null,
    val searching: Boolean = false,
    val searchResults: List<UserSearchEntity> = emptyList(),
)

/** 好友页。账号切换即重新拉取（Epic friends 端点需要该账号的有效令牌）。 */
class FriendsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val accounts: StateFlow<List<AccountRecord>> = container.accountStore.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedAccountId: StateFlow<String?> = container.settings.selectedAccountId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    private var loadedFor: String? = null

    init {
        viewModelScope.launch {
            selectedAccountId.collect { accountId ->
                if (accountId == null) {
                    loadedFor = null
                    _state.update { it.copy(overview = null, errorMessage = Copy.noAccountsYet) }
                } else if (accountId != loadedFor) {
                    loadedFor = accountId
                    load(accountId)
                }
            }
        }
    }

    fun select(accountId: String) {
        viewModelScope.launch { container.settings.select(accountId) }
    }

    fun reload() {
        val accountId = selectedAccountId.value ?: return
        loadedFor = accountId
        load(accountId)
    }

    private fun load(accountId: String) {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                container.session.use(accountId)
                val overview = container.friendsRepository.load(accountId)
                _state.update { it.copy(loading = false, overview = overview, errorMessage = null) }
            } catch (failure: EpicException) {
                _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = failure.failure.displayMessage(),
                    )
                }
            } catch (failure: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        errorMessage = "未预期的错误：${failure.javaClass.simpleName} ${failure.message}",
                    )
                }
            }
        }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        _state.update { it.copy(searching = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val results = container.friendsRepository.search(trimmed)
                _state.update { it.copy(searching = false, searchResults = results) }
            } catch (failure: EpicException) {
                _state.update {
                    it.copy(searching = false, searchResults = emptyList(), errorMessage = failure.failure.displayMessage())
                }
            } catch (failure: Exception) {
                _state.update { it.copy(searching = false, errorMessage = failure.message) }
            }
        }
    }

    fun sendRequest(targetAccountId: String, targetName: String) = mutate {
        container.friendsRepository.sendRequest(it, targetAccountId)
        "${Copy.requestSent}：$targetName"
    }

    fun acceptRequest(targetAccountId: String, targetName: String) = mutate {
        container.friendsRepository.acceptRequest(it, targetAccountId)
        "${Copy.requestAccepted}：$targetName"
    }

    fun remove(targetAccountId: String, targetName: String) = mutate {
        container.friendsRepository.remove(it, targetAccountId)
        "${Copy.friendRemoved}：$targetName"
    }

    private fun mutate(action: suspend (String) -> String) {
        val accountId = selectedAccountId.value ?: return
        _state.update { it.copy(loading = true, errorMessage = null, notice = null) }
        viewModelScope.launch {
            try {
                container.session.use(accountId)
                val notice = action(accountId)
                val overview = container.friendsRepository.load(accountId)
                _state.update { state ->
                    state.copy(loading = false, notice = notice, overview = overview, searchResults = emptyList())
                }
            } catch (failure: EpicException) {
                _state.update { it.copy(loading = false, errorMessage = failure.failure.displayMessage()) }
            } catch (failure: Exception) {
                _state.update { it.copy(loading = false, errorMessage = failure.message) }
            }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }
}
