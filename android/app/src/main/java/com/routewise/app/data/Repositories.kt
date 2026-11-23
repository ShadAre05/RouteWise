package com.routewise.app.data

import com.routewise.app.data.api.ApiErrorKind
import com.routewise.app.data.api.ApiException
import com.routewise.app.data.api.RouteWiseApi
import com.routewise.app.data.api.TripDto
import com.routewise.app.data.api.SpeedProfileDto
import com.routewise.app.data.api.TrendPointDto
import com.routewise.app.data.api.TripPageDto
import com.routewise.app.data.api.VehicleDto
import com.routewise.app.data.api.VehicleRequest
import com.routewise.app.data.session.Session
import com.routewise.app.data.session.Tokens

class AuthRepository(private val api: RouteWiseApi, private val session: Session) {
    suspend fun register(email: String, password: String) {
        val tokens = api.register(email.trim(), password)
        session.update(Tokens(tokens.accessToken, tokens.refreshToken, email.trim()))
    }

    suspend fun login(email: String, password: String) {
        val tokens = api.login(email.trim(), password)
        session.update(Tokens(tokens.accessToken, tokens.refreshToken, email.trim()))
    }

    /** Signs out even when the server cannot be reached: the local tokens are always removed. */
    suspend fun logout() {
        try {
            api.logout()
        } finally {
            session.clear()
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) =
        api.changePassword(oldPassword, newPassword)

    /**
     * Deleting an account asks for the password (UX SCR-07). The API needs only the bearer token, so the client
     * proves the password by signing in once more; a wrong password throws and nothing is deleted.
     */
    suspend fun deleteAccount(password: String) {
        val email = session.current?.email ?: throw ApiException(ApiErrorKind.UNAUTHORIZED)
        val tokens = api.login(email, password)
        session.update(Tokens(tokens.accessToken, tokens.refreshToken, email))
        api.deleteAccount()
        session.clear()
    }

    val email: String? get() = session.current?.email
}

class VehicleRepository(private val api: RouteWiseApi) {
    suspend fun list(): List<VehicleDto> = api.listVehicles()

    suspend fun add(request: VehicleRequest): VehicleDto = api.createVehicle(request)

    suspend fun update(id: String, request: VehicleRequest): VehicleDto = api.updateVehicle(id, request)

    suspend fun setDefault(id: String): VehicleDto = api.updateVehicle(id, VehicleRequest(isDefault = true))

    suspend fun delete(id: String) = api.deleteVehicle(id)
}

class TripRepository(private val api: RouteWiseApi, private val cache: TripCache = InMemoryTripCache()) {
    suspend fun page(limit: Int = PAGE_SIZE, offset: Int = 0): TripPageDto {
        val page = api.listTrips(limit, offset)
        if (offset == 0) cache.save(page.items)
        return page
    }

    /** The trips stored by the last successful load, for the offline view of the history. */
    fun cached(): List<TripDto> = cache.load()

    suspend fun get(id: String): TripDto = api.getTrip(id)

    suspend fun delete(id: String) {
        api.deleteTrip(id)
        cache.save(cache.load().filter { it.id != id })
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}

/** Public reference data: price trend and speed profiles. */
class ReferenceRepository(private val api: RouteWiseApi) {
    suspend fun fuelTrend(country: String, fuelType: String): List<TrendPointDto> =
        api.fuelTrend(country, fuelType).sortedBy { it.bulletinDate }

    suspend fun speedProfiles(): List<SpeedProfileDto> = api.speedProfiles()
}
