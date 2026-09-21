package com.example.fn_app.ui.todayshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fn_app.di.AppContainer
import com.example.fn_app.ui.todayshop.TodayShopUiState.Source
import com.fnassistantdog.core.error.EpicException
import com.fnassistantdog.core.shop.ShopCard
import com.fnassistantdog.core.shop.ShopSnapshot
import com.fnassistantdog.core.shop.ShopSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayShopUiState(
    val loading: Boolean = false,
    val cards: List<ShopCard> = emptyList(),
    val utcDay: String = "",
    val source: Source = Source.NONE,
    val errorMessage: String? = null,
) {
    enum class Source { NONE, ONLINE, CACHE }

    val loaded: Boolean get() = source != Source.NONE
}

/** 打开页面即缓存优先渲染，缺当日缓存才打网络（这就是"冷启动补偿刷新"）。 */
class TodayShopViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(TodayShopUiState())
    val state: StateFlow<TodayShopUiState> = _state.asStateFlow()

    init {
        load(force = false)
    }

    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val snapshot = container.shopRepository.load(force)
                _state.update { snapshot.toState(errorMessage = null) }
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

    private fun ShopSnapshot.toState(errorMessage: String?) = TodayShopUiState(
        loading = false,
        cards = entries,
        utcDay = utcDay,
        source = if (this.source == ShopSource.ONLINE) Source.ONLINE else Source.CACHE,
        errorMessage = errorMessage ?: degraded,
    )
}
