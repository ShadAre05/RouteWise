package com.routewise.app.data.api

import com.routewise.app.core.Logger
import com.routewise.app.data.session.Session
import com.routewise.app.data.session.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@OptIn(ExperimentalSerializationApi::class)
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Client of the real RouteWise API. It is the single place where HTTP is used (centralised request handling):
 * it adds the bearer token, renews it once when the server answers 401, converts every failure into an
 * [ApiException] and logs requests without their bodies.
 */
class HttpRouteWiseApi(
    baseUrl: String,
    private val client: OkHttpClient,
    private val session: Session,
    private val logger: Logger,
) : RouteWiseApi {

    private val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val refreshMutex = Mutex()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private class Raw(val status: Int, val body: String)

    // ---- accounts -------------------------------------------------------------------------------------------

    override suspend fun register(email: String, password: String): TokenResponse =
        decode(call("POST", "auth/register", ApiJson.encodeToString(RegisterRequest(email, password, true)), false))

    override suspend fun login(email: String, password: String): TokenResponse =
        decode(call("POST", "auth/login", ApiJson.encodeToString(LoginRequest(email, password)), false))

    override suspend fun logout() {
        val refresh = session.current?.refreshToken ?: return
        call("POST", "auth/logout", ApiJson.encodeToString(RefreshRequest(refresh)), false)
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String) {
        call("POST", "auth/change-password", ApiJson.encodeToString(ChangePasswordRequest(oldPassword, newPassword)), true)
    }

    override suspend fun deleteAccount() {
        call("DELETE", "users/me", null, true)
    }

    // ---- vehicles -------------------------------------------------------------------------------------------

    override suspend fun listVehicles(): List<VehicleDto> = decode(call("GET", "vehicles", null, true))

    override suspend fun createVehicle(request: VehicleRequest): VehicleDto =
        decode(call("POST", "vehicles", ApiJson.encodeToString(request), true))

    override suspend fun updateVehicle(id: String, request: VehicleRequest): VehicleDto =
        decode(call("PATCH", "vehicles/$id", ApiJson.encodeToString(request), true))

    override suspend fun deleteVehicle(id: String) {
        call("DELETE", "vehicles/$id", null, true)
    }

    // ---- trips ----------------------------------------------------------------------------------------------

    override suspend fun createTrip(request: TripRequest): TripDto =
        decode(call("POST", "trips", ApiJson.encodeToString(request), true))

    override suspend fun listTrips(limit: Int, offset: Int): TripPageDto =
        decode(call("GET", "trips?limit=$limit&offset=$offset", null, true))

    override suspend fun getTrip(id: String): TripDto = decode(call("GET", "trips/$id", null, true))

    override suspend fun deleteTrip(id: String) {
        call("DELETE", "trips/$id", null, true)
    }

    // ---- transport ------------------------------------------------------------------------------------------

    /** Sends the request; on 401 renews the tokens once and repeats it. Throws [ApiException] unless 2xx. */
    private suspend fun call(method: String, path: String, body: String?, authorized: Boolean): Raw {
        val usedAccess = session.current?.accessToken
        var raw = send(method, path, body, authorized)
        if (raw.status == 401 && authorized && refreshTokens(usedAccess)) {
            raw = send(method, path, body, authorized)
        }
        if (raw.status !in 200..299) {
            throw toException(raw)
        }
        return raw
    }

    private suspend fun send(method: String, path: String, body: String?, authorized: Boolean): Raw {
        val builder = Request.Builder().url(base + "api/v1/" + path)
        if (authorized) {
            session.current?.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        }
        val requestBody = if (method == "GET" || method == "DELETE") null else (body ?: "").toRequestBody(jsonType)
        builder.method(method, requestBody)
        val request = builder.build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    logger.info("$method /api/v1/$path -> ${response.code}")
                    Raw(response.code, response.body?.string().orEmpty())
                }
            } catch (e: IOException) {
                logger.warn("$method /api/v1/$path failed: ${e.javaClass.simpleName}")
                throw ApiException(ApiErrorKind.NETWORK, cause = e)
            }
        }
    }

    /** Returns true when a fresh access token is available. Concurrent callers share one refresh request. */
    private suspend fun refreshTokens(usedAccess: String?): Boolean = refreshMutex.withLock {
        val current = session.current ?: return@withLock false
        if (current.accessToken != usedAccess) return@withLock true
        val raw = send("POST", "auth/refresh", ApiJson.encodeToString(RefreshRequest(current.refreshToken)), false)
        if (raw.status in 200..299) {
            val tokens: TokenResponse = decode(raw)
            session.update(Tokens(tokens.accessToken, tokens.refreshToken))
            true
        } else {
            session.clear(expired = true)
            false
        }
    }

    private fun toException(raw: Raw): ApiException {
        val error = try {
            ApiJson.decodeFromString<ErrorEnvelope>(raw.body).error
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        return ApiException(ApiException.kindOf(raw.status, error?.code), error?.message, error?.requestId)
    }

    private inline fun <reified T> decode(raw: Raw): T =
        try {
            ApiJson.decodeFromString<T>(raw.body)
        } catch (e: SerializationException) {
            throw ApiException(ApiErrorKind.UNKNOWN, "unexpected answer of the server", cause = e)
        }
}
