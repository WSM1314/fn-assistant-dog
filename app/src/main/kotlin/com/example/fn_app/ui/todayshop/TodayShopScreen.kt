package com.example.fn_app.ui.todayshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fn_app.copy.Copy
import com.example.fn_app.di.LocalContainer
import com.example.fn_app.ui.common.EmptyState
import com.example.fn_app.ui.common.ErrorBanner
import com.example.fn_app.ui.common.LoadingState
import com.example.fn_app.ui.todayshop.TodayShopUiState.Source
import com.fnassistantdog.core.shop.ShopCard
import com.fnassistantdog.core.util.UtcDay

@Composable
fun TodayShopScreen(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val vm: TodayShopViewModel = viewModel(factory = container.viewModelFactory)
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Copy.tabTodayShop, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            if (state.loading) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
            Box(Modifier.weight(1f))
            IconButton(onClick = vm::refresh, enabled = !state.loading) {
                Icon(Icons.Filled.Refresh, contentDescription = Copy.refresh)
            }
        }
        Text(
            text = statusLine(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        state.errorMessage?.let { message ->
            ErrorBanner(message = message, onRetry = if (state.loaded) null else vm::refresh)
        }

        when {
            !state.loaded && state.loading -> LoadingState(Copy.loading)
            !state.loaded && !state.loading -> EmptyState(state.errorMessage ?: Copy.shopEmpty)
            state.cards.isEmpty() -> EmptyState(Copy.shopEmpty)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.cards, key = { if (it.offerId.isNotBlank()) it.offerId else it.name }) { card ->
                    ShopCardItem(card)
                }
            }
        }
    }
}

private fun statusLine(state: TodayShopUiState): String = when {
    state.utcDay.isBlank() -> Copy.shopNotLoaded
    state.source == Source.ONLINE -> "${Copy.shopUpdatedFrom} ${UtcDay.pretty(state.utcDay)} (UTC) · ${Copy.shopOnline}"
    else -> "${Copy.shopUpdatedFrom} ${UtcDay.pretty(state.utcDay)} (UTC) · ${Copy.shopFromCache}"
}

@Composable
private fun ShopCardItem(card: ShopCard) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(bottom = 8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(rarityColor(card.rarity).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (card.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }
                if (card.isBundle) {
                    Text(
                        text = "${Copy.bundleBadge} ×${card.itemCount}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    )
                }
            }
            Text(
                text = card.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.rarityLabel.ifBlank { card.typeDisplay },
                    style = MaterialTheme.typography.labelSmall,
                    color = rarityColor(card.rarity),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (card.regularPrice > card.price && card.price > 0) {
                    Text(
                        text = card.regularPrice.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = "${card.price} ${Copy.vbucksUnit}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 稀有度取色（value 为 fortnite-api 的小写枚举，抓包所见：common/uncommon/rare/epic/legendary/mythic/series） */
private fun rarityColor(rarity: String): Color = when (rarity.lowercase()) {
    "common" -> Color(0xFF9AA7B3)
    "uncommon" -> Color(0xFF49D18B)
    "rare" -> Color(0xFF3FA9F5)
    "epic" -> Color(0xFFB968F5)
    "legendary" -> Color(0xFFFF9F43)
    "mythic" -> Color(0xFFFFD200)
    "marvel", "dc", "icon", "star", "series" -> Color(0xFFFF5C8A)
    else -> Color(0xFF9AA7B3)
}
