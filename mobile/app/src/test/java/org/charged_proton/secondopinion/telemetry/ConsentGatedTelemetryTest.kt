package org.charged_proton.secondopinion.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentGatedTelemetryTest {
    private val sink = RecordingSink()
    private val telemetry = ConsentGatedTelemetry(sink)

    @Test
    fun `events and failures are blocked before consent`() {
        telemetry.screen(TelemetryScreen.RECORD)
        telemetry.event(TelemetryEvent.RECORDING_STARTED)
        telemetry.recordNonFatal(TelemetryOperation.ASSESSMENT_UPLOAD, RuntimeException("patient"))

        assertTrue(sink.screens.isEmpty())
        assertTrue(sink.events.isEmpty())
        assertTrue(sink.failures.isEmpty())
    }

    @Test
    fun `enabled telemetry forwards only fixed schema values`() {
        telemetry.setCollectionEnabled(true)
        telemetry.screen(TelemetryScreen.ASSESSMENT)
        telemetry.event(TelemetryEvent.DECISION_ACCEPTED)
        telemetry.setCollectionEnabled(false)
        telemetry.event(TelemetryEvent.UPLOAD_FAILED)

        assertEquals(listOf(TelemetryScreen.ASSESSMENT), sink.screens)
        assertEquals(listOf(TelemetryEvent.DECISION_ACCEPTED), sink.events)
    }

    @Test
    fun `non fatal report removes original message and cause`() {
        telemetry.setCollectionEnabled(true)
        telemetry.recordNonFatal(
            TelemetryOperation.ASSESSMENT_UPLOAD,
            IllegalStateException("case and patient details"),
        )

        val reported = sink.failures.single()
        assertEquals("assessment_upload failed (IllegalStateException)", reported.message)
        assertFalse(reported.message.orEmpty().contains("patient"))
        assertEquals(null, reported.cause)
    }

    @Test
    fun `uncaught crash sanitizer removes original data`() {
        val reported = sanitizeFailure(
            TelemetryOperation.UNCAUGHT_CRASH,
            IllegalArgumentException("transcript contents"),
        )

        assertEquals("uncaught_crash failed (IllegalArgumentException)", reported.message)
        assertFalse(reported.message.orEmpty().contains("transcript"))
        assertEquals(null, reported.cause)
    }

    private class RecordingSink : TelemetrySink {
        val screens = mutableListOf<TelemetryScreen>()
        val events = mutableListOf<TelemetryEvent>()
        val failures = mutableListOf<Throwable>()

        override fun setCollectionEnabled(enabled: Boolean) = Unit
        override fun screen(screen: TelemetryScreen) { screens += screen }
        override fun event(event: TelemetryEvent) { events += event }
        override fun recordNonFatal(operation: TelemetryOperation, failure: Throwable) {
            failures += failure
        }
    }
}