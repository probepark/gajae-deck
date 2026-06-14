package io.devnogari.gajaedeck.observability

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import io.devnogari.gajaedeck.auth.Redactor

/**
 * A Kermit [LogWriter] that redacts secrets out of every record before delegating. Both the message
 * and the throwable's message are passed through the [Redactor], so bearer tokens, owner tokens, and
 * Authorization values can never reach the underlying log sink.
 */
class RedactingLogWriter(
    private val redactor: Redactor,
    private val delegate: LogWriter,
) : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val safeThrowable = throwable?.let { RedactedThrowable(redactor.redact("${it::class.simpleName}: ${it.message ?: ""}")) }
        delegate.log(severity, redactor.redact(message), tag, safeThrowable)
    }
}

/** Carries a pre-redacted message so the delegate writer never formats a raw secret-bearing throwable. */
private class RedactedThrowable(message: String) : Throwable(message)
