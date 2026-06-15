package io.devnogari.gajaedeck.notifications

import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.DeviceRegistration

fun missingPushTokenFailure(): Result<DeviceRegistration> =
    Result.failure(IllegalStateException("no push token (real provider deferred)"))

suspend fun registerWithPlatformPushToken(
    controlPlaneClient: ControlPlaneClient,
    registration: DeviceRegistration,
    pushTokenProvider: PushTokenProvider,
): Result<DeviceRegistration> {
    val pushToken = pushTokenProvider.currentToken() ?: return missingPushTokenFailure()
    return controlPlaneClient.registerDevice(registration.copy(pushToken = pushToken))
}
