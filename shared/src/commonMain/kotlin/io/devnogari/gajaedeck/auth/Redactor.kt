package io.devnogari.gajaedeck.auth

/** Replaces known secret values with a mask so tokens never leak into logs/diagnostics. */
class Redactor(secrets: Set<String> = emptySet(), private val mask: String = "***") {
    private val secrets: List<String> = secrets.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }

    fun withSecrets(extra: Set<String>): Redactor = Redactor(secrets.toSet() + extra, mask)

    fun redact(text: String?): String {
        var out: String = text ?: return ""
        for (s in secrets) out = out.replace(s, mask)
        out = bearerRegex.replace(out) { "Bearer $mask" }
        // Mask sensitive key/value forms even when the concrete secret was never injected, so the
        // default (unseeded) redactor still scrubs raw token/ownerToken/Authorization/owner-token-header
        // values out of arbitrary error/log text.
        out = sensitiveKvRegex.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + mask }
        return out
    }

    fun redactHeaders(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (k, v) ->
            if (k.equals("Authorization", true) || k.equals("X-GJC-Bridge-Owner-Token", true)) mask else redact(v)
        }

    companion object {
        private val bearerRegex = Regex("""Bearer\s+[A-Za-z0-9._\-]+""")
        private val sensitiveKvRegex = Regex(
            "(authorization|x-gjc-bridge-owner-token|owner[_-]?token|access[_-]?token|token)(\\s*[:=]\\s*)(\\S+)",
            RegexOption.IGNORE_CASE,
        )
    }
}
