package io.github.alirezajavan.pulsekit.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semantic tone for [Pill], reused for source/sync/permission status across every screen. */
enum class Tone { POSITIVE, ATTENTION, NEUTRAL, NEGATIVE }

@Composable
private fun Tone.containerColor(): Color = when (this) {
    Tone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer
    Tone.ATTENTION -> MaterialTheme.colorScheme.tertiaryContainer
    Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    Tone.NEGATIVE -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun Tone.contentColor(): Color = when (this) {
    Tone.POSITIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    Tone.ATTENTION -> MaterialTheme.colorScheme.onTertiaryContainer
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    Tone.NEGATIVE -> MaterialTheme.colorScheme.onErrorContainer
}

/** Small rounded status pill, e.g. "Collecting", "Waiting for permission", "Unsupported". */
@Composable
fun Pill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tone.containerColor())
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = tone.contentColor(),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Small live/dead dot indicator, e.g. next to "Scanning" or a stale sensor reading. */
@Composable
fun LiveDot(active: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Circle,
        contentDescription = null,
        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier.size(10.dp),
    )
}

/** Icon in a colored circular container -- the recurring "feature icon" motif across screens. */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tone.containerColor()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tone.contentColor())
    }
}

/** Section label used above each grouped block of cards on a screen. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

/** Consistent outlined/tonal card shell every screen builds its content blocks from. */
@Composable
fun DemoCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Big number + caption used for the Home screen's hero stats and Sensors screen's readouts. */
@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Placeholder shown when a screen has nothing to show yet (sensor not started, no scans, etc). */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon = icon, tone = Tone.NEUTRAL)
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
