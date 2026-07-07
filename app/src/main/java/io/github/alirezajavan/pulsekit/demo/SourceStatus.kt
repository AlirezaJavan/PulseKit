package io.github.alirezajavan.pulsekit.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController
import io.github.alirezajavan.pulsekit.ui.rememberDataSourcePermissionState

/**
 * The permission-aware "status pill + start button" body shared by every screen that shows a
 * [DataSource] card (Home, Sensors, Bluetooth) so the staged permission-request flow is only
 * wired up once.
 */
@Composable
fun SourceStatusBody(
    source: DataSource,
    permissionController: PermissionController,
    isCollecting: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (!source.isSupported) {
            Pill("Unsupported hardware", tone = Tone.NEUTRAL)
            return@Column
        }

        val permissionState = rememberDataSourcePermissionState(permissionController, source)
        val (label, tone) = when {
            isCollecting -> "Collecting" to Tone.POSITIVE
            permissionState.isRequesting -> "Requesting permission..." to Tone.ATTENTION
            permissionState.missingRequired.isNotEmpty() -> "Needs permission" to Tone.ATTENTION
            else -> "Idle" to Tone.NEUTRAL
        }
        Pill(label, tone = tone)

        if (isCollecting && permissionState.missingOptional.isNotEmpty()) {
            Text(
                "Without: ${permissionState.missingOptional.joinToString { it.name.replace('_', ' ').lowercase() }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (!isCollecting) {
            TextButton(
                onClick = { permissionState.request { canCollect -> if (canCollect) onStart() } },
                enabled = !permissionState.isRequesting,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text("Start collecting")
            }
        }
    }
}
