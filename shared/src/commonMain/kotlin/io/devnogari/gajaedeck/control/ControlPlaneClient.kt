package io.devnogari.gajaedeck.control

interface ControlPlaneClient {
    suspend fun getProjects(): Result<List<Project>>
    suspend fun getProject(id: String): Result<Project>
    suspend fun getSessions(projectId: String): Result<List<Session>>
    suspend fun startSession(projectId: String, resume: String, scopes: List<String>): Result<SessionRoute>
    suspend fun stopSession(sessionId: String): Result<Session>
    suspend fun respawnSession(sessionId: String): Result<SessionRoute>
    suspend fun registerDevice(reg: DeviceRegistration): Result<DeviceRegistration>
    suspend fun health(): Result<HealthResponse>
    suspend fun metrics(): Result<MetricsResponse>
}
