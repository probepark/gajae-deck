package io.devnogari.gajaedeck.control

import io.devnogari.gajaedeck.auth.Redactor
import io.devnogari.gajaedeck.bridge.createBridgeHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val controlJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

class KtorControlPlaneClient(
    supervisorBaseUrl: String,
    private val controlToken: String,
    private val client: HttpClient = createBridgeHttpClient(),
    redactor: Redactor = Redactor(setOf(controlToken)),
) : ControlPlaneClient {
    private val baseUrl = supervisorBaseUrl.trimEnd('/')
    private val redactor = redactor.withSecrets(setOf(controlToken))

    override suspend fun getProjects(): Result<List<Project>> = request(ProjectsResponse.serializer()) {
        client.get(controlUrl("projects")) { auth() }
    }.map { it.projects }

    override suspend fun getProject(id: String): Result<Project> = request(ProjectResponse.serializer()) {
        client.get(controlUrl("projects", id.encodeURLPathPart())) { auth() }
    }.map { it.project }

    override suspend fun getSessions(projectId: String): Result<List<Session>> = request(SessionsResponse.serializer()) {
        client.get(controlUrl("projects", projectId.encodeURLPathPart(), "sessions")) { auth() }
    }.map { it.sessions }

    override suspend fun startSession(projectId: String, resume: String, scopes: List<String>): Result<SessionRoute> =
        request(SessionResponse.serializer()) {
            client.post(controlUrl("projects", projectId.encodeURLPathPart(), "sessions")) {
                auth()
                contentType(ContentType.Application.Json)
                setBody(controlJson.encodeToString(StartSessionRequest.serializer(), StartSessionRequest(resume, scopes)))
            }
        }.mapCatching { it.route ?: throw ControlPlaneException(ControlError("route_missing", "route missing", false)) }

    override suspend fun stopSession(sessionId: String): Result<Session> = request(SessionResponse.serializer()) {
        client.post(controlUrl("sessions", "${sessionId.encodeURLPathPart()}:stop")) { auth() }
    }.map { it.session }

    override suspend fun respawnSession(sessionId: String): Result<SessionRoute> = request(SessionResponse.serializer()) {
        client.post(controlUrl("sessions", "${sessionId.encodeURLPathPart()}:respawn")) { auth() }
    }.mapCatching { it.route ?: throw ControlPlaneException(ControlError("route_missing", "route missing", false)) }

    override suspend fun registerDevice(reg: DeviceRegistration): Result<DeviceRegistration> = request(DeviceRegistrationResponse.serializer()) {
        client.post(controlUrl("devices")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(
                controlJson.encodeToString(
                    DeviceRegistrationRequest.serializer(),
                    DeviceRegistrationRequest(
                        installId = reg.installId,
                        platform = reg.platform,
                        environment = reg.environment,
                        pushToken = reg.pushToken,
                        appVersion = reg.appVersion,
                        locale = reg.locale,
                        capabilities = reg.capabilities,
                    ),
                ),
            )
        }
        }.map { DeviceRegistration(deviceId = it.deviceId, registeredAt = it.registeredAt, platform = reg.platform, appVersion = reg.appVersion) }

    override suspend fun health(): Result<HealthResponse> = request(HealthResponse.serializer()) {
        client.get(controlUrl("health")) { auth() }
    }

    override suspend fun metrics(): Result<MetricsResponse> = request(MetricsResponse.serializer()) {
        client.get(controlUrl("metrics")) { auth() }
    }

    private fun controlUrl(vararg parts: String): String = buildString {
        append(baseUrl)
        append("/control/v1")
        parts.forEach { part -> append('/').append(part) }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer $controlToken")
    }

    private suspend inline fun <T> request(serializer: KSerializer<T>, crossinline block: suspend () -> io.ktor.client.statement.HttpResponse): Result<T> = runCatching {
        val response = block()
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw mapError(response.status.value, text)
        controlJson.decodeFromString(serializer, text)
    }.recoverCatching { throwable ->
        if (throwable is ControlPlaneException) throw throwable
        throw ControlPlaneException(ControlError("network", redactor.redact(throwable.message), true))
    }

    private fun mapError(status: Int, body: String): ControlPlaneException {
        val redactedBody = redactor.redact(body)
        val parsed = runCatching { controlJson.parseToJsonElement(redactedBody).jsonObject }.getOrNull()
        val errorObject = parsed?.get("error") as? JsonObject
        val decoded = errorObject?.let {
            runCatching { controlJson.decodeFromJsonElement(ControlError.serializer(), it) }.getOrNull()
        }
        val fallbackCode = runCatching { parsed?.get("code")?.jsonPrimitive?.content }.getOrNull()
        val code = decoded?.code ?: fallbackCode ?: when (status) {
            401 -> "unauthorized"
            403 -> "scope_denied"
            404 -> "not_found"
            409 -> "conflict"
            429 -> "rate_limited"
            502 -> "bridge_start_failed"
            503 -> "provider_unhealthy"
            else -> "http_$status"
        }
        return ControlPlaneException(
            ControlError(
                code = code,
                message = redactor.redact(decoded?.message ?: code),
                retryable = decoded?.retryable ?: (status == 429 || status >= 500),
                details = decoded?.details,
            ),
        )
    }
}
