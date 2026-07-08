package io.github.alirezajavan.pulsekit.export

import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

object GpxExporter {
    suspend fun export(events: Flow<SensorEventLog>, output: Appendable) {
        output.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        output.append("<gpx version=\"1.1\" creator=\"PulseKit\" ")
        output.append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        output.append("  <trk>\n")
        output.append("    <trkseg>\n")

        events.collect { event ->
            val payload = event.payload
            if (payload is SensorPayload.Location) {
                val time = Instant.fromEpochMilliseconds(event.timestamp).toString()
                output.append("      <trkpt lat=\"${payload.latitude}\" ")
                output.append("lon=\"${payload.longitude}\">\n")
                output.append("        <time>$time</time>\n")
                output.append("        <speed>${payload.speed}</speed>\n")
                output.append("      </trkpt>\n")
            }
        }

        output.append("    </trkseg>\n")
        output.append("  </trk>\n")
        output.append("</gpx>")
    }
}
