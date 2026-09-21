package com.example.fn_app.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fn_app.copy.Copy
import com.example.fn_app.di.LocalContainer
import com.example.fn_app.ui.common.ConfirmDialogActions
import com.example.fn_app.ui.common.ErrorBanner
import com.fnassistantdog.core.EpicEndpoints
import com.fnassistantdog.core.auth.AccountRecord
import com.fnassistantdog.core.auth.AccountStatus

@Composable
fun AccountScreen(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val vm: AccountViewModel = viewModel(factory = container.viewModelFactory)
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val selectedId by vm.selectedAccountId.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var input by rememberSaveable { mutableStateOf("") }
    var showAbout by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AccountRecord?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Copy.tabAccounts, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${accounts.size}/20 · ${Copy.accountLimitHint}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButtonCompat(onClick = { showAbout = true })
        }

        if (accounts.isEmpty()) {
            Text(
                text = Copy.noAccountsYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    isCurrent = account.accountId == selectedId,
                    busy = busy,
                    nowMillis = vm.now(),
                    onSelect = { vm.select(account.accountId) },
                    onRefresh = { vm.refresh(account.accountId) },
                    onDelete = { pendingDelete = account },
                )
            }
        }

        message?.let { text ->
            ErrorBanner(
                message = text,
                onRetry = null,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            TextButton(onClick = vm::dismissMessage) { Text(Copy.close) }
        }

        Spacer(Modifier.height(8.dp))
        Text(Copy.addAccountsTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            text = Copy.authCodeGuide,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(Copy.authCodesLabel) },
            placeholder = { Text(Copy.authCodeHint) },
            minLines = 3,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    runCatching { uriHandler.openUri(EpicEndpoints.AUTH_CODE_PAGE) }
                        .onFailure { vm.notice(Copy.openBrowserFailed) }
                },
            ) { Text(Copy.getAuthCode) }
            Button(
                onClick = {
                    val lines = input.lines()
                    input = ""
                    vm.bind(lines)
                },
                enabled = !busy,
            ) { Text(if (busy) Copy.refreshing else Copy.bindAccounts) }
        }
        Text(
            text = Copy.maxAccountsOnce,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("${Copy.deleteAccount}：${account.displayName}") },
            text = { Text(Copy.deleteAccountConfirm) },
            confirmButton = {
                ConfirmDialogActions(
                    onConfirm = {
                        vm.remove(account.accountId)
                        pendingDelete = null
                    },
                    onDismiss = { pendingDelete = null },
                    confirmText = Copy.delete,
                    destructive = true,
                )
            },
        )
    }
}

@Composable
private fun IconButtonCompat(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = Copy.aboutTitle,
        )
    }
}

@Composable
private fun AccountRow(
    account: AccountRecord,
    isCurrent: Boolean,
    busy: Boolean,
    nowMillis: Long,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onSelect),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = statusText(account, nowMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (account.accountStatus == AccountStatus.ACTIVE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            account.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRefresh, enabled = !busy) { Text(Copy.refresh) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete, enabled = !busy) { Text(Copy.delete) }
            }
        }
    }
}

private fun statusText(account: AccountRecord, nowMillis: Long): String {
    val base = when (account.accountStatus) {
        AccountStatus.ACTIVE -> Copy.statusActive
        AccountStatus.PENDING -> Copy.statusPending
        AccountStatus.EXPIRED -> Copy.statusNeedsReauth
        AccountStatus.ERROR -> Copy.statusError
        else -> Copy.statusUnknown
    }
    val remaining = account.accessTokenExpiresAt - nowMillis
    val token = if (remaining <= 0) {
        Copy.tokenExpired
    } else {
        val minutes = remaining / 60_000L
        val hours = minutes / 60
        if (hours > 0) "${Copy.tokenRemainingPrefix} ${hours}h${minutes % 60}m" else "${Copy.tokenRemainingPrefix} ${minutes}m"
    }
    return "$base · $token"
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Copy.aboutTitle) },
        text = {
            Column {
                listOf(Copy.aboutUnofficial, Copy.aboutCredential, Copy.aboutRisk).forEach { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "凭据加密：Android Keystore AES/GCM/NoPadding · 全 HTTPS",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(Copy.close) }
        },
    )
}
