package org.charged_proton.secondopinion.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

enum class TelemetryScreen(val value: String) {
    RECORD("record"),
    HISTORY("history"),
    ASSESSMENT("assessment"),
}

enum class TelemetryEvent(val value: String) {
    RECORDING_STARTED("recording_started"),
    RECORDING_STOPPED("recording_stopped"),
    ASSESSMENT_REQUESTED("assessment_requested"),
    ASSESSMENT_RETRIED("assessment_retried"),
    DECISION_ACCEPTED("decision_accepted"),
    DECISION_REJECTED("decision_rejected"),
    DECISION_OVERRIDDEN("decision_overridden"),
    UPLOAD_SUCCEEDED("upload_succeeded"),
    UPLOAD_RETRY_SCHEDULED("upload_retry_scheduled"),
    UPLOAD_FAILED("upload_failed"),
}

enum class TelemetryOperation(val value: String) {
    ASSESSMENT_UPLOAD("assessment_upload"),
    UNCAUGHT_CRASH("uncaught_crash"),
}

interface AppTelemetry {
    fun setCollectionEnabled(enabled: Boolean)
    fun screen(screen: TelemetryScreen)
    fun event(event: TelemetryEvent)
    fun recordNonFatal(operation: TelemetryOperation, failure: Throwable)
}

internal interface TelemetrySink {
    fun setCollectionEnabled(enabled: Boolean)
    fun screen(screen: TelemetryScreen)
    fun event(event: TelemetryEvent)
    fun recordNonFatal(operation: TelemetryOperation, failure: Throwable)
}

internal class ConsentGatedTelemetry(private val sink: TelemetrySink) : AppTelemetry {
    @Volatile
    private var enabled = false

    override fun setCollectionEnabled(enabled: Boolean) {
        this.enabled = enabled
        sink.setCollectionEnabled(enabled)
    }

    override fun screen(screen: TelemetryScreen) {
        if (enabled) sink.screen(screen)
    }

    override fun event(event: TelemetryEvent) {
        if (enabled) sink.event(event)
    }

    override fun recordNonFatal(operation: TelemetryOperation, failure: Throwable) {
        if (enabled) {
            sink.recordNonFatal(
                operation,
                sanitizeFailure(operation, failure),
            )
        }
    }
}

private class FirebaseTelemetrySink(context: Context) : TelemetrySink {
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    init {
        val crashlyticsHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            crashlyticsHandler?.uncaughtException(
                thread,
                sanitizeFailure(TelemetryOperation.UNCAUGHT_CRASH, failure),
            )
        }
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        if (!enabled) {
            analytics.resetAnalyticsData()
            crashlytics.deleteUnsentReports()
        }
    }

    override fun screen(screen: TelemetryScreen) {
        analytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply { putString(FirebaseAnalytics.Param.SCREEN_NAME, screen.value) },
        )
    }

    override fun event(event: TelemetryEvent) {
        analytics.logEvent(event.value, null)
    }

    override fun recordNonFatal(operation: TelemetryOperation, failure: Throwable) {
        crashlytics.recordException(failure)
    }
}

internal class SanitizedNonFatalException(operation: String, type: String) :
    RuntimeException("$operation failed ($type)")

internal fun sanitizeFailure(operation: TelemetryOperation, failure: Throwable): Throwable =
    SanitizedNonFatalException(operation.value, failure::class.java.simpleName).apply {
        stackTrace = failure.stackTrace
    }

fun createAppTelemetry(context: Context): AppTelemetry =
    if (FirebaseApp.getApps(context).isEmpty()) {
        NoOpAppTelemetry
    } else {
        ConsentGatedTelemetry(FirebaseTelemetrySink(context))
    }

object NoOpAppTelemetry : AppTelemetry {
    override fun setCollectionEnabled(enabled: Boolean) = Unit
    override fun screen(screen: TelemetryScreen) = Unit
    override fun event(event: TelemetryEvent) = Unit
    override fun recordNonFatal(operation: TelemetryOperation, failure: Throwable) = Unit
}