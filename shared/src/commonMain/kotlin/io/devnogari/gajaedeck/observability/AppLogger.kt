package io.devnogari.gajaedeck.observability

import co.touchlab.kermit.Logger
import co.touchlab.kermit.mutableLoggerConfigInit
import co.touchlab.kermit.platformLogWriter
import io.devnogari.gajaedeck.auth.Redactor

/**
 * Thin logging facade over Kermit. Construct with [redacting] so every record passes through a
 * [RedactingLogWriter]; redaction is by construction, not at each call site.
 */
class AppLogger(private val logger: Logger) {

    fun debug(tag: String, message: String) = logger.d(tag) { message }
    fun info(tag: String, message: String) = logger.i(tag) { message }
    fun warn(tag: String, message: String, throwable: Throwable? = null) = logger.w(tag, throwable) { message }
    fun error(tag: String, message: String, throwable: Throwable? = null) = logger.e(tag, throwable) { message }

    companion object {
        fun redacting(
            redactor: Redactor,
            delegate: co.touchlab.kermit.LogWriter = platformLogWriter(),
        ): AppLogger {
            val config = mutableLoggerConfigInit(listOf(RedactingLogWriter(redactor, delegate)))
            return AppLogger(Logger(config, tag = "gajae-deck"))
        }
    }
}
