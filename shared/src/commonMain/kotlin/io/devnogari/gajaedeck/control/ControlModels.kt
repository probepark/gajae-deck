package io.devnogari.gajaedeck.control

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ControlEnvelope<T>(
    val schemaVersion: Int,
    val requestId: String,
    val serverTime: String,
    val data: T,
)

@Serializable
data class ProjectsResponse(
    val schemaVersion: Int,
    val requestId: String,
    val serverTime: String,
    val projects: List<Project>,
)

@Serializable
data class ProjectResponse(
    val schemaVersion: Int,
    val requestId: String,
    val serverTime: String,
    val project: Project,
)

@Serializable
data class SessionsResponse(
    val schemaVersion: Int,
    val requestId: String,
    val serverTime: String,
    val sessions: List<Session>,
)

@Serializable
data class SessionResponse(
    val schemaVersion: Int,
    val requestId: String,
    val serverTime: String,
    val session: Session,
    val route: SessionRoute? = null,
)

@Serializable
data class ControlErrorResponse(
    val schemaVersion: Int? = null,
    val requestId: String? = null,
    val serverTime: String? = null,
    val error: ControlError,
)

@Serializable
data class Project(
    val id: String,
    @SerialName("displayAlias") val displayName: String,
    val cwdHash: String,
    val status: ProjectStatus,
    val lastActiveAt: String? = null,
    val lastSessionId: String? = null,
    val autostart: String,
    val health: String,
    val safeSummaryAlias: String,
    val sessionCounts: Map<String, Int>? = null,
    val persistenceBudget: PersistenceBudget? = null,
)

@Serializable
enum class ProjectStatus {
    @SerialName("running") RUNNING,
    @SerialName("stopped") STOPPED,
    @SerialName("starting") STARTING,
    @SerialName("stopping") STOPPING,
    @SerialName("idle") IDLE,
    @SerialName("degraded") DEGRADED,
    @SerialName("error") ERROR,
}

@Serializable
data class PersistenceBudget(
    val maxRestoredSessions: Int,
    val staleSessionTtlSeconds: Long,
    val idleSoftLimitSeconds: Long,
)

@Serializable
data class Session(
    val id: String,
    val projectId: String,
    val routeIdHash: String,
    val gjcSessionId: String? = null,
    val status: SessionStatus,
    val scopes: List<String>,
    val startedAt: String,
    val lastActiveAt: String? = null,
    val lastSeq: Long,
    val restoreEligibility: RestoreStatus? = null,
    val skipReason: String? = null,
    val error: ControlError? = null,
    val bridgePid: Long? = null,
)

@Serializable
enum class SessionStatus {
    @SerialName("starting") STARTING,
    @SerialName("ready") READY,
    @SerialName("reconnecting") RECONNECTING,
    @SerialName("idle") IDLE,
    @SerialName("degraded") DEGRADED,
    @SerialName("stopped") STOPPED,
    @SerialName("error") ERROR,
}

@Serializable
data class SessionRoute(
    val sessionId: String,
    val projectId: String,
    val routeId: String,
    val routePath: String,
    val baseUrl: String,
    val scopedToken: String,
    val scopes: List<String>,
    val expiresAt: String? = null,
    val revocation: String,
    val ownerToken: String? = null,
)

@Serializable
data class ControlError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val details: JsonObject? = null,
)

class ControlPlaneException(val error: ControlError) : Exception(error.message)

@Serializable
data class DeviceRegistration(
    val deviceId: String,
    val registeredAt: String? = null,
    val installId: String = deviceId,
    val platform: String,
    val environment: String = "production",
    val pushToken: String? = null,
    val appVersion: String,
    val locale: String? = null,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class DeviceRegistrationRequest(
    val installId: String,
    val platform: String,
    val environment: String,
    val pushToken: String? = null,
    val appVersion: String,
    val locale: String? = null,
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class DeviceRegistrationResponse(
    val schemaVersion: Int? = null,
    val requestId: String? = null,
    val serverTime: String? = null,
    val deviceId: String,
    val registeredAt: String,
)

@Serializable
data class GateNotificationSummary(
    val notificationId: String? = null,
    val sessionId: String? = null,
    val sessionAlias: String? = null,
    val gateKind: String? = null,
    val safeTitleAlias: String? = null,
    val safeBodyAlias: String? = null,
    val collapseIdHash: String? = null,
    val deepLinkOpaqueId: String? = null,
    val frameId: String? = null,
    val createdAt: String? = null,
    val createdAtBucket: String? = null,
)

@Serializable
enum class RestoreStatus {
    @SerialName("eligible") ELIGIBLE,
    @SerialName("skipped") SKIPPED,
    @SerialName("restored") RESTORED,
    @SerialName("ineligible") INELIGIBLE,
}

@Serializable
data class HealthResponse(
    val schemaVersion: Int? = null,
    val requestId: String? = null,
    val serverTime: String? = null,
    val status: String,
    val dependencies: Map<String, String> = emptyMap(),
)

@Serializable
data class MetricsResponse(
    val schemaVersion: Int? = null,
    val requestId: String? = null,
    val serverTime: String? = null,
    val counters: JsonObject = JsonObject(emptyMap()),
    val gauges: JsonObject = JsonObject(emptyMap()),
    val labels: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class StartSessionRequest(
    val resume: String = "latest",
    val scopes: List<String> = emptyList(),
)
