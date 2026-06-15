package io.devnogari.gajaedeck.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactorV2Test {
    @Test
    fun redactsV2RouteControlBridgeOwnerAndDeviceTokens() {
        val secrets = setOf(
            "route-token-dummy-secret",
            "control-token-dummy-secret",
            "scoped-token-dummy-secret",
            "bridge-token-dummy-secret",
            "owner-token-dummy-secret",
            "device-token-dummy-secret",
            "device-id-dummy-secret",
        )
        val redacted = Redactor(secrets).redact(
            "route=route-token-dummy-secret control=control-token-dummy-secret " +
                "scoped=scoped-token-dummy-secret bridge=bridge-token-dummy-secret " +
                "ownerToken=owner-token-dummy-secret Authorization: Bearer bearer-dummy-secret " +
                "deviceToken=device-token-dummy-secret deviceId=device-id-dummy-secret",
        )

        secrets.forEach { assertFalse(redacted.contains(it), "secret leaked: $it in $redacted") }
        assertFalse(redacted.contains("bearer-dummy-secret"), redacted)
        assertTrue(redacted.contains("***"), redacted)
    }

    @Test
    fun redactsProviderPayloadAndDeepLinkTokenMaterial() {
        val secrets = setOf(
            "apns-token-dummy-secret",
            "fcm-token-dummy-secret",
            "route-token-dummy-secret",
            "control-token-dummy-secret",
            "device-token-dummy-secret",
            "dl-token-dummy-secret",
            "raw-route-id-dummy-secret",
        )
        val providerPayload = """
            {
              "apnsToken":"apns-token-dummy-secret",
              "fcmToken":"fcm-token-dummy-secret",
              "deviceToken":"device-token-dummy-secret",
              "url":"gajaedeck://notification/open?id=dl_opaque_safe&token=dl-token-dummy-secret&routeId=raw-route-id-dummy-secret",
              "Authorization":"Bearer control-token-dummy-secret",
              "routeToken":"route-token-dummy-secret"
            }
        """.trimIndent()

        val redacted = Redactor(secrets).redact(providerPayload)

        secrets.forEach { assertFalse(redacted.contains(it), "secret leaked: $it in $redacted") }
        assertTrue(redacted.contains("dl_opaque_safe"), redacted)
        assertTrue(redacted.contains("***"), redacted)
    }

    @Test
    fun redactsCwdRepoCustomerPathPromptCommandArgsAndDisplayName() {
        val secrets = setOf(
            "/Users/dummy/customer/repo",
            "forbidden-repo-name",
            "forbidden-customer-name",
            "/Users/dummy/customer/repo/src/Main.kt",
            "approve deployment for customer alpha",
            "gjc bridge --cwd /Users/dummy/customer/repo --token control-token-dummy-secret",
            "Forbidden Project Display Name",
            "control-token-dummy-secret",
        )
        val diagnostic = """
            cwd=/Users/dummy/customer/repo
            repo=forbidden-repo-name
            customer=forbidden-customer-name
            path=/Users/dummy/customer/repo/src/Main.kt
            prompt=approve deployment for customer alpha
            commandArgs=gjc bridge --cwd /Users/dummy/customer/repo --token control-token-dummy-secret
            displayName=Forbidden Project Display Name
        """.trimIndent()

        val redacted = Redactor(secrets).redact(diagnostic)

        secrets.forEach { assertFalse(redacted.contains(it), "sensitive value leaked: $it in $redacted") }
        assertTrue(redacted.contains("***"), redacted)
    }
}
