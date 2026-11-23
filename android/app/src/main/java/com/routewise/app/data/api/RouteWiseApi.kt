package com.routewise.app.data.api

/**
 * The contract between the app and the back-end. [HttpRouteWiseApi] talks to the real server, [FakeRouteWiseApi]
 * serves built-in sample data (demo mode and unit tests). Every function throws [ApiException] on failure.
 */
interface RouteWiseApi {
    suspend fun register(email: String, password: String): TokenResponse
    suspend fun login(email: String, password: String): TokenResponse
    suspend fun logout()
    suspend fun changePassword(oldPassword: String, newPassword: String)
    suspend fun deleteAccount()

    suspend fun listVehicles(): List<VehicleDto>
    suspend fun createVehicle(request: VehicleRequest): VehicleDto
    suspend fun updateVehicle(id: String, request: VehicleRequest): VehicleDto
    suspend fun deleteVehicle(id: String)

    suspend fun createTrip(request: TripRequest): TripDto
    suspend fun listTrips(limit: Int, offset: Int): TripPageDto
    suspend fun getTrip(id: String): TripDto
    suspend fun deleteTrip(id: String)

    /** Public reference data. */
    suspend fun fuelTrend(country: String, fuelType: String): List<TrendPointDto>
    suspend fun speedProfiles(): List<SpeedProfileDto>
}
