package io.github.alirezajavan.pulsekit.demo

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.alirezajavan.pulsekit.core.EventQuery
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import io.github.alirezajavan.pulsekit.export.CsvExporter
import io.github.alirezajavan.pulsekit.export.GpxExporter
import io.github.alirezajavan.pulsekit.export.NdjsonExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HistoryScreen(pulseKit: PulseKit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val query = remember { EventQuery(limit = 100) }
    val recentEvents by pulseKit.observeEvents(query).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(8.dp)) {
            Button(
                modifier = Modifier.weight(1f).padding(4.dp),
                onClick = { scope.launch { export(context, pulseKit, query, "ndjson") } },
            ) { Text("JSON") }
            Button(
                modifier = Modifier.weight(1f).padding(4.dp),
                onClick = { scope.launch { export(context, pulseKit, query, "gpx") } },
            ) { Text("GPX") }
            Button(
                modifier = Modifier.weight(1f).padding(4.dp),
                onClick = { scope.launch { export(context, pulseKit, query, "csv") } },
            ) { Text("CSV") }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(recentEvents) { event ->
                EventItem(event)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EventItem(event: SensorEventLog) {
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Text(text = event.sensorType, style = MaterialTheme.typography.titleMedium)
        Text(text = "Time: ${event.timestamp}", style = MaterialTheme.typography.bodySmall)
        Text(text = "Payload: ${event.payload}", style = MaterialTheme.typography.bodyMedium)
    }
}

private suspend fun export(context: Context, pulseKit: PulseKit, query: EventQuery, format: String) {
    val events = pulseKit.queryEvents(query)
    if (events.isEmpty()) return

    val file = File(context.cacheDir, "pulsekit_export.$format")
    withContext(Dispatchers.IO) {
        file.bufferedWriter().use { writer ->
            val flow = events.asFlow()
            when (format) {
                "ndjson" -> NdjsonExporter.export(flow, writer)
                "gpx" -> GpxExporter.export(flow, writer)
                "csv" -> CsvExporter.export(flow, writer)
            }
        }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PulseKit Export"))
}
