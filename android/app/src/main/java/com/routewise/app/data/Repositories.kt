package com.routewise.app.data

import com.routewise.app.data.api.RouteWiseApi
import com.routewise.app.data.api.TripDto
import com.routewise.app.data.api.TripPageDto
import com.routewise.app.data.api.VehicleDto
import com.routewise.app.data.api.VehicleRequest
import com.routewise.app.data.session.Session
import com.routewise.app.data.session.Tokens

class AuthRepository(private val api: RouteWiseApi, private val session: Session) {
    suspend fun register(email: String, password: String) {
        val tokens = api.register(email.trim(), password)
        session.update(Tokens(tokens.accessToken, tokens.refreshToken))
    }

    suspend fun login(email: String, password: String) {
        val tokens = api.login(email.trim(), password)
        session.update(Tokens(tokens.accessToken, tokens.refreshToken))
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

    suspend fun deleteAccount() {
        api.deleteAccount()
        session.clear()
    }
}

class VehicleRepository(private val api: RouteWiseApi) {
    suspend fun list(): List<VehicleDto> = api.listVehicles()

    suspend fun add(request: VehicleRequest): VehicleDto = api.createVehicle(request)

    suspend fun update(id: String, request: VehicleRequest): VehicleDto = api.updateVehicle(id, request)

    suspend fun setDefault(id: String): VehicleDto = api.updateVehicle(id, VehicleRequest(isDefault = true))

    suspend fun delete(id: String) = api.deleteVehicle(id)
}

class TripRepository(private val api: RouteWiseApi) {
    suspend fun page(limit: Int = PAGE_SIZE, offset: Int = 0): TripPageDto = api.listTrips(limit, offset)

    suspend fun get(id: String): TripDto = api.getTrip(id)

    suspend fun delete(id: String) = api.deleteTrip(id)

    companion object {
        const val PAGE_SIZE = 50
    }
}
