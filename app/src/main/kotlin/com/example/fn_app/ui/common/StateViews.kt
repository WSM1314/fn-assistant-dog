package com.example.fn_app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fn_app.copy.Copy

@Composable
fun LoadingState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 错误一律原文回显（含服务端 errorMessage），可恢复操作按需提供重试。 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.error,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) { Text(Copy.retry) }
            }
        }
    }
}

@Composable
fun ConfirmDialogActions(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) { Text(Copy.cancel) }
        Spacer(Modifier.width(8.dp))
        if (destructive) {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(confirmText) }
        } else {
            Button(onClick = onConfirm) { Text(confirmText) }
        }
    }
}
