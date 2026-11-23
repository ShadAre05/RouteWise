package com.routewise.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Data transfer objects of the RouteWise API (see docs/api). Decimal numbers arrive as JSON strings. */

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("privacy_accepted") val privacyAccepted: Boolean,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int = 900,
)

@Serializable
data class VehicleDto(
    val id: String,
    val name: String,
    @SerialName("fuel_type") val fuelType: String,
    @SerialName("consumption_l100") val consumptionL100: String,
    @SerialName("tank_l") val tankL: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable
data class VehicleRequest(
    val name: String? = null,
    @SerialName("fuel_type") val fuelType: String? = null,
    @SerialName("consumption_l100") val consumptionL100: String? = null,
    @SerialName("tank_l") val tankL: String? = null,
    @SerialName("is_default") val isDefault: Boolean? = null,
)

@Serializable
data class PointDto(val lon: Double, val lat: Double)

@Serializable
data class TripRequest(
    @SerialName("origin_label") val originLabel: String,
    @SerialName("destination_label") val destinationLabel: String,
    val origin: PointDto,
    val destination: PointDto,
    val variant: String = "fastest",
    @SerialName("vehicle_id") val vehicleId: String? = null,
    @SerialName("daily_limit_h") val dailyLimitH: Int? = null,
    @SerialName("lodging_class") val lodgingClass: String = "standard",
    @SerialName("lodging_entered_eur") val lodgingEnteredEur: String? = null,
    @SerialName("start_fuel") val startFuel: String? = null,
)

@Serializable
data class CostLineDto(
    val kind: String,
    val country: String? = null,
    @SerialName("distance_km") val distanceKm: String? = null,
    @SerialName("unit_price") val unitPrice: String? = null,
    @SerialName("amount_eur") val amountEur: String? = null,
    val status: String = "ok",
)

@Serializable
data class RefuelStopDto(
    val seq: Int,
    val country: String,
    @SerialName("km_from_start") val kmFromStart: String,
    val liters: String,
    @SerialName("price_eur_l") val priceEurL: String,
)

@Serializable
data class TripDto(
    val id: String,
    val title: String,
    @SerialName("origin_label") val originLabel: String,
    @SerialName("destination_label") val destinationLabel: String,
    @SerialName("departed_at") val departedAt: String,
    @SerialName("arrival_at") val arrivalAt: String? = null,
    @SerialName("destination_country") val destinationCountry: String? = null,
    val variant: String,
    @SerialName("distance_km") val distanceKm: String,
    @SerialName("duration_min") val durationMin: Int,
    @SerialName("daily_limit_h") val dailyLimitH: String? = null,
    val nights: Int = 0,
    @SerialName("vehicle_name") val vehicleName: String,
    @SerialName("fuel_type") val fuelType: String,
    @SerialName("consumption_l100") val consumptionL100: String,
    @SerialName("start_fuel") val startFuel: String? = null,
    @SerialName("refuel_saving_eur") val refuelSavingEur: String? = null,
    @SerialName("price_date") val priceDate: String,
    @SerialName("total_eur") val totalEur: String,
    @SerialName("created_at") val createdAt: String? = null,
    val lines: List<CostLineDto> = emptyList(),
    @SerialName("refuel_stops") val refuelStops: List<RefuelStopDto> = emptyList(),
)

@Serializable
data class TripPageDto(val items: List<TripDto>, val total: Int, val limit: Int, val offset: Int)

@Serializable
data class TrendPointDto(
    @SerialName("bulletin_date") val bulletinDate: String,
    @SerialName("price_eur_l") val priceEurL: String,
)

@Serializable
data class SpeedProfileDto(
    val country: String,
    val season: String = "",
    @SerialName("avg_speed_kmh") val avgSpeedKmh: String? = null,
    @SerialName("motorway_limit_kmh") val motorwayLimitKmh: Int? = null,
    @SerialName("limit_note") val limitNote: String? = null,
)

@Serializable
data class ErrorEnvelope(val error: ErrorBodyDto)

@Serializable
data class ErrorBodyDto(
    val code: String,
    val message: String,
    @SerialName("request_id") val requestId: String? = null,
)
