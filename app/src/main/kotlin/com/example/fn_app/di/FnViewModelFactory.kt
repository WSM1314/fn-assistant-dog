package com.example.fn_app.di

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.fn_app.ui.account.AccountViewModel
import com.example.fn_app.ui.friends.FriendsViewModel
import com.example.fn_app.ui.todayshop.TodayShopViewModel

val LocalContainer = compositionLocalOf<AppContainer> { error("未提供 AppContainer") }

class FnViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(TodayShopViewModel::class.java) -> TodayShopViewModel(container) as T
        modelClass.isAssignableFrom(FriendsViewModel::class.java) -> FriendsViewModel(container) as T
        modelClass.isAssignableFrom(AccountViewModel::class.java) -> AccountViewModel(container) as T
        else -> throw IllegalArgumentException("未知 ViewModel：${modelClass.name}")
    }
}
