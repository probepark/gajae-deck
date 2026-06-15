package io.devnogari.gajaedeck.notifications

import io.devnogari.gajaedeck.control.ControlPlaneClient
import io.devnogari.gajaedeck.control.DeviceRegistration

/** Registers this install with the control plane after obtaining a platform push token. */
expect interface DeviceRegistrar {
    suspend fun register(controlPlaneClient: ControlPlaneClient, registration: DeviceRegistration): Result<DeviceRegistration>
}

expect fun platformDeviceRegistrar(): DeviceRegistrar
