package com.example.fn_app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.fn_app.copy.Copy

enum class FnTopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TODAY_SHOP("today", Copy.tabTodayShop, Icons.Filled.Storefront),
    FRIENDS("friends", Copy.tabFriends, Icons.Filled.People),
    ACCOUNTS("accounts", Copy.tabAccounts, Icons.Filled.AccountBalanceWallet),
}
