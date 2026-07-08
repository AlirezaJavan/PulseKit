package io.github.alirezajavan.pulsekit.export

import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.SyncStatus
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ExporterTest {

    private val mixedEvents = listOf(
        SensorEventLog(
            id = "1",
            sensorType = "location",
            timestamp = 1000L,
            payload = SensorPayload.Location(1.0, 2.0, 3.0f, 4.0f),
            syncStatus = SyncStatus.IDLE,
        ),
        SensorEventLog(
            id = "2",
            sensorType = "step",
            timestamp = 2000L,
            payload = SensorPayload.StepCount(10),
            syncStatus = SyncStatus.IDLE,
        ),
        SensorEventLog(
            id = "3",
            sensorType = "ble",
            timestamp = 3000L,
            payload = SensorPayload.BluetoothScan("addr", "name", -50),
            syncStatus = SyncStatus.IDLE,
        ),
    )

    @Test
    fun gpxExporterSkipsNonLocationPayloads() = runTest {
        val output = StringBuilder()
        GpxExporter.export(mixedEvents.asFlow(), output)

        val result = output.toString()
        assertTrue(result.contains("lat=\"1.0\" lon=\"2.0\""))
        assertTrue(!result.contains("StepCount"))
        assertTrue(!result.contains("BluetoothScan"))
        assertTrue(result.contains("</gpx>"))
    }

    @Test
    fun csvExporterHandlesMixedPayloads() = runTest {
        val output = StringBuilder()
        CsvExporter.export(mixedEvents.asFlow(), output)

        val result = output.toString()
        assertTrue(result.contains("timestamp,type,lat,lon,accuracy,speed,steps,x,y,z"))
        assertTrue(result.contains("1000,location,1.0,2.0,3.0,4.0,,,,"))
        assertTrue(result.contains("2000,step,,,,,10,,,"))
    }

    @Test
    fun ndjsonExporterSerializesAllPayloads() = runTest {
        val output = StringBuilder()
        NdjsonExporter.export(mixedEvents.asFlow(), output)

        val result = output.toString()
        val lines = result.trim().split("\n")
        assertTrue(lines.size == 3)
        assertTrue(lines[0].contains("\"type\":\"location\""))
        assertTrue(lines[1].contains("\"type\":\"step\""))
        assertTrue(lines[2].contains("\"type\":\"ble\""))
    }
}
