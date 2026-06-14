package io.devnogari.gajaedeck.observability

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import io.devnogari.gajaedeck.auth.Redactor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactingLogWriterTest {

    private class CapturingLogWriter : LogWriter() {
        val records = mutableListOf<String>()
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            records.add(message)
            throwable?.message?.let { records.add(it) }
        }
    }

    @Test
    fun redactsSecretsFromMessageAndThrowable() {
        val secret = "super-secret-token-xyz"
        val capture = CapturingLogWriter()
        val writer = RedactingLogWriter(Redactor(setOf(secret)), capture)

        writer.log(
            Severity.Error,
            "auth failed for $secret",
            "tag",
            IllegalStateException("ownerToken=$secret Authorization: Bearer $secret"),
        )

        assertTrue(capture.records.isNotEmpty())
        capture.records.forEach { assertFalse(it.contains(secret), "secret leaked into log record: $it") }
        assertTrue(capture.records.any { it.contains("***") }, "expected a masked value")
    }
}
