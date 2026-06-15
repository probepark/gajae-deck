package io.devnogari.gajaedeck.notifications

import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.DeviceRegistration

/** Phase 3 control-plane registration; real iOS APNs token acquisition lands in G008. */
actual interface DeviceRegistrar {
    actual suspend fun register(
        controlPlaneClient: ControlPlaneClient,
        registration: DeviceRegistration,
    ): Result<DeviceRegistration>
}

private class PlatformDeviceRegistrar(
    private val pushTokenProvider: PushTokenProvider = platformPushTokenProvider(),
) : DeviceRegistrar {
    override suspend fun register(
        controlPlaneClient: ControlPlaneClient,
        registration: DeviceRegistration,
    ): Result<DeviceRegistration> = registerWithPlatformPushToken(controlPlaneClient, registration, pushTokenProvider)
}

actual fun platformDeviceRegistrar(): DeviceRegistrar = PlatformDeviceRegistrar()
