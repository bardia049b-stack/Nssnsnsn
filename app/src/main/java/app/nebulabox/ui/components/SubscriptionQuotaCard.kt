package app.nebulabox.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nebulabox.R
import app.nebulabox.data.SubscriptionItem
import app.nebulabox.util.SubscriptionUsage

private val LowColor = Color(0xFFE0A030)

@Composable
fun SubscriptionQuotaCard(
    subscription: SubscriptionItem,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val state = remember(
        subscription.usedBytes,
        subscription.totalBytes,
        subscription.expireAtSeconds,
    ) {
        SubscriptionUsage.statusOf(subscription)
    }

    val accent = when (state) {
        SubscriptionUsage.State.EXHAUSTED, SubscriptionUsage.State.EXPIRED -> MaterialTheme.colorScheme.error
        SubscriptionUsage.State.LOW -> LowColor
        else -> MaterialTheme.colorScheme.primary
    }

    val usage = remember(subscription.usedBytes, subscription.totalBytes) {
        SubscriptionUsage.usageLine(context, subscription)
    }
    val remaining = remember(subscription.usedBytes, subscription.totalBytes, subscription.expireAtSeconds) {
        SubscriptionUsage.remainingLine(context, subscription)
    }
    val expiry = remember(subscription.expireAtSeconds) {
        SubscriptionUsage.expiryLine(context, subscription)
    }
    val percent = remember(subscription.usedBytes, subscription.totalBytes) {
        SubscriptionUsage.progressLabel(subscription)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        onClick = onRefresh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DataUsage,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = subscription.remarks.ifBlank { stringResource(R.string.subscription_usage_empty) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (percent.isNotEmpty()) {
                    Text(
                        text = percent,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                    )
                }
            }

            if (subscription.hasQuota) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { subscription.quotaFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }

            Spacer(Modifier.height(9.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = remaining.ifBlank { usage },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = expiry.ifBlank { stringResource(R.string.subscription_remaining_unknown) },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state == SubscriptionUsage.State.EXPIRED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }

            if (remaining.isNotBlank() && usage.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = usage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SubscriptionQuotaDot(context: Context, subscription: SubscriptionItem) {
    val state = SubscriptionUsage.statusOf(subscription)
    val color = when (state) {
        SubscriptionUsage.State.EXHAUSTED, SubscriptionUsage.State.EXPIRED -> MaterialTheme.colorScheme.error
        SubscriptionUsage.State.LOW -> LowColor
        SubscriptionUsage.State.OK -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color),
    )
}
