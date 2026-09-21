package com.example.fn_app.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fn_app.copy.Copy
import com.example.fn_app.di.LocalContainer
import com.example.fn_app.ui.common.AccountPicker
import com.example.fn_app.ui.common.EmptyState
import com.example.fn_app.ui.common.ErrorBanner
import com.example.fn_app.ui.common.LoadingState
import com.fnassistantdog.core.friends.FriendsRepository
import com.fnassistantdog.core.model.UserSearchEntity

@Composable
fun FriendsScreen(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val vm: FriendsViewModel = viewModel(factory = container.viewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val selectedId by vm.selectedAccountId.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Copy.tabFriends, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(6.dp))
            AccountPicker(
                accounts = accounts,
                selectedId = selectedId,
                onSelect = vm::select,
                modifier = Modifier.weight(1f),
            )
            if (state.loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
            IconButton(onClick = vm::reload, enabled = !state.loading) {
                Icon(Icons.Filled.Refresh, contentDescription = Copy.refresh)
            }
        }

        state.errorMessage?.let {
            ErrorBanner(message = it, onRetry = if (state.overview == null) vm::reload else null)
        }
        state.notice?.let { text ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::dismissNotice) { Text(Copy.close) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(Copy.addFriend) },
                placeholder = { Text(Copy.searchHint) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { vm.search(query) }, enabled = !state.searching && query.isNotBlank()) {
                Text(if (state.searching) Copy.loading else Copy.search)
            }
        }

        val overview = state.overview
        when {
            overview == null && state.loading -> LoadingState(Copy.loading)
            overview == null && accounts.isEmpty() -> EmptyState(Copy.noAccountsYet)
            overview == null -> EmptyState(state.errorMessage ?: Copy.friendsNoData)
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.searchResults.isNotEmpty()) {
                    item { SectionHeader("${Copy.addFriend} · ${state.searchResults.size}") }
                    items(state.searchResults, key = { "search-${it.accountId}" }) { entity ->
                        SearchResultRow(entity = entity, busy = state.loading, onAdd = {
                            vm.sendRequest(entity.accountId.orEmpty(), entity.shownName)
                        })
                    }
                }
                if (overview.incoming.isNotEmpty()) {
                    item { SectionHeader("${Copy.friendRequests} (${overview.incoming.size})") }
                    items(overview.incoming, key = { "in-${it.accountId}" }) { row ->
                        FriendRow(
                            row = row,
                            actions = {
                                TextButton(onClick = { vm.acceptRequest(row.accountId, row.name) }, enabled = !state.loading) { Text(Copy.accept) }
                                TextButton(onClick = { vm.remove(row.accountId, row.name) }, enabled = !state.loading) { Text(Copy.reject) }
                            },
                        )
                    }
                }
                item { SectionHeader("${Copy.sectionFriends} (${overview.friends.size})") }
                if (overview.friends.isEmpty()) {
                    item { EmptyState(Copy.friendsEmpty) }
                } else {
                    items(overview.friends, key = { "f-${it.accountId}" }) { row ->
                        FriendRow(
                            row = row,
                            actions = {
                                TextButton(onClick = { vm.remove(row.accountId, row.name) }, enabled = !state.loading) { Text(Copy.removeFriend) }
                            },
                        )
                    }
                }
                if (overview.outgoing.isNotEmpty()) {
                    item { SectionHeader("${Copy.sectionOutgoing} (${overview.outgoing.size})") }
                    items(overview.outgoing, key = { "out-${it.accountId}" }) { row ->
                        FriendRow(
                            row = row,
                            actions = {
                                OutlinedButton(onClick = { vm.remove(row.accountId, row.name) }, enabled = !state.loading) { Text(Copy.cancel) }
                            },
                        )
                    }
                }
                if (overview.blocked.isNotEmpty()) {
                    item { SectionHeader("${Copy.sectionBlocked} (${overview.blocked.size})") }
                    items(overview.blocked, key = { "b-${it.accountId}" }) { row ->
                        FriendRow(row = row, actions = {})
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FriendRow(
    row: FriendsRepository.FriendRow,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = row.accountId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.favorite) {
            Text(Copy.favoriteMark, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(6.dp))
        }
        actions()
    }
}

@Composable
private fun SearchResultRow(
    entity: UserSearchEntity,
    busy: Boolean,

    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entity.shownName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = entity.accountId.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(onClick = onAdd, enabled = !busy) { Text(Copy.sendRequest) }
    }
}
