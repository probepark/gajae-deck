package io.devnogari.gajaedeck.bridge

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val OWNER_TOKEN_HEADER = "X-GJC-Bridge-Owner-Token"
private const val IDEMPOTENCY_HEADER = "Idempotency-Key"

/** Real [BridgeTransport] over Ktor. Trust/streaming differences live behind [bridgeEngineFactory]. */
class KtorBridgeTransport(
    baseUrl: String,
    private val token: String,
    sessionId: String? = null,
    private val ownerToken: String? = null,
    private val client: HttpClient = createBridgeHttpClient(),
) : BridgeTransport {

    private val endpoints = BridgeEndpoints(baseUrl, sessionId)

    override suspend fun health(): Boolean {
        val resp = client.get(endpoints.healthz)
        if (!resp.status.isSuccess()) return false
        val obj = parseObject(resp.bodyAsText())
        return obj?.get("status")?.jsonPrimitive?.contentOrNull == "ok"
    }

    override suspend fun help(): JsonObject {
        val resp = client.get(endpoints.help)
        return parseObject(resp.bodyAsText()) ?: JsonObject(emptyMap())
    }

    override suspend fun handshake(request: BridgeHandshakeRequest): BridgeHandshakeResult {
        val resp = client.post(endpoints.handshake) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(bridgeJson.encodeToString(BridgeHandshakeRequest.serializer(), request))
        }
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) throw mapError(resp.status.value, text)
        val status = parseObject(text)?.get("status")?.jsonPrimitive?.contentOrNull
        return if (status == "accepted") {
            BridgeHandshakeResult.Accepted(bridgeJson.decodeFromString(BridgeHandshakeAccepted.serializer(), text))
        } else {
            BridgeHandshakeResult.Rejected(bridgeJson.decodeFromString(BridgeHandshakeRejected.serializer(), text))
        }
    }

    override fun events(lastSeq: Long): Flow<BridgeFrame> = channelFlow {
        client.prepareGet(endpoints.events(lastSeq)) { auth() }.execute { resp ->
            if (!resp.status.isSuccess()) throw mapError(resp.status.value, runCatching { resp.bodyAsText() }.getOrDefault(""))
            val parser = BridgeStreamParser()
            val channel = resp.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                for (frame in parser.feed(line + "\n")) send(frame)
            }
            for (frame in parser.flush()) send(frame)
        }
    }

    override suspend fun postCommand(type: String, params: JsonObject, idempotencyKey: String): CommandResponse =
        postJson(endpoints.commands, idempotencyKey, buildJsonObject {
            put("type", type)
            for ((k, v) in params) put(k, v)
        })

    override suspend fun postUiResponse(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
        postJson(endpoints.uiResponse(correlationId), idempotencyKey, body)

    override suspend fun postHostToolResult(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
        postJson(endpoints.hostToolResult(correlationId), idempotencyKey, body)

    override suspend fun postHostUriResult(correlationId: String, body: JsonObject, idempotencyKey: String): CommandResponse =
        postJson(endpoints.hostUriResult(correlationId), idempotencyKey, body)

    private suspend fun postJson(url: String, idempotencyKey: String, body: JsonObject): CommandResponse {
        val resp = client.post(url) {
            auth()
            header(IDEMPOTENCY_HEADER, idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = resp.bodyAsText()
        if (!resp.status.isSuccess()) throw mapError(resp.status.value, text)
        return bridgeJson.decodeFromString(CommandResponse.serializer(), text)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer $token")
        ownerToken?.let { header(OWNER_TOKEN_HEADER, it) }
    }

    private fun mapError(status: Int, body: String): BridgeException {
        val obj = parseObject(body)
        val errorCode = obj?.get("error")?.jsonPrimitive?.contentOrNull
        val scope = obj?.get("scope")?.jsonPrimitive?.contentOrNull
        val endpoint = obj?.get("endpoint")?.jsonPrimitive?.contentOrNull
        val code = when {
            errorCode != null && BridgeErrorCode.fromWire(errorCode) != BridgeErrorCode.UNKNOWN ->
                BridgeErrorCode.fromWire(errorCode)
            status == 401 -> BridgeErrorCode.UNAUTHORIZED
            status == 403 -> BridgeErrorCode.SERVER_REJECTED
            status == 409 -> BridgeErrorCode.IDEMPOTENCY_CONFLICT
            status == 503 -> BridgeErrorCode.COMMANDS_UNAVAILABLE
            else -> BridgeErrorCode.SERVER_REJECTED
        }
        return BridgeException(code, message = errorCode ?: "http $status", scope = scope, endpoint = endpoint)
    }

    private fun parseObject(text: String): JsonObject? =
        runCatching { bridgeJson.parseToJsonElement(text).jsonObject }.getOrNull()
}