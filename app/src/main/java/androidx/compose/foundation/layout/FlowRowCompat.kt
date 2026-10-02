package androidx.compose.foundation.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Thin wrapper so the call sites do not have to opt in to the experimental
 * FlowRow API every time they use it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowCompat(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}
