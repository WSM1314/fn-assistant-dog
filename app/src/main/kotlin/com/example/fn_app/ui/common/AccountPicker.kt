package com.example.fn_app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fnassistantdog.core.auth.AccountRecord

/** 多账号切换器（购买/赠送/好友都跟着它，见设计 §3.3）。 */
@Composable
fun AccountPicker(
    accounts: List<AccountRecord>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyLabel: String = "未添加账号",
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.accountId == selectedId } ?: accounts.firstOrNull()
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }, enabled = accounts.isNotEmpty()) {
            Text(
                text = selected?.displayName ?: emptyLabel,
                style = MaterialTheme.typography.titleMedium,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.displayName) },
                    onClick = {
                        onSelect(account.accountId)
                        expanded = false
                    },
                )
            }
        }
    }
}
